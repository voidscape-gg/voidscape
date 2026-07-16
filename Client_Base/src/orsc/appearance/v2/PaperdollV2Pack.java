package orsc.appearance.v2;

import com.openrsc.client.model.Sprite;
import orsc.util.CacheArchive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Strict reader for the non-shipping Paperdoll V2 proof archive.
 *
 * <p>The reader deliberately validates the complete archive before exposing any
 * sprite. A missing channel, mismatched digest, malformed sprite, or undeclared
 * ZIP entry invalidates the whole pack; callers must never render a partial V2
 * appearance.</p>
 */
public final class PaperdollV2Pack {
	public static final String SCHEMA = "voidscape-paperdoll-v2-pack/v1";
	public static final String TEMPLATE = "rsc-player-2x-v1";
	public static final int FRAME_COUNT = 18;
	public static final int WALK_WIDTH = 128;
	public static final int COMBAT_WIDTH = 168;
	public static final int PLAYER_HEIGHT = 204;
	public static final int PREVIEW_WIDTH = 176;
	public static final int PREVIEW_HEIGHT = 224;
	public static final int PREVIEW_DRAW_X = 24;
	public static final int PREVIEW_DRAW_Y = 4;

	private static final int MAX_ARCHIVE_BYTES = 32 * 1024 * 1024;
	private static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;
	private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
	private static final Pattern CHANNEL_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
	private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");
	private static final Set<String> ALLOWED_KINDS = immutableSet(
		"head", "hair", "facial-hair", "body", "legs", "hat", "legacy-control");
	private static final Set<String> ALLOWED_TINTS = immutableSet(
		"fixed", "skin", "hair", "facial-hair", "top", "bottom", "primary", "secondary");
	private static final Set<String> ALLOWED_SOURCE_MODES = immutableSet("native", "legacy-upscaled");
	private static final Set<String> ALLOWED_PROPAGATION = immutableSet("rigid-head", "explicit-frames");
	private static final Set<String> ALLOWED_STACK_MODES = immutableSet("pack-only", "live-controls");

	private final String registrySha256;
	private final String archiveSha256;
	private final String templateSha256;
	private final String derivedMasksSha256;
	private final String sourceV1Sha256;
	private final long archiveByteCount;
	private final long decodedArgbByteCount;
	private final int spriteCount;
	private final Map<String, Asset> assets;
	private final Map<String, RenderStack> renderStacks;

	private PaperdollV2Pack(String registrySha256, String archiveSha256, String templateSha256,
		String derivedMasksSha256, String sourceV1Sha256, long archiveByteCount,
		long decodedArgbByteCount, int spriteCount, Map<String, Asset> assets,
		Map<String, RenderStack> renderStacks) {
		this.registrySha256 = registrySha256;
		this.archiveSha256 = archiveSha256;
		this.templateSha256 = templateSha256;
		this.derivedMasksSha256 = derivedMasksSha256;
		this.sourceV1Sha256 = sourceV1Sha256;
		this.archiveByteCount = archiveByteCount;
		this.decodedArgbByteCount = decodedArgbByteCount;
		this.spriteCount = spriteCount;
		this.assets = Collections.unmodifiableMap(new LinkedHashMap<>(assets));
		this.renderStacks = Collections.unmodifiableMap(new LinkedHashMap<>(renderStacks));
	}

