package launcher.Utils;

import launcher.Fancy.MainWindow;

import java.util.Arrays;

public class WorldPopulations implements Runnable {
	final static int WORLDS_SUPPORTED = 6; // increment when Kale or other servers are implemented. do not decrement.

	public final static int PRESERVATION = 0;
	public final static int CABBAGE = 1;
	public final static int URANIUM = 2;
	public final static int COLESLAW = 3;
	public final static int TWOTHOUSANDONESCAPE = 4;
	public final static int OPENPK = 5;
	public final static int KALE = 6;

	private static long lastPopCheck = 0;

	public static String[] worldOnlineTexts = new String[WORLDS_SUPPORTED];

	public static void updateWorldPopulations() {
		Thread t = new Thread(new WorldPopulations());
		t.start();
	}

	@Override
	public void run() {
		if (System.currentTimeMillis() < lastPopCheck + 1000) {
			MainWindow.get().updateWorldTotalTexts();
			return;
		}

		lastPopCheck = System.currentTimeMillis();
		Arrays.fill(worldOnlineTexts, "");

		MainWindow.get().updateWorldTotalTexts();
	}
}
