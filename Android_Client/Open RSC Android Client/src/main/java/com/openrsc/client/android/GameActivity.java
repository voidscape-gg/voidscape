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
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;

import com.openrsc.android.render.InputImpl;
import com.openrsc.android.render.RSCBitmapSurfaceView;
import com.openrsc.android.security.AndroidCredentialStore;
import com.openrsc.client.R;
import com.openrsc.client.model.Sprite;

import java.io.ByteArrayInputStream;
import java.io.File;
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

	private static mudclient retainedClient;
	private static final int NETWORK_NONE = 0;
	private static final int NETWORK_CELLULAR = 1;
	private static final int NETWORK_WIFI = 2;
	private static final int NETWORK_OTHER = 3;

    private InputImpl inputImpl;
    private mudclient mudclient;
    private RSCBitmapSurfaceView gameView;

	private boolean loadedReceivers = false;
	private volatile boolean appInBackground = false;
	private volatile long suppressReconnectOverlayUntilMillis = 0L;
	private int batteryLevel;
	private int batteryScale;
	private boolean batteryCharging;
	private final Object networkLock = new Object();
	private final Map<Network, Integer> validatedNetworks = new HashMap<>();
	private volatile int connectivityKind = NETWORK_NONE;
	private CredentialStore credentialStore = CredentialStore.unsupported();

    private boolean hadSideMenu;
	private boolean keyboardShowing;
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

			mudclient existingClient = retainedClient;
		if (existingClient != null && existingClient.threadState >= 0 && existingClient.clientBaseThread != null
			&& existingClient.clientBaseThread.isAlive()) {
			setMudclient(existingClient);
			mudclient.clientPort = this;
		} else {
			setMudclient(new mudclient(this));
			mudclient.packetHandler = new PacketHandler(mudclient);

			if (mudclient.threadState >= 0) mudclient.threadState = 0;

			mudclient.startMainThread();
			retainedClient = mudclient;
		}

        setInputImpl(new InputImpl(mudclient, gameView));

		//Utils.context = getApplicationContext();

		// Hide the bars and stuff
		updateHideUi();
		registerBackHandler();
		setReceivers();
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
		if (retainedClient == client) {
			retainedClient = null;
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
		appInBackground = true;
		soundPlayer.suspendForBackground();
		super.onPause();
	}

	@Override
	public void onResume() {
    	super.onResume();
		soundPlayer.resumeForeground();
		if (appInBackground) {
			suppressReconnectOverlayUntilMillis = System.currentTimeMillis() + 15000L;
		}
		appInBackground = false;
		refreshInteractiveState();
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
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			refreshInteractiveState();
		}
	}

	private void refreshInteractiveState() {
		updateHideUi();
		checkNetwork();
		if (gameView != null) {
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
		if (gameView == null || mudclient == null) return;
		if (keyboardShowing) {
			return;
		}
        InputMethodManager imm = (InputMethodManager) this
                .getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm == null) return;
	        gameView.requestFocus();
	        gameView.post(() -> imm.showSoftInput(gameView, InputMethodManager.SHOW_IMPLICIT));
	        keyboardShowing = true;
	        osConfig.F_SHOWING_KEYBOARD = true;
        if (Config.S_SIDE_MENU_TOGGLE) {
        	hadSideMenu = mudclient.getOptionSideMenu();
			mudclient.setOptionSideMenu(false);
		}
    }

    public void closeKeyboard() {
		if (gameView == null) return;
		InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
		if (imm != null) {
			imm.hideSoftInputFromWindow(gameView.getWindowToken(), 0);
		}
		keyboardShowing = false;
        osConfig.F_SHOWING_KEYBOARD = false;
		if (Config.S_SIDE_MENU_TOGGLE && mudclient != null) {
			mudclient.setOptionSideMenu(hadSideMenu);
		}
    }

	public boolean isKeyboardShowing() {
		return keyboardShowing;
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
