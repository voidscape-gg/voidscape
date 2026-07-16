package orsc.appearance.v2;

import com.openrsc.client.model.Sprite;
import orsc.util.PngSpriteLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Strict, development-only reader for the compiler's 1x Paperdoll V2 fallback.
 *
 * <p>This deliberately does not reuse {@code VoidscapeHairOverlay}: the shipped
 * legacy loader is permissive and may substitute frame zero.  This reader
 * accepts only the canonical build projection, verifies every referenced file,
 * and publishes a style only after all eighteen frames pass.  A bad frame
 * therefore disables that entire style without affecting other validated
 * styles.</p>
 */
public final class PaperdollV2LegacyCompatibility {
	public static final String PROPERTY = "voidscape.paperdollV2.legacyCompatibility";
	public static final String FILE_NAME = "runtime.properties";
	public static final String SCHEMA =
		"voidscape-paperdoll-v2-legacy-compatibility-runtime-properties/v1";
	public static final int FRAME_COUNT = 18;
	public static final int COMPATIBLE_HEAD_APPEARANCE_ID = 8;

	private static final int MAX_PROPERTIES_BYTES = 256 * 1024;
	private static final int MAX_MANIFEST_BYTES = 4 * 1024 * 1024;
	private static final int MAX_SELECTOR_BYTES = 256 * 1024;
	private static final int MAX_PNG_BYTES = 1024 * 1024;
	private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");
	private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

	private static final Set<String> GLOBAL_KEYS = immutableSet(
		"activation.approved", "avatar.role", "avatar.thumbnail_count",
		"catalog.path", "catalog.sha256", "default.enabled", "downscale.algorithm",
		"downscale.max_detached_pixels_removed_per_frame", "downscale.post_process",
		"downscale.sample_phase", "frame.count", "manifest.path", "manifest.schema",
		"manifest.sha256", "output.scope", "overlay.file_count", "overlay.frame_count",
		"pack.path", "pack.sha256", "runtime.compatible_head_appearance_id",
		"runtime.hat.allowed_appearance_ids", "runtime.hat.nonzero.action",
		"runtime.platforms", "runtime.style_failure_policy", "runtime.write.client_cache",
		"runtime.write.max_selectors", "runtime.write.server_avatars", "schema",
		"selector.count", "selector.ids", "selector_registry.json.path",
		"selector_registry.json.sha256", "selector_registry.properties.path",
		"selector_registry.properties.sha256", "shipping", "sidecar.keys",
		"template.derived_masks.sha256", "template.path", "template.sha256",
		"template.source_v1.sha256");

	private static final String[] FRAME_SUFFIXES = {
		"crop.height", "crop.width", "crop.x", "crop.y", "detached_pixels_removed",
		"four_connected_components", "logical.height", "logical.width",
		"logical_rgba.sha256", "png.path", "png.sha256", "sidecar.path",
		"sidecar.requires_shift", "sidecar.sha256", "source_decimated_rgba.sha256"
	};

	private final boolean rootValid;
	private final String rootReason;
	private final File propertiesFile;
	private final String propertiesSha256;
	private final String manifestSha256;
	private final String expectedPackSha256;
	private final Sprite[][] framesBySelector;
	private final String[] styleBySelector;
	private final String[] rejectionBySelector;
	private final String[][] sourceDecimatedSha256BySelector;

	private PaperdollV2LegacyCompatibility(boolean rootValid, String rootReason,
		File propertiesFile, String propertiesSha256, String manifestSha256,
		String expectedPackSha256, Sprite[][] framesBySelector, String[] styleBySelector,
		String[] rejectionBySelector, String[][] sourceDecimatedSha256BySelector) {
		this.rootValid = rootValid;
		this.rootReason = rootReason == null ? "" : rootReason;
		this.propertiesFile = propertiesFile;
		this.propertiesSha256 = propertiesSha256 == null ? "" : propertiesSha256;
		this.manifestSha256 = manifestSha256 == null ? "" : manifestSha256;
		this.expectedPackSha256 = expectedPackSha256 == null ? "" : expectedPackSha256;
		this.framesBySelector = framesBySelector == null ? new Sprite[0][] : framesBySelector;
		this.styleBySelector = styleBySelector == null ? new String[0] : styleBySelector;
		this.rejectionBySelector = rejectionBySelector == null
			? new String[0] : rejectionBySelector;
		this.sourceDecimatedSha256BySelector = sourceDecimatedSha256BySelector == null
			? new String[0][] : sourceDecimatedSha256BySelector;
	}

	public static PaperdollV2LegacyCompatibility unavailable(String configuredPath,
		String reason) {
		File file = configuredPath == null || configuredPath.length() == 0
			? null : new File(configuredPath).getAbsoluteFile();
		return new PaperdollV2LegacyCompatibility(false, reason, file, "", "", "",
			null, null, null, null);
	}

	/** Reads one explicit compiler output below the repository {@code tmp/} tree. */
	public static PaperdollV2LegacyCompatibility read(File configuredFile,
		File repositoryRoot) throws IOException {
		if (configuredFile == null || repositoryRoot == null) {
			throw new IOException("legacy compatibility requires explicit file and repository root");
		}
		File repository = repositoryRoot.getCanonicalFile();
		File tmpRoot = new File(repository, "tmp").getCanonicalFile();
		File properties = requireCanonicalInput(configuredFile, tmpRoot, FILE_NAME,
			"legacy compatibility runtime properties");
		File compatibilityRoot = properties.getParentFile();
		File buildRoot = compatibilityRoot == null ? null : compatibilityRoot.getParentFile();
		File workspaceRoot = buildRoot == null ? null : buildRoot.getParentFile();
		if (compatibilityRoot == null || buildRoot == null || workspaceRoot == null
			|| !"legacy-compatibility".equals(compatibilityRoot.getName())
			|| !"build".equals(buildRoot.getName())) {
			throw new IOException("legacy compatibility must be workspace/build/legacy-compatibility/runtime.properties");
		}
		requireDescendant(tmpRoot, workspaceRoot, "legacy compatibility workspace");
		requireNoSymlinks(tmpRoot, properties, "legacy compatibility runtime properties");

		byte[] propertyBytes = readFileBounded(properties, MAX_PROPERTIES_BYTES);
		Map<String, String> values = parseCanonicalProperties(propertyBytes,
			"legacy compatibility runtime properties");
		List<Integer> selectorIds = requireSelectorIds(values);
		Set<String> expectedKeys = expectedKeys(selectorIds);
		if (!values.keySet().equals(expectedKeys)) {
			Set<String> missing = new TreeSet<>(expectedKeys);
			missing.removeAll(values.keySet());
			Set<String> unknown = new TreeSet<>(values.keySet());
			unknown.removeAll(expectedKeys);
			throw new IOException("legacy compatibility key set mismatch: missing=" + missing
				+ " unknown=" + unknown);
		}

		validateGlobalContract(values, selectorIds.size());
		String manifestSha256 = requireSha256(values, "manifest.sha256");
		String expectedPackSha256 = requireSha256(values, "pack.sha256");
		File manifest = bindFile(workspaceRoot, values, "manifest.path", "manifest.sha256",
			"build/legacy-compatibility/manifest.json", MAX_MANIFEST_BYTES);
		if (!manifest.getParentFile().equals(compatibilityRoot)) {
			throw new IOException("legacy compatibility manifest is not a runtime-properties sibling");
		}
		File catalog = bindFile(repository, values, "catalog.path", "catalog.sha256",
			"content/appearance/v2/catalog.yaml", MAX_MANIFEST_BYTES);
		File template = bindFile(repository, values, "template.path", "template.sha256",
			"content/appearance/templates/rsc-player-2x-v1/template.yaml", MAX_MANIFEST_BYTES);
		File selectorJson = bindFile(workspaceRoot, values, "selector_registry.json.path",
			"selector_registry.json.sha256", "build/selector-registry.json", MAX_SELECTOR_BYTES);
		File selectorProperties = bindFile(workspaceRoot, values,
			"selector_registry.properties.path", "selector_registry.properties.sha256",
			"build/selector-registry.properties", MAX_SELECTOR_BYTES);
		// Keep strong references in this scope so the bindings above cannot be optimized
		// into path-only checks by a future refactor.
		if (catalog.length() <= 0 || template.length() <= 0 || selectorJson.length() <= 0
			|| manifest.length() <= 0) {
			throw new IOException("legacy compatibility provenance file is empty");
		}

		Map<String, String> selectorValues = parseCanonicalProperties(
			readFileBounded(selectorProperties, MAX_SELECTOR_BYTES), "selector registry");
		validateSelectorProperties(selectorValues, values, selectorIds);
		Object selectorJsonContract = PaperdollV2StrictJson.parse(
			readFileBounded(selectorJson, MAX_SELECTOR_BYTES), "selector registry");
		validateSelectorJson(selectorJsonContract, values, selectorValues, selectorIds);
		Object manifestContract = PaperdollV2StrictJson.parse(
			readFileBounded(manifest, MAX_MANIFEST_BYTES), "legacy compatibility manifest");
		validateManifestJson(manifestContract, values, selectorIds);

		File pack = new File(workspaceRoot, requireExact(values, "pack.path",
			"build/Paperdoll_V2.orsc"));
		if (pack.exists()) {
			requireNoSymlinks(workspaceRoot, pack.getAbsoluteFile().toPath().normalize().toFile(),
				"legacy compatibility pack");
		}

		int maximumSelector = selectorIds.get(selectorIds.size() - 1);
		Sprite[][] frames = new Sprite[maximumSelector + 1][];
		String[] styles = new String[maximumSelector + 1];
		String[] rejections = new String[maximumSelector + 1];
		String[][] sourceDecimatedSha256 = new String[maximumSelector + 1][];
		HashSet<String> uniqueStyles = new HashSet<>();

		// A symlink is a root trust violation, not an individual corrupt frame.  Scan
		// every declared leaf before attempting style-local validation.
		for (int selectorId : selectorIds) {
			String style = requireId(values, "selector." + selectorId + ".style");
			if (!uniqueStyles.add(style)) {
				throw new IOException("legacy compatibility repeats style " + style);
			}
			styles[selectorId] = style;
			sourceDecimatedSha256[selectorId] = new String[FRAME_COUNT];
			for (int frame = 0; frame < FRAME_COUNT; frame++) {
				String prefix = framePrefix(selectorId, frame);
				sourceDecimatedSha256[selectorId][frame] = requireSha256(values,
					prefix + ".source_decimated_rgba.sha256");
				File png = new File(compatibilityRoot, values.get(prefix + ".png.path"));
				File sidecar = new File(compatibilityRoot, values.get(prefix + ".sidecar.path"));
				requireNoSymlinks(compatibilityRoot,
					png.getAbsoluteFile().toPath().normalize().toFile(),
					"legacy compatibility PNG");
				requireNoSymlinks(compatibilityRoot,
					sidecar.getAbsoluteFile().toPath().normalize().toFile(),
					"legacy compatibility sidecar");
			}
		}

		for (int selectorId : selectorIds) {
			try {
				frames[selectorId] = loadStyle(compatibilityRoot, values, selectorId,
					styles[selectorId]);
			} catch (IOException error) {
				frames[selectorId] = null;
				rejections[selectorId] = safeMessage(error);
			}
		}
		return new PaperdollV2LegacyCompatibility(true, "", properties,
			sha256(propertyBytes), manifestSha256, expectedPackSha256, frames, styles, rejections,
			sourceDecimatedSha256);
	}

