package orsc;

public class osConfig {
	public static final boolean F_WEB_BUILD = false;
	/* Android: */
	public static boolean F_ANDROID_BUILD = true; // This MUST be true if Android client or it will crash on launch, needs to be set as public for the Android client to use
	public static final String DL_URL = "voidscape"; // needs to be set as public for the Android client to use
	public static final String ANDROID_DOWNLOAD_PATH = ""; // Optional future APK update endpoint.
	public static final String CACHE_URL = ""; // Optional future cache endpoint; bundled cache is used first.
	public static final String MD5_TABLENAME = "MD5.SUM";
	public static final int ANDROID_CLIENT_VERSION = 1; // Important! Depends on web server android_version.txt to check for an updated version
	public static final String VOIDSCAPE_PUBLIC_HOST = "voidscape.gg";
	public static final String VOIDSCAPE_EMULATOR_HOST = "10.0.2.2";
	public static final String VOIDSCAPE_LAN_HOST = "192.168.1.100";
	public static final String VOIDSCAPE_DEFAULT_PORT = "43596";
	public static final String VOIDSCAPE_PORTAL_ACCOUNT_URL = "https://voidscape.gg/portal?auth=login";
	public static final String VOIDSCAPE_PORTAL_RECOVERY_URL = "https://voidscape.gg/portal?auth=recovery";
	// Google Play launch build: account/card redemption remains available, but purchasing is not linked in-app.
	public static final String VOIDSCAPE_PORTAL_SHOP_URL = "";
	public static boolean F_SHOWING_KEYBOARD = false;
	public static int C_STATUS_BAR = 0; // default to icons and text
	public static boolean C_HOLD_AND_CHOOSE = true;
	public static int C_LONG_PRESS_TIMER = 5; // default hold timer setting
	public static int C_MENU_SIZE = 3; // default font choice
	public static int C_SWIPE_TO_SCROLL_MODE = 1; // default to normal
	public static int C_SWIPE_TO_ROTATE_MODE = 1; // default to normal
	public static int C_SWIPE_TO_ZOOM_MODE = 1; // default to normal
	public static int C_VOLUME_FUNCTION = 0; // default as rotate
	public static boolean C_ANDROID_INV_TOGGLE = false;
	public static int C_LAST_ZOOM = 75;
}
