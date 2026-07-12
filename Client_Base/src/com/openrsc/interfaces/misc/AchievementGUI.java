package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import orsc.Config;
import orsc.graphics.gui.Panel;
import orsc.graphics.gui.UiSkin;
import orsc.graphics.two.GraphicsController;
import orsc.mudclient;

public final class AchievementGUI {
	private static final int FOOTER_H = 25;

	public Panel achievementPanel;
	private int x, y;
	private int width, height;
	private int achievementID = -1;
	private boolean visible;
	private mudclient mc;

	public AchievementGUI(mudclient mc) {
		this.mc = mc;

		width = 375;
		height = 246 - 25;

		x = (mc.getGameWidth() / 2) - width;
		y = (mc.getGameHeight() / 2) - height;

		achievementPanel = new Panel(mc.getSurface(), 5);
	}

	public void reposition() {
		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;
	}

	public boolean onRender(GraphicsController graphics) {
		reposition();

		// Glass card: title bar + content area (height) + close footer.
		int cardHeight = InterfaceChrome.TITLE_H + height + FOOTER_H;
		boolean closeHover = UiSkin.hit(InterfaceChrome.closeX(x, width), InterfaceChrome.closeY(y),
			InterfaceChrome.CLOSE_SIZE, InterfaceChrome.CLOSE_SIZE, mc.getMouseX(), mc.getMouseY());
		InterfaceChrome.window(graphics, x, y, width, cardHeight,
			"Achievement:" + (mc.achievementProgress[getAchievement()] == 2 ? "@gre@ Completed" : ""), closeHover);

		// CONTENT
		graphics.drawColoredStringCentered(width / 2 + x, (mc.achievementProgress[getAchievement()] == 2 ? "@gre@" : "@yel@") + mc.achievementNames[getAchievement()], UiSkin.TEXT_BODY, 0, 5, y + 40);
		graphics.drawWrappedCenteredString(mc.achievementDescs[getAchievement()], width / 2 + x, y + 55, width - 14, 1, UiSkin.TEXT_BODY, true);

		graphics.drawString("Rewards: ", x + 6, y + 135, UiSkin.GOLD_HEADER, 1);
		graphics.drawString((mc.achievementProgress[getAchievement()] == 2 ? "@gr2@Congratulations! " + (Config.isAndroid() ? "tap" : "click") + " on each box to claim your rewards." : "@or1@You have not completed your achievement yet."), x + 63, y + 135, UiSkin.TEXT_BODY, 0);
		int rewardBoxX = 0;
		int rewardBoxY = y + 143;
		for (int box = 0; box < 12; box++) {
			int sizeX = 48;
			int sizeY = 34;
			graphics.drawBoxAlpha(rewardBoxX + 74, rewardBoxY, sizeX, sizeY, UiSkin.VOID_BOX, UiSkin.A_BUTTON);
			graphics.drawBoxBorder(rewardBoxX + 74 - 1, sizeX + 2, rewardBoxY - 1, sizeY + 2, UiSkin.VOID_LINE);
			//rewardBox.setSprite(2150 + 130, 48, 32, 0);
			graphics.drawSpriteClipping(mc.spriteSelect(EntityHandler.getItemDef(30)), rewardBoxX + 74, rewardBoxY, 48, 32, 0, 0, 0,false, 0, 1);

			rewardBoxX += sizeX + 15;

			if (rewardBoxX + sizeX > this.width) {
				rewardBoxY += sizeY + 15;
				rewardBoxX = 0;
			}
		}

		// CLOSE FOOTER — anchored to the card bottom (was drawn at absolute
		// y = height + 71, which ignored the panel position and fell off-panel).
		drawButton(graphics, x, y + cardHeight - FOOTER_H, width, FOOTER_H, (Config.isAndroid() ? "Tap here to close" : "Click left mouse button to close"), false, new ButtonHandler() {
			@Override
			void handle() {
				setAchievement(-1);
				hide();
			}
		});

		// X-close dispatch last: setAchievement(-1) must not run before the
		// content above indexes mc.achievement* arrays with getAchievement().
		if (closeHover && mc.getMouseClick() == 1) {
			setAchievement(-1);
			hide();
			mc.setMouseClick(0);
		}

		return true;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public void show() {
		setVisible(true);
	}

	public void hide() {
		setVisible(false);
	}

	private int getAchievement() {
		return achievementID;
	}

	public void setAchievement(int i) {
		this.achievementID = i;
	}

	private void drawButton(GraphicsController graphics, int x, int y, int width, int height, String text,
							boolean checked, ButtonHandler handler) {
		boolean hover = InterfaceChrome.button(graphics, x, y, width, height, text, UiSkin.FONT_BODY, checked, false,
			mc.getMouseX(), mc.getMouseY());
		if (hover && mc.getMouseClick() == 1) {
			handler.handle();
			mc.setMouseClick(0);
		}
	}
}
