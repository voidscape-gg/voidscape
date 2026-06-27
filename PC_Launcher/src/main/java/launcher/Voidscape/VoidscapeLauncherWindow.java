package launcher.Voidscape;

import launcher.Utils.Utils;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

public class VoidscapeLauncherWindow extends JFrame {
  private static final Color VOID_PURPLE = new Color(126, 49, 220);
  private static final Color VOID_PURPLE_DARK = new Color(50, 20, 82);
  private static final Color PANEL_BG = new Color(13, 14, 18, 218);
  private static final Color PANEL_BG_DARK = new Color(8, 9, 13, 230);
  private static final Color BORDER = new Color(142, 118, 86, 150);
  private static final Color TEXT = new Color(230, 225, 214);
  private static final Color MUTED = new Color(169, 158, 145);
  private static final Color GOOD = new Color(88, 190, 105);
  private static final Color GOLD = new Color(247, 202, 93);
  private static final int HIT_PLAY = 1;
  private static final int HIT_STATUS = 2;
  private static final int HIT_ICON = 3;
  private static final int HIT_WINDOW = 4;
  private static final int PLAY_X = 218;
  private static final int PLAY_Y = 314;
  private static final int PLAY_W = 390;
  private static final int PLAY_H = 130;
  private static final int STATUS_X = 218;
  private static final int STATUS_Y = 458;
  private static final int STATUS_W = 390;
  private static final int STATUS_H = 56;
  private static final int ICONS_X = 544;
  private static final int ICONS_Y = 36;
  private static final int ICONS_W = 272;
  private static final int ICONS_H = 46;
  private static final int WINDOW_CONTROLS_X = 744;
  private static final int WINDOW_CONTROLS_Y = 9;
  private static final int WINDOW_CONTROLS_W = 70;
  private static final int WINDOW_CONTROLS_H = 24;

  private final VoidscapeUpdater updater;
  private final JProgressBar progressBar;
  private final JLabel statusLabel;
  private final JLabel endpointLabel;
  private final JButton playButton;
  private final JButton updateButton;
  private Timer smokeTimer;
  private Point dragStart;

  public VoidscapeLauncherWindow() {
    this.updater = new VoidscapeUpdater(VoidscapeLauncherConfig.cacheDir(), new VoidscapeUpdater.StatusListener() {
      @Override
      public void onStatus(String message, int progress, boolean busy) {
        updateStatus(message, progress, busy);
      }
    });
    this.progressBar = new VoidProgressBar();
    this.statusLabel = new ShadowLabel("Preparing launcher...", new Color(0, 0, 0, 190));
    this.endpointLabel = new ShadowLabel(VoidscapeLauncherConfig.endpointLabel(), new Color(0, 0, 0, 170));
    this.playButton = makeHitButton("Play", HIT_PLAY);
    this.updateButton = makeHitButton("Check for Updates", HIT_STATUS);
  }

  public void build() {
    setTitle(VoidscapeLauncherConfig.TITLE);
    setUndecorated(true);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(VoidscapeLauncherConfig.WINDOW_WIDTH, VoidscapeLauncherConfig.WINDOW_HEIGHT);
    setMinimumSize(new Dimension(VoidscapeLauncherConfig.WINDOW_WIDTH, VoidscapeLauncherConfig.WINDOW_HEIGHT));
    setResizable(false);

    Image icon = loadImage("icon.png");
    if (icon != null) {
      setIconImage(icon);
    }

    BackgroundPanel root = new BackgroundPanel();
    root.setLayout(null);
    addDragSupport(root);

    addWindowControls(root);
    addSkinHitTargets(root);
    addSkinText(root);

    setContentPane(root);
    setLocationRelativeTo(null);
    setVisible(true);
    toFront();
    requestFocus();
    updater.prepareAsync();
    maybeCaptureSmokeAndExit();
  }

  private void addWindowControls(JPanel root) {
    JButton minimize = makeHitButton("Minimize", HIT_WINDOW);
    minimize.setBounds(WINDOW_CONTROLS_X, WINDOW_CONTROLS_Y, WINDOW_CONTROLS_W / 2, WINDOW_CONTROLS_H);
    minimize.addActionListener(e -> setState(JFrame.ICONIFIED));
    root.add(minimize);

    JButton close = makeHitButton("Close", HIT_WINDOW);
    close.setBounds(WINDOW_CONTROLS_X + WINDOW_CONTROLS_W / 2, WINDOW_CONTROLS_Y, WINDOW_CONTROLS_W / 2, WINDOW_CONTROLS_H);
    close.addActionListener(e -> System.exit(0));
    root.add(close);
  }

