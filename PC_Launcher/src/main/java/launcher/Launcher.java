package launcher;

import launcher.Gameupdater.ClientUpdater;
import launcher.Voidscape.VoidscapeLauncherWindow;

import java.awt.*;
import javax.swing.ImageIcon;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

public class Launcher extends Component {
  public static ImageIcon icon = null;
  private JProgressBar m_progressBar;
  public static ClientUpdater updater;

  public void initializeLauncher() {
    Settings.loadSettings();

    m_progressBar = new JProgressBar();
    updater = new ClientUpdater(Main.configFileLocation);
    VoidscapeLauncherWindow frame = new VoidscapeLauncherWindow();
    frame.build();
  }

  /**
   * Sets the progress value of the launcher progress bar.
   *
   * @param value the number of tasks that have been completed
   * @param total the total number of tasks to complete
   */
  public void setProgress(final int value, final int total) {
    SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            if (total == 0) {
              m_progressBar.setValue(0);
              return;
            }

            m_progressBar.setValue(value * 100 / total);
          }
        });
  }

  /**
   * Changes the launcher progress bar text and pauses the thread for 5 seconds.
   *
   * @param text the text to change the progress bar text to
   */
  public void error(String text) {
    setStatus("Error: " + text);
    try {
      Thread.sleep(5000);
      System.exit(0);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void setStatus(final String text) {
    SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            m_progressBar.setString(text);
          }
        });
  }
}
