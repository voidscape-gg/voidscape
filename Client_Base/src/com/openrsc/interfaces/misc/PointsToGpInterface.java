package com.openrsc.interfaces.misc;

import orsc.Config;
import orsc.graphics.gui.Panel;
import orsc.graphics.two.GraphicsController;
import orsc.enumerations.InputXAction;
import orsc.graphics.gui.InputXPrompt;
import orsc.graphics.gui.UiSkin;
import orsc.mudclient;


public final class PointsToGpInterface {
	public Panel experienceConfig;
	public int experienceConfigScroll;
	public boolean selectSkillMenu = false;
	int width = 350, height = 75;
	private boolean visible = false;
	private mudclient mc;
	private int x, y;

	public PointsToGpInterface(mudclient mc) {
		this.mc = mc;

		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;

		experienceConfig = new Panel(mc.getSurface(), 5);
		experienceConfigScroll = experienceConfig.addScrollingList(x + 95, y + 34, 160, height - 40, 20, 2, false);
	}

	public void reposition() {
		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;

		experienceConfig.reposition(experienceConfigScroll, x + 95, y + 34, 160, height - 40);
	}

	public void onRender(GraphicsController graphics) {
		reposition();

		drawExperienceConfig();

		if (selectSkillMenu) {
			drawSelectSkillMenu();
		}
	}

	private void drawExperienceConfig() {
		reposition();

		experienceConfig.handleMouse(mc.getMouseX(), mc.getMouseY(), mc.getMouseButtonDown(), mc.getLastMouseDown());

		boolean closeHover = UiSkin.hit(InterfaceChrome.closeX(x, width), InterfaceChrome.closeY(y - 50),
			InterfaceChrome.CLOSE_SIZE, InterfaceChrome.CLOSE_SIZE, mc.getMouseX(), mc.getMouseY());
		InterfaceChrome.window(mc.getSurface(), x, y - 50, width, height, "Exchange Points to Gp", closeHover);
		if (closeHover && mc.getMouseClick() == 1) {
			if (!selectSkillMenu) {
				mc.packetHandler.getClientStream().newPacket(212);
				mc.packetHandler.getClientStream().finishPacket();
				setVisible(false);
			}
			mc.setMouseClick(0);
		}

		this.drawString(Config.S_OPENPK_POINTS_TO_GP_RATIO + " Points = 1 Gp", x + 10, y - 18, 3, UiSkin.TEXT_BODY);
		this.drawString("Points: " + mc.getPoints(), x + 10, y + 20, 3, UiSkin.GOLD_HOT);
		this.drawButton(x + 198, y - 20, 85, 28, "Exchange", 3, false, new ButtonHandler() {
			@Override
			void handle() {
				mc.showItemModX(InputXPrompt.pointsToGp, InputXAction.POINTS_TO_GP, true);
			}
		});
	}

	private void drawSelectSkillMenu() {
		reposition();

		UiSkin.glassPanel(mc.getSurface(), x + 90, y + 5, 166, height - 10, UiSkin.A_GLASS_TEXT);

		this.drawStringCentered("Select a skill to track", x - 12, y + 22, 3, UiSkin.GOLD_HEADER);

		mc.getSurface().drawLineHoriz(x + 90, y + 30, 166, UiSkin.GOLD_LINE);

		boolean subCloseHover = UiSkin.hit(x + 237, y + 6, 18, 18, mc.getMouseX(), mc.getMouseY());
		UiSkin.closeButton(mc.getSurface(), x + 237, y + 6, 18, subCloseHover);
		if (subCloseHover && mc.getMouseClick() == 1) {
			experienceConfig.resetScrollIndex(experienceConfigScroll);
			selectSkillMenu = false;
			mc.setMouseClick(0);
		}

		String[] skillNames = mc.getSkillNamesLong();

		experienceConfig.clearList(experienceConfigScroll);

		for (int i = 0; i < mudclient.skillCount; i++) {
			experienceConfig.setListEntry(experienceConfigScroll, i, "@bod@" + skillNames[i], 0, (String) null, (String) null);
		}

		int index = experienceConfig.getControlSelectedListIndex(experienceConfigScroll);
		if (index >= 0 && mc.mouseButtonClick == 1) {
			mc.selectedSkill = index;
			experienceConfig.resetScrollIndex(experienceConfigScroll);
			selectSkillMenu = false;
			mc.setMouseClick(0);
		}

		experienceConfig.drawPanel();

		Config.C_EXPERIENCE_COUNTER_MODE = 2;
	}

	private void drawString(String str, int x, int y, int font, int color) {
		mc.getSurface().drawShadowText(str, x, y, color, font, false);
	}

	private void drawStringCentered(String str, int x, int y, int font, int color) {
		int stringWid = mc.getSurface().stringWidth(font, str);
		drawString(str, x + (width / 2) - (stringWid / 2), y, font, color);
	}

	private void drawButton(int x, int y, int width, int height, String text, int font, boolean checked, ButtonHandler handler) {
		// Submenu-open suppression preserved from the legacy hit-test: no hover, no clicks.
		int mouseX = selectSkillMenu ? -1 : mc.getMouseX();
		int mouseY = selectSkillMenu ? -1 : mc.getMouseY();
		boolean hover = InterfaceChrome.button(mc.getSurface(), x, y, width, height, text, font, checked, false,
			mouseX, mouseY);
		if (hover && mc.getMouseClick() == 1) {
			handler.handle();
			mc.setMouseClick(0);
		}
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}
}
