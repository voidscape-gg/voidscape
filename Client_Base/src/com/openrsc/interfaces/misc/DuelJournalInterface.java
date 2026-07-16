package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import orsc.Config;
import orsc.graphics.gui.UiAnchor;
import orsc.graphics.gui.UiSkin;
import orsc.graphics.two.GraphicsController;
import orsc.mudclient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Private, read-only view of the local player's completed duels.
 *
 * The server sends only the requester's swing rows. This view deliberately has
 * no opponent-swing model, so a later rendering mistake cannot expose one.
 */
public final class DuelJournalInterface {

	public enum ProofState {
		VERIFIED,
		FAILED,
		UNAVAILABLE
	}

	public interface SelectionHandler {
		void select(long duelId);
	}

	public static final class DuelSummary {
		public final long duelId;
		public final long startedAt;
		public final long completedAt;
		public final boolean won;
		public final int opponentPlayerId;
		public final String opponentName;

		public DuelSummary(long duelId, long startedAt, long completedAt, boolean won,
						   int opponentPlayerId, String opponentName) {
			this.duelId = duelId;
			this.startedAt = startedAt;
			this.completedAt = completedAt;
			this.won = won;
			this.opponentPlayerId = opponentPlayerId;
			this.opponentName = safe(opponentName);
		}
	}

	public static final class StakeItem {
		public final boolean mine;
		public final int slot;
		public final int catalogId;
		public final int amount;
		public final boolean noted;

		public StakeItem(boolean mine, int slot, int catalogId, int amount, boolean noted) {
			this.mine = mine;
			this.slot = slot;
			this.catalogId = catalogId;
			this.amount = amount;
			this.noted = noted;
		}
	}

	public static final class Swing {
		public final int number;
		public final int combatStyle;
		public final boolean accurate;
		public final int damage;

		public Swing(int number, int combatStyle, boolean accurate, int damage) {
			this.number = number;
			this.combatStyle = combatStyle;
			this.accurate = accurate;
			this.damage = damage;
		}
	}

	public static final class DuelDetail {
		public final long duelId;
		public final int requesterPlayerId;
		public final int opponentPlayerId;
		public final boolean won;
		public final long startedAt;
		public final long completedAt;
		public final String opponentName;
		public final ProofState proofState;
		public final String proofId;
		public final List<StakeItem> stakes;
		public final List<Swing> swings;

		public DuelDetail(long duelId, int requesterPlayerId, int opponentPlayerId, boolean won,
						  long startedAt, long completedAt, String opponentName,
						  ProofState proofState, String proofId,
						  List<StakeItem> stakes, List<Swing> swings) {
			this.duelId = duelId;
			this.requesterPlayerId = requesterPlayerId;
			this.opponentPlayerId = opponentPlayerId;
			this.won = won;
			this.startedAt = startedAt;
			this.completedAt = completedAt;
			this.opponentName = safe(opponentName);
			this.proofState = proofState == null ? ProofState.UNAVAILABLE : proofState;
			this.proofId = safe(proofId);
			this.stakes = immutableCopy(stakes);
			this.swings = immutableCopy(swings);
		}
	}

	private static final int OUTER_PAD = 8;
	private static final int HISTORY_WIDTH = 132;
	private static final int SECTION_H = 18;
	private static final int FOOTER_H = 18;
	private static final int ITEM_COLUMNS = 4;
	private static final int ITEM_ROWS = 3;
	private static final int PROOF_CARD_H = 54;

	private final mudclient mc;
	private final SelectionHandler selectionHandler;
	private List<DuelSummary> history = Collections.emptyList();
	private DuelDetail detail;
	private boolean visible;
	private boolean loading;
	private int historyScroll;
	private int swingScroll;

	// Last rendered geometry is also the single source of truth for hit-testing
	// and the AI workbench's later inspection hooks.
	private int windowX;
	private int windowY;
	private int windowWidth;
	private int windowHeight;
	private int historyX;
	private int historyY;
	private int historyW;
	private int historyH;
	private int swingX;
	private int swingY;
	private int swingW;
	private int swingH;