	public static PaperdollV2Pack read(InputStream input) throws IOException {
		if (input == null) throw new IOException("Paperdoll V2 archive input is null");
		byte[] archiveBytes = readBounded(input, MAX_ARCHIVE_BYTES, "Paperdoll V2 archive");
		Set<String> zipEntries = validateZipEntries(archiveBytes);
		CacheArchive archive = CacheArchive.read(new ByteArrayInputStream(archiveBytes));
		byte[] registryBytes = archive.getEntry("registry.properties");
		if (registryBytes == null) throw new IOException("Paperdoll V2 archive is missing registry.properties");

		Map<String, String> registry = parseRegistry(registryBytes);
		Set<String> expectedRegistryKeys = new HashSet<>();
		Collections.addAll(expectedRegistryKeys, "schema", "template", "frame.count",
			"canvas.walk.width", "canvas.combat.width", "canvas.height",
			"preview.width", "preview.height", "preview.draw_x", "preview.draw_y",
			"preview.draw_width", "preview.draw_height", "template.sha256",
			"template.derived_masks.sha256", "template.source_v1.sha256",
			"asset.ids", "render.stack.ids");
		requireExact(registry, "schema", SCHEMA);
		requireExact(registry, "template", TEMPLATE);
		requireInt(registry, "frame.count", FRAME_COUNT);
		requireInt(registry, "canvas.walk.width", WALK_WIDTH);
		requireInt(registry, "canvas.combat.width", COMBAT_WIDTH);
		requireInt(registry, "canvas.height", PLAYER_HEIGHT);
		requireInt(registry, "preview.width", PREVIEW_WIDTH);
		requireInt(registry, "preview.height", PREVIEW_HEIGHT);
		requireInt(registry, "preview.draw_x", PREVIEW_DRAW_X);
		requireInt(registry, "preview.draw_y", PREVIEW_DRAW_Y);
		requireInt(registry, "preview.draw_width", WALK_WIDTH);
		requireInt(registry, "preview.draw_height", PLAYER_HEIGHT);
		String templateSha256 = requireSha256(registry, "template.sha256");
		String derivedMasksSha256 = requireSha256(registry, "template.derived_masks.sha256");
		String sourceV1Sha256 = requireSha256(registry, "template.source_v1.sha256");

		List<String> assetIds = requireIdList(registry, "asset.ids", ID_PATTERN);
		Map<String, Asset> assets = new LinkedHashMap<>();
		Set<String> expectedZipEntries = new HashSet<>();
		expectedZipEntries.add("registry.properties");
		for (String assetId : assetIds) {
			Asset asset = readAsset(archive, registry, assetId, expectedZipEntries, expectedRegistryKeys);
			assets.put(assetId, asset);
		}

		List<String> stackIds = requireIdList(registry, "render.stack.ids", ID_PATTERN);
		Map<String, RenderStack> stacks = new LinkedHashMap<>();
		Set<String> referencedAssets = new HashSet<>();
		for (String stackId : stackIds) {
			String prefix = "render.stack." + stackId;
			expectedRegistryKeys.add(prefix + ".assets");
			expectedRegistryKeys.add(prefix + ".mode");
			String mode = requireAllowed(registry, prefix + ".mode", ALLOWED_STACK_MODES);
			List<String> ids = requireIdList(registry, prefix + ".assets", ID_PATTERN);
			List<Asset> stackAssets = new ArrayList<>();
			for (String assetId : ids) {
				Asset asset = assets.get(assetId);
				if (asset == null) {
					throw new IOException("Paperdoll V2 stack " + stackId
						+ " references undeclared asset " + assetId);
				}
				stackAssets.add(asset);
				referencedAssets.add(assetId);
			}
			validateStackMode(stackId, mode, stackAssets);
			stacks.put(stackId, new RenderStack(stackId, mode, stackAssets));
		}
		if (!referencedAssets.equals(assets.keySet())) {
			Set<String> unused = new HashSet<>(assets.keySet());
			unused.removeAll(referencedAssets);
			throw new IOException("Paperdoll V2 registry contains assets unused by every render stack: " + unused);
		}
		if (!zipEntries.equals(expectedZipEntries)) {
			Set<String> missing = new HashSet<>(expectedZipEntries);
			missing.removeAll(zipEntries);
			Set<String> undeclared = new HashSet<>(zipEntries);
			undeclared.removeAll(expectedZipEntries);
			throw new IOException("Paperdoll V2 ZIP entries disagree with registry: missing=" + missing
				+ " undeclared=" + undeclared);
		}
		if (!registry.keySet().equals(expectedRegistryKeys)) {
			Set<String> missing = new HashSet<>(expectedRegistryKeys);
			missing.removeAll(registry.keySet());
			Set<String> unknown = new HashSet<>(registry.keySet());
			unknown.removeAll(expectedRegistryKeys);
			throw new IOException("Paperdoll V2 registry key set mismatch: missing=" + missing
				+ " unknown=" + unknown);
		}

		long decodedArgbBytes = 0L;
		int spriteCount = 0;
		for (Asset asset : assets.values()) {
			for (Channel channel : asset.getChannels()) {
				for (int frame = 0; frame < FRAME_COUNT; frame++) {
					Sprite sprite = channel.getFrame(frame);
					decodedArgbBytes += (long) sprite.getWidth() * (long) sprite.getHeight() * 4L;
					spriteCount++;
				}
			}
		}
		return new PaperdollV2Pack(sha256(registryBytes), sha256(archiveBytes), templateSha256,
			derivedMasksSha256, sourceV1Sha256, archiveBytes.length, decodedArgbBytes,
			spriteCount, assets, stacks);
	}

