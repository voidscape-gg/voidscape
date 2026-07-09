package orsc.multiclient;

import com.openrsc.client.model.Sprite;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import orsc.Config;
import orsc.PacketHandler;
import orsc.net.Network_Base;
import orsc.net.Network_Socket;
import orsc.util.CacheArchive;

public interface ClientPort {

	default CredentialStore getCredentialStore() {
		return CredentialStore.unsupported();
	}

	boolean drawLoading(int i);

	void showLoadingProgress(int percentage, String status);

	void initListeners();

	default void pollInput() {
	}

	default boolean isAppInBackground() {
		return false;
	}

	default boolean shouldSuppressReconnectOverlay() {
		return false;
	}

	void crashed();

	void drawLoadingError();

	void drawOutOfMemoryError();

	boolean isDisplayable();

	void drawTextBox(String line2, byte var2, String line1);

	void initGraphics();

	void draw();

	void close();

	String getCacheLocation();

	Sprite getBattery(int level);

	int getBatteryPercent();

	boolean getBatteryCharging();

	Sprite getConnectivity(int level);

	String getConnectivityText();

	void resized();

	Sprite getSpriteFromByteArray(ByteArrayInputStream byteArrayInputStream);

	void playSound(byte[] soundData, int offset, int dataLength);

	void stopSoundPlayer();

	void drawKeyboard();

	void closeKeyboard();

	boolean openUrl(String url);

	void setTitle(String title);

	void setIconImage(String serverName);

	default String saveScreenshot() {
		return "";
	}

	default Network_Base openNetworkConnection(PacketHandler packetHandler, String host, int port) throws IOException {
		return new Network_Socket(packetHandler.openSocket(port, host), packetHandler);
	}

	default InputStream openCacheResource(String relativePath) throws IOException {
		return new FileInputStream(getCacheLocation() + relativePath);
	}

	default CacheArchive openCacheArchive(String relativePath) throws IOException {
		return CacheArchive.read(openCacheResource(relativePath));
	}

	static boolean saveHideIp(int preference) {
		FileOutputStream fileout;
		try {
			fileout = new FileOutputStream(Config.F_CACHE_DIR + File.separator + "hideIp.txt");

			OutputStreamWriter outputWriter = new OutputStreamWriter(fileout);
			outputWriter.write("" + preference);
			outputWriter.close();
			return true;
		} catch (Exception ignored) {
		}
		return false;
	}

	static int loadHideIp() {
		try {
			FileInputStream in = new FileInputStream(Config.F_CACHE_DIR + File.separator + "hideIp.txt");
			InputStreamReader inputStreamReader = new InputStreamReader(in);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				sb.append(line);
			}
			in.close();

			return Integer.parseInt(sb.toString());
		} catch (Exception ignored) {
		}
		return 0;
	}

	static boolean saveCredentials(String creds) {
		FileOutputStream fileout;
		try {
			fileout = new FileOutputStream(Config.F_CACHE_DIR + File.separator + "credentials.txt");

			OutputStreamWriter outputWriter = new OutputStreamWriter(fileout);
			outputWriter.write(creds);
			outputWriter.close();
			return true;
		} catch (Exception ignored) {
		}
		return false;
	}

	static String loadCredentials() {
		try {
			FileInputStream in = new FileInputStream(Config.F_CACHE_DIR + File.separator + "credentials.txt");
			InputStreamReader inputStreamReader = new InputStreamReader(in);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				sb.append(line);
			}
			in.close();

			return sb.toString();
		} catch (Exception ignored) {
		}
		return "";
	}

	static String loadIP() {
		try {
			FileInputStream in = new FileInputStream(Config.F_CACHE_DIR + File.separator + "ip.txt");
			InputStreamReader inputStreamReader = new InputStreamReader(in);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				sb.append(line);
			}
			in.close();

			return sb.toString();
		} catch (Exception ignored) {
		}
		return "";
	}

	static int loadPort() {
		try {
			FileInputStream in = new FileInputStream(Config.F_CACHE_DIR + File.separator + "port.txt");
			InputStreamReader inputStreamReader = new InputStreamReader(in);
			BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				sb.append(line);
			}
			in.close();

			return Integer.parseInt(sb.toString());
		} catch (Exception ignored) {
		}
		return 0;
	}
}
