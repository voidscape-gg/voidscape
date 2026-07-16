package orsc.appearance.v2;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Strict runtime projection of the manifest-generated Paperdoll V2 selector registry.
 *
 * <p>This reader intentionally implements the canonical ASCII contract emitted by
 * {@code v2_selector_registry.py}; it is not a general Java-properties reader. The
 * sidecar is accepted only when its complete key set, base identities, selector
 * routes, pack bindings, and repository catalog binding all agree.</p>
 */
public final class PaperdollV2SelectorRegistry {
	public static final String SCHEMA =
		"voidscape-paperdoll-v2-selector-registry-properties/v1";
	public static final String FILE_NAME = "selector-registry.properties";
	public static final String CATALOG_PATH = "content/appearance/v2/catalog.yaml";
	public static final String PACK_PATH = "build/Paperdoll_V2.orsc";
	public static final int CLASSIC_SELECTOR_ID = 0;
	public static final int MINIMUM_SELECTOR_ID = 0;
	public static final int MAXIMUM_SELECTOR_ID = 255;

	private static final int MAX_BYTES = 64 * 1024;
	private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");
	private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");
	private static final Set<String> ELIGIBILITY_STATES = immutableSet(
		"proof-only", "qa-ready", "shipping");
	private static final Set<String> PLATFORMS = immutableSet("pc", "android", "teavm");

	private final File propertiesFile;
	private final String propertiesSha256;
	private final String catalogSha256;
	private final String packSha256;
	private final List<Entry> entries;
	private final Entry[] entriesBySelector;

	private PaperdollV2SelectorRegistry(File propertiesFile, String propertiesSha256,
		String catalogSha256, String packSha256, List<Entry> entries) {
		this.propertiesFile = propertiesFile;
		this.propertiesSha256 = propertiesSha256;
		this.catalogSha256 = catalogSha256;
		this.packSha256 = packSha256;
		this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
		this.entriesBySelector = new Entry[MAXIMUM_SELECTOR_ID + 1];
		for (Entry entry : entries) this.entriesBySelector[entry.selectorId] = entry;
	}

