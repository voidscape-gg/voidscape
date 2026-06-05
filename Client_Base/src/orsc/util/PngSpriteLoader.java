package orsc.util;

import com.openrsc.client.model.Sprite;

import java.io.File;
import java.lang.reflect.Method;

public final class PngSpriteLoader {

	private PngSpriteLoader() {
	}

	public static Sprite read(File file) {
		return read(file, false);
	}

	public static Sprite readArgb(File file) {
		return read(file, true);
	}

	private static Sprite read(File file, boolean preserveAlpha) {
		try {
			Class<?> imageIoClass = Class.forName("javax.imageio.ImageIO");
			Method read = imageIoClass.getMethod("read", File.class);
			Object image = read.invoke(null, file);
			if (image == null) {
				return null;
			}

			Class<?> imageClass = image.getClass();
			int width = ((Number) imageClass.getMethod("getWidth").invoke(image)).intValue();
			int height = ((Number) imageClass.getMethod("getHeight").invoke(image)).intValue();
			int[] pixels = new int[width * height];
			Method getRgb = imageClass.getMethod("getRGB",
				int.class, int.class, int.class, int.class, int[].class, int.class, int.class);
			getRgb.invoke(image, 0, 0, width, height, pixels, 0, width);
			if (!preserveAlpha) {
				for (int i = 0; i < pixels.length; i++) {
					if ((pixels[i] >>> 24) < 128) {
						pixels[i] = 0;
					}
				}
			}
			return new Sprite(pixels, width, height);
		} catch (Exception ex) {
			return null;
		}
	}
}