	public String getRegistrySha256() {
		return registrySha256;
	}

	public String getArchiveSha256() { return archiveSha256; }
	public String getTemplateSha256() { return templateSha256; }
	public String getDerivedMasksSha256() { return derivedMasksSha256; }
	public String getSourceV1Sha256() { return sourceV1Sha256; }
	public long getArchiveByteCount() { return archiveByteCount; }
	public long getDecodedArgbByteCount() { return decodedArgbByteCount; }
	public int getSpriteCount() { return spriteCount; }

	public List<String> getAssetIds() {
		return Collections.unmodifiableList(new ArrayList<>(assets.keySet()));
	}

	public List<String> getRenderStackIds() {
		return Collections.unmodifiableList(new ArrayList<>(renderStacks.keySet()));
	}

	public Asset getAsset(String assetId) {
		return assets.get(assetId);
	}

	public RenderStack requireRenderStack(String stackId) throws IOException {
		RenderStack stack = renderStacks.get(stackId);
		if (stack == null) throw new IOException("Unknown Paperdoll V2 render stack: " + stackId);
		return stack;
	}

	private static Asset readAsset(CacheArchive archive, Map<String, String> registry, String assetId,
		Set<String> expectedZipEntries, Set<String> expectedRegistryKeys) throws IOException {
		String prefix = "asset." + assetId;
		Collections.addAll(expectedRegistryKeys, prefix + ".kind", prefix + ".source_mode",
			prefix + ".propagation", prefix + ".paperdoll_slot", prefix + ".channels");
		String kind = requireAllowed(registry, prefix + ".kind", ALLOWED_KINDS);
		String sourceMode = requireAllowed(registry, prefix + ".source_mode", ALLOWED_SOURCE_MODES);
		String propagation = requireAllowed(registry, prefix + ".propagation", ALLOWED_PROPAGATION);
		int paperdollSlot = requireIntRange(registry, prefix + ".paperdoll_slot", 0, 11);
		validateAssetSlot(assetId, kind, paperdollSlot);
		List<String> channelIds = requireIdList(registry, prefix + ".channels", CHANNEL_PATTERN);
		Map<String, Channel> channels = new LinkedHashMap<>();
		for (String channelId : channelIds) {
			String channelPrefix = prefix + ".channel." + channelId;
			expectedRegistryKeys.add(channelPrefix + ".tint");
			String tint = requireAllowed(registry, channelPrefix + ".tint", ALLOWED_TINTS);
			Sprite[] frames = new Sprite[FRAME_COUNT];
			String[] hashes = new String[FRAME_COUNT];
			boolean[] empty = new boolean[FRAME_COUNT];
			for (int frame = 0; frame < FRAME_COUNT; frame++) {
				String frameKey = String.format("%02d", frame);
				String framePrefix = prefix + ".frame." + frameKey + "." + channelId;
				expectedRegistryKeys.add(framePrefix + ".entry");
				expectedRegistryKeys.add(framePrefix + ".sha256");
				expectedRegistryKeys.add(framePrefix + ".empty");
				String entryName = require(registry, framePrefix + ".entry");
				String expectedEntry = "sprites/" + assetId + "/" + channelId + "/frame_" + frameKey;
				if (!expectedEntry.equals(entryName)) {
					throw new IOException("Paperdoll V2 registry entry path mismatch for " + framePrefix
						+ ": expected " + expectedEntry + " but found " + entryName);
				}
				if (!expectedZipEntries.add(entryName)) {
					throw new IOException("Paperdoll V2 sprite entry is declared more than once: " + entryName);
				}
				String expectedHash = require(registry, framePrefix + ".sha256");
				if (!SHA256_PATTERN.matcher(expectedHash).matches()) {
					throw new IOException("Invalid lowercase SHA-256 for " + framePrefix);
				}
				boolean expectedEmpty = requireBoolean(registry, framePrefix + ".empty");
				byte[] packed = archive.getEntry(entryName);
				if (packed == null) throw new IOException("Paperdoll V2 archive is missing " + entryName);
				String actualHash = sha256(packed);
				if (!expectedHash.equals(actualHash)) {
					throw new IOException("Paperdoll V2 SHA-256 mismatch for " + entryName
						+ ": expected " + expectedHash + " but found " + actualHash);
				}
				Sprite sprite = unpackSprite(packed, entryName, frame, expectedEmpty);
				frames[frame] = sprite;
				hashes[frame] = actualHash;
				empty[frame] = expectedEmpty;
			}
			channels.put(channelId, new Channel(channelId, tint, frames, hashes, empty));
		}
		Asset asset = new Asset(assetId, kind, sourceMode, propagation, paperdollSlot, channels);
		validateNativeAsset(asset);
		return asset;
	}