	public static PaperdollV2SelectorRegistry read(File propertiesFile,
		PaperdollV2Pack pack, File repositoryRoot) throws IOException {
		if (propertiesFile == null || !propertiesFile.isFile()
			|| !FILE_NAME.equals(propertiesFile.getName())) {
			throw new IOException("selector registry must be an existing " + FILE_NAME);
		}
		if (pack == null) throw new IOException("selector registry has no validated V2 pack");
		if (repositoryRoot == null || !repositoryRoot.isDirectory()) {
			throw new IOException("selector registry has no repository root");
		}

		byte[] bytes;
		try (FileInputStream input = new FileInputStream(propertiesFile)) {
			bytes = readBounded(input, MAX_BYTES);
		}
		Map<String, String> values = parseCanonicalProperties(bytes);
		List<Integer> selectorIds = requireSelectorIds(values);
		Set<String> expectedKeys = expectedKeys(selectorIds);
		if (!values.keySet().equals(expectedKeys)) {
			Set<String> missing = new TreeSet<>(expectedKeys);
			missing.removeAll(values.keySet());
			Set<String> unknown = new TreeSet<>(values.keySet());
			unknown.removeAll(expectedKeys);
			throw new IOException("selector registry key set mismatch: missing=" + missing
				+ " unknown=" + unknown);
		}

		requireExact(values, "schema", SCHEMA);
		requireExact(values, "default.enabled", "false");
		requireExact(values, "namespace.field", "hairStyle");
		requireInt(values, "namespace.classic_id", CLASSIC_SELECTOR_ID);
		requireInt(values, "namespace.minimum", MINIMUM_SELECTOR_ID);
		requireInt(values, "namespace.maximum", MAXIMUM_SELECTOR_ID);
		requireExact(values, "namespace.stability_policy", "never-reuse");
		requireExact(values, "base.ids", "male,female");
		requireExact(values, "selector.unknown.route", "legacy");
		requireExact(values, "hat.v2_allowed_ids", "0");
		requireExact(values, "hat.nonzero.route", "legacy");
		requireExact(values, "hat.unknown.route", "legacy");
		requireExact(values, "catalog.path", CATALOG_PATH);
		requireExact(values, "pack.path", PACK_PATH);

		validateBaseProfile(values, PaperdollV2BaseProfile.MALE);
		validateBaseProfile(values, PaperdollV2BaseProfile.FEMALE);

		String packSha256 = requireSha256(values, "pack.sha256");
		if (!pack.getArchiveSha256().equals(packSha256)) {
			throw new IOException("selector registry pack SHA-256 does not match sibling archive");
		}
		requireDigestBinding(values, "template.sha256", pack.getTemplateSha256());
		requireDigestBinding(values, "template.source_v1.sha256", pack.getSourceV1Sha256());
		requireDigestBinding(values, "template.derived_masks.sha256",
			pack.getDerivedMasksSha256());

		File repository = repositoryRoot.getCanonicalFile();
		File catalog = new File(repository, CATALOG_PATH).getCanonicalFile();
		if (!catalog.getPath().startsWith(repository.getPath() + File.separator)
			|| !catalog.isFile()) {
			throw new IOException("selector registry catalog path is missing or escapes repository");
		}
		String catalogSha256 = sha256(readFileBounded(catalog, 4 * 1024 * 1024));
		if (!catalogSha256.equals(requireSha256(values, "catalog.sha256"))) {
			throw new IOException("selector registry catalog SHA-256 does not match repository catalog");
		}

		ArrayList<Entry> entries = new ArrayList<>();
		HashSet<String> styles = new HashSet<>();
		for (int selectorIndex = 0; selectorIndex < selectorIds.size(); selectorIndex++) {
			int selectorId = selectorIds.get(selectorIndex);
			String prefix = "selector." + selectorId;
			String style = requireId(values, prefix + ".style");
			if (!styles.add(style)) {
				throw new IOException("selector registry repeats style " + style);
			}
			requireExact(values, prefix + ".base_profiles", "male,female");
			String state = requireAllowed(values, prefix + ".eligibility.state",
				ELIGIBILITY_STATES);
			List<String> platforms = requireCsv(values,
				prefix + ".eligibility.platforms", PLATFORMS);

			PaperdollV2Pack.RenderStack maleControl = requireQaControl(values, pack,
				prefix + ".qa_control.male", PaperdollV2BaseProfile.MALE);
			PaperdollV2Pack.RenderStack femaleControl = requireQaControl(values, pack,
				prefix + ".qa_control.female", PaperdollV2BaseProfile.FEMALE);
			if (selectorId == CLASSIC_SELECTOR_ID) {
				requireExact(values, prefix + ".style", "classic");
				requireExact(values, prefix + ".route", "legacy");
				if (!"shipping".equals(state)
					|| !platforms.equals(csv("pc", "android", "teavm"))) {
					throw new IOException("Classic selector eligibility must remain shipping on all clients");
				}
				entries.add(new Entry(selectorId, style, "legacy", state, platforms,
					maleControl, femaleControl, null, null));
				continue;
			}

			requireExact(values, prefix + ".route", "v2");
			if (!platforms.equals(csv("pc"))) {
				throw new IOException("V2 selector " + selectorId + " must remain PC-only");
			}
			PaperdollV2Pack.RenderStack male = requireV2Stack(values, pack,
				prefix + ".stack.male", PaperdollV2BaseProfile.MALE, style);
			PaperdollV2Pack.RenderStack female = requireV2Stack(values, pack,
				prefix + ".stack.female", PaperdollV2BaseProfile.FEMALE, style);
			entries.add(new Entry(selectorId, style, "v2", state, platforms,
				maleControl, femaleControl, male, female));
		}
		return new PaperdollV2SelectorRegistry(propertiesFile.getCanonicalFile(), sha256(bytes),
			catalogSha256, packSha256, entries);
	}

	private static void validateBaseProfile(Map<String, String> values,
		PaperdollV2BaseProfile profile) throws IOException {
		String prefix = "base." + profile.getId();
		requireExact(values, prefix + ".gender", profile.getId());
		requireInt(values, prefix + ".head_appearance_id", profile.getHeadAppearanceId());
		requireInt(values, prefix + ".body_appearance_id", profile.getBodyAppearanceId());
		requireInt(values, prefix + ".legs_appearance_id", profile.getLegsAppearanceId());
	}

	private static PaperdollV2Pack.RenderStack requireQaControl(Map<String, String> values,
		PaperdollV2Pack pack, String key, PaperdollV2BaseProfile profile) throws IOException {
		String stackId = requireId(values, key);
		if (!(profile.getId() + "_control").equals(stackId)) {
			throw new IOException(key + " must name the canonical " + profile.getId() + " control");
		}
		PaperdollV2Pack.RenderStack stack = pack.requireRenderStack(stackId);
		if (stack.usesLiveControls()) {
			throw new IOException("selector registry QA control " + stackId + " must remain pack-only");
		}
		if (PaperdollV2BaseProfile.derive(stack, null) != profile) {
			throw new IOException("selector registry QA control " + stackId + " has wrong base profile");
		}
		return stack;
	}