	public DuelJournalInterface(mudclient mc, SelectionHandler selectionHandler) {
		this.mc = mc;
		this.selectionHandler = selectionHandler;
	}

	public void beginLoading() {
		loading = true;
	}

	public void cancelLoading() {
		loading = false;
	}

	/** Commits a complete server envelope in one frame. */
	public void applyJournal(List<DuelSummary> summaries, DuelDetail selected) {
		history = immutableCopy(summaries);
		detail = selected;
		historyScroll = clamp(historyScroll, 0, historyMaxScroll());
		swingScroll = 0;
		loading = false;
		visible = true;
	}

	public void close() {
		visible = false;
		loading = false;
	}

	/** Clears all account-owned journal state. */
	public void reset() {
		history = Collections.emptyList();
		detail = null;
		historyScroll = 0;
		swingScroll = 0;
		visible = false;
		loading = false;
	}

	public boolean isVisible() {
		return visible;
	}

	public boolean isLoading() {
		return loading;
	}

	public long getSelectedDuelId() {
		return detail == null ? -1L : detail.duelId;
	}

	public int getHistoryCount() {
		return history.size();
	}

	public int getSwingCount() {
		return detail == null ? 0 : detail.swings.size();
	}

	public String getProofState() {
		if (detail == null || detail.proofState == ProofState.UNAVAILABLE) return "unavailable";
		return detail.proofState == ProofState.VERIFIED ? "verified" : "failed";
	}

	public String getProofId() {
		return detail == null ? "" : detail.proofId;
	}

	public int getWindowX() { layout(); return windowX; }
	public int getWindowY() { layout(); return windowY; }
	public int getWindowWidth() { layout(); return windowWidth; }
	public int getWindowHeight() { layout(); return windowHeight; }
	public int getHistoryX() { layout(); return historyX; }
	public int getHistoryY() { layout(); return historyY; }
	public int getHistoryWidth() { layout(); return historyW; }
	public int getHistoryHeight() { layout(); return historyH; }
	public int getSwingX() { layout(); return swingX; }
	public int getSwingY() { layout(); return swingY; }
	public int getSwingWidth() { layout(); return swingW; }
	public int getSwingHeight() { layout(); return swingH; }
	public int getCloseCenterX() {
		layout();
		return InterfaceChrome.closeX(windowX, windowWidth) + InterfaceChrome.CLOSE_SIZE / 2;
	}
	public int getCloseCenterY() {
		layout();
		return InterfaceChrome.closeY(windowY) + InterfaceChrome.CLOSE_SIZE / 2;
	}

	/** Draws and consumes pointer clicks while the modal is open. */
	public void onRender() {
		if (!visible) return;
		layout();
		final GraphicsController g = mc.getSurface();
		final int mouseX = mc.getMouseX();
		final int mouseY = mc.getMouseY();
		final boolean closeHover = isCloseHit(mouseX, mouseY);

		InterfaceChrome.window(g, windowX, windowY, windowWidth, windowHeight,
			"Duel Journal", closeHover);
		g.drawString("PRIVATE", windowX + 9, windowY + 16, UiSkin.PURPLE_FOCUS, UiSkin.FONT_SMALL);

		drawHistory(g, mouseX, mouseY);
		drawDetail(g, mouseX, mouseY);

		if (mc.getMouseClick() != 0) handleClick(mouseX, mouseY, mc.getMouseClick());
	}

	/** Consumes every pointer click while visible so none can reach the world. */
	public boolean handleClick(int x, int y, int button) {
		if (!visible) return false;
		layout();
		if (button == 1) {
			if (isCloseHit(x, y)) mc.closeDuelJournal();
			else handleHistorySelection(x, y);
		}
		mc.setMouseClick(0);
		return true;
	}

