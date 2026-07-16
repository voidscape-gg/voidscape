package tools;

import com.openrsc.client.model.Sprite;
import orsc.appearance.v2.PaperdollV2LegacyCompatibility;
import orsc.appearance.v2.PaperdollV2Pack;
import orsc.appearance.v2.PaperdollV2Runtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standalone corruption and exact-RGBA checks for the PC-only fallback reader. */
public final class PaperdollV2LegacyCompatibilityAdversarialTest {
	private static int assertions;

	private PaperdollV2LegacyCompatibilityAdversarialTest() {}

	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			throw new IllegalArgumentException("usage: <valid-workspace> <repository-root> <mutant-root>");
		}
		File sourceWorkspace = new File(args[0]).getCanonicalFile();
		File repository = new File(args[1]).getCanonicalFile();
		File mutantRoot = new File(args[2]).getCanonicalFile();
		deleteTree(mutantRoot.toPath());
		Files.createDirectories(mutantRoot.toPath());

		File runtimeProperties = new File(sourceWorkspace,
			"build/legacy-compatibility/runtime.properties");
		PaperdollV2LegacyCompatibility valid = PaperdollV2LegacyCompatibility.read(
			runtimeProperties, repository);
		check(valid.isRootValid(), "valid root");
		check(valid.getLoadedStyleCount() == 6, "six valid styles");
		check(valid.getMaximumSelectorId() == 6, "maximum selector");
		Map<String, String> validValues = readProperties(runtimeProperties.toPath());
		check(valid.getPropertiesSha256().equals(sha256(Files.readAllBytes(runtimeProperties.toPath()))),
			"runtime properties digest");
		check(valid.getManifestSha256().equals(validValues.get("manifest.sha256")),
			"manifest binding");
		check(valid.getExpectedPackSha256().equals(validValues.get("pack.sha256")),
			"pack identity binding");
		try (FileInputStream input = new FileInputStream(
			new File(sourceWorkspace, "build/Paperdoll_V2.orsc"))) {
			PaperdollV2Pack pack = PaperdollV2Pack.read(input);
			check(valid.verifySourceDecimationAgainst(pack) == 108,
				"108 exact V2 phase-0 source decimations");
		}

		String[] styles = {"", "rare_spikes", "faded_buzzcut", "mohawk",
			"textured_crop", "slick_back_undercut", "high_topknot"};
		int frameCount = 0;
		for (int selector = 1; selector <= 6; selector++) {
			check(styles[selector].equals(valid.getStyle(selector)), "style " + selector);
			check(valid.hasStyle(selector), "published style " + selector);
			check(valid.getStyleRejectionReason(selector).length() == 0,
				"no valid rejection " + selector);
			for (int frame = 0; frame < 18; frame++) {
				Sprite sprite = valid.getFrame(selector, frame, 8, 0);
				check(sprite != null, "valid frame " + selector + "/" + frame);
				String prefix = String.format("selector.%d.frame.%02d", selector, frame);
				check(sprite.getSomething1() == Integer.parseInt(validValues.get(prefix + ".logical.width")),
					"logical width " + prefix);
				check(sprite.getSomething2() == 102, "logical height " + prefix);
				check(sprite.getXShift() == Integer.parseInt(validValues.get(prefix + ".crop.x")),
					"x shift " + prefix);
				check(sprite.getYShift() == Integer.parseInt(validValues.get(prefix + ".crop.y")),
					"y shift " + prefix);
				check(logicalRgbaSha256(sprite).equals(validValues.get(prefix + ".logical_rgba.sha256")),
					"logical RGBA parity " + prefix);
				frameCount++;
			}
		}
		check(frameCount == 108, "108 exact frames");
		check(valid.getFrame(1, -1, 8, 0) == null && valid.getFrame(1, 18, 8, 0) == null,
			"no frame clamping");
		check(valid.getFrame(1, 0, 1, 0) == null, "head 8 only");
		check(valid.getFrame(1, 0, 8, 151) == null && valid.getFrame(1, 0, 8, 245) == null,
			"nonzero hats suppress fallback hair");

		File missingPack = copyWorkspaceBuild(sourceWorkspace, new File(mutantRoot, "missing-pack"), false);
		PaperdollV2LegacyCompatibility withoutPack = readWorkspace(missingPack, repository);
		check(withoutPack.getLoadedStyleCount() == 6, "missing pack preserves fallback styles");
		File corruptPack = copyWorkspaceBuild(sourceWorkspace, new File(mutantRoot, "corrupt-pack"), true);
		Files.write(new File(corruptPack, "build/Paperdoll_V2.orsc").toPath(), new byte[] {1, 2, 3});
		PaperdollV2LegacyCompatibility withCorruptPack = readWorkspace(corruptPack, repository);
		check(withCorruptPack.getLoadedStyleCount() == 6, "corrupt pack preserves fallback styles");

		File missingPng = copyWorkspaceBuild(sourceWorkspace, new File(mutantRoot, "missing-png"), false);
		Files.delete(new File(missingPng,
			"build/legacy-compatibility/voidscape/hair/style_02/frame_07.png").toPath());
		assertWholeStyleRejected(readWorkspace(missingPng, repository), 2, "missing PNG");

		File alteredPng = copyWorkspaceBuild(sourceWorkspace, new File(mutantRoot, "altered-png"), false);
		Path alteredPngPath = new File(alteredPng,
			"build/legacy-compatibility/voidscape/hair/style_03/frame_04.png").toPath();
		byte[] alteredBytes = Files.readAllBytes(alteredPngPath);
		alteredBytes[alteredBytes.length - 1] ^= 1;
		Files.write(alteredPngPath, alteredBytes);
		assertWholeStyleRejected(readWorkspace(alteredPng, repository), 3, "altered PNG");

		File unknownSidecar = copyWorkspaceBuild(sourceWorkspace,
			new File(mutantRoot, "unknown-sidecar-key"), false);
		Path unknownSidecarPath = new File(unknownSidecar,
			"build/legacy-compatibility/voidscape/hair/style_04/frame_06.properties").toPath();
		Map<String, String> unknownValues = readProperties(unknownSidecarPath);
		unknownValues.put("unknown", "1");
		writeProperties(unknownSidecarPath, unknownValues);
		coordinateSidecarHash(unknownSidecar, "selector.4.frame.06", unknownSidecarPath);
		assertWholeStyleRejected(readWorkspace(unknownSidecar, repository), 4,
			"unknown sidecar key");

		File wrongShift = copyWorkspaceBuild(sourceWorkspace, new File(mutantRoot, "wrong-shift"), false);
		Path wrongShiftPath = new File(wrongShift,
			"build/legacy-compatibility/voidscape/hair/style_05/frame_11.properties").toPath();
		Map<String, String> wrongShiftValues = readProperties(wrongShiftPath);
		wrongShiftValues.put("xShift", Integer.toString(
			Integer.parseInt(wrongShiftValues.get("xShift")) + 1));
		writeProperties(wrongShiftPath, wrongShiftValues);
		coordinateSidecarHash(wrongShift, "selector.5.frame.11", wrongShiftPath);
		assertWholeStyleRejected(readWorkspace(wrongShift, repository), 5, "wrong sidecar shift");

		File badManifest = copyWorkspaceBuild(sourceWorkspace, new File(mutantRoot, "bad-manifest"), false);
		Files.write(new File(badManifest, "build/legacy-compatibility/manifest.json").toPath(),
			new byte[] {'x'}, java.nio.file.StandardOpenOption.APPEND);
		expectRootRejected(badManifest, repository, "manifest binding");

		File badSelector = copyWorkspaceBuild(sourceWorkspace, new File(mutantRoot, "bad-selector"), false);
		Files.write(new File(badSelector, "build/selector-registry.properties").toPath(),
			new byte[] {'x'}, java.nio.file.StandardOpenOption.APPEND);
		expectRootRejected(badSelector, repository, "selector binding");

		File coordinatedSelectorProperties = copyWorkspaceBuild(sourceWorkspace,
			new File(mutantRoot, "coordinated-selector-properties"), false);
		Path coordinatedSelectorPropertiesPath = new File(coordinatedSelectorProperties,
			"build/selector-registry.properties").toPath();
		Map<String, String> coordinatedSelectorValues = readProperties(
			coordinatedSelectorPropertiesPath);
		coordinatedSelectorValues.put("selector.1.style", "coordinated_mismatch");
		writeProperties(coordinatedSelectorPropertiesPath, coordinatedSelectorValues);
		updateRuntimeHash(coordinatedSelectorProperties,
			"selector_registry.properties.sha256", coordinatedSelectorPropertiesPath);
		expectRootRejected(coordinatedSelectorProperties, repository,
			"coordinated selector properties mismatch");

		File coordinatedSelectorJson = copyWorkspaceBuild(sourceWorkspace,
			new File(mutantRoot, "coordinated-selector-json"), false);
		Path coordinatedSelectorJsonPath = new File(coordinatedSelectorJson,
			"build/selector-registry.json").toPath();
		replaceUtf8(coordinatedSelectorJsonPath, "\"style\": \"rare_spikes\"",
			"\"style\": \"coordinated_mismatch\"");
		updateRuntimeHash(coordinatedSelectorJson, "selector_registry.json.sha256",
			coordinatedSelectorJsonPath);
		expectRootRejected(coordinatedSelectorJson, repository,
			"coordinated selector JSON mismatch");

		File coordinatedManifest = copyWorkspaceBuild(sourceWorkspace,
			new File(mutantRoot, "coordinated-manifest"), false);
		Path coordinatedManifestPath = new File(coordinatedManifest,
			"build/legacy-compatibility/manifest.json").toPath();
		replaceUtf8(coordinatedManifestPath,
			"\"png\": \"voidscape/hair/style_01/frame_00.png\"",
			"\"png\": \"voidscape/hair/style_01/frame_01.png\"");
		updateRuntimeHash(coordinatedManifest, "manifest.sha256", coordinatedManifestPath);
		expectRootRejected(coordinatedManifest, repository, "coordinated manifest mismatch");

		File coordinatedTemplateProvenance = copyWorkspaceBuild(sourceWorkspace,
			new File(mutantRoot, "coordinated-template-provenance"), false);
		Path coordinatedTemplateSelector = new File(coordinatedTemplateProvenance,
			"build/selector-registry.properties").toPath();
		Map<String, String> coordinatedTemplateSelectorValues = readProperties(
			coordinatedTemplateSelector);
		String alternateSha = repeat('0', 64);
		coordinatedTemplateSelectorValues.put("template.source_v1.sha256", alternateSha);
		writeProperties(coordinatedTemplateSelector, coordinatedTemplateSelectorValues);
		Path coordinatedTemplateRuntime = runtimePath(coordinatedTemplateProvenance);
		Map<String, String> coordinatedTemplateRuntimeValues = readProperties(
			coordinatedTemplateRuntime);
		coordinatedTemplateRuntimeValues.put("template.source_v1.sha256", alternateSha);
		coordinatedTemplateRuntimeValues.put("selector_registry.properties.sha256",
			sha256(Files.readAllBytes(coordinatedTemplateSelector)));
		writeProperties(coordinatedTemplateRuntime, coordinatedTemplateRuntimeValues);
		expectRootRejected(coordinatedTemplateProvenance, repository,
			"coordinated template source mismatch");

		File coordinatedSourceHash = copyWorkspaceBuild(sourceWorkspace,
			new File(mutantRoot, "coordinated-source-hash"), false);
		coordinateSourceDecimationHash(coordinatedSourceHash,
			"selector.1.frame.00", alternateSha);
		PaperdollV2LegacyCompatibility sourceHashCompatibility = readWorkspace(
			coordinatedSourceHash, repository);
		try (FileInputStream input = new FileInputStream(
			new File(sourceWorkspace, "build/Paperdoll_V2.orsc"))) {
			try {
				sourceHashCompatibility.verifySourceDecimationAgainst(PaperdollV2Pack.read(input));
				throw new AssertionError("coordinated source hash unexpectedly matched V2 pack");
			} catch (IOException expected) {
				assertions++;
			}
		}
		String packProperty = PaperdollV2Runtime.PACK_PROPERTY;
		String selectorProperty = PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY;
		String compatibilityProperty = PaperdollV2Runtime.LEGACY_COMPATIBILITY_PROPERTY;
		String workbenchProperty = "voidscape.workbench";
		try {
			System.setProperty(PaperdollV2Runtime.ENABLED_PROPERTY, "true");
			System.setProperty(workbenchProperty, "true");
			System.setProperty(packProperty, new File(sourceWorkspace,
				"build/Paperdoll_V2.orsc").getAbsolutePath());
			System.setProperty(selectorProperty, new File(sourceWorkspace,
				"build/selector-registry.properties").getAbsolutePath());
			System.setProperty(compatibilityProperty, new File(coordinatedSourceHash,
				"build/legacy-compatibility/runtime.properties").getAbsolutePath());
			PaperdollV2Runtime parityBoundRuntime = PaperdollV2Runtime.load();
			check(parityBoundRuntime.isPackValid(),
				"source-parity mismatch must not discard an otherwise valid V2 pack");
			check(!parityBoundRuntime.isLegacyCompatibilityUsable(),
				"runtime binding must fail closed on source-parity mismatch");
			check(parityBoundRuntime.getLegacyCompatibility().getRootReason()
				.contains("source-parity-rejected"),
				"runtime binding must expose the source-parity rejection reason");
		} finally {
			System.clearProperty(PaperdollV2Runtime.ENABLED_PROPERTY);
			System.clearProperty(packProperty);
			System.clearProperty(selectorProperty);
			System.clearProperty(compatibilityProperty);
			System.clearProperty(workbenchProperty);
		}

		File trailingSelectorJson = copyWorkspaceBuild(sourceWorkspace,
			new File(mutantRoot, "trailing-selector-json"), false);
		Path trailingSelectorJsonPath = new File(trailingSelectorJson,
			"build/selector-registry.json").toPath();
		Files.write(trailingSelectorJsonPath, "{}".getBytes(StandardCharsets.UTF_8),
			java.nio.file.StandardOpenOption.APPEND);
		updateRuntimeHash(trailingSelectorJson, "selector_registry.json.sha256",
			trailingSelectorJsonPath);
		expectRootRejected(trailingSelectorJson, repository, "trailing selector JSON");

		File duplicateManifestJson = copyWorkspaceBuild(sourceWorkspace,
			new File(mutantRoot, "duplicate-manifest-json"), false);
		Path duplicateManifestJsonPath = new File(duplicateManifestJson,
			"build/legacy-compatibility/manifest.json").toPath();
		replaceUtf8(duplicateManifestJsonPath, "\"shipping\": false",
			"\"shipping\": false,\n  \"shipping\": false");
		updateRuntimeHash(duplicateManifestJson, "manifest.sha256", duplicateManifestJsonPath);
		expectRootRejected(duplicateManifestJson, repository, "duplicate manifest JSON key");

		File typedManifestJson = copyWorkspaceBuild(sourceWorkspace,
			new File(mutantRoot, "typed-manifest-json"), false);
		Path typedManifestJsonPath = new File(typedManifestJson,
			"build/legacy-compatibility/manifest.json").toPath();
		replaceUtf8(typedManifestJsonPath, "\"overlayFrameCount\": 108",
			"\"overlayFrameCount\": \"108\"");
		updateRuntimeHash(typedManifestJson, "manifest.sha256", typedManifestJsonPath);
		expectRootRejected(typedManifestJson, repository, "mistyped manifest JSON value");

		File malformedManifestJson = copyWorkspaceBuild(sourceWorkspace,
			new File(mutantRoot, "malformed-manifest-json"), false);
		Path malformedManifestJsonPath = new File(malformedManifestJson,
			"build/legacy-compatibility/manifest.json").toPath();
		byte[] malformedManifestBytes = Files.readAllBytes(malformedManifestJsonPath);
		Files.write(malformedManifestJsonPath,
			java.util.Arrays.copyOf(malformedManifestBytes, malformedManifestBytes.length - 2));
		updateRuntimeHash(malformedManifestJson, "manifest.sha256", malformedManifestJsonPath);
		expectRootRejected(malformedManifestJson, repository, "malformed manifest JSON");

		File pathEscape = copyWorkspaceBuild(sourceWorkspace, new File(mutantRoot, "path-escape"), false);
		Path pathEscapeRuntime = runtimePath(pathEscape);
		Map<String, String> pathEscapeValues = readProperties(pathEscapeRuntime);
		pathEscapeValues.put("selector.1.frame.00.png.path", "../../../../outside.png");
		writeProperties(pathEscapeRuntime, pathEscapeValues);
		expectRootRejected(pathEscape, repository, "path escape");

		File unknownRuntime = copyWorkspaceBuild(sourceWorkspace,
			new File(mutantRoot, "unknown-runtime-key"), false);
		Path unknownRuntimePath = runtimePath(unknownRuntime);
		Map<String, String> unknownRuntimeValues = readProperties(unknownRuntimePath);
		unknownRuntimeValues.put("unknown.key", "1");
		writeProperties(unknownRuntimePath, unknownRuntimeValues);
		expectRootRejected(unknownRuntime, repository, "unknown runtime key");

		File symlink = copyWorkspaceBuild(sourceWorkspace, new File(mutantRoot, "symlink"), false);
		Path link = new File(symlink,
			"build/legacy-compatibility/voidscape/hair/style_06/frame_00.png").toPath();
		Path target = new File(symlink,
			"build/legacy-compatibility/voidscape/hair/style_06/frame_01.png").toPath();
		Files.delete(link);
		Files.createSymbolicLink(link, target.getFileName());
		expectRootRejected(symlink, repository, "symlink");

		assertRuntimeRetention(sourceWorkspace, missingPng, corruptPack);

		System.out.println("Paperdoll V2 legacy compatibility adversarial PASS: assertions="
			+ assertions + " frames=" + frameCount + " styles=6");
	}

	private static void assertRuntimeRetention(File validWorkspace, File styleMutant,
		File corruptPackWorkspace) throws Exception {
		String[] properties = {
			PaperdollV2Runtime.ENABLED_PROPERTY, "voidscape.workbench",
			PaperdollV2Runtime.PACK_PROPERTY, PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY,
			PaperdollV2Runtime.FORCE_LEGACY_PROPERTY,
			PaperdollV2Runtime.LEGACY_COMPATIBILITY_PROPERTY
		};
		try {
			System.setProperty(PaperdollV2Runtime.ENABLED_PROPERTY, "true");
			System.setProperty("voidscape.workbench", "true");
			System.setProperty(PaperdollV2Runtime.FORCE_LEGACY_PROPERTY, "true");
			System.setProperty(PaperdollV2Runtime.PACK_PROPERTY,
				new File(validWorkspace, "build/Paperdoll_V2.orsc").getAbsolutePath());
			System.setProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY,
				new File(validWorkspace, "build/selector-registry.properties").getAbsolutePath());
			System.setProperty(PaperdollV2Runtime.LEGACY_COMPATIBILITY_PROPERTY,
				runtimePath(validWorkspace).toFile().getAbsolutePath());
			PaperdollV2Runtime forcedLegacy = PaperdollV2Runtime.load();
			check(forcedLegacy.isPackValid() && forcedLegacy.isForceLegacy(),
				"forced-legacy runtime retains valid pack");
			check(forcedLegacy.isLegacyCompatibilityUsable()
				&& forcedLegacy.getLegacyCompatibility().getLoadedStyleCount() == 6,
				"forced-legacy runtime retains six compatibility styles");

			System.clearProperty(PaperdollV2Runtime.PACK_PROPERTY);
			System.clearProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY);
			PaperdollV2Runtime missingPack = PaperdollV2Runtime.load();
			check(!missingPack.isPackValid() && missingPack.isLegacyCompatibilityUsable(),
				"missing V2 pack retains validated compatibility");

			System.setProperty(PaperdollV2Runtime.PACK_PROPERTY,
				new File(corruptPackWorkspace, "build/Paperdoll_V2.orsc").getAbsolutePath());
			PaperdollV2Runtime corruptPack = PaperdollV2Runtime.load();
			check(!corruptPack.isPackValid() && corruptPack.isLegacyCompatibilityUsable(),
				"corrupt V2 pack retains validated compatibility");

			System.setProperty(PaperdollV2Runtime.PACK_PROPERTY,
				new File(validWorkspace, "build/Paperdoll_V2.orsc").getAbsolutePath());
			System.setProperty(PaperdollV2Runtime.SELECTOR_REGISTRY_PROPERTY,
				new File(validWorkspace, "build/selector-registry.properties").getAbsolutePath());
			System.setProperty(PaperdollV2Runtime.LEGACY_COMPATIBILITY_PROPERTY,
				runtimePath(styleMutant).toFile().getAbsolutePath());
			PaperdollV2Runtime styleFailure = PaperdollV2Runtime.load();
			check(styleFailure.isPackValid(), "style corruption does not disable V2 pack");
			check(styleFailure.getLegacyCompatibility().isRootValid()
				&& !styleFailure.getLegacyCompatibility().hasStyle(2)
				&& styleFailure.getLegacyCompatibility().hasStyle(1),
				"runtime compatibility preserves whole-style atomic rejection");
		} finally {
			for (String property : properties) System.clearProperty(property);
		}
	}

	private static PaperdollV2LegacyCompatibility readWorkspace(File workspace, File repository)
		throws IOException {
		return PaperdollV2LegacyCompatibility.read(runtimePath(workspace).toFile(), repository);
	}

	private static Path runtimePath(File workspace) {
		return new File(workspace, "build/legacy-compatibility/runtime.properties").toPath();
	}

	private static void assertWholeStyleRejected(PaperdollV2LegacyCompatibility value,
		int rejectedSelector, String label) {
		check(value.isRootValid(), label + " keeps root valid");
		check(!value.hasStyle(rejectedSelector), label + " rejects style");
		check(value.getStyleRejectionReason(rejectedSelector).length() > 0,
			label + " records reason");
		for (int frame = 0; frame < 18; frame++) {
			check(value.getFrame(rejectedSelector, frame, 8, 0) == null,
				label + " publishes no partial frame " + frame);
		}
		int unaffected = rejectedSelector == 1 ? 2 : 1;
		check(value.getFrame(unaffected, 0, 8, 0) != null,
			label + " preserves unrelated style");
	}

	private static void expectRootRejected(File workspace, File repository, String label)
		throws Exception {
		try {
			readWorkspace(workspace, repository);
			throw new AssertionError(label + " unexpectedly accepted");
		} catch (IOException expected) {
			assertions++;
		}
	}

	private static File copyWorkspaceBuild(File sourceWorkspace, File destination,
		boolean includePack) throws IOException {
		deleteTree(destination.toPath());
		Path sourceBuild = new File(sourceWorkspace, "build").toPath();
		Path destinationBuild = new File(destination, "build").toPath();
		Files.createDirectories(destinationBuild);
		copyTree(new File(sourceBuild.toFile(), "legacy-compatibility").toPath(),
			new File(destinationBuild.toFile(), "legacy-compatibility").toPath());
		Files.copy(new File(sourceBuild.toFile(), "selector-registry.json").toPath(),
			new File(destinationBuild.toFile(), "selector-registry.json").toPath(),
			StandardCopyOption.REPLACE_EXISTING);
		Files.copy(new File(sourceBuild.toFile(), "selector-registry.properties").toPath(),
			new File(destinationBuild.toFile(), "selector-registry.properties").toPath(),
			StandardCopyOption.REPLACE_EXISTING);
		if (includePack) {
			Files.copy(new File(sourceBuild.toFile(), "Paperdoll_V2.orsc").toPath(),
				new File(destinationBuild.toFile(), "Paperdoll_V2.orsc").toPath(),
				StandardCopyOption.REPLACE_EXISTING);
		}
		return destination;
	}

	private static void copyTree(Path source, Path destination) throws IOException {
		Files.walk(source).forEach(path -> {
			try {
				Path relative = source.relativize(path);
				Path target = destination.resolve(relative);
				if (Files.isDirectory(path)) Files.createDirectories(target);
				else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException error) {
				throw new CopyFailure(error);
			}
		});
	}

	private static void updateRuntimeHash(File workspace, String key, Path artifact)
		throws Exception {
		Path runtime = runtimePath(workspace);
		Map<String, String> values = readProperties(runtime);
		values.put(key, sha256(Files.readAllBytes(artifact)));
		writeProperties(runtime, values);
	}

	private static void coordinateSidecarHash(File workspace, String framePrefix,
		Path artifact) throws Exception {
		Path runtime = runtimePath(workspace);
		Map<String, String> runtimeValues = readProperties(runtime);
		String hashKey = framePrefix + ".sidecar.sha256";
		String pathKey = framePrefix + ".sidecar.path";
		String oldHash = runtimeValues.get(hashKey);
		String newHash = sha256(Files.readAllBytes(artifact));
		String sidecarPath = runtimeValues.get(pathKey);
		if (oldHash == null || sidecarPath == null) throw new IOException("missing frame contract");
		runtimeValues.put(hashKey, newHash);
		writeProperties(runtime, runtimeValues);

		Path manifest = new File(workspace, "build/legacy-compatibility/manifest.json").toPath();
		String text = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);
		String marker = "\"properties\": \"" + sidecarPath + "\"";
		int markerIndex = text.indexOf(marker);
		String hashField = "\"propertiesSha256\": \"" + oldHash + "\"";
		int hashIndex = markerIndex < 0 ? -1 : text.indexOf(hashField, markerIndex + marker.length());
		if (markerIndex < 0 || hashIndex < 0 || hashIndex - markerIndex > 512) {
			throw new IOException("manifest frame sidecar contract is absent");
		}
		text = text.substring(0, hashIndex)
			+ "\"propertiesSha256\": \"" + newHash + "\""
			+ text.substring(hashIndex + hashField.length());
		String artifactMarker = "\"path\": \"" + sidecarPath + "\"";
		int artifactIndex = text.indexOf(artifactMarker);
		String artifactHashField = "\"sha256\": \"" + oldHash + "\"";
		int artifactHashIndex = artifactIndex < 0 ? -1
			: text.indexOf(artifactHashField, artifactIndex + artifactMarker.length());
		if (artifactIndex < 0 || artifactHashIndex < 0 || artifactHashIndex - artifactIndex > 256) {
			throw new IOException("manifest artifact sidecar contract is absent");
		}
		text = text.substring(0, artifactHashIndex)
			+ "\"sha256\": \"" + newHash + "\""
			+ text.substring(artifactHashIndex + artifactHashField.length());
		Files.write(manifest, text.getBytes(StandardCharsets.UTF_8));
		runtimeValues = readProperties(runtime);
		runtimeValues.put("manifest.sha256", sha256(Files.readAllBytes(manifest)));
		writeProperties(runtime, runtimeValues);
	}

	private static void coordinateSourceDecimationHash(File workspace, String framePrefix,
		String newHash) throws Exception {
		Path runtime = runtimePath(workspace);
		Map<String, String> runtimeValues = readProperties(runtime);
		String hashKey = framePrefix + ".source_decimated_rgba.sha256";
		String oldHash = runtimeValues.get(hashKey);
		String pngPath = runtimeValues.get(framePrefix + ".png.path");
		if (oldHash == null || pngPath == null || oldHash.equals(newHash)) {
			throw new IOException("missing source-decimation frame contract");
		}
		runtimeValues.put(hashKey, newHash);
		writeProperties(runtime, runtimeValues);

		Path manifest = new File(workspace, "build/legacy-compatibility/manifest.json").toPath();
		String text = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);
		String marker = "\"png\": \"" + pngPath + "\"";
		int markerIndex = text.indexOf(marker);
		String hashField = "\"sourceDecimatedRgbaSha256\": \"" + oldHash + "\"";
		int hashIndex = markerIndex < 0 ? -1
			: text.indexOf(hashField, markerIndex + marker.length());
		if (markerIndex < 0 || hashIndex < 0 || hashIndex - markerIndex > 1024) {
			throw new IOException("manifest source-decimation contract is absent");
		}
		text = text.substring(0, hashIndex)
			+ "\"sourceDecimatedRgbaSha256\": \"" + newHash + "\""
			+ text.substring(hashIndex + hashField.length());
		Files.write(manifest, text.getBytes(StandardCharsets.UTF_8));
		runtimeValues = readProperties(runtime);
		runtimeValues.put("manifest.sha256", sha256(Files.readAllBytes(manifest)));
		writeProperties(runtime, runtimeValues);
	}

	private static String repeat(char value, int count) {
		StringBuilder result = new StringBuilder(count);
		for (int index = 0; index < count; index++) result.append(value);
		return result.toString();
	}

	private static void replaceUtf8(Path path, String original, String replacement)
		throws IOException {
		String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		int first = text.indexOf(original);
		if (first < 0 || text.indexOf(original, first + original.length()) >= 0) {
			throw new IOException("fixture replacement is absent or ambiguous: " + original);
		}
		Files.write(path, (text.substring(0, first) + replacement
			+ text.substring(first + original.length())).getBytes(StandardCharsets.UTF_8));
	}

	private static Map<String, String> readProperties(Path path) throws IOException {
		LinkedHashMap<String, String> result = new LinkedHashMap<>();
		for (String line : Files.readAllLines(path, StandardCharsets.US_ASCII)) {
			int equals = line.indexOf('=');
			if (equals > 0) result.put(line.substring(0, equals), line.substring(equals + 1));
		}
		return result;
	}

	private static void writeProperties(Path path, Map<String, String> values)
		throws IOException {
		List<String> keys = new ArrayList<>(values.keySet());
		Collections.sort(keys);
		StringBuilder output = new StringBuilder();
		for (String key : keys) output.append(key).append('=').append(values.get(key)).append('\n');
		Files.write(path, output.toString().getBytes(StandardCharsets.US_ASCII));
	}

	private static String logicalRgbaSha256(Sprite sprite) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		updateInt(digest, sprite.getSomething1());
		updateInt(digest, sprite.getSomething2());
		for (int y = 0; y < sprite.getSomething2(); y++) {
			for (int x = 0; x < sprite.getSomething1(); x++) {
				int sourceX = x - sprite.getXShift();
				int sourceY = y - sprite.getYShift();
				int argb = sourceX >= 0 && sourceY >= 0 && sourceX < sprite.getWidth()
					&& sourceY < sprite.getHeight()
					? sprite.getPixels()[sourceY * sprite.getWidth() + sourceX] : 0;
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

	private static String sha256(byte[] bytes) throws Exception {
		return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static String hex(byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) result.append(String.format("%02x", value & 255));
		return result.toString();
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) return;
		List<Path> paths = new ArrayList<>();
		Files.walk(root).forEach(paths::add);
		Collections.sort(paths, Collections.reverseOrder());
		for (Path path : paths) Files.deleteIfExists(path);
	}

	private static void check(boolean condition, String label) {
		if (!condition) throw new AssertionError(label);
		assertions++;
	}

	private static final class CopyFailure extends RuntimeException {
		CopyFailure(IOException cause) { super(cause); }
	}
}