	private static PaperdollV2Pack.RenderStack requireV2Stack(Map<String, String> values,
		PaperdollV2Pack pack, String key, PaperdollV2BaseProfile profile, String style)
		throws IOException {
		String stackId = requireId(values, key);
		PaperdollV2Pack.RenderStack stack = pack.requireRenderStack(stackId);
		if (!stack.usesLiveControls()) {
			throw new IOException("selector registry V2 stack " + stackId + " is not live-controls");
		}
		if (PaperdollV2BaseProfile.derive(stack, null) != profile) {
			throw new IOException("selector registry V2 stack " + stackId + " has wrong base profile");
		}
		String expectedStackId = profile.getId() + "_" + style;
		if (!expectedStackId.equals(stackId)) {
			throw new IOException("selector registry " + key + " must equal " + expectedStackId);
		}
		int[] slots = stack.getNativePaperdollSlots();
		if (slots.length != 3 || slots[0] != 0 || slots[1] != 1 || slots[2] != 2) {
			throw new IOException("selector registry V2 stack " + stackId
				+ " must substitute exactly native slots 0,1,2");
		}
		String expectedHairAssetId = "hair_" + style;
		int hairAssets = 0;
		boolean foundExpectedHairAsset = false;
		for (int assetIndex = 0; assetIndex < stack.getAssetCount(); assetIndex++) {
			PaperdollV2Pack.Asset asset = stack.getAsset(assetIndex);
			if ("hair".equals(asset.getKind())) {
				hairAssets++;
				if (expectedHairAssetId.equals(asset.getId())) foundExpectedHairAsset = true;
			}
		}
		if (hairAssets != 1 || !foundExpectedHairAsset) {
			throw new IOException("selector registry V2 stack " + stackId
				+ " must contain exactly hair asset " + expectedHairAssetId);
		}
		return stack;
	}

	private static Set<String> expectedKeys(List<Integer> selectorIds) {
		HashSet<String> keys = new HashSet<>();
		Collections.addAll(keys, "schema", "default.enabled", "catalog.path", "catalog.sha256",
			"template.sha256", "template.source_v1.sha256", "template.derived_masks.sha256",
			"pack.path", "pack.sha256", "namespace.field", "namespace.classic_id",
			"namespace.minimum", "namespace.maximum", "namespace.stability_policy", "base.ids",
			"selector.ids", "selector.unknown.route", "hat.v2_allowed_ids",
			"hat.nonzero.route", "hat.unknown.route");
		for (String profile : new String[] {"male", "female"}) {
			Collections.addAll(keys, "base." + profile + ".gender",
				"base." + profile + ".head_appearance_id",
				"base." + profile + ".body_appearance_id",
				"base." + profile + ".legs_appearance_id");
		}
		for (int selectorIndex = 0; selectorIndex < selectorIds.size(); selectorIndex++) {
			int selectorId = selectorIds.get(selectorIndex);
			String prefix = "selector." + selectorId;
			Collections.addAll(keys, prefix + ".style", prefix + ".route",
				prefix + ".base_profiles", prefix + ".eligibility.state",
				prefix + ".eligibility.platforms", prefix + ".qa_control.male",
				prefix + ".qa_control.female");
			if (selectorId > 0) {
				keys.add(prefix + ".stack.male");
				keys.add(prefix + ".stack.female");
			}
		}
		return keys;
	}

	private static List<Integer> requireSelectorIds(Map<String, String> values) throws IOException {
		String encoded = values.get("selector.ids");
		if (encoded == null || encoded.length() == 0) {
			throw new IOException("selector registry selector.ids is empty");
		}
		String[] parts = encoded.split(",", -1);
		ArrayList<Integer> ids = new ArrayList<>();
		for (int index = 0; index < parts.length; index++) {
			String part = parts[index];
			int selectorId;
			try {
				selectorId = Integer.parseInt(part);
			} catch (NumberFormatException error) {
				throw new IOException("selector registry selector.ids contains a non-integer id", error);
			}
			if (!Integer.toString(selectorId).equals(part) || selectorId != index
				|| selectorId < MINIMUM_SELECTOR_ID || selectorId > MAXIMUM_SELECTOR_ID) {
				throw new IOException("selector registry selector.ids must be canonical contiguous 0..N "
					+ "within 0..255");
			}
			ids.add(selectorId);
		}
		if (ids.get(0) != CLASSIC_SELECTOR_ID) {
			throw new IOException("selector registry selector.ids must begin with Classic 0");
		}
		return Collections.unmodifiableList(ids);
	}

