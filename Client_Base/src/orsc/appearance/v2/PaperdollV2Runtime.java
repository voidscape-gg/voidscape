package orsc.appearance.v2;

import orsc.osConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default-off, PC-only runtime gate for the Paperdoll V2 scene proof.
 *
 * <p>The proof deliberately accepts only an explicit {@code Paperdoll_V2.orsc}
 * below this repository's {@code tmp/} tree. Any path, digest, stack, or pack
 * failure leaves the normal legacy player renderer selected for the whole
 * player.</p>
 */
public final class PaperdollV2Runtime {
	public static final String ENABLED_PROPERTY = "voidscape.paperdollV2";
	public static final String PACK_PROPERTY = "voidscape.paperdollV2.pack";
	public static final String STACK_PROPERTY = "voidscape.paperdollV2.stack";
	public static final String SELECTOR_REGISTRY_PROPERTY =
		"voidscape.paperdollV2.selectorRegistry";
	public static final String LEGACY_COMPATIBILITY_PROPERTY =
		PaperdollV2LegacyCompatibility.PROPERTY;
	public static final String FORCE_LEGACY_PROPERTY = "voidscape.paperdollV2.forceLegacy";
	public static final String DESIGNER_EVALUATION_PROPERTY =
		"voidscape.paperdollV2.designerEvaluation";
	public static final String DEFAULT_STACK = "rare_hair";
	public static final int VIEWPORT_WIDTH = 1024;
	public static final int VIEWPORT_HEIGHT = 680;
	public static final int GAME_HEIGHT = VIEWPORT_HEIGHT - 12;
	public static final int PROJECTION_SHIFT = 10;
	/** Current native_head is calibrated from the approved male head4 / appearance 8 base. */
	public static final int COMPATIBLE_HEAD_APPEARANCE_ID = 8;
	public static final int COMPATIBLE_MALE_BODY_APPEARANCE_ID = 2;
	public static final int COMPATIBLE_FEMALE_BODY_APPEARANCE_ID = 5;
	public static final int COMPATIBLE_LEGS_APPEARANCE_ID = 3;
	public static final int DEFAULT_PRIMARY_RGB = 0x244866;
	public static final int DEFAULT_SECONDARY_RGB = 0xd1ad53;

	private static final String EXPECTED_TEMPLATE_SHA256 =
		"d7400f68d450498434967e5ec2c6b6d8165b739a511271401d65b530c9d8b019";
	private static final String EXPECTED_DERIVED_MASKS_SHA256 =
		"5f30960f67f7f02ca3799bfd4c8b0e8b708594e0d9d5ab517d184f37d0b0208f";
	private static final String EXPECTED_SOURCE_V1_SHA256 =
		"636fbad37576feef7ab8f801563745fc5ab9fb6deb2830a852829a6ea4f99b03";

	private final boolean requested;
	private final boolean forceLegacy;
	private final PaperdollV2Pack pack;
	private final boolean selectorModeRequested;
	private final PaperdollV2SelectorRegistry selectorRegistry;
	private final PaperdollV2LegacyCompatibility legacyCompatibility;
	private final String selectorRegistryPath;
	private final String configuredStackId;
	private volatile RenderSelection renderSelection;
	private long stackSelectionRevision;
	private final String packPath;
	private final String fallbackReason;
	private final long heapUsedBeforeLoad;
	private final long heapUsedAfterLoad;
	private final long loadNanos;
	private long v2RenderCount;
	private long legacyFallbackRenderCount;
	private long v2RenderNanos;
	private long legacyFallbackRenderNanos;
	private long localV2RenderCount;
	private long localLegacyFallbackRenderCount;
	private long localV2RenderNanos;
	private long localLegacyFallbackRenderNanos;
	private long remoteV2RenderCount;
	private long remoteLegacyFallbackRenderCount;
	private long remoteV2RenderNanos;
	private long remoteLegacyFallbackRenderNanos;
	private String lastRenderFallbackReason = "";
	private long[] benchmarkDurations;
	private int benchmarkSampleCount;
	private boolean benchmarkExpectedV2;
	private long benchmarkUnexpectedPathCount;
	private boolean benchmarkTelemetryComplete;
	private long benchmarkLocalV2Before;
	private long benchmarkLocalLegacyBefore;
	private long benchmarkRemoteV2Before;
	private long benchmarkRemoteLegacyBefore;
	private long benchmarkLocalV2After;
	private long benchmarkLocalLegacyAfter;
	private long benchmarkRemoteV2After;
	private long benchmarkRemoteLegacyAfter;