	private static void validateGlobalContract(Map<String, String> values, int selectorCount)
		throws IOException {
		requireExact(values, "schema", SCHEMA);
		requireExact(values, "shipping", "false");
		requireExact(values, "activation.approved", "false");
		requireExact(values, "default.enabled", "false");
		requireExact(values, "output.scope", "workspace-build-only");
		requireExact(values, "runtime.platforms", "pc-workbench");
		requireInt(values, "runtime.compatible_head_appearance_id",
			COMPATIBLE_HEAD_APPEARANCE_ID);
		requireExact(values, "runtime.hat.allowed_appearance_ids", "0");
		requireExact(values, "runtime.hat.nonzero.action", "suppress-overlay");
		requireExact(values, "runtime.style_failure_policy", "reject-whole-style");
		requireExact(values, "runtime.write.client_cache", "false");
		requireExact(values, "runtime.write.max_selectors", "false");
		requireExact(values, "runtime.write.server_avatars", "false");
		requireExact(values, "manifest.schema",
			"voidscape-paperdoll-v2-legacy-compatibility/v1");
		requireExact(values, "downscale.algorithm", "explicit-nearest-2-to-1");
		requireExact(values, "downscale.sample_phase", "0,0");
		requireExact(values, "downscale.post_process", "retain-largest-4-connected");
		requireInt(values, "downscale.max_detached_pixels_removed_per_frame", 1);
		requireInt(values, "frame.count", FRAME_COUNT);
		requireExact(values, "sidecar.keys",
			"requiresShift,something1,something2,xShift,yShift");
		requireInt(values, "selector.count", selectorCount);
		requireInt(values, "overlay.frame_count", selectorCount * FRAME_COUNT);
		requireInt(values, "overlay.file_count", selectorCount * FRAME_COUNT * 2);
		requireInt(values, "avatar.thumbnail_count", (selectorCount + 1) * 2);
		requireExact(values, "avatar.role", "qa-thumbnail-non-runtime");
	}

	private static void validateSelectorProperties(Map<String, String> selector,
		Map<String, String> runtime, List<Integer> selectorIds) throws IOException {
		Set<String> expected = new HashSet<>();
		Collections.addAll(expected, "schema", "default.enabled", "catalog.path",
			"catalog.sha256", "template.sha256", "template.source_v1.sha256",
			"template.derived_masks.sha256", "pack.path", "pack.sha256",
			"namespace.field", "namespace.classic_id", "namespace.minimum",
			"namespace.maximum", "namespace.stability_policy", "base.ids",
			"selector.ids", "selector.unknown.route", "hat.v2_allowed_ids",
			"hat.nonzero.route", "hat.unknown.route");
		for (String profile : new String[] {"male", "female"}) {
			Collections.addAll(expected, "base." + profile + ".gender",
				"base." + profile + ".head_appearance_id",
				"base." + profile + ".body_appearance_id",
				"base." + profile + ".legs_appearance_id");
		}
		ArrayList<Integer> allIds = new ArrayList<>();
		allIds.add(0);
		allIds.addAll(selectorIds);
		for (int selectorId : allIds) {
			String prefix = "selector." + selectorId;
			Collections.addAll(expected, prefix + ".style", prefix + ".route",
				prefix + ".base_profiles", prefix + ".eligibility.state",
				prefix + ".eligibility.platforms", prefix + ".qa_control.male",
				prefix + ".qa_control.female");
			if (selectorId > 0) {
				expected.add(prefix + ".stack.male");
				expected.add(prefix + ".stack.female");
			}
		}
		if (!selector.keySet().equals(expected)) {
			throw new IOException("selector registry semantic key set differs from runtime contract");
		}
		requireExact(selector, "schema",
			"voidscape-paperdoll-v2-selector-registry-properties/v1");
		requireExact(selector, "default.enabled", "false");
		requireExact(selector, "catalog.path", requireExact(runtime, "catalog.path",
			"content/appearance/v2/catalog.yaml"));
		requireExact(selector, "catalog.sha256", requireSha256(runtime, "catalog.sha256"));
		requireExact(selector, "template.sha256", requireSha256(runtime, "template.sha256"));
		requireExact(selector, "template.source_v1.sha256",
			requireSha256(runtime, "template.source_v1.sha256"));
		requireExact(selector, "template.derived_masks.sha256",
			requireSha256(runtime, "template.derived_masks.sha256"));
		requireExact(selector, "pack.path", requireExact(runtime, "pack.path",
			"build/Paperdoll_V2.orsc"));
		requireExact(selector, "pack.sha256", requireSha256(runtime, "pack.sha256"));
		requireExact(selector, "namespace.field", "hairStyle");
		requireExact(selector, "namespace.classic_id", "0");
		requireExact(selector, "namespace.minimum", "0");
		requireExact(selector, "namespace.maximum", "255");
		requireExact(selector, "namespace.stability_policy", "never-reuse");
		requireExact(selector, "base.ids", "male,female");
		requireExact(selector, "base.male.gender", "male");
		requireExact(selector, "base.female.gender", "female");
		requireExact(selector, "base.male.head_appearance_id", "8");
		requireExact(selector, "base.male.body_appearance_id", "2");
		requireExact(selector, "base.male.legs_appearance_id", "3");
		requireExact(selector, "base.female.head_appearance_id", "8");
		requireExact(selector, "base.female.body_appearance_id", "5");
		requireExact(selector, "base.female.legs_appearance_id", "3");
		requireExact(selector, "selector.ids", "0," + runtime.get("selector.ids"));
		requireExact(selector, "selector.unknown.route", "legacy");
		requireExact(selector, "hat.v2_allowed_ids", "0");
		requireExact(selector, "hat.nonzero.route", "legacy");
		requireExact(selector, "hat.unknown.route", "legacy");
		validateSelectorPropertyEntry(selector, runtime, 0, "classic", "legacy");
		for (int selectorId : selectorIds) {
			String style = requireId(runtime, "selector." + selectorId + ".style");
			validateSelectorPropertyEntry(selector, runtime, selectorId, style, "v2");
		}
	}

