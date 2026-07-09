package com.openrsc.android.security;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import orsc.multiclient.CredentialSnapshot;
import orsc.multiclient.CredentialStore;
import orsc.multiclient.SavedCredentialAccount;
import orsc.util.StringUtil;

/** Android Keystore-backed implementation of the shared credential-store contract. */
public final class AndroidCredentialStore implements CredentialStore {

	private static final Object PROCESS_LOCK = new Object();

	private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
	private static final String KEY_ALIAS = "voidscape.credentials.aes.v1";
	private static final String STORE_FILE_NAME = "voidscape-credentials.v1.bin";
	private static final String TOMBSTONE_FILE_NAME = "voidscape-credentials.v1.cleared";
	private static final String LEGACY_CREDENTIALS_FILE_NAME = "credentials.txt";
	private static final String LEGACY_ACCOUNTS_FILE_NAME = "accounts.txt";

	private static final int ENVELOPE_MAGIC = 0x56534345; // VSCE
	private static final int ENVELOPE_VERSION = 1;
	private static final int PAYLOAD_MAGIC = 0x56534350; // VSCP
	private static final int PAYLOAD_VERSION = 1;
	private static final int TOMBSTONE_MAGIC = 0x56534354; // VSCT
	private static final int TOMBSTONE_VERSION = 1;
	private static final int GCM_TAG_BITS = 128;
	private static final int GCM_IV_BYTES = 12;
	private static final int MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;
	private static final int MAX_ENVELOPE_BYTES = MAX_PAYLOAD_BYTES + 64;
	private static final int MAX_LEGACY_FILE_BYTES = 4 * 1024 * 1024;
	private static final int MAX_STRING_BYTES = 1024 * 1024;
	private static final int MAX_ACCOUNTS = 128;
	private static final int MAX_METADATA_ENTRIES = 512;
	private static final byte[] AAD = "voidscape.credentials|envelope=1|payload=1"
		.getBytes(StandardCharsets.UTF_8);

	private final File noBackupDirectory;
	private final File legacyDirectory;
	private final AtomicFile storeFile;
	private final AtomicFile tombstoneFile;

	public AndroidCredentialStore(Context context) {
		Context applicationContext = context.getApplicationContext();
		Context safeContext = applicationContext == null ? context : applicationContext;
		this.noBackupDirectory = safeContext.getNoBackupFilesDir();
		this.legacyDirectory = safeContext.getFilesDir();
		this.storeFile = new AtomicFile(new File(noBackupDirectory, STORE_FILE_NAME));
		this.tombstoneFile = new AtomicFile(new File(noBackupDirectory, TOMBSTONE_FILE_NAME));
	}

	@Override
	public Result load() {
		synchronized (PROCESS_LOCK) {
			try {
				ensureStorageDirectory();
				if (hasAtomicArtifacts(tombstoneFile)) {
					readAndVerifyTombstone();
					cleanupClearedState();
					return Result.absent();
				}
				if (hasAtomicArtifacts(storeFile)) {
					CredentialSnapshot snapshot = readAndVerifyEnvelope();
					if (snapshot.isEmpty()) {
						tryWriteAndVerifyTombstone();
					}
					cleanupLegacyFiles();
					return asResult(snapshot);
				}
				return migrateLegacyIfPresent();
			} catch (Exception ignored) {
				return Result.unavailable();
			}
		}
	}

	@Override
	public Result replace(CredentialSnapshot snapshot) {
		if (!isSemanticallyValid(snapshot)) {
			return Result.unavailable();
		}
		if (snapshot.isEmpty()) {
			return clear();
		}
		synchronized (PROCESS_LOCK) {
			try {
				ensureStorageDirectory();
				if (hasAtomicArtifacts(tombstoneFile)) {
					readAndVerifyTombstone();
				} else if (hasAtomicArtifacts(storeFile)) {
					readAndVerifyEnvelope();
				}
				writeAndVerifyEnvelope(snapshot);
				deleteAtomicFile(tombstoneFile);
				if (hasAtomicArtifacts(tombstoneFile)) {
					return Result.unavailable();
				}
				cleanupLegacyFiles();
				return Result.value(snapshot);
			} catch (Exception ignored) {
				return Result.unavailable();
			}
		}
	}