	private PaperdollV2Runtime(boolean requested, boolean forceLegacy, PaperdollV2Pack pack,
		PaperdollV2Pack.RenderStack stack, PaperdollV2BaseProfile baseProfile,
		PaperdollV2SelectorRegistry selectorRegistry,
		PaperdollV2LegacyCompatibility legacyCompatibility, boolean selectorModeRequested,
		String configuredStackId, String packPath, String selectorRegistryPath, String fallbackReason,
		long heapUsedBeforeLoad, long heapUsedAfterLoad, long loadNanos) {
		this.requested = requested;
		this.forceLegacy = forceLegacy;
		this.pack = pack;
		this.renderSelection = stack == null || baseProfile == null
			? null : new RenderSelection(stack, baseProfile, -1, "");
		this.selectorRegistry = selectorRegistry;
		this.legacyCompatibility = legacyCompatibility == null
			? PaperdollV2LegacyCompatibility.unavailable("", "not-configured")
			: legacyCompatibility;
		this.selectorModeRequested = selectorModeRequested;
		this.selectorRegistryPath = selectorRegistryPath == null ? "" : selectorRegistryPath;
		this.configuredStackId = configuredStackId == null ? "" : configuredStackId;
		this.packPath = packPath == null ? "" : packPath;
		this.fallbackReason = fallbackReason == null ? "" : fallbackReason;
		this.heapUsedBeforeLoad = heapUsedBeforeLoad;
		this.heapUsedAfterLoad = heapUsedAfterLoad;
		this.loadNanos = loadNanos;
	}

	public static boolean isRequested() {
		return !osConfig.F_ANDROID_BUILD && !osConfig.F_WEB_BUILD
			&& Boolean.getBoolean(ENABLED_PROPERTY);
	}

	public static PaperdollV2Runtime load() {
		if (!isRequested()) {
			return new PaperdollV2Runtime(false, false, null, null, null, null,
				PaperdollV2LegacyCompatibility.unavailable("", "disabled"), false,
				"", "", "", "disabled", 0L, 0L, 0L);
		}

		boolean forceLegacy = Boolean.getBoolean(FORCE_LEGACY_PROPERTY);
		long heapBefore = usedHeapBytes();
		long started = System.nanoTime();
		PaperdollV2LegacyCompatibility legacyCompatibility = loadLegacyCompatibility();
		String configuredSelectorPath = System.getProperty(SELECTOR_REGISTRY_PROPERTY, "").trim();
		boolean selectorModeRequested = configuredSelectorPath.length() > 0;
		String configuredPath = System.getProperty(PACK_PROPERTY, "").trim();
		if (configuredPath.length() == 0) {
			return failed(forceLegacy, selectorModeRequested, legacyCompatibility,
				"missing-explicit-pack-property", "",
				configuredSelectorPath, heapBefore, started);
		}

		File packFile;
		try {
			packFile = requireTmpPack(configuredPath);
		} catch (IOException error) {
			return failed(forceLegacy, selectorModeRequested, legacyCompatibility,
				"invalid-pack-path: " + safeMessage(error), configuredPath,
				configuredSelectorPath, heapBefore, started);
		}

		try (FileInputStream input = new FileInputStream(packFile)) {
			PaperdollV2Pack pack = PaperdollV2Pack.read(input);
			requirePinnedContract(pack);
			legacyCompatibility = bindLegacyCompatibilityToPack(legacyCompatibility, pack);
			if (selectorModeRequested) {
				File registryFile = requireTmpSelectorRegistry(configuredSelectorPath, packFile);
				PaperdollV2SelectorRegistry registry = PaperdollV2SelectorRegistry.read(
					registryFile, pack, findRepositoryRoot());
				String fallback = forceLegacy ? "forced-legacy-baseline" : "";
				PaperdollV2Runtime runtime = new PaperdollV2Runtime(true, forceLegacy, pack,
					null, null, registry, legacyCompatibility, true, "", packFile.getAbsolutePath(),
					registryFile.getAbsolutePath(), fallback, heapBefore, usedHeapBytes(),
					System.nanoTime() - started);
				System.out.println("Paperdoll V2 selector runtime "
					+ (runtime.isActive() ? "active" : "validated") + ": selectors="
					+ registry.getEntries().size() + " pack=" + packFile.getAbsolutePath()
					+ (fallback.length() == 0 ? "" : " fallback=" + fallback));
				return runtime;
			}

			String stackId = System.getProperty(STACK_PROPERTY, DEFAULT_STACK).trim();
			if (stackId.length() == 0) stackId = DEFAULT_STACK;
			PaperdollV2Pack.RenderStack stack = pack.requireRenderStack(stackId);
			PaperdollV2BaseProfile baseProfile = requireLiveStack(stack);
			String fallback = forceLegacy ? "forced-legacy-baseline" : "";
			PaperdollV2Runtime runtime = new PaperdollV2Runtime(true, forceLegacy, pack, stack,
				baseProfile, null, legacyCompatibility, false, stackId,
				packFile.getAbsolutePath(), "", fallback,
				heapBefore, usedHeapBytes(), System.nanoTime() - started);
			System.out.println("Paperdoll V2 runtime " + (runtime.isActive() ? "active" : "validated")
				+ ": stack=" + stackId + " pack=" + packFile.getAbsolutePath()
				+ (fallback.length() == 0 ? "" : " fallback=" + fallback));
			return runtime;
		} catch (IOException error) {
			String prefix = selectorModeRequested ? "selector-registry-rejected: " : "pack-rejected: ";
			return failed(forceLegacy, selectorModeRequested, legacyCompatibility,
				prefix + safeMessage(error),
				packFile.getAbsolutePath(), configuredSelectorPath, heapBefore, started);
		}
	}

