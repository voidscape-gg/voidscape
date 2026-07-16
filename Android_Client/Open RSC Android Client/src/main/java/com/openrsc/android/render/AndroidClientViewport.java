package com.openrsc.android.render;

final class AndroidClientViewport {
	static final int BASE_WIDTH = 512;
	static final int BASE_FULL_HEIGHT = 346;
	static final int MAX_LOGICAL_SIZE = 1152;

	private AndroidClientViewport() {
	}

	static Metrics metricsForSurface(
		int surfaceWidth,
		int surfaceHeight,
		SafeInsets requestedInsets,
		float density
	) {
		int safeSurfaceWidth = Math.max(1, surfaceWidth);
		int safeSurfaceHeight = Math.max(1, surfaceHeight);
		SafeInsets insets = sanitizeInsets(
			requestedInsets == null ? SafeInsets.NONE : requestedInsets,
			safeSurfaceWidth,
			safeSurfaceHeight
		);
		int contentWidth = Math.max(1, safeSurfaceWidth - insets.left - insets.right);
		int contentHeight = Math.max(1, safeSurfaceHeight - insets.top - insets.bottom);
		TargetSize target = targetForSurface(contentWidth, contentHeight);
		Transform transform = transformInContent(
			contentWidth,
			contentHeight,
			insets.left,
			insets.top,
			target.width,
			target.fullHeight
		);
		float safeDensity = density > 0.0f ? density : 1.0f;
		return new Metrics(
			safeSurfaceWidth,
			safeSurfaceHeight,
			contentWidth,
			contentHeight,
			insets,
			target,
			transform,
			safeDensity
		);
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
		return transformInContent(surfaceWidth, surfaceHeight, 0, 0, logicalWidth, logicalFullHeight);
	}

	static Transform transformInContent(
		int contentWidth,
		int contentHeight,
		int contentLeft,
		int contentTop,
		int logicalWidth,
		int logicalFullHeight
	) {
		float safeLogicalWidth = Math.max(1, logicalWidth);
		float safeLogicalHeight = Math.max(1, logicalFullHeight);
		float safeContentWidth = Math.max(1, contentWidth);
		float safeContentHeight = Math.max(1, contentHeight);
		float scale = Math.max(0.01f, Math.min(
			safeContentWidth / safeLogicalWidth,
			safeContentHeight / safeLogicalHeight
		));
		float offsetX = contentLeft + (safeContentWidth - safeLogicalWidth * scale) / 2.0f;
		float offsetY = contentTop + (safeContentHeight - safeLogicalHeight * scale) / 2.0f;
		return new Transform(scale, offsetX, offsetY);
	}

	static int logicalPixelsForDp(int dp, float density, Transform transform) {
		if (dp <= 0) {
			return 1;
		}
		float safeDensity = density > 0.0f ? density : 1.0f;
		float safeScale = transform == null ? 1.0f : Math.max(0.01f, transform.scale);
		return Math.max(1, (int) Math.ceil(dp * safeDensity / safeScale));
	}

	private static SafeInsets sanitizeInsets(SafeInsets insets, int surfaceWidth, int surfaceHeight) {
		int left = clamp(insets.left, 0, Math.max(0, surfaceWidth - 1));
		int right = clamp(insets.right, 0, Math.max(0, surfaceWidth - left - 1));
		int top = clamp(insets.top, 0, Math.max(0, surfaceHeight - 1));
		int bottom = clamp(insets.bottom, 0, Math.max(0, surfaceHeight - top - 1));
		return new SafeInsets(left, top, right, bottom);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	static final class SafeInsets {
		static final SafeInsets NONE = new SafeInsets(0, 0, 0, 0);

		final int left;
		final int top;
		final int right;
		final int bottom;

		SafeInsets(int left, int top, int right, int bottom) {
			this.left = Math.max(0, left);
			this.top = Math.max(0, top);
			this.right = Math.max(0, right);
			this.bottom = Math.max(0, bottom);
		}

		boolean sameAs(SafeInsets other) {
			return other != null
				&& left == other.left
				&& top == other.top
				&& right == other.right
				&& bottom == other.bottom;
		}
	}

	static final class Metrics {
		final int surfaceWidth;
		final int surfaceHeight;
		final int contentWidth;
		final int contentHeight;
		final SafeInsets insets;
		final TargetSize target;
		final Transform transform;
		final float density;

		private Metrics(
			int surfaceWidth,
			int surfaceHeight,
			int contentWidth,
			int contentHeight,
			SafeInsets insets,
			TargetSize target,
			Transform transform,
			float density
		) {
			this.surfaceWidth = surfaceWidth;
			this.surfaceHeight = surfaceHeight;
			this.contentWidth = contentWidth;
			this.contentHeight = contentHeight;
			this.insets = insets;
			this.target = target;
			this.transform = transform;
			this.density = density;
		}

		Transform transformFor(int logicalWidth, int logicalFullHeight) {
			if (logicalWidth == target.width && logicalFullHeight == target.fullHeight) {
				return transform;
			}
			return transformInContent(
				contentWidth,
				contentHeight,
				insets.left,
				insets.top,
				logicalWidth,
				logicalFullHeight
			);
		}

		boolean sameAs(Metrics other) {
			return other != null
				&& surfaceWidth == other.surfaceWidth
				&& surfaceHeight == other.surfaceHeight
				&& contentWidth == other.contentWidth
				&& contentHeight == other.contentHeight
				&& insets.sameAs(other.insets)
				&& target.width == other.target.width
				&& target.fullHeight == other.target.fullHeight
				&& Float.floatToIntBits(transform.scale) == Float.floatToIntBits(other.transform.scale)
				&& Float.floatToIntBits(density) == Float.floatToIntBits(other.density);
		}
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