	private static void validateNativeAsset(Asset asset) throws IOException {
		if (!"native".equals(asset.sourceMode)) return;
		for (int frame = 0; frame < FRAME_COUNT; frame++) {
			boolean compositeVisible = false;
			for (int channelIndex = 0; channelIndex < asset.getChannelCount(); channelIndex++) {
				if (!asset.getChannel(channelIndex).isEmpty(frame)) compositeVisible = true;
			}
			if (!compositeVisible) {
				throw new IOException("Paperdoll V2 native asset " + asset.id
					+ " has no visible raster for frame " + frame);
			}
		}
		if (!"head".equals(asset.kind) && !"body".equals(asset.kind)) return;
		Channel skin = null;
		for (int channelIndex = 0; channelIndex < asset.getChannelCount(); channelIndex++) {
			Channel channel = asset.getChannel(channelIndex);
			if (!"skin".equals(channel.tintRole)) continue;
			if (skin != null) {
				throw new IOException("Paperdoll V2 native " + asset.kind + " asset " + asset.id
					+ " has more than one skin channel");
			}
			skin = channel;
		}
		if (skin == null) {
			throw new IOException("Paperdoll V2 native " + asset.kind + " asset " + asset.id
				+ " lacks its required skin channel");
		}
		for (int frame = 0; frame < FRAME_COUNT; frame++) {
			if (skin.isEmpty(frame)) {
				throw new IOException("Paperdoll V2 native " + asset.kind + " skin raster is empty: "
					+ asset.id + " frame " + frame);
			}
			boolean visible = false;
			for (int argb : skin.getFrame(frame).getPixels()) {
				if ((argb >>> 24) == 0) continue;
				visible = true;
				int red = argb >>> 16 & 0xff;
				int green = argb >>> 8 & 0xff;
				int blue = argb & 0xff;
				if (red != green || red != blue || (red != 0x76 && red != 0xb0 && red != 0xff)) {
					throw new IOException("Paperdoll V2 native " + asset.kind + " skin raster "
						+ asset.id + " frame " + frame
						+ " contains a visible pixel outside neutral ramp 76,b0,ff");
				}
			}
			if (!visible) {
				throw new IOException("Paperdoll V2 native " + asset.kind + " skin raster has no "
					+ "visible pixels: " + asset.id + " frame " + frame);
			}
		}
	}

