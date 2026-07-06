package launcher.Utils;

import launcher.Gameupdater.Downloader;
import launcher.Main;
import launcher.Voidscape.VoidscapeLauncherConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.swing.JOptionPane;

public class ClientLauncher {
	public static void launchClientForServer(String serverName) throws IOException {
    if (Downloader.currently_updating) {
      JOptionPane.showMessageDialog(null, "Currently updating the client, please wait!");
      return;
    }
		setOpenRSCClientEndpoint(VoidscapeLauncherConfig.serverHost(),
			String.valueOf(VoidscapeLauncherConfig.serverPort()));
		launchOpenRSCClient();
	}

	private static void setOpenRSCClientEndpoint(String ip, String port) {
		// Sets the IP and port
		FileOutputStream fileout;
		try {
			fileout = new FileOutputStream(Main.configFileLocation + File.separator + "ip.txt");
			OutputStreamWriter outputWriter = new OutputStreamWriter(fileout);
			outputWriter.write(ip);
			outputWriter.close();
		} catch (Exception e) {
      Logger.Error("Error setting ip.txt: " + e.getMessage());
		}
		try {
			fileout = new FileOutputStream(Main.configFileLocation + File.separator + "port.txt");
			OutputStreamWriter outputWriter = new OutputStreamWriter(fileout);
			outputWriter.write(port);
			outputWriter.close();
		} catch (Exception e) {
      Logger.Error("Error setting port.txt: " + e.getMessage());
		}
	}

	private static void launchOpenRSCClient() {
		// Deletes the client.properties file that may persist unwanted settings between different games
		File f = new File(Main.configFileLocation + File.separator + "client.properties");
		f.delete();

		// Update the sprite pack config file
		File configFile = new File(Main.configFileLocation + File.separator + "config.txt");
		configFile.delete();

		File openRscClientJar = new File(Main.configFileLocation + File.separator
			+ Defaults._CLIENT_FILENAME + ".jar");
		Utils.execCmd(new String[]{"java", "-jar", openRscClientJar.getAbsolutePath()}, openRscClientJar.getParentFile());
	}

	public static void launchFleaCircus() {
		File fleaCircusDir = new File(Main.configFileLocation + File.separator + "extras" + File.separator + "fleacircus");
		Utils.execCmd(new String[]{"java", "-cp", fleaCircusDir.getAbsolutePath(), "fleas"}, fleaCircusDir);
	}

}
