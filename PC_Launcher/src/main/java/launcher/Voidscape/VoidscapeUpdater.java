package launcher.Voidscape;

import launcher.Utils.Logger;
import launcher.Utils.Utils;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
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
import java.util.Properties;

public class VoidscapeUpdater {
  public interface StatusListener {
    void onStatus(String message, int progress, boolean busy);
  }

  private static final String[] RUNTIME_FILES = new String[] {
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

  private final File cacheDir;
  private final StatusListener listener;
  private volatile boolean busy;

  public VoidscapeUpdater(File cacheDir, StatusListener listener) {
    this.cacheDir = cacheDir;
    this.listener = listener;
  }

  public boolean isBusy() {
    return busy;
  }

  public void prepareAsync() {
    runAsync("Preparing launcher", new CheckedRunnable() {
      @Override
      public void run() throws Exception {
        prepare(false);
      }
    });
  }

  public void checkForUpdatesAsync() {
    runAsync("Checking for updates", new CheckedRunnable() {
      @Override
      public void run() throws Exception {
        prepare(false);
        updateFromManifest();
      }
    });
  }

  public void repairAsync() {
    runAsync("Repairing cache", new CheckedRunnable() {
      @Override
      public void run() throws Exception {
        deleteMutableClientFiles();
        prepare(true);
        updateFromManifest();
      }
    });
  }

  public void launchClient(Component parent) {
    if (busy) {
      JOptionPane.showMessageDialog(parent, "The launcher is still preparing files. Try again in a moment.");
      return;
    }

    try {
      prepare(false);
      File jar = VoidscapeLauncherConfig.clientJar(cacheDir);
      if (hasManifestUrl()) {
        updateFromManifest();
      }
      if (!jar.exists()) {
        JOptionPane.showMessageDialog(parent,
            "The Voidscape client is missing.\n\nRun Check for Updates, or build the PC client locally first.",
            VoidscapeLauncherConfig.TITLE,
            JOptionPane.ERROR_MESSAGE);
        return;
      }

      status("Launching Voidscape...", 100, false);
      List<String> command = new ArrayList<String>();
      command.add("java");
      command.add("-Dsun.java2d.d3d=false");
      command.add("-Dsun.java2d.noddraw=true");
      command.add("-Dsun.java2d.opengl=false");
      addJvmProperty(command, "voidscape.discordApplicationId", VoidscapeLauncherConfig.discordApplicationId());
      addJvmProperty(command, "voidscape.discordLargeImageKey", VoidscapeLauncherConfig.discordLargeImageKey());
      addJvmProperty(command, "voidscape.discordLargeImageText", VoidscapeLauncherConfig.discordLargeImageText());
      command.add("-jar");
      command.add(jar.getAbsolutePath());
      Utils.execCmd(command.toArray(new String[command.size()]), cacheDir);
    } catch (Exception e) {
      Logger.Error("Unable to launch Voidscape client: " + e.getMessage());
      JOptionPane.showMessageDialog(parent,
          "Unable to launch the client:\n" + e.getMessage(),
          VoidscapeLauncherConfig.TITLE,
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void addJvmProperty(List<String> command, String property, String value) {
    if (value == null || value.trim().length() == 0) {
      return;
    }
    command.add("-D" + property + "=" + value.trim());
  }

  private boolean hasManifestUrl() {
    String manifestUrl = VoidscapeLauncherConfig.manifestUrl();
    return manifestUrl != null && manifestUrl.trim().length() > 0;
  }

  public File getCacheDir() {
    return cacheDir;
  }

  private void runAsync(final String initialStatus, final CheckedRunnable runnable) {
    if (busy) {
      status("Launcher is already working...", -1, true);
      return;
    }

    Thread worker = new Thread(new Runnable() {
      @Override
      public void run() {
        busy = true;
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
    status("Ready to play", 100, false);
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

    status("Seeding local client build", 35, true);
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
    status("Seeding local cache assets", 20, true);
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

  private void updateFromManifest() throws Exception {
    String manifestUrl = VoidscapeLauncherConfig.manifestUrl();
    if (manifestUrl == null || manifestUrl.trim().length() == 0) {
      status("Ready to play - local dev files", 100, false);
      return;
    }

    status("Fetching update manifest", 10, true);
    Manifest manifest = fetchManifest(manifestUrl);
    if (manifest.entries.size() == 0) {
      status("Manifest has no files", 100, false);
      return;
    }

    int complete = 0;
    for (ManifestEntry entry : manifest.entries) {
      complete++;
      updateManifestEntry(manifest, entry, complete, manifest.entries.size());
    }
    status("Updated to " + manifest.versionLabel(), 100, false);
  }

  private Manifest fetchManifest(String manifestUrl) throws Exception {
    URLConnection connection = new URL(manifestUrl).openConnection();
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(10000);
    connection.setRequestProperty("User-Agent", VoidscapeLauncherConfig.USER_AGENT);

    Properties properties = new Properties();
    BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
    try {
      properties.load(input);
    } finally {
      input.close();
    }
    return Manifest.fromProperties(properties);
  }

  private void updateManifestEntry(Manifest manifest, ManifestEntry entry, int complete, int total) throws Exception {
    File destination = safeDestination(entry.path);
    if (destination.exists() && sha256(destination).equalsIgnoreCase(entry.sha256)) {
      status("Verified " + entry.path, progressFor(complete, total), true);
      return;
    }

    URL url = new URL(entry.url != null ? entry.url : manifest.baseUrl + entry.path.replace(File.separatorChar, '/'));
    File temp = new File(destination.getParentFile(), destination.getName() + ".download");
    download(url, temp, entry.path, progressFor(complete - 1, total), progressFor(complete, total));

    String downloadedSha = sha256(temp);
    if (!downloadedSha.equalsIgnoreCase(entry.sha256)) {
      temp.delete();
      throw new Exception("SHA-256 mismatch for " + entry.path);
    }

    moveIntoPlace(temp.toPath(), destination.toPath());
  }

  private File safeDestination(String relativePath) throws Exception {
    String normalizedInput = relativePath.replace('\\', '/');
    if (normalizedInput.startsWith("/") || normalizedInput.contains("../") || normalizedInput.equals("..")
        || normalizedInput.contains("/..")) {
      throw new Exception("Unsafe manifest path: " + relativePath);
    }

    Path normalized = Paths.get(normalizedInput).normalize();
    if (normalized.isAbsolute()) {
      throw new Exception("Unsafe manifest path: " + relativePath);
    }

    File destination = new File(cacheDir, normalized.toString());
    File parent = destination.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new Exception("Could not create folder: " + parent.getAbsolutePath());
    }
    return destination;
  }

  private void download(URL url, File destination, String label, int startProgress, int endProgress) throws Exception {
    URLConnection connection = url.openConnection();
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(20000);
    connection.setRequestProperty("User-Agent", VoidscapeLauncherConfig.USER_AGENT);

    long size = connection.getContentLengthLong();
    BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
    BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination));
    try {
      byte[] buffer = new byte[8192];
      long totalRead = 0;
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
        totalRead += read;
        if (size > 0) {
          int progress = startProgress + (int) ((endProgress - startProgress) * totalRead / size);
          status("Downloading " + label, progress, true);
        }
      }
    } finally {
      output.close();
      input.close();
    }
  }

  private void moveIntoPlace(Path source, Path destination) throws Exception {
    try {
      Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private int progressFor(int complete, int total) {
    if (total <= 0) {
      return 100;
    }
    return 10 + (complete * 90 / total);
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

  private interface CheckedRunnable {
    void run() throws Exception;
  }

  private static class Manifest {
    final String version;
    final String baseUrl;
    final List<ManifestEntry> entries;

    Manifest(String version, String baseUrl, List<ManifestEntry> entries) {
      this.version = version;
      this.baseUrl = baseUrl == null ? "" : baseUrl;
      this.entries = entries;
    }

    String versionLabel() {
      if (version == null || version.trim().length() == 0) {
        return "latest manifest";
      }
      return version;
    }

    static Manifest fromProperties(Properties properties) {
      String version = properties.getProperty("version", "");
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
        if (path.trim().length() > 0 && sha256.trim().length() > 0) {
          entries.add(new ManifestEntry(path.trim(), sha256.trim(), url == null ? null : url.trim()));
        }
        index++;
      }

      return new Manifest(version, baseUrl, entries);
    }
  }

  private static class ManifestEntry {
    final String path;
    final String sha256;
    final String url;

    ManifestEntry(String path, String sha256, String url) {
      this.path = path;
      this.sha256 = sha256;
      this.url = url == null || url.length() == 0 ? null : url;
    }
  }
}
