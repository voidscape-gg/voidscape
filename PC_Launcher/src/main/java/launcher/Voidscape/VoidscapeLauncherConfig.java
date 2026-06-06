package launcher.Voidscape;

import launcher.Main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Properties;

public final class VoidscapeLauncherConfig {
  public static final String TITLE = "Voidscape Launcher";
  public static final String CLIENT_JAR = "Open_RSC_Client.jar";
  public static final String USER_AGENT = "VoidscapeLauncher/1.0";
  public static final int WINDOW_WIDTH = 820;
  public static final int WINDOW_HEIGHT = 560;

  private static final String DEFAULT_PORTAL_URL = "";
  private static final String DEFAULT_WEBSITE_URL = "";
  private static final String PORTAL_MANIFEST_PATH = "api/launcher/manifest.properties";
  private static final String LAUNCHER_PROPERTIES = "voidscape-launcher.properties";
  private static final Properties PACKAGED_SETTINGS = loadPackagedSettings();

  private VoidscapeLauncherConfig() {
  }

  public static File cacheDir() {
    return new File(Main.configFileLocation);
  }

  public static File clientJar(File cacheDir) {
    return new File(cacheDir, CLIENT_JAR);
  }

  public static String serverHost() {
    return setting("voidscape.serverHost", "VOIDSCAPE_SERVER_HOST", "127.0.0.1");
  }

  public static int serverPort() {
    String configured = setting("voidscape.serverPort", "VOIDSCAPE_SERVER_PORT", null);
    if (configured != null && configured.trim().length() > 0) {
      return parsePort(configured.trim(), 43594);
    }
    return detectLocalServerPort();
  }

  public static String manifestUrl() {
    String configured = setting("voidscape.manifestUrl", "VOIDSCAPE_MANIFEST_URL", null);
    if (configured != null && configured.trim().length() > 0) {
      return configured.trim();
    }
    return portalPath(PORTAL_MANIFEST_PATH);
  }

  public static String portalUrl() {
    return setting("voidscape.portalUrl", "VOIDSCAPE_PORTAL_URL", DEFAULT_PORTAL_URL);
  }

  public static String portalAccountUrl() {
    return portalRoute("dashboard");
  }

  public static String portalCharactersUrl() {
    return portalRoute("characters");
  }

  public static String websiteUrl() {
    return setting("voidscape.websiteUrl", "VOIDSCAPE_WEBSITE_URL", DEFAULT_WEBSITE_URL);
  }

  public static String discordUrl() {
    return setting("voidscape.discordUrl", "VOIDSCAPE_DISCORD_URL", "");
  }

  public static String endpointLabel() {
    return serverHost() + ":" + serverPort();
  }

  private static String portalRoute(String route) {
    String url = portalBaseUrl();
    if (url == null || url.trim().length() == 0) {
      return "";
    }
    return url + "#" + route;
  }

  private static String portalPath(String path) {
    String url = portalBaseUrl();
    if (url == null || url.trim().length() == 0) {
      return "";
    }
    String cleanedPath = path == null ? "" : path.trim();
    while (cleanedPath.startsWith("/")) {
      cleanedPath = cleanedPath.substring(1);
    }
    return url + "/" + cleanedPath;
  }

  private static String portalBaseUrl() {
    String url = portalUrl();
    if (url == null || url.trim().length() == 0) {
      return "";
    }
    String trimmed = url.trim();
    int hash = trimmed.indexOf('#');
    if (hash >= 0) {
      trimmed = trimmed.substring(0, hash);
    }
    int query = trimmed.indexOf('?');
    if (query >= 0) {
      trimmed = trimmed.substring(0, query);
    }
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static String setting(String property, String env, String fallback) {
    String value = System.getProperty(property);
    if (value != null && value.trim().length() > 0) {
      return value.trim();
    }
    value = System.getenv(env);
    if (value != null && value.trim().length() > 0) {
      return value.trim();
    }
    value = PACKAGED_SETTINGS.getProperty(property);
    if (value != null && value.trim().length() > 0) {
      return value.trim();
    }
    return fallback;
  }

  private static Properties loadPackagedSettings() {
    Properties settings = new Properties();
    loadResource(settings, "/data/" + LAUNCHER_PROPERTIES);
    loadResource(settings, "/" + LAUNCHER_PROPERTIES);
    loadFile(settings, launcherSidecarFile());
    loadFile(settings, new File(LAUNCHER_PROPERTIES));
    return settings;
  }

  private static void loadResource(Properties settings, String path) {
    InputStream input = null;
    try {
      input = VoidscapeLauncherConfig.class.getResourceAsStream(path);
      if (input != null) {
        settings.load(input);
      }
    } catch (Exception ignored) {
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  private static void loadFile(Properties settings, File file) {
    if (file == null || !file.exists() || !file.isFile()) {
      return;
    }
    InputStream input = null;
    try {
      input = new FileInputStream(file);
      settings.load(input);
    } catch (Exception ignored) {
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  private static File launcherSidecarFile() {
    try {
      URL location = VoidscapeLauncherConfig.class.getProtectionDomain().getCodeSource().getLocation();
      if (location == null) {
        return null;
      }
      URI uri = location.toURI();
      File path = new File(uri);
      File dir = path.isDirectory() ? path : path.getParentFile();
      if (dir == null) {
        return null;
      }
      return new File(dir, LAUNCHER_PROPERTIES);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static int detectLocalServerPort() {
    int port = readPortFrom(new File("server/local.conf"));
    if (port > 0) {
      return port;
    }
    port = readPortFrom(new File("../server/local.conf"));
    if (port > 0) {
      return port;
    }
    return 43594;
  }

  private static int readPortFrom(File file) {
    if (!file.exists() || !file.isFile()) {
      return -1;
    }
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(file));
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("server_port:")) {
          continue;
        }
        String value = trimmed.substring("server_port:".length()).trim();
        int commentIndex = value.indexOf('#');
        if (commentIndex >= 0) {
          value = value.substring(0, commentIndex).trim();
        }
        return parsePort(value, -1);
      }
    } catch (Exception ignored) {
      return -1;
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (Exception ignored) {
        }
      }
    }
    return -1;
  }

  private static int parsePort(String value, int fallback) {
    try {
      int port = Integer.parseInt(value);
      if (port > 0 && port <= 65535) {
        return port;
      }
    } catch (NumberFormatException ignored) {
    }
    return fallback;
  }
}