	public void handleWheel(int delta) {
		if (!visible || delta == 0) return;
		layout();
		if (UiSkin.hit(historyX, historyY, historyW, historyH, mc.getMouseX(), mc.getMouseY())) {
			historyScroll = clamp(historyScroll + delta * historyRowHeight(), 0, historyMaxScroll());
		} else if (UiSkin.hit(swingX, swingY, swingW, swingH, mc.getMouseX(), mc.getMouseY())) {
			swingScroll = clamp(swingScroll + delta * swingRowHeight(), 0, swingMaxScroll());
		}
	}

	private void layout() {
		windowWidth = UiSkin.modalWidth(mc.getGameWidth(), UiSkin.MODAL_W_GRID);
		windowHeight = Math.min(318, Math.max(220, mc.getGameHeight() - 16));
		windowX = UiAnchor.centeredDialogX(mc.getGameWidth(), windowWidth);
		windowY = UiAnchor.centeredCardY(mc.getGameHeight(), windowHeight, 8);

		historyX = windowX + OUTER_PAD;
		historyY = windowY + InterfaceChrome.TITLE_H + SECTION_H;
		historyW = Math.min(HISTORY_WIDTH, Math.max(98, windowWidth / 3));
		historyH = windowHeight - InterfaceChrome.TITLE_H - SECTION_H - FOOTER_H - OUTER_PAD;

		final int detailX = historyX + historyW + OUTER_PAD;
		final int detailW = windowX + windowWidth - OUTER_PAD - detailX;
		final int detailTop = windowY + InterfaceChrome.TITLE_H + 2;
		final int stakesY = detailTop + 31 + PROOF_CARD_H + 4;
		final int swingsTitleY = stakesY + 82 + 4;
		final int footerY = windowY + windowHeight - FOOTER_H;
		swingX = detailX;
		swingY = swingsTitleY + SECTION_H;
		swingW = detailW;
		swingH = Math.max(24, footerY - swingY - 2);
	}

	private boolean isCloseHit(int mouseX, int mouseY) {
		final int visualX = InterfaceChrome.closeX(windowX, windowWidth);
		final int visualY = InterfaceChrome.closeY(windowY);
		if (!Config.isAndroid()) {
			return UiSkin.hit(visualX, visualY, InterfaceChrome.CLOSE_SIZE,
				InterfaceChrome.CLOSE_SIZE, mouseX, mouseY);
		}
		final int touch = 48;
		return UiSkin.hit(visualX + InterfaceChrome.CLOSE_SIZE - touch,
			visualY, touch, touch, mouseX, mouseY);
	}

	private void drawHistory(GraphicsController g, int mouseX, int mouseY) {
		InterfaceChrome.sectionStrip(g, historyX, windowY + InterfaceChrome.TITLE_H + 2,
			historyW, SECTION_H - 2);
		g.drawString("RECENT DUELS", historyX + 5, windowY + InterfaceChrome.TITLE_H + 14,
			UiSkin.GOLD_HEADER, UiSkin.FONT_SMALL);
		UiSkin.glassPanel(g, historyX, historyY, historyW, historyH, UiSkin.A_GLASS);

		if (history.isEmpty()) {
			g.drawColoredStringCentered(historyX + historyW / 2, "No completed duels",
				UiSkin.TEXT_DIM, 0, UiSkin.FONT_SMALL, historyY + 24);
			return;
		}

		final int rowH = historyRowHeight();
		historyScroll = clamp(historyScroll, 0, historyMaxScroll());
		UiSkin.pushClip(g, historyX + 1, historyY + 1, historyW - 2, historyH - 2);
		for (int i = 0; i < history.size(); i++) {
			DuelSummary summary = history.get(i);
			int rowY = historyY + 1 + i * rowH - historyScroll;
			if (rowY + rowH <= historyY || rowY >= historyY + historyH) continue;
			boolean selected = detail != null && detail.duelId == summary.duelId;
			boolean hover = UiSkin.hit(historyX + 1, rowY, historyW - 2, rowH,
				mouseX, mouseY) && UiSkin.hit(historyX, historyY, historyW, historyH, mouseX, mouseY);
			UiSkin.listRowFill(g, historyX + 1, rowY, historyW - 2, rowH, hover, selected);
			g.drawLineHoriz(historyX + 4, rowY + rowH - 1, historyW - 8, UiSkin.VOID_LINE);
			g.drawString(summary.won ? "WIN" : "LOSS", historyX + 6, rowY + 12,
				summary.won ? UiSkin.GOOD : UiSkin.BAD, UiSkin.FONT_SMALL);
			g.drawString(fit(summary.opponentName, historyW - 45), historyX + 38, rowY + 12,
				UiSkin.TEXT_BODY, UiSkin.FONT_SMALL);
			g.drawString(relativeTime(summary.completedAt), historyX + 6, rowY + rowH - 5,
				UiSkin.TEXT_DIM, UiSkin.FONT_SMALL);
		}
		UiSkin.popClip(g);
		drawScrollbar(g, historyX + historyW - 5, historyY + 3, historyH - 6,
			history.size() * rowH, historyH - 2, historyScroll);
	}