	private static PaperdollV2Runtime failed(boolean forceLegacy, boolean selectorModeRequested,
		PaperdollV2LegacyCompatibility legacyCompatibility, String reason, String path,
		String selectorPath, long heapBefore, long started) {
		System.out.println("Paperdoll V2 runtime fallback: " + reason);
		return new PaperdollV2Runtime(true, forceLegacy, null, null, null, null,
			legacyCompatibility,
			selectorModeRequested, selectorModeRequested ? ""
				: System.getProperty(STACK_PROPERTY, DEFAULT_STACK).trim(), path, selectorPath,
			reason, heapBefore, usedHeapBytes(), System.nanoTime() - started);
	}

	private static PaperdollV2LegacyCompatibility loadLegacyCompatibility() {
		String configuredPath = System.getProperty(LEGACY_COMPATIBILITY_PROPERTY, "").trim();
		if (!Boolean.getBoolean("voidscape.workbench")) {
			return PaperdollV2LegacyCompatibility.unavailable(configuredPath,
				"workbench-required");
		}
		if (configuredPath.length() == 0) {
			return PaperdollV2LegacyCompatibility.unavailable("", "not-configured");
		}
		try {
			File repository = findRepositoryRoot();
			File candidate = new File(configuredPath);
			if (!candidate.isAbsolute()) candidate = new File(repository, configuredPath);
			PaperdollV2LegacyCompatibility compatibility =
				PaperdollV2LegacyCompatibility.read(candidate, repository);
			System.out.println("Paperdoll V2 legacy compatibility validated: styles="
				+ compatibility.getLoadedStyleCount() + " properties="
				+ compatibility.getPropertiesFile().getAbsolutePath());
			return compatibility;
		} catch (IOException error) {
			String reason = "legacy-compatibility-rejected: " + safeMessage(error);
			System.out.println("Paperdoll V2 " + reason);
			return PaperdollV2LegacyCompatibility.unavailable(configuredPath, reason);
		}
	}

	private static PaperdollV2LegacyCompatibility bindLegacyCompatibilityToPack(
		PaperdollV2LegacyCompatibility compatibility, PaperdollV2Pack pack) {
		if (compatibility == null || !compatibility.isRootValid() || pack == null) {
			return compatibility;
		}
		if (!pack.getArchiveSha256().equals(compatibility.getExpectedPackSha256())) {
			String reason = "legacy-compatibility-pack-binding-mismatch";
			System.out.println("Paperdoll V2 " + reason);
			return PaperdollV2LegacyCompatibility.unavailable(
				compatibility.getPropertiesFile().getAbsolutePath(), reason);
		}
		try {
			compatibility.verifySourceDecimationAgainst(pack);
		} catch (IOException error) {
			String reason = "legacy-compatibility-source-parity-rejected: "
				+ safeMessage(error);
			System.out.println("Paperdoll V2 " + reason);
			return PaperdollV2LegacyCompatibility.unavailable(
				compatibility.getPropertiesFile().getAbsolutePath(), reason);
		}
		return compatibility;
	}

	private static File requireTmpPack(String configuredPath) throws IOException {
		File repository = findRepositoryRoot();
		File tmpRoot = new File(repository, "tmp").getCanonicalFile();
		File candidate = new File(configuredPath);
		if (!candidate.isAbsolute()) candidate = new File(repository, configuredPath);
		candidate = requireUnsymLinkedTmpPath(tmpRoot, candidate, "pack");
		String rootPath = tmpRoot.getPath();
		String candidatePath = candidate.getPath();
		if (!candidatePath.startsWith(rootPath + File.separator)) {
			throw new IOException("pack must remain below repository tmp");
		}
		if (!candidate.isFile() || !"Paperdoll_V2.orsc".equals(candidate.getName())) {
			throw new IOException("pack must be an existing Paperdoll_V2.orsc file");
		}
		return candidate;
	}

