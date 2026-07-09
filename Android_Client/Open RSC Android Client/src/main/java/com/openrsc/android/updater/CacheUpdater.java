package com.openrsc.android.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.openrsc.android.security.AndroidCredentialStore;
import com.openrsc.client.R;
import com.openrsc.client.android.GameActivity;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import orsc.multiclient.CredentialStore;
import orsc.osConfig;

public class CacheUpdater extends Activity {

	private static final String BUNDLED_CACHE_ROOT = "cache";
	private static final int NETWORK_TIMEOUT_MS = 5000;
	private static final String BOOTSTRAP_PREFERENCES = "voidscape.bootstrap";
	private static final String CACHE_INSTALL_IDENTITY = "cache.install_identity";
	private static final String ENDPOINT_HOST = "endpoint.host";
	private static final String ENDPOINT_PORT = "endpoint.port";
	private static final String HOST_MIRROR_FILE = "ip.txt";
	private static final String PORT_MIRROR_FILE = "port.txt";
	private static final String EXTRA_SMOKE_ENDPOINT_HOST = "voidscape.smoke.endpoint_host";
	private static final String EXTRA_SMOKE_ENDPOINT_PORT = "voidscape.smoke.endpoint_port";
	private static final String EXTRA_SMOKE_CLEAR_CREDENTIALS = "voidscape.smoke.clear_credentials";
	private static final String EXTRA_SMOKE_CACHE_FAIL_AFTER_FILES =
		"voidscape.smoke.cache_fail_after_files";
	private static final String CACHE_FAILURE_MESSAGE =
		"Voidscape couldn't prepare its game files. Check that the device has free storage, then retry. "
			+ "If the problem continues, reinstall the latest APK.";

