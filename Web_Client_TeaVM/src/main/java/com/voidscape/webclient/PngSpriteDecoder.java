package com.voidscape.webclient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import com.openrsc.client.model.Sprite;

final class PngSpriteDecoder {
	private static final byte[] SIGNATURE = new byte[]{
		(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};
	private static final int CHUNK_IHDR = 0x49484452;
	private static final int CHUNK_IDAT = 0x49444154;
	private static final int CHUNK_IEND = 0x49454E44;

	private PngSpriteDecoder() {
	}

	static Sprite decode(InputStream input) throws IOException {
		byte[] png = readAll(input);
		verifySignature(png);

		int width = 0;
		int height = 0;
		int bitDepth = 0;
		int colorType = 0;
		ByteArrayOutputStream idat = new ByteArrayOutputStream();

		int offset = SIGNATURE.length;
		while (offset + 12 <= png.length) {
			int length = readInt(png, offset);
			offset += 4;
			int type = readInt(png, offset);
			offset += 4;
			if (length < 0 || offset + length + 4 > png.length) {
				throw new IOException("Invalid PNG chunk length");
			}

			if (type == CHUNK_IHDR) {
				if (length != 13) {
					throw new IOException("Invalid PNG header length");
				}
				width = readInt(png, offset);
				height = readInt(png, offset + 4);
				bitDepth = png[offset + 8] & 0xFF;
				colorType = png[offset + 9] & 0xFF;
				int compression = png[offset + 10] & 0xFF;
				int filter = png[offset + 11] & 0xFF;
				int interlace = png[offset + 12] & 0xFF;
				if (width <= 0 || height <= 0 || bitDepth != 8 || compression != 0 || filter != 0 || interlace != 0) {
					throw new IOException("Unsupported PNG header");
				}
				if (colorType != 2 && colorType != 6) {
					throw new IOException("Unsupported PNG color type: " + colorType);
				}
			} else if (type == CHUNK_IDAT) {
				idat.write(png, offset, length);
			} else if (type == CHUNK_IEND) {
				break;
			}
			offset += length + 4;
		}

		if (width <= 0 || height <= 0 || idat.size() == 0) {
			throw new IOException("Incomplete PNG");
		}

		byte[] filtered = inflate(idat.toByteArray());
		int channels = colorType == 6 ? 4 : 3;
		int stride = width * channels;
		int expected = height * (stride + 1);
		if (filtered.length < expected) {
			throw new IOException("PNG image data is truncated");
		}

		int[] pixels = new int[width * height];
		byte[] previous = new byte[stride];
		byte[] current = new byte[stride];
		int source = 0;
		for (int y = 0; y < height; y++) {
			int filterType = filtered[source++] & 0xFF;
			System.arraycopy(filtered, source, current, 0, stride);
			source += stride;
			unfilter(filterType, current, previous, channels);
			copyPixels(current, pixels, y * width, width, colorType);

			byte[] swap = previous;
			previous = current;
			current = swap;
		}

		Sprite sprite = new Sprite(pixels, width, height);
		sprite.setSomething(width, height);
		sprite.setShift(0, 0);
		sprite.setRequiresShift(false);
		return sprite;
	}

	private static byte[] readAll(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = input.read(buffer)) != -1) {
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static void verifySignature(byte[] png) throws IOException {
		if (png.length < SIGNATURE.length) {
			throw new IOException("PNG data is too short");
		}
		for (int i = 0; i < SIGNATURE.length; i++) {
			if (png[i] != SIGNATURE[i]) {
				throw new IOException("Invalid PNG signature");
			}
		}
	}

	private static byte[] inflate(byte[] compressed) throws IOException {
		try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
			return readAll(input);
		}
	}

	private static int readInt(byte[] data, int offset) {
		return ((data[offset] & 0xFF) << 24)
			| ((data[offset + 1] & 0xFF) << 16)
			| ((data[offset + 2] & 0xFF) << 8)
			| (data[offset + 3] & 0xFF);
	}

	private static void unfilter(int filterType, byte[] current, byte[] previous, int bytesPerPixel) throws IOException {
		for (int i = 0; i < current.length; i++) {
			int raw = current[i] & 0xFF;
			int left = i >= bytesPerPixel ? current[i - bytesPerPixel] & 0xFF : 0;
			int up = previous[i] & 0xFF;
			int upperLeft = i >= bytesPerPixel ? previous[i - bytesPerPixel] & 0xFF : 0;
			int value;
			switch (filterType) {
				case 0:
					value = raw;
					break;
				case 1:
					value = raw + left;
					break;
				case 2:
					value = raw + up;
					break;
				case 3:
					value = raw + ((left + up) >> 1);
					break;
				case 4:
					value = raw + paeth(left, up, upperLeft);
					break;
				default:
					throw new IOException("Unsupported PNG filter: " + filterType);
			}
			current[i] = (byte) value;
		}
	}

	private static int paeth(int left, int up, int upperLeft) {
		int estimate = left + up - upperLeft;
		int leftDistance = Math.abs(estimate - left);
		int upDistance = Math.abs(estimate - up);
		int upperLeftDistance = Math.abs(estimate - upperLeft);
		if (leftDistance <= upDistance && leftDistance <= upperLeftDistance) {
			return left;
		}
		return upDistance <= upperLeftDistance ? up : upperLeft;
	}

	private static void copyPixels(byte[] current, int[] pixels, int dest, int width, int colorType) {
		int source = 0;
		for (int x = 0; x < width; x++) {
			int red = current[source++] & 0xFF;
			int green = current[source++] & 0xFF;
			int blue = current[source++] & 0xFF;
			int alpha = 255;
			if (colorType == 6) {
				alpha = current[source++] & 0xFF;
			}
			pixels[dest + x] = (alpha << 24) | (red << 16) | (green << 8) | blue;
		}
	}
}
