package launcher.Voidscape;

import launcher.Main;
import launcher.Utils.Logger;
import launcher.Utils.Utils;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class VoidscapeUpdater {
  public interface StatusListener {
    void onStatus(String message, int progress, boolean busy);
  }

  private static final String[] RUNTIME_FILES = new String[] {
      "accounts.txt",
      "credentials.txt",
      "uid.dat",
      "ip.txt",
      "port.txt",
      "hideIp.txt",
      "config.txt",
      "client.properties",
      "discord_inuse.txt",
      "launcherSettings.conf",
      "voidscapeLauncher.properties"
  };

  private static final String SYNC_STATE_FILE = ".voidscape-sync-state.properties";
  private static final String LAUNCHER_DIR = "launcher";
  private static final String TEMP_SUFFIX = ".download";
  private static final int MANIFEST_ATTEMPTS = 3;
  private static final int DOWNLOAD_ATTEMPTS = 3;
  private static final int CONNECT_TIMEOUT_MS = 10000;
  private static final int READ_TIMEOUT_MS = 30000;

  // Progress bands for the status bar: verify runs 5-30, downloads 30-97.
  private static final int PROGRESS_VERIFY_START = 5;
  private static final int PROGRESS_VERIFY_END = 30;
  private static final int PROGRESS_DOWNLOAD_END = 97;

  public enum Outcome {
    UPDATED,
    UP_TO_DATE,
    OFFLINE,
    NO_MANIFEST
  }

  public static class SyncResult {
    public final Outcome outcome;
    public final int downloadedFiles;
    public final String versionLabel;
    public final String clientVersion;
    public final File stagedLauncherJar;

    SyncResult(Outcome outcome, int downloadedFiles, String versionLabel, String clientVersion,
        File stagedLauncherJar) {
      this.outcome = outcome;
      this.downloadedFiles = downloadedFiles;
      this.versionLabel = versionLabel;
      this.clientVersion = clientVersion;
      this.stagedLauncherJar = stagedLauncherJar;
    }
  }

  private static class UpdatePolicyException extends Exception {
    UpdatePolicyException(String message) {
      super(message);
    }
  }

  private final File cacheDir;
  private final StatusListener listener;
  private volatile boolean busy;
  private volatile String lastVersionLabel = "";
  private volatile String lastClientVersion = "";

  public VoidscapeUpdater(File cacheDir, StatusListener listener) {
    this.cacheDir = cacheDir;
    this.listener = listener;
  }

  public boolean isBusy() {
    return busy;
  }

  public String lastVersionLabel() {
    return lastVersionLabel;
  }

  public String lastClientVersion() {
    return lastClientVersion;
  }

  public void prepareAsync() {
    runAsync("Preparing launcher", new CheckedRunnable() {
      @Override
      public void run() throws Exception {
        prepare(false);
        status(Main.disabledUpdate ? "Ready to play - updates disabled" : "Ready to play", 100, false);
      }
    });
  }

  /** Startup flow: prepare, sync from the manifest, then chain-restart if the launcher itself updated. */
  public void syncAsync() {
    runAsync("Checking for updates", new CheckedRunnable() {
      @Override
      public void run() throws Exception {
        prepare(false);
        SyncResult result = sync(false);
        if (result.stagedLauncherJar != null && restartIntoUpdatedLauncher(result.stagedLauncherJar)) {
          return;
        }
        status(describe(result), 100, false);
      }
    });
  }

  public void checkForUpdatesAsync() {
    syncAsync();
  }

  public void repairAsync() {
    runAsync("Repairing cache", new CheckedRunnable() {
      @Override
      public void run() throws Exception {
        deleteMutableClientFiles();
        deleteSyncState();
        prepare(true);
        SyncResult result = sync(true);
        if (result.stagedLauncherJar != null && restartIntoUpdatedLauncher(result.stagedLauncherJar)) {
          return;
        }
        status(describe(result), 100, false);
      }
    });
  }

  /**
   * Play: sync game files first (offline falls back to verified local files), then spawn the
   * client. A pending launcher self-update is staged for the next launcher start instead of
   * restarting now, so pressing Play never bounces the window out from under the player.
   */
  public void launchAsync(final Component parent) {
    if (busy) {
      showDialog(parent, "The launcher is still preparing files. Try again in a moment.",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    runAsync("Preparing to play", new CheckedRunnable() {
      @Override
      public void run() throws Exception {
        prepare(false);
        SyncResult result = sync(false);

        File jar = VoidscapeLauncherConfig.clientJar(cacheDir);
        if (!jar.exists()) {
          throw new Exception("The Voidscape client is missing.\nRun Check for Updates, or build the PC client locally first.");
        }

        status("Launching Voidscape...", 100, false);
        List<String> command = new ArrayList<String>();
        command.add(javaBinary());
        command.add("-Dsun.java2d.d3d=false");
        command.add("-Dsun.java2d.noddraw=true");
        command.add("-Dsun.java2d.opengl=false");
        addJvmProperty(command, "voidscape.discordApplicationId", VoidscapeLauncherConfig.discordApplicationId());
        addJvmProperty(command, "voidscape.discordLargeImageKey", VoidscapeLauncherConfig.discordLargeImageKey());
        addJvmProperty(command, "voidscape.discordLargeImageText", VoidscapeLauncherConfig.discordLargeImageText());
        command.add("-jar");
        command.add(jar.getAbsolutePath());
        Utils.execCmd(command.toArray(new String[command.size()]), cacheDir);

        if (result.outcome == Outcome.OFFLINE) {
          status("Playing - couldn't check for updates", 100, false);
        } else if (result.stagedLauncherJar != null) {
          status("Playing - launcher update applies on next restart", 100, false);
        }
      }
    });
  }

  /** Headless sync for smokes/CI. Prints machine-readable markers; never restarts the process. */
  public boolean syncForCli() {
    busy = true;
    try {
      prepare(false);
      SyncResult result = sync(false);
      System.out.println("SYNC_OUTCOME " + result.outcome.name().toLowerCase(Locale.ROOT).replace('_', '-'));
      if (result.versionLabel != null && result.versionLabel.length() > 0) {
        System.out.println("SYNC_VERSION " + result.versionLabel);
      }
      if (result.clientVersion != null && result.clientVersion.length() > 0) {
        System.out.println("SYNC_CLIENT_VERSION " + result.clientVersion);
      }
      System.out.println("SYNC_DOWNLOADED " + result.downloadedFiles);
      if (result.stagedLauncherJar != null) {
        System.out.println("SYNC_LAUNCHER_STAGED " + result.stagedLauncherJar.getAbsolutePath());
      }
      return true;
    } catch (Exception e) {
      Logger.Error("Sync failed: " + e.getMessage());
      System.out.println("SYNC_OUTCOME failed");
      System.out.println("SYNC_ERROR " + e.getMessage());
      return false;
    } finally {
      busy = false;
    }
  }

  public File getCacheDir() {
    return cacheDir;
  }

  // ---------------------------------------------------------------------
  // Sync engine
  // ---------------------------------------------------------------------

  private SyncResult sync(boolean forceFullVerify) throws Exception {
    if (Main.disabledUpdate) {
      return new SyncResult(Outcome.NO_MANIFEST, 0, "", "", null);
    }

    String manifestUrl = VoidscapeLauncherConfig.manifestUrl();
    if (manifestUrl == null || manifestUrl.trim().length() == 0) {
      status("Ready to play - local dev files", 100, false);
      return new SyncResult(Outcome.NO_MANIFEST, 0, "", "", null);
    }

    cleanupStaleTemps(cacheDir);

    status("Checking for updates", 3, true);
    Manifest manifest;
    try {
      manifest = fetchManifestWithRetries(manifestUrl);
    } catch (Exception e) {
      if (e instanceof UpdatePolicyException) {
        throw e;
      }
      if (hasVerifiedLocalFilesForFallback()) {
        Logger.Warn("Manifest unreachable, using local files: " + e.getMessage());
        return new SyncResult(Outcome.OFFLINE, 0, "", "", null);
      }
      throw new Exception("Can't reach the update server and no verified local client is installed yet.\n" + e.getMessage());
    }

    if (manifest.entries.size() == 0) {
      throw new Exception("The update manifest lists no files - refusing to sync against it.");
    }

    Properties state = forceFullVerify ? new Properties() : loadSyncState();

    // Verify phase: figure out what actually needs downloading.
    List<ManifestEntry> needed = new ArrayList<ManifestEntry>();
    int checked = 0;
    for (ManifestEntry entry : manifest.entries) {
      checked++;
      status("Verifying files (" + checked + "/" + manifest.entries.size() + ")",
          PROGRESS_VERIFY_START + (checked * (PROGRESS_VERIFY_END - PROGRESS_VERIFY_START) / manifest.entries.size()),
          true);
      File destination = safeDestination(entry.path);
      if (!isVerified(destination, entry, state)) {
        needed.add(entry);
      }
    }

    // Download phase, with aggregate byte progress when the manifest carries sizes.
    long totalBytes = 0;
    boolean sizesKnown = true;
    for (ManifestEntry entry : needed) {
      if (entry.size <= 0) {
        sizesKnown = false;
        break;
      }
      totalBytes += entry.size;
    }

    long doneBytes = 0;
    int doneFiles = 0;
    for (ManifestEntry entry : needed) {
      File destination = safeDestination(entry.path);
      URL url = resolveEntryUrl(manifest.baseUrl, entry.url, entry.path);
      requireSecureUrl(url);
      DownloadProgress progress = new DownloadProgress("Downloading update", doneFiles, needed.size(),
          doneBytes, totalBytes, sizesKnown, PROGRESS_VERIFY_END, PROGRESS_DOWNLOAD_END);
      downloadVerifiedWithRetries(url, destination, entry, progress);
      recordVerified(state, entry, destination);
      doneBytes += Math.max(entry.size, 0);
      doneFiles++;
    }

    pruneRemovedFiles(manifest, state);

    // Stage a launcher self-update if the manifest advertises a different launcher jar.
    File stagedLauncher = null;
    if (shouldStageLauncherUpdate(manifest)) {
      stagedLauncher = stageLauncherUpdate(manifest);
    }
    pruneStaleLauncherJars(manifest);

    state.setProperty("meta.version", manifest.versionLabel());
    state.setProperty("meta.clientVersion", manifest.clientVersion == null ? "" : manifest.clientVersion);
    saveSyncState(state);
    lastVersionLabel = manifest.versionLabel();
    lastClientVersion = manifest.clientVersion == null ? "" : manifest.clientVersion;

    Outcome outcome = (doneFiles > 0 || stagedLauncher != null) ? Outcome.UPDATED : Outcome.UP_TO_DATE;
    return new SyncResult(outcome, doneFiles, manifest.versionLabel(), lastClientVersion, stagedLauncher);
  }

  private String describe(SyncResult result) {
    String release = result.clientVersion != null && result.clientVersion.length() > 0
        ? "client " + result.clientVersion
        : result.versionLabel;
    switch (result.outcome) {
      case UPDATED:
        return "Updated to " + release + " - ready to play";
      case UP_TO_DATE:
        return "Up to date - " + release;
      case OFFLINE:
        return "Offline - couldn't check for updates";
      case NO_MANIFEST:
      default:
        return Main.disabledUpdate ? "Ready to play - updates disabled" : "Ready to play - local dev files";
    }
  }

  private boolean isVerified(File destination, ManifestEntry entry, Properties state) throws Exception {
    if (!destination.exists() || !destination.isFile()) {
      return false;
    }

    String recorded = state.getProperty(stateKey(entry.path));
    if (recorded != null) {
      String[] parts = recorded.split(":", 3);
      if (parts.length == 3
          && parts[2].equalsIgnoreCase(entry.sha256)
          && String.valueOf(destination.length()).equals(parts[0])
          && String.valueOf(destination.lastModified()).equals(parts[1])) {
        return true;
      }
    }

    if (sha256(destination).equalsIgnoreCase(entry.sha256)) {
      recordVerified(state, entry, destination);
      return true;
    }
    return false;
  }

  private void recordVerified(Properties state, ManifestEntry entry, File destination) {
    state.setProperty(stateKey(entry.path),
        destination.length() + ":" + destination.lastModified() + ":" + entry.sha256.toLowerCase(Locale.ROOT));
  }

  private String stateKey(String path) {
    return "f." + path;
  }

  private void downloadVerifiedWithRetries(URL url, File destination, ManifestEntry entry,
      DownloadProgress progress) throws Exception {
    Exception last = null;
    for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
      File temp = new File(destination.getParentFile(), destination.getName() + TEMP_SUFFIX);
      try {
        download(url, temp, entry, progress);
        String downloadedSha = sha256(temp);
        if (!downloadedSha.equalsIgnoreCase(entry.sha256)) {
          throw new Exception("SHA-256 mismatch for " + entry.path);
        }
        moveIntoPlace(temp.toPath(), destination.toPath());
        return;
      } catch (Exception e) {
        last = e;
        temp.delete();
        Logger.Warn("Download attempt " + attempt + "/" + DOWNLOAD_ATTEMPTS + " failed for "
            + entry.path + ": " + e.getMessage());
        if (attempt < DOWNLOAD_ATTEMPTS) {
          sleepQuietly(attempt * 1000L);
        }
      }
    }
    throw new Exception("Couldn't download " + entry.path + " after " + DOWNLOAD_ATTEMPTS
        + " attempts: " + (last == null ? "unknown error" : last.getMessage()));
  }

  private void download(URL url, File temp, ManifestEntry entry, DownloadProgress progress) throws Exception {
    URLConnection connection = openConnection(url);

    long size = connection.getContentLengthLong();
    if (size <= 0) {
      size = entry.size;
    }

    InputStream input = new BufferedInputStream(connection.getInputStream());
    OutputStream output = new BufferedOutputStream(new FileOutputStream(temp));
    try {
      byte[] buffer = new byte[8192];
      long fileRead = 0;
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
        fileRead += read;
        reportDownloadProgress(entry, fileRead, size, progress);
      }
    } finally {
      output.close();
      input.close();
    }
  }

  private long lastProgressReportAt;

  private void reportDownloadProgress(ManifestEntry entry, long fileRead, long fileSize, DownloadProgress p) {
    long now = System.currentTimeMillis();
    if (now - lastProgressReportAt < 100) {
      return;
    }
    lastProgressReportAt = now;

    int span = p.bandEnd - p.bandStart;
    if (p.sizesKnown && p.totalBytes > 0) {
      long overall = p.doneBytes + fileRead;
      int progress = p.bandStart + (int) (span * Math.min(overall, p.totalBytes) / p.totalBytes);
      status(p.label + " - " + formatMegabytes(overall) + " / " + formatMegabytes(p.totalBytes)
          + " MB (file " + (p.doneFiles + 1) + "/" + p.totalFiles + ")", progress, true);
    } else if (fileSize > 0) {
      int base = p.bandStart + (p.doneFiles * span / Math.max(p.totalFiles, 1));
      int next = p.bandStart + ((p.doneFiles + 1) * span / Math.max(p.totalFiles, 1));
      int progress = base + (int) ((next - base) * Math.min(fileRead, fileSize) / fileSize);
      status("Downloading " + entry.path + " (" + (p.doneFiles + 1) + "/" + p.totalFiles + ")", progress, true);
    } else {
      status("Downloading " + entry.path + " (" + (p.doneFiles + 1) + "/" + p.totalFiles + ")", -1, true);
    }
  }

  private static class DownloadProgress {
    final String label;
    final int doneFiles;
    final int totalFiles;
    final long doneBytes;
    final long totalBytes;
    final boolean sizesKnown;
    final int bandStart;
    final int bandEnd;

    DownloadProgress(String label, int doneFiles, int totalFiles, long doneBytes, long totalBytes,
        boolean sizesKnown, int bandStart, int bandEnd) {
      this.label = label;
      this.doneFiles = doneFiles;
      this.totalFiles = totalFiles;
      this.doneBytes = doneBytes;
      this.totalBytes = totalBytes;
      this.sizesKnown = sizesKnown;
      this.bandStart = bandStart;
      this.bandEnd = bandEnd;
    }
  }

  private String formatMegabytes(long bytes) {
    return String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0));
  }

  private Manifest fetchManifestWithRetries(String manifestUrl) throws Exception {
    URL url = new URL(manifestUrl);
    requireSecureUrl(url);
    Exception last = null;
    for (int attempt = 1; attempt <= MANIFEST_ATTEMPTS; attempt++) {
      try {
        return fetchManifest(url);
      } catch (Exception e) {
        last = e;
        Logger.Warn("Manifest fetch attempt " + attempt + "/" + MANIFEST_ATTEMPTS + " failed: " + e.getMessage());
        if (attempt < MANIFEST_ATTEMPTS) {
          sleepQuietly(attempt * 800L);
        }
      }
    }
    throw last == null ? new Exception("Manifest fetch failed") : last;
  }

  private Manifest fetchManifest(URL url) throws Exception {
    URLConnection connection = openConnection(url);
    Properties properties = new Properties();
    InputStream input = new BufferedInputStream(connection.getInputStream());
    try {
      properties.load(input);
    } finally {
      input.close();
    }
    return Manifest.fromProperties(properties);
  }

  private URLConnection openConnection(URL url) throws Exception {
    URLConnection connection = url.openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    connection.setRequestProperty("User-Agent", VoidscapeLauncherConfig.USER_AGENT);
    if (connection instanceof HttpURLConnection) {
      int code = ((HttpURLConnection) connection).getResponseCode();
      if (code >= 400) {
        throw new IOException("HTTP " + code + " from " + url);
      }
    }
    return connection;
  }

  private URL resolveEntryUrl(String baseUrl, String entryUrl, String path) throws Exception {
    if (entryUrl != null && entryUrl.length() > 0) {
      return new URL(entryUrl);
    }
    return new URL(baseUrl + path.replace(File.separatorChar, '/'));
  }

  /** Update payloads execute on player machines: require TLS except for loopback dev/test hosts. */
  private void requireSecureUrl(URL url) throws Exception {
    String protocol = url.getProtocol() == null ? "" : url.getProtocol().toLowerCase(Locale.ROOT);
    if (protocol.equals("https") || protocol.equals("file")) {
      return;
    }
    if (protocol.equals("http")) {
      String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
      if (host.equals("localhost") || host.startsWith("127.") || host.equals("::1") || host.equals("[::1]")) {
        return;
      }
      if (VoidscapeLauncherConfig.allowInsecureHttp()) {
        return;
      }
      throw new UpdatePolicyException("Refusing plain-http update URL " + url
          + " (set voidscape.allowInsecureHttp=true to override)");
    }
    throw new UpdatePolicyException("Unsupported update URL scheme: " + url);
  }

  // ---------------------------------------------------------------------
  // Launcher self-update
  // ---------------------------------------------------------------------

  private boolean shouldStageLauncherUpdate(Manifest manifest) {
    if (Main.disabledUpdate || Main.relaunchedAfterSelfUpdate) {
      return false;
    }
    if (manifest.launcherSha256 == null || manifest.launcherUrl == null) {
      return false;
    }
    String runningSha = runningJarSha();
    if (runningSha == null) {
      // Not running from a jar (dev classes dir): nothing meaningful to update.
      return false;
    }
    return !runningSha.equalsIgnoreCase(manifest.launcherSha256);
  }

  private File stageLauncherUpdate(Manifest manifest) throws Exception {
    File target = launcherJarFor(manifest.launcherSha256);
    if (target.exists() && sha256(target).equalsIgnoreCase(manifest.launcherSha256)) {
      return target;
    }

    status("Downloading launcher update", 98, true);
    File parent = target.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new Exception("Could not create folder: " + parent.getAbsolutePath());
    }

    URL url = resolveEntryUrl(manifest.baseUrl, manifest.launcherUrl, "");
    requireSecureUrl(url);
    ManifestEntry launcherEntry = new ManifestEntry("launcher update", manifest.launcherSha256, null, manifest.launcherSize);
    DownloadProgress progress = new DownloadProgress("Downloading launcher update", 0, 1,
        0, Math.max(manifest.launcherSize, 0), manifest.launcherSize > 0, 97, 99);
    Exception last = null;
    for (int attempt = 1; attempt <= DOWNLOAD_ATTEMPTS; attempt++) {
      File temp = new File(parent, target.getName() + TEMP_SUFFIX);
      try {
        download(url, temp, launcherEntry, progress);
        if (!sha256(temp).equalsIgnoreCase(manifest.launcherSha256)) {
          throw new Exception("SHA-256 mismatch for launcher update");
        }
        moveIntoPlace(temp.toPath(), target.toPath());
        return target;
      } catch (Exception e) {
        last = e;
        temp.delete();
        if (attempt < DOWNLOAD_ATTEMPTS) {
          sleepQuietly(attempt * 1000L);
        }
      }
    }
    throw new Exception("Couldn't download the launcher update: "
        + (last == null ? "unknown error" : last.getMessage()));
  }

  private File launcherJarFor(String sha256) {
    String sha8 = sha256.toLowerCase(Locale.ROOT).substring(0, Math.min(8, sha256.length()));
    return new File(new File(cacheDir, LAUNCHER_DIR), "VoidscapeLauncher-" + sha8 + ".jar");
  }

  private void pruneStaleLauncherJars(Manifest manifest) {
    File dir = new File(cacheDir, LAUNCHER_DIR);
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    File keep = manifest.launcherSha256 == null ? null : launcherJarFor(manifest.launcherSha256);
    File running = runningJarFile();
    for (File file : files) {
      String name = file.getName();
      if (!name.startsWith("VoidscapeLauncher-") || !name.endsWith(".jar")) {
        continue;
      }
      if (keep != null && file.equals(keep)) {
        continue;
      }
      if (running != null && file.getAbsolutePath().equals(running.getAbsolutePath())) {
        continue;
      }
      if (!file.delete()) {
        Logger.Warn("Unable to delete old launcher jar " + file.getAbsolutePath());
      }
    }
  }

  /**
   * Re-exec into the freshly staged launcher jar. The originally distributed jar stays untouched
   * and keeps working as a bootstrap: on its next start it finds its own hash differs from the
   * manifest, downloads/reuses the staged jar, and chains into it again.
   */
  private boolean restartIntoUpdatedLauncher(File stagedJar) {
    try {
      status("Launcher updated - restarting...", 100, true);
      List<String> command = new ArrayList<String>();
      command.add(javaBinary());
      command.add("-jar");
      command.add(stagedJar.getAbsolutePath());
      command.add("--dir");
      command.add(cacheDir.getAbsolutePath());
      command.add("--relaunched");
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.directory(cacheDir);
      builder.redirectErrorStream(true);
      builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
      builder.start();
      sleepQuietly(600);
      System.exit(0);
      return true;
    } catch (Exception e) {
      Logger.Error("Couldn't restart into updated launcher: " + e.getMessage());
      status("Launcher update downloaded - restart the launcher to apply", 100, false);
      return false;
    }
  }

  private static String cachedRunningJarSha;
  private static boolean runningJarShaComputed;

  private String runningJarSha() {
    if (runningJarShaComputed) {
      return cachedRunningJarSha;
    }
    runningJarShaComputed = true;
    try {
      File jar = runningJarFile();
      if (jar != null) {
        cachedRunningJarSha = sha256(jar);
      }
    } catch (Exception e) {
      Logger.Warn("Couldn't hash running launcher jar: " + e.getMessage());
    }
    return cachedRunningJarSha;
  }

  private File runningJarFile() {
    try {
      URL location = VoidscapeUpdater.class.getProtectionDomain().getCodeSource().getLocation();
      if (location == null) {
        return null;
      }
      File file = new File(location.toURI());
      if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
        return file;
      }
      return null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private String javaBinary() {
    String javaHome = System.getProperty("java.home");
    if (javaHome != null && javaHome.length() > 0) {
      boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
      File candidate = new File(javaHome, "bin" + File.separator + (windows ? "java.exe" : "java"));
      if (candidate.isFile()) {
        return candidate.getAbsolutePath();
      }
    }
    return "java";
  }

  // ---------------------------------------------------------------------
  // Sync state + pruning
  // ---------------------------------------------------------------------

  private File syncStateFile() {
    return new File(cacheDir, SYNC_STATE_FILE);
  }

  private Properties loadSyncState() {
    Properties state = new Properties();
    File file = syncStateFile();
    if (!file.isFile()) {
      return state;
    }
    InputStream input = null;
    try {
      input = new FileInputStream(file);
      state.load(input);
    } catch (Exception e) {
      Logger.Warn("Couldn't read sync state, re-verifying everything: " + e.getMessage());
      state = new Properties();
    } finally {
      closeQuietly(input);
    }
    return state;
  }

  private boolean hasVerifiedLocalFilesForFallback() {
    File clientJar = VoidscapeLauncherConfig.clientJar(cacheDir);
    if (!clientJar.isFile()) {
      return false;
    }

    Properties state = loadSyncState();
    String clientRecord = state.getProperty(stateKey(VoidscapeLauncherConfig.CLIENT_JAR));
    if (!recordMatchesFile(clientRecord, clientJar)) {
      return false;
    }

    int verifiedCount = 0;
    for (Object keyObject : state.keySet()) {
      String key = String.valueOf(keyObject);
      if (!key.startsWith("f.")) {
        continue;
      }
      String path = key.substring(2);
      try {
        File file = safeDestinationForLookup(path);
        if (!file.isFile() || !recordMatchesFile(state.getProperty(key), file)) {
          return false;
        }
        verifiedCount++;
      } catch (Exception e) {
        Logger.Warn("Verified local fallback rejected stale path " + path + ": " + e.getMessage());
        return false;
      }
    }
    return verifiedCount > 0;
  }

  private boolean recordMatchesFile(String record, File file) {
    if (record == null || file == null || !file.isFile()) {
      return false;
    }
    String[] parts = record.split(":", 3);
    if (parts.length != 3) {
      return false;
    }
    if (!String.valueOf(file.length()).equals(parts[0]) || !parts[2].matches("(?i)[0-9a-f]{64}")) {
      return false;
    }
    try {
      return sha256(file).equalsIgnoreCase(parts[2]);
    } catch (Exception e) {
      Logger.Warn("Couldn't verify cached file " + file.getAbsolutePath() + ": " + e.getMessage());
      return false;
    }
  }

  private void saveSyncState(Properties state) {
    OutputStream output = null;
    try {
      output = new FileOutputStream(syncStateFile());
      state.store(output, "Voidscape launcher sync state - safe to delete (forces a full re-verify)");
    } catch (Exception e) {
      Logger.Warn("Couldn't save sync state: " + e.getMessage());
    } finally {
      closeQuietly(output);
    }
  }

  private void deleteSyncState() {
    File file = syncStateFile();
    if (file.exists() && !file.delete()) {
      Logger.Warn("Unable to delete " + file.getAbsolutePath());
    }
  }

  /** Delete files we previously managed that the manifest no longer lists. */
  private void pruneRemovedFiles(Manifest manifest, Properties state) {
    List<String> stale = new ArrayList<String>();
    for (Object keyObject : state.keySet()) {
      String key = String.valueOf(keyObject);
      if (!key.startsWith("f.")) {
        continue;
      }
      String path = key.substring(2);
      if (manifest.hasPath(path)) {
        continue;
      }
      stale.add(key);
      if (isRuntimeFile(new File(path).getName())) {
        continue;
      }
      try {
        File file = safeDestination(path);
        if (file.exists() && !file.delete()) {
          Logger.Warn("Unable to prune " + file.getAbsolutePath());
        }
      } catch (Exception e) {
        Logger.Warn("Unable to prune " + path + ": " + e.getMessage());
      }
    }
    for (String key : stale) {
      state.remove(key);
    }
  }

  private void cleanupStaleTemps(File dir) {
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (File file : files) {
      if (file.isDirectory()) {
        cleanupStaleTemps(file);
      } else if (file.getName().endsWith(TEMP_SUFFIX)) {
        if (!file.delete()) {
          Logger.Warn("Unable to delete stale temp " + file.getAbsolutePath());
        }
      }
    }
  }

  // ---------------------------------------------------------------------
  // Prepare / local dev seeding (unchanged behavior)
  // ---------------------------------------------------------------------

  private void runAsync(final String initialStatus, final CheckedRunnable runnable) {
    synchronized (this) {
      if (busy) {
        status("Launcher is already working...", -1, true);
        return;
      }
      busy = true;
    }

    Thread worker = new Thread(new Runnable() {
      @Override
      public void run() {
        status(initialStatus, -1, true);
        try {
          runnable.run();
        } catch (Exception e) {
          Logger.Error(initialStatus + " failed: " + e.getMessage());
          status("Error: " + e.getMessage(), 0, false);
        } finally {
          busy = false;
        }
      }
    }, "VoidscapeLauncherWorker");
    worker.setDaemon(true);
    worker.start();
  }

  private void prepare(boolean forceLocalSeed) throws Exception {
    ensureCacheDir();
    writeEndpointFiles();
    seedLocalCacheAssets(forceLocalSeed);
    seedLocalClientJar(forceLocalSeed);
  }

  private void ensureCacheDir() throws Exception {
    if (!cacheDir.exists() && !cacheDir.mkdirs()) {
      throw new Exception("Could not create cache folder: " + cacheDir.getAbsolutePath());
    }
    if (!cacheDir.isDirectory()) {
      throw new Exception("Cache path is not a folder: " + cacheDir.getAbsolutePath());
    }
  }

  private void writeEndpointFiles() throws Exception {
    writeText(new File(cacheDir, "ip.txt"), VoidscapeLauncherConfig.serverHost());
    writeText(new File(cacheDir, "port.txt"), String.valueOf(VoidscapeLauncherConfig.serverPort()));
  }

  private void writeText(File file, String value) throws Exception {
    OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
    try {
      writer.write(value);
      writer.write(System.lineSeparator());
    } finally {
      writer.close();
    }
  }

  private void seedLocalClientJar(boolean force) throws Exception {
    File source = findLocalClientJar();
    if (source == null) {
      return;
    }

    File destination = VoidscapeLauncherConfig.clientJar(cacheDir);
    if (!force && destination.exists() && destination.length() == source.length()
        && destination.lastModified() >= source.lastModified()) {
      return;
    }

    status("Seeding local client build", -1, true);
    Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
  }

  private File findLocalClientJar() {
    File[] candidates = new File[] {
        new File("Client_Base" + File.separator + VoidscapeLauncherConfig.CLIENT_JAR),
        new File(".." + File.separator + "Client_Base" + File.separator + VoidscapeLauncherConfig.CLIENT_JAR),
        new File(".." + File.separator + ".." + File.separator + "Client_Base" + File.separator + VoidscapeLauncherConfig.CLIENT_JAR)
    };
    for (File candidate : candidates) {
      if (candidate.exists() && candidate.isFile()) {
        return candidate;
      }
    }
    return null;
  }

  private void seedLocalCacheAssets(boolean force) throws Exception {
    File source = findLocalCacheDir();
    if (source == null) {
      return;
    }
    status("Seeding local cache assets", -1, true);
    copyCacheDirectory(source, cacheDir, force);
  }

  private File findLocalCacheDir() {
    File[] candidates = new File[] {
        new File("Client_Base" + File.separator + "Cache"),
        new File(".." + File.separator + "Client_Base" + File.separator + "Cache"),
        new File(".." + File.separator + ".." + File.separator + "Client_Base" + File.separator + "Cache")
    };
    for (File candidate : candidates) {
      if (candidate.exists() && candidate.isDirectory()) {
        return candidate;
      }
    }
    return null;
  }

  private void copyCacheDirectory(File sourceRoot, File destinationRoot, boolean force) throws Exception {
    File[] files = sourceRoot.listFiles();
    if (files == null) {
      return;
    }

    for (File source : files) {
      if (isRuntimeFile(source.getName())) {
        continue;
      }

      File destination = new File(destinationRoot, source.getName());
      if (source.isDirectory()) {
        if (!destination.exists() && !destination.mkdirs()) {
          throw new Exception("Could not create cache folder: " + destination.getAbsolutePath());
        }
        copyCacheDirectory(source, destination, force);
      } else if (source.isFile()) {
        copyIfNeeded(source, destination, force);
      }
    }
  }

  private void copyIfNeeded(File source, File destination, boolean force) throws Exception {
    if (!force && destination.exists() && destination.length() == source.length()
        && destination.lastModified() >= source.lastModified()) {
      return;
    }
    File parent = destination.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new Exception("Could not create cache folder: " + parent.getAbsolutePath());
    }
    Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
  }

  private boolean isRuntimeFile(String name) {
    for (String runtimeFile : RUNTIME_FILES) {
      if (runtimeFile.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  private void deleteMutableClientFiles() {
    String[] files = new String[] {
        "client.properties",
        "config.txt",
        "discord_inuse.txt"
    };
    for (String file : files) {
      File target = new File(cacheDir, file);
      if (target.exists() && !target.delete()) {
        Logger.Warn("Unable to delete " + target.getAbsolutePath());
      }
    }
  }

  // ---------------------------------------------------------------------
  // Shared plumbing
  // ---------------------------------------------------------------------

  private void addJvmProperty(List<String> command, String property, String value) {
    if (value == null || value.trim().length() == 0) {
      return;
    }
    command.add("-D" + property + "=" + value.trim());
  }

  private File safeDestination(String relativePath) throws Exception {
    File destination = safeDestinationForLookup(relativePath);
    File parent = destination.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new Exception("Could not create folder: " + parent.getAbsolutePath());
    }
    return destination;
  }

  private File safeDestinationForLookup(String relativePath) throws Exception {
    String normalizedInput = relativePath.replace('\\', '/');
    if (normalizedInput.startsWith("/") || normalizedInput.contains("../") || normalizedInput.equals("..")
        || normalizedInput.contains("/..")) {
      throw new Exception("Unsafe manifest path: " + relativePath);
    }

    Path normalized = Paths.get(normalizedInput).normalize();
    if (normalized.isAbsolute()) {
      throw new Exception("Unsafe manifest path: " + relativePath);
    }

    return new File(cacheDir, normalized.toString());
  }

  private void moveIntoPlace(Path source, Path destination) throws Exception {
    try {
      Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private String sha256(File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    FileInputStream input = new FileInputStream(file);
    try {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
    } finally {
      input.close();
    }

    byte[] hash = digest.digest();
    StringBuilder builder = new StringBuilder();
    for (byte value : hash) {
      builder.append(String.format("%02x", value & 0xff));
    }
    return builder.toString();
  }

  private void status(String message, int progress, boolean isBusy) {
    if (listener != null) {
      listener.onStatus(message, progress, isBusy);
    }
  }

  private void showDialog(final Component parent, final String message, final int type) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        JOptionPane.showMessageDialog(parent, message, VoidscapeLauncherConfig.TITLE, type);
      }
    });
  }

  private void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private void closeQuietly(InputStream input) {
    if (input != null) {
      try {
        input.close();
      } catch (Exception ignored) {
      }
    }
  }

  private void closeQuietly(OutputStream output) {
    if (output != null) {
      try {
        output.close();
      } catch (Exception ignored) {
      }
    }
  }

  private interface CheckedRunnable {
    void run() throws Exception;
  }

  private static class Manifest {
    final String version;
    final String clientVersion;
    final String baseUrl;
    final List<ManifestEntry> entries;
    final String launcherSha256;
    final String launcherUrl;
    final long launcherSize;

    Manifest(String version, String clientVersion, String baseUrl, List<ManifestEntry> entries,
        String launcherSha256, String launcherUrl, long launcherSize) {
      this.version = version;
      this.clientVersion = clientVersion;
      this.baseUrl = baseUrl == null ? "" : baseUrl;
      this.entries = entries;
      this.launcherSha256 = launcherSha256;
      this.launcherUrl = launcherUrl;
      this.launcherSize = launcherSize;
    }

    String versionLabel() {
      if (version == null || version.trim().length() == 0) {
        return "latest manifest";
      }
      return version;
    }

    boolean hasPath(String path) {
      for (ManifestEntry entry : entries) {
        if (entry.path.equals(path)) {
          return true;
        }
      }
      return false;
    }

    static Manifest fromProperties(Properties properties) {
      String version = properties.getProperty("version", "");
      String clientVersion = trimToNull(properties.getProperty("clientVersion"));
      String baseUrl = properties.getProperty("baseUrl", "");
      List<ManifestEntry> entries = new ArrayList<ManifestEntry>();

      int index = 1;
      while (true) {
        String prefix = "file." + index + ".";
        String path = properties.getProperty(prefix + "path");
        if (path == null) {
          break;
        }
        String sha256 = properties.getProperty(prefix + "sha256", "");
        String url = properties.getProperty(prefix + "url");
        long size = parseSize(properties.getProperty(prefix + "size"));
        if (path.trim().length() > 0 && sha256.trim().length() > 0) {
          entries.add(new ManifestEntry(path.trim(), sha256.trim(), url == null ? null : url.trim(), size));
        }
        index++;
      }

      String launcherSha256 = trimToNull(properties.getProperty("launcher.sha256"));
      String launcherUrl = trimToNull(properties.getProperty("launcher.url"));
      long launcherSize = parseSize(properties.getProperty("launcher.size"));
      if (launcherSha256 == null || launcherUrl == null) {
        launcherSha256 = null;
        launcherUrl = null;
      }

      return new Manifest(version, clientVersion, baseUrl, entries, launcherSha256, launcherUrl, launcherSize);
    }

    private static String trimToNull(String value) {
      if (value == null || value.trim().length() == 0) {
        return null;
      }
      return value.trim();
    }

    private static long parseSize(String value) {
      if (value == null || value.trim().length() == 0) {
        return -1;
      }
      try {
        long parsed = Long.parseLong(value.trim());
        return parsed >= 0 ? parsed : -1;
      } catch (NumberFormatException ignored) {
        return -1;
      }
    }
  }

  private static class ManifestEntry {
    final String path;
    final String sha256;
    final String url;
    final long size;

    ManifestEntry(String path, String sha256, String url, long size) {
      this.path = path;
      this.sha256 = sha256;
      this.url = url == null || url.length() == 0 ? null : url;
      this.size = size;
    }
  }
}
