package orsc.util;

import org.teavm.jso.JSBody;

public final class Utils {
	private static long timeCorrection;
	private static long lastTimeUpdate;

	private Utils() {
	}

	public static void openWebpage(String url) {
		openWindow(url);
	}

	public static synchronized long currentTimeMillis() {
		long now = System.currentTimeMillis();
		if (now < lastTimeUpdate) {
			timeCorrection += lastTimeUpdate - now;
		}
		lastTimeUpdate = now;
		return now + timeCorrection;
	}

	public static String stripHtml(String text) {
		return text == null ? "" : text.replaceAll("\\<.*?\\>", "");
	}

	public static int getJavaVersion() {
		return 8;
	}

	public static boolean isWindowsOS() {
		return false;
	}

	public static boolean isModernWindowsOS() {
		return false;
	}

	public static boolean isMacOS() {
		return false;
	}

	@JSBody(params = { "url" }, script = "window.open(url, '_blank', 'noopener');")
	private static native void openWindow(String url);
}