	private void drawDetail(GraphicsController g, int mouseX, int mouseY) {
		final int detailX = historyX + historyW + OUTER_PAD;
		final int detailW = windowX + windowWidth - OUTER_PAD - detailX;
		final int detailTop = windowY + InterfaceChrome.TITLE_H + 2;
		final int footerY = windowY + windowHeight - FOOTER_H;

		if (detail == null) {
			UiSkin.glassPanel(g, detailX, detailTop, detailW, footerY - detailTop,
				UiSkin.A_GLASS);
			g.drawColoredStringCentered(detailX + detailW / 2,
				history.isEmpty() ? "Complete a duel to begin your journal." : "Select a duel.",
				UiSkin.TEXT_DIM, 0, UiSkin.FONT_BODY, detailTop + 42);
			drawFooter(g, footerY);
			return;
		}

		InterfaceChrome.sectionStrip(g, detailX, detailTop, detailW, 28);
		g.drawString(detail.won ? "VICTORY" : "DEFEAT", detailX + 8, detailTop + 18,
			detail.won ? UiSkin.GOOD : UiSkin.BAD, UiSkin.FONT_TITLE);
		g.drawString("vs " + fit(detail.opponentName, detailW - 126), detailX + 82,
			detailTop + 17, UiSkin.TEXT_BODY, UiSkin.FONT_BODY);
		g.drawString("#" + detail.duelId, detailX + detailW - 67, detailTop + 17,
			UiSkin.TEXT_DIM, UiSkin.FONT_SMALL);

		final int proofY = detailTop + 31;
		drawProofCard(g, detailX, proofY, detailW, PROOF_CARD_H);

		final int stakesY = proofY + PROOF_CARD_H + 4;
		final int stakeGap = 5;
		final int stakeW = (detailW - stakeGap) / 2;
		final int stakeGridH = 82;
		drawStakeCard(g, detailX, stakesY, stakeW, stakeGridH, true, mouseX, mouseY);
		drawStakeCard(g, detailX + stakeW + stakeGap, stakesY, detailW - stakeW - stakeGap,
			stakeGridH, false, mouseX, mouseY);

		final int swingsTitleY = stakesY + stakeGridH + 4;
		InterfaceChrome.sectionStrip(g, detailX, swingsTitleY, detailW, SECTION_H);
		g.drawString("YOUR MELEE SWINGS", detailX + 6, swingsTitleY + 13,
			UiSkin.GOLD_HEADER, UiSkin.FONT_SMALL);
		g.drawString(detail.swings.size() + " total", detailX + detailW - 47, swingsTitleY + 13,
			UiSkin.TEXT_DIM, UiSkin.FONT_SMALL);

		swingX = detailX;
		swingY = swingsTitleY + SECTION_H;
		swingW = detailW;
		swingH = Math.max(24, footerY - swingY - 2);
		UiSkin.glassPanel(g, swingX, swingY, swingW, swingH, UiSkin.A_GLASS);
		drawSwings(g, mouseX, mouseY);
		drawFooter(g, footerY);
	}