	private static void validateSelectorPropertyEntry(Map<String, String> selector,
		Map<String, String> runtime, int selectorId, String style, String route) throws IOException {
		String prefix = "selector." + selectorId;
		requireExact(selector, prefix + ".style", style);
		requireExact(selector, prefix + ".route", route);
		requireExact(selector, prefix + ".base_profiles", "male,female");
		requireExact(selector, prefix + ".qa_control.male", "male_control");
		requireExact(selector, prefix + ".qa_control.female", "female_control");
		if (selectorId == 0) {
			requireExact(selector, prefix + ".eligibility.state", "shipping");
			requireExact(selector, prefix + ".eligibility.platforms", "pc,android,teavm");
			return;
		}
		requireExact(selector, prefix + ".eligibility.platforms", "pc");
		String state = selector.get(prefix + ".eligibility.state");
		if (!"qa-ready".equals(state) && !"proof-only".equals(state)) {
			throw new IOException(prefix + " eligibility state is not evaluation-only");
		}
		requireExact(selector, prefix + ".stack.male", "male_" + style);
		requireExact(selector, prefix + ".stack.female", "female_" + style);
		requireExact(runtime, prefix + ".asset", "hair_" + style);
	}

	private static void validateSelectorJson(Object contract, Map<String, String> runtime,
		Map<String, String> selector, List<Integer> selectorIds) throws IOException {
		Map<String, Object> root = jsonObject(contract, "selector registry root");
		requireJsonKeys(root, "selector registry root", "baseProfiles", "catalog",
			"defaultEnabled", "entries", "hatOcclusionPolicy", "namespace", "pack",
			"renderStacks", "schema", "template");
		requireJsonString(root, "schema", "voidscape-paperdoll-v2-selector-registry/v1");
		requireJsonBoolean(root, "defaultEnabled", false);
		validateJsonPathDigest(root.get("catalog"), "selector catalog",
			runtime.get("catalog.path"), runtime.get("catalog.sha256"), null, null);
		validateJsonPathDigest(root.get("pack"), "selector pack",
			runtime.get("pack.path"), runtime.get("pack.sha256"), null, null);
		validateJsonPathDigest(root.get("template"), "selector template",
			runtime.get("template.path"), runtime.get("template.sha256"),
			runtime.get("template.source_v1.sha256"),
			runtime.get("template.derived_masks.sha256"));

		Map<String, Object> namespace = jsonObject(root.get("namespace"), "selector namespace");
		requireJsonKeys(namespace, "selector namespace", "classicId", "field", "maximum",
			"minimum", "stabilityPolicy");
		requireJsonInt(namespace, "classicId", 0);
		requireJsonString(namespace, "field", "hairStyle");
		requireJsonInt(namespace, "minimum", 0);
		requireJsonInt(namespace, "maximum", 255);
		requireJsonString(namespace, "stabilityPolicy", "never-reuse");
		validateSelectorJsonBaseProfiles(root.get("baseProfiles"));
		validateSelectorJsonHatPolicy(root.get("hatOcclusionPolicy"));

		Map<String, Map<String, Object>> stacks = validateSelectorJsonStacks(
			root.get("renderStacks"));
		HashSet<String> expectedStackIds = new HashSet<>();
		Collections.addAll(expectedStackIds, "male_control", "male_native_base",
			"female_control", "female_native_base");
		List<Object> entries = jsonArray(root.get("entries"), "selector entries");
		if (entries.size() != selectorIds.size() + 1) {
			throw new IOException("selector JSON entry count differs from runtime selectors");
		}
		for (int index = 0; index < entries.size(); index++) {
			int selectorId = index;
			Map<String, Object> entry = jsonObject(entries.get(index),
				"selector entry " + selectorId);
			requireJsonKeys(entry, "selector entry " + selectorId, "baseProfiles",
				"eligibility", "label", "qaControlStackByBase", "route", "selectorId",
				"stackByBase", "style");
			requireJsonInt(entry, "selectorId", selectorId);
			String style = selectorId == 0 ? "classic"
				: runtime.get("selector." + selectorId + ".style");
			String route = selectorId == 0 ? "legacy" : "v2";
			requireJsonString(entry, "style", style);
			requireJsonString(entry, "route", route);
			jsonString(entry.get("label"), "selector label");
			requireJsonStringList(entry.get("baseProfiles"), "selector base profiles",
				"male", "female");
			Map<String, Object> controls = jsonObject(entry.get("qaControlStackByBase"),
				"selector QA controls");
			requireJsonKeys(controls, "selector QA controls", "female", "male");
			requireJsonString(controls, "male", selector.get("selector." + selectorId
				+ ".qa_control.male"));
			requireJsonString(controls, "female", selector.get("selector." + selectorId
				+ ".qa_control.female"));
			validateSelectorJsonEligibility(entry.get("eligibility"), selector, selectorId);
			Map<String, Object> stackByBase = jsonObject(entry.get("stackByBase"),
				"selector stack mapping");
			if (selectorId == 0) {
				requireJsonKeys(stackByBase, "Classic selector stack mapping");
			} else {
				requireJsonKeys(stackByBase, "selector stack mapping", "female", "male");
				String male = selector.get("selector." + selectorId + ".stack.male");
				String female = selector.get("selector." + selectorId + ".stack.female");
				expectedStackIds.add(male);
				expectedStackIds.add(female);
				requireJsonString(stackByBase, "male", male);
				requireJsonString(stackByBase, "female", female);
				validateMappedStack(stacks.get(male), male, "hair_" + style,
					"male_native_legs", "male_native_body", "male_native_head");
				validateMappedStack(stacks.get(female), female, "hair_" + style,
					"female_native_legs", "female_native_body", "female_native_head");
			}
		}
		if (!stacks.keySet().equals(expectedStackIds)) {
			throw new IOException("selector JSON render-stack set differs from selector mappings");
		}
		validateExactStack(stacks.get("male_control"), "male_control", "pack-only",
			"male_legacy_legs", "male_legacy_body", "male_legacy_head");
		validateExactStack(stacks.get("female_control"), "female_control", "pack-only",
			"female_legacy_legs", "female_legacy_body", "female_legacy_head");
		validateExactStack(stacks.get("male_native_base"), "male_native_base", "live-controls",
			"male_native_legs", "male_native_body", "male_native_head");
		validateExactStack(stacks.get("female_native_base"), "female_native_base", "live-controls",
			"female_native_legs", "female_native_body", "female_native_head");
	}

	private static void validateSelectorJsonBaseProfiles(Object value) throws IOException {
		List<Object> profiles = jsonArray(value, "selector base profiles");
		if (profiles.size() != 2) throw new IOException("selector JSON requires two base profiles");
		for (int index = 0; index < profiles.size(); index++) {
			String id = index == 0 ? "male" : "female";
			Map<String, Object> profile = jsonObject(profiles.get(index), "base profile " + id);
			requireJsonKeys(profile, "base profile " + id, "assets", "eligibility", "gender",
				"id", "label", "legacyIdentity");
			requireJsonString(profile, "id", id);
			requireJsonString(profile, "gender", id);
			jsonString(profile.get("label"), "base profile label");
			Map<String, Object> identity = jsonObject(profile.get("legacyIdentity"),
				"base profile legacy identity");
			requireJsonKeys(identity, "base profile legacy identity", "bodyAppearanceId",
				"headAppearanceId", "legsAppearanceId");
			requireJsonInt(identity, "headAppearanceId", 8);
			requireJsonInt(identity, "bodyAppearanceId", "male".equals(id) ? 2 : 5);
			requireJsonInt(identity, "legsAppearanceId", 3);
			Map<String, Object> assets = jsonObject(profile.get("assets"), "base profile assets");
			requireJsonKeys(assets, "base profile assets", "controlBody", "controlHead",
				"controlLegs", "nativeBody", "nativeHead", "nativeLegs");
			for (Object asset : assets.values()) requireJsonId(asset, "base profile asset");
			requireJsonString(assets, "controlBody", id + "_legacy_body");
			requireJsonString(assets, "controlHead", id + "_legacy_head");
			requireJsonString(assets, "controlLegs", id + "_legacy_legs");
			requireJsonString(assets, "nativeBody", id + "_native_body");
			requireJsonString(assets, "nativeHead", id + "_native_head");
			requireJsonString(assets, "nativeLegs", id + "_native_legs");
			Map<String, Object> eligibility = jsonObject(profile.get("eligibility"),
				"base profile eligibility");
			requireJsonKeys(eligibility, "base profile eligibility", "platforms", "reasons",
				"state");
			requireJsonStringList(eligibility.get("platforms"), "base profile platforms", "pc");
			requireJsonString(eligibility, "state", "qa-ready");
			requireStringArray(eligibility.get("reasons"), "base profile reasons");
		}
	}

