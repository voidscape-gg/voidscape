package orsc;

// Stub class for feature that is disabled on Android
public class ScaledWindow {

	private static ScaledWindow instance = null;
	private ScaledWindow() {
		instance = this;
	}
	public enum ScalingAlgorithm {
		INTEGER_SCALING,
		BILINEAR_INTERPOLATION,
		BICUBIC_INTERPOLATION
	}
	public static ScaledWindow getInstance() {
		if (instance == null) {
			synchronized (ScaledWindow.class) {
				instance = new ScaledWindow();
			}
		}
		return instance;
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
		return "Phone";
	}
	public static int getBaseViewportWidth() {
		return 512;
	}
	public static int getBaseViewportHeight() {
		return 346;
	}
	public static String getViewportPresetLabel(int index) {
		return getViewportPresetLabel();
	}
	public void applyViewportPreset(int index) {
	}
	public void applyStartupScalingDefaults(boolean hasSavedScalingScalar) {
	}
}