	private static Sprite unpackSprite(byte[] packed, String entryName, int frame,
		boolean expectedEmpty) throws IOException {
		if (packed.length < 25) throw new IOException("Paperdoll V2 sprite header is truncated: " + entryName);
		ByteBuffer header = ByteBuffer.wrap(packed);
		int croppedWidth = header.getInt();
		int croppedHeight = header.getInt();
		byte shiftFlag = header.get();
		int xShift = header.getInt();
		int yShift = header.getInt();
		int logicalWidth = header.getInt();
		int logicalHeight = header.getInt();
		int expectedLogicalWidth = frame < 15 ? WALK_WIDTH : COMBAT_WIDTH;
		if (croppedWidth <= 0 || croppedHeight <= 0 || croppedWidth > expectedLogicalWidth
			|| croppedHeight > PLAYER_HEIGHT) {
			throw new IOException("Paperdoll V2 cropped dimensions are invalid for " + entryName);
		}
		if (shiftFlag != 1 || logicalWidth != expectedLogicalWidth || logicalHeight != PLAYER_HEIGHT) {
			throw new IOException("Paperdoll V2 logical geometry mismatch for " + entryName
				+ ": shift=" + shiftFlag + " logical=" + logicalWidth + "x" + logicalHeight);
		}
		if (xShift < 0 || yShift < 0 || xShift + croppedWidth > logicalWidth
			|| yShift + croppedHeight > logicalHeight) {
			throw new IOException("Paperdoll V2 crop lies outside its logical canvas: " + entryName);
		}
		long expectedLength = 25L + (long) croppedWidth * (long) croppedHeight * 4L;
		if (expectedLength != packed.length) {
			throw new IOException("Paperdoll V2 sprite length mismatch for " + entryName
				+ ": expected " + expectedLength + " but found " + packed.length);
		}
		Sprite sprite = Sprite.unpack(ByteBuffer.wrap(packed));
		boolean hasVisiblePixel = false;
		for (int argb : sprite.getPixels()) {
			if ((argb >>> 24) != 0) {
				hasVisiblePixel = true;
				break;
			}
		}
		if (expectedEmpty) {
			if (croppedWidth != 1 || croppedHeight != 1 || sprite.getPixels()[0] != 0) {
				throw new IOException("Paperdoll V2 empty frame must be one transparent pixel: " + entryName);
			}
		} else if (!hasVisiblePixel) {
			throw new IOException("Paperdoll V2 non-empty frame contains no visible pixels: " + entryName);
		}
		return sprite;
	}

	private static void validateAssetSlot(String assetId, String kind, int slot) throws IOException {
		if (("head".equals(kind) || "hair".equals(kind) || "facial-hair".equals(kind)) && slot != 0) {
			throw new IOException("Paperdoll V2 " + kind + " asset " + assetId + " must use paperdoll slot 0");
		}
		if ("hat".equals(kind) && slot != 5) {
			throw new IOException("Paperdoll V2 hat asset " + assetId + " must use paperdoll slot 5");
		}
		if ("body".equals(kind) && slot != 1) {
			throw new IOException("Paperdoll V2 body asset " + assetId + " must use paperdoll slot 1");
		}
		if ("legs".equals(kind) && slot != 2) {
			throw new IOException("Paperdoll V2 legs asset " + assetId + " must use paperdoll slot 2");
		}
		if ("legacy-control".equals(kind) && slot != 0 && slot != 1 && slot != 2 && slot != 5) {
			throw new IOException("Paperdoll V2 legacy control " + assetId + " must use slot 0, 1, 2, or 5");
		}
	}