  private void addSkinHitTargets(JPanel root) {
    playButton.setBounds(PLAY_X, PLAY_Y, PLAY_W, PLAY_H);
    playButton.addActionListener(e -> updater.launchClient(this));
    root.add(playButton);

    updateButton.setBounds(STATUS_X, STATUS_Y, STATUS_W, STATUS_H);
    updateButton.addActionListener(e -> updater.checkForUpdatesAsync());
    root.add(updateButton);

    JButton settings = makeHitButton("Settings", HIT_ICON);
    settings.setBounds(ICONS_X, ICONS_Y, ICONS_W / 5, ICONS_H);
    settings.addActionListener(e -> showSettings());
    root.add(settings);

    JButton repair = makeHitButton("Repair Cache", HIT_ICON);
    repair.setBounds(ICONS_X + ICONS_W / 5, ICONS_Y, ICONS_W / 5, ICONS_H);
    repair.addActionListener(e -> confirmRepair());
    root.add(repair);

    JButton website = makeHitButton("Website", HIT_ICON);
    website.setBounds(ICONS_X + (ICONS_W / 5) * 2, ICONS_Y, ICONS_W / 5, ICONS_H);
    website.addActionListener(e -> openUrl(VoidscapeLauncherConfig.websiteUrl(), "Website"));
    root.add(website);

    JButton discord = makeHitButton("Discord", HIT_ICON);
    discord.setBounds(ICONS_X + (ICONS_W / 5) * 3, ICONS_Y, ICONS_W / 5, ICONS_H);
    discord.addActionListener(e -> openUrl(VoidscapeLauncherConfig.discordUrl(), "Discord"));
    root.add(discord);

    JButton account = makeHitButton("Account", HIT_ICON);
    account.setBounds(ICONS_X + (ICONS_W / 5) * 4, ICONS_Y, ICONS_W / 5, ICONS_H);
    account.addActionListener(e -> openUrl(VoidscapeLauncherConfig.portalAccountUrl(), "Account portal"));
    root.add(account);
  }

  private void addSkinText(JPanel root) {
    statusLabel.setFont(displayFont(Font.BOLD, 10));
    statusLabel.setForeground(new Color(224, 215, 194));
    statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
    statusLabel.setBounds(STATUS_X + 18, STATUS_Y + 21, 230, 15);
    root.add(statusLabel);

    endpointLabel.setFont(displayFont(Font.PLAIN, 9));
    endpointLabel.setForeground(new Color(178, 161, 134));
    endpointLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    endpointLabel.setBounds(STATUS_X + STATUS_W - 142, STATUS_Y + 21, 124, 15);
    root.add(endpointLabel);

    progressBar.setBounds(STATUS_X + 18, STATUS_Y + 39, STATUS_W - 36, 8);
    progressBar.setValue(0);
    root.add(progressBar);
  }

  private void addActionRail(JPanel root) {
    GlassPanel rail = new GlassPanel(new Color(7, 8, 12, 170), new Color(160, 129, 90, 110));
    rail.setLayout(null);
    rail.setBounds(32, 128, 118, 286);
    root.add(rail);

    JButton account = makeRailButton("Account");
    account.setBounds(14, 16, 90, 38);
    account.addActionListener(e -> openUrl(VoidscapeLauncherConfig.portalAccountUrl(), "Account portal"));
    rail.add(account);

    JButton website = makeRailButton("Website");
    website.setBounds(14, 62, 90, 38);
    website.addActionListener(e -> openUrl(VoidscapeLauncherConfig.websiteUrl(), "Website"));
    rail.add(website);

    JButton discord = makeRailButton("Discord");
    discord.setBounds(14, 108, 90, 38);
    discord.addActionListener(e -> openUrl(VoidscapeLauncherConfig.discordUrl(), "Discord"));
    rail.add(discord);

    JButton settings = makeRailButton("Settings");
    settings.setBounds(14, 154, 90, 38);
    settings.addActionListener(e -> showSettings());
    rail.add(settings);

    JButton repair = makeRailButton("Repair");
    repair.setBounds(14, 200, 90, 38);
    repair.addActionListener(e -> confirmRepair());
    rail.add(repair);
  }