	private static Map<String, String> parseCanonicalProperties(byte[] bytes) throws IOException {
		for (byte value : bytes) {
			if ((value & 0xff) > 0x7f) {
				throw new IOException("selector registry must be ASCII");
			}
		}
		String text = new String(bytes, StandardCharsets.US_ASCII);
		if (!text.endsWith("\n") || text.indexOf('\r') >= 0) {
			throw new IOException("selector registry must use terminal LF and no CR characters");
		}
		LinkedHashMap<String, String> values = new LinkedHashMap<>();
		String previousKey = null;
		String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
		for (String line : lines) {
			if (line.length() == 0 || line.startsWith("#") || line.startsWith("!")) {
				throw new IOException("selector registry contains an unsupported line");
			}
			int equals = line.indexOf('=');
			if (equals <= 0) throw new IOException("selector registry contains an unsupported line");
			String key = line.substring(0, equals);
			String value = line.substring(equals + 1);
			if (key.indexOf(':') >= 0 || key.indexOf('=') >= 0) {
				throw new IOException("selector registry contains an unsafe key");
			}
			if (values.containsKey(key)) {
				throw new IOException("duplicate selector registry property " + key);
			}
			if (previousKey != null && previousKey.compareTo(key) >= 0) {
				throw new IOException("selector registry keys must be sorted canonically");
			}
			values.put(key, value);
			previousKey = key;
		}
		return values;
	}

	private static void requireExact(Map<String, String> values, String key, String expected)
		throws IOException {
		String actual = values.get(key);
		if (!expected.equals(actual)) {
			throw new IOException("selector registry " + key + " must equal " + expected);
		}
	}

	private static void requireInt(Map<String, String> values, String key, int expected)
		throws IOException {
		requireExact(values, key, Integer.toString(expected));
	}

	private static String requireId(Map<String, String> values, String key) throws IOException {
		String value = values.get(key);
		if (value == null || !ID_PATTERN.matcher(value).matches()) {
			throw new IOException("selector registry " + key + " is not a canonical id");
		}
		return value;
	}

	private static String requireSha256(Map<String, String> values, String key) throws IOException {
		String value = values.get(key);
		if (value == null || !SHA256_PATTERN.matcher(value).matches()) {
			throw new IOException("selector registry " + key + " is not a SHA-256 digest");
		}
		return value;
	}

	private static void requireDigestBinding(Map<String, String> values, String key,
		String expected) throws IOException {
		if (!expected.equals(requireSha256(values, key))) {
			throw new IOException("selector registry " + key + " does not match sibling archive");
		}
	}

	private static String requireAllowed(Map<String, String> values, String key,
		Set<String> allowed) throws IOException {
		String value = values.get(key);
		if (!allowed.contains(value)) {
			throw new IOException("selector registry " + key + " is unsupported: " + value);
		}
		return value;
	}

	private static List<String> requireCsv(Map<String, String> values, String key,
		Set<String> allowed) throws IOException {
		String value = values.get(key);
		if (value == null || value.length() == 0) {
			throw new IOException("selector registry " + key + " is empty");
		}
		String[] parts = value.split(",", -1);
		ArrayList<String> result = new ArrayList<>();
		HashSet<String> unique = new HashSet<>();
		for (String part : parts) {
			if (!allowed.contains(part) || !unique.add(part)) {
				throw new IOException("selector registry " + key + " is not a unique allowed list");
			}
			result.add(part);
		}
		return Collections.unmodifiableList(result);
	}

	private static List<String> csv(String... values) {
		ArrayList<String> result = new ArrayList<>();
		Collections.addAll(result, values);
		return result;
	}

	private static byte[] readFileBounded(File file, int maximum) throws IOException {
		try (FileInputStream input = new FileInputStream(file)) {
			return readBounded(input, maximum);
		}
	}