	private static void validateStackMode(String stackId, String mode, List<Asset> stackAssets)
		throws IOException {
		if ("pack-only".equals(mode)) {
			for (Asset asset : stackAssets) {
				if (!"legacy-upscaled".equals(asset.sourceMode)) {
					throw new IOException("Paperdoll V2 pack-only stack " + stackId
						+ " contains native asset " + asset.id);
				}
			}
			return;
		}
		boolean[] nativeSlots = new boolean[12];
		for (Asset asset : stackAssets) {
			if ("native".equals(asset.sourceMode)) nativeSlots[asset.paperdollSlot] = true;
		}
		int nativeHeadCount = 0;
		int nativeBodyCount = 0;
		int nativeLegsCount = 0;
		boolean nativeHeadSeen = false;
		for (Asset asset : stackAssets) {
			if (nativeSlots[asset.paperdollSlot] && "legacy-upscaled".equals(asset.sourceMode)) {
				throw new IOException("Paperdoll V2 live-controls stack " + stackId
					+ " cannot mix a legacy asset into native slot-" + asset.paperdollSlot
					+ " substitution: " + asset.id);
			}
			if (!"native".equals(asset.sourceMode)) continue;
			if (asset.paperdollSlot == 0) {
				if ("head".equals(asset.kind)) {
					nativeHeadCount++;
					nativeHeadSeen = true;
				} else if ("hair".equals(asset.kind) || "facial-hair".equals(asset.kind)) {
					if (!nativeHeadSeen) {
						throw new IOException("Paperdoll V2 live-controls stack " + stackId
							+ " must draw its native slot-0 head before " + asset.kind
							+ " asset " + asset.id);
					}
				} else {
					throw new IOException("Paperdoll V2 live-controls stack " + stackId
						+ " has unsupported native slot-0 anatomy: " + asset.id);
				}
			}
			if (asset.paperdollSlot == 1 && "body".equals(asset.kind)) nativeBodyCount++;
			if (asset.paperdollSlot == 2 && "legs".equals(asset.kind)) nativeLegsCount++;
			if (asset.paperdollSlot == 1 && !"body".equals(asset.kind)) {
				throw new IOException("Paperdoll V2 live-controls stack " + stackId
					+ " has a non-body native slot-1 asset: " + asset.id);
			}
			if (asset.paperdollSlot == 2 && !"legs".equals(asset.kind)) {
				throw new IOException("Paperdoll V2 live-controls stack " + stackId
					+ " has a non-legs native slot-2 asset: " + asset.id);
			}
			if (asset.paperdollSlot == 5) {
				throw new IOException("Paperdoll V2 live-controls stack " + stackId
					+ " cannot substitute native slot-5 until an appearance binding is explicit: "
					+ asset.id);
			}
		}
		if (nativeHeadCount != 1) {
			throw new IOException("Paperdoll V2 live-controls stack " + stackId
				+ " must provide exactly one native slot-0 head");
		}
		if (nativeBodyCount > 1 || nativeLegsCount > 1) {
			throw new IOException("Paperdoll V2 live-controls stack " + stackId
				+ " declares duplicate native base anatomy");
		}
	}

