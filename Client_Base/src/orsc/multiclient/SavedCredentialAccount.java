package orsc.multiclient;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One saved login and its non-secret account metadata.
 *
 * <p>The password remains in memory while the client is using this value. Persistent storage is
 * delegated to the platform {@link CredentialStore}; callers must not serialize this object to a
 * regular cache file.</p>
 */
public final class SavedCredentialAccount {

	private final String username;
	private final String password;
	private final String displayName;
	private final int combatLevel;
	private final long lastUsedMillis;
	private final Map<String, String> metadata;

	public SavedCredentialAccount(String username, String password, String displayName,
			int combatLevel, long lastUsedMillis) {
		this(username, password, displayName, combatLevel, lastUsedMillis,
			Collections.<String, String>emptyMap());
	}

	public SavedCredentialAccount(String username, String password, String displayName,
			int combatLevel, long lastUsedMillis, Map<String, String> metadata) {
		this.username = Objects.requireNonNull(username, "username");
		this.password = Objects.requireNonNull(password, "password");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
		this.combatLevel = combatLevel;
		this.lastUsedMillis = lastUsedMillis;
		this.metadata = immutableCopy(metadata);
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getCombatLevel() {
		return combatLevel;
	}

	public long getLastUsedMillis() {
		return lastUsedMillis;
	}

	public Map<String, String> getMetadata() {
		return metadata;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof SavedCredentialAccount)) {
			return false;
		}
		SavedCredentialAccount account = (SavedCredentialAccount) other;
		return combatLevel == account.combatLevel
			&& lastUsedMillis == account.lastUsedMillis
			&& username.equals(account.username)
			&& password.equals(account.password)
			&& displayName.equals(account.displayName)
			&& metadata.equals(account.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(username, password, displayName, combatLevel, lastUsedMillis, metadata);
	}

	@Override
	public String toString() {
		return "SavedCredentialAccount{redacted}";
	}

	private static Map<String, String> immutableCopy(Map<String, String> source) {
		Objects.requireNonNull(source, "metadata");
		LinkedHashMap<String, String> copy = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : source.entrySet()) {
			copy.put(Objects.requireNonNull(entry.getKey(), "metadata key"),
				Objects.requireNonNull(entry.getValue(), "metadata value"));
		}
		return Collections.unmodifiableMap(copy);
	}
}