	private static File requireTmpSelectorRegistry(String configuredPath, File packFile)
		throws IOException {
		File repository = findRepositoryRoot();
		File tmpRoot = new File(repository, "tmp").getCanonicalFile();
		File candidate = new File(configuredPath);
		if (!candidate.isAbsolute()) candidate = new File(repository, configuredPath);
		candidate = requireUnsymLinkedTmpPath(tmpRoot, candidate, "selector registry");
		if (!candidate.getPath().startsWith(tmpRoot.getPath() + File.separator)) {
			throw new IOException("selector registry must remain below repository tmp");
		}
		if (!candidate.isFile()
			|| !PaperdollV2SelectorRegistry.FILE_NAME.equals(candidate.getName())) {
			throw new IOException("selector registry must be an existing "
				+ PaperdollV2SelectorRegistry.FILE_NAME);
		}
		if (!candidate.getParentFile().equals(packFile.getCanonicalFile().getParentFile())) {
			throw new IOException("selector registry must be the sibling of Paperdoll_V2.orsc");
		}
		return candidate;
	}

	private static File requireUnsymLinkedTmpPath(File tmpRoot, File configured,
		String label) throws IOException {
		File root = tmpRoot.getCanonicalFile();
		File lexical = configured.getAbsoluteFile().toPath().normalize().toFile();
		if (!lexical.getPath().startsWith(root.getPath() + File.separator)) {
			throw new IOException(label + " must remain below repository tmp");
		}
		Path cursor = root.toPath();
		for (Path part : cursor.relativize(lexical.toPath())) {
			cursor = cursor.resolve(part);
			if (Files.isSymbolicLink(cursor)) {
				throw new IOException(label + " path contains a symlink");
			}
		}
		File canonical = lexical.getCanonicalFile();
		if (!lexical.equals(canonical)) {
			throw new IOException(label + " path is not canonical");
		}
		return canonical;
	}

	private static File findRepositoryRoot() throws IOException {
		File cursor = new File(System.getProperty("user.dir", ".")).getCanonicalFile();
		for (int depth = 0; cursor != null && depth < 10; depth++, cursor = cursor.getParentFile()) {
			if (new File(cursor, "AGENTS.md").isFile() && new File(cursor, "Client_Base").isDirectory()
				&& new File(cursor, "PC_Client").isDirectory()) return cursor;
		}
		throw new IOException("unable to locate repository root");
	}

	private static void requirePinnedContract(PaperdollV2Pack pack) throws IOException {
		if (!EXPECTED_TEMPLATE_SHA256.equals(pack.getTemplateSha256())) {
			throw new IOException("template digest is not the approved rsc-player-2x-v1 digest");
		}
		if (!EXPECTED_DERIVED_MASKS_SHA256.equals(pack.getDerivedMasksSha256())) {
			throw new IOException("derived-mask digest is not approved");
		}
		if (!EXPECTED_SOURCE_V1_SHA256.equals(pack.getSourceV1Sha256())) {
			throw new IOException("legacy-source digest is not approved");
		}
	}

	private static PaperdollV2BaseProfile requireLiveStack(PaperdollV2Pack.RenderStack stack)
		throws IOException {
		if (!stack.usesLiveControls()) {
			throw new IOException("selected stack is not a live-controls stack");
		}
		boolean foundHead = false;
		for (PaperdollV2Pack.Asset asset : stack.getAssets()) {
			if (asset.getPaperdollSlot() == 0 && "native".equals(asset.getSourceMode())) {
				if ("head".equals(asset.getKind())) {
					if (foundHead) throw new IOException("selected stack has more than one native head");
					foundHead = true;
				} else if (!foundHead) {
					throw new IOException("selected stack draws slot-0 material before its native head");
				}
			} else if (asset.getPaperdollSlot() == 5 && "native".equals(asset.getSourceMode())) {
				throw new IOException("selected stack has disabled native slot-5 substitution");
			}
		}
		if (!stack.substitutesSlot(0) || !foundHead) {
			throw new IOException("selected stack lacks a native slot-0 head");
		}
		return PaperdollV2BaseProfile.derive(stack, null);
	}

	public boolean isRequestedByUser() { return requested; }
	public boolean isPackValid() {
		return pack != null && (selectorModeRequested ? selectorRegistry != null : renderSelection != null);
	}
	public boolean isActive() { return requested && isPackValid() && !forceLegacy; }
	public boolean isSelectorModeRequested() { return selectorModeRequested; }
	public boolean isSelectorModeValid() { return selectorModeRequested && selectorRegistry != null; }
	public boolean isSelectorModeActive() { return isActive() && isSelectorModeValid(); }
	public boolean isForceLegacy() { return forceLegacy; }
	public String getFallbackReason() { return fallbackReason; }
	public String getPackPath() { return packPath; }
	public String getSelectorRegistryPath() { return selectorRegistryPath; }
	public PaperdollV2SelectorRegistry getSelectorRegistry() { return selectorRegistry; }
	public PaperdollV2LegacyCompatibility getLegacyCompatibility() {
		return legacyCompatibility;
	}
	public boolean isLegacyCompatibilityUsable() {
		return legacyCompatibility != null && legacyCompatibility.isRootValid()
			&& legacyCompatibility.getLoadedStyleCount() > 0;
	}
	public PaperdollV2Pack getPack() { return pack; }
	public PaperdollV2Pack.RenderStack getStack() {
		RenderSelection selected = renderSelection;
		return selected == null ? null : selected.getStack();
	}
	public PaperdollV2BaseProfile getBaseProfile() {
		RenderSelection selected = renderSelection;
		return selected == null ? null : selected.getBaseProfile();
	}
	public String getConfiguredStackId() { return configuredStackId; }
	public String getSelectedStackId() {
		PaperdollV2Pack.RenderStack selected = getStack();
		return selected == null ? "" : selected.getId();
	}
	public synchronized long getStackSelectionRevision() { return stackSelectionRevision; }
	public int getPrimaryRgb() { return DEFAULT_PRIMARY_RGB; }
	public int getSecondaryRgb() { return DEFAULT_SECONDARY_RGB; }
	public boolean isDevStackSelectionEnabled() {
		return Boolean.getBoolean("voidscape.workbench") && requested && pack != null
			&& !selectorModeRequested;
	}