	private static void validateSelectorJsonHatPolicy(Object value) throws IOException {
		Map<String, Object> policy = jsonObject(value, "hat occlusion policy");
		requireJsonKeys(policy, "hat occlusion policy", "rules", "strategy");
		requireJsonString(policy, "strategy", "explicit-first-match");
		List<Object> rules = jsonArray(policy.get("rules"), "hat occlusion rules");
		if (rules.size() != 2) throw new IOException("hat occlusion policy requires two rules");
		Map<String, Object> none = jsonObject(rules.get(0), "no-hat rule");
		requireJsonKeys(none, "no-hat rule", "action", "id", "match", "reason");
		requireJsonString(none, "action", "allow-v2");
		requireJsonString(none, "id", "no_hat");
		jsonString(none.get("reason"), "no-hat reason");
		Map<String, Object> noneMatch = jsonObject(none.get("match"), "no-hat match");
		requireJsonKeys(noneMatch, "no-hat match", "hatAppearanceIds");
		requireJsonIntList(noneMatch.get("hatAppearanceIds"), "no-hat ids", 0);
		Map<String, Object> other = jsonObject(rules.get(1), "nonzero-hat rule");
		requireJsonKeys(other, "nonzero-hat rule", "action", "id", "match", "reason");
		requireJsonString(other, "action", "fallback-legacy");
		requireJsonString(other, "id", "unsupported_hat");
		jsonString(other.get("reason"), "nonzero-hat reason");
		Map<String, Object> otherMatch = jsonObject(other.get("match"), "nonzero-hat match");
		requireJsonKeys(otherMatch, "nonzero-hat match", "anyNonzeroHat");
		requireJsonBoolean(otherMatch, "anyNonzeroHat", true);
	}

	private static Map<String, Map<String, Object>> validateSelectorJsonStacks(Object value)
		throws IOException {
		List<Object> encoded = jsonArray(value, "selector render stacks");
		LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
		for (Object item : encoded) {
			Map<String, Object> stack = jsonObject(item, "selector render stack");
			requireJsonKeys(stack, "selector render stack", "assets", "id", "label", "mode");
			String id = requireJsonId(stack.get("id"), "selector render stack id");
			if (result.put(id, stack) != null) throw new IOException("selector JSON repeats stack " + id);
			String mode = jsonString(stack.get("mode"), "selector render stack mode");
			if (!"pack-only".equals(mode) && !"live-controls".equals(mode)) {
				throw new IOException("selector render stack mode is unsupported");
			}
			jsonString(stack.get("label"), "selector render stack label");
			List<Object> assets = jsonArray(stack.get("assets"), "selector render stack assets");
			if (assets.isEmpty()) throw new IOException("selector render stack has no assets");
			HashSet<String> unique = new HashSet<>();
			for (Object asset : assets) {
				String assetId = requireJsonId(asset, "selector render stack asset");
				if (!unique.add(assetId)) throw new IOException("selector render stack repeats an asset");
			}
		}
		return result;
	}

	private static void validateMappedStack(Map<String, Object> stack, String id, String hair,
		String legs, String body, String head) throws IOException {
		if (stack == null) throw new IOException("selector JSON lacks mapped stack " + id);
		requireJsonString(stack, "mode", "live-controls");
		requireJsonStringList(stack.get("assets"), "mapped stack assets", legs, body, head, hair);
	}

	private static void validateExactStack(Map<String, Object> stack, String id, String mode,
		String... assets) throws IOException {
		if (stack == null) throw new IOException("selector JSON lacks stack " + id);
		requireJsonString(stack, "id", id);
		requireJsonString(stack, "mode", mode);
		requireJsonStringList(stack.get("assets"), id + " assets", assets);
	}

	private static void validateSelectorJsonEligibility(Object value,
		Map<String, String> selector, int selectorId) throws IOException {
		Map<String, Object> eligibility = jsonObject(value, "selector eligibility");
		if (selectorId == 0) {
			requireJsonKeys(eligibility, "Classic selector eligibility", "platforms", "reasons",
				"state");
			requireJsonStringList(eligibility.get("platforms"), "Classic selector platforms",
				"pc", "android", "teavm");
		} else {
			requireJsonKeys(eligibility, "selector eligibility", "baseProfiles", "platforms",
				"reasons", "state");
			requireJsonStringList(eligibility.get("baseProfiles"), "eligible base profiles",
				"male", "female");
			requireJsonStringList(eligibility.get("platforms"), "selector platforms", "pc");
		}
		requireJsonString(eligibility, "state",
			selector.get("selector." + selectorId + ".eligibility.state"));
		requireStringArray(eligibility.get("reasons"), "selector eligibility reasons");
	}

	private static void validateManifestJson(Object contract, Map<String, String> runtime,
		List<Integer> selectorIds) throws IOException {
		Map<String, Object> root = jsonObject(contract, "legacy compatibility manifest root");
		requireJsonKeys(root, "legacy compatibility manifest root", "activationApproved",
			"artifacts", "avatarContract", "avatarThumbnailCount", "avatars", "catalog",
			"detachedPixelsRemoved", "downscale", "outputScope", "overlayFrameCount",
			"overlays", "pack", "runtimeContract", "runtimeFallbackContract", "runtimeWrites",
			"schema", "selectorRegistry", "shipping", "template");
		requireJsonString(root, "schema", "voidscape-paperdoll-v2-legacy-compatibility/v1");
		requireJsonBoolean(root, "shipping", false);
		requireJsonBoolean(root, "activationApproved", false);
		requireJsonString(root, "outputScope", "workspace-build-only");
		validateJsonPathDigest(root.get("catalog"), "manifest catalog",
			runtime.get("catalog.path"), runtime.get("catalog.sha256"), null, null);
		validateJsonPathDigest(root.get("pack"), "manifest pack",
			runtime.get("pack.path"), runtime.get("pack.sha256"), null, null);
		validateJsonPathDigest(root.get("template"), "manifest template",
			runtime.get("template.path"), runtime.get("template.sha256"),
			runtime.get("template.source_v1.sha256"),
			runtime.get("template.derived_masks.sha256"));

		Map<String, Object> selectorRegistry = jsonObject(root.get("selectorRegistry"),
			"manifest selector registry");
		requireJsonKeys(selectorRegistry, "manifest selector registry", "jsonSha256",
			"propertiesSha256");
		requireJsonString(selectorRegistry, "jsonSha256",
			runtime.get("selector_registry.json.sha256"));
		requireJsonString(selectorRegistry, "propertiesSha256",
			runtime.get("selector_registry.properties.sha256"));
		Map<String, Object> runtimeContract = jsonObject(root.get("runtimeContract"),
			"manifest runtime contract");
		requireJsonKeys(runtimeContract, "manifest runtime contract", "path", "schema");
		requireJsonString(runtimeContract, "path", "build/legacy-compatibility/runtime.properties");
		requireJsonString(runtimeContract, "schema", SCHEMA);
		validateManifestFallback(root.get("runtimeFallbackContract"));
		Map<String, Object> writes = jsonObject(root.get("runtimeWrites"), "manifest runtime writes");
		requireJsonKeys(writes, "manifest runtime writes", "clientCache", "maxSelectors",
			"serverAvatars");
		requireJsonBoolean(writes, "clientCache", false);
		requireJsonBoolean(writes, "maxSelectors", false);
		requireJsonBoolean(writes, "serverAvatars", false);
		validateManifestDownscale(root.get("downscale"));

		int expectedFrames = selectorIds.size() * FRAME_COUNT;
		requireJsonInt(root, "overlayFrameCount", expectedFrames);
		requireJsonInt(root, "avatarThumbnailCount", (selectorIds.size() + 1) * 2);
		LinkedHashMap<String, String> expectedArtifacts = new LinkedHashMap<>();
		List<Object> overlays = jsonArray(root.get("overlays"), "manifest overlays");
		if (overlays.size() != selectorIds.size()) {
			throw new IOException("manifest overlay count differs from runtime selectors");
		}
		int detachedTotal = 0;
		for (int index = 0; index < overlays.size(); index++) {
			int selectorId = selectorIds.get(index);
			detachedTotal += validateManifestOverlay(overlays.get(index), runtime,
				selectorId, expectedArtifacts);
		}
		requireJsonInt(root, "detachedPixelsRemoved", detachedTotal);
		validateManifestAvatars(root.get("avatarContract"), root.get("avatars"), runtime,
			selectorIds, expectedArtifacts);
		validateManifestArtifacts(root.get("artifacts"), expectedArtifacts);
	}

