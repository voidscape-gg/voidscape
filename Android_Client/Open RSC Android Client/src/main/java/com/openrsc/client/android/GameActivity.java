package com.openrsc.client.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import com.openrsc.android.render.InputImpl;
import com.openrsc.android.render.RSCBitmapSurfaceView;
import com.openrsc.android.security.AndroidCredentialStore;
import com.openrsc.client.R;
import com.openrsc.client.model.Sprite;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import orsc.Config;
import orsc.PacketHandler;
import orsc.mudclient;
import orsc.multiclient.ClientPort;
import orsc.multiclient.CredentialStore;
import orsc.osConfig;
import orsc.soundPlayer;
import orsc.util.Utils;

public class GameActivity extends Activity implements ClientPort {

	private static final Object RETAINED_CLIENT_LOCK = new Object();
	private static final int MAX_KEYBOARD_SHOW_ATTEMPTS = 3;
	private static final long KEYBOARD_SHOW_RETRY_DELAY_MS = 120L;
	private static final Object DUEL_PROOF_RANDOM_LOCK = new Object();
	private static SecureRandom duelProofSecureRandom;
	private static volatile mudclient retainedClient;
	private static volatile GameActivity retainedClientOwner;
	// Guarded by RETAINED_CLIENT_LOCK with the retained client/owner pair. These
	// survive Activity replacement so a rotation cannot erase a real background
	// interval before the replacement Activity resumes the same game session.
	private static boolean retainedClientBackgrounded;
	private static long retainedClientBackgroundedAtMillis;
	private static final int NETWORK_NONE = 0;
	private static final int NETWORK_CELLULAR = 1;
	private static final int NETWORK_WIFI = 2;
	private static final int NETWORK_OTHER = 3;

    private InputImpl inputImpl;
    private mudclient mudclient;
    private RSCBitmapSurfaceView gameView;

	private boolean loadedReceivers = false;
	private volatile boolean appInBackground = false;
	private volatile long backgroundedAtMillis = 0L;
	private volatile long suppressReconnectOverlayUntilMillis = 0L;
	private int batteryLevel;
	private int batteryScale;
	private boolean batteryCharging;
	private final Object networkLock = new Object();
	private final Map<Network, Integer> validatedNetworks = new HashMap<>();
	private volatile int connectivityKind = NETWORK_NONE;
	private CredentialStore credentialStore = CredentialStore.unsupported();

    private boolean hadSideMenu;
	private final Object keyboardLock = new Object();
	private volatile boolean keyboardRequested;
	private volatile boolean keyboardShowing;
	private boolean keyboardDismissRequested;
	private boolean sideMenuHiddenForKeyboard;
	private int keyboardRequestGeneration;
	private int keyboardShowAttempts;
	private Object backCallback;

	final BroadcastReceiver batteryReceiver = new BatteryReceiver();

	final ConnectivityManager.NetworkCallback networkManager = new NetworkManager();

	class BatteryReceiver extends BroadcastReceiver {
		BatteryReceiver() {
		}

		public void onReceive(Context context, Intent intent) {
			batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
			batteryScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
			int intExtra = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			batteryCharging = intExtra == BatteryManager.BATTERY_STATUS_CHARGING || intExtra == BatteryManager.BATTERY_STATUS_FULL;
		}
	}

	class NetworkManager extends ConnectivityManager.NetworkCallback {
		NetworkManager() {
		}

		public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
			networkChange(network, networkCapabilities);
		}