	@Override
	public Result forget(String username) {
		if (username == null || username.trim().isEmpty()) {
			return Result.unavailable();
		}
		synchronized (PROCESS_LOCK) {
			Result loaded = load();
			if (!loaded.hasValue()) {
				return loaded;
			}

			CredentialSnapshot current = loaded.getValue();
			String targetKey = accountKey(username);
			List<SavedCredentialAccount> remaining = new ArrayList<>();
			boolean removed = false;
			for (SavedCredentialAccount account : current.getAccounts()) {
				if (targetKey.equals(accountKey(account.getUsername()))) {
					removed = true;
				} else {
					remaining.add(account);
				}
			}
			if (!removed) {
				return loaded;
			}
			if (remaining.isEmpty()) {
				return clear();
			}

			String selected = current.getSelectedUsername();
			if (selected != null && targetKey.equals(accountKey(selected))) {
				selected = null;
			}
			return replace(new CredentialSnapshot(selected, remaining, current.getMetadata()));
		}
	}

	@Override
	public Result clear() {
		synchronized (PROCESS_LOCK) {
			try {
				ensureStorageDirectory();
				writeAndVerifyTombstone();
			} catch (Exception ignored) {
				return Result.unavailable();
			}
			// The verified tombstone is the logical commit point. Physical cleanup is retried on load.
			cleanupClearedState();
			return Result.absent();
		}
	}

	private Result migrateLegacyIfPresent() throws Exception {
		File credentialsFile = new File(legacyDirectory, LEGACY_CREDENTIALS_FILE_NAME);
		File accountsFile = new File(legacyDirectory, LEGACY_ACCOUNTS_FILE_NAME);
		if (!credentialsFile.isFile() && !accountsFile.isFile()) {
			return Result.absent();
		}

		CredentialSnapshot snapshot = readLegacySnapshot(credentialsFile, accountsFile);
		writeAndVerifyEnvelope(snapshot);
		if (snapshot.isEmpty()) {
			writeAndVerifyTombstone();
			cleanupClearedState();
			return Result.absent();
		}
		cleanupLegacyFiles();
		return Result.value(snapshot);
	}

	private CredentialSnapshot readLegacySnapshot(File credentialsFile, File accountsFile)
			throws IOException {
		LinkedHashMap<String, SavedCredentialAccount> accountsByKey = new LinkedHashMap<>();
		Map<String, String> storeMetadata = new LinkedHashMap<>();

		if (accountsFile.isFile()) {
			ensureLegacyFileSize(accountsFile);
			Properties properties = new Properties();
			try (InputStream input = new BufferedInputStream(new FileInputStream(accountsFile))) {
				properties.load(input);
			}
			int count = legacyAccountCount(properties);
			validateLegacyAccountIndices(properties, count);
			for (int index = 0; index < count; index++) {
				String prefix = "account." + index + ".";
				String username = properties.getProperty(prefix + "username", "").trim();
				String password = properties.getProperty(prefix + "password", "");
				if (username.isEmpty() || password.isEmpty()) {
					throw new IOException("legacy account record is incomplete");
				}
				String displayName = properties.getProperty(prefix + "displayName", username).trim();
				if (displayName.isEmpty()) {
					displayName = username;
				}
				int combatLevel = nonNegativeInt(properties.getProperty(prefix + "combatLevel"));
				long lastUsed = nonNegativeLong(properties.getProperty(prefix + "lastUsed"));
				Map<String, String> accountMetadata = legacyAccountMetadata(properties, prefix);
				SavedCredentialAccount account = new SavedCredentialAccount(username, password,
					displayName, combatLevel, lastUsed, accountMetadata);
				if (accountsByKey.put(accountKey(username), account) != null) {
					throw new IOException("legacy account record is duplicated");
				}
			}
			storeMetadata.putAll(legacyStoreMetadata(properties));
		}

		String selectedUsername = null;
		LegacyCredential selected = readLegacyCredential(credentialsFile);
		if (selected != null) {
			String key = accountKey(selected.username);
			SavedCredentialAccount prior = accountsByKey.get(key);
			if (prior == null) {
				prior = new SavedCredentialAccount(selected.username, selected.password,
					selected.username, 0, 0L);
			} else {
				prior = new SavedCredentialAccount(prior.getUsername(), selected.password,
					prior.getDisplayName(), prior.getCombatLevel(), prior.getLastUsedMillis(),
					prior.getMetadata());
			}
			accountsByKey.put(key, prior);
			selectedUsername = prior.getUsername();
		}

		CredentialSnapshot snapshot = new CredentialSnapshot(selectedUsername,
			new ArrayList<>(accountsByKey.values()), storeMetadata);
		if (!isSemanticallyValid(snapshot)) {
			throw new IOException("legacy credential data is invalid");
		}
		return snapshot;
	}