	private static int validateManifestOverlay(Object encoded, Map<String, String> runtime,
		int selectorId, Map<String, String> artifacts) throws IOException {
		String selectorPrefix = "selector." + selectorId;
		Map<String, Object> overlay = jsonObject(encoded, "manifest overlay " + selectorId);
		requireJsonKeys(overlay, "manifest overlay", "asset", "frames", "path", "selectorId",
			"style");
		requireJsonInt(overlay, "selectorId", selectorId);
		requireJsonString(overlay, "style", runtime.get(selectorPrefix + ".style"));
		requireJsonString(overlay, "asset", runtime.get(selectorPrefix + ".asset"));
		requireJsonString(overlay, "path", runtime.get(selectorPrefix + ".path"));
		List<Object> frames = jsonArray(overlay.get("frames"), "manifest overlay frames");
		if (frames.size() != FRAME_COUNT) throw new IOException("manifest overlay has partial frames");
		int detachedTotal = 0;
		for (int frame = 0; frame < FRAME_COUNT; frame++) {
			String prefix = framePrefix(selectorId, frame);
			Map<String, Object> item = jsonObject(frames.get(frame), "manifest frame " + prefix);
			requireJsonKeys(item, "manifest frame", "crop", "detachedPixelsRemoved",
				"fourConnectedComponents", "logicalRgbaSha256", "logicalSize", "offset", "png",
				"pngSha256", "properties", "propertiesSha256", "requiresShift",
				"sourceDecimatedRgbaSha256");
			requireJsonInt(item, "offset", frame);
			requireJsonString(item, "png", runtime.get(prefix + ".png.path"));
			requireJsonString(item, "pngSha256", runtime.get(prefix + ".png.sha256"));
			requireJsonString(item, "properties", runtime.get(prefix + ".sidecar.path"));
			requireJsonString(item, "propertiesSha256", runtime.get(prefix + ".sidecar.sha256"));
			requireExact(runtime, prefix + ".sidecar.requires_shift", "true");
			requireJsonBoolean(item, "requiresShift", true);
			int components = requireInt(runtime, prefix + ".four_connected_components");
			if (components != 1) {
				throw new IOException("runtime component count differs at " + prefix);
			}
			requireJsonInt(item, "fourConnectedComponents", components);
			int detached = jsonInt(item.get("detachedPixelsRemoved"), "manifest detached pixels");
			if (detached != requireInt(runtime, prefix + ".detached_pixels_removed")) {
				throw new IOException("manifest detached-pixel count differs at " + prefix);
			}
			detachedTotal += detached;
			requireJsonString(item, "logicalRgbaSha256",
				runtime.get(prefix + ".logical_rgba.sha256"));
			requireJsonString(item, "sourceDecimatedRgbaSha256",
				runtime.get(prefix + ".source_decimated_rgba.sha256"));
			requireJsonIntList(item.get("logicalSize"), "manifest logical size",
				requireInt(runtime, prefix + ".logical.width"),
				requireInt(runtime, prefix + ".logical.height"));
			Map<String, Object> crop = jsonObject(item.get("crop"), "manifest frame crop");
			requireJsonKeys(crop, "manifest frame crop", "height", "width", "x", "y");
			requireJsonInt(crop, "x", requireInt(runtime, prefix + ".crop.x"));
			requireJsonInt(crop, "y", requireInt(runtime, prefix + ".crop.y"));
			requireJsonInt(crop, "width", requireInt(runtime, prefix + ".crop.width"));
			requireJsonInt(crop, "height", requireInt(runtime, prefix + ".crop.height"));
			putArtifact(artifacts, runtime.get(prefix + ".png.path"),
				runtime.get(prefix + ".png.sha256"));
			putArtifact(artifacts, runtime.get(prefix + ".sidecar.path"),
				runtime.get(prefix + ".sidecar.sha256"));
		}
		return detachedTotal;
	}

	private static void validateManifestFallback(Object value) throws IOException {
		Map<String, Object> fallback = jsonObject(value, "manifest fallback contract");
		requireJsonKeys(fallback, "manifest fallback contract", "compatibleHeadAppearanceId",
			"defaultEnabled", "frameCount", "hatAllowedAppearanceIds", "nonzeroHatAction",
			"platforms", "styleFailurePolicy");
		requireJsonInt(fallback, "compatibleHeadAppearanceId", 8);
		requireJsonBoolean(fallback, "defaultEnabled", false);
		requireJsonInt(fallback, "frameCount", FRAME_COUNT);
		requireJsonIntList(fallback.get("hatAllowedAppearanceIds"), "fallback hat ids", 0);
		requireJsonString(fallback, "nonzeroHatAction", "suppress-overlay");
		requireJsonStringList(fallback.get("platforms"), "fallback platforms", "pc-workbench");
		requireJsonString(fallback, "styleFailurePolicy", "reject-whole-style");
	}

	private static void validateManifestDownscale(Object value) throws IOException {
		Map<String, Object> downscale = jsonObject(value, "manifest downscale contract");
		requireJsonKeys(downscale, "manifest downscale contract", "algorithm", "postProcess",
			"samplePhase");
		requireJsonString(downscale, "algorithm", "explicit-nearest-2-to-1");
		requireJsonIntList(downscale.get("samplePhase"), "downscale phase", 0, 0);
		Map<String, Object> post = jsonObject(downscale.get("postProcess"), "downscale post process");
		requireJsonKeys(post, "downscale post process", "discardedComponentPixels",
			"maxDetachedPixelsRemovedPerFrame", "type");
		requireJsonInt(post, "discardedComponentPixels", 1);
		requireJsonInt(post, "maxDetachedPixelsRemovedPerFrame", 1);
		requireJsonString(post, "type", "retain-largest-4-connected");
	}

	private static void validateManifestAvatars(Object contractValue, Object avatarsValue,
		Map<String, String> runtime, List<Integer> selectorIds, Map<String, String> artifacts)
		throws IOException {
		Map<String, Object> contract = jsonObject(contractValue, "manifest avatar contract");
		requireJsonKeys(contract, "manifest avatar contract", "height", "regions", "role",
			"state", "width");
		requireJsonInt(contract, "width", 64);
		requireJsonInt(contract, "height", 102);
		requireJsonString(contract, "role", "qa-thumbnail-non-runtime");
		Map<String, Object> state = jsonObject(contract.get("state"), "avatar state");
		requireJsonKeys(state, "avatar state", "direction", "frame", "kind", "spriteOffset");
		requireJsonString(state, "direction", "north");
		requireJsonInt(state, "frame", 1);
		requireJsonString(state, "kind", "walk");
		requireJsonInt(state, "spriteOffset", 1);
		Map<String, Object> regions = jsonObject(contract.get("regions"), "avatar regions");
		requireJsonKeys(regions, "avatar regions", "body", "head", "legs");
		requireJsonIntList(regions.get("head"), "avatar head region", 0, 30);
		requireJsonIntList(regions.get("body"), "avatar body region", 30, 62);
		requireJsonIntList(regions.get("legs"), "avatar legs region", 62, 102);

		List<Object> avatars = jsonArray(avatarsValue, "manifest avatars");
		if (avatars.size() != (selectorIds.size() + 1) * 2) {
			throw new IOException("manifest avatar count differs from runtime contract");
		}
		int index = 0;
		for (int selectorId = 0; selectorId <= selectorIds.size(); selectorId++) {
			String style = selectorId == 0 ? "classic" : runtime.get("selector." + selectorId + ".style");
			for (String base : new String[] {"male", "female"}) {
				Map<String, Object> avatar = jsonObject(avatars.get(index++), "manifest avatar");
				requireJsonKeys(avatar, "manifest avatar", "baseProfile", "nonzeroRegionPixels",
					"path", "rawRgbaSha256", "role", "selectorId", "sha256", "style");
				requireJsonInt(avatar, "selectorId", selectorId);
				requireJsonString(avatar, "style", style);
				requireJsonString(avatar, "baseProfile", base);
				requireJsonString(avatar, "role", "qa-thumbnail-non-runtime");
				String path = jsonString(avatar.get("path"), "avatar path");
				String expectedPath = String.format("avatars/selector_%02d/%s.png", selectorId, base);
				if (!expectedPath.equals(path)) throw new IOException("manifest avatar path changed");
				String sha = requireJsonSha(avatar.get("sha256"), "avatar SHA-256");
				requireJsonSha(avatar.get("rawRgbaSha256"), "avatar raw RGBA SHA-256");
				Map<String, Object> counts = jsonObject(avatar.get("nonzeroRegionPixels"),
					"avatar region counts");
				requireJsonKeys(counts, "avatar region counts", "body", "head", "legs");
				for (String region : new String[] {"head", "body", "legs"}) {
					if (jsonInt(counts.get(region), "avatar region count") <= 0) {
						throw new IOException("manifest avatar has an empty region");
					}
				}
				putArtifact(artifacts, path, sha);
			}
		}
	}

	private static void validateManifestArtifacts(Object value, Map<String, String> expected)
		throws IOException {
		List<Object> artifacts = jsonArray(value, "manifest artifacts");
		if (artifacts.size() != expected.size()) {
			throw new IOException("manifest artifact count differs from frames and avatars");
		}
		LinkedHashMap<String, String> actual = new LinkedHashMap<>();
		for (Object encoded : artifacts) {
			Map<String, Object> artifact = jsonObject(encoded, "manifest artifact");
			requireJsonKeys(artifact, "manifest artifact", "bytes", "path", "sha256");
			String path = jsonString(artifact.get("path"), "manifest artifact path");
			String sha = requireJsonSha(artifact.get("sha256"), "manifest artifact SHA-256");
			if (jsonInt(artifact.get("bytes"), "manifest artifact byte count") <= 0) {
				throw new IOException("manifest artifact byte count is not positive");
			}
			if (actual.put(path, sha) != null) throw new IOException("manifest repeats artifact " + path);
		}
		if (!actual.equals(expected)) {
			throw new IOException("manifest artifact inventory differs from overlays and avatars");
		}
	}

