package orsc;

import orsc.graphics.gui.UiSkin;
import orsc.util.Utils;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.*;

import static orsc.OpenRSC.applet;
import static orsc.OpenRSC.jframe;

/**
 * This class is responsible for rendering all output from the applet onto the screen, which it
 * receives via a {@link BufferedImage} from the {@link ORSCApplet#draw()} method.
 * All window interactions are then forwarded to the applet within {@link OpenRSC}.
 * <p>
 * Code adapted from <a href="https://github.com/RSCPlus/rscplus">RSCPlus</a>
 */
public class ScaledWindow extends JFrame implements WindowListener, FocusListener, ComponentListener,
	MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

	private static ScaledWindow instance = null;
	private static boolean initialRender = true;
	private static int javaVersion = 0;
	private static boolean isMacOS = false;
	private static boolean shouldRealign = false;
	private static final int[][] VIEWPORT_PRESETS = new int[][]{
		{640, 480}, {720, 540}, {800, 600}, {896, 672}, {1024, 768}, {512, 346}
	};
	private static final String[] VIEWPORT_PRESET_LABELS = new String[]{
		"Small", "Medium", "Large", "XL", "Huge", "Classic"
	};
	private static int viewportPresetIndex = 0;
	// Voidscape: base render resolution. Uses a compact native buffer so the world does not turn into
	// a zoomed-out 1024x768 postcard, while Settings can cycle larger 4:3 native presets when wanted.
	private static int baseViewportWidth = VIEWPORT_PRESETS[viewportPresetIndex][0];
	private static int baseViewportHeight = VIEWPORT_PRESETS[viewportPresetIndex][1];
	// Voidscape: keep the startup window at native scale so the pixel-art HUD stays crisp; the buffer
	// itself changes size via native presets instead of downscaling one fixed large image.
	private static final float MIN_WINDOW_SCALE = 1.0f;
	private static final float MAX_INTEGER_SCALE = 6.0f;
	private static final float MAX_INTERPOLATION_SCALE = 4.0f;
	private int frameWidth = 0;
	private int frameHeight = 0;
	private ScaledViewport scaledViewport;
	private int viewportWidth = 0;
	private int viewportHeight = 0;
	private final BufferedImage[] viewportFrameBuffers = new BufferedImage[]{
		new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB),
		new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
	};
	private int viewportFrameBufferIndex = 0;
	private int previousViewportBufferWidth;
	private int previousViewportBufferHeight;
	private int previousViewportBufferType = BufferedImage.TYPE_INT_RGB;
	private int scaledDrawX = 0;
	private int scaledDrawY = 0;
	private int scaledDrawWidth = baseViewportWidth;
	private int scaledDrawHeight = baseViewportHeight;

	/** Private constructor to ensure singleton nature */
	private ScaledWindow() {
		try {
			// Set System L&F as the default
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (UnsupportedLookAndFeelException e) {
			System.out.println("Unable to set L&F: Unsupported look and feel");
		} catch (ClassNotFoundException e) {
			System.out.println("Unable to set L&F: Class not found");
		} catch (InstantiationException e) {
			System.out.println("Unable to set L&F: Class object cannot be instantiated");
		} catch (IllegalAccessException e) {
			System.out.println("Unable to set L&F: Illegal access exception");
		}

		System.out.println("Creating scaled window");

		/* Initialize the contents of the frame. */
		try {
			SwingUtilities.invokeAndWait(() -> {
				javaVersion = Utils.getJavaVersion();

				runInit();
			});
		} catch (InvocationTargetException e) {
			System.out.println("There was a thread-related error while setting up the scaled window!");
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.out.println(
				"There was a thread-related error while setting up the scaled window! The window may not be initialized properly!");
			e.printStackTrace();
		}
	}

	private void runInit() {
		// Set window properties
		setBackground(Color.black);
		setFocusTraversalKeysEnabled(false);

		// Add window listeners
		addWindowListener(this);
		addComponentListener(this);
		addFocusListener(this);
		addKeyListener(this);

		// Enable macOS fullscreen button, if possible
		isMacOS = Utils.isMacOS();

		if (isMacOS) {
			try {
				Class util = Class.forName("com.apple.eawt.FullScreenUtilities");
				Class params[] = new Class[] {Window.class, Boolean.TYPE};
				@SuppressWarnings("unchecked")
				Method method = util.getMethod("setWindowCanFullScreen", params);
				method.invoke(util, this, true);
			} catch (Exception ignored) {
			}
		}

		// Set minimum size to the smallest allowed native viewport size.
		setMinimumSize(new Dimension(minViewportWidth(), minViewportHeight()));

		// Default icon, will be overridden later
		setIconImage(Utils.getImage("icon.png").getImage());

		// Initialize scaled view
		scaledViewport = new ScaledViewport();

		scaledViewport.addMouseListener(this);
		scaledViewport.addMouseMotionListener(this);
		scaledViewport.addMouseWheelListener(this);

		scaledViewport.setSize(getSize());
		scaledViewport.setBackground(Color.black);
		scaledViewport.revalidate();
		scaledViewport.repaint();
		scaledViewport.setVisible(true);

		add(scaledViewport);

		pack();
		revalidate();
		repaint();

		refreshAvailableScalars();
	}

	private void refreshAvailableScalars() {
		Dimension maxEffectiveWindowSize = getMaximumEffectiveWindowSize();
		int maxIntegerScalar = largestIntegerScalarThatFits(maxEffectiveWindowSize);

		List<Float> integerScalars = new ArrayList<>();
		for (float i = 1.0f; i <= maxIntegerScalar && i <= MAX_INTEGER_SCALE; i++) {
			integerScalars.add(i);
		}

		mudclient.integerScalars = integerScalars;

		List<Float> interpolationScalars = new ArrayList<>();
		for (float i = 1.0f; i <= maxIntegerScalar && i <= MAX_INTERPOLATION_SCALE; i += 0.5f) {
			interpolationScalars.add(i);
		}

		mudclient.interpolationScalars = interpolationScalars;
	}

	private static int minViewportWidth() {
		return Math.round(baseViewportWidth * MIN_WINDOW_SCALE);
	}

	private static int minViewportHeight() {
		return Math.round(baseViewportHeight * MIN_WINDOW_SCALE);
	}

	public static int getBaseViewportWidth() {
		return baseViewportWidth;
	}

	public static int getBaseViewportHeight() {
		return baseViewportHeight;
	}

	public static int getViewportPresetCount() {
		return VIEWPORT_PRESETS.length;
	}

	public static int getViewportPresetIndex() {
		return viewportPresetIndex;
	}

	public static void setViewportPresetIndex(int index) {
		viewportPresetIndex = normalizeViewportPresetIndex(index);
		baseViewportWidth = VIEWPORT_PRESETS[viewportPresetIndex][0];
		baseViewportHeight = VIEWPORT_PRESETS[viewportPresetIndex][1];
	}

	private static int normalizeViewportPresetIndex(int index) {
		if (index < 0) {
			return 0;
		}
		if (index >= VIEWPORT_PRESETS.length) {
			return VIEWPORT_PRESETS.length - 1;
		}
		return index;
	}

	public static String getViewportPresetLabel() {
		return getViewportPresetLabel(viewportPresetIndex);
	}

	public static String getViewportPresetLabel(int index) {
		int preset = normalizeViewportPresetIndex(index);
		return VIEWPORT_PRESET_LABELS[preset] + " "
			+ VIEWPORT_PRESETS[preset][0] + "x" + VIEWPORT_PRESETS[preset][1];
	}

	public void applyViewportPreset(int index) {
		setViewportPresetIndex(index);
		mudclient.windowScaleMode = true;
		mudclient.renderingScalar = 1.0f;
		mudclient.newRenderingScalar = 1.0f;
		ORSCApplet.oldRenderingScalar = 1.0f;
		refreshAvailableScalars();
		SwingUtilities.invokeLater(() -> {
			resizeWindowToDefaultSize();
			revalidate();
			repaint();
		});
	}

	/**
	 * Voidscape: first run only (no persisted window settings) — pick the
	 * largest viewport preset whose window fits the usable screen, so large
	 * displays don't boot into the 640x480-class default. Uses the same fit
	 * math as {@link #largestIntegerScalarThatFits}: preset dimensions compared
	 * against {@link #getMaximumEffectiveWindowSize()}, which already accounts
	 * for window insets. Persisted settings always win — the caller must only
	 * invoke this when clientSettings.conf carried no viewport_preset and no
	 * scaling_scalar.
	 */
	public void applyFirstRunViewportPreset() {
		Dimension maxEffectiveWindowSize = getMaximumEffectiveWindowSize();
		int bestIndex = -1;
		long bestArea = -1L;
		for (int i = 0; i < VIEWPORT_PRESETS.length; i++) {
			int width = VIEWPORT_PRESETS[i][0];
			int height = VIEWPORT_PRESETS[i][1];
			if (width <= maxEffectiveWindowSize.width && height <= maxEffectiveWindowSize.height) {
				long area = (long) width * height;
				if (area > bestArea) {
					bestArea = area;
					bestIndex = i;
				}
			}
		}

		if (bestIndex >= 0 && bestIndex != viewportPresetIndex) {
			setViewportPresetIndex(bestIndex);
			setMinimumSize(new Dimension(minViewportWidth(), minViewportHeight()));
			refreshAvailableScalars();
			System.out.println("Voidscape first run: viewport preset " + getViewportPresetLabel());
		}
	}

	public void applyStartupScalingDefaults(boolean hasSavedScalingScalar) {
		if (hasSavedScalingScalar) {
			mudclient.windowScaleMode = false;
			mudclient.newRenderingScalar = clampScalarToAvailableScalars(mudclient.scalingType, mudclient.newRenderingScalar);
			return;
		}

		mudclient.windowScaleMode = true;
		mudclient.renderingScalar = 1.0f;
		mudclient.newRenderingScalar = 1.0f;
		ORSCApplet.oldRenderingScalar = 1.0f;
		System.out.println("Voidscape UI scale: default window size; drag the window to scale the UI");
	}

	private int largestIntegerScalarThatFits(Dimension maxEffectiveWindowSize) {
		int maxScalar = Math.max(1, (int) MAX_INTEGER_SCALE);
		for (int i = maxScalar; i >= 1; i--) {
			int width = baseViewportWidth * i;
			int height = baseViewportHeight * i;

			if (width <= maxEffectiveWindowSize.width && height <= maxEffectiveWindowSize.height) {
				return i;
			}
		}
		return 1;
	}

	private float clampScalarToAvailableScalars(ScalingAlgorithm scalingAlgorithm, float requestedScalar) {
		List<Float> scalars = scalingAlgorithm == ScalingAlgorithm.INTEGER_SCALING
			? mudclient.integerScalars
			: mudclient.interpolationScalars;
		if (scalars == null || scalars.isEmpty()) {
			return 1.0f;
		}

		float fallback = scalars.get(0);
		for (Float scalar : scalars) {
			if (Math.abs(scalar - requestedScalar) < 0.001f) {
				return scalar;
			}
			if (scalar <= requestedScalar) {
				fallback = scalar;
			}
		}
		return fallback;
	}

	/**
	 * Keep track of frame dimensions internally to avoid possible thread-safety issues when needing
	 * to invoke a method that uses the frame size, immediately after setting it.
	 *
	 * <p>NOTE: Must <i>always</i> call setMinimumSize before invoking this method
	 */
	@Override
	public void setSize(int width, int height) {
		super.setSize(width, height);

		frameWidth = width;
		frameHeight = height;
	}

	/** Sets a flag to align the window after resizing the applet */
	public void setWindowRealignmentIntent(boolean flag) {
		shouldRealign = flag;
	}

	/**
	 * Centers the window or pins it to the top of the screen, if the custom size exactly matches the
	 * available space.
	 */
	private void alignWindow() {
		Rectangle currentScreenBounds = getGraphicsConfiguration().getDevice().getDefaultConfiguration().getBounds();

		int x = ((currentScreenBounds.width - frameWidth) / 2) + currentScreenBounds.x;
		int y = ((currentScreenBounds.height - frameHeight) / 2) + currentScreenBounds.y;

		// Set the window location
		setLocation(x, y);
	}

	/**
	 * Used to determine the user's maximum effective window size, taking the window's insets into
	 * consideration.
	 */
	public Dimension getMaximumEffectiveWindowSize() {
		Dimension maximumWindowSize = getMaximumWindowSize();

		// Subtract
		int windowWidth = maximumWindowSize.width - getWindowWidthInsets();
		int windowHeight = maximumWindowSize.height - getWindowHeightInsets();

		if (Utils.isModernWindowsOS()) {
			windowWidth += 16;
			windowHeight += 8;
		}

		return new Dimension(windowWidth, windowHeight);
	}

	/** Used to determine the user's maximum window size */
	public Dimension getMaximumWindowSize() {
		GraphicsConfiguration graphicsConfiguration = getGraphicsConfiguration().getDevice().getDefaultConfiguration();
		Rectangle screenBounds = graphicsConfiguration.getBounds();
		Insets screenInsets = getToolkit().getScreenInsets(graphicsConfiguration);

		// Subtract the operating system insets from the current display's max bounds
		int maxWidth = screenBounds.width - screenInsets.left - screenInsets.right;
		int maxHeight = screenBounds.height - screenInsets.top - screenInsets.bottom;

		return new Dimension(maxWidth, maxHeight);
	}

	/** Opens the window */
	public void launchScaledWindow() {
		setLocationRelativeTo(null);
		setVisible(true);
	}

	/**
	 * Sets the {@link BufferedImage} that the window should display,
	 * from {@link ORSCApplet#draw()}
	 */
	public void setGameImage(BufferedImage gameImage) {
		long pacingStartNs = FramePacingMonitor.now();
		if (gameImage == null) {
			return;
		}

		viewportWidth = gameImage.getWidth();
		viewportHeight = gameImage.getHeight();

		if (scaledViewport.isViewportImageLoaded()) {
			if (initialRender) {
				// Set the window size for the scalar (will be realigned in the method)
				if (mudclient.windowScaleMode) {
					resizeWindowToDefaultSize();
				} else {
					resizeWindowToScalar();
				}
				initialRender = false;
			}
		}

		if (mudclient.windowScaleMode) {
			updateWindowScaleFromViewport();
		} else {
			updateFixedScaleDrawBounds();
		}

		scaledViewport.setViewportImage(copyViewportFrame(gameImage));
		scaledViewport.repaint();
		FramePacingMonitor.recordDuration("desktop.setGameImage", pacingStartNs, FramePacingMonitor.now());
	}

	private BufferedImage copyViewportFrame(BufferedImage gameImage) {
		int width = gameImage.getWidth();
		int height = gameImage.getHeight();
		int type = gameImage.getType() == BufferedImage.TYPE_CUSTOM
			? BufferedImage.TYPE_INT_ARGB
			: gameImage.getType();

		if (previousViewportBufferWidth != width || previousViewportBufferHeight != height
			|| previousViewportBufferType != type) {
			for (int i = 0; i < viewportFrameBuffers.length; i++) {
				viewportFrameBuffers[i] = new BufferedImage(width, height, type);
			}
			previousViewportBufferWidth = width;
			previousViewportBufferHeight = height;
			previousViewportBufferType = type;
		}

		viewportFrameBufferIndex = (viewportFrameBufferIndex + 1) % viewportFrameBuffers.length;
		BufferedImage target = viewportFrameBuffers[viewportFrameBufferIndex];
		Graphics2D g2d = target.createGraphics();
		try {
			g2d.drawImage(gameImage, 0, 0, null);
		} finally {
			g2d.dispose();
		}
		return target;
	}

	public boolean isViewportLoaded() {
		return scaledViewport.isViewportImageLoaded();
	}

	public void repaintBootstrapStatus() {
		if (scaledViewport != null && !scaledViewport.isViewportImageLoaded()) {
			scaledViewport.repaint();
		}
	}

	public int getWindowWidthInsets() {
		return getInsets().left + getInsets().right;
	}

	public int getWindowHeightInsets() {
		return getInsets().top + getInsets().bottom;
	}

	/** Resizes the window size for the scalar */
	public void resizeWindowToScalar() {
		Dimension minimumWindowSizeForScalar = getMinimumWindowSizeForScalar();

		if (!getSize().equals(minimumWindowSizeForScalar)) {
			// Update the window size as necessary, which will in turn
			// invoke the componentResized listener on this JFrame
			setWindowRealignmentIntent(true);

			setMinimumSize(minimumWindowSizeForScalar);
			setSize(minimumWindowSizeForScalar);
		} else {
			// Resize the viewport if the actual window size didn't change, since
			// the componentResized listener won't get triggered in that case.
			// e.g. size set to 1024x692, then scale x2 turned on
			setMinimumSize(minimumWindowSizeForScalar);
			resizeApplet();
		}
	}

	/** Resizes the window to the classic applet size for window-follow scaling mode. */
	private void resizeWindowToDefaultSize() {
		Dimension minimumWindowSize = new Dimension(
			minViewportWidth() + getWindowWidthInsets(),
			minViewportHeight() + getWindowHeightInsets());

		setMinimumSize(minimumWindowSize);

		if (!getSize().equals(minimumWindowSize)) {
			setWindowRealignmentIntent(true);
			setSize(minimumWindowSize);
		} else {
			resizeApplet();
		}
	}

	/** Determines the smallest window size for the scalar, including insets */
	private Dimension getMinimumWindowSizeForScalar() {
		Dimension minimumViewPortSizeForScalar = getMinimumViewportSizeForScalar();

		int frameWidth = minimumViewPortSizeForScalar.width + getWindowWidthInsets();
		int frameHeight = minimumViewPortSizeForScalar.height + getWindowHeightInsets();

		return new Dimension(frameWidth, frameHeight);
	}

	/** Determines the minimum window size for the applet based on the scalar */
	public Dimension getMinimumViewportSizeForScalar() {
		if (mudclient.windowScaleMode) {
			return new Dimension(minViewportWidth(), minViewportHeight());
		}

		return new Dimension(
			Math.round(baseViewportWidth * mudclient.renderingScalar),
			Math.round(baseViewportHeight * mudclient.renderingScalar));
	}

	/** Resizes the applet contained within {@link OpenRSC} */
	private void resizeApplet() {
		if (mudclient.windowScaleMode) {
			updateWindowScaleFromViewport();
			realignIfNeeded();
			return;
		}

		if (mudclient.renderingScalar == 0.0f || !isViewportLoaded()) {
			return;
		}

		int newWidth = Math.round(scaledViewport.getWidth() / mudclient.renderingScalar);
		int newHeight = Math.round(scaledViewport.getHeight() / mudclient.renderingScalar);

		if (applet != null) {
			applet.setSize(newWidth, newHeight);
			applet.resizeMudclient(newWidth, newHeight);
		}

		realignIfNeeded();
	}

	/** Resizes the mudclient if its dimensions don't match the current frame size */
	public void validateAppletSize() {
		if (applet == null) return;

		if (mudclient.windowScaleMode) {
			resizeAppletToBase();
			return;
		}

		int newWidth = Math.round(scaledViewport.getWidth() / mudclient.renderingScalar);
		int newHeight = Math.round(scaledViewport.getHeight() / mudclient.renderingScalar);

		if (applet.getWidth() != newWidth || applet.getHeight() != newHeight) {
			applet.setSize(newWidth, newHeight);
			applet.resizeMudclient(newWidth, newHeight);
		}
	}

	private void updateWindowScaleFromViewport() {
		if (!mudclient.windowScaleMode || scaledViewport == null) {
			return;
		}

		int availableWidth = Math.max(minViewportWidth(), scaledViewport.getWidth());
		int availableHeight = Math.max(minViewportHeight(), scaledViewport.getHeight());
		float scalar = Math.max(MIN_WINDOW_SCALE, Math.min(
			availableWidth / (float) baseViewportWidth,
			availableHeight / (float) baseViewportHeight));

		mudclient.renderingScalar = scalar;
		mudclient.newRenderingScalar = scalar;
		ORSCApplet.oldRenderingScalar = scalar;

		scaledDrawWidth = Math.round(baseViewportWidth * scalar);
		scaledDrawHeight = Math.round(baseViewportHeight * scalar);
		scaledDrawX = Math.max(0, (scaledViewport.getWidth() - scaledDrawWidth) / 2);
		scaledDrawY = Math.max(0, (scaledViewport.getHeight() - scaledDrawHeight) / 2);

		resizeAppletToBase();
	}

	private void updateFixedScaleDrawBounds() {
		int width = viewportWidth > 0 ? viewportWidth : baseViewportWidth;
		int height = viewportHeight > 0 ? viewportHeight : baseViewportHeight;

		scaledDrawX = 0;
		scaledDrawY = 0;
		scaledDrawWidth = Math.max(1, Math.round(width * mudclient.renderingScalar));
		scaledDrawHeight = Math.max(1, Math.round(height * mudclient.renderingScalar));
	}

	private void resizeAppletToBase() {
		if (applet == null) {
			return;
		}

		if (applet.getWidth() != baseViewportWidth || applet.getHeight() != baseViewportHeight) {
			applet.setSize(baseViewportWidth, baseViewportHeight);
			applet.resizeMudclient(baseViewportWidth, baseViewportHeight);
		}
	}

	private void realignIfNeeded() {
		if (shouldRealign) {
			setWindowRealignmentIntent(false);
			alignWindow();
		}
	}

	/*
	 * WindowListener methods - forward to Game.java
	 */

	@Override
	public void windowClosed(WindowEvent e) {
		jframe.dispatchEvent(new WindowEvent(jframe, WindowEvent.WINDOW_CLOSED));
	}

	@Override
	public void windowClosing(WindowEvent e) {
		jframe.dispatchEvent(new WindowEvent(jframe, WindowEvent.WINDOW_CLOSING));
	}

	@Override
	public void windowOpened(WindowEvent e) {}

	@Override
	public void windowDeactivated(WindowEvent e) {}

	@Override
	public void windowActivated(WindowEvent e) {}

	@Override
	public void windowDeiconified(WindowEvent e) {}

	@Override
	public void windowIconified(WindowEvent e) {}

	/*
	 * FocusListener methods - forward to Game.java
	 */

	@Override
	public void focusGained(FocusEvent e) {}

	@Override
	public void focusLost(FocusEvent e) {
		if (applet.getKeyHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.resetArrowKeys();
	}

	/*
	 * ComponentListener methods
	 */

	@Override
	public void componentResized(ComponentEvent e) {
		frameWidth = e.getComponent().getWidth();
		frameHeight = e.getComponent().getHeight();

		if (scaledViewport == null) {
			return;
		}

		resizeApplet();
		scaledViewport.repaint();
	}

	@Override
	public void componentMoved(ComponentEvent e) {}

	@Override
	public void componentShown(ComponentEvent e) {}

	@Override
	public void componentHidden(ComponentEvent e) {}

	/*
	 * MouseListener, MouseMotionListener, and MouseWheelListener methods
	 * - forward to Client.handler_mouse
	 */

	@Override
	public void mouseClicked(MouseEvent e) {
		if (applet.getMouseHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.getMouseHandler().mouseClicked(mapMouseEvent(e));
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (applet.getMouseHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.getMouseHandler().mousePressed(mapMouseEvent(e));
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (applet.getMouseHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.getMouseHandler().mouseReleased(mapMouseEvent(e));
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		if (applet.getMouseHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.getMouseHandler().mouseEntered(mapMouseEvent(e));
	}

	@Override
	public void mouseExited(MouseEvent e) {
		if (applet.getMouseHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.getMouseHandler().mouseExited(mapMouseEvent(e));
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if (applet.getMouseHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.getMouseHandler().mouseDragged(mapMouseEvent(e));
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		if (applet.getMouseHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.getMouseHandler().mouseMoved(mapMouseEvent(e));
	}

	private static MouseEvent mapMouseEvent(MouseEvent e) {
		Component mouseEventSource = (Component) e.getSource();
		int mouseEventId = e.getID();
		long mouseEventWhen = e.getWhen();
		int mouseEventModifiers = e.getModifiers();
		int mappedMouseEventX = mapScaledX(e.getX());
		int mappedMouseEventY = mapScaledY(e.getY());
		int mouseEventXOnScreen = e.getXOnScreen();
		int mouseEventYOnScreen = e.getYOnScreen();
		int mouseEventClickCount = e.getClickCount();
		boolean mouseEventPopupTrigger = e.isPopupTrigger();
		int mouseEventButton = e.getButton();

		return new MouseEvent(
			mouseEventSource,
			mouseEventId,
			mouseEventWhen,
			mouseEventModifiers,
			mappedMouseEventX,
			mappedMouseEventY,
			mouseEventXOnScreen,
			mouseEventYOnScreen,
			mouseEventClickCount,
			mouseEventPopupTrigger,
			mouseEventButton);
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		if (applet.getMouseHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.getMouseHandler().mouseWheelMoved(mapMouseWheelEvent(e));
	}

	private static MouseWheelEvent mapMouseWheelEvent(MouseWheelEvent e) {
		Component mouseWheelEventSource = (Component) e.getSource();
		int mouseWheelEventId = e.getID();
		long mouseWheelEventWhen = e.getWhen();
		int mouseWheelEventModifiers = e.getModifiers();
		int mappedMouseWheelEventX = mapScaledX(e.getX());
		int mappedMouseWheelEventY = mapScaledY(e.getY());
		int mouseWheelEventXOnScreen = e.getXOnScreen();
		int mouseWheelEventYOnScreen = e.getYOnScreen();
		int mouseWheelEventClickCount = e.getClickCount();
		boolean mouseWheelEventPopupTrigger = e.isPopupTrigger();
		int mouseWheelEventScrollType = e.getScrollType();
		int mouseWheelEventScrollAmount = e.getScrollAmount();
		int mouseWheelEventWheelRotation = e.getWheelRotation();
		double mouseWheelEventPreciseWheelRotation = e.getPreciseWheelRotation();

		return new MouseWheelEvent(
			mouseWheelEventSource,
			mouseWheelEventId,
			mouseWheelEventWhen,
			mouseWheelEventModifiers,
			mappedMouseWheelEventX,
			mappedMouseWheelEventY,
			mouseWheelEventXOnScreen,
			mouseWheelEventYOnScreen,
			mouseWheelEventClickCount,
			mouseWheelEventPopupTrigger,
			mouseWheelEventScrollType,
			mouseWheelEventScrollAmount,
			mouseWheelEventWheelRotation,
			mouseWheelEventPreciseWheelRotation);
	}

	private static int mapScaledX(int x) {
		ScaledWindow scaledWindow = getInstance();
		return Math.round((x - scaledWindow.scaledDrawX) / mudclient.renderingScalar);
	}

	private static int mapScaledY(int y) {
		ScaledWindow scaledWindow = getInstance();
		return Math.round((y - scaledWindow.scaledDrawY) / mudclient.renderingScalar);
	}

	/*
	 * KeyListener methods - forward to Client.handler_keyboard
	 */

	@Override
	public void keyTyped(KeyEvent e) {
		if (applet.getKeyHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.getKeyHandler().keyTyped(e);
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (applet.getMouseHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.getKeyHandler().keyPressed(e);
	}

	@Override
	public void keyReleased(KeyEvent e) {
		if (applet.getMouseHandler() == null || mudclient.renderingScalar == 0.0f) return;

		applet.getKeyHandler().keyReleased(e);
	}

	/**
	 * All possible types of scaling supported by the client
	 */
	public enum ScalingAlgorithm {
		INTEGER_SCALING,
		BILINEAR_INTERPOLATION,
		BICUBIC_INTERPOLATION
	}

	/**
	 * @return The {@link BufferedImage} type based on the current {@link ScalingAlgorithm}
	 */
	public static int getBufferedImageType() {
		if (mudclient.scalingType == ScalingAlgorithm.INTEGER_SCALING) {
			return BufferedImage.TYPE_INT_RGB;
		} else if (mudclient.scalingType == ScalingAlgorithm.BILINEAR_INTERPOLATION) {
			return BufferedImage.TYPE_3BYTE_BGR;
		} else if (mudclient.scalingType == ScalingAlgorithm.BICUBIC_INTERPOLATION) {
			return BufferedImage.TYPE_3BYTE_BGR;
		}

		return BufferedImage.TYPE_INT_RGB;
	}

	/**
	 * Gets the scaled window instance. It makes one if one doesn't exist.
	 *
	 * @return The scaled window instance
	 */
	public static ScaledWindow getInstance() {
		if (instance == null) {
			synchronized (ScaledWindow.class) {
				instance = new ScaledWindow();
			}
		}
		return instance;
	}

	/*
	 * Image rendering
	 */

	/** JPanel used for rendering the game viewport, with scaling capabilities */
	private static class ScaledViewport extends JPanel {
		BufferedImage interpolationBackground = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
		private volatile BufferedImage viewportImage;

		int previousWidth = 0;
		int previousHeight = 0;

		int newWidth;
		int newHeight;

		public ScaledViewport() {
			super();
			setOpaque(true);
			setBackground(Color.black);
		}

		/** Provides the game image to the viewport */
		public void setViewportImage(BufferedImage gameImage) {
			viewportImage = gameImage;
		}

		/** Ensures the viewport image has been set */
		public boolean isViewportImageLoaded() {
			return viewportImage != null;
		}

		@Override
		protected void paintComponent(Graphics g) {
			long pacingStartNs = FramePacingMonitor.now();
			super.paintComponent(g);

			if (viewportImage == null
				|| getInstance().viewportWidth == 0
				|| getInstance().viewportHeight == 0) {
				drawBootstrapStatus(g);
				FramePacingMonitor.recordFrame("desktop.paint", pacingStartNs, FramePacingMonitor.now());
				return;
			}

			ScaledWindow scaledWindow = getInstance();
			newWidth = scaledWindow.scaledDrawWidth;
			newHeight = scaledWindow.scaledDrawHeight;
			int drawX = scaledWindow.scaledDrawX;
			int drawY = scaledWindow.scaledDrawY;

			if (newWidth == viewportImage.getWidth() && newHeight == viewportImage.getHeight()) {
				g.drawImage(viewportImage, drawX, drawY, null);
				FramePacingMonitor.recordFrame("desktop.paint", pacingStartNs, FramePacingMonitor.now());
				return;
			}

			// Nearest-neighbor scaling performs well when resized directly via drawImage().
			// Interpolation scaling draws into a reusable buffer so paints do not allocate images.
			if (mudclient.scalingType == ScalingAlgorithm.INTEGER_SCALING) {
				Graphics2D g2d = (Graphics2D) g;
				g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

				// Workaround for direct drawImage warping which seems to only affect macOS on JDK 19
				if (isMacOS && javaVersion >= 19) {
					g2d.setClip(drawX, drawY, newWidth, newHeight);
				}

				g2d.drawImage(viewportImage, drawX, drawY, newWidth, newHeight, null);
			} else {
				if (interpolationBackground == null) {
					return;
				}

				// Reset image background when the window properties have changed
				if (previousWidth != newWidth || previousHeight != newHeight) {
					interpolationBackground = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_3BYTE_BGR);

					previousWidth = newWidth;
					previousHeight = newHeight;
				}

				Graphics2D g2d = interpolationBackground.createGraphics();
				try {
					g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, getInterpolationHint());
					g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
					g2d.drawImage(viewportImage, 0, 0, newWidth, newHeight, null);
				} finally {
					g2d.dispose();
				}

				// Draw the interpolation-scaled image
				g.drawImage(interpolationBackground, drawX, drawY, null);
			}
			FramePacingMonitor.recordFrame("desktop.paint", pacingStartNs, FramePacingMonitor.now());
		}

		private void drawBootstrapStatus(Graphics g) {
			Graphics2D g2d = (Graphics2D) g.create();
			try {
				int width = getWidth();
				int height = getHeight();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2d.setColor(Color.black);
				g2d.fillRect(0, 0, width, height);

				String state = ORSCApplet.globalLoadingState;
				if (state == null || state.trim().length() == 0) {
					state = "Starting Voidscape...";
				}
				boolean error = state.toLowerCase(Locale.ENGLISH).contains("unable")
					|| state.toLowerCase(Locale.ENGLISH).contains("failed")
					|| state.toLowerCase(Locale.ENGLISH).contains("error");
				int accent = error ? UiSkin.BAD : UiSkin.PURPLE_FOCUS;
				int panelWidth = Math.min(340, Math.max(240, width - 64));
				int panelHeight = 96;
				int x = Math.max(16, (width - panelWidth) / 2);
				int y = Math.max(24, (height - panelHeight) / 2);

				g2d.setColor(new Color(10, 10, 14));
				g2d.fillRect(x, y, panelWidth, panelHeight);
				g2d.setColor(new Color(accent));
				g2d.drawRect(x, y, panelWidth, panelHeight);
				g2d.setColor(new Color(230, 225, 214));
				g2d.setFont(new Font("SansSerif", Font.BOLD, 15));
				drawCentered(g2d, "VOIDSCAPE", x, y + 24, panelWidth);
				g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
				drawCentered(g2d, state, x + 14, y + 50, panelWidth - 28);

				int progress = Math.max(0, Math.min(100, ORSCApplet.globalLoadingPercent));
				int barX = x + 28;
				int barY = y + 68;
				int barWidth = panelWidth - 56;
				g2d.setColor(new Color(45, 40, 54));
				g2d.fillRect(barX, barY, barWidth, 9);
				g2d.setColor(new Color(accent));
				g2d.fillRect(barX, barY, Math.max(6, progress * barWidth / 100), 9);
			} finally {
				g2d.dispose();
			}
		}

		private void drawCentered(Graphics2D g2d, String text, int x, int y, int width) {
			FontMetrics metrics = g2d.getFontMetrics();
			int textX = x + Math.max(0, (width - metrics.stringWidth(text)) / 2);
			g2d.drawString(text, textX, y);
		}

		private Object getInterpolationHint() {
			if (mudclient.scalingType == ScalingAlgorithm.BICUBIC_INTERPOLATION) {
				return RenderingHints.VALUE_INTERPOLATION_BICUBIC;
			}
			return RenderingHints.VALUE_INTERPOLATION_BILINEAR;
		}
	}
}