	public boolean isDesignerEvaluationEnabled() {
		return !osConfig.F_ANDROID_BUILD && !osConfig.F_WEB_BUILD
			&& Boolean.getBoolean("voidscape.workbench")
			&& Boolean.getBoolean(DESIGNER_EVALUATION_PROPERTY)
			&& isSelectorModeActive() && selectorRegistry != null
			&& selectorRegistry.getHighestDefinedSelectorId() == 6;
	}

	public List<String> getAvailableLiveStackIds() {
		if (pack == null) return Collections.emptyList();
		ArrayList<String> result = new ArrayList<>();
		for (String stackId : pack.getRenderStackIds()) {
			try {
				PaperdollV2Pack.RenderStack candidate = pack.requireRenderStack(stackId);
				if (candidate.usesLiveControls()) {
					requireLiveStack(candidate);
					result.add(stackId);
				}
			} catch (IOException ignored) {
				// The strict pack reader or initial selection will expose malformed stacks.
			}
		}
		return Collections.unmodifiableList(result);
	}

	/** Workbench-only mutable selector. Normal clients have no call path and remain default-off. */
	public synchronized void selectDevStack(String stackId) throws IOException {
		if (!isDevStackSelectionEnabled()) {
			throw new IOException("Paperdoll V2 mutable stack selection requires the Workbench proof");
		}
		String normalized = stackId == null ? "" : stackId.trim();
		if (normalized.length() == 0) throw new IOException("Paperdoll V2 stack id is empty");
		PaperdollV2Pack.RenderStack candidate = pack.requireRenderStack(normalized);
		PaperdollV2BaseProfile candidateProfile = requireLiveStack(candidate);
		RenderSelection current = renderSelection;
		if (current != null && current.getStack().getId().equals(candidate.getId())) return;
		renderSelection = new RenderSelection(candidate, candidateProfile, -1, "");
		stackSelectionRevision++;
		lastRenderFallbackReason = "";
	}

	public boolean substitutesSlot(int slot) {
		PaperdollV2Pack.RenderStack current = getStack();
		return current != null && current.substitutesSlot(slot);
	}

	/** Complete preflight; false means draw the untouched legacy player instead. */
	public boolean canRender(int[] layerAnimation, int spriteOffset, int width, int height) {
		return preflight(layerAnimation, spriteOffset, width, height) != null;
	}

	/** Selector-aware complete preflight used by the live per-player compositor. */
	public boolean canRender(int[] layerAnimation, int hairStyle, int spriteOffset,
		int width, int height) {
		return preflight(layerAnimation, hairStyle, spriteOffset, width, height) != null;
	}

	/** Atomic, allocation-free selection snapshot for one complete player draw. */
	public synchronized RenderSelection preflight(int[] layerAnimation, int spriteOffset,
		int width, int height) {
		if (!preflightCommon(layerAnimation, spriteOffset, width, height)) return null;
		if (selectorModeRequested) {
			lastRenderFallbackReason = "selector-id-required";
			return null;
		}
		RenderSelection selected = renderSelection;
		return validateSelection(selected, layerAnimation);
	}