  private void addPlayCard(JPanel root) {
    GlassPanel card = new GlassPanel(PANEL_BG, BORDER);
    card.setLayout(null);
    card.setBounds(188, 308, 350, 156);
    root.add(card);

    JLabel title = label("Voidscape", 18, Font.BOLD, TEXT);
    title.setBounds(22, 16, 190, 24);
    card.add(title);

    JLabel subtitle = label("Classic roots. Void-touched future.", 12, Font.PLAIN, MUTED);
    subtitle.setBounds(22, 42, 260, 20);
    card.add(subtitle);

    playButton.setBounds(22, 76, 200, 56);
    playButton.addActionListener(e -> updater.launchClient(this));
    card.add(playButton);

    updateButton.setBounds(234, 76, 92, 56);
    updateButton.addActionListener(e -> updater.checkForUpdatesAsync());
    card.add(updateButton);
  }

  private void addStatusCard(JPanel root) {
    GlassPanel card = new GlassPanel(PANEL_BG_DARK, new Color(112, 92, 70, 120));
    card.setLayout(null);
    card.setBounds(188, 472, 586, 72);
    root.add(card);

    JLabel statusDot = new JLabel();
    statusDot.setOpaque(true);
    statusDot.setBackground(GOOD);
    statusDot.setBounds(22, 18, 12, 12);
    card.add(statusDot);

    statusLabel.setFont(new Font("Serif", Font.BOLD, 14));
    statusLabel.setForeground(TEXT);
    statusLabel.setBounds(42, 12, 320, 24);
    card.add(statusLabel);

    endpointLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
    endpointLabel.setForeground(MUTED);
    endpointLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    endpointLabel.setBounds(392, 12, 160, 24);
    card.add(endpointLabel);

    progressBar.setBounds(22, 42, 530, 12);
    progressBar.setValue(0);
    card.add(progressBar);
  }

  private void addNewsCard(JPanel root) {
    GlassPanel card = new GlassPanel(PANEL_BG, BORDER);
    card.setLayout(null);
    card.setBounds(560, 92, 214, 286);
    root.add(card);

    JLabel title = label("Latest News", 17, Font.BOLD, TEXT);
    title.setBounds(18, 18, 160, 26);
    card.add(title);

    addNewsRow(card, 58, "Launcher revamp", "Modern updater shell in progress.");
    addNewsRow(card, 112, "Founder rewards", "Early access grants a free sub card.");
    addNewsRow(card, 166, "Void Knight", "Solo instance tuning is live.");
    addNewsRow(card, 220, "Milestones", "Titles and world broadcasts added.");
  }

  private void addPremiumCard(JPanel root) {
    GlassPanel card = new GlassPanel(new Color(19, 14, 24, 220), new Color(190, 151, 81, 150));
    card.setLayout(null);
    card.setBounds(560, 388, 214, 76);
    root.add(card);

    JLabel title = label("Subscription", 16, Font.BOLD, new Color(239, 206, 126));
    title.setBounds(18, 12, 160, 22);
    card.add(title);

    JLabel copy = label("Weekly boost card available in-game.", 11, Font.PLAIN, MUTED);
    copy.setBounds(18, 36, 176, 18);
    card.add(copy);
  }

  private void addNewsRow(JPanel card, int y, String headline, String detail) {
    JLabel h = label(headline, 13, Font.BOLD, TEXT);
    h.setBounds(18, y, 176, 18);
    card.add(h);

    JLabel d = label(detail, 10, Font.PLAIN, MUTED);
    d.setBounds(18, y + 18, 176, 18);
    card.add(d);

    JPanel line = new JPanel();
    line.setBackground(new Color(126, 49, 220, 70));
    line.setBounds(18, y + 42, 176, 1);
    card.add(line);
  }

  private JLabel label(String text, int size, int style, Color color) {
    JLabel label = new JLabel(text);
    label.setFont(displayFont(style, size));
    label.setForeground(color);
    return label;
  }

  private Font displayFont(int style, int size) {
    String[] preferred = new String[] {"Palatino", "Georgia", "Times New Roman", "Serif"};
    for (String name : preferred) {
      Font font = new Font(name, style, size);
      if (font.getFamily() != null && font.getFamily().length() > 0) {
        return font;
      }
    }
    return new Font("Serif", style, size);
  }

  private JButton makeTopButton(String text) {
    JButton button = makeButton(text, false);
    button.setFont(new Font("SansSerif", Font.BOLD, 13));
    button.setMargin(new java.awt.Insets(0, 0, 1, 0));
    return button;
  }