	private TextProgressBar progressBar;
	private TextView statusText;
	private Button launchButton;
	private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private boolean completed = false;
	private boolean launching = false;
	private int smokeCacheFailAfterFiles = -1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.updater);

		progressBar = findViewById(R.id.progressBar);
		progressBar.setTextSize(18);
		progressBar.setTextColor(getColor(R.color.voidscape_ink));
		progressBar.setIndeterminate(false);
		progressBar.setMax(100);

		launchButton = findViewById(R.id.launch_client);
		launchButton.setVisibility(View.GONE);
		launchButton.setOnClickListener(v -> {
			if (completed) {
				Endpoint endpoint = getSavedEndpoint();
				selectServer(endpoint.host, endpoint.port);
			}
		});
		launchButton.setOnLongClickListener(v -> {
			if (completed) {
				if (!isDebuggable()) {
					Toast.makeText(this, "Public server selected", Toast.LENGTH_SHORT).show();
					return true;
				}
				showGameSelectionDialog();
				return true;
			}
			return false;
		});

		statusText = findViewById(R.id.textView1);
		setStatus("Checking game data");
		applyDebugLaunchExtras(getIntent());
		try {
			loadAndRepairEndpoint();
		} catch (IOException e) {
			Log.w("Voidscape", "Unable to repair the saved server endpoint", e);
		}
		startUpdateTask();
	}

	private void setStatus(String status) {
		statusText.setText(status);
	}

	private void applyDebugLaunchExtras(Intent intent) {
		if (!isDebuggable() || intent == null) {
			return;
		}

		String host = trimmedOrNull(intent.getStringExtra(EXTRA_SMOKE_ENDPOINT_HOST));
		String port = trimmedOrNull(intent.getStringExtra(EXTRA_SMOKE_ENDPOINT_PORT));
		smokeCacheFailAfterFiles = Math.max(
			-1,
			intent.getIntExtra(EXTRA_SMOKE_CACHE_FAIL_AFTER_FILES, -1));
		if (smokeCacheFailAfterFiles >= 0) {
			Log.i(
				"Voidscape",
				"CACHE_BOOTSTRAP smoke-fail-after-files=" + smokeCacheFailAfterFiles);
		}
		try {
			if (host != null || port != null) {
				if (host == null || port == null) {
					Log.w("Voidscape", "Ignoring incomplete smoke server endpoint");
				} else {
					Endpoint endpoint = validatedEndpoint(host, port);
					persistEndpoint(endpoint);
				}
			}
		} catch (IOException | IllegalArgumentException e) {
			Log.w("Voidscape", "Unable to apply smoke launch extras", e);
		}
		if (intent.getBooleanExtra(EXTRA_SMOKE_CLEAR_CREDENTIALS, false)) {
			CredentialStore.Result cleared = new AndroidCredentialStore(this).clear();
			if (cleared.getState() == CredentialStore.State.UNAVAILABLE) {
				Log.w("Voidscape", "Unable to clear saved logins for Android smoke");
			}
		}
	}

	private boolean isDebuggable() {
		return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
	}

	@Override
	protected void onDestroy() {
		updateExecutor.shutdownNow();
		super.onDestroy();
	}

	private void startUpdateTask() {
		completed = false;
		launching = false;
		launchButton.setEnabled(false);
		launchButton.setVisibility(View.GONE);
		updateExecutor.execute(() -> {
			boolean ready = false;
			try {
				ready = updateCache();
			} catch (Exception e) {
				if (!(e instanceof InterruptedIOException) || !isFinishing()) {
					Log.e("Voidscape", "Unable to prepare bundled game data", e);
				}
			}
			if (!ready) {
				boolean markerCleared = getBootstrapPreferences()
					.edit()
					.remove(CACHE_INSTALL_IDENTITY)
					.commit();
				Log.i("Voidscape", "CACHE_BOOTSTRAP failure marker-cleared=" + markerCleared);
			}
			boolean finalReady = ready;
			mainHandler.post(() -> {
				if (isFinishing() || isDestroyed()) {
					return;
				}
				completed = finalReady;
				launching = false;
				if (finalReady) {
					setStatus("Ready to play");
					progressBar.setText("Game data ready");
					progressBar.setProgress(100);
					launchButton.setEnabled(true);
					launchButton.setVisibility(View.VISIBLE);
				} else {
					setStatus("Game data needs attention");
					progressBar.setText("Setup stopped");
					progressBar.setProgress(0);
					showCacheFailureDialog();
				}
			});
		});
	}

	private boolean updateCache() throws IOException {
		File cacheHome = getFilesDir();
		if (!cacheHome.exists() && !cacheHome.mkdirs()) {
			throw new IOException("Unable to create cache directory");
		}

		throwIfInterrupted();
		publishUpdateProgress("Checking bundled game data", 5);
		String installIdentity = getInstallIdentity();
		SharedPreferences preferences = getBootstrapPreferences();
		boolean markerMatches = installIdentity.equals(
			preferences.getString(CACHE_INSTALL_IDENTITY, null));
		boolean requiredCacheReady = hasRequiredCacheFiles(cacheHome);
		boolean bundledCacheCurrent = smokeCacheFailAfterFiles < 0
			&& markerMatches
			&& requiredCacheReady;
		boolean installedBundledCache = false;

		if (bundledCacheCurrent) {
			Log.i("Voidscape", "CACHE_BOOTSTRAP marker-hit identity=" + installIdentity);
			publishUpdateProgress("Bundled game data verified", 90);
		} else {
			boolean repairing = markerMatches && !requiredCacheReady;
			Log.i(
				"Voidscape",
				repairing
					? "CACHE_BOOTSTRAP repair-required identity=" + installIdentity
					: "CACHE_BOOTSTRAP install-required identity=" + installIdentity);
			publishUpdateProgress(
				repairing ? "Repairing bundled game data" : "Installing bundled game data",
				8);
			if (!preferences.edit().remove(CACHE_INSTALL_IDENTITY).commit()) {
				throw new IOException("Unable to clear stale game-data marker");
			}
			installBundledCache(cacheHome);
			if (!hasRequiredCacheFiles(cacheHome)) {
				throw new IOException("Required bundled game files are missing or empty");
			}
			installedBundledCache = true;
			publishUpdateProgress("Bundled game data installed", 90);
		}

		if (hasRemoteCacheEndpoint()) {
			publishUpdateProgress("Checking remote game data", 35);
			updateRemoteCache(cacheHome);
		}

		throwIfInterrupted();
		boolean ready = hasRequiredCacheFiles(cacheHome);
		if (ready && installedBundledCache) {
			if (!preferences.edit().putString(CACHE_INSTALL_IDENTITY, installIdentity).commit()) {
				throw new IOException("Unable to save game-data marker");
			}
			Log.i("Voidscape", "CACHE_BOOTSTRAP install-complete identity=" + installIdentity);
		}
		publishUpdateProgress(ready ? "Game data ready" : "Game data unavailable", ready ? 100 : 0);
		return ready;
	}

	private void publishUpdateProgress(String status, int percent) {
		mainHandler.post(() -> {
			if (isFinishing() || isDestroyed()) {
				return;
			}
			statusText.setText(status);
			progressBar.setText(status);
			progressBar.setProgress(Math.max(0, Math.min(100, percent)));
		});
	}

	private boolean hasRemoteCacheEndpoint() {
		return osConfig.CACHE_URL != null && osConfig.CACHE_URL.trim().length() > 0;
	}

	private String getDefaultServerHost() {
		return isDebuggable() && isProbablyEmulator()
			? osConfig.VOIDSCAPE_EMULATOR_HOST
			: osConfig.VOIDSCAPE_PUBLIC_HOST;
	}

	private boolean isProbablyEmulator() {
		return Build.FINGERPRINT.startsWith("generic")
			|| Build.FINGERPRINT.startsWith("unknown")
			|| Build.MODEL.contains("Emulator")
			|| Build.MODEL.contains("Android SDK built for")
			|| Build.MANUFACTURER.contains("Genymotion")
			|| Build.PRODUCT.contains("sdk_gphone")
			|| Build.PRODUCT.contains("emulator")
			|| Build.HARDWARE.contains("goldfish")
			|| Build.HARDWARE.contains("ranchu");
	}

	private SharedPreferences getBootstrapPreferences() {
		return getSharedPreferences(BOOTSTRAP_PREFERENCES, MODE_PRIVATE);
	}

	private String getInstallIdentity() throws IOException {
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			long versionCode = getVersionCode(packageInfo);
			return versionCode + ":" + packageInfo.lastUpdateTime;
		} catch (PackageManager.NameNotFoundException e) {
			throw new IOException("Unable to identify the installed APK", e);
		}
	}

	@SuppressWarnings("deprecation")
	private long getVersionCode(PackageInfo packageInfo) {
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
			? packageInfo.getLongVersionCode()
			: packageInfo.versionCode;
	}

	private void installBundledCache(File cacheHome) throws IOException {
		AssetManager assetManager = getAssets();
		List<AssetEntry> entries = new ArrayList<>();
		collectAssetFiles(assetManager, BUNDLED_CACHE_ROOT, cacheHome, entries);
		if (entries.isEmpty()) {
			throw new IOException("The APK does not contain bundled game data");
		}
		Log.i("Voidscape", "CACHE_BOOTSTRAP install-start files=" + entries.size());
		if (smokeCacheFailAfterFiles == 0) {
			smokeCacheFailAfterFiles = -1;
			Log.i("Voidscape", "CACHE_BOOTSTRAP injected-failure copied=0");
			throw new IOException("Injected cache-install failure after 0 files");
		}

		for (int index = 0; index < entries.size(); index++) {
			throwIfInterrupted();
			AssetEntry entry = entries.get(index);
			int completedFiles = index + 1;
			int progress = 10 + (75 * completedFiles / entries.size());
			publishUpdateProgress(
				"Installing game data " + completedFiles + "/" + entries.size(),
				progress);
			try (InputStream input = assetManager.open(entry.assetPath)) {
				writeStreamAtomically(input, entry.destination, null);
			}
			if (smokeCacheFailAfterFiles >= 0 && completedFiles >= smokeCacheFailAfterFiles) {
				smokeCacheFailAfterFiles = -1;
				Log.i("Voidscape", "CACHE_BOOTSTRAP injected-failure copied=" + completedFiles);
				throw new IOException(
					"Injected cache-install failure after " + completedFiles + " files");
			}
		}
	}

	private void collectAssetFiles(
		AssetManager assetManager,
		String assetPath,
		File destination,
		List<AssetEntry> entries) throws IOException {
		throwIfInterrupted();
		String[] children = assetManager.list(assetPath);
		if (children != null && children.length > 0) {
			for (String child : children) {
				collectAssetFiles(
					assetManager,
					assetPath + "/" + child,
					new File(destination, child),
					entries);
			}
			return;
		}
		entries.add(new AssetEntry(assetPath, destination));
	}

	private boolean updateRemoteCache(File cacheHome) {
		File remoteMd5 = new File(cacheHome, osConfig.MD5_TABLENAME + ".remote");
		String remoteMd5Url = normalizedCacheUrl() + osConfig.MD5_TABLENAME;
		if (!downloadUrlToFile(remoteMd5Url, remoteMd5, "Checksum")) {
			return false;
		}

		md5 localCache = new md5(cacheHome, "");
		md5 remoteCache = new md5(remoteMd5, "");
		for (md5.Entry entry : remoteCache.entries) {
			if (osConfig.MD5_TABLENAME.equals(entry.getRef().getName())) {
				continue;
			}

			File entryFile = new File(cacheHome, entry.getRef().toString());
			File parent = entryFile.getParentFile();
			if (parent != null && !parent.exists()) {
				parent.mkdirs();
			}

			String localSum = localCache.getRefSum(entryFile);
			if (entry.getSum().equalsIgnoreCase(localSum)) {
				continue;
			}

			String remoteFileUrl = normalizedCacheUrl() + entry.getRef().toString().replace(File.separator, "/");
			if (!downloadUrlToFile(remoteFileUrl, entryFile, getDescription(entryFile))) {
				return false;
			}
		}

		File md5Table = new File(cacheHome, osConfig.MD5_TABLENAME);
		try {
			atomicReplace(remoteMd5, md5Table);
			return true;
		} catch (IOException e) {
			Log.w("Voidscape", "Unable to publish remote checksum table", e);
			return false;
		}
	}

	private String normalizedCacheUrl() {
		String cacheUrl = osConfig.CACHE_URL.trim();
		return cacheUrl.endsWith("/") ? cacheUrl : cacheUrl + "/";
	}

	private boolean downloadUrlToFile(String url, File file, String description) {
		HttpURLConnection connection = null;
		try {
			publishDownloadStatus("Downloading " + description, 0);
			connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
			connection.setReadTimeout(NETWORK_TIMEOUT_MS);
			connection.connect();
			if (connection.getResponseCode() >= 400) {
				return false;
			}

			int fileSize = connection.getContentLength();
			try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream())) {
				writeStreamAtomically(in, file, totalRead -> {
					if (fileSize > 0) {
						publishDownloadStatus(
							"Downloading " + description,
							35 + (int) (60L * totalRead / fileSize));
					}
				});
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private void publishDownloadStatus(String status, int percent) {
		mainHandler.post(() -> {
			if (isFinishing() || isDestroyed()) {
				return;
			}
			statusText.setText(status);
			progressBar.setText(status);
			progressBar.setProgress(Math.max(0, Math.min(100, percent)));
		});
	}

	private boolean hasRequiredCacheFiles(File cacheHome) {
		return isNonEmptyFile(new File(cacheHome, "video/Authentic_Sprites.orsc"))
			&& isNonEmptyFile(new File(cacheHome, "video/models.orsc"))
			&& isNonEmptyFile(new File(cacheHome, "video/Authentic_Landscape.orsc"))
			&& isNonEmptyFile(new File(cacheHome, "video/library.orsc"));
	}

	private boolean isNonEmptyFile(File file) {
		return file.isFile() && file.length() > 0;
	}

	private void showCacheFailureDialog() {
		AlertDialog dialog = new AlertDialog.Builder(CacheUpdater.this, R.style.VoidscapeDialogTheme)
			.setTitle("Game data unavailable")
			.setMessage(CACHE_FAILURE_MESSAGE)
			.setPositiveButton("Retry", (d, which) -> startUpdateTask())
			.setNegativeButton("Close", (d, which) -> finish())
			.show();
		styleDialog(dialog);
	}

	private void showGameSelectionDialog() {
		launchButton.setVisibility(View.VISIBLE);

		String publicServer = "Public: " + osConfig.VOIDSCAPE_PUBLIC_HOST + ":" + osConfig.VOIDSCAPE_DEFAULT_PORT;
		String emulator = "Emulator: " + osConfig.VOIDSCAPE_EMULATOR_HOST + ":" + osConfig.VOIDSCAPE_DEFAULT_PORT;
		String lan = "LAN: " + osConfig.VOIDSCAPE_LAN_HOST + ":" + osConfig.VOIDSCAPE_DEFAULT_PORT;
		String manual = "Manual Server";
		String[] choices = {publicServer, emulator, lan, manual};
		ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.voidscape_dialog_item, choices);

		AlertDialog dialog = new AlertDialog.Builder(CacheUpdater.this, R.style.VoidscapeDialogTheme)
			.setTitle("Server Options")
			.setAdapter(adapter, (d, which) -> {
				if (which == 0) {
					selectServer(osConfig.VOIDSCAPE_PUBLIC_HOST, osConfig.VOIDSCAPE_DEFAULT_PORT);
				} else if (which == 1) {
					selectServer(osConfig.VOIDSCAPE_EMULATOR_HOST, osConfig.VOIDSCAPE_DEFAULT_PORT);
				} else if (which == 2) {
					selectServer(osConfig.VOIDSCAPE_LAN_HOST, osConfig.VOIDSCAPE_DEFAULT_PORT);
				} else {
					showManualServerDialog();
				}
			})
			.create();
		dialog.setOnCancelListener(d -> launchButton.setVisibility(View.VISIBLE));
		dialog.show();
		styleDialog(dialog);
	}

	private void showManualServerDialog() {
		Endpoint savedEndpoint = getSavedEndpoint();
		LinearLayout layout = new LinearLayout(CacheUpdater.this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(dp(24), dp(8), dp(24), 0);

		final EditText hostBox = new EditText(CacheUpdater.this);
		hostBox.setHint(osConfig.VOIDSCAPE_PUBLIC_HOST);
		hostBox.setText(savedEndpoint.host);
		hostBox.setSingleLine(true);
		hostBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		styleInput(hostBox);
		LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
		hostParams.setMargins(0, dp(20), 0, 0);
		layout.addView(hostBox, hostParams);

		final EditText portBox = new EditText(CacheUpdater.this);
		portBox.setHint(osConfig.VOIDSCAPE_DEFAULT_PORT);
		portBox.setText(savedEndpoint.port);
		portBox.setSingleLine(true);
		portBox.setInputType(InputType.TYPE_CLASS_NUMBER);
		styleInput(portBox);
		LinearLayout.LayoutParams portParams = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
		portParams.setMargins(0, dp(8), 0, 0);
		layout.addView(portBox, portParams);

		AlertDialog dialog = new AlertDialog.Builder(CacheUpdater.this, R.style.VoidscapeDialogTheme)
			.setTitle("Manual Server")
			.setMessage("Enter the Voidscape host and port.")
			.setView(layout)
			.setPositiveButton("Play", (d, whichButton) -> {
				String host = hostBox.getText().toString().trim();
				String port = portBox.getText().toString().trim();
				if (host.length() == 0) {
					host = osConfig.VOIDSCAPE_PUBLIC_HOST;
				}
				if (port.length() == 0) {
					port = osConfig.VOIDSCAPE_DEFAULT_PORT;
				}
				selectServer(host, port);
			})
			.setNegativeButton("Back", (d, whichButton) -> showGameSelectionDialog())
			.show();
		styleDialog(dialog);
	}

	private void styleDialog(AlertDialog dialog) {
		Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
		if (positive != null) {
			positive.setTextColor(getColor(R.color.voidscape_gold));
			positive.setAllCaps(false);
		}
		Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
		if (negative != null) {
			negative.setTextColor(getColor(R.color.voidscape_muted));
			negative.setAllCaps(false);
		}
	}

	private void styleInput(EditText editText) {
		editText.setTextColor(getColor(R.color.voidscape_text));
		editText.setHintTextColor(getColor(R.color.voidscape_muted));
		editText.setBackgroundResource(R.drawable.voidscape_text_field);
		editText.setBackgroundTintList(null);
		editText.setPadding(dp(12), 0, dp(12), 0);
		editText.setTextSize(18);
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	private void selectServer(String host, String port) {
		if (launching) {
			return;
		}

		try {
			Endpoint endpoint = acceptedEndpointForBuild(validatedEndpoint(host, port));
			launching = true;
			launchButton.setEnabled(false);
			setStatus("Launching game");
			persistEndpoint(endpoint);
			Log.i("Voidscape", "Selected server " + endpoint.host + ":" + endpoint.port);
		} catch (IllegalArgumentException e) {
			showServerSelectionError(e.getMessage());
			return;
		} catch (IOException e) {
			Log.e("Voidscape", "Unable to save server selection", e);
			launching = false;
			launchButton.setEnabled(true);
			showServerSelectionError("Unable to save server selection");
			return;
		}

		Intent mainIntent = new Intent(CacheUpdater.this, GameActivity.class);
		hideSoftKeyboard();
		mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(mainIntent);
		finish();
	}

	private void hideSoftKeyboard() {
		InputMethodManager inputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		View focused = getCurrentFocus();
		if (inputManager != null && focused != null) {
			inputManager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
		}
	}

	private void showServerSelectionError(String message) {
		setStatus(message);
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
	}

	private String readSavedServerValue(String fileName) {
		File file = new File(getFilesDir(), fileName);
		if (!file.isFile()) {
			return null;
		}

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
			String value = reader.readLine();
			if (value == null || value.trim().length() == 0) {
				return null;
			}
			return value.trim();
		} catch (IOException e) {
			return null;
		}
	}

	private Endpoint getSavedEndpoint() {
		try {
			return loadAndRepairEndpoint();
		} catch (IOException e) {
			Log.w("Voidscape", "Unable to load the saved server endpoint", e);
			return getDefaultEndpoint();
		}
	}

	private Endpoint loadAndRepairEndpoint() throws IOException {
		SharedPreferences preferences = getBootstrapPreferences();
		boolean hasHost = preferences.contains(ENDPOINT_HOST);
		boolean hasPort = preferences.contains(ENDPOINT_PORT);
		Endpoint endpoint = null;
		boolean rewriteCanonicalPair = false;

		if (hasHost && hasPort) {
			endpoint = endpointOrNull(
				preferences.getString(ENDPOINT_HOST, null),
				preferences.getString(ENDPOINT_PORT, null));
			if (endpoint == null) {
				rewriteCanonicalPair = true;
			}
		} else if (!hasHost && !hasPort) {
			endpoint = endpointOrNull(
				readSavedServerValue(HOST_MIRROR_FILE),
				readSavedServerValue(PORT_MIRROR_FILE));
			rewriteCanonicalPair = true;
		} else {
			// Never assemble an endpoint from one preference and one legacy mirror.
			rewriteCanonicalPair = true;
		}

		if (endpoint == null) {
			endpoint = getDefaultEndpoint();
		}
		Endpoint acceptedEndpoint = acceptedEndpointForBuild(endpoint);
		if (!acceptedEndpoint.equals(endpoint)) {
			Log.i("Voidscape", "ENDPOINT_BOOTSTRAP developer-host-reset-to-defaults");
			endpoint = acceptedEndpoint;
			rewriteCanonicalPair = true;
		}

		if (rewriteCanonicalPair) {
			persistCanonicalEndpoint(endpoint);
		}
		repairEndpointMirrors(endpoint);
		return endpoint;
	}

	private Endpoint acceptedEndpointForBuild(Endpoint endpoint) {
		if (!isDebuggable() && isDeveloperServerHost(endpoint.host)) {
			return new Endpoint(osConfig.VOIDSCAPE_PUBLIC_HOST, osConfig.VOIDSCAPE_DEFAULT_PORT);
		}
		return endpoint;
	}

	private Endpoint getDefaultEndpoint() {
		return new Endpoint(getDefaultServerHost(), osConfig.VOIDSCAPE_DEFAULT_PORT);
	}

	private Endpoint endpointOrNull(String host, String port) {
		try {
			return validatedEndpoint(host, port);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private Endpoint validatedEndpoint(String host, String port) {
		String selectedHost = trimmedOrNull(host);
		String selectedPort = trimmedOrNull(port);
		if (selectedHost == null || selectedPort == null) {
			throw new IllegalArgumentException("Server host and port are required");
		}
		if (!selectedHost.matches("[A-Za-z0-9._:-]+")) {
			throw new IllegalArgumentException("Use a host name or IP address only");
		}

		final int parsedPort;
		try {
			parsedPort = Integer.parseInt(selectedPort);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Port must be a number");
		}
		if (parsedPort < 1 || parsedPort > 65535) {
			throw new IllegalArgumentException("Port must be between 1 and 65535");
		}
		return new Endpoint(selectedHost, Integer.toString(parsedPort));
	}

	private String trimmedOrNull(String value) {
		if (value == null || value.trim().length() == 0) {
			return null;
		}
		return value.trim();
	}

	private void persistEndpoint(Endpoint requestedEndpoint) throws IOException {
		Endpoint endpoint = acceptedEndpointForBuild(requestedEndpoint);
		persistCanonicalEndpoint(endpoint);
		repairEndpointMirrors(endpoint);
	}

	private void persistCanonicalEndpoint(Endpoint endpoint) throws IOException {
		boolean saved = getBootstrapPreferences()
			.edit()
			.putString(ENDPOINT_HOST, endpoint.host)
			.putString(ENDPOINT_PORT, endpoint.port)
			.commit();
		if (!saved) {
			throw new IOException("Unable to save server endpoint");
		}
	}

	private void repairEndpointMirrors(Endpoint endpoint) throws IOException {
		String mirroredHost = readSavedServerValue(HOST_MIRROR_FILE);
		String mirroredPort = readSavedServerValue(PORT_MIRROR_FILE);
		if (endpoint.host.equals(mirroredHost) && endpoint.port.equals(mirroredPort)) {
			return;
		}

		Log.i(
			"Voidscape",
			"ENDPOINT_BOOTSTRAP mirror-repair endpoint=" + endpoint.host + ":" + endpoint.port);
		writeTextFileAtomically(new File(getFilesDir(), HOST_MIRROR_FILE), endpoint.host);
		writeTextFileAtomically(new File(getFilesDir(), PORT_MIRROR_FILE), endpoint.port);
		if (!endpoint.host.equals(readSavedServerValue(HOST_MIRROR_FILE))
			|| !endpoint.port.equals(readSavedServerValue(PORT_MIRROR_FILE))) {
			throw new IOException("Unable to verify server endpoint mirrors");
		}
	}

	private void writeTextFileAtomically(File destination, String value) throws IOException {
		File temporary = prepareTemporaryFile(destination);
		boolean published = false;
		try {
			try (FileOutputStream output = new FileOutputStream(temporary);
				 OutputStreamWriter writer = new OutputStreamWriter(output, "UTF-8")) {
				writer.write(value);
				writer.flush();
				output.getFD().sync();
			}
			throwIfInterrupted();
			atomicReplace(temporary, destination);
			published = true;
		} finally {
			if (!published) {
				deleteTemporaryFile(temporary);
			}
		}
	}

	private void writeStreamAtomically(
		InputStream input,
		File destination,
		ByteProgressListener progressListener) throws IOException {
		File temporary = prepareTemporaryFile(destination);
		boolean published = false;
		try {
			try (FileOutputStream output = new FileOutputStream(temporary)) {
				byte[] buffer = new byte[8192];
				int read;
				long totalRead = 0;
				while ((read = input.read(buffer)) != -1) {
					throwIfInterrupted();
					output.write(buffer, 0, read);
					totalRead += read;
					if (progressListener != null) {
						progressListener.onProgress(totalRead);
					}
				}
				output.flush();
				output.getFD().sync();
			}
			throwIfInterrupted();
			atomicReplace(temporary, destination);
			published = true;
		} finally {
			if (!published) {
				deleteTemporaryFile(temporary);
			}
		}
	}

	private File prepareTemporaryFile(File destination) throws IOException {
		File parent = destination.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs()) {
			throw new IOException("Unable to create " + parent.getAbsolutePath());
		}
		File temporary = new File(parent, "." + destination.getName() + ".voidscape-tmp");
		if (temporary.exists() && !temporary.delete()) {
			throw new IOException("Unable to clear " + temporary.getAbsolutePath());
		}
		return temporary;
	}

	private void atomicReplace(File source, File destination) throws IOException {
		throwIfInterrupted();
		try {
			Os.rename(source.getAbsolutePath(), destination.getAbsolutePath());
		} catch (ErrnoException e) {
			throw new IOException("Unable to publish " + destination.getAbsolutePath(), e);
		}
	}

	private void deleteTemporaryFile(File temporary) {
		if (temporary.exists() && !temporary.delete()) {
			Log.w("Voidscape", "Unable to remove temporary file " + temporary.getAbsolutePath());
		}
	}

	private void throwIfInterrupted() throws InterruptedIOException {
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedIOException("Game-data setup interrupted");
		}
	}

	private boolean isDeveloperServerHost(String host) {
		return osConfig.VOIDSCAPE_EMULATOR_HOST.equals(host)
			|| osConfig.VOIDSCAPE_LAN_HOST.equals(host)
			|| "localhost".equalsIgnoreCase(host)
			|| "127.0.0.1".equals(host)
			|| "::1".equals(host);
	}

	private String getDescription(File ref) {
		int index = ref.getName().lastIndexOf('.');
		if (index == -1) {
			return "General";
		}
		String extension = ref.getName().substring(index + 1);
		if (extension.equalsIgnoreCase("ospr")) {
			return "Graphics";
		}
		if (extension.equalsIgnoreCase("wav")) {
			return "Audio";
		}
		if (extension.equalsIgnoreCase("orsc")) {
			return "Graphics";
		}
		return "General";
	}

	private interface ByteProgressListener {
		void onProgress(long totalBytes);
	}

	private static final class AssetEntry {
		private final String assetPath;
		private final File destination;

		private AssetEntry(String assetPath, File destination) {
			this.assetPath = assetPath;
			this.destination = destination;
		}
	}

	private static final class Endpoint {
		private final String host;
		private final String port;

		private Endpoint(String host, String port) {
			this.host = host;
			this.port = port;
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			}
			if (!(object instanceof Endpoint)) {
				return false;
			}
			Endpoint endpoint = (Endpoint) object;
			return host.equals(endpoint.host) && port.equals(endpoint.port);
		}

		@Override
		public int hashCode() {
			return 31 * host.hashCode() + port.hashCode();
		}
	}
}