	/**
	 * Resolves one immutable selector/profile pair before any V2 pixel is drawn.
	 * Successful selector routes return objects cached when the sidecar was read.
	 */
	public synchronized RenderSelection preflight(int[] layerAnimation, int hairStyle,
		int spriteOffset, int width, int height) {
		if (!preflightCommon(layerAnimation, spriteOffset, width, height)) return null;
		if (!selectorModeRequested) return validateSelection(renderSelection, layerAnimation);
		if (selectorRegistry == null) {
			lastRenderFallbackReason = "selector-registry-unavailable";
			return null;
		}

		PaperdollV2SelectorRegistry.Entry entry = selectorRegistry.getEntry(hairStyle);
		if (entry == null) {
			lastRenderFallbackReason = "unknown-selector-" + hairStyle;
			return null;
		}
		if (!entry.isV2Route()) {
			lastRenderFallbackReason = "classic-selector-legacy";
			return null;
		}
		if (layerAnimation[5] != 0) {
			lastRenderFallbackReason = "unsupported-hat-appearance-" + layerAnimation[5];
			return null;
		}
		PaperdollV2BaseProfile profile = selectorRegistry.matchBaseProfile(layerAnimation);
		if (profile == null) {
			lastRenderFallbackReason = "incompatible-base-identity-"
				+ layerAnimation[0] + "-" + layerAnimation[1] + "-" + layerAnimation[2];
			return null;
		}
		RenderSelection selected = entry.getSelection(profile);
		if (selected == null) {
			lastRenderFallbackReason = "selector-route-missing-profile-" + profile.getId();
			return null;
		}
		return validateSelection(selected, layerAnimation);
	}

	/** Workbench/test diagnostic that observes selection and fallback under one runtime monitor. */
	public synchronized PreflightObservation inspectPreflight(int[] layerAnimation, int hairStyle,
		int spriteOffset, int width, int height) {
		RenderSelection selection = preflight(layerAnimation, hairStyle, spriteOffset, width, height);
		return new PreflightObservation(selection, lastRenderFallbackReason);
	}

	private boolean preflightCommon(int[] layerAnimation, int spriteOffset, int width, int height) {
		lastRenderFallbackReason = "";
		if (!isActive()) {
			lastRenderFallbackReason = fallbackReason.length() == 0 ? "runtime-inactive" : fallbackReason;
			return false;
		}
		if (layerAnimation == null || layerAnimation.length < 12) {
			lastRenderFallbackReason = "incomplete-player-layers";
			return false;
		}
		if (spriteOffset < 0 || spriteOffset >= PaperdollV2Pack.FRAME_COUNT
			|| width <= 0 || height <= 0) {
			lastRenderFallbackReason = "invalid-runtime-geometry";
			return false;
		}
		return true;
	}

	private RenderSelection validateSelection(RenderSelection selected, int[] layerAnimation) {
		PaperdollV2Pack.RenderStack current = selected == null ? null : selected.getStack();
		PaperdollV2BaseProfile currentProfile = selected == null ? null : selected.getBaseProfile();
		if (current == null || currentProfile == null || !current.substitutesSlot(0)) {
			lastRenderFallbackReason = "missing-slot-0-substitution";
			return null;
		}
		for (int slot = 0; slot <= 2; slot++) {
			if (!current.substitutesSlot(slot)) continue;
			int expected = currentProfile.expectedAppearanceId(slot);
			if (layerAnimation[slot] != expected) {
				lastRenderFallbackReason = "incompatible-slot-" + slot + "-appearance-"
					+ layerAnimation[slot] + "-expected-" + expected;
				return null;
			}
		}
		if (layerAnimation[5] != 0) {
			lastRenderFallbackReason = "unsupported-hat-appearance-" + layerAnimation[5];
			return null;
		}
		return selected;
	}

	public synchronized void recordRender(boolean localActor, boolean v2, long durationNanos) {
		long safeDuration = Math.max(0L, durationNanos);
		if (v2) {
			v2RenderCount++;
			v2RenderNanos += safeDuration;
			if (localActor) {
				localV2RenderCount++;
				localV2RenderNanos += safeDuration;
			} else {
				remoteV2RenderCount++;
				remoteV2RenderNanos += safeDuration;
			}
		} else {
			legacyFallbackRenderCount++;
			legacyFallbackRenderNanos += safeDuration;
			if (localActor) {
				localLegacyFallbackRenderCount++;
				localLegacyFallbackRenderNanos += safeDuration;
			} else {
				remoteLegacyFallbackRenderCount++;
				remoteLegacyFallbackRenderNanos += safeDuration;
			}
		}
		if (localActor && benchmarkDurations != null) {
			if (v2 == benchmarkExpectedV2) {
				if (benchmarkSampleCount < benchmarkDurations.length) {
					benchmarkDurations[benchmarkSampleCount++] = safeDuration;
				}
			} else {
				benchmarkUnexpectedPathCount++;
			}
			if (!benchmarkTelemetryComplete
				&& benchmarkSampleCount == benchmarkDurations.length) {
				captureBenchmarkTelemetryAfter();
			}
		}
	}

	/** Backward-compatible local-actor telemetry call for the original global proof tests. */
	public void recordRender(boolean v2, long durationNanos) {
		recordRender(true, v2, durationNanos);
	}