	private void drawProofCard(GraphicsController g, int x, int y, int width, int height) {
		final int accent;
		final String heading;
		final String explanationFirst;
		final String explanationSecond;
		switch (detail.proofState) {
			case VERIFIED:
				accent = UiSkin.GOOD;
				heading = "COMBAT REPLAY VERIFIED";
				explanationFirst = "This No Magic duel's melee result matches";
				explanationSecond = "seeds locked before combat.";
				break;
			case FAILED:
				accent = UiSkin.BAD;
				heading = "COMBAT REPLAY FAILED";
				explanationFirst = "This duel's replay proof could not be";
				explanationSecond = "independently verified.";
				break;
			default:
				accent = UiSkin.TEXT_DIM;
				heading = "COMBAT REPLAY UNAVAILABLE";
				explanationFirst = "No replay proof was created for this duel.";
				explanationSecond = "Next time, check No Magic before accepting.";
				break;
		}
		UiSkin.glassPanel(g, x, y, width, height, UiSkin.A_GLASS_TEXT);
		g.drawBoxAlpha(x + 2, y + 2, 3, height - 4, accent, UiSkin.A_BUTTON);
		g.drawString(heading, x + 10, y + 15, accent, UiSkin.FONT_SMALL);
		if (!detail.proofId.isEmpty()) {
			String shortId = detail.proofId.substring(0, Math.min(8, detail.proofId.length()));
			String label = "Proof " + shortId;
			g.drawString(label, x + width - g.stringWidth(UiSkin.FONT_SMALL, label) - 7,
				y + 15, UiSkin.TEXT_DIM, UiSkin.FONT_SMALL);
		}
		g.drawString(fit(explanationFirst, width - 18), x + 10, y + 33,
			UiSkin.TEXT_BODY, UiSkin.FONT_SMALL);
		g.drawString(fit(explanationSecond, width - 18), x + 10, y + 46,
			UiSkin.TEXT_BODY, UiSkin.FONT_SMALL);
	}

	private void drawStakeCard(GraphicsController g, int x, int y, int width, int height,
						   boolean mine, int mouseX, int mouseY) {
		UiSkin.glassPanel(g, x, y, width, height, UiSkin.A_GLASS);
		g.drawString(mine ? "YOU STAKED" : "OPPONENT STAKED", x + 5, y + 12,
			mine ? UiSkin.GOLD_HEADER : UiSkin.TEXT_LABEL, UiSkin.FONT_SMALL);

		List<StakeItem> items = new ArrayList<StakeItem>();
		for (StakeItem item : detail.stakes) {
			if (item.mine == mine) items.add(item);
		}
		if (items.isEmpty()) {
			g.drawColoredStringCentered(x + width / 2, "Nothing", UiSkin.TEXT_DIM,
				0, UiSkin.FONT_SMALL, y + 48);
			return;
		}

		final int gridY = y + 16;
		final int cellW = Math.max(24, (width - 4) / ITEM_COLUMNS);
		final int cellH = Math.max(20, (height - 18) / ITEM_ROWS);
		String tooltip = null;
		for (int i = 0; i < items.size() && i < ITEM_COLUMNS * ITEM_ROWS; i++) {
			StakeItem item = items.get(i);
			int cellX = x + 2 + (i % ITEM_COLUMNS) * cellW;
			int cellY = gridY + (i / ITEM_COLUMNS) * cellH;
			boolean hover = UiSkin.hit(cellX, cellY, cellW, cellH, mouseX, mouseY);
			if (hover) UiSkin.listRowFill(g, cellX, cellY, cellW, cellH, true, false);
			drawItem(g, item, cellX, cellY, cellW, cellH);
			if (hover) {
				ItemDef def = EntityHandler.getItemDef(item.catalogId);
				tooltip = safe(def == null ? "Item " + item.catalogId : def.getName())
					+ (item.noted ? " (noted)" : "") + " x " + item.amount;
			}
		}
		if (tooltip != null) drawItemTooltip(g, tooltip, mouseX, mouseY);
	}

