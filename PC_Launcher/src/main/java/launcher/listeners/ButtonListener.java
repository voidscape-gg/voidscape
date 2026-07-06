package launcher.listeners;

import launcher.Fancy.MainWindow;
import launcher.Gameupdater.ClientUpdater;
import launcher.Launcher;
import launcher.Main;
import launcher.Settings;
import launcher.Utils.ClientLauncher;
import launcher.Utils.Logger;
import launcher.Utils.Utils;
import launcher.Voidscape.VoidscapeLauncherConfig;
import launcher.popup.PopupFrame;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import javax.swing.JOptionPane;

public class ButtonListener implements ActionListener {
  public static PopupFrame settingsFrame;
  public static PopupFrame launcherFrame;

  @Override
  public void actionPerformed(final ActionEvent event) {
    final String action = event.getActionCommand().toLowerCase();
    switch (action) {
      case "openrsc_sword_logo": {
        openWebsite();
        return;
      }

      case "bots": {
        Settings.showBotButtons = !Settings.showBotButtons;
        MainWindow.get().toggleBotServers();
        MainWindow.get().buttons.robotCheckbox.setSelected(Settings.showBotButtons);
        return;
      }

      case "uranium_wiki": {
        openWebsite();
        return;
      }
      case "preservation_wiki": {
        openWebsite();
        return;
      }
      case "coleslaw_wiki": {
        openWebsite();
        return;
      }
      case "cabbage_wiki": {
        openWebsite();
        return;
      }
      case "2001scape_wiki": {
        openWebsite();
        return;
      }
      case "kale_wiki": {
        openWebsite();
        return;
      }
      case "openpk_wiki": {
        openWebsite();
        return;
      }

      case "openrsc-forums": {
        openWebsite();
        return;
      }

      case "reddit": {
        showLegacyLinkDisabled("Legacy subreddit link");
        return;
      }

      case "bugs":
      case "cockroach": { // bug report
        openSupport();
        return;
      }
      case "chat":
      case "libera": {
        openDiscord();
        return;
      }
      case "discord": {
        openDiscord();
        return;
      }

      case "preservation_hiscores": {
        openWebsite();
        return;
      }

      case "cabbage_hiscores": {
        openWebsite();
        return;
      }

      case "uranium_hiscores": {
        openWebsite();
        return;
      }

      case "coleslaw_hiscores": {
        openWebsite();
        return;
      }

      case "2001scape_hiscores": {
        openWebsite();
        return;
      }

      case "openpk_hiscores": {
        openWebsite();
        return;
      }

      case "kale_hiscores": {
        openWebsite();
        return;
      }

      case "preservation":
      case "cabbage":
      case "uranium":
      case "coleslaw":
      case "2001scape":
      case "openpk":
      case "kale": {
        try {
          ClientLauncher.launchClientForServer(action);
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        return;
      }

      case "fleacircus": {
        try {
          ClientUpdater.updateFleaCircus();
        } catch (SecurityException | IOException e) {
          e.printStackTrace();
        }
        ClientLauncher.launchFleaCircus();
        return;
      }

      case "robot_checkbox": {
        Settings.showBotButtons = !Settings.showBotButtons;
        MainWindow.get().toggleBotServers();
        return;
      }

      case "undecorated_checkbox": {
        Settings.undecoratedWindowSave = !Settings.undecoratedWindowSave;
        return;
      }

      case "autoupdate_checkbox": {
        Settings.autoUpdate = !Settings.autoUpdate;
        return;
      }

      case "show_prerelease_checkbox": {
        Settings.showPrerelease = !Settings.showPrerelease;
        return;
      }

      case "minimize": {
        MainWindow.get().setState(1);
        return;
      }
      case "close": {
        System.exit(0);
        return;
      }

      case "delete": {
        // Deletes all cache files except for .txt files and .wav files

        File folder = new File(Main.configFileLocation);
        File[] fList = folder.listFiles();
        assert fList != null;
        for (File file : fList) {
          String extension = String.valueOf(file);
          if (!extension.endsWith(".txt")) {
            new File(String.valueOf(file)).delete();
          }
        }

        File video = new File(Main.configFileLocation + "/video");
        File[] vList = video.listFiles();
        assert vList != null;
        for (File file : vList) {
          String extension = String.valueOf(file);
          if (extension.endsWith(".orsc")) {
            new File(String.valueOf(file)).delete();
          }
          if (extension.endsWith(".osar")) {
            new File(String.valueOf(file)).delete();
          }
        }

        File spritepacks = new File(Main.configFileLocation + "/video/spritepacks");
        File[] sList = spritepacks.listFiles();
        assert sList != null;
        for (File file : sList) {
          String extension = String.valueOf(file);
          if (extension.endsWith(".osar")) {
            new File(String.valueOf(file)).delete();
          }
        }

        // Re-download the legacy bundled client.
        Launcher.updater.updateOpenRSCClient();
        return;
      }

      case "gear":
      case "client_settings_button": {
        if (null != settingsFrame)
          settingsFrame.setVisible(false);
        if (null != launcherFrame)
          launcherFrame.setVisible(false);
        settingsFrame = new PopupFrame(PopupFrame.CLIENT_SETTINGS);
        settingsFrame.showFrame();
        return;
      }

      case "advanced_settings_button": {
        if (null != settingsFrame)
          settingsFrame.setVisible(false);
        if (null != launcherFrame)
          launcherFrame.setVisible(false);
        launcherFrame = new PopupFrame(PopupFrame.LAUNCHER_SETTINGS);
        launcherFrame.showFrame();
        return;
      }

      case "question_mark":
      case "about_our_servers_button": {
        openWebsite();
        return;
      }

      case "floppy_disk":
      case "apply_and_save_button": {
        PopupFrame.get().saveClientSelectionsToSettings();
        return;
      }

      case "closepopup":
      case "exit_gear":
      case "exit_settings_button": {
        PopupFrame.get().hideFrame();
        return;
      }

      case "rune-large": {
        showLegacyLinkDisabled("Legacy client source link");
        return;
      }

      case "rscplus-large": {
        showLegacyLinkDisabled("Legacy client source link");
        return;
      }

      case "rsctimes-large": {
        showLegacyLinkDisabled("Legacy client source link");
        return;
      }

      case "openrsc-large": {
        showLegacyLinkDisabled("Legacy client source link");
        return;
      }

      case "aposbot-large": {
        showLegacyLinkDisabled("Legacy client source link");
        return;
      }

      case "mudclient38-large": {
        showLegacyLinkDisabled("Legacy client source link");
        return;
      }

      case "idlersc-large": {
        showLegacyLinkDisabled("Legacy client source link");
        return;
      }

      case "webbrowser-large": {
        showLegacyLinkDisabled("Legacy client source link");
        return;
      }

      default:
        break;
    }
    Logger.Error("unhandled button: " + action);
  }

  private static void openWebsite() {
    openConfiguredUrl(VoidscapeLauncherConfig.websiteUrl(), "Voidscape website");
  }

  private static void openDiscord() {
    openConfiguredUrl(VoidscapeLauncherConfig.discordUrl(), "Voidscape Discord");
  }

  private static void openSupport() {
    Utils.openWebpage("mailto:support@voidscape.gg");
  }

  private static void openConfiguredUrl(String url, String label) {
    if (url != null && url.trim().length() > 0) {
      Utils.openWebpage(url.trim());
      return;
    }
    JOptionPane.showMessageDialog(null, label + " is not configured for this launcher build.",
        "Voidscape Launcher", JOptionPane.INFORMATION_MESSAGE);
  }

  private static void showLegacyLinkDisabled(String label) {
    JOptionPane.showMessageDialog(null,
        label + " is not available in the Voidscape launcher. Visit voidscape.gg or contact support@voidscape.gg.",
        "Voidscape Launcher", JOptionPane.INFORMATION_MESSAGE);
  }
}
