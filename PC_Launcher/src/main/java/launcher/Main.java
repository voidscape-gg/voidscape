package launcher;

import launcher.Utils.Utils;
import launcher.Voidscape.VoidscapeLauncherConfig;
import launcher.Voidscape.VoidscapeUpdater;

import java.io.File;

public class Main {

  // DEFAULT FOLDERS
  private static final String DEFAULT_CACHE_DIR = System.getProperty("user.home")
      + File.separator + ".voidscape" + File.separator + "client";
  public static String configFileLocation = DEFAULT_CACHE_DIR;
  public static String SPRITEPACK_DIR = configFileLocation + File.separator + "video" + File.separator + "spritepacks";
  public static boolean disabledUpdate = false;
  // Set when this process was spawned by a previous launcher as part of a
  // self-update; suppresses further self-update chaining so a bad manifest
  // can never relaunch in a loop.
  public static boolean relaunchedAfterSelfUpdate = false;
  public static boolean syncOnly = false;

  public static void main(final String[] args) {

    handleArgs(args);

    if (syncOnly) {
      runHeadlessSync();
      return;
    }

    Launcher mainLauncher = new Launcher();
    mainLauncher.initializeLauncher();

  }

  /** Headless prepare + manifest sync for smokes/CI; exits 0 on success, 1 on failure. */
  private static void runHeadlessSync() {
    VoidscapeUpdater updater = new VoidscapeUpdater(VoidscapeLauncherConfig.cacheDir(),
        new VoidscapeUpdater.StatusListener() {
          @Override
          public void onStatus(String message, int progress, boolean busy) {
            System.out.println("SYNC_STATUS " + (progress < 0 ? "-" : String.valueOf(progress)) + " " + message);
          }
        });
    boolean ok = updater.syncForCli();
    System.out.println("SYNC_RESULT " + (ok ? "ok" : "failed"));
    System.exit(ok ? 0 : 1);
  }

  public static void handleArgs(final String[] args) {
    String helpMessage = "Help for the Voidscape launcher:\n" +
        "	--help, -h displays this help message\n" +
        "	--dir [loc], -d [loc] changes the cache directory location\n" +
        "	--portable uses ./Cache next to the current working directory\n" +
        "	--no-update, -n Disables update checks (game files and launcher)\n" +
        "	--sync-only Runs the update sync without a window and exits (for scripts)\n" +
        "Example:\n" +
        "java -jar VoidscapeLauncher.jar -d /home/foo/.voidscape/client";

    int argIndex = 0;
    while (argIndex < args.length) {
      String arg = args[argIndex];
      if (arg.equals("--help") || arg.equals("-h")) {
        System.out.println(helpMessage);
        System.exit(0);
      } else if (arg.equals("--dir") || arg.equals("-d")) {
        if (argIndex + 1 < args.length) {
          String path = args[argIndex + 1];
          if (Utils.isValidPath(path)) {
            configFileLocation = Utils.getCanonicalPath(path);
            SPRITEPACK_DIR = configFileLocation + File.separator + "video" + File.separator + "spritepacks";
            argIndex += 2;
          } else {
            System.out.println("Error: please provide a valid path.\n" +
                "Usage: java -jar VoidscapeLauncher.jar -d /path/to/cache/folder");
            System.exit(1);
          }
        } else {
          System.out.println("Error: no path specified.\n" +
              "Usage: java -jar VoidscapeLauncher.jar -d /path/to/cache/folder");
          System.exit(1);
        }
      } else if (arg.equals("--no-update") || arg.equals("-n")) {
        disabledUpdate = true;
        argIndex++;
      } else if (arg.equals("--portable")) {
        configFileLocation = "Cache";
        SPRITEPACK_DIR = configFileLocation + File.separator + "video" + File.separator + "spritepacks";
        argIndex++;
      } else if (arg.equals("--relaunched")) {
        relaunchedAfterSelfUpdate = true;
        argIndex++;
      } else if (arg.equals("--sync-only")) {
        syncOnly = true;
        argIndex++;
      } else {
        System.out.println("Unrecognized modifier.\n" +
            "Use -h for help.");
        System.exit(1);
      }
    }
  }

}
