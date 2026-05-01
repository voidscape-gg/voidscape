package com.openrsc.android.updater;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
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
		progressBar.setIndeterminate(false);
		progressBar.setMax(100);

		launchButton = findViewById(R.id.launch_client);
		launchButton.setVisibility(View.GONE);
		launchButton.setOnClickListener(v -> {
			if (completed) {
				showGameSelectionDialog();
			}
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
				showGameSelectionDialog();
			} else {
				showCacheFailureDialog();
			}
		}
	}

	private boolean hasRemoteCacheEndpoint() {
		return osConfig.CACHE_URL != null && osConfig.CACHE_URL.trim().length() > 0;
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
		new AlertDialog.Builder(CacheUpdater.this)
			.setTitle("Game data unavailable")
			.setMessage("The bundled cache could not be installed. Rebuild the APK from a repository with Client_Base/Cache populated.")
			.setPositiveButton("Retry", (dialog, which) -> new UpdateTask().execute())
			.setNegativeButton("Close", (dialog, which) -> finish())
			.show();
	}

	private void showGameSelectionDialog() {
		launchButton.setVisibility(View.VISIBLE);

		String emulator = "Voidscape Emulator (" + osConfig.VOIDSCAPE_EMULATOR_HOST + ":" + osConfig.VOIDSCAPE_DEFAULT_PORT + ")";
		String lan = "Voidscape LAN (" + osConfig.VOIDSCAPE_LAN_HOST + ":" + osConfig.VOIDSCAPE_DEFAULT_PORT + ")";
		String manual = "Manual Server";
		String[] choices = {emulator, lan, manual};

		AlertDialog dialog = new AlertDialog.Builder(CacheUpdater.this)
			.setTitle("Choose Server")
			.setItems(choices, (d, which) -> {
				if (which == 0) {
					selectServer(osConfig.VOIDSCAPE_EMULATOR_HOST, osConfig.VOIDSCAPE_DEFAULT_PORT);
				} else if (which == 1) {
					selectServer(osConfig.VOIDSCAPE_LAN_HOST, osConfig.VOIDSCAPE_DEFAULT_PORT);
				} else {
					showManualServerDialog();
				}
			})
			.create();
		dialog.setOnCancelListener(d -> launchButton.setVisibility(View.VISIBLE));
		dialog.show();
	}

	private void showManualServerDialog() {
		LinearLayout layout = new LinearLayout(CacheUpdater.this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(32, 8, 32, 0);

		final EditText hostBox = new EditText(CacheUpdater.this);
		hostBox.setHint(osConfig.VOIDSCAPE_LAN_HOST);
		hostBox.setSingleLine(true);
		hostBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
		layout.addView(hostBox);

		final EditText portBox = new EditText(CacheUpdater.this);
		portBox.setHint(osConfig.VOIDSCAPE_DEFAULT_PORT);
		portBox.setSingleLine(true);
		portBox.setInputType(InputType.TYPE_CLASS_NUMBER);
		layout.addView(portBox);

		new AlertDialog.Builder(CacheUpdater.this)
			.setTitle("Manual Server")
			.setMessage("Enter the Voidscape host and port.")
			.setView(layout)
			.setPositiveButton("Play", (dialog, whichButton) -> {
				String host = hostBox.getText().toString().trim();
				String port = portBox.getText().toString().trim();
				if (host.length() == 0) {
					host = osConfig.VOIDSCAPE_LAN_HOST;
				}
				if (port.length() == 0) {
					port = osConfig.VOIDSCAPE_DEFAULT_PORT;
				}
				selectServer(host, port);
			})
			.setNegativeButton("Back", (dialog, whichButton) -> showGameSelectionDialog())
			.show();
	}

	private void selectServer(String host, String port) {
		if (host == null || host.trim().length() == 0 || port == null || port.trim().length() == 0) {
			Toast.makeText(this, "Server host and port are required", Toast.LENGTH_LONG).show();
			return;
		}

		try {
			Integer.parseInt(port.trim());
			writeTextFile(new File(getFilesDir(), "ip.txt"), host.trim());
			writeTextFile(new File(getFilesDir(), "port.txt"), port.trim());
		} catch (Exception e) {
			e.printStackTrace();
			Toast.makeText(this, "Unable to save server selection", Toast.LENGTH_LONG).show();
			return;
		}

		Intent mainIntent = new Intent(CacheUpdater.this, GameActivity.class);
		mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(mainIntent);
		finish();
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
