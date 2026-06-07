package com.openrsc.android.updater;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
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

import com.openrsc.client.R;
import com.openrsc.client.android.GameActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import orsc.osConfig;

public class CacheUpdater extends Activity {

	private static final String BUNDLED_CACHE_ROOT = "cache";
	private static final int NETWORK_TIMEOUT_MS = 5000;

	private TextProgressBar progressBar;
	private TextView statusText;
	private Button launchButton;
	private boolean completed = false;

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
				selectServer(getDefaultServerHost(), osConfig.VOIDSCAPE_DEFAULT_PORT);
			}
		});
		launchButton.setOnLongClickListener(v -> {
			if (completed) {
				showGameSelectionDialog();
				return true;
			}
			return false;
		});

		statusText = findViewById(R.id.textView1);
		setStatus("Checking game data");
		new UpdateTask().execute();
	}

	private void setStatus(String status) {
		statusText.setText(status);
	}

	@SuppressLint("StaticFieldLeak")
	private class UpdateTask extends AsyncTask<Void, String, Boolean> {

		@Override
		protected Boolean doInBackground(Void... ignored) {
			File cacheHome = getFilesDir();
			if (!cacheHome.exists() && !cacheHome.mkdirs()) {
				publishProgress("Unable to create cache directory", "0");
				return false;
			}

			publishProgress("Installing bundled game data", "5");
			boolean seeded = seedBundledCache(cacheHome);

			if (hasRemoteCacheEndpoint()) {
				publishProgress("Checking remote game data", "35");
				updateRemoteCache(cacheHome);
			}

			boolean ready = seeded || hasRequiredCacheFiles(cacheHome);
			publishProgress(ready ? "Game data ready" : "Game data unavailable", ready ? "100" : "0");
			return ready;
		}

		@Override
		protected void onProgressUpdate(String... values) {
			if (values.length > 0) {
				statusText.setText(values[0]);
			}
			if (values.length > 1) {
				try {
					progressBar.setProgress(Integer.parseInt(values[1]));
				} catch (NumberFormatException ignored) {
				}
				progressBar.setText(values[0]);
			}
		}

		@Override
		protected void onPostExecute(Boolean ready) {
			completed = ready;
			if (ready) {
				setStatus("Ready to play");
				launchButton.setVisibility(View.VISIBLE);
			} else {
				showCacheFailureDialog();
			}
		}
	}

	private boolean hasRemoteCacheEndpoint() {
		return osConfig.CACHE_URL != null && osConfig.CACHE_URL.trim().length() > 0;
	}

	private String getDefaultServerHost() {
		return isProbablyEmulator() ? osConfig.VOIDSCAPE_EMULATOR_HOST : osConfig.VOIDSCAPE_PUBLIC_HOST;
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

	private boolean seedBundledCache(File cacheHome) {
		try {
			copyAssetPath(getAssets(), BUNDLED_CACHE_ROOT, cacheHome);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return hasRequiredCacheFiles(cacheHome);
		}
	}

	private void copyAssetPath(AssetManager assetManager, String assetPath, File destination) throws IOException {
		String[] children = assetManager.list(assetPath);
		if (children != null && children.length > 0) {
			if (!destination.exists() && !destination.mkdirs()) {
				throw new IOException("Unable to create " + destination.getAbsolutePath());
			}
			for (String child : children) {
				copyAssetPath(assetManager, assetPath + "/" + child, new File(destination, child));
			}
			return;
		}

		File parent = destination.getParentFile();
		if (parent != null && !parent.exists() && !parent.mkdirs()) {
			throw new IOException("Unable to create " + parent.getAbsolutePath());
		}

		try (InputStream in = assetManager.open(assetPath);
			 OutputStream out = new FileOutputStream(destination)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
		}
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
		if (md5Table.exists() && !md5Table.delete()) {
			return false;
		}
		return remoteMd5.renameTo(md5Table);
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
			try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
				 FileOutputStream out = new FileOutputStream(file)) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				int totalRead = 0;
				while ((bytesRead = in.read(buffer)) != -1) {
					totalRead += bytesRead;
					out.write(buffer, 0, bytesRead);
					if (fileSize > 0) {
						publishDownloadStatus("Downloading " + description, 35 + (60 * totalRead / fileSize));
					}
				}
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
		runOnUiThread(() -> {
			statusText.setText(status);
			progressBar.setText(status);
			progressBar.setProgress(Math.max(0, Math.min(100, percent)));
		});
	}

	private boolean hasRequiredCacheFiles(File cacheHome) {
		return new File(cacheHome, "video/Authentic_Sprites.orsc").exists()
			&& new File(cacheHome, "video/models.orsc").exists()
			&& new File(cacheHome, "video/Authentic_Landscape.orsc").exists();
	}

	private void showCacheFailureDialog() {
		AlertDialog dialog = new AlertDialog.Builder(CacheUpdater.this, R.style.VoidscapeDialogTheme)
			.setTitle("Game data unavailable")
			.setMessage("The bundled cache could not be installed. Rebuild the APK from a repository with Client_Base/Cache populated.")
			.setPositiveButton("Retry", (d, which) -> new UpdateTask().execute())
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
		LinearLayout layout = new LinearLayout(CacheUpdater.this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(dp(24), dp(8), dp(24), 0);

		final EditText hostBox = new EditText(CacheUpdater.this);
		hostBox.setHint(osConfig.VOIDSCAPE_PUBLIC_HOST);
		hostBox.setSingleLine(true);
		hostBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		styleInput(hostBox);
		LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
		hostParams.setMargins(0, dp(20), 0, 0);
		layout.addView(hostBox, hostParams);

		final EditText portBox = new EditText(CacheUpdater.this);
		portBox.setHint(osConfig.VOIDSCAPE_DEFAULT_PORT);
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
		if (host == null || host.trim().length() == 0 || port == null || port.trim().length() == 0) {
			showServerSelectionError("Server host and port are required");
			return;
		}

		String selectedHost = host.trim();
		String selectedPort = port.trim();
		if (!selectedHost.matches("[A-Za-z0-9._:-]+")) {
			showServerSelectionError("Use a host name or IP address only");
			return;
		}

		try {
			int parsedPort = Integer.parseInt(selectedPort);
			if (parsedPort < 1 || parsedPort > 65535) {
				showServerSelectionError("Port must be between 1 and 65535");
				return;
			}
			writeTextFile(new File(getFilesDir(), "ip.txt"), selectedHost);
			writeTextFile(new File(getFilesDir(), "port.txt"), selectedPort);
			Log.i("Voidscape", "Selected server " + selectedHost + ":" + selectedPort);
		} catch (NumberFormatException e) {
			showServerSelectionError("Port must be a number");
			return;
		} catch (Exception e) {
			e.printStackTrace();
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

	private void writeTextFile(File file, String value) throws IOException {
		try (FileOutputStream outputStream = new FileOutputStream(file);
			 OutputStreamWriter outputWriter = new OutputStreamWriter(outputStream)) {
			outputWriter.write(value);
		}
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
}