	private static void putArtifact(Map<String, String> artifacts, String path, String sha)
		throws IOException {
		if (artifacts.put(path, sha) != null) throw new IOException("manifest repeats artifact " + path);
	}

	private static void validateJsonPathDigest(Object value, String label, String expectedPath,
		String expectedSha, String expectedSourceSha, String expectedDerivedMaskSha)
		throws IOException {
		Map<String, Object> object = jsonObject(value, label);
		if (expectedSourceSha != null || expectedDerivedMaskSha != null) {
			if (expectedSourceSha == null || expectedDerivedMaskSha == null) {
				throw new IOException(label + " has a partial template provenance binding");
			}
			requireJsonKeys(object, label, "derivedMaskTreeSha256", "path", "sha256",
				"sourceV1Sha256");
			requireJsonString(object, "derivedMaskTreeSha256",
				expectedDerivedMaskSha);
			requireJsonString(object, "sourceV1Sha256", expectedSourceSha);
		} else {
			requireJsonKeys(object, label, "path", "sha256");
		}
		requireJsonString(object, "path", expectedPath);
		requireJsonString(object, "sha256", expectedSha);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> jsonObject(Object value, String label) throws IOException {
		if (!(value instanceof Map)) throw new IOException(label + " must be a JSON object");
		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	private static List<Object> jsonArray(Object value, String label) throws IOException {
		if (!(value instanceof List)) throw new IOException(label + " must be a JSON array");
		return (List<Object>) value;
	}

	private static void requireJsonKeys(Map<String, Object> value, String label, String... keys)
		throws IOException {
		Set<String> expected = immutableSet(keys);
		if (!value.keySet().equals(expected)) throw new IOException(label + " key set changed");
	}

	private static String jsonString(Object value, String label) throws IOException {
		if (!(value instanceof String)) throw new IOException(label + " must be a JSON string");
		return (String) value;
	}

	private static void requireJsonString(Map<String, Object> value, String key, String expected)
		throws IOException {
		String actual = jsonString(value.get(key), key);
		if (!expected.equals(actual)) throw new IOException(key + " JSON value changed");
	}

	private static String requireJsonId(Object value, String label) throws IOException {
		String id = jsonString(value, label);
		if (!ID_PATTERN.matcher(id).matches()) throw new IOException(label + " is not a canonical id");
		return id;
	}

	private static String requireJsonSha(Object value, String label) throws IOException {
		String sha = jsonString(value, label);
		if (!SHA256_PATTERN.matcher(sha).matches()) throw new IOException(label + " is not SHA-256");
		return sha;
	}

	private static int jsonInt(Object value, String label) throws IOException {
		if (!(value instanceof Long)) throw new IOException(label + " must be a JSON integer");
		long encoded = (Long) value;
		if (encoded < Integer.MIN_VALUE || encoded > Integer.MAX_VALUE) {
			throw new IOException(label + " is outside Java integer range");
		}
		return (int) encoded;
	}

	private static void requireJsonInt(Map<String, Object> value, String key, int expected)
		throws IOException {
		if (jsonInt(value.get(key), key) != expected) throw new IOException(key + " JSON value changed");
	}

	private static void requireJsonBoolean(Map<String, Object> value, String key, boolean expected)
		throws IOException {
		Object actual = value.get(key);
		if (!(actual instanceof Boolean) || ((Boolean) actual) != expected) {
			throw new IOException(key + " JSON boolean changed");
		}
	}

	private static void requireJsonStringList(Object value, String label, String... expected)
		throws IOException {
		List<Object> actual = jsonArray(value, label);
		if (actual.size() != expected.length) throw new IOException(label + " length changed");
		for (int index = 0; index < expected.length; index++) {
			if (!expected[index].equals(jsonString(actual.get(index), label))) {
				throw new IOException(label + " ordering or value changed");
			}
		}
	}

	private static void requireJsonIntList(Object value, String label, int... expected)
		throws IOException {
		List<Object> actual = jsonArray(value, label);
		if (actual.size() != expected.length) throw new IOException(label + " length changed");
		for (int index = 0; index < expected.length; index++) {
			if (jsonInt(actual.get(index), label) != expected[index]) {
				throw new IOException(label + " ordering or value changed");
			}
		}
	}

	private static void requireStringArray(Object value, String label) throws IOException {
		for (Object item : jsonArray(value, label)) jsonString(item, label);
	}

	private static Sprite[] loadStyle(File compatibilityRoot, Map<String, String> values,
		int selectorId, String style) throws IOException {
		String selectorPrefix = "selector." + selectorId;
		requireExact(values, selectorPrefix + ".asset", "hair_" + style);
		requireExact(values, selectorPrefix + ".path",
			String.format("voidscape/hair/style_%02d", selectorId));
		requireInt(values, selectorPrefix + ".frame.count", FRAME_COUNT);
		requireExact(values, selectorPrefix + ".frame.ids",
			"00,01,02,03,04,05,06,07,08,09,10,11,12,13,14,15,16,17");

		Sprite[] pending = new Sprite[FRAME_COUNT];
		for (int frame = 0; frame < FRAME_COUNT; frame++) {
			String prefix = framePrefix(selectorId, frame);
			String expectedDirectory = String.format("voidscape/hair/style_%02d", selectorId);
			String expectedPng = expectedDirectory + String.format("/frame_%02d.png", frame);
			String expectedSidecar = expectedDirectory
				+ String.format("/frame_%02d.properties", frame);
			requireExact(values, prefix + ".png.path", expectedPng);
			requireExact(values, prefix + ".sidecar.path", expectedSidecar);
			requireExact(values, prefix + ".sidecar.requires_shift", "true");
			requireInt(values, prefix + ".four_connected_components", 1);
			int detached = requireInt(values, prefix + ".detached_pixels_removed");
			if (detached < 0 || detached > 1) {
				throw new IOException(prefix + " detached-pixel count is out of range");
			}
			int logicalWidth = requireInt(values, prefix + ".logical.width");
			int logicalHeight = requireInt(values, prefix + ".logical.height");
			if (logicalWidth != (frame >= 15 ? 84 : 64) || logicalHeight != 102) {
				throw new IOException(prefix + " logical geometry changed");
			}
			int cropX = requireInt(values, prefix + ".crop.x");
			int cropY = requireInt(values, prefix + ".crop.y");
			int cropWidth = requireInt(values, prefix + ".crop.width");
			int cropHeight = requireInt(values, prefix + ".crop.height");
			if (cropX < 0 || cropY < 0 || cropWidth <= 0 || cropHeight <= 0
				|| cropX + cropWidth > logicalWidth || cropY + cropHeight > logicalHeight) {
				throw new IOException(prefix + " crop escapes its logical canvas");
			}

			File png = requireStyleFile(compatibilityRoot, expectedPng, MAX_PNG_BYTES);
			File sidecar = requireStyleFile(compatibilityRoot, expectedSidecar, 4096);
			if (!sha256(readFileBounded(png, MAX_PNG_BYTES)).equals(
				requireSha256(values, prefix + ".png.sha256"))) {
				throw new IOException(prefix + " PNG SHA-256 changed");
			}
			byte[] sidecarBytes = readFileBounded(sidecar, 4096);
			if (!sha256(sidecarBytes).equals(requireSha256(values, prefix + ".sidecar.sha256"))) {
				throw new IOException(prefix + " sidecar SHA-256 changed");
			}
			Map<String, String> sidecarValues = parseCanonicalProperties(sidecarBytes,
				prefix + " sidecar");
			Set<String> sidecarKeys = immutableSet(
				"requiresShift", "something1", "something2", "xShift", "yShift");
			if (!sidecarValues.keySet().equals(sidecarKeys)) {
				throw new IOException(prefix + " sidecar key set changed");
			}
			requireExact(sidecarValues, "requiresShift", "true");
			requireInt(sidecarValues, "something1", logicalWidth);
			requireInt(sidecarValues, "something2", logicalHeight);
			requireInt(sidecarValues, "xShift", cropX);
			requireInt(sidecarValues, "yShift", cropY);

			Sprite sprite = PngSpriteLoader.readArgb(png);
			if (sprite == null || sprite.getWidth() != cropWidth || sprite.getHeight() != cropHeight
				|| sprite.getPixels() == null
				|| sprite.getPixels().length != cropWidth * cropHeight) {
				throw new IOException(prefix + " PNG decode or crop geometry changed");
			}
			validatePixels(sprite, prefix);
			String logicalSha = logicalRgbaSha256(sprite, cropX, cropY,
				logicalWidth, logicalHeight);
			if (!logicalSha.equals(requireSha256(values, prefix + ".logical_rgba.sha256"))) {
				throw new IOException(prefix + " logical RGBA SHA-256 changed");
			}
			requireSha256(values, prefix + ".source_decimated_rgba.sha256");
			sprite.setRequiresShift(true);
			sprite.setShift(cropX, cropY);
			sprite.setSomething(logicalWidth, logicalHeight);
			pending[frame] = sprite;
		}
		return pending;
	}

	private static void validatePixels(Sprite sprite, String label) throws IOException {
		int width = sprite.getWidth();
		int height = sprite.getHeight();
		boolean[] visible = new boolean[width * height];
		int visibleCount = 0;
		int minX = width, minY = height, maxX = -1, maxY = -1;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int argb = sprite.getPixels()[y * width + x];
				int alpha = argb >>> 24;
				if (alpha == 0) {
					if ((argb & 0x00ffffff) != 0) {
						throw new IOException(label + " has RGB data under transparent pixels");
					}
					continue;
				}
				int red = argb >> 16 & 255;
				int green = argb >> 8 & 255;
				int blue = argb & 255;
				if (red != green || green != blue) {
					throw new IOException(label + " contains non-neutral hair pixels");
				}
				visible[y * width + x] = true;
				visibleCount++;
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
			}
		}
		if (visibleCount == 0 || minX != 0 || minY != 0 || maxX != width - 1
			|| maxY != height - 1) {
			throw new IOException(label + " is empty or no longer tightly cropped");
		}
		int components = 0;
		int[] queue = new int[visible.length];
		for (int index = 0; index < visible.length; index++) {
			if (!visible[index]) continue;
			components++;
			if (components > 1) throw new IOException(label + " has detached components");
			int head = 0, tail = 0;
			queue[tail++] = index;
			visible[index] = false;
			while (head < tail) {
				int current = queue[head++];
				int x = current % width;
				int y = current / width;
				if (x > 0) tail = enqueue(visible, queue, tail, current - 1);
				if (x + 1 < width) tail = enqueue(visible, queue, tail, current + 1);
				if (y > 0) tail = enqueue(visible, queue, tail, current - width);
				if (y + 1 < height) tail = enqueue(visible, queue, tail, current + width);
			}
		}
	}

