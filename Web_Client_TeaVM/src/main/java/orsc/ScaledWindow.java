package orsc;

public final class ScaledWindow {
	private static final ScaledWindow INSTANCE = new ScaledWindow();

	public enum ScalingAlgorithm {
		INTEGER_SCALING,
		BILINEAR_INTERPOLATION,
		BICUBIC_INTERPOLATION
	}

	private ScaledWindow() {
	}

	public static ScaledWindow getInstance() {
		return INSTANCE;
	}

	public void validateAppletSize() {
	}

	public static int getViewportPresetCount() {
		return 1;
	}

	public static int getViewportPresetIndex() {
		return 0;
	}

	public static void setViewportPresetIndex(int index) {
	}

	public static String getViewportPresetLabel() {
		return "Web 640x480";
	}

	public static String getViewportPresetLabel(int index) {
		return getViewportPresetLabel();
	}

	public void applyViewportPreset(int index) {
	}

	public void applyStartupScalingDefaults(boolean hasSavedScalingScalar) {
	}
}