  private JButton makeRailButton(String text) {
    JButton button = makeButton(text, false);
    button.setFont(new Font("Serif", Font.BOLD, 13));
    return button;
  }

  private JButton makeButton(String text, boolean primary) {
    JButton button = new JButton(text);
    button.setFocusPainted(false);
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.setForeground(primary ? Color.WHITE : TEXT);
    button.setBackground(primary ? VOID_PURPLE : new Color(22, 20, 24));
    button.setBorder(BorderFactory.createLineBorder(primary ? new Color(218, 180, 102) : new Color(92, 76, 60), 1));
    button.setFont(new Font("Serif", primary ? Font.BOLD : Font.PLAIN, primary ? 28 : 11));
    button.setOpaque(true);

    Color normal = button.getBackground();
    Color hover = primary ? new Color(150, 72, 235) : new Color(37, 31, 43);
    button.addMouseListener(new HoverPainter(button, normal, hover));
    return button;
  }

  private JButton makeHitButton(String tooltip, int style) {
    return new LayeredHitButton(tooltip, style);
  }

  private void confirmRepair() {
    int response = JOptionPane.showConfirmDialog(this,
        "Repair the local client cache?\n\nThis keeps saved credentials and account files, but refreshes launcher-managed files.",
        VoidscapeLauncherConfig.TITLE,
        JOptionPane.YES_NO_OPTION,
        JOptionPane.QUESTION_MESSAGE);
    if (response == JOptionPane.YES_OPTION) {
      updater.repairAsync();
    }
  }

  private void showSettings() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(new EmptyBorder(8, 8, 8, 8));
    panel.add(settingsLine("Cache", updater.getCacheDir().getAbsolutePath()));
    panel.add(settingsLine("Endpoint", VoidscapeLauncherConfig.endpointLabel()));
    panel.add(settingsLine("Manifest", blankLabel(VoidscapeLauncherConfig.manifestUrl())));
    panel.add(settingsLine("Portal", VoidscapeLauncherConfig.portalUrl()));

