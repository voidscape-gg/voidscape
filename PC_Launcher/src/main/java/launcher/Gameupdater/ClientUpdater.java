package launcher.Gameupdater;

import launcher.Utils.Defaults;
import launcher.Utils.Logger;
import java.io.*;
import java.util.ArrayList;

public class ClientUpdater {
  private static String _CACHE_DIR;
  private static Downloader gameUpdater;

  public ClientUpdater(String cacheDir) {
    _CACHE_DIR = cacheDir;
  }

  public void updateOpenRSCClient() {
    File gamePath = new File(_CACHE_DIR);
    if (!gamePath.exists() || !gamePath.isDirectory())
      gamePath.mkdir();

    if (gameUpdater == null) {
      gameUpdater = new Downloader(_CACHE_DIR, new ArrayList<>());
    }
    gameUpdater.initOpenRSCClientUpdate();
  }

  private static void executeUpdate(String gamePath, String fileName, String repositoryDL,
      String versionName) throws SecurityException, IOException {
    if (repositoryDL == null || repositoryDL.trim().isEmpty()) {
      Logger.Warn("Legacy external client download is disabled in the Voidscape launcher.");
      return;
    }
    File _GAME_PATH = new File(_CACHE_DIR + gamePath);
    String _FILE_NAME = fileName;

    ClientDownloader.downloadOrUpdate(_GAME_PATH, _FILE_NAME, repositoryDL, versionName);
  }

  public static void updateFleaCircus() throws SecurityException, IOException {
    executeUpdate("/extras/fleacircus/", "fleacircus.zip", Defaults._FLEACIRCUS_REPOSITORY_DL,
        "_FLEACIRCUS_VERSION");
  }
}