	private static Set<String> validateZipEntries(byte[] archiveBytes) throws IOException {
		Set<String> names = new HashSet<>();
		long total = 0;
		String previousName = null;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.isDirectory()) throw new IOException("Paperdoll V2 ZIP contains a directory entry");
				String name = entry.getName();
				if (name == null || name.isEmpty() || name.startsWith("/") || name.contains("\\")
					|| name.contains("../") || name.contains("/..")) {
					throw new IOException("Unsafe Paperdoll V2 ZIP entry name: " + name);
				}
				if (!names.add(name)) throw new IOException("Duplicate Paperdoll V2 ZIP entry: " + name);
				if (previousName != null && previousName.compareTo(name) >= 0) {
					throw new IOException("Paperdoll V2 ZIP entries are not in canonical sorted order");
				}
				previousName = name;
				if (entry.getMethod() != ZipEntry.STORED || entry.getSize() < 0
					|| entry.getSize() > MAX_ENTRY_BYTES) {
					throw new IOException("Paperdoll V2 ZIP entry is not a bounded STORED entry: " + name);
				}
				total += entry.getSize();
				if (total > MAX_ARCHIVE_BYTES) throw new IOException("Paperdoll V2 ZIP expands beyond its limit");
				zip.closeEntry();
			}
		}
		if (names.isEmpty()) throw new IOException("Paperdoll V2 archive contains no entries");
		return names;
	}

	private static Map<String, String> parseRegistry(byte[] bytes) throws IOException {
		if (bytes.length == 0 || bytes[bytes.length - 1] != '\n') {
			throw new IOException("Paperdoll V2 registry must be non-empty and end with LF");
		}
		for (byte value : bytes) {
			int c = value & 0xff;
			if (c != '\n' && (c < 0x20 || c > 0x7e)) {
				throw new IOException("Paperdoll V2 registry must contain printable ASCII and LF only");
			}
		}
		String text = new String(bytes, StandardCharsets.US_ASCII);
		String[] lines = text.split("\n", -1);
		Map<String, String> values = new LinkedHashMap<>();
		String previousKey = null;
		for (int index = 0; index < lines.length - 1; index++) {
			String line = lines[index];
			if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '!') {
				throw new IOException("Paperdoll V2 registry line " + (index + 1) + " is not a key=value entry");
			}
			int equals = line.indexOf('=');
			if (equals <= 0 || equals == line.length() - 1) {
				throw new IOException("Paperdoll V2 registry line " + (index + 1) + " is malformed");
			}
			String key = line.substring(0, equals);
			String value = line.substring(equals + 1);
			if (!key.equals(key.trim()) || !value.equals(value.trim())) {
				throw new IOException("Paperdoll V2 registry line " + (index + 1) + " has surrounding whitespace");
			}
			if (values.put(key, value) != null) {
				throw new IOException("Duplicate Paperdoll V2 registry key: " + key);
			}
			if (previousKey != null && previousKey.compareTo(key) >= 0) {
				throw new IOException("Paperdoll V2 registry keys are not in canonical sorted order");
			}
			previousKey = key;
		}
		return values;
	}

	private static List<String> requireIdList(Map<String, String> registry, String key, Pattern pattern)
		throws IOException {
		String value = require(registry, key);
		String[] parts = value.split(",", -1);
		List<String> ids = new ArrayList<>();
		Set<String> unique = new HashSet<>();
		for (String part : parts) {
			if (!pattern.matcher(part).matches()) throw new IOException("Invalid id in " + key + ": " + part);
			if (!unique.add(part)) throw new IOException("Duplicate id in " + key + ": " + part);
			ids.add(part);
		}
		if (ids.isEmpty()) throw new IOException("Paperdoll V2 registry list is empty: " + key);
		return Collections.unmodifiableList(ids);
	}

	private static String require(Map<String, String> registry, String key) throws IOException {
		String value = registry.get(key);
		if (value == null || value.isEmpty()) throw new IOException("Paperdoll V2 registry is missing " + key);
		return value;
	}

	private static String requireAllowed(Map<String, String> registry, String key, Set<String> allowed)
		throws IOException {
		String value = require(registry, key);
		if (!allowed.contains(value)) throw new IOException("Unsupported Paperdoll V2 value for " + key + ": " + value);
		return value;
	}

	private static boolean requireBoolean(Map<String, String> registry, String key) throws IOException {
		String value = require(registry, key);
		if ("true".equals(value)) return true;
		if ("false".equals(value)) return false;
		throw new IOException("Paperdoll V2 registry expected true or false for " + key);
	}

	private static String requireSha256(Map<String, String> registry, String key) throws IOException {
		String value = require(registry, key);
		if (!SHA256_PATTERN.matcher(value).matches()) {
			throw new IOException("Invalid lowercase SHA-256 for " + key);
		}
		return value;
	}

	private static void requireExact(Map<String, String> registry, String key, String expected) throws IOException {
		String value = require(registry, key);
		if (!expected.equals(value)) {
			throw new IOException("Paperdoll V2 registry expected " + key + "=" + expected + " but found " + value);
		}
	}

	private static void requireInt(Map<String, String> registry, String key, int expected) throws IOException {
		int value = requireIntRange(registry, key, Integer.MIN_VALUE, Integer.MAX_VALUE);
		if (value != expected) {
			throw new IOException("Paperdoll V2 registry expected " + key + "=" + expected + " but found " + value);
		}
	}

	private static int requireIntRange(Map<String, String> registry, String key, int min, int max)
		throws IOException {
		String text = require(registry, key);
		try {
			int value = Integer.parseInt(text);
			if (value < min || value > max) throw new IOException("Paperdoll V2 value out of range for " + key);
			return value;
		} catch (NumberFormatException e) {
			throw new IOException("Paperdoll V2 registry expected an integer for " + key, e);
		}
	}

	private static byte[] readBounded(InputStream input, int maxBytes, String label) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = input.read(buffer)) != -1) {
			if (output.size() + read > maxBytes) throw new IOException(label + " exceeds " + maxBytes + " bytes");
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static String sha256(byte[] bytes) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(bytes);
			StringBuilder result = new StringBuilder(64);
			for (byte value : hash) result.append(String.format("%02x", value & 0xff));
			return result.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 is unavailable", e);
		}
	}

	private static Set<String> immutableSet(String... values) {
		Set<String> result = new HashSet<>();
		Collections.addAll(result, values);
		return Collections.unmodifiableSet(result);
	}

	public static final class Asset {
		private final String id;
		private final String kind;
		private final String sourceMode;
		private final String propagation;
		private final int paperdollSlot;
		private final Map<String, Channel> channels;
		private final List<Channel> channelList;

		private Asset(String id, String kind, String sourceMode, String propagation, int paperdollSlot,
			Map<String, Channel> channels) {
			this.id = id;
			this.kind = kind;
			this.sourceMode = sourceMode;
			this.propagation = propagation;
			this.paperdollSlot = paperdollSlot;
			this.channels = Collections.unmodifiableMap(new LinkedHashMap<>(channels));
			this.channelList = Collections.unmodifiableList(new ArrayList<>(channels.values()));
		}

		public String getId() { return id; }
		public String getKind() { return kind; }
		public String getSourceMode() { return sourceMode; }
		public String getPropagation() { return propagation; }
		public int getPaperdollSlot() { return paperdollSlot; }
		public List<Channel> getChannels() { return channelList; }
		public int getChannelCount() { return channelList.size(); }
		public Channel getChannel(int index) { return channelList.get(index); }
	}

	public static final class Channel {
		private final String id;
		private final String tintRole;
		private final int tintRoleCode;
		private final Sprite[] frames;
		private final String[] sha256;
		private final boolean[] empty;

		private Channel(String id, String tintRole, Sprite[] frames, String[] sha256, boolean[] empty) {
			this.id = id;
			this.tintRole = tintRole;
			this.tintRoleCode = tintRoleCode(tintRole);
			this.frames = frames.clone();
			this.sha256 = sha256.clone();
			this.empty = empty.clone();
		}

		public String getId() { return id; }
		public String getTintRole() { return tintRole; }
		public int getTintRoleCode() { return tintRoleCode; }
		public Sprite getFrame(int frame) {
			if (frame < 0 || frame >= FRAME_COUNT) throw new IllegalArgumentException("frame out of range: " + frame);
			return frames[frame];
		}
		public String getSha256(int frame) {
			if (frame < 0 || frame >= FRAME_COUNT) throw new IllegalArgumentException("frame out of range: " + frame);
			return sha256[frame];
		}
		public boolean isEmpty(int frame) {
			if (frame < 0 || frame >= FRAME_COUNT) throw new IllegalArgumentException("frame out of range: " + frame);
			return empty[frame];
		}

		private static int tintRoleCode(String role) {
			if ("fixed".equals(role)) return 0;
			if ("skin".equals(role)) return 1;
			if ("hair".equals(role)) return 2;
			if ("facial-hair".equals(role)) return 3;
			if ("top".equals(role)) return 4;
			if ("bottom".equals(role)) return 5;
			if ("primary".equals(role)) return 6;
			if ("secondary".equals(role)) return 7;
			throw new IllegalArgumentException("Unknown Paperdoll V2 tint role: " + role);
		}
	}

	public static final class RenderStack {
		private final String id;
		private final String mode;
		private final List<Asset> assets;
		private final int[] nativePaperdollSlots;

		private RenderStack(String id, String mode, List<Asset> assets) {
			this.id = id;
			this.mode = mode;
			this.assets = Collections.unmodifiableList(new ArrayList<>(assets));
			boolean[] nativeSlots = new boolean[12];
			for (Asset asset : assets) {
				if ("native".equals(asset.sourceMode)) nativeSlots[asset.paperdollSlot] = true;
			}
			int count = 0;
			for (boolean nativeSlot : nativeSlots) if (nativeSlot) count++;
			this.nativePaperdollSlots = new int[count];
			int index = 0;
			for (int slot = 0; slot < nativeSlots.length; slot++) {
				if (nativeSlots[slot]) this.nativePaperdollSlots[index++] = slot;
			}
		}

		public String getId() { return id; }
		public String getMode() { return mode; }
		public boolean usesLiveControls() { return "live-controls".equals(mode); }
		public List<Asset> getAssets() { return assets; }
		public int getAssetCount() { return assets.size(); }
		public Asset getAsset(int index) { return assets.get(index); }
		public boolean substitutesSlot(int paperdollSlot) {
			for (int slot : nativePaperdollSlots) if (slot == paperdollSlot) return true;
			return false;
		}
		public int[] getNativePaperdollSlots() { return nativePaperdollSlots.clone(); }
	}
}