    int response = JOptionPane.showOptionDialog(this,
        panel,
        "Voidscape Settings",
        JOptionPane.DEFAULT_OPTION,
        JOptionPane.PLAIN_MESSAGE,
        null,
        new Object[] {"Open Cache", "Close"},
        "Close");
    if (response == 0) {
      openCacheFolder();
    }
  }

  private JPanel settingsLine(String key, String value) {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBorder(new EmptyBorder(3, 0, 8, 0));

    JLabel k = new JLabel(key);
    k.setFont(new Font("SansSerif", Font.BOLD, 12));
    panel.add(k);

    JLabel v = new JLabel(value);
    v.setFont(new Font("SansSerif", Font.PLAIN, 11));
    panel.add(v);
    return panel;
  }

  private String blankLabel(String value) {
    if (value == null || value.trim().length() == 0) {
      return "Not configured";
    }
    return value;
  }

  private void openCacheFolder() {
    try {
      File dir = updater.getCacheDir();
      if (!dir.exists()) {
        dir.mkdirs();
      }
      Desktop.getDesktop().open(dir);
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Could not open cache folder:\n" + e.getMessage());
    }
  }

  private void openUrl(String url, String label) {
    if (url == null || url.trim().length() == 0) {
      JOptionPane.showMessageDialog(this, label + " is not configured yet.");
      return;
    }
    Utils.openWebpage(url);
  }

  private void maybeCaptureSmokeAndExit() {
    String output = System.getProperty("voidscape.launcher.smoke.out", "").trim();
    boolean enabled = Boolean.getBoolean("voidscape.launcher.smoke") || output.length() > 0;
    if (!enabled) {
      return;
    }

    int delayMs = parseIntProperty("voidscape.launcher.smoke.delayMs", 2200);
    smokeTimer = new Timer(delayMs, e -> {
      try {
        captureSmokeScreenshot(output);
      } catch (Exception ex) {
        System.err.println("VOIDSCAPE_LAUNCHER_SMOKE_ERROR " + ex.getMessage());
      }
      if (!"false".equalsIgnoreCase(System.getProperty("voidscape.launcher.smoke.exit", "true"))) {
        System.exit(0);
      }
    });
    smokeTimer.setRepeats(false);
    smokeTimer.start();
  }

  private int parseIntProperty(String key, int fallback) {
    String value = System.getProperty(key);
    if (value == null || value.trim().length() == 0) {
      return fallback;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private void captureSmokeScreenshot(String output) throws Exception {
    File file = output == null || output.trim().length() == 0
        ? new File("voidscape-launcher-smoke.png")
        : new File(output.trim());
    file = file.getAbsoluteFile();
    File parent = file.getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
      throw new Exception("Could not create screenshot folder: " + parent.getAbsolutePath());
    }

    BufferedImage image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      paintAll(graphics);
    } finally {
      graphics.dispose();
    }
    ImageIO.write(image, "png", file);
    System.out.println("VOIDSCAPE_LAUNCHER_SMOKE_SCREENSHOT " + file.getAbsolutePath());
  }

  private void updateStatus(final String message, final int progress, final boolean busy) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        statusLabel.setText(message);
        if (progress < 0) {
          progressBar.setIndeterminate(true);
        } else {
          progressBar.setIndeterminate(false);
          progressBar.setValue(progress);
        }
        playButton.setEnabled(!busy);
        updateButton.setEnabled(!busy);
      }
    });
  }

  private void addDragSupport(JPanel panel) {
    MouseAdapter adapter = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        dragStart = e.getPoint();
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        if (dragStart == null) {
          return;
        }
        Point screen = e.getLocationOnScreen();
        setLocation(screen.x - dragStart.x, screen.y - dragStart.y);
      }
    };
    panel.addMouseListener(adapter);
    panel.addMouseMotionListener(adapter);
  }

  private static Image loadImage(String name) {
    URL url = VoidscapeLauncherWindow.class.getResource("/data/images/voidscape/" + name);
    if (url == null) {
      return null;
    }
    return new ImageIcon(url).getImage();
  }

  private static Image loadImageFromFile(File file) {
    if (file == null || !file.exists() || !file.isFile()) {
      return null;
    }
    return new ImageIcon(file.getAbsolutePath()).getImage();
  }

  private static Image loadAnimatedBackground() {
    String configured = System.getProperty("voidscape.backgroundAnimation");
    if (configured == null || configured.trim().length() == 0) {
      configured = System.getenv("VOIDSCAPE_BACKGROUND_ANIMATION");
    }
    Image external = loadImageFromFile(configured == null ? null : new File(configured.trim()));
    if (external != null) {
      return external;
    }

    File cacheOverride = new File(VoidscapeLauncherConfig.cacheDir(),
        "launcher" + File.separator + "background.gif");
    external = loadImageFromFile(cacheOverride);
    if (external != null) {
      return external;
    }

    Image bundledGif = loadImage("layered/background.gif");
    if (bundledGif != null) {
      return bundledGif;
    }
    return loadImage("layered/background.png");
  }

  private static class BackgroundPanel extends JPanel {
    private final Image background = loadAnimatedBackground();
    private final Image logo = loadImage("layered/logo.png");
    private final Image topIcons = loadImage("layered/top-icons.png");
    private final Image windowControls = loadImage("layered/window-controls.png");
    private final Timer repaintTimer;

    BackgroundPanel() {
      setOpaque(true);
      setBackground(Color.BLACK);
      repaintTimer = new Timer(33, e -> repaint());
      repaintTimer.setRepeats(true);
      repaintTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      drawCover(g2, background, 0, 0, getWidth(), getHeight());
      g2.setColor(new Color(0, 0, 0, 38));
      g2.fillRect(0, 0, getWidth(), getHeight());
      g2.drawImage(logo, 24, 30, 430, 113, null);
      g2.drawImage(topIcons, ICONS_X, ICONS_Y, ICONS_W, ICONS_H, null);
      g2.drawImage(windowControls, WINDOW_CONTROLS_X, WINDOW_CONTROLS_Y, WINDOW_CONTROLS_W, WINDOW_CONTROLS_H, null);
      drawUpdaterPanel(g2);
      g2.dispose();
    }

    private void drawUpdaterPanel(Graphics2D g2) {
      g2.setColor(new Color(5, 4, 9, 210));
      g2.fillRoundRect(STATUS_X, STATUS_Y, STATUS_W, STATUS_H, 12, 12);
      g2.setColor(new Color(196, 154, 79, 150));
      g2.setStroke(new BasicStroke(1.3f));
      g2.drawRoundRect(STATUS_X, STATUS_Y, STATUS_W - 1, STATUS_H - 1, 12, 12);

      g2.setColor(new Color(124, 50, 212, 95));
      g2.drawLine(STATUS_X + 14, STATUS_Y + 18, STATUS_X + STATUS_W - 14, STATUS_Y + 18);
      g2.setFont(new Font("Serif", Font.BOLD, 10));
      g2.setColor(new Color(230, 202, 145));
      g2.drawString("UPDATER STATUS", STATUS_X + 18, STATUS_Y + 14);

      g2.setColor(new Color(38, 25, 61, 180));
      g2.fillRoundRect(STATUS_X + 14, STATUS_Y + 36, STATUS_W - 28, 18, 9, 9);
    }

    private void drawCover(Graphics2D g2, Image image, int x, int y, int width, int height) {
      if (image == null) {
        return;
      }
      int imageWidth = image.getWidth(this);
      int imageHeight = image.getHeight(this);
      if (imageWidth <= 0 || imageHeight <= 0) {
        g2.drawImage(image, x, y, width, height, this);
        return;
      }

      double scale = Math.max((double) width / imageWidth, (double) height / imageHeight);
      int drawWidth = (int) Math.round(imageWidth * scale);
      int drawHeight = (int) Math.round(imageHeight * scale);
      int drawX = x + (width - drawWidth) / 2;
      int drawY = y + (height - drawHeight) / 2;
      g2.drawImage(image, drawX, drawY, drawWidth, drawHeight, this);
    }
  }

  private static class ShadowLabel extends JLabel {
    private final Color shadow;

    ShadowLabel(String text, Color shadow) {
      super(text);
      this.shadow = shadow;
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setFont(getFont());

      String text = getText();
      if (text == null || text.length() == 0) {
        g2.dispose();
        return;
      }

      int textWidth = g2.getFontMetrics().stringWidth(text);
      int x = 0;
      if (getHorizontalAlignment() == SwingConstants.RIGHT) {
        x = getWidth() - textWidth;
      } else if (getHorizontalAlignment() == SwingConstants.CENTER) {
        x = (getWidth() - textWidth) / 2;
      }
      int y = (getHeight() - g2.getFontMetrics().getHeight()) / 2 + g2.getFontMetrics().getAscent();

      g2.setColor(shadow);
      g2.drawString(text, x + 1, y + 1);
      g2.setColor(getForeground());
      g2.drawString(text, x, y);
      g2.dispose();
    }
  }

  private static class GlassPanel extends JPanel {
    private final Color fill;
    private final Color border;

    GlassPanel(Color fill, Color border) {
      this.fill = fill;
      this.border = border;
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(fill);
      g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
      g2.setColor(border);
      g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
      g2.dispose();
      super.paintComponent(g);
    }
  }

  private static class HoverPainter extends MouseAdapter {
    private final JButton button;
    private final Color normal;
    private final Color hover;

    HoverPainter(JButton button, Color normal, Color hover) {
      this.button = button;
      this.normal = normal;
      this.hover = hover;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      button.setBackground(hover);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      button.setBackground(normal);
    }
  }

  private static class LayeredHitButton extends JButton {
    private final int style;
    private final Image playNormal;
    private final Image playHover;
    private final Image playPressed;
    private final Image playActive;
    private boolean hovered;
    private boolean pressed;
    private long pulseStartedAt;
    private Timer pulseTimer;

    LayeredHitButton(String tooltip, int style) {
      this.style = style;
      this.playNormal = style == HIT_PLAY ? loadImage("layered/play-button-normal.png") : null;
      this.playHover = style == HIT_PLAY ? loadImage("layered/play-button-hover.png") : null;
      this.playPressed = style == HIT_PLAY ? loadImage("layered/play-button-pressed.png") : null;
      this.playActive = style == HIT_PLAY ? loadImage("layered/play-button-active.png") : null;
      setToolTipText(tooltip);
      setFocusPainted(false);
      setBorderPainted(false);
      setContentAreaFilled(false);
      setOpaque(false);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          hovered = true;
          repaint();
        }

        @Override
        public void mouseExited(MouseEvent e) {
          hovered = false;
          pressed = false;
          repaint();
        }

        @Override
        public void mousePressed(MouseEvent e) {
          pressed = true;
          repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          pressed = false;
          startPulse();
          repaint();
        }
      });
    }

    private void startPulse() {
      pulseStartedAt = System.currentTimeMillis();
      if (pulseTimer != null && pulseTimer.isRunning()) {
        pulseTimer.stop();
      }
      pulseTimer = new Timer(16, e -> {
        if (System.currentTimeMillis() - pulseStartedAt > 220) {
          pulseTimer.stop();
        }
        repaint();
      });
      pulseTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

      if (style == HIT_PLAY) {
        paintPlayButton(g2);
        g2.dispose();
        return;
      }

      if (!isEnabled()) {
        g2.dispose();
        return;
      }

      int pad = style == HIT_PLAY ? 12 : style == HIT_STATUS ? 8 : 3;
      int radius = style == HIT_PLAY ? 34 : style == HIT_STATUS ? 15 : 7;
      int yOffset = pressed ? (style == HIT_PLAY ? 4 : 2) : 0;

      if (pressed) {
        g2.setColor(new Color(0, 0, 0, style == HIT_PLAY ? 80 : 55));
        g2.fillRoundRect(pad, pad + yOffset, getWidth() - pad * 2, getHeight() - pad * 2, radius, radius);
      }

      if (style != HIT_ICON) {
        g2.dispose();
        return;
      }

      if (hovered || pressed) {
        float pulse = (float) ((Math.sin(System.currentTimeMillis() / 120.0) + 1.0) * 0.5);
        int alpha = pressed ? 230 : 145 + (int) (55 * pulse);
        int stroke = 2;
        g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), alpha / 3));
        g2.setStroke(new BasicStroke(stroke + 5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawRoundRect(pad, pad + yOffset, getWidth() - pad * 2 - 1, getHeight() - pad * 2 - 1, radius, radius);

        g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), alpha));
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawRoundRect(pad, pad + yOffset, getWidth() - pad * 2 - 1, getHeight() - pad * 2 - 1, radius, radius);
      }

      float clickPulse = clickPulse();
      if (clickPulse > 0f) {
        int alpha = (int) (170 * clickPulse);
        int grow = (int) ((1f - clickPulse) * 10);
        g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), alpha));
        g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawRoundRect(pad - grow, pad - grow, getWidth() - (pad - grow) * 2 - 1,
            getHeight() - (pad - grow) * 2 - 1, radius + grow, radius + grow);
      }

      g2.dispose();
    }

    private void paintPlayButton(Graphics2D g2) {
      Image image = choosePlayImage();
      if (image != null) {
        g2.drawImage(image, 0, 0, getWidth(), getHeight(), this);
      }
      if (!isEnabled()) {
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fillRoundRect(14, 14, getWidth() - 28, getHeight() - 28, 32, 32);
      }
    }

    private Image choosePlayImage() {
      if (!isEnabled()) {
        return fallback(playNormal, playActive);
      }
      if (pressed) {
        return fallback(playPressed, playNormal);
      }
      if (clickPulse() > 0f) {
        return fallback(playActive, playHover);
      }
      if (hovered) {
        return fallback(playHover, playNormal);
      }
      return fallback(playNormal, playHover);
    }

    private Image fallback(Image preferred, Image fallback) {
      return preferred != null ? preferred : fallback;
    }

    private float clickPulse() {
      if (pulseStartedAt <= 0) {
        return 0f;
      }
      long elapsed = System.currentTimeMillis() - pulseStartedAt;
      if (elapsed >= 220) {
        return 0f;
      }
      return 1f - (elapsed / 220f);
    }
  }

  private static class VoidProgressBar extends JProgressBar {
    VoidProgressBar() {
      super(0, 100);
      setOpaque(false);
      setBorderPainted(false);
      setStringPainted(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int width = getWidth();
      int height = getHeight();
      g2.setColor(new Color(28, 24, 34));
      g2.fillRoundRect(0, 0, width, height, height, height);

      int fillWidth;
      if (isIndeterminate()) {
        fillWidth = Math.max(width / 3, 24);
      } else {
        fillWidth = Math.max(0, Math.min(width, width * getValue() / Math.max(1, getMaximum())));
      }

      if (fillWidth > 0) {
        GradientPaint fill = new GradientPaint(
            0, 0, new Color(99, 38, 184),
            fillWidth, 0, new Color(172, 75, 238));
        g2.setPaint(fill);
        g2.fillRoundRect(0, 0, fillWidth, height, height, height);
      }

      g2.setColor(new Color(214, 168, 91, 85));
      g2.drawRoundRect(0, 0, width - 1, height - 1, height, height);
      g2.dispose();
    }
  }
}