	public synchronized void beginDevBenchmark(boolean expectedV2, int targetSamples) throws IOException {
		if (!Boolean.getBoolean("voidscape.workbench")) {
			throw new IOException("Paperdoll V2 benchmark requires Workbench diagnostics");
		}
		if (targetSamples < 1 || targetSamples > 10000) {
			throw new IOException("Paperdoll V2 benchmark sample count is out of range");
		}
		benchmarkDurations = new long[targetSamples];
		benchmarkSampleCount = 0;
		benchmarkExpectedV2 = expectedV2;
		benchmarkUnexpectedPathCount = 0L;
		benchmarkTelemetryComplete = false;
		benchmarkLocalV2Before = localV2RenderCount;
		benchmarkLocalLegacyBefore = localLegacyFallbackRenderCount;
		benchmarkRemoteV2Before = remoteV2RenderCount;
		benchmarkRemoteLegacyBefore = remoteLegacyFallbackRenderCount;
		benchmarkLocalV2After = benchmarkLocalV2Before;
		benchmarkLocalLegacyAfter = benchmarkLocalLegacyBefore;
		benchmarkRemoteV2After = benchmarkRemoteV2Before;
		benchmarkRemoteLegacyAfter = benchmarkRemoteLegacyBefore;
	}

	public synchronized int getDevBenchmarkSampleCount() { return benchmarkSampleCount; }

	public synchronized BenchmarkSnapshot endDevBenchmark() throws IOException {
		if (benchmarkDurations == null) throw new IOException("Paperdoll V2 benchmark is not running");
		if (!benchmarkTelemetryComplete) captureBenchmarkTelemetryAfter();
		long[] samples = new long[benchmarkSampleCount];
		System.arraycopy(benchmarkDurations, 0, samples, 0, benchmarkSampleCount);
		BenchmarkSnapshot snapshot = new BenchmarkSnapshot(benchmarkExpectedV2, samples,
			benchmarkUnexpectedPathCount, benchmarkLocalV2Before, benchmarkLocalLegacyBefore,
			benchmarkRemoteV2Before, benchmarkRemoteLegacyBefore, benchmarkLocalV2After,
			benchmarkLocalLegacyAfter, benchmarkRemoteV2After, benchmarkRemoteLegacyAfter);
		cancelDevBenchmark();
		return snapshot;
	}

	private void captureBenchmarkTelemetryAfter() {
		benchmarkLocalV2After = localV2RenderCount;
		benchmarkLocalLegacyAfter = localLegacyFallbackRenderCount;
		benchmarkRemoteV2After = remoteV2RenderCount;
		benchmarkRemoteLegacyAfter = remoteLegacyFallbackRenderCount;
		benchmarkTelemetryComplete = true;
	}

	public synchronized void cancelDevBenchmark() {
		benchmarkDurations = null;
		benchmarkSampleCount = 0;
		benchmarkUnexpectedPathCount = 0L;
		benchmarkTelemetryComplete = false;
	}

	public synchronized long getV2RenderCount() { return v2RenderCount; }
	public synchronized long getLegacyFallbackRenderCount() { return legacyFallbackRenderCount; }
	public synchronized long getV2RenderNanos() { return v2RenderNanos; }
	public synchronized long getLegacyFallbackRenderNanos() { return legacyFallbackRenderNanos; }
	public synchronized long getLocalV2RenderCount() { return localV2RenderCount; }
	public synchronized long getLocalLegacyFallbackRenderCount() {
		return localLegacyFallbackRenderCount;
	}
	public synchronized long getLocalV2RenderNanos() { return localV2RenderNanos; }
	public synchronized long getLocalLegacyFallbackRenderNanos() {
		return localLegacyFallbackRenderNanos;
	}
	public synchronized long getRemoteV2RenderCount() { return remoteV2RenderCount; }
	public synchronized long getRemoteLegacyFallbackRenderCount() {
		return remoteLegacyFallbackRenderCount;
	}
	public synchronized long getRemoteV2RenderNanos() { return remoteV2RenderNanos; }
	public synchronized long getRemoteLegacyFallbackRenderNanos() {
		return remoteLegacyFallbackRenderNanos;
	}
	public synchronized TelemetrySnapshot snapshotTelemetry() {
		return new TelemetrySnapshot(localV2RenderCount, localLegacyFallbackRenderCount,
			remoteV2RenderCount, remoteLegacyFallbackRenderCount);
	}
	public synchronized String getLastRenderFallbackReason() { return lastRenderFallbackReason; }
	public long getHeapUsedBeforeLoad() { return heapUsedBeforeLoad; }
	public long getHeapUsedAfterLoad() { return heapUsedAfterLoad; }
	public long getHeapDeltaAtLoad() { return heapUsedAfterLoad - heapUsedBeforeLoad; }
	public long getLoadNanos() { return loadNanos; }

