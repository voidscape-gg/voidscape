package com.openrsc.interfaces.misc;

import orsc.graphics.gui.Panel;
import orsc.graphics.gui.UiSkin;
import orsc.mudclient;


public final class TerritorySignupInterface {
	public Panel territorySignup;
	int width = 250;
	int height = 200;
	int autoHeight = 0;
	int index = 0;
	int trackY = 0;
	private boolean visible = false;
	private mudclient mc;
	private int x, y;

	public TerritorySignupInterface(mudclient mc) {
		this.mc = mc;

		territorySignup = new Panel(mc.getSurface(), 15);

		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;
	}

	public void reposition() {
		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;
	}

	public void onRender() {
		reposition();

		int x = (mc.getGameWidth() - width) / 2;
		int y = (mc.getGameHeight() - height) / 2;

		territorySignup.handleMouse(mc.getMouseX(), mc.getMouseY(), mc.getMouseButtonDown(), mc.getLastMouseDown());

		int panelHeight = (autoHeight - y > 200) ? height : (autoHeight - y);
		boolean closeHover = UiSkin.hit(InterfaceChrome.closeX(x, width), InterfaceChrome.closeY(y),
			InterfaceChrome.CLOSE_SIZE, InterfaceChrome.CLOSE_SIZE, mc.getMouseX(), mc.getMouseY());
		InterfaceChrome.window(mc.getSurface(), x, y, width, panelHeight, "Territory Signup", closeHover);
		if (closeHover && mc.getMouseClick() == 1) {
			setVisible(false);
			mc.setMouseClick(0);
		}

		trackY = y + 55;

		drawString("Time until war begins: ", x + 8, trackY, 3, UiSkin.TEXT_BODY);
		trackY += 15;

		// TODO - add check to see if player is signed up
		// if (checkSignup() == true) {
		if (false) {
			this.drawButton(x + 75, trackY, 100, 30, "Drop out", 4, false, new ButtonHandler() {
				@Override
				void handle() {
					// TODO - add handler to drop player from territory
				}
			});
		} else {
			this.drawButton(x + 75, trackY, 100, 30, "Signup", 4, false, new ButtonHandler() {
				@Override
				void handle() {
					// TODO - add handler to sign player up to territory
				}
			});
		}
		trackY += 45;

		this.drawButton(x + 75, trackY, 100, 30, "Switch teams", 4, false, new ButtonHandler() {
			@Override
			void handle() {
				// TODO - add handler to switch teams
			}
		});
		trackY += 45;

		autoHeight = trackY;

		territorySignup.drawPanel();
	}

	public void drawString(String str, int x, int y, int font, int color) {
		mc.getSurface().drawShadowText(str, x, y, color, font, false);
	}

	public void drawStringCentered(String str, int x, int y, int font, int color) {
		int stringWid = mc.getSurface().stringWidth(font, str);
		drawString(str, x + (width / 2) - (stringWid / 2), y, font, color);
	}

	private void drawButton(int x, int y, int width, int height, String text, int font, boolean checked, ButtonHandler handler) {
		boolean hover = InterfaceChrome.button(mc.getSurface(), x, y, width, height, text, font, checked, false,
			mc.getMouseX(), mc.getMouseY());
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