	private void drawItem(GraphicsController g, StakeItem item, int x, int y, int width, int height) {
		ItemDef def = EntityHandler.getItemDef(item.catalogId);
		if (def == null) return;
		int iconW = Math.min(29, Math.max(18, width - 5));
		int iconH = Math.min(19, Math.max(13, height - 5));
		int iconX = x + (width - iconW) / 2;
		int iconY = y + (height - iconH) / 2;
		if (item.noted && Config.S_WANT_CERT_AS_NOTES) {
			ItemDef note = EntityHandler.noteDef;
			g.drawSpriteClipping(mc.spriteSelect(note), iconX, iconY, iconW, iconH,
				note.getPictureMask(), 0, note.getBlueMask(), false, 0, 1);
			int innerW = Math.max(12, iconW * 3 / 5);
			int innerH = Math.max(9, iconH * 3 / 5);
			g.drawSpriteClipping(mc.spriteSelect(def), iconX + iconW - innerW,
				iconY + iconH - innerH, innerW, innerH, def.getPictureMask(), 0,
				def.getBlueMask(), false, 0, 1);
		} else if (item.noted) {
			ItemDef certificate = EntityHandler.certificateDef;
			g.drawSpriteClipping(mc.spriteSelect(certificate), iconX, iconY, iconW, iconH,
				certificate.getPictureMask(), 0, certificate.getBlueMask(), false, 0, 1);
		} else {
			g.drawSpriteClipping(mc.spriteSelect(def), iconX, iconY, iconW, iconH,
				def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
		}
		if (item.amount > 1) {
			g.drawString(mudclient.formatStackAmount(item.amount), x + 1, y + 9,
				UiSkin.GOLD_HOT, UiSkin.FONT_SMALL);
		}
	}

	private void drawItemTooltip(GraphicsController g, String text, int mouseX, int mouseY) {
		String fitted = fit(text, 34);
		int width = Math.min(windowWidth - 12,
			g.stringWidth(UiSkin.FONT_SMALL, fitted) + 10);
		int x = clamp(mouseX + 8, windowX + 4, windowX + windowWidth - width - 4);
		int y = clamp(mouseY + 8, windowY + 4, windowY + windowHeight - 21);
		UiSkin.tooltip(g, x, y, width, 17);
		g.drawString(fitted, x + 5, y + 12, UiSkin.TEXT_BODY, UiSkin.FONT_SMALL);
	}

	private void drawSwings(GraphicsController g, int mouseX, int mouseY) {
		if (detail.swings.isEmpty()) {
			g.drawColoredStringCentered(swingX + swingW / 2, "No recorded swings",
				UiSkin.TEXT_DIM, 0, UiSkin.FONT_SMALL, swingY + 20);
			return;
		}

		final int rowH = swingRowHeight();
		swingScroll = clamp(swingScroll, 0, swingMaxScroll());
		UiSkin.pushClip(g, swingX + 1, swingY + 1, swingW - 2, swingH - 2);
		for (int i = 0; i < detail.swings.size(); i++) {
			Swing swing = detail.swings.get(i);
			int rowY = swingY + 1 + i * rowH - swingScroll;
			if (rowY + rowH <= swingY || rowY >= swingY + swingH) continue;
			boolean hover = UiSkin.hit(swingX + 1, rowY, swingW - 2, rowH, mouseX, mouseY)
				&& UiSkin.hit(swingX, swingY, swingW, swingH, mouseX, mouseY);
			UiSkin.listRowFill(g, swingX + 1, rowY, swingW - 2, rowH, hover, false);
			g.drawLineHoriz(swingX + 5, rowY + rowH - 1, swingW - 10, UiSkin.VOID_LINE);
			g.drawString(twoDigits(swing.number), swingX + 7, rowY + rowH / 2 + 4,
				UiSkin.TEXT_DIM, UiSkin.FONT_SMALL);
			g.drawString(styleLabel(swing.combatStyle), swingX + 28, rowY + rowH / 2 + 4,
				UiSkin.TEXT_BODY, UiSkin.FONT_SMALL);
			String result = swing.accurate ? "HIT  " + swing.damage : "MISS";
			g.drawString(result, swingX + swingW - 53, rowY + rowH / 2 + 4,
				swing.accurate ? UiSkin.GOOD : UiSkin.BAD, UiSkin.FONT_SMALL);
		}
		UiSkin.popClip(g);
		drawScrollbar(g, swingX + swingW - 5, swingY + 3, swingH - 6,
			detail.swings.size() * rowH, swingH - 2, swingScroll);
	}

	private void drawFooter(GraphicsController g, int footerY) {
		g.drawLineHoriz(windowX + OUTER_PAD, footerY, windowWidth - OUTER_PAD * 2,
			UiSkin.VOID_LINE);
		g.drawColoredStringCentered(windowX + windowWidth / 2,
			"Private journal / Your swings only / Esc closes",
			loading ? UiSkin.GOLD_HOT : UiSkin.TEXT_DIM, 0, UiSkin.FONT_SMALL, footerY + 13);
	}

	private void handleHistorySelection(int mouseX, int mouseY) {
		if (!UiSkin.hit(historyX, historyY, historyW, historyH, mouseX, mouseY)) return;
		int index = (mouseY - historyY - 1 + historyScroll) / historyRowHeight();
		if (index < 0 || index >= history.size()) return;
		DuelSummary selected = history.get(index);
		if (detail != null && detail.duelId == selected.duelId) return;
		loading = true;
		if (selectionHandler != null) selectionHandler.select(selected.duelId);
	}

	private void drawScrollbar(GraphicsController g, int x, int y, int height,
						   int contentHeight, int viewportHeight, int scroll) {
		if (height <= 0 || contentHeight <= viewportHeight) return;
		g.drawBoxAlpha(x, y, 3, height, UiSkin.VOID_BOX, UiSkin.A_GLASS_TEXT);
		int thumbH = Math.max(12, height * viewportHeight / contentHeight);
		int maxThumbY = Math.max(0, height - thumbH);
		int maxScroll = Math.max(1, contentHeight - viewportHeight);
		int thumbY = y + maxThumbY * scroll / maxScroll;
		g.drawBoxAlpha(x, thumbY, 3, thumbH, UiSkin.PURPLE_FOCUS, UiSkin.A_BUTTON);
	}

	private int historyRowHeight() {
		return Config.isAndroid() ? 48 : 30;
	}

	private int swingRowHeight() {
		return Config.isAndroid() ? 48 : 22;
	}

	private int historyMaxScroll() {
		return Math.max(0, history.size() * historyRowHeight() - Math.max(0, historyH - 2));
	}

	private int swingMaxScroll() {
		return detail == null ? 0 : Math.max(0,
			detail.swings.size() * swingRowHeight() - Math.max(0, swingH - 2));
	}

	private String fit(String text, int width) {
		String value = safe(text);
		if (width <= 0) return "";
		while (value.length() > 1 && mc.getSurface().stringWidth(UiSkin.FONT_SMALL, value) > width) {
			value = value.substring(0, value.length() - 1);
		}
		return value.equals(safe(text)) ? value : value + "...";
	}

	private static String styleLabel(int style) {
		switch (style) {
			case 0: return "CONTROLLED  +1 ALL";
			case 1: return "AGGRESSIVE  +3 STR";
			case 2: return "ACCURATE  +3 ATT";
			case 3: return "DEFENSIVE  +3 DEF";
			default: return "UNKNOWN STYLE";
		}
	}

	private static String relativeTime(long completedAt) {
		long age = Math.max(0L, System.currentTimeMillis() - completedAt);
		long minutes = age / 60000L;
		if (minutes < 1L) return "just now";
		if (minutes < 60L) return minutes + "m ago";
		long hours = minutes / 60L;
		if (hours < 24L) return hours + "h ago";
		return (hours / 24L) + "d ago";
	}

	private static String twoDigits(int value) {
		return value < 10 ? "0" + value : Integer.toString(value);
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static <T> List<T> immutableCopy(List<T> values) {
		if (values == null || values.isEmpty()) return Collections.emptyList();
		return Collections.unmodifiableList(new ArrayList<T>(values));
	}
}