	private static byte[] readBounded(FileInputStream input, int maximum) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = input.read(buffer)) != -1) {
			if (output.size() + read > maximum) {
				throw new IOException("selector registry input exceeds " + maximum + " bytes");
			}
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
		} catch (NoSuchAlgorithmException error) {
			throw new IOException("SHA-256 is unavailable", error);
		}
	}

	private static Set<String> immutableSet(String... values) {
		HashSet<String> result = new HashSet<>();
		Collections.addAll(result, values);
		return Collections.unmodifiableSet(result);
	}

	public File getPropertiesFile() { return propertiesFile; }
	public String getPropertiesSha256() { return propertiesSha256; }
	public String getCatalogPath() { return CATALOG_PATH; }
	public String getCatalogSha256() { return catalogSha256; }
	public String getPackSha256() { return packSha256; }
	public String getNamespaceField() { return "hairStyle"; }
	public int getClassicSelectorId() { return CLASSIC_SELECTOR_ID; }
	public int getMinimumSelectorId() { return MINIMUM_SELECTOR_ID; }
	public int getMaximumSelectorId() { return MAXIMUM_SELECTOR_ID; }
	/** Highest selector currently declared by the contiguous manifest registry. */
	public int getHighestDefinedSelectorId() { return entries.size() - 1; }
	public boolean isDefaultEnabled() { return false; }
	public List<Entry> getEntries() { return entries; }

	public Entry getEntry(int selectorId) {
		return selectorId < 0 || selectorId >= entriesBySelector.length
			? null : entriesBySelector[selectorId];
	}

	public PaperdollV2BaseProfile matchBaseProfile(int[] layerAnimation) {
		if (layerAnimation == null || layerAnimation.length < 3) return null;
		if (matches(layerAnimation, PaperdollV2BaseProfile.MALE)) {
			return PaperdollV2BaseProfile.MALE;
		}
		if (matches(layerAnimation, PaperdollV2BaseProfile.FEMALE)) {
			return PaperdollV2BaseProfile.FEMALE;
		}
		return null;
	}

	private static boolean matches(int[] layerAnimation, PaperdollV2BaseProfile profile) {
		return layerAnimation[0] == profile.getHeadAppearanceId()
			&& layerAnimation[1] == profile.getBodyAppearanceId()
			&& layerAnimation[2] == profile.getLegsAppearanceId();
	}

	public static final class Entry {
		private final int selectorId;
		private final String style;
		private final String route;
		private final String eligibilityState;
		private final List<String> eligibilityPlatforms;
		private final PaperdollV2Pack.RenderStack maleQaControl;
		private final PaperdollV2Pack.RenderStack femaleQaControl;
		private final PaperdollV2Runtime.RenderSelection maleSelection;
		private final PaperdollV2Runtime.RenderSelection femaleSelection;

		private Entry(int selectorId, String style, String route, String eligibilityState,
			List<String> eligibilityPlatforms, PaperdollV2Pack.RenderStack maleQaControl,
			PaperdollV2Pack.RenderStack femaleQaControl, PaperdollV2Pack.RenderStack male,
			PaperdollV2Pack.RenderStack female) {
			this.selectorId = selectorId;
			this.style = style;
			this.route = route;
			this.eligibilityState = eligibilityState;
			this.eligibilityPlatforms = eligibilityPlatforms;
			this.maleQaControl = maleQaControl;
			this.femaleQaControl = femaleQaControl;
			this.maleSelection = male == null ? null : new PaperdollV2Runtime.RenderSelection(
				male, PaperdollV2BaseProfile.MALE, selectorId, style);
			this.femaleSelection = female == null ? null : new PaperdollV2Runtime.RenderSelection(
				female, PaperdollV2BaseProfile.FEMALE, selectorId, style);
		}

		public int getSelectorId() { return selectorId; }
		public String getStyle() { return style; }
		public String getRoute() { return route; }
		public boolean isV2Route() { return "v2".equals(route); }
		public String getEligibilityState() { return eligibilityState; }
		public List<String> getEligibilityPlatforms() { return eligibilityPlatforms; }
		public String getQaControlStackId(PaperdollV2BaseProfile profile) {
			return (profile == PaperdollV2BaseProfile.FEMALE
				? femaleQaControl : maleQaControl).getId();
		}
		public PaperdollV2Runtime.RenderSelection getSelection(PaperdollV2BaseProfile profile) {
			return profile == PaperdollV2BaseProfile.FEMALE ? femaleSelection : maleSelection;
		}
		public String getStackId(PaperdollV2BaseProfile profile) {
			PaperdollV2Runtime.RenderSelection selection = getSelection(profile);
			return selection == null ? "" : selection.getStack().getId();
		}
	}
}
