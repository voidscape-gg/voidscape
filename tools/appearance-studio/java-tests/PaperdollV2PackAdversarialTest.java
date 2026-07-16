package tools;

import orsc.appearance.v2.PaperdollV2Pack;
import orsc.appearance.v2.PaperdollV2BaseProfile;
import orsc.appearance.v2.PaperdollV2Runtime;
import orsc.appearance.v2.PaperdollV2SelectorRegistry;
import orsc.appearance.v2.PaperdollV2Pose;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Adversarial executable test for the strict Workbench-only V2 pack reader. */
public final class PaperdollV2PackAdversarialTest {
	private PaperdollV2PackAdversarialTest() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			throw new IllegalArgumentException("usage: PaperdollV2PackAdversarialTest VALID_PACK OUTPUT_DIR");
		}
		File validPack = new File(args[0]).getCanonicalFile();
		File outputDirectory = new File(args[1]).getCanonicalFile();
		if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
			throw new IOException("Unable to create test output directory " + outputDirectory);
		}
		Map<String, byte[]> baseline = readZip(validPack);
		testPreviewGeometry();
		Map<String, String> baselineRegistry = registry(baseline);
		boolean collectionPack = baselineRegistry.containsKey("render.stack.male_rare_spikes.assets");
		String maleLiveStack = collectionPack ? "male_rare_spikes" : "rare_hair";
		String maleControlStack = collectionPack ? "male_control" : "control";
		String hairAsset = "hair_rare_spikes";
		if (!"hair".equals(baselineRegistry.get("asset." + hairAsset + ".kind"))) {
			throw new IOException("Proof pack lacks the final rare-spikes hair asset");
		}
		String[] hairChannels = baselineRegistry.get("asset." + hairAsset + ".channels").split(",");
		if (hairChannels.length == 0 || hairChannels[0].isEmpty()) {
			throw new IOException("Rare-spikes hair asset has no channels");
		}
		String hairChannel = hairChannels[0];
		String framePrefix = "asset." + hairAsset + ".frame.00." + hairChannel;
		String frameEntry = baselineRegistry.get(framePrefix + ".entry");
		if (frameEntry == null) throw new IOException("Proof pack lacks " + framePrefix);
		try (FileInputStream input = new FileInputStream(validPack)) {
			PaperdollV2Pack pack = PaperdollV2Pack.read(input);
			if (pack.getArchiveByteCount() != validPack.length() || pack.getSpriteCount() <= 0
				|| pack.getDecodedArgbByteCount() <= 0L) {
				throw new AssertionError("Paperdoll V2 pack memory telemetry is incomplete");
			}
			PaperdollV2Pack.RenderStack rare = pack.requireRenderStack(maleLiveStack);
			if (collectionPack) assertSlots(rare.getNativePaperdollSlots(), 0, 1, 2);
			else assertSlots(rare.getNativePaperdollSlots(), 0);
			if (PaperdollV2BaseProfile.derive(rare, null) != PaperdollV2BaseProfile.MALE) {
				throw new AssertionError("Legacy rare_hair proof lost its pinned male base profile");
			}
		}
		testRuntimeGate(validPack, maleLiveStack, maleControlStack);
		if (collectionPack) testCollectionNativeBaseRuntimeVariants(validPack);
		File selectorProperties = new File(validPack.getParentFile(),
			PaperdollV2SelectorRegistry.FILE_NAME);
		if (selectorProperties.isFile()) {
			testSelectorRuntime(validPack, selectorProperties, outputDirectory);
		}

		Map<String, byte[]> unknownKey = copy(baseline);
		Map<String, String> unknownRegistry = registry(unknownKey);
		unknownRegistry.put("unknown.key", "must-fail");
		unknownKey.put("registry.properties", properties(unknownRegistry));
		expectFailure(write(outputDirectory, "unknown-key.orsc", unknownKey), "registry key set mismatch");

		Map<String, byte[]> undeclaredEntry = copy(baseline);
		undeclaredEntry.put("sprites/undeclared", new byte[] {1});
		expectFailure(write(outputDirectory, "undeclared-entry.orsc", undeclaredEntry),
			"ZIP entries disagree with registry");

		Map<String, byte[]> missingFrame = copy(baseline);
		if (missingFrame.remove(frameEntry) == null) throw new IOException("Proof pack lacks " + frameEntry);
		expectFailure(write(outputDirectory, "missing-frame.orsc", missingFrame), "archive is missing");

		Map<String, byte[]> malformedSidecar = copy(baseline);
		byte[] malformed = malformedSidecar.get(frameEntry).clone();
		malformed[8] = 0;
		malformedSidecar.put(frameEntry, malformed);
		Map<String, String> malformedRegistry = registry(malformedSidecar);
		malformedRegistry.put(framePrefix + ".sha256", sha256(malformed));
		malformedSidecar.put("registry.properties", properties(malformedRegistry));
		expectFailure(write(outputDirectory, "malformed-sidecar.orsc", malformedSidecar),
			"logical geometry mismatch");

		Map<String, byte[]> badDigest = copy(baseline);
		Map<String, String> badDigestRegistry = registry(badDigest);
		badDigestRegistry.put(framePrefix + ".sha256",
			"0000000000000000000000000000000000000000000000000000000000000000");
		badDigest.put("registry.properties", properties(badDigestRegistry));
		expectFailure(write(outputDirectory, "bad-digest.orsc", badDigest), "SHA-256 mismatch");

		Map<String, byte[]> partialAsset = copy(baseline);
		for (String channel : hairChannels) {
			for (int frame = 0; frame < PaperdollV2Pack.FRAME_COUNT; frame++) {
				String key = String.format("asset.%s.frame.%02d.%s.entry", hairAsset, frame, channel);
				partialAsset.remove(baselineRegistry.get(key));
			}
		}
		expectFailure(write(outputDirectory, "partial-asset.orsc", partialAsset), "archive is missing");

		Map<String, byte[]> wrongContract = copy(baseline);
		Map<String, String> wrongContractRegistry = registry(wrongContract);
		wrongContractRegistry.put("template.sha256",
			"0000000000000000000000000000000000000000000000000000000000000000");
		wrongContract.put("registry.properties", properties(wrongContractRegistry));
		File wrongContractDirectory = new File(outputDirectory, "wrong-contract");
		if (!wrongContractDirectory.isDirectory() && !wrongContractDirectory.mkdirs()) {
			throw new IOException("Unable to create wrong-contract test directory");
		}
		File wrongContractPack = write(wrongContractDirectory, "Paperdoll_V2.orsc", wrongContract);
		System.clearProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY);
		System.setProperty(PaperdollV2Runtime.STACK_PROPERTY, maleLiveStack);
		System.setProperty(PaperdollV2Runtime.PACK_PROPERTY, wrongContractPack.getAbsolutePath());
		PaperdollV2Runtime rejectedRuntime = PaperdollV2Runtime.load();
		if (rejectedRuntime.isActive() || rejectedRuntime.getFallbackReason().indexOf("template digest") < 0) {
			throw new AssertionError("Runtime accepted an unpinned V2 template contract");
		}

		System.out.println("Paperdoll V2 Java adversarial tests passed: global runtime + male/female"
			+ " native bases + strict selector registry (when present) + rejected pack/sidecar mutants");
	}

	private static void testPreviewGeometry() {
		if (PaperdollV2Pose.canonical().size() != 30) {
			throw new AssertionError("Paperdoll V2 canonical pose count changed");
		}
		for (PaperdollV2Pose pose : PaperdollV2Pose.canonical()) {
			if (pose.getV2DrawX() != PaperdollV2Pack.PREVIEW_DRAW_X) {
				throw new AssertionError("Pose supplies an already-expanded combat origin: "
					+ pose.getKey());
			}
			int drawWidth = pose.getV2DrawWidth();
			int drawX = pose.getV2DrawX()
				- (drawWidth - PaperdollV2Pack.WALK_WIDTH) / 2;
			if (drawX <= 0 || drawX + drawWidth >= PaperdollV2Pack.PREVIEW_WIDTH) {
				throw new AssertionError("Pose is clipped by the preview canvas: " + pose.getKey()
					+ " x=" + drawX + " width=" + drawWidth);
			}
		}
	}

	private static void testRuntimeGate(File validPack, String liveStackId, String controlStackId) {
		System.setProperty(PaperdollV2Runtime.ENABLED_PROPERTY, "true");
		System.setProperty(PaperdollV2Runtime.PACK_PROPERTY, validPack.getAbsolutePath());
		System.setProperty(PaperdollV2Runtime.STACK_PROPERTY, liveStackId);
		System.clearProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY);
		System.clearProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY);
		PaperdollV2Runtime runtime = PaperdollV2Runtime.load();
		if (!runtime.isRequestedByUser() || !runtime.isPackValid() || !runtime.isActive()) {
			throw new AssertionError("Valid explicit tmp pack did not activate the PC V2 runtime: "
				+ runtime.getFallbackReason());
		}
		if (runtime.getBaseProfile() != PaperdollV2BaseProfile.MALE
			|| runtime.isDevStackSelectionEnabled()) {
			throw new AssertionError("Legacy proof runtime profile/default-off selector contract changed");
		}
		int[] compatible = new int[12];
		PaperdollV2BaseProfile.MALE.applyTo(compatible);
		if (!runtime.canRender(compatible, 0, PaperdollV2Pack.WALK_WIDTH,
			PaperdollV2Pack.PLAYER_HEIGHT)) {
			throw new AssertionError("Approved head could not pass V2 runtime preflight");
		}
		compatible[0] = 1;
		if (runtime.canRender(compatible, 0, PaperdollV2Pack.WALK_WIDTH,
			PaperdollV2Pack.PLAYER_HEIGHT)) {
			throw new AssertionError("Mismatched head anatomy passed V2 runtime preflight");
		}
		compatible[0] = PaperdollV2Runtime.COMPATIBLE_HEAD_APPEARANCE_ID;
		compatible[5] = 1;
		if (runtime.canRender(compatible, 0, PaperdollV2Pack.WALK_WIDTH,
			PaperdollV2Pack.PLAYER_HEIGHT)) {
			throw new AssertionError("Undeclared live hat passed V2 runtime preflight");
		}
		try {
			runtime.selectDevStack(liveStackId);
			throw new AssertionError("Mutable stack selection was enabled outside Workbench");
		} catch (IOException expected) {
			// Default-off gate is intentional.
		}
		System.setProperty("voidscape.workbench", "true");
		try {
			runtime.selectDevStack(liveStackId);
			if (!runtime.isDevStackSelectionEnabled()) {
				throw new AssertionError("Workbench did not enable dev stack selection");
			}
			try {
				runtime.selectDevStack(controlStackId);
				throw new AssertionError("Pack-only control became a live runtime selection");
			} catch (IOException expected) {
				// Selector must preserve the prior valid stack.
			}
			if (!liveStackId.equals(runtime.getSelectedStackId())) {
				throw new AssertionError("Rejected selection changed the active stack");
			}
			runtime.beginDevBenchmark(true, 3);
			runtime.recordRender(false, false, 888L);
			runtime.recordRender(false, true, 777L);
			runtime.recordRender(true, true, 30L);
			runtime.recordRender(true, false, 999L);
			runtime.recordRender(true, true, 10L);
			runtime.recordRender(true, true, 20L);
			PaperdollV2Runtime.BenchmarkSnapshot benchmark = runtime.endDevBenchmark();
			long[] samples = benchmark.getDurationsNanos();
			if (!benchmark.isExpectedV2() || samples.length != 3
				|| samples[0] != 30L || samples[1] != 10L || samples[2] != 20L
				|| benchmark.getUnexpectedPathCount() != 1L
				|| benchmark.getLocalV2Delta() != 3L
				|| benchmark.getLocalLegacyDelta() != 1L
				|| benchmark.getRemoteV2Delta() != 1L
				|| benchmark.getRemoteLegacyDelta() != 1L) {
				throw new AssertionError("Workbench benchmark window did not isolate exact render samples");
			}
			if (runtime.getLocalV2RenderCount() != 3L
				|| runtime.getLocalLegacyFallbackRenderCount() != 1L
				|| runtime.getRemoteV2RenderCount() != 1L
				|| runtime.getRemoteLegacyFallbackRenderCount() != 1L) {
				throw new AssertionError("Actor-specific Paperdoll V2 telemetry was contaminated");
			}
		} catch (IOException e) {
			throw new AssertionError("Valid Workbench stack selection failed", e);
		} finally {
			System.clearProperty("voidscape.workbench");
		}
		System.setProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY, "true");
		PaperdollV2Runtime baseline = PaperdollV2Runtime.load();
		if (!baseline.isPackValid() || baseline.isActive()
			|| !"forced-legacy-baseline".equals(baseline.getFallbackReason())) {
			throw new AssertionError("Forced-legacy baseline did not preserve a validated pack fallback");
		}
		System.clearProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY);
	}

	private static void testNativeBaseRuntimeVariants(Map<String, byte[]> baseline,
		File outputDirectory) throws Exception {
		testNativeBaseRuntimeVariant(baseline, outputDirectory, "rare_hair",
			PaperdollV2BaseProfile.MALE);
		testNativeBaseRuntimeVariant(baseline, outputDirectory, "female_rare_spikes",
			PaperdollV2BaseProfile.FEMALE);
	}

	private static void testCollectionNativeBaseRuntimeVariants(File validPack) throws Exception {
		testCollectionNativeBaseRuntimeVariant(validPack, "male_rare_spikes",
			PaperdollV2BaseProfile.MALE);
		testCollectionNativeBaseRuntimeVariant(validPack, "female_rare_spikes",
			PaperdollV2BaseProfile.FEMALE);
	}

	private static void testCollectionNativeBaseRuntimeVariant(File validPack, String stackId,
		PaperdollV2BaseProfile profile) throws Exception {
		System.clearProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY);
		System.clearProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY);
		System.setProperty(PaperdollV2Runtime.PACK_PROPERTY, validPack.getAbsolutePath());
		System.setProperty(PaperdollV2Runtime.STACK_PROPERTY, stackId);
		PaperdollV2Runtime runtime = PaperdollV2Runtime.load();
		if (!runtime.isActive() || runtime.getBaseProfile() != profile) {
			throw new AssertionError("Collection " + profile.getId() + " runtime did not activate: "
				+ runtime.getFallbackReason());
		}
		assertSlots(runtime.getStack().getNativePaperdollSlots(), 0, 1, 2);
		int[] layers = new int[12];
		profile.applyTo(layers);
		if (!runtime.canRender(layers, 0, PaperdollV2Pack.WALK_WIDTH,
			PaperdollV2Pack.PLAYER_HEIGHT)) {
			throw new AssertionError("Collection " + profile.getId() + " base failed preflight");
		}
	}

	private static void testSelectorRuntime(File validPack, File validProperties,
		File outputDirectory) throws Exception {
		System.setProperty(PaperdollV2Runtime.ENABLED_PROPERTY, "true");
		System.setProperty(PaperdollV2Runtime.PACK_PROPERTY, validPack.getAbsolutePath());
		System.setProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY,
			validProperties.getAbsolutePath());
		System.clearProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY);
		PaperdollV2Runtime runtime = PaperdollV2Runtime.load();
		if (!runtime.isActive() || !runtime.isSelectorModeRequested()
			|| !runtime.isSelectorModeValid() || !runtime.isSelectorModeActive()
			|| runtime.getStack() != null || runtime.getBaseProfile() != null
			|| runtime.isDevStackSelectionEnabled()) {
			throw new AssertionError("Valid selector sidecar did not activate per-player mode: "
				+ runtime.getFallbackReason());
		}
		PaperdollV2SelectorRegistry selectorRegistry = runtime.getSelectorRegistry();
		if (selectorRegistry == null || selectorRegistry.getEntries().size() != 7
			|| !selectorRegistry.getPropertiesSha256().equals(
				sha256(Files.readAllBytes(validProperties.toPath())))
			|| !selectorRegistry.getPackSha256().equals(
				sha256(Files.readAllBytes(validPack.toPath())))) {
			throw new AssertionError("Selector registry telemetry/bindings differ from source files");
		}

		long revision = runtime.getStackSelectionRevision();
		for (int selectorId = 1; selectorId <= 6; selectorId++) {
			PaperdollV2SelectorRegistry.Entry entry = selectorRegistry.getEntry(selectorId);
			if (entry == null || !entry.isV2Route()) {
				throw new AssertionError("Positive selector is absent: " + selectorId);
			}
			for (PaperdollV2BaseProfile profile : new PaperdollV2BaseProfile[] {
				PaperdollV2BaseProfile.MALE, PaperdollV2BaseProfile.FEMALE}) {
				int[] layers = new int[12];
				profile.applyTo(layers);
				layers[3] = 98;
				layers[4] = 48;
				PaperdollV2Runtime.RenderSelection selected = runtime.preflight(layers, selectorId,
					0, PaperdollV2Pack.WALK_WIDTH, PaperdollV2Pack.PLAYER_HEIGHT);
				if (selected == null || selected.getSelectorId() != selectorId
					|| selected.getBaseProfile() != profile
					|| !entry.getStyle().equals(selected.getStyle())
					|| !entry.getStackId(profile).equals(selected.getStack().getId())) {
					throw new AssertionError("Selector/profile resolution failed for " + selectorId
						+ "/" + profile.getId() + ": " + runtime.getLastRenderFallbackReason());
				}
				assertSlots(selected.getStack().getNativePaperdollSlots(), 0, 1, 2);
				if (selected.getStack().substitutesSlot(3) || selected.getStack().substitutesSlot(4)
					|| selected.getStack().substitutesSlot(5) || layers[3] != 98 || layers[4] != 48) {
					throw new AssertionError("Selector replaced/mutated weapon or shield slots");
				}
				for (int repeat = 0; repeat < 1024; repeat++) {
					if (runtime.preflight(layers, selectorId, 0, PaperdollV2Pack.WALK_WIDTH,
						PaperdollV2Pack.PLAYER_HEIGHT) != selected) {
						throw new AssertionError("Successful selector preflight did not reuse cached object");
					}
				}
			}
		}
		int[] male = new int[12];
		PaperdollV2BaseProfile.MALE.applyTo(male);
		expectSelectorFallback(runtime, male, 0, "classic-selector-legacy");
		expectSelectorFallback(runtime, male, 255, "unknown-selector-255");
		male[5] = 245;
		expectSelectorFallback(runtime, male, 1, "unsupported-hat-appearance-245");
		male[5] = 151;
		expectSelectorFallback(runtime, male, 1, "unsupported-hat-appearance-151");
		male[5] = -1;
		expectSelectorFallback(runtime, male, 1, "unsupported-hat-appearance--1");
		PaperdollV2BaseProfile.MALE.applyTo(male);
		male[5] = 0;
		male[1] = 1;
		expectSelectorFallback(runtime, male, 1, "incompatible-base-identity-");
		if (runtime.getStackSelectionRevision() != revision) {
			throw new AssertionError("Per-player selector mutated global stack revision");
		}

		System.setProperty("voidscape.workbench", "true");
		try {
			runtime.selectDevStack("male_mohawk");
			throw new AssertionError("Selector mode enabled mutable global Workbench selection");
		} catch (IOException expected) {
			// Selector mode has no global mutable stack.
		} finally {
			System.clearProperty("voidscape.workbench");
		}

		System.setProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY, "true");
		PaperdollV2Runtime forced = PaperdollV2Runtime.load();
		if (!forced.isSelectorModeValid() || forced.isActive()
			|| !"forced-legacy-baseline".equals(forced.getFallbackReason())) {
			throw new AssertionError("Forced-legacy selector baseline lost validated sidecar");
		}
		System.clearProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY);

		byte[] validBytes = Files.readAllBytes(validProperties.toPath());
		Map<String, String> values = parseProperties(validBytes);
		Map<String, String> changed = new TreeMap<>(values);
		changed.put("unknown.key", "reject");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-unknown-key",
			properties(changed), "key set mismatch");
		changed = new TreeMap<>(values);
		changed.remove("selector.6.stack.female");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-missing-key",
			properties(changed), "key set mismatch");
		byte[] duplicate = concatenate(validBytes,
			"schema=voidscape-paperdoll-v2-selector-registry-properties/v1\n"
				.getBytes(StandardCharsets.US_ASCII));
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-duplicate",
			duplicate, "duplicate selector registry property schema");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-unsorted",
			unsortedProperties(validBytes), "keys must be sorted canonically");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-crlf",
			new String(validBytes, StandardCharsets.US_ASCII).replace("\n", "\r\n")
				.getBytes(StandardCharsets.US_ASCII), "terminal LF and no CR");
		byte[] nonAscii = validBytes.clone();
		nonAscii[0] = (byte) 0x80;
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-non-ascii",
			nonAscii, "must be ASCII");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-no-terminal-lf",
			copyOf(validBytes, validBytes.length - 1), "terminal LF and no CR");
		changed = new TreeMap<>(values);
		changed.put("pack.sha256", zeros());
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-pack-sha",
			properties(changed), "pack SHA-256");
		changed = new TreeMap<>(values);
		changed.put("template.sha256", zeros());
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-template-sha",
			properties(changed), "template.sha256 does not match sibling archive");
		changed = new TreeMap<>(values);
		changed.put("template.derived_masks.sha256", zeros());
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-mask-sha",
			properties(changed), "template.derived_masks.sha256 does not match sibling archive");
		changed = new TreeMap<>(values);
		changed.put("catalog.sha256", zeros());
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-catalog-sha",
			properties(changed), "catalog SHA-256");
		changed = new TreeMap<>(values);
		changed.put("base.male.body_appearance_id", "5");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-base-identity",
			properties(changed), "base.male.body_appearance_id must equal 2");
		changed = new TreeMap<>(values);
		changed.put("selector.1.stack.male", "female_rare_spikes");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-wrong-profile-stack",
			properties(changed), "has wrong base profile");
		changed = new TreeMap<>(values);
		changed.put("selector.1.stack.male", "male_mohawk");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-wrong-style-stack",
			properties(changed), "must equal male_rare_spikes");
		changed = new TreeMap<>(values);
		changed.put("selector.ids", "0,1,2,3,4,5,6,8");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-id-gap",
			properties(changed), "selector.ids must be canonical contiguous");
		changed = new TreeMap<>(values);
		changed.put("selector.ids", "0,1,2,3,4,5,6,6");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-id-duplicate",
			properties(changed), "selector.ids must be canonical contiguous");
		changed = new TreeMap<>(values);
		changed.put("selector.ids", selectorIdsThrough(256));
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-id-out-of-range",
			properties(changed), "selector.ids must be canonical contiguous");
		changed = selectorSevenValues(values);
		testSelectorSevenRuntime(validPack, outputDirectory, changed);
		changed = new TreeMap<>(values);
		changed.put("hat.v2_allowed_ids", "");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-hat-no-id",
			properties(changed), "hat.v2_allowed_ids must equal 0");
		changed = new TreeMap<>(values);
		changed.put("hat.v2_allowed_ids", "151");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-hat-151",
			properties(changed), "hat.v2_allowed_ids must equal 0");
		changed = new TreeMap<>(values);
		changed.put("hat.v2_allowed_ids", "245");
		expectSelectorRuntimeFailure(validPack, outputDirectory, "selector-hat-245",
			properties(changed), "hat.v2_allowed_ids must equal 0");

		Map<String, byte[]> coordinated = readZip(validPack);
		Map<String, String> coordinatedRegistry = registry(coordinated);
		coordinatedRegistry.put("template.sha256", zeros());
		coordinated.put("registry.properties", properties(coordinatedRegistry));
		changed = new TreeMap<>(values);
		changed.put("template.sha256", zeros());
		expectCoordinatedSelectorPackFailure(coordinated, changed, outputDirectory,
			"coordinated-zero-template", "template digest is not the approved");

		coordinated = readZip(validPack);
		coordinatedRegistry = registry(coordinated);
		coordinatedRegistry.put("render.stack.male_rare_spikes.assets",
			replaceAsset(coordinatedRegistry.get("render.stack.male_rare_spikes.assets"),
				"hair_rare_spikes", "hair_mohawk"));
		coordinated.put("registry.properties", properties(coordinatedRegistry));
		expectCoordinatedSelectorPackFailure(coordinated, values, outputDirectory,
			"coordinated-mismatched-hair", "must contain exactly hair asset hair_rare_spikes");

		coordinated = readZip(validPack);
		coordinatedRegistry = registry(coordinated);
		String maleRareAssets = coordinatedRegistry.get("render.stack.male_rare_spikes.assets");
		coordinatedRegistry.put("render.stack.male_rare_spikes.assets",
			moveAssetFirst(maleRareAssets, "hair_rare_spikes"));
		coordinated.put("registry.properties", properties(coordinatedRegistry));
		expectCoordinatedSelectorPackFailure(coordinated, values, outputDirectory,
			"coordinated-hair-before-head", "must draw its native slot-0 head before hair");

		coordinated = readZip(validPack);
		coordinatedRegistry = registry(coordinated);
		coordinatedRegistry.put("asset.hair_rare_spikes.kind", "hat");
		coordinatedRegistry.put("asset.hair_rare_spikes.paperdoll_slot", "5");
		coordinated.put("registry.properties", properties(coordinatedRegistry));
		expectCoordinatedSelectorPackFailure(coordinated, values, outputDirectory,
			"coordinated-native-slot5", "cannot substitute native slot-5");

		coordinated = readZip(validPack);
		coordinatedRegistry = registry(coordinated);
		String bodySkinPrefix = "asset.male_native_body.frame.00.skin";
		String bodySkinEntry = coordinatedRegistry.get(bodySkinPrefix + ".entry");
		byte[] changedRamp = coordinated.get(bodySkinEntry).clone();
		changeFirstVisiblePixelToGray(changedRamp, 0x77);
		coordinated.put(bodySkinEntry, changedRamp);
		coordinatedRegistry.put(bodySkinPrefix + ".sha256", sha256(changedRamp));
		coordinated.put("registry.properties", properties(coordinatedRegistry));
		expectCoordinatedSelectorPackFailure(coordinated, values, outputDirectory,
			"coordinated-body-skin-ramp", "outside neutral ramp");

		coordinated = readZip(validPack);
		coordinatedRegistry = registry(coordinated);
		String hairFramePrefix = "asset.hair_rare_spikes.frame.00.hair";
		String hairFrameEntry = coordinatedRegistry.get(hairFramePrefix + ".entry");
		byte[] emptyNativeFrame = emptySprite(0);
		coordinated.put(hairFrameEntry, emptyNativeFrame);
		coordinatedRegistry.put(hairFramePrefix + ".sha256", sha256(emptyNativeFrame));
		coordinatedRegistry.put(hairFramePrefix + ".empty", "true");
		coordinated.put("registry.properties", properties(coordinatedRegistry));
		expectCoordinatedSelectorPackFailure(coordinated, values, outputDirectory,
			"coordinated-empty-native-frame", "has no visible raster for frame 0");

		Map<String, byte[]> packOnlySlot5 = readZip(validPack);
		Map<String, String> packOnlyRegistry = registry(packOnlySlot5);
		packOnlyRegistry.put("asset.male_legacy_head.kind", "hat");
		packOnlyRegistry.put("asset.male_legacy_head.paperdoll_slot", "5");
		packOnlySlot5.put("registry.properties", properties(packOnlyRegistry));
		expectPackSuccess(write(outputDirectory, "pack-only-slot5-legal.orsc", packOnlySlot5));

		File nonsiblingDirectory = new File(outputDirectory, "selector-nonsibling");
		if (!nonsiblingDirectory.isDirectory() && !nonsiblingDirectory.mkdirs()) {
			throw new IOException("Unable to create selector nonsibling directory");
		}
		File nonsibling = new File(nonsiblingDirectory, PaperdollV2SelectorRegistry.FILE_NAME);
		Files.write(nonsibling.toPath(), validBytes);
		System.setProperty(PaperdollV2Runtime.PACK_PROPERTY, validPack.getAbsolutePath());
		System.setProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY, nonsibling.getAbsolutePath());
		PaperdollV2Runtime rejected = PaperdollV2Runtime.load();
		if (rejected.isActive() || rejected.getFallbackReason().indexOf("must be the sibling") < 0) {
			throw new AssertionError("Runtime accepted a non-sibling selector registry");
		}
	}

	private static Map<String, String> selectorSevenValues(Map<String, String> baseline) {
		Map<String, String> values = new TreeMap<>(baseline);
		values.put("selector.ids", "0,1,2,3,4,5,6,7");
		values.put("selector.7.style", "selector_seven_probe");
		values.put("selector.7.route", "v2");
		values.put("selector.7.base_profiles", "male,female");
		values.put("selector.7.eligibility.state", "proof-only");
		values.put("selector.7.eligibility.platforms", "pc");
		values.put("selector.7.qa_control.male", "male_control");
		values.put("selector.7.qa_control.female", "female_control");
		values.put("selector.7.stack.male", "male_selector_seven_probe");
		values.put("selector.7.stack.female", "female_selector_seven_probe");
		return values;
	}

	private static void testSelectorSevenRuntime(File validPack, File outputDirectory,
		Map<String, String> selectorValues) throws Exception {
		File directory = new File(outputDirectory, "selector-valid-seven");
		if (!directory.isDirectory() && !directory.mkdirs()) {
			throw new IOException("Unable to create selector-seven directory");
		}
		Map<String, byte[]> packEntries = readZip(validPack);
		Map<String, String> packRegistry = registry(packEntries);
		cloneHairAsset(packEntries, packRegistry, "hair_high_topknot",
			"hair_selector_seven_probe");
		packRegistry.put("asset.ids", packRegistry.get("asset.ids")
			+ ",hair_selector_seven_probe");
		packRegistry.put("render.stack.ids", packRegistry.get("render.stack.ids")
			+ ",male_selector_seven_probe,female_selector_seven_probe");
		packRegistry.put("render.stack.male_selector_seven_probe.mode", "live-controls");
		packRegistry.put("render.stack.male_selector_seven_probe.assets",
			"male_native_legs,male_native_body,male_native_head,hair_selector_seven_probe");
		packRegistry.put("render.stack.female_selector_seven_probe.mode", "live-controls");
		packRegistry.put("render.stack.female_selector_seven_probe.assets",
			"female_native_legs,female_native_body,female_native_head,hair_selector_seven_probe");
		packEntries.put("registry.properties", properties(packRegistry));
		File pack = write(directory, "Paperdoll_V2.orsc", packEntries);
		Map<String, String> boundSelector = new TreeMap<>(selectorValues);
		boundSelector.put("pack.sha256", sha256(Files.readAllBytes(pack.toPath())));
		File properties = new File(directory, PaperdollV2SelectorRegistry.FILE_NAME);
		Files.write(properties.toPath(), properties(boundSelector));
		System.setProperty(PaperdollV2Runtime.PACK_PROPERTY, pack.getAbsolutePath());
		System.setProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY, properties.getAbsolutePath());
		System.clearProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY);
		PaperdollV2Runtime runtime = PaperdollV2Runtime.load();
		if (!runtime.isSelectorModeActive() || runtime.getSelectorRegistry().getEntries().size() != 8
			|| runtime.getSelectorRegistry().getEntry(7) == null
			|| !"selector_seven_probe".equals(runtime.getSelectorRegistry().getEntry(7).getStyle())) {
			throw new AssertionError("Manifest-added selector 7 did not activate: "
				+ runtime.getFallbackReason());
		}
		for (PaperdollV2BaseProfile profile : new PaperdollV2BaseProfile[] {
			PaperdollV2BaseProfile.MALE, PaperdollV2BaseProfile.FEMALE}) {
			int[] layers = new int[12];
			profile.applyTo(layers);
			PaperdollV2Runtime.RenderSelection selected = runtime.preflight(layers, 7, 0,
				PaperdollV2Pack.WALK_WIDTH, PaperdollV2Pack.PLAYER_HEIGHT);
			if (selected == null || selected.getSelectorId() != 7
				|| selected.getBaseProfile() != profile) {
				throw new AssertionError("Manifest-added selector 7 failed " + profile.getId());
			}
		}
	}

	private static void cloneHairAsset(Map<String, byte[]> packEntries,
		Map<String, String> packRegistry, String sourceAssetId, String targetAssetId)
		throws IOException {
		String sourcePrefix = "asset." + sourceAssetId;
		String targetPrefix = "asset." + targetAssetId;
		Map<String, String> additions = new TreeMap<>();
		for (Map.Entry<String, String> entry : packRegistry.entrySet()) {
			if (!entry.getKey().startsWith(sourcePrefix + ".")) continue;
			String targetKey = targetPrefix + entry.getKey().substring(sourcePrefix.length());
			String targetValue = entry.getValue();
			if (targetKey.endsWith(".entry")) {
				String sourcePath = targetValue;
				targetValue = sourcePath.replace("sprites/" + sourceAssetId + "/",
					"sprites/" + targetAssetId + "/");
				byte[] sourceBytes = packEntries.get(sourcePath);
				if (sourceBytes == null) {
					throw new IOException("Synthetic selector fixture lacks " + sourcePath);
				}
				packEntries.put(targetValue, sourceBytes.clone());
			}
			additions.put(targetKey, targetValue);
		}
		if (additions.isEmpty()) {
			throw new IOException("Synthetic selector fixture lacks " + sourceAssetId);
		}
		packRegistry.putAll(additions);
	}

	private static void expectSelectorFallback(PaperdollV2Runtime runtime, int[] layers,
		int selectorId, String reasonPrefix) {
		PaperdollV2Runtime.PreflightObservation observation = runtime.inspectPreflight(layers,
			selectorId, 0, PaperdollV2Pack.WALK_WIDTH, PaperdollV2Pack.PLAYER_HEIGHT);
		if (observation.getSelection() != null
			|| !observation.getFallbackReason().startsWith(reasonPrefix)) {
			throw new AssertionError("Selector fallback differs for " + selectorId + ": "
				+ observation.getFallbackReason());
		}
	}

	private static void expectSelectorRuntimeFailure(File validPack, File outputDirectory,
		String name, byte[] propertiesBytes, String expectedMessage) throws Exception {
		File directory = new File(outputDirectory, name);
		if (!directory.isDirectory() && !directory.mkdirs()) {
			throw new IOException("Unable to create selector mutant directory " + directory);
		}
		File pack = new File(directory, "Paperdoll_V2.orsc");
		Files.copy(validPack.toPath(), pack.toPath(),
			java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		File properties = new File(directory, PaperdollV2SelectorRegistry.FILE_NAME);
		Files.write(properties.toPath(), propertiesBytes);
		System.setProperty(PaperdollV2Runtime.PACK_PROPERTY, pack.getAbsolutePath());
		System.setProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY, properties.getAbsolutePath());
		System.clearProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY);
		PaperdollV2Runtime rejected = PaperdollV2Runtime.load();
		if (rejected.isActive() || rejected.isPackValid()
			|| rejected.getFallbackReason().indexOf(expectedMessage) < 0) {
			throw new AssertionError("Selector mutant was not rejected as expected (" + name + "): "
				+ rejected.getFallbackReason());
		}
	}

	private static void expectCoordinatedSelectorPackFailure(Map<String, byte[]> packEntries,
		Map<String, String> selectorValues, File outputDirectory, String name,
		String expectedMessage) throws Exception {
		File directory = new File(outputDirectory, name);
		if (!directory.isDirectory() && !directory.mkdirs()) {
			throw new IOException("Unable to create coordinated mutant directory " + directory);
		}
		File pack = write(directory, "Paperdoll_V2.orsc", packEntries);
		Map<String, String> boundSelector = new TreeMap<>(selectorValues);
		boundSelector.put("pack.sha256", sha256(Files.readAllBytes(pack.toPath())));
		File properties = new File(directory, PaperdollV2SelectorRegistry.FILE_NAME);
		Files.write(properties.toPath(), properties(boundSelector));
		System.setProperty(PaperdollV2Runtime.PACK_PROPERTY, pack.getAbsolutePath());
		System.setProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY,
			properties.getAbsolutePath());
		System.clearProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY);
		PaperdollV2Runtime rejected = PaperdollV2Runtime.load();
		if (rejected.isActive() || rejected.isPackValid()
			|| rejected.getFallbackReason().indexOf(expectedMessage) < 0) {
			throw new AssertionError("Coordinated pack/selector mutant was not rejected (" + name
				+ "): " + rejected.getFallbackReason());
		}
	}

	private static String moveAssetFirst(String encoded, String assetId) throws IOException {
		if (encoded == null) throw new IOException("Fixture render stack assets are missing");
		String[] assets = encoded.split(",");
		StringBuilder result = new StringBuilder(assetId);
		boolean found = false;
		for (String asset : assets) {
			if (assetId.equals(asset)) {
				found = true;
				continue;
			}
			result.append(',').append(asset);
		}
		if (!found) throw new IOException("Fixture render stack lacks " + assetId);
		return result.toString();
	}

	private static String replaceAsset(String encoded, String oldAssetId, String newAssetId)
		throws IOException {
		if (encoded == null) throw new IOException("Fixture render stack assets are missing");
		String[] assets = encoded.split(",");
		StringBuilder result = new StringBuilder();
		boolean found = false;
		for (String asset : assets) {
			if (result.length() > 0) result.append(',');
			if (oldAssetId.equals(asset)) {
				result.append(newAssetId);
				found = true;
			} else {
				result.append(asset);
			}
		}
		if (!found) throw new IOException("Fixture render stack lacks " + oldAssetId);
		return result.toString();
	}

	private static void changeFirstVisiblePixelToGray(byte[] packed, int gray) throws IOException {
		for (int offset = 25; offset + 3 < packed.length; offset += 4) {
			if ((packed[offset] & 0xff) == 0) continue;
			packed[offset + 1] = (byte) gray;
			packed[offset + 2] = (byte) gray;
			packed[offset + 3] = (byte) gray;
			return;
		}
		throw new IOException("Fixture skin raster has no visible pixel to mutate");
	}

	private static byte[] emptySprite(int frame) {
		int logicalWidth = frame < 15 ? PaperdollV2Pack.WALK_WIDTH : PaperdollV2Pack.COMBAT_WIDTH;
		ByteBuffer bytes = ByteBuffer.allocate(29);
		bytes.putInt(1).putInt(1).put((byte) 1).putInt(0).putInt(0)
			.putInt(logicalWidth).putInt(PaperdollV2Pack.PLAYER_HEIGHT).putInt(0);
		return bytes.array();
	}

	private static void expectPackSuccess(File archive) throws Exception {
		try (FileInputStream input = new FileInputStream(archive)) {
			PaperdollV2Pack.read(input);
		}
	}

	private static void testNativeBaseRuntimeVariant(Map<String, byte[]> baseline,
		File outputDirectory, String stackId, PaperdollV2BaseProfile profile) throws Exception {
		Map<String, byte[]> variant = copy(baseline);
		Map<String, String> values = registry(variant);
		for (String asset : new String[]{"legacy_head", "legacy_body", "legacy_legs"}) {
			values.put("asset." + asset + ".source_mode", "native");
		}
		values.put("asset.legacy_head.kind", "head");
		values.put("asset.legacy_body.kind", "body");
		values.put("asset.legacy_legs.kind", "legs");
		values.put("render.stack.control.mode", "live-controls");
		if (!"rare_hair".equals(stackId)) {
			String assets = values.remove("render.stack.rare_hair.assets");
			String mode = values.remove("render.stack.rare_hair.mode");
			values.put("render.stack." + stackId + ".assets", assets);
			values.put("render.stack." + stackId + ".mode", mode);
			values.put("render.stack.ids", "control," + stackId);
		}
		variant.put("registry.properties", properties(values));
		File directory = new File(outputDirectory, "native-base-" + profile.getId());
		if (!directory.isDirectory() && !directory.mkdirs()) {
			throw new IOException("Unable to create native-base test directory");
		}
		File pack = write(directory, "Paperdoll_V2.orsc", variant);
		System.setProperty(PaperdollV2Runtime.PACK_PROPERTY, pack.getAbsolutePath());
		System.setProperty(PaperdollV2Runtime.STACK_PROPERTY, stackId);
		System.clearProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY);
		System.clearProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY);
		PaperdollV2Runtime runtime = PaperdollV2Runtime.load();
		if (!runtime.isActive() || runtime.getBaseProfile() != profile) {
			throw new AssertionError("Native-base " + profile.getId() + " runtime did not activate: "
				+ runtime.getFallbackReason());
		}
		assertSlots(runtime.getStack().getNativePaperdollSlots(), 0, 1, 2);
		int[] layers = new int[12];
		profile.applyTo(layers);
		if (!runtime.canRender(layers, 0, PaperdollV2Pack.WALK_WIDTH,
			PaperdollV2Pack.PLAYER_HEIGHT)) {
			throw new AssertionError("Compatible " + profile.getId() + " native base failed preflight: "
				+ runtime.getLastRenderFallbackReason());
		}
		layers[1] = profile == PaperdollV2BaseProfile.MALE
			? PaperdollV2Runtime.COMPATIBLE_FEMALE_BODY_APPEARANCE_ID
			: PaperdollV2Runtime.COMPATIBLE_MALE_BODY_APPEARANCE_ID;
		if (runtime.canRender(layers, 0, PaperdollV2Pack.WALK_WIDTH,
			PaperdollV2Pack.PLAYER_HEIGHT)
			|| runtime.getLastRenderFallbackReason().indexOf("incompatible-slot-1") < 0) {
			throw new AssertionError("Mismatched " + profile.getId() + " body passed native preflight");
		}
		profile.applyTo(layers);
		layers[2] = 4;
		if (runtime.canRender(layers, 0, PaperdollV2Pack.WALK_WIDTH,
			PaperdollV2Pack.PLAYER_HEIGHT)
			|| runtime.getLastRenderFallbackReason().indexOf("incompatible-slot-2") < 0) {
			throw new AssertionError("Mismatched legs passed native preflight");
		}
	}

	private static void assertSlots(int[] actual, int... expected) {
		if (actual.length != expected.length) {
			throw new AssertionError("Native slot count differs");
		}
		for (int i = 0; i < expected.length; i++) {
			if (actual[i] != expected[i]) throw new AssertionError("Native slot order differs");
		}
	}

	private static void expectFailure(File archive, String expectedMessage) throws Exception {
		try (FileInputStream input = new FileInputStream(archive)) {
			PaperdollV2Pack pack = PaperdollV2Pack.read(input);
			throw new AssertionError("Partial/malformed pack became renderable: " + archive + " / " + pack);
		} catch (IOException expected) {
			if (expected.getMessage() == null || !expected.getMessage().contains(expectedMessage)) {
				throw new AssertionError("Unexpected rejection for " + archive + ": " + expected.getMessage(), expected);
			}
		}
	}

	private static Map<String, byte[]> readZip(File archive) throws IOException {
		Map<String, byte[]> entries = new TreeMap<>();
		try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				byte[] buffer = new byte[8192];
				int read;
				while ((read = zip.read(buffer)) != -1) bytes.write(buffer, 0, read);
				if (entries.put(entry.getName(), bytes.toByteArray()) != null) {
					throw new IOException("Duplicate fixture ZIP entry " + entry.getName());
				}
			}
		}
		return entries;
	}

	private static Map<String, byte[]> copy(Map<String, byte[]> source) {
		Map<String, byte[]> result = new TreeMap<>();
		for (Map.Entry<String, byte[]> entry : source.entrySet()) {
			result.put(entry.getKey(), entry.getValue().clone());
		}
		return result;
	}

	private static Map<String, String> registry(Map<String, byte[]> entries) throws IOException {
		byte[] bytes = entries.get("registry.properties");
		if (bytes == null) throw new IOException("Fixture registry is missing");
		return parseProperties(bytes);
	}

	private static Map<String, String> parseProperties(byte[] bytes) throws IOException {
		Map<String, String> values = new TreeMap<>();
		String text = new String(bytes, StandardCharsets.US_ASCII);
		for (String line : text.split("\n")) {
			if (line.isEmpty()) continue;
			int equals = line.indexOf('=');
			if (equals <= 0) throw new IOException("Malformed fixture registry line");
			values.put(line.substring(0, equals), line.substring(equals + 1));
		}
		return values;
	}

	private static byte[] unsortedProperties(byte[] canonical) {
		String text = new String(canonical, StandardCharsets.US_ASCII);
		String[] lines = text.substring(0, text.length() - 1).split("\n");
		String first = lines[0];
		lines[0] = lines[1];
		lines[1] = first;
		StringBuilder result = new StringBuilder();
		for (String line : lines) result.append(line).append('\n');
		return result.toString().getBytes(StandardCharsets.US_ASCII);
	}

	private static byte[] concatenate(byte[] first, byte[] second) {
		byte[] result = new byte[first.length + second.length];
		System.arraycopy(first, 0, result, 0, first.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	private static byte[] copyOf(byte[] source, int length) {
		byte[] result = new byte[length];
		System.arraycopy(source, 0, result, 0, length);
		return result;
	}

	private static String zeros() {
		return "0000000000000000000000000000000000000000000000000000000000000000";
	}

	private static String selectorIdsThrough(int maximum) {
		StringBuilder ids = new StringBuilder();
		for (int selectorId = 0; selectorId <= maximum; selectorId++) {
			if (selectorId > 0) ids.append(',');
			ids.append(selectorId);
		}
		return ids.toString();
	}

	private static byte[] properties(Map<String, String> values) {
		StringBuilder text = new StringBuilder();
		for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
			text.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
		}
		return text.toString().getBytes(StandardCharsets.US_ASCII);
	}

	private static File write(File directory, String name, Map<String, byte[]> entries) throws IOException {
		File output = new File(directory, name);
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
			for (Map.Entry<String, byte[]> entry : new TreeMap<>(entries).entrySet()) {
				byte[] bytes = entry.getValue();
				CRC32 crc = new CRC32();
				crc.update(bytes);
				ZipEntry zipEntry = new ZipEntry(entry.getKey());
				zipEntry.setMethod(ZipEntry.STORED);
				zipEntry.setSize(bytes.length);
				zipEntry.setCompressedSize(bytes.length);
				zipEntry.setCrc(crc.getValue());
				zipEntry.setTime(0L);
				zip.putNextEntry(zipEntry);
				zip.write(bytes);
				zip.closeEntry();
			}
		}
		return output;
	}

	private static String sha256(byte[] bytes) throws Exception {
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
		StringBuilder result = new StringBuilder(64);
		for (byte value : digest) result.append(String.format("%02x", value & 0xff));
		return result.toString();
	}
}