	private static long usedHeapBytes() {
		Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	private static String safeMessage(Exception error) {
		String message = error.getMessage();
		return message == null || message.trim().length() == 0
			? error.getClass().getSimpleName() : message.replace('\n', ' ').replace('\r', ' ');
	}

	/** Atomic actor-specific count snapshot for Workbench evidence. */
	public static final class TelemetrySnapshot {
		private final long localV2;
		private final long localLegacy;
		private final long remoteV2;
		private final long remoteLegacy;

		private TelemetrySnapshot(long localV2, long localLegacy, long remoteV2,
			long remoteLegacy) {
			this.localV2 = localV2;
			this.localLegacy = localLegacy;
			this.remoteV2 = remoteV2;
			this.remoteLegacy = remoteLegacy;
		}

		public long getLocalV2() { return localV2; }
		public long getLocalLegacy() { return localLegacy; }
		public long getRemoteV2() { return remoteV2; }
		public long getRemoteLegacy() { return remoteLegacy; }
		public long getV2() { return localV2 + remoteV2; }
		public long getLegacy() { return localLegacy + remoteLegacy; }
	}

	public static final class BenchmarkSnapshot {
		private final boolean expectedV2;
		private final long[] durationsNanos;
		private final long unexpectedPathCount;
		private final long localV2Before;
		private final long localLegacyBefore;
		private final long remoteV2Before;
		private final long remoteLegacyBefore;
		private final long localV2After;
		private final long localLegacyAfter;
		private final long remoteV2After;
		private final long remoteLegacyAfter;

		private BenchmarkSnapshot(boolean expectedV2, long[] durationsNanos,
			long unexpectedPathCount, long localV2Before, long localLegacyBefore,
			long remoteV2Before, long remoteLegacyBefore, long localV2After,
			long localLegacyAfter, long remoteV2After, long remoteLegacyAfter) {
			this.expectedV2 = expectedV2;
			this.durationsNanos = durationsNanos.clone();
			this.unexpectedPathCount = unexpectedPathCount;
			this.localV2Before = localV2Before;
			this.localLegacyBefore = localLegacyBefore;
			this.remoteV2Before = remoteV2Before;
			this.remoteLegacyBefore = remoteLegacyBefore;
			this.localV2After = localV2After;
			this.localLegacyAfter = localLegacyAfter;
			this.remoteV2After = remoteV2After;
			this.remoteLegacyAfter = remoteLegacyAfter;
		}

		public boolean isExpectedV2() { return expectedV2; }
		public long[] getDurationsNanos() { return durationsNanos.clone(); }
		public long getUnexpectedPathCount() { return unexpectedPathCount; }
		public long getLocalV2Before() { return localV2Before; }
		public long getLocalLegacyBefore() { return localLegacyBefore; }
		public long getRemoteV2Before() { return remoteV2Before; }
		public long getRemoteLegacyBefore() { return remoteLegacyBefore; }
		public long getLocalV2After() { return localV2After; }
		public long getLocalLegacyAfter() { return localLegacyAfter; }
		public long getRemoteV2After() { return remoteV2After; }
		public long getRemoteLegacyAfter() { return remoteLegacyAfter; }
		public long getLocalV2Delta() { return localV2After - localV2Before; }
		public long getLocalLegacyDelta() { return localLegacyAfter - localLegacyBefore; }
		public long getRemoteV2Delta() { return remoteV2After - remoteV2Before; }
		public long getRemoteLegacyDelta() { return remoteLegacyAfter - remoteLegacyBefore; }
		public long getV2Before() { return localV2Before + remoteV2Before; }
		public long getLegacyBefore() { return localLegacyBefore + remoteLegacyBefore; }
		public long getV2After() { return localV2After + remoteV2After; }
		public long getLegacyAfter() { return localLegacyAfter + remoteLegacyAfter; }
		public long getV2Delta() { return getV2After() - getV2Before(); }
		public long getLegacyDelta() { return getLegacyAfter() - getLegacyBefore(); }
	}

	public static final class PreflightObservation {
		private final RenderSelection selection;
		private final String fallbackReason;

		private PreflightObservation(RenderSelection selection, String fallbackReason) {
			this.selection = selection;
			this.fallbackReason = fallbackReason == null ? "" : fallbackReason;
		}

		public RenderSelection getSelection() { return selection; }
		public String getFallbackReason() { return fallbackReason; }
	}

	/** Immutable selected stack/profile pair; safe to retain for one complete draw. */
	public static final class RenderSelection {
		private final PaperdollV2Pack.RenderStack stack;
		private final PaperdollV2BaseProfile baseProfile;
		private final int selectorId;
		private final String style;

		RenderSelection(PaperdollV2Pack.RenderStack stack,
			PaperdollV2BaseProfile baseProfile, int selectorId, String style) {
			this.stack = stack;
			this.baseProfile = baseProfile;
			this.selectorId = selectorId;
			this.style = style == null ? "" : style;
		}

		public PaperdollV2Pack.RenderStack getStack() { return stack; }
		public PaperdollV2BaseProfile getBaseProfile() { return baseProfile; }
		public int getSelectorId() { return selectorId; }
		public String getStyle() { return style; }
	}
}
