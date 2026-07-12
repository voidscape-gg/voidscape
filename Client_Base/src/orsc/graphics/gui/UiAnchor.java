package orsc.graphics.gui;

/**
 * Resolution-independent anchoring math for UI layout.
 *
 * See docs/UI-STYLE-GUIDE.md §2. Pure static int math over passed-in
 * dimensions — no client state, no rendering. Kept separate from UiSkin so
 * geometry and color changes stay independently reviewable, and the sweep for
 * legacy 512-era literals (256-centers etc.) stays greppable.
 */
public final class UiAnchor {

	private UiAnchor() {
	}

	/** X that centers content of the given width. */
	public static int centerX(int gameWidth, int contentWidth) {
		return (gameWidth - contentWidth) / 2;
	}

	/** X of the right edge minus a margin. */
	public static int rightEdge(int gameWidth, int margin) {
		return gameWidth - margin;
	}

	/** Clamp a top coordinate below the HUD-safe boundary. */
	public static int clampTopSafe(int y, int topSafeY) {
		return Math.max(y, topSafeY);
	}

	/** Clamp a top coordinate so the content bottom stays above the safe boundary. */
	public static int clampBottomSafe(int y, int height, int bottomSafeY) {
		return Math.min(y, bottomSafeY - height);
	}

	/**
	 * Centered dialog X with a minimum 8px inset (promoted from the void-arena
	 * dialog helpers, mudclient.java:5480-5482).
	 */
	public static int centeredDialogX(int gameWidth, int dialogWidth) {
		return Math.max(8, (gameWidth - dialogWidth) / 2);
	}

	/**
	 * Dialog Y: prefers vertical centering capped at a comfortable top band,
	 * then respects a HUD top clearance and a bottom pad (promoted from the
	 * void-arena dialog helpers, mudclient.java:5484-5493).
	 */
	public static int centeredDialogY(int gameHeight, int dialogHeight, int topClearance, int bottomPad) {
		int y = Math.max(8, Math.min(36, (gameHeight - dialogHeight) / 2));
		int maxY = Math.max(8, gameHeight - dialogHeight - bottomPad);
		return Math.min(maxY, Math.max(y, topClearance));
	}

	/**
	 * Y that vertically centers a card of the given height, but never above the
	 * given top clearance. Pre-game (login flow) cards pass their historic top
	 * offset as the clearance: at the classic 334px game height the center sits
	 * above the clearance, so the legacy layout is preserved exactly, while
	 * taller windows center the card properly.
	 */
	public static int centeredCardY(int gameHeight, int cardHeight, int topClearance) {
		return Math.max(topClearance, (gameHeight - cardHeight) / 2);
	}
}
