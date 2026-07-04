package com.openrsc.interfaces.misc;

import orsc.Config;
import orsc.graphics.gui.Panel;
import orsc.graphics.gui.UiSkin;
import orsc.graphics.two.GraphicsController;
import orsc.mudclient;


public final class ExperienceConfigInterface {
	public Panel experienceConfig;
	public int experienceConfigScroll;
	public boolean selectSkillMenu = false;
	int width = 350, height = 225;
	private boolean visible = false;
	private mudclient mc;
	private int x, y;

	public ExperienceConfigInterface(mudclient mc) {
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

		boolean closeHover = UiSkin.hit(InterfaceChrome.closeX(x, width), InterfaceChrome.closeY(y),
			InterfaceChrome.CLOSE_SIZE, InterfaceChrome.CLOSE_SIZE, mc.getMouseX(), mc.getMouseY());
		InterfaceChrome.window(mc.getSurface(), x, y, width, height, "Experience Config Menu", closeHover);
		if (closeHover && mc.getMouseClick() == 1) {
			if (!selectSkillMenu) {
				setVisible(false);
			}
			mc.setMouseClick(0);
		}

		experienceConfig.clearList(experienceConfigScroll);

		this.drawString("Mode: ", x + 10, y + 60, 3, UiSkin.GOLD_HEADER);
		this.drawButton(x + 105, y + 45, 50, 20, "Recent", 2, Config.C_EXPERIENCE_COUNTER_MODE == 0 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				mc.selectedSkill = -1;
				Config.C_EXPERIENCE_COUNTER_MODE = 0;
			}
		});
		this.drawButton(x + 175, y + 45, 50, 20, "Total", 2, Config.C_EXPERIENCE_COUNTER_MODE == 1 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				mc.selectedSkill = -1;
				Config.C_EXPERIENCE_COUNTER_MODE = 1;
			}
		});
		this.drawButton(x + 245, y + 45, 50, 20, "Select", 2, Config.C_EXPERIENCE_COUNTER_MODE == 2 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				selectSkillMenu = true;
			}
		});

		this.drawString("Show: ", x + 10, y + 90, 3, UiSkin.GOLD_HEADER);
		this.drawButton(x + 105, y + 75, 50, 20, "Never", 2, Config.C_EXPERIENCE_COUNTER == 0 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_COUNTER = 0;
			}
		});
		this.drawButton(x + 175, y + 75, 50, 20, "Recent", 2, Config.C_EXPERIENCE_COUNTER == 1 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_COUNTER = 1;
			}
		});
		this.drawButton(x + 245, y + 75, 50, 20, "Always", 2, Config.C_EXPERIENCE_COUNTER == 2 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_COUNTER = 2;
			}
		});

		this.drawString("Color:", x + 10, y + 120, 3, UiSkin.GOLD_HEADER);
		this.drawButton(x + 65, y + 105, 50, 20, "White", 2, Config.C_EXPERIENCE_COUNTER_COLOR == 0 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_COUNTER_COLOR = 0;
			}
		});
		this.drawButton(x + 120, y + 105, 50, 20, "@yel@Yellow", 2, Config.C_EXPERIENCE_COUNTER_COLOR == 1 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_COUNTER_COLOR = 1;
			}
		});
		this.drawButton(x + 175, y + 105, 50, 20, "@red@Red", 2, Config.C_EXPERIENCE_COUNTER_COLOR == 2 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_COUNTER_COLOR = 2;
			}
		});
		this.drawButton(x + 230, y + 105, 50, 20, "@blu@Blue", 2, Config.C_EXPERIENCE_COUNTER_COLOR == 3 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_COUNTER_COLOR = 3;
			}
		});
		this.drawButton(x + 285, y + 105, 50, 20, "@gre@Green", 2, Config.C_EXPERIENCE_COUNTER_COLOR == 4 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_COUNTER_COLOR = 4;
			}
		});
		this.drawButton(x + 145, y + 135, 50, 20, "@pin@Pink", 2, Config.C_EXPERIENCE_COUNTER_COLOR == 5 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_COUNTER_COLOR = 5;
			}
		});
		this.drawButton(x + 205, y + 135, 50, 20, "@mag@Magenta", 2, Config.C_EXPERIENCE_COUNTER_COLOR == 6 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_COUNTER_COLOR = 6;
			}
		});

		this.drawString("Speed: ", x + 10, y + 180, 3, UiSkin.GOLD_HEADER);
		this.drawButton(x + 105, y + 165, 50, 20, "Slow", 2, Config.C_EXPERIENCE_DROP_SPEED == 0 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_DROP_SPEED = 0;
			}
		});
		this.drawButton(x + 175, y + 165, 50, 20, "Medium", 2, Config.C_EXPERIENCE_DROP_SPEED == 1 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_DROP_SPEED = 1;
			}
		});
		this.drawButton(x + 245, y + 165, 50, 20, "Fast", 2, Config.C_EXPERIENCE_DROP_SPEED == 2 ? true : false, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_DROP_SPEED = 2;
			}
		});

		this.drawString("Controls: ", x + 10, y + 210, 3, UiSkin.GOLD_HEADER);
		this.drawButton(x + 135, y + 195, 50, 20, "Reset", 2, false, new ButtonHandler() {
			@Override
			void handle() {
				long time = System.currentTimeMillis();
				mc.totalXpGainedStartTime = time;
				mc.setPlayerXpGainedTotal(0);
				if (mc.getRecentSkill() >= 0) {
					mc.setPlayerStatXpGained(mc.getRecentSkill(), 0);
					mc.setXpGainedStartTime(mc.getRecentSkill(), time);
				}
				if (mc.selectedSkill >= 0) {
					mc.setPlayerStatXpGained(mc.selectedSkill, 0);
					mc.setXpGainedStartTime(mc.selectedSkill, time);
				}

			}
		});
		this.drawButton(x + 200, y + 195, 60, 20, "Submenu", 2, Config.C_EXPERIENCE_CONFIG_SUBMENU, new ButtonHandler() {
			@Override
			void handle() {
				Config.C_EXPERIENCE_CONFIG_SUBMENU = Config.C_EXPERIENCE_CONFIG_SUBMENU == false ? true : false;
			}
		});

		if (selectSkillMenu)
			mc.getSurface().drawBoxAlpha(x, y, width, height, 0, 192);
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