		public void onLost(Network network) {
			networkChange(network, (NetworkCapabilities) null);
		}
	}

	    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		configureMobileViewportWindow();
		Utils.attach(this);
		credentialStore = new AndroidCredentialStore(this);

        osConfig.F_ANDROID_BUILD = true;
        File externalSmokeDir = getExternalFilesDir(null);
        Config.F_ANDROID_SMOKE_DIR = externalSmokeDir == null ? "" : externalSmokeDir.getAbsolutePath();

        gameView = new RSCBitmapSurfaceView(this) {

				@Override
            public void setTitle(String title) {

            }

            @Override
            public void setIconImage(String serverName) {

            }
        };
        setContentView(gameView);

		synchronized (RETAINED_CLIENT_LOCK) {
			mudclient existingClient = retainedClient;
			if (existingClient != null && existingClient.threadState >= 0
				&& existingClient.clientBaseThread != null
				&& existingClient.clientBaseThread.isAlive()) {
				setMudclient(existingClient);
			} else {
				setMudclient(new mudclient(this));
				mudclient.packetHandler = new PacketHandler(mudclient);

				if (mudclient.threadState >= 0) mudclient.threadState = 0;

				mudclient.startMainThread();
				retainedClient = mudclient;
				retainedClientBackgrounded = false;
				retainedClientBackgroundedAtMillis = 0L;
			}
			retainedClientOwner = this;
			mudclient.clientPort = this;
			appInBackground = retainedClientBackgrounded;
			backgroundedAtMillis = retainedClientBackgroundedAtMillis;
		}

		setInputImpl(new InputImpl(mudclient, gameView));
		gameView.refreshViewportMetrics();

		//Utils.context = getApplicationContext();

		// Hide the bars and stuff
		updateHideUi();
		registerBackHandler();
		setReceivers();
    }

	private void configureMobileViewportWindow() {
		Window window = getWindow();
		window.setSoftInputMode(
			WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
				| WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
		);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			WindowManager.LayoutParams attributes = window.getAttributes();
			attributes.layoutInDisplayCutoutMode =
				WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			window.setAttributes(attributes);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			window.setDecorFitsSystemWindows(false);
		}
	}

	@Override
	protected void onDestroy() {
		unsetReceivers();
		unregisterBackHandler();
		soundPlayer.stopAll();
		Utils.detach(this);
		if (isFinishing() && !isChangingConfigurations()) {
			shutdownClientThread();
		}
		inputImpl = null;
		gameView = null;
		super.onDestroy();
	}

	private void shutdownClientThread() {
		mudclient client = mudclient;
		if (client == null) {
			return;
		}
		synchronized (RETAINED_CLIENT_LOCK) {
			// A stale Activity may finish after a replacement has rebound the same
			// retained client. Only the current owner may terminate that client.
			if (retainedClient != client || retainedClientOwner != this) {
				mudclient = null;
				return;
			}
			retainedClientOwner = null;
			retainedClient = null;
			retainedClientBackgrounded = false;
			retainedClientBackgroundedAtMillis = 0L;
		}

		client.threadState = -1;
		try {
			client.closeConnection(false);
		} catch (RuntimeException ignored) {
		}

		Thread thread = client.clientBaseThread;
		if (thread != null && thread != Thread.currentThread() && thread.isAlive()) {
			thread.interrupt();
			try {
				thread.join(750L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		if (client.clientPort == this) {
			client.clientPort = null;
		}
		mudclient = null;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void onBackPressed() {
		handleBackNavigation();
	}

	private void handleBackNavigation() {
		if (mudclient != null && mudclient.handleAndroidBackButton()) {
			return;
		}
		finish();
	}

	private void registerBackHandler() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			return;
		}
		android.window.OnBackInvokedCallback callback = this::handleBackNavigation;
		getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
			android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
			callback);
		backCallback = callback;
	}

	private void unregisterBackHandler() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backCallback == null) {
			return;
		}
		getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
			(android.window.OnBackInvokedCallback) backCallback);
		backCallback = null;
	}

	public void networkChange(Network network, NetworkCapabilities networkCapabilities) {
		if (network == null) {
			return;
		}
		int kind = classifyNetwork(networkCapabilities);
		synchronized (networkLock) {
			validatedNetworks.remove(network);
			if (kind != NETWORK_NONE) {
				validatedNetworks.put(network, kind);
			}
			refreshConnectivityKindLocked();
		}
	}

	private void checkNetwork() {
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivityManager == null) {
			clearNetworks();
			return;
		}
		Map<Network, Integer> snapshot = new HashMap<>();
		try {
			Network network = connectivityManager.getActiveNetwork();
			int kind = network == null
				? NETWORK_NONE
				: classifyNetwork(connectivityManager.getNetworkCapabilities(network));
			if (network != null && kind != NETWORK_NONE) {
				snapshot.put(network, kind);
			}
		} catch (RuntimeException ignored) {
			snapshot.clear();
		}
		synchronized (networkLock) {
			validatedNetworks.clear();
			validatedNetworks.putAll(snapshot);
			refreshConnectivityKindLocked();
		}
	}

	private int classifyNetwork(NetworkCapabilities capabilities) {
		if (capabilities == null
			|| !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
			|| !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
			return NETWORK_NONE;
		}
		if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
			return NETWORK_WIFI;
		}
		if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
			return NETWORK_CELLULAR;
		}
		return NETWORK_OTHER;
	}

	private void clearNetworks() {
		synchronized (networkLock) {
			validatedNetworks.clear();
			connectivityKind = NETWORK_NONE;
		}
	}

	private void refreshConnectivityKindLocked() {
		int next = NETWORK_NONE;
		for (int kind : validatedNetworks.values()) {
			if (kind == NETWORK_WIFI) {
				next = NETWORK_WIFI;
				break;
			}
			if (kind == NETWORK_CELLULAR) {
				next = NETWORK_CELLULAR;
			} else if (next == NETWORK_NONE) {
				next = NETWORK_OTHER;
			}
		}
		connectivityKind = next;
	}

	private void setReceivers() {
		if (!this.loadedReceivers) {
			this.loadedReceivers = true;
			checkNetwork();
			ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			if (connectivityManager != null) {
				connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkManager);
			}
			registerReceiver(batteryReceiver, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
		}
	}

	private void unsetReceivers() {
		if (!this.loadedReceivers) {
			return;
		}
		this.loadedReceivers = false;
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivityManager != null) {
			try {
				connectivityManager.unregisterNetworkCallback(networkManager);
			} catch (IllegalArgumentException ignored) {
			}
		}
		try {
			unregisterReceiver(batteryReceiver);
		} catch (IllegalArgumentException ignored) {
		}
	}

	@Override
	protected void onPause() {
		long now = System.currentTimeMillis();
		boolean notifyClient = false;
		appInBackground = true;
		synchronized (RETAINED_CLIENT_LOCK) {
			if (mudclient != null && retainedClient == mudclient
				&& retainedClientOwner == this) {
				if (!retainedClientBackgrounded
					|| retainedClientBackgroundedAtMillis <= 0L) {
					retainedClientBackgroundedAtMillis = now;
				}
				retainedClientBackgrounded = true;
				backgroundedAtMillis = retainedClientBackgroundedAtMillis;
				notifyClient = true;
			} else {
				backgroundedAtMillis = now;
			}
		}
		if (notifyClient) {
			mudclient.noteAndroidActivityPaused();
		}
		soundPlayer.suspendForBackground();
		super.onPause();
	}

	@Override
	public void onResume() {
    	super.onResume();
		soundPlayer.resumeForeground();
		boolean resumedFromBackground = false;
		long backgroundDuration = 0L;
		synchronized (RETAINED_CLIENT_LOCK) {
			if (mudclient != null && retainedClient == mudclient
				&& retainedClientOwner == this) {
				resumedFromBackground = retainedClientBackgrounded || appInBackground;
				long backgroundStart = retainedClientBackgroundedAtMillis > 0L
					? retainedClientBackgroundedAtMillis : backgroundedAtMillis;
				if (resumedFromBackground && backgroundStart > 0L) {
					backgroundDuration = Math.max(0L,
						System.currentTimeMillis() - backgroundStart);
				}
				retainedClientBackgrounded = false;
				retainedClientBackgroundedAtMillis = 0L;
			}
			backgroundedAtMillis = 0L;
			appInBackground = false;
		}
		if (resumedFromBackground) {
			suppressReconnectOverlayUntilMillis = System.currentTimeMillis() + 15000L;
			if (mudclient != null) {
				mudclient.noteAndroidActivityResumedFromBackground(backgroundDuration);
			}
		}
		refreshInteractiveState();
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		if (mudclient != null && event != null) {
			int action = event.getActionMasked();
			if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
					|| action == MotionEvent.ACTION_CANCEL) {
				mudclient.logAndroidSmokeTouchEvent("activity", action,
					Math.round(event.getX()), Math.round(event.getY()), true, -1, -1);
			}
		}
		return super.dispatchTouchEvent(event);
	}

	@Override
	public boolean isAppInBackground() {
		return appInBackground;
	}

	@Override
	public boolean shouldSuppressReconnectOverlay() {
		return System.currentTimeMillis() < suppressReconnectOverlayUntilMillis;
	}

	@Override
	public void setAfkMonitorActive(boolean active) {
		soundPlayer.setAfkSuspended(active);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			refreshInteractiveState();
			if (keyboardRequested && !keyboardShowing) {
				requestKeyboardOnUiThread();
			}
		}
	}

	private void refreshInteractiveState() {
		updateHideUi();
		checkNetwork();
		if (gameView != null) {
			gameView.refreshViewportMetrics();
			gameView.requestFocus();
			gameView.postInvalidate();
		}
	}

	private void updateHideUi() {
		final Window window = getWindow();
		final View decorView = window.getDecorView();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			WindowInsetsController controller = decorView.getWindowInsetsController();
			if (controller != null) {
				controller.hide(WindowInsets.Type.systemBars());
				controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
			}
			return;
		}
		updateHideUiLegacy(decorView);
	}

	@SuppressWarnings("deprecation")
	private void updateHideUiLegacy(final View decorView) {
		final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
			| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
			| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
			| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
			| View.SYSTEM_UI_FLAG_FULLSCREEN
			| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
		decorView.setSystemUiVisibility(flags);
		decorView.setOnSystemUiVisibilityChangeListener (new View.OnSystemUiVisibilityChangeListener() {
			@Override
			public void onSystemUiVisibilityChange(int visibility) {
				if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
					decorView.setSystemUiVisibility(flags);
				}
			}
		});
	}

	@Override
	public CredentialStore getCredentialStore() {
		return credentialStore;
	}

	@Override
	public boolean fillSecureRandom(byte[] destination) {
		if (destination == null || destination.length != 32) {
			if (destination != null) Arrays.fill(destination, (byte) 0);
			return false;
		}
		synchronized (DUEL_PROOF_RANDOM_LOCK) {
			try {
				if (duelProofSecureRandom == null) duelProofSecureRandom = new SecureRandom();
				duelProofSecureRandom.nextBytes(destination);
				return true;
			} catch (RuntimeException ignored) {
				Arrays.fill(destination, (byte) 0);
				return false;
			}
		}
	}

	@Override
	public int getTouchTargetClientPixels(int dp) {
		if (gameView != null) {
			return gameView.getTouchTargetClientPixels(dp);
		}
		return ClientPort.super.getTouchTargetClientPixels(dp);
	}

	@Override
	public int getKeyboardTopClientPixel() {
		if (gameView != null) {
			return gameView.getKeyboardTopClientPixel();
		}
		return ClientPort.super.getKeyboardTopClientPixel();
	}

	@Override
    public boolean drawLoading(int i) {
        if (gameView == null) return false;
        return gameView.drawLoading(i);
    }

    @Override
    public void showLoadingProgress(int percentage, String status) {
        if (gameView != null) {
        	gameView.showLoadingProgress(percentage, status);
        	gameView.postInvalidate();
        }
    }

    @Override
    public void initListeners() {
        if (gameView != null) gameView.initListeners();
    }

    @Override
    public void crashed() {
        if (gameView != null) gameView.crashed();
    }

    @Override
    public void drawLoadingError() {
        if (gameView != null) {
            gameView.drawLoadingError();
            gameView.postInvalidate();
        }
    }

    @Override
    public void drawOutOfMemoryError() {
        if (gameView != null) {
            gameView.drawOutOfMemoryError();
            gameView.postInvalidate();
        }
    }

    @Override
    public boolean isDisplayable() {
        if (gameView != null) return gameView.isDisplayable();
        return false;
    }

    @Override
    public void drawTextBox(String line2, byte var2, String line1) {
        if (gameView != null) gameView.drawTextBox(line2, var2, line1);
    }

    @Override
    public void initGraphics() {
        if (gameView != null) gameView.initGraphics();
    }

    @Override
    public void draw() {
        if (gameView != null) gameView.draw();
    }

    @Override
    public void close() {
        if (gameView != null) gameView.close();
    }

    @Override
    public String getCacheLocation() {
        if (gameView != null) return gameView.getCacheLocation();
        return null;
    }

    @Override
    public void resized() {
        if (gameView != null) gameView.resized();
    }

    @Override
    public Sprite getSpriteFromByteArray(ByteArrayInputStream byteArrayInputStream) {
        if (gameView != null) return gameView.getSpriteFromByteArray(byteArrayInputStream);
		return null;
    }

    @Override
    public void playSound(byte[] soundData, int offset, int dataLength) {
        if (gameView != null) gameView.playSound(soundData, offset, dataLength);
    }

    @Override
    public void stopSoundPlayer() {
		soundPlayer.stopAll();
    }

    public mudclient getMudclient() {
        return mudclient;
    }

    public void setMudclient(mudclient mudclient) {
        this.mudclient = mudclient;
    }

    public InputImpl getInputImpl() {
        return inputImpl;
    }

    public void setInputImpl(InputImpl inputImpl) {
        this.inputImpl = inputImpl;
    }

	public void drawKeyboard() {
		if (gameView == null || mudclient == null || isFinishing() || isDestroyed()) {
			return;
		}
		synchronized (keyboardLock) {
			if (keyboardRequested || keyboardShowing) {
				return;
			}
			keyboardRequested = true;
			keyboardDismissRequested = false;
			keyboardShowAttempts = 0;
			keyboardRequestGeneration++;
			osConfig.F_SHOWING_KEYBOARD = true;
			hideSideMenuForKeyboardLocked();
		}
		requestKeyboardOnUiThread();
	}

	private void requestKeyboardOnUiThread() {
		runOnUiThread(() -> {
			RSCBitmapSurfaceView view = gameView;
			int requestGeneration;
			synchronized (keyboardLock) {
				if (!keyboardRequested || keyboardShowing || keyboardDismissRequested || view == null) {
					return;
				}
				if (!view.isAttachedToWindow() || !view.hasWindowFocus()) {
					// onWindowFocusChanged retries once the editor can connect to an IME.
					return;
				}
				if (keyboardShowAttempts >= MAX_KEYBOARD_SHOW_ATTEMPTS) {
					abandonKeyboardRequestLocked();
					return;
				}
				keyboardShowAttempts++;
				requestGeneration = keyboardRequestGeneration;
			}

			if (!view.requestFocus()) {
				scheduleKeyboardRetry(view, requestGeneration);
				return;
			}
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm == null) {
				synchronized (keyboardLock) {
					if (requestGeneration == keyboardRequestGeneration) {
						abandonKeyboardRequestLocked();
					}
				}
				return;
			}

			imm.restartInput(view);
			view.post(() -> {
				synchronized (keyboardLock) {
					if (!keyboardRequested || keyboardShowing || keyboardDismissRequested
						|| requestGeneration != keyboardRequestGeneration) {
						return;
					}
				}
				if (!imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)) {
					scheduleKeyboardRetry(view, requestGeneration);
				}
			});
		});
	}

	private void scheduleKeyboardRetry(RSCBitmapSurfaceView view, int requestGeneration) {
		view.postDelayed(() -> {
			synchronized (keyboardLock) {
				if (!keyboardRequested || keyboardShowing || keyboardDismissRequested
					|| requestGeneration != keyboardRequestGeneration) {
					return;
				}
			}
			requestKeyboardOnUiThread();
		}, KEYBOARD_SHOW_RETRY_DELAY_MS);
	}

	public void closeKeyboard() {
		RSCBitmapSurfaceView view = gameView;
		synchronized (keyboardLock) {
			keyboardRequested = false;
			keyboardDismissRequested = true;
			keyboardShowAttempts = 0;
			keyboardRequestGeneration++;
			osConfig.F_SHOWING_KEYBOARD = false;
			restoreSideMenuAfterKeyboardLocked();
		}
		if (view == null) {
			return;
		}
		runOnUiThread(() -> {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (imm != null && view.isAttachedToWindow()) {
				imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
			}
		});
	}

	/** Called from the SurfaceView's window-inset listener on the UI thread. */
	public void onImeVisibilityChanged(boolean visible) {
		if (!isUiThread()) {
			runOnUiThread(() -> onImeVisibilityChanged(visible));
			return;
		}
		boolean reassertDismiss = false;
		synchronized (keyboardLock) {
			keyboardShowing = visible;
			keyboardShowAttempts = 0;
			if (visible) {
				if (keyboardDismissRequested) {
					reassertDismiss = true;
				} else {
					keyboardRequested = true;
					osConfig.F_SHOWING_KEYBOARD = true;
					hideSideMenuForKeyboardLocked();
				}
			} else {
				keyboardRequested = false;
				keyboardDismissRequested = false;
				keyboardRequestGeneration++;
				osConfig.F_SHOWING_KEYBOARD = false;
				restoreSideMenuAfterKeyboardLocked();
			}
		}
		if (reassertDismiss) {
			// A previously accepted show request can race a later close.
			// Reassert the close now that the IME has actually appeared.
			RSCBitmapSurfaceView view = gameView;
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			if (view != null && imm != null && view.isAttachedToWindow()) {
				view.post(() -> imm.hideSoftInputFromWindow(view.getWindowToken(), 0));
			}
		}
	}

	private boolean isUiThread() {
		return android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
	}

	private void abandonKeyboardRequestLocked() {
		keyboardRequested = false;
		keyboardDismissRequested = false;
		keyboardShowAttempts = 0;
		keyboardRequestGeneration++;
		osConfig.F_SHOWING_KEYBOARD = false;
		restoreSideMenuAfterKeyboardLocked();
	}

	private void hideSideMenuForKeyboardLocked() {
		if (!Config.S_SIDE_MENU_TOGGLE || sideMenuHiddenForKeyboard || mudclient == null) {
			return;
		}
		hadSideMenu = mudclient.getOptionSideMenu();
		mudclient.setOptionSideMenu(false);
		sideMenuHiddenForKeyboard = true;
	}

	private void restoreSideMenuAfterKeyboardLocked() {
		if (!sideMenuHiddenForKeyboard) {
			return;
		}
		if (Config.S_SIDE_MENU_TOGGLE && mudclient != null) {
			mudclient.setOptionSideMenu(hadSideMenu);
		}
		sideMenuHiddenForKeyboard = false;
	}

	public boolean isKeyboardShowing() {
		return keyboardRequested || keyboardShowing;
	}

	@Override
	public boolean openUrl(String url) {
		if (url == null || url.trim().length() == 0) {
			return false;
		}

		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
		if (browserIntent.resolveActivity(getPackageManager()) == null) {
			return false;
		}

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					startActivity(browserIntent);
				} catch (Exception ignored) {
				}
			}
		});
		return true;
	}

	private double getBatteryPercentage() {
		if (batteryLevel == -1 || batteryScale == -1) {
			return 50.0;
		}

		return ((double)batteryLevel / (double)batteryScale) * 100.0;
	}

	public boolean getBatteryCharging() {
		return batteryCharging;
	}

	public int getBatteryPercent() {
		return (int)getBatteryPercentage();
	}

	public Sprite getBattery(int level) {
		if (keyboardShowing) {
			return null;
		}
		if (gameView != null) {
			if (batteryCharging) {
				return gameView.getBattery(8);
			} else {
				double batteryPercent = getBatteryPercentage();
				if (batteryPercent > 92.5) return gameView.getBattery(7);
				else if (batteryPercent > 77.5) return gameView.getBattery(6);
				else if (batteryPercent > 62.5) return gameView.getBattery(5);
				else if (batteryPercent > 47.5) return gameView.getBattery(4);
				else if (batteryPercent > 32.5) return gameView.getBattery(3);
				else if (batteryPercent > 17.5) return gameView.getBattery(2);
				else if (batteryPercent > 10) return gameView.getBattery(1);
				else return gameView.getBattery(0);
			}
		}
		return null;
	}

	public Sprite getConnectivity(int level) {
		if (keyboardShowing) {
			return null;
		}
		if (gameView != null) {
			if (connectivityKind == NETWORK_NONE) return gameView.getConnectivity(0);
			if (connectivityKind == NETWORK_CELLULAR) return gameView.getConnectivity(1);
			return gameView.getConnectivity(2);
		}
		return null;
	}

	public String getConnectivityText() {
		if (keyboardShowing) {
			return "";
		}
		if (connectivityKind == NETWORK_NONE) return "NONE";
		if (connectivityKind == NETWORK_WIFI) return "WIFI";
		if (connectivityKind == NETWORK_CELLULAR) return "CELL";
		return "NET";
	}

    @Override
    public void setTitle(String title) {

    }

    @Override
    public void setIconImage(String serverName) {

    }
}