	private static int enqueue(boolean[] visible, int[] queue, int tail, int index) {
		if (visible[index]) {
			visible[index] = false;
			queue[tail++] = index;
		}
		return tail;
	}

	private static String logicalRgbaSha256(Sprite sprite, int cropX, int cropY,
		int logicalWidth, int logicalHeight) throws IOException {
		MessageDigest digest = sha256Digest();
		updateInt(digest, logicalWidth);
		updateInt(digest, logicalHeight);
		for (int y = 0; y < logicalHeight; y++) {
			for (int x = 0; x < logicalWidth; x++) {
				int argb = 0;
				int sourceX = x - cropX;
				int sourceY = y - cropY;
				if (sourceX >= 0 && sourceY >= 0 && sourceX < sprite.getWidth()
					&& sourceY < sprite.getHeight()) {
					argb = sprite.getPixels()[sourceY * sprite.getWidth() + sourceX];
				}
				digest.update((byte) (argb >> 16 & 255));
				digest.update((byte) (argb >> 8 & 255));
				digest.update((byte) (argb & 255));
				digest.update((byte) (argb >>> 24));
			}
		}
		return hex(digest.digest());
	}

	private static void updateInt(MessageDigest digest, int value) {
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}

	private static File bindFile(File root, Map<String, String> values, String pathKey,
		String shaKey, String expectedPath, int maximumBytes) throws IOException {
		requireExact(values, pathKey, expectedPath);
		File file = requireStyleFile(root, expectedPath, maximumBytes);
		String actual = sha256(readFileBounded(file, maximumBytes));
		if (!actual.equals(requireSha256(values, shaKey))) {
			throw new IOException("legacy compatibility " + pathKey + " SHA-256 changed");
		}
		return file;
	}

	private static File requireStyleFile(File root, String relativePath, int maximumBytes)
		throws IOException {
		if (relativePath == null || relativePath.startsWith("/") || relativePath.indexOf('\\') >= 0
			|| relativePath.contains("..") || relativePath.indexOf(':') >= 0) {
			throw new IOException("legacy compatibility path is unsafe: " + relativePath);
		}
		File unresolved = new File(root, relativePath).getAbsoluteFile().toPath().normalize().toFile();
		requireDescendant(root.getCanonicalFile(), unresolved, "legacy compatibility artifact");
		requireNoSymlinks(root.getCanonicalFile(), unresolved, "legacy compatibility artifact");
		File file = unresolved.getCanonicalFile();
		if (!file.isFile() || file.length() <= 0 || file.length() > maximumBytes) {
			throw new IOException("legacy compatibility artifact is missing or oversized: " + relativePath);
		}
		return file;
	}

	private static File requireCanonicalInput(File configured, File allowedRoot,
		String expectedName, String label) throws IOException {
		File absolute = configured.getAbsoluteFile();
		File canonical = configured.getCanonicalFile();
		if (!absolute.toPath().normalize().toFile().equals(canonical)) {
			throw new IOException(label + " path is non-canonical or symlinked");
		}
		requireDescendant(allowedRoot, canonical, label);
		if (!canonical.isFile() || !expectedName.equals(canonical.getName())) {
			throw new IOException(label + " must be an existing " + expectedName);
		}
		return canonical;
	}

	private static void requireDescendant(File root, File candidate, String label)
		throws IOException {
		String rootPath = root.getCanonicalPath();
		String candidatePath = candidate.getCanonicalPath();
		if (!candidatePath.startsWith(rootPath + File.separator)) {
			throw new IOException(label + " escapes its allowed root");
		}
	}

	private static void requireNoSymlinks(File root, File candidate, String label)
		throws IOException {
		File canonicalRoot = root.getCanonicalFile();
		File absoluteCandidate = candidate.getAbsoluteFile().toPath().normalize().toFile();
		requireDescendant(canonicalRoot, absoluteCandidate, label);
		java.nio.file.Path rootPath = canonicalRoot.toPath();
		java.nio.file.Path relative = rootPath.relativize(absoluteCandidate.toPath());
		java.nio.file.Path cursor = rootPath;
		for (java.nio.file.Path part : relative) {
			cursor = cursor.resolve(part);
			if (Files.isSymbolicLink(cursor)) {
				throw new IOException(label + " contains a symlink");
			}
		}
	}

	private static Set<String> expectedKeys(List<Integer> selectorIds) {
		HashSet<String> keys = new HashSet<>(GLOBAL_KEYS);
		for (int selectorId : selectorIds) {
			String selectorPrefix = "selector." + selectorId;
			Collections.addAll(keys, selectorPrefix + ".asset", selectorPrefix + ".path",
				selectorPrefix + ".style", selectorPrefix + ".frame.count",
				selectorPrefix + ".frame.ids");
			for (int frame = 0; frame < FRAME_COUNT; frame++) {
				String prefix = framePrefix(selectorId, frame);
				for (String suffix : FRAME_SUFFIXES) keys.add(prefix + "." + suffix);
			}
		}
		return keys;
	}

	private static String framePrefix(int selectorId, int frame) {
		return "selector." + selectorId + String.format(".frame.%02d", frame);
	}

	private static List<Integer> requireSelectorIds(Map<String, String> values)
		throws IOException {
		String encoded = values.get("selector.ids");
		if (encoded == null || encoded.length() == 0) {
			throw new IOException("legacy compatibility selector.ids is empty");
		}
		String[] parts = encoded.split(",", -1);
		ArrayList<Integer> result = new ArrayList<>();
		for (int index = 0; index < parts.length; index++) {
			int selectorId;
			try {
				selectorId = Integer.parseInt(parts[index]);
			} catch (NumberFormatException error) {
				throw new IOException("legacy compatibility selector.ids is not numeric", error);
			}
			if (!Integer.toString(selectorId).equals(parts[index]) || selectorId != index + 1
				|| selectorId > 255) {
				throw new IOException("legacy compatibility selector.ids must be contiguous 1..N");
			}
			result.add(selectorId);
		}
		return result;
	}

