package com.openrsc.android.render;

final class AndroidClientViewport {
	static final int BASE_WIDTH = 512;
	static final int BASE_FULL_HEIGHT = 346;
	static final int MAX_LOGICAL_SIZE = 1152;

	private AndroidClientViewport() {
	}

	static TargetSize targetForSurface(int surfaceWidth, int surfaceHeight) {
		if (surfaceWidth <= 0 || surfaceHeight <= 0) {
			return new TargetSize(BASE_WIDTH, BASE_FULL_HEIGHT, false, false);
		}

		if (surfaceHeight > surfaceWidth) {
			int rawFullHeight = Math.round(BASE_WIDTH * ((float) surfaceHeight / (float) surfaceWidth));
			return new TargetSize(
				BASE_WIDTH,
				clamp(rawFullHeight, BASE_FULL_HEIGHT, MAX_LOGICAL_SIZE),
				true,
				rawFullHeight > MAX_LOGICAL_SIZE);
		}

		int rawWidth = Math.round(BASE_FULL_HEIGHT * ((float) surfaceWidth / (float) surfaceHeight));
		return new TargetSize(
			clamp(rawWidth, BASE_WIDTH, MAX_LOGICAL_SIZE),
			BASE_FULL_HEIGHT,
			false,
			rawWidth > MAX_LOGICAL_SIZE);
	}

	static Transform transform(int surfaceWidth, int surfaceHeight, int logicalWidth, int logicalFullHeight) {
		float safeLogicalWidth = Math.max(1, logicalWidth);
		float safeLogicalHeight = Math.max(1, logicalFullHeight);
		float scale = Math.max(0.01f, Math.min(surfaceWidth / safeLogicalWidth, surfaceHeight / safeLogicalHeight));
		float offsetX = (surfaceWidth - safeLogicalWidth * scale) / 2.0f;
		float offsetY = (surfaceHeight - safeLogicalHeight * scale) / 2.0f;
		return new Transform(scale, offsetX, offsetY);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	static final class TargetSize {
		final int width;
		final int fullHeight;
		final boolean portrait;
		final boolean capped;

		private TargetSize(int width, int fullHeight, boolean portrait, boolean capped) {
			this.width = width;
			this.fullHeight = fullHeight;
			this.portrait = portrait;
			this.capped = capped;
		}
	}

	static final class Transform {
		final float scale;
		final float offsetX;
		final float offsetY;

		private Transform(float scale, float offsetX, float offsetY) {
			this.scale = scale;
			this.offsetX = offsetX;
			this.offsetY = offsetY;
		}
	}
}