	private LegacyCredential readLegacyCredential(File credentialsFile) throws IOException {
		if (!credentialsFile.isFile()) {
			return null;
		}
		ensureLegacyFileSize(credentialsFile);
		StringBuilder value = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(credentialsFile), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				value.append(line);
			}
		}
		int separator = value.indexOf(",");
		if (value.length() == 0) {
			return null;
		}
		if (separator < 0) {
			throw new IOException("legacy credential record is incomplete");
		}
		String username = value.substring(0, separator).trim();
		String password = value.substring(separator + 1);
		if (username.isEmpty() || password.isEmpty()) {
			throw new IOException("legacy credential record is incomplete");
		}
		return new LegacyCredential(username, password);
	}

	private int legacyAccountCount(Properties properties) throws IOException {
		String countValue = properties.getProperty("count");
		if (countValue != null) {
			try {
				int count = Integer.parseInt(countValue.trim());
				if (count < 0 || count > MAX_ACCOUNTS) {
					throw new IOException("legacy account count is out of range");
				}
				return count;
			} catch (NumberFormatException exception) {
				throw new IOException("legacy account count is invalid", exception);
			}
		}

		int inferredCount = 0;
		for (String name : properties.stringPropertyNames()) {
			if (!name.startsWith("account.")) {
				continue;
			}
			int index = legacyAccountIndex(name);
			inferredCount = Math.max(inferredCount, index + 1);
		}
		if (inferredCount > MAX_ACCOUNTS) {
			throw new IOException("legacy account count is out of range");
		}
		return inferredCount;
	}

	private void validateLegacyAccountIndices(Properties properties, int count) throws IOException {
		for (String name : properties.stringPropertyNames()) {
			if (name.startsWith("account.") && legacyAccountIndex(name) >= count) {
				throw new IOException("legacy account index is out of range");
			}
		}
	}

	private int legacyAccountIndex(String propertyName) throws IOException {
		int prefixLength = "account.".length();
		int nextDot = propertyName.indexOf('.', prefixLength);
		if (nextDot <= prefixLength || nextDot == propertyName.length() - 1) {
			throw new IOException("legacy account property is invalid");
		}
		try {
			int index = Integer.parseInt(propertyName.substring(prefixLength, nextDot));
			if (index < 0 || index >= MAX_ACCOUNTS) {
				throw new IOException("legacy account index is out of range");
			}
			return index;
		} catch (NumberFormatException exception) {
			throw new IOException("legacy account property is invalid", exception);
		}
	}

	private Map<String, String> legacyAccountMetadata(Properties properties, String prefix) {
		Map<String, String> metadata = new LinkedHashMap<>();
		Set<String> known = new HashSet<>();
		Collections.addAll(known, "username", "password", "displayName", "combatLevel", "lastUsed");
		for (String name : properties.stringPropertyNames()) {
			if (name.startsWith(prefix)) {
				String key = name.substring(prefix.length());
				if (!known.contains(key)) {
					metadata.put(key, properties.getProperty(name, ""));
				}
			}
		}
		return metadata;
	}

	private Map<String, String> legacyStoreMetadata(Properties properties) {
		Map<String, String> metadata = new LinkedHashMap<>();
		for (String name : properties.stringPropertyNames()) {
			if (!"count".equals(name) && !name.startsWith("account.")) {
				metadata.put(name, properties.getProperty(name, ""));
			}
		}
		return metadata;
	}

	private void writeAndVerifyEnvelope(CredentialSnapshot snapshot) throws Exception {
		if (!isSemanticallyValid(snapshot)) {
			throw new IOException("credential snapshot is invalid");
		}
		SecretKey key = getOrCreateKey();
		byte[] payload = encodePayload(snapshot);

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, key);
		cipher.updateAAD(AAD);
		byte[] ciphertext = cipher.doFinal(payload);
		byte[] iv = cipher.getIV();
		if (iv == null || iv.length != GCM_IV_BYTES) {
			throw new IOException("credential IV is unavailable");
		}
		byte[] envelope = encodeEnvelope(iv, ciphertext);
		if (envelope.length > MAX_ENVELOPE_BYTES) {
			throw new IOException("credential envelope is too large");
		}
		byte[] previous = hasAtomicArtifacts(storeFile)
			? readAtomic(storeFile, MAX_ENVELOPE_BYTES)
			: null;
		writeAtomic(storeFile, envelope);

		try {
			CredentialSnapshot reopened = readAndVerifyEnvelope();
			if (!snapshot.equals(reopened)) {
				throw new IOException("credential verification failed");
			}
		} catch (Exception verificationFailure) {
			try {
				if (previous == null) {
					deleteAtomicFile(storeFile);
				} else {
					writeAtomic(storeFile, previous);
				}
			} catch (Exception ignored) {
			}
			throw verificationFailure;
		}
	}

	private CredentialSnapshot readAndVerifyEnvelope() throws Exception {
		byte[] envelope = readAtomic(storeFile, MAX_ENVELOPE_BYTES);
		DataInputStream input = new DataInputStream(new ByteArrayInputStream(envelope));
		if (input.readInt() != ENVELOPE_MAGIC || input.readInt() != ENVELOPE_VERSION) {
			throw new IOException("unsupported credential envelope");
		}
		int ivLength = input.readInt();
		if (ivLength != GCM_IV_BYTES) {
			throw new IOException("invalid credential IV");
		}
		byte[] iv = new byte[ivLength];
		input.readFully(iv);
		int ciphertextLength = input.readInt();
		if (ciphertextLength < 16 || ciphertextLength > MAX_PAYLOAD_BYTES + 16
				|| ciphertextLength != input.available()) {
			throw new IOException("invalid credential ciphertext");
		}
		byte[] ciphertext = new byte[ciphertextLength];
		input.readFully(ciphertext);

		SecretKey key = getExistingKey();
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
		cipher.updateAAD(AAD);
		byte[] payload = cipher.doFinal(ciphertext);
		CredentialSnapshot snapshot = decodePayload(payload);
		if (!isSemanticallyValid(snapshot)) {
			throw new IOException("credential payload is invalid");
		}
		return snapshot;
	}

	private byte[] encodeEnvelope(byte[] iv, byte[] ciphertext) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeInt(ENVELOPE_MAGIC);
			output.writeInt(ENVELOPE_VERSION);
			output.writeInt(iv.length);
			output.write(iv);
			output.writeInt(ciphertext.length);
			output.write(ciphertext);
		}
		return bytes.toByteArray();
	}

	private byte[] encodePayload(CredentialSnapshot snapshot) throws IOException {
		int encodedSize = checkedPayloadSize(snapshot);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(encodedSize);
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeInt(PAYLOAD_MAGIC);
			output.writeInt(PAYLOAD_VERSION);
			writeNullableString(output, snapshot.getSelectedUsername());
			output.writeInt(snapshot.getAccounts().size());
			for (SavedCredentialAccount account : snapshot.getAccounts()) {
				writeString(output, account.getUsername());
				writeString(output, account.getPassword());
				writeString(output, account.getDisplayName());
				output.writeInt(account.getCombatLevel());
				output.writeLong(account.getLastUsedMillis());
				writeMetadata(output, account.getMetadata());
			}
			writeMetadata(output, snapshot.getMetadata());
		}
		byte[] payload = bytes.toByteArray();
		if (payload.length > MAX_PAYLOAD_BYTES) {
			throw new IOException("credential payload is too large");
		}
		return payload;
	}

	private int checkedPayloadSize(CredentialSnapshot snapshot) throws IOException {
		long size = 8L + encodedNullableStringSize(snapshot.getSelectedUsername()) + 4L;
		for (SavedCredentialAccount account : snapshot.getAccounts()) {
			size += encodedStringSize(account.getUsername());
			size += encodedStringSize(account.getPassword());
			size += encodedStringSize(account.getDisplayName());
			size += 12L;
			size += encodedMetadataSize(account.getMetadata());
			if (size > MAX_PAYLOAD_BYTES) {
				throw new IOException("credential payload is too large");
			}
		}
		size += encodedMetadataSize(snapshot.getMetadata());
		if (size > MAX_PAYLOAD_BYTES) {
			throw new IOException("credential payload is too large");
		}
		return (int) size;
	}

	private long encodedNullableStringSize(String value) {
		return value == null ? 4L : encodedStringSize(value);
	}

	private long encodedMetadataSize(Map<String, String> metadata) {
		long size = 4L;
		for (Map.Entry<String, String> entry : metadata.entrySet()) {
			size += encodedStringSize(entry.getKey());
			size += encodedStringSize(entry.getValue());
		}
		return size;
	}

	private long encodedStringSize(String value) {
		return 4L + value.getBytes(StandardCharsets.UTF_8).length;
	}

	private CredentialSnapshot decodePayload(byte[] payload) throws IOException {
		if (payload.length > MAX_PAYLOAD_BYTES) {
			throw new IOException("credential payload is too large");
		}
		DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
		if (input.readInt() != PAYLOAD_MAGIC || input.readInt() != PAYLOAD_VERSION) {
			throw new IOException("unsupported credential payload");
		}
		String selected = readNullableString(input);
		int accountCount = readBoundedCount(input, MAX_ACCOUNTS, "account");
		List<SavedCredentialAccount> accounts = new ArrayList<>(accountCount);
		for (int index = 0; index < accountCount; index++) {
			String username = readString(input);
			String password = readString(input);
			String displayName = readString(input);
			int combatLevel = input.readInt();
			long lastUsed = input.readLong();
			Map<String, String> metadata = readMetadata(input);
			accounts.add(new SavedCredentialAccount(username, password, displayName,
				combatLevel, lastUsed, metadata));
		}
		Map<String, String> metadata = readMetadata(input);
		if (input.available() != 0) {
			throw new IOException("trailing credential payload data");
		}
		return new CredentialSnapshot(selected, accounts, metadata);
	}

	private void writeMetadata(DataOutputStream output, Map<String, String> metadata)
			throws IOException {
		output.writeInt(metadata.size());
		for (Map.Entry<String, String> entry : metadata.entrySet()) {
			writeString(output, entry.getKey());
			writeString(output, entry.getValue());
		}
	}

	private Map<String, String> readMetadata(DataInputStream input) throws IOException {
		int count = readBoundedCount(input, MAX_METADATA_ENTRIES, "metadata");
		Map<String, String> metadata = new LinkedHashMap<>();
		for (int index = 0; index < count; index++) {
			String key = readString(input);
			String value = readString(input);
			if (metadata.put(key, value) != null) {
				throw new IOException("duplicate credential metadata");
			}
		}
		return metadata;
	}

	private void writeNullableString(DataOutputStream output, String value) throws IOException {
		if (value == null) {
			output.writeInt(-1);
			return;
		}
		writeString(output, value);
	}

	private String readNullableString(DataInputStream input) throws IOException {
		int length = input.readInt();
		if (length == -1) {
			return null;
		}
		return readString(input, length);
	}

	private void writeString(DataOutputStream output, String value) throws IOException {
		byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		if (encoded.length > MAX_STRING_BYTES) {
			throw new IOException("credential string is too large");
		}
		output.writeInt(encoded.length);
		output.write(encoded);
	}

	private String readString(DataInputStream input) throws IOException {
		return readString(input, input.readInt());
	}

	private String readString(DataInputStream input, int length) throws IOException {
		if (length < 0 || length > MAX_STRING_BYTES || length > input.available()) {
			throw new IOException("invalid credential string");
		}
		byte[] encoded = new byte[length];
		input.readFully(encoded);
		return new String(encoded, StandardCharsets.UTF_8);
	}

	private int readBoundedCount(DataInputStream input, int maximum, String name)
			throws IOException {
		int count = input.readInt();
		if (count < 0 || count > maximum) {
			throw new IOException("invalid credential " + name + " count");
		}
		return count;
	}

	private boolean isSemanticallyValid(CredentialSnapshot snapshot) {
		if (snapshot == null || snapshot.getAccounts().size() > MAX_ACCOUNTS
				|| !isValidMetadata(snapshot.getMetadata())) {
			return false;
		}
		Set<String> accountKeys = new HashSet<>();
		for (SavedCredentialAccount account : snapshot.getAccounts()) {
			if (account == null || account.getUsername().trim().isEmpty()
					|| account.getPassword().isEmpty() || account.getDisplayName() == null
					|| account.getCombatLevel() < 0 || account.getLastUsedMillis() < 0L
					|| !isBoundedString(account.getUsername())
					|| !isBoundedString(account.getPassword())
					|| !isBoundedString(account.getDisplayName())
					|| !isValidMetadata(account.getMetadata())
					|| !accountKeys.add(accountKey(account.getUsername()))) {
				return false;
			}
		}
		String selected = snapshot.getSelectedUsername();
		if (snapshot.isEmpty()) {
			return selected == null && snapshot.getMetadata().isEmpty();
		}
		return selected == null || (isBoundedString(selected)
			&& accountKeys.contains(accountKey(selected)));
	}

	private boolean isValidMetadata(Map<String, String> metadata) {
		if (metadata == null || metadata.size() > MAX_METADATA_ENTRIES) {
			return false;
		}
		for (Map.Entry<String, String> entry : metadata.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null
					|| !isBoundedString(entry.getKey()) || !isBoundedString(entry.getValue())) {
				return false;
			}
		}
		return true;
	}

	private boolean isBoundedString(String value) {
		return value != null && value.getBytes(StandardCharsets.UTF_8).length <= MAX_STRING_BYTES;
	}

	private SecretKey getExistingKey() throws Exception {
		KeyStore keyStore = loadKeyStore();
		if (!keyStore.containsAlias(KEY_ALIAS)) {
			throw new IOException("credential key is unavailable");
		}
		Key key = keyStore.getKey(KEY_ALIAS, null);
		if (!(key instanceof SecretKey)) {
			throw new IOException("credential key is unavailable");
		}
		return (SecretKey) key;
	}

	private SecretKey getOrCreateKey() throws Exception {
		KeyStore keyStore = loadKeyStore();
		if (keyStore.containsAlias(KEY_ALIAS)) {
			Key key = keyStore.getKey(KEY_ALIAS, null);
			if (key instanceof SecretKey) {
				return (SecretKey) key;
			}
			throw new IOException("credential key is unavailable");
		}

		KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
			KEYSTORE_PROVIDER);
		generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
			KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
			.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
			.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
			.setKeySize(256)
			.setRandomizedEncryptionRequired(true)
			.build());
		return generator.generateKey();
	}

	private KeyStore loadKeyStore() throws Exception {
		KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
		keyStore.load(null);
		return keyStore;
	}

	private void deleteKey() throws Exception {
		KeyStore keyStore = loadKeyStore();
		if (keyStore.containsAlias(KEY_ALIAS)) {
			keyStore.deleteEntry(KEY_ALIAS);
		}
	}

	private void writeAndVerifyTombstone() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(8);
		try (DataOutputStream output = new DataOutputStream(bytes)) {
			output.writeInt(TOMBSTONE_MAGIC);
			output.writeInt(TOMBSTONE_VERSION);
		}
		writeAtomic(tombstoneFile, bytes.toByteArray());
		readAndVerifyTombstone();
	}

	private void tryWriteAndVerifyTombstone() {
		try {
			writeAndVerifyTombstone();
		} catch (IOException ignored) {
		}
	}

	private void readAndVerifyTombstone() throws IOException {
		byte[] bytes = readAtomic(tombstoneFile, 64);
		DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
		if (input.readInt() != TOMBSTONE_MAGIC || input.readInt() != TOMBSTONE_VERSION
				|| input.available() != 0) {
			throw new IOException("invalid credential tombstone");
		}
	}

	private void writeAtomic(AtomicFile file, byte[] bytes) throws IOException {
		FileOutputStream output = null;
		try {
			output = file.startWrite();
			output.write(bytes);
			output.flush();
			output.getFD().sync();
			file.finishWrite(output);
			output = null;
		} catch (IOException | RuntimeException exception) {
			if (output != null) {
				file.failWrite(output);
			}
			throw exception;
		}
	}

	private byte[] readAtomic(AtomicFile file, int maximumBytes) throws IOException {
		try (InputStream input = new BufferedInputStream(file.openRead())) {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int total = 0;
			int read;
			while ((read = input.read(buffer)) != -1) {
				total += read;
				if (total > maximumBytes) {
					throw new IOException("credential file is too large");
				}
				bytes.write(buffer, 0, read);
			}
			return bytes.toByteArray();
		}
	}

	private boolean hasAtomicArtifacts(AtomicFile file) {
		File base = file.getBaseFile();
		return base.exists() || new File(base.getPath() + ".bak").exists()
			|| new File(base.getPath() + ".new").exists();
	}

	private void deleteAtomicFile(AtomicFile file) {
		file.delete();
		File base = file.getBaseFile();
		new File(base.getPath() + ".bak").delete();
		new File(base.getPath() + ".new").delete();
	}

	private void ensureStorageDirectory() throws IOException {
		if (!noBackupDirectory.isDirectory() && !noBackupDirectory.mkdirs()) {
			throw new IOException("credential storage is unavailable");
		}
	}

	private void cleanupLegacyFiles() {
		deleteIfFile(new File(legacyDirectory, LEGACY_CREDENTIALS_FILE_NAME));
		deleteIfFile(new File(legacyDirectory, LEGACY_ACCOUNTS_FILE_NAME));
	}

	private void cleanupClearedState() {
		deleteAtomicFile(storeFile);
		try {
			deleteKey();
		} catch (Exception ignored) {
		}
		cleanupLegacyFiles();
	}

	private void deleteIfFile(File file) {
		if (file.isFile()) {
			file.delete();
		}
	}

	private void ensureLegacyFileSize(File file) throws IOException {
		if (file.length() > MAX_LEGACY_FILE_BYTES) {
			throw new IOException("legacy credential file is too large");
		}
	}

	private int nonNegativeInt(String value) {
		if (value == null) {
			return 0;
		}
		try {
			return Math.max(0, Integer.parseInt(value.trim()));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private long nonNegativeLong(String value) {
		if (value == null) {
			return 0L;
		}
		try {
			return Math.max(0L, Long.parseLong(value.trim()));
		} catch (NumberFormatException ignored) {
			return 0L;
		}
	}

	private String accountKey(String username) {
		if (username == null) {
			return "";
		}
		String key = StringUtil.displayNameToKey(username);
		return key == null ? username.trim().toLowerCase(Locale.ROOT) : key;
	}

	private Result asResult(CredentialSnapshot snapshot) {
		return snapshot.isEmpty() ? Result.absent() : Result.value(snapshot);
	}

	private static final class LegacyCredential {
		private final String username;
		private final String password;

		private LegacyCredential(String username, String password) {
			this.username = username;
			this.password = password;
		}
	}
}