	private static Map<String, String> parseCanonicalProperties(byte[] bytes, String label)
		throws IOException {
		for (byte value : bytes) {
			if ((value & 255) > 127) throw new IOException(label + " must be ASCII");
		}
		String text = new String(bytes, StandardCharsets.US_ASCII);
		if (!text.endsWith("\n") || text.indexOf('\r') >= 0) {
			throw new IOException(label + " must use terminal LF and no CR");
		}
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		String previous = null;
		String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
		for (String line : lines) {
			if (line.length() == 0 || line.startsWith("#") || line.startsWith("!")) {
				throw new IOException(label + " contains an unsupported line");
			}
			int equals = line.indexOf('=');
			if (equals <= 0 || line.indexOf('=', equals + 1) >= 0) {
				throw new IOException(label + " contains an unsupported property");
			}
			String key = line.substring(0, equals);
			String value = line.substring(equals + 1);
			if (key.indexOf(':') >= 0 || key.indexOf('\\') >= 0 || value.indexOf('\\') >= 0
				|| value.indexOf('\t') >= 0 || result.containsKey(key)) {
				throw new IOException(label + " contains an unsafe or duplicate property");
			}
			if (previous != null && previous.compareTo(key) >= 0) {
				throw new IOException(label + " keys are not sorted canonically");
			}
			result.put(key, value);
			previous = key;
		}
		return result;
	}

	private static String requireExact(Map<String, String> values, String key,
		String expected) throws IOException {
		String actual = values.get(key);
		if (!expected.equals(actual)) {
			throw new IOException("legacy compatibility " + key + " must equal " + expected);
		}
		return actual;
	}

	private static void requireInt(Map<String, String> values, String key, int expected)
		throws IOException {
		int actual = requireInt(values, key);
		if (actual != expected) {
			throw new IOException("legacy compatibility " + key + " must equal " + expected);
		}
	}

	private static int requireInt(Map<String, String> values, String key) throws IOException {
		String value = values.get(key);
		int result;
		try {
			result = Integer.parseInt(value);
		} catch (RuntimeException error) {
			throw new IOException("legacy compatibility " + key + " is not an integer", error);
		}
		if (!Integer.toString(result).equals(value)) {
			throw new IOException("legacy compatibility " + key + " is not canonical");
		}
		return result;
	}

	private static String requireId(Map<String, String> values, String key) throws IOException {
		String value = values.get(key);
		if (value == null || !ID_PATTERN.matcher(value).matches()) {
			throw new IOException("legacy compatibility " + key + " is not a canonical id");
		}
		return value;
	}

	private static String requireSha256(Map<String, String> values, String key)
		throws IOException {
		String value = values.get(key);
		if (value == null || !SHA256_PATTERN.matcher(value).matches()) {
			throw new IOException("legacy compatibility " + key + " is not a SHA-256 digest");
		}
		return value;
	}

	private static byte[] readFileBounded(File file, int maximum) throws IOException {
		try (FileInputStream input = new FileInputStream(file)) {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) != -1) {
				if (output.size() + read > maximum) {
					throw new IOException("legacy compatibility input exceeds " + maximum + " bytes");
				}
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		}
	}

	private static String sha256(byte[] bytes) throws IOException {
		return hex(sha256Digest().digest(bytes));
	}

	private static MessageDigest sha256Digest() throws IOException {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException error) {
			throw new IOException("SHA-256 is unavailable", error);
		}
	}

	private static String hex(byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) result.append(String.format("%02x", value & 255));
		return result.toString();
	}

	private static Set<String> immutableSet(String... values) {
		HashSet<String> result = new HashSet<>();
		result.addAll(Arrays.asList(values));
		return Collections.unmodifiableSet(result);
	}

	private static String safeMessage(Exception error) {
		String message = error.getMessage();
		return message == null || message.length() == 0
			? error.getClass().getSimpleName() : message;
	}

	/**
	 * Verifies all declared 1x source-decimation digests against the exact bound
	 * V2 hair assets using nearest phase {@code (0,0)}. This is intentionally
	 * separate from {@link #read(File, File)} so compatibility remains usable
	 * when the V2 pack is absent or rejected.
	 *
	 * @return the number of verified selector/frame pairs
	 */
	public int verifySourceDecimationAgainst(PaperdollV2Pack pack) throws IOException {
		if (!rootValid) throw new IOException("legacy compatibility root is not valid");
		if (pack == null) throw new IOException("legacy compatibility has no V2 pack for source parity");
		if (!expectedPackSha256.equals(pack.getArchiveSha256())) {
			throw new IOException("legacy compatibility source parity pack SHA-256 differs");
		}
		int verified = 0;
		for (int selectorId = 1; selectorId < styleBySelector.length; selectorId++) {
			String style = styleBySelector[selectorId];
			if (style == null || style.length() == 0
				|| selectorId >= sourceDecimatedSha256BySelector.length
				|| sourceDecimatedSha256BySelector[selectorId] == null) {
				throw new IOException("legacy compatibility source parity lacks selector " + selectorId);
			}
			String assetId = "hair_" + style;
			PaperdollV2Pack.Asset asset = pack.getAsset(assetId);
			if (asset == null || !"hair".equals(asset.getKind())
				|| !"native".equals(asset.getSourceMode()) || asset.getPaperdollSlot() != 0
				|| asset.getChannelCount() != 1) {
				throw new IOException("legacy compatibility source parity rejects asset " + assetId);
			}
			PaperdollV2Pack.Channel channel = asset.getChannel(0);
			if (!"hair".equals(channel.getId()) || !"hair".equals(channel.getTintRole())) {
				throw new IOException("legacy compatibility source parity rejects channel " + assetId);
			}
			for (int frame = 0; frame < FRAME_COUNT; frame++) {
				if (channel.isEmpty(frame)) {
					throw new IOException("legacy compatibility source parity has empty "
						+ assetId + " frame " + frame);
				}
				Sprite source = channel.getFrame(frame);
				int expectedWidth = frame >= 15 ? 168 : 128;
				if (source == null || !source.requiresShift()
					|| source.getSomething1() != expectedWidth || source.getSomething2() != 204
					|| source.getPixels() == null
					|| source.getPixels().length != source.getWidth() * source.getHeight()
					|| source.getXShift() < 0 || source.getYShift() < 0
					|| source.getXShift() + source.getWidth() > expectedWidth
					|| source.getYShift() + source.getHeight() > 204) {
					throw new IOException("legacy compatibility source parity geometry changed for "
						+ assetId + " frame " + frame);
				}
				String actual = decimatedRgbaSha256(source, expectedWidth / 2, 102);
				String expected = sourceDecimatedSha256BySelector[selectorId][frame];
				if (!actual.equals(expected)) {
					throw new IOException("legacy compatibility source parity differs for selector "
						+ selectorId + " frame " + frame);
				}
				verified++;
			}
		}
		if (verified != getMaximumSelectorId() * FRAME_COUNT) {
			throw new IOException("legacy compatibility source parity verification is partial");
		}
		return verified;
	}

	private static String decimatedRgbaSha256(Sprite source, int width, int height)
		throws IOException {
		MessageDigest digest = sha256Digest();
		updateInt(digest, width);
		updateInt(digest, height);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int sourceX = x * 2 - source.getXShift();
				int sourceY = y * 2 - source.getYShift();
				int argb = 0;
				if (sourceX >= 0 && sourceY >= 0 && sourceX < source.getWidth()
					&& sourceY < source.getHeight()) {
					argb = source.getPixels()[sourceY * source.getWidth() + sourceX];
				}
				digest.update((byte) (argb >> 16 & 255));
				digest.update((byte) (argb >> 8 & 255));
				digest.update((byte) (argb & 255));
				digest.update((byte) (argb >>> 24));
			}
		}
		return hex(digest.digest());
	}

	public boolean isRootValid() { return rootValid; }
	public String getRootReason() { return rootReason; }
	public File getPropertiesFile() { return propertiesFile; }
	public String getPropertiesSha256() { return propertiesSha256; }
	public String getManifestSha256() { return manifestSha256; }
	public String getExpectedPackSha256() { return expectedPackSha256; }
	public int getMaximumSelectorId() { return Math.max(0, framesBySelector.length - 1); }

	public int getLoadedStyleCount() {
		int result = 0;
		for (int selectorId = 1; selectorId < framesBySelector.length; selectorId++) {
			if (framesBySelector[selectorId] != null) result++;
		}
		return result;
	}

	public String getStyle(int selectorId) {
		return selectorId <= 0 || selectorId >= styleBySelector.length
			? "" : styleBySelector[selectorId];
	}

	public String getStyleRejectionReason(int selectorId) {
		if (selectorId <= 0 || selectorId >= rejectionBySelector.length) {
			return "unknown-selector-" + selectorId;
		}
		String reason = rejectionBySelector[selectorId];
		return reason == null ? "" : reason;
	}

	public boolean hasStyle(int selectorId) {
		return rootValid && selectorId > 0 && selectorId < framesBySelector.length
			&& framesBySelector[selectorId] != null;
	}

	/** Returns only an exact validated frame; no clamping or frame-zero substitution. */
	public Sprite getFrame(int selectorId, int frameOffset, int headAppearanceId,
		int hatAppearanceId) {
		if (!rootValid || headAppearanceId != COMPATIBLE_HEAD_APPEARANCE_ID
			|| hatAppearanceId != 0 || frameOffset < 0 || frameOffset >= FRAME_COUNT
			|| !hasStyle(selectorId)) return null;
		return framesBySelector[selectorId][frameOffset];
	}
}
