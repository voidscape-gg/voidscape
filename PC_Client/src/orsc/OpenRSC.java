package orsc;

import orsc.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class OpenRSC extends ORSCApplet {

	public static OpenRSC applet;
	static JFrame jframe;
	private static final long serialVersionUID = 1L;

	public static void main(String[] args) {
		boolean loadedScalingScalar = false;
		boolean loadedViewportPreset = false;

		// MUST do this before anything else runs in order to override OS-level dpi settings
		// (not applicable to macOS, which implements OS-scaling in a different fashion)
		if (!Utils.isMacOS()) {
			// Disable OS-level scaling in all JREs > 8
			System.setProperty("sun.java2d.uiScale.enabled", "false");
			System.setProperty("sun.java2d.uiScale", "1");

			// Required for newer versions of Oracle 8 to disable OS-level scaling
			System.setProperty("sun.java2d.dpiaware", "true");

			if (Utils.isWindowsOS()) {
				// Some Windows/JRE/GPU combinations leave the Swing back buffer black.
				// The classic viewport is tiny, so the software Java2D path is safer.
				System.setProperty("sun.java2d.d3d", "false");
				System.setProperty("sun.java2d.noddraw", "true");
				System.setProperty("sun.java2d.opengl", "false");
			}

			// Linux / other
			if (!Utils.isWindowsOS()) {
				System.setProperty("GDK_SCALE", "1");
			}
		}

		if (Utils.isMacOS()) {
			// Note: Only works on some Java 8 implementations
			System.setProperty("apple.awt.application.appearance", "system");
		}

		File scalingSettings = new File("./clientSettings.conf");
		if (scalingSettings.exists()) {
			Properties props = new Properties();

			try (FileInputStream in = new FileInputStream(scalingSettings.getAbsolutePath())) {
				props.load(in);

				// Load scaling settings
				String scalingTypeString = props.getProperty("scaling_type");
				String scalarString = props.getProperty("scaling_scalar");
				if (scalingTypeString != null && !scalingTypeString.isEmpty()) {
					int scalingTypeOrdinal = Integer.parseInt(scalingTypeString);
					mudclient.scalingType = ScaledWindow.ScalingAlgorithm.values()[scalingTypeOrdinal];
				}
				if (scalarString != null && !scalarString.isEmpty()) {
					ORSCApplet.oldRenderingScalar = mudclient.renderingScalar;
					mudclient.newRenderingScalar = Float.parseFloat(scalarString);
					loadedScalingScalar = true;
				}
				String viewportPresetString = props.getProperty("viewport_preset");
				if (viewportPresetString != null && !viewportPresetString.isEmpty()) {
					ScaledWindow.setViewportPresetIndex(Integer.parseInt(viewportPresetString));
					loadedViewportPreset = true;
				}
			} catch (Exception e) {
				System.out.println("Something went wrong loading scaling settings");
				e.printStackTrace();
			}
		}

		scaledWindow = ScaledWindow.getInstance();
		// Voidscape: first run only — no persisted window settings — size the
		// window to the display. Any saved viewport preset or scaling scalar wins.
		if (!loadedScalingScalar && !loadedViewportPreset) {
			scaledWindow.applyFirstRunViewportPreset();
		}
		scaledWindow.applyStartupScalingDefaults(loadedScalingScalar);
		SwingUtilities.invokeLater(OpenRSC::createAndShowGUI);
	}

	public static void createAndShowGUI() {
		try {
			jframe = new JFrame(Config.getServerNameWelcome());
			applet = new OpenRSC();
			// Voidscape: desktop viewport preset. Height includes the historic 12px client footer area.
			applet.setPreferredSize(new Dimension(ScaledWindow.getBaseViewportWidth(), ScaledWindow.getBaseViewportHeight()));
			jframe.getContentPane().setLayout(new BorderLayout());
			jframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			jframe.setIconImage(Utils.getImage("icon.png").getImage());
			jframe.setTitle(Config.WINDOW_TITLE);
			jframe.getContentPane().add(applet);
			jframe.setResizable(true); // true or false based on server sent config
			jframe.setVisible(false); // All rendering is forwarded to the ScaledWindow class
			jframe.setBackground(Color.black);
			// Just like above, here we add 12 because 12 was added back in 2009 for the skip tutorial line.
			jframe.setMinimumSize(new Dimension(ScaledWindow.getBaseViewportWidth(), ScaledWindow.getBaseViewportHeight()));
			jframe.pack();
			jframe.setLocationRelativeTo(null);
			applet.init();
			applet.start();

			scaledWindow.launchScaledWindow();

			applet.resizeMudclient(ScaledWindow.getBaseViewportWidth(), ScaledWindow.getBaseViewportHeight());
			WorkbenchServer.start();
		} catch (HeadlessException e) {
			e.printStackTrace();
		}
	}

	public void setTitle(String title) {
		scaledWindow.setTitle(title);
	}

	public void setIconImage(String serverName) {
		switch (serverName) {
			case "RSC Coleslaw":
				scaledWindow.setIconImage(Utils.getImage("coleslaw.icon.png").getImage());
				break;
			case "RSC Uranium":
				scaledWindow.setIconImage(Utils.getImage("uranium.icon.png").getImage());
				break;
			case "RSC Cabbage":
				scaledWindow.setIconImage(Utils.getImage("cabbage.icon.png").getImage());
				break;
			case "OpenPK":
				scaledWindow.setIconImage(Utils.getImage("openpk.icon.png").getImage());
				break;
			default:
				scaledWindow.setIconImage(Utils.getImage("icon.png").getImage());
		}
	}

	public String getCacheLocation() {
		return Config.F_CACHE_DIR + File.separator;
	}

	@Override
	public void playSound(byte[] soundData, int offset, int dataLength) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void stopSoundPlayer() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public boolean getResizable() {
		return Config.allowResize1();
	}
}
