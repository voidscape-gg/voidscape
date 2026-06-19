package orsc;

import com.openrsc.client.model.Sprite;
import orsc.util.PngSpriteLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

final class VoidscapeHairOverlay {
	private static final int FRAME_COUNT = 18;
	private static final Map<Integer, Sprite[]> CACHE = new HashMap<>();

	private VoidscapeHairOverlay() {
	}

	static Sprite getFrame(int style, int frameOffset) {
		if (style <= 0) return null;
		Sprite[] frames = CACHE.get(style);
		if (frames == null) {
			frames = loadStyle(style);
			CACHE.put(style, frames);
		}
		if (frames.length == 0) return null;
		int offset = Math.max(0, Math.min(frames.length - 1, frameOffset));
		Sprite sprite = frames[offset];
		return sprite != null ? sprite : frames[0];
	}

	private static Sprite[] loadStyle(int style) {
		Sprite[] frames = new Sprite[FRAME_COUNT];
		File styleDir = new File(Config.F_CACHE_DIR + File.separator + "voidscape" + File.separator
			+ "hair" + File.separator + String.format("style_%02d", style));
		for (int frame = 0; frame < FRAME_COUNT; frame++) {
			frames[frame] = loadFrame(styleDir, frame);
		}
		return frames;
	}

	private static Sprite loadFrame(File styleDir, int frame) {
		File png = new File(styleDir, String.format("frame_%02d.png", frame));
		if (!png.isFile() && frame != 0) {
			png = new File(styleDir, "frame_00.png");
		}
		if (!png.isFile()) return null;

		try {
			Sprite sprite = PngSpriteLoader.readArgb(png);
			if (sprite == null) return null;
			applySidecar(sprite, sidecarFor(png, frame));
			return sprite;
		} catch (RuntimeException | IOException ex) {
			return null;
		}
	}

	private static File sidecarFor(File png, int frame) {
		File properties = new File(png.getParentFile(), String.format("frame_%02d.properties", frame));
		if (!properties.isFile() && frame != 0) {
			properties = new File(png.getParentFile(), "frame_00.properties");
		}
		return properties;
	}

	private static void applySidecar(Sprite sprite, File propertiesFile) throws IOException {
		if (!propertiesFile.isFile()) {
			sprite.setRequiresShift(true);
			sprite.setShift(24, 9);
			sprite.setSomething(64, 102);
			return;
		}

		Properties properties = new Properties();
		try (FileInputStream input = new FileInputStream(propertiesFile)) {
			properties.load(input);
		}
		sprite.setRequiresShift(Boolean.parseBoolean(properties.getProperty("requiresShift", "true")));
		sprite.setShift(readInt(properties, "xShift", 24), readInt(properties, "yShift", 9));
		sprite.setSomething(readInt(properties, "something1", 64), readInt(properties, "something2", 102));
	}

	private static int readInt(Properties properties, String key, int fallback) {
		try {
			return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}
}
