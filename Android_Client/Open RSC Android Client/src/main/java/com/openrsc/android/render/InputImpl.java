package com.openrsc.android.render;

import android.content.Context;
import android.media.AudioManager;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;

import java.util.Timer;
import java.util.TimerTask;

import orsc.Config;
import orsc.enumerations.MessageTab;
import orsc.enumerations.MessageType;
import orsc.graphics.two.Fonts;
import orsc.mudclient;
import orsc.osConfig;

public class InputImpl implements OnGestureListener, OnKeyListener, OnTouchListener, OnGenericMotionListener {

    private final mudclient mudclient;
    private final GestureDetector gestureDetector;
    private final AudioManager audioManager;

    public InputImpl(mudclient mudclient, View view) {
        this.mudclient = mudclient;
        this.view = view;
        gestureDetector = new GestureDetector(view.getContext(), this);
        audioManager = (AudioManager) view.getContext().getSystemService(Context.AUDIO_SERVICE);

        view.setOnTouchListener(this);
        view.setOnKeyListener(this);
        view.setOnGenericMotionListener(this);
    }

    private boolean isLongPress = false;
    private boolean longPressTriggered = false;
    private boolean worldMapTouchActive = false;
    private boolean worldMapTouchMoved = false;
    private int worldMapTouchStartX = 0;
    private int worldMapTouchStartY = 0;
    private final View view;
    private long lastScrollOrRotate;

    @Override
    public boolean onDown(MotionEvent e) {
        if (osConfig.C_HOLD_AND_CHOOSE)
            return false;

        setMousePosition(e);
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {
        if (osConfig.C_HOLD_AND_CHOOSE)
            return;
        setMousePosition(e);
        mudclient.currentMouseButtonDown = 2;
        mudclient.lastMouseButtonDown = mudclient.currentMouseButtonDown;
        mudclient.lastMouseAction = 0;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        if (osConfig.C_HOLD_AND_CHOOSE)
            return false;
        setMousePosition(e);
        mudclient.currentMouseButtonDown = 1;
        mudclient.lastMouseButtonDown = mudclient.currentMouseButtonDown;
        mudclient.recordAndroidTap(mudclient.mouseX, mudclient.mouseY);
        mudclient.lastMouseAction = 0;
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (mudclient.topMouseMenuVisible) {
            return true;
        }

        if (mudclient.worldMapPanel != null && mudclient.worldMapPanel.isVisible()) {
            setMousePosition(e2);
            mudclient.currentMouseButtonDown = 1;
            mudclient.lastMouseButtonDown = 0;
            mudclient.lastMouseAction = 0;
            lastScrollOrRotate = System.currentTimeMillis();
            return true;
        }

        if (distanceY > 1)
            distanceY = 1;
        if (distanceY < -1)
            distanceY = -1;

        lastScrollOrRotate = System.currentTimeMillis();

		int firstY = screenToClientY(e1.getY());
		int secondY = screenToClientY(e2.getY());
		boolean touchedMessagePanelArea = mudclient.getGameHeight() + 12 - Math.max(secondY, firstY) <= 130;

		boolean scrollableMessagePanel = mudclient.hasScroll(mudclient.messageTabSelected) && touchedMessagePanelArea;
		boolean mayBeScrollable = mudclient.showUiTab != 0;
		boolean verticalSwipe = Math.abs(e2.getY() - e1.getY()) >= 70; // some threshold to discard zoom when wanting to rotate only
		boolean zoomable = (!scrollableMessagePanel && !mayBeScrollable) || osConfig.C_SWIPE_TO_SCROLL_MODE == 0;

        // Disables zoom functions while visible
        boolean inScrollable = (Config.S_SPAWN_AUCTION_NPCS && mudclient.auctionHouse.isVisible() || mudclient.onlineList.isVisible() || Config.S_WANT_SKILL_MENUS && mudclient.skillGuideInterface.isVisible()
                || Config.S_WANT_QUEST_MENUS && mudclient.questGuideInterface.isVisible() || mudclient.experienceConfigInterface.isVisible()
                || mudclient.ironmanInterface.isVisible() || mudclient.achievementInterface.isVisible() || Config.S_WANT_SKILL_MENUS && mudclient.doSkillInterface.isVisible()
                || Config.S_ITEMS_ON_DEATH_MENU && mudclient.lostOnDeathInterface.isVisible() || mudclient.territorySignupInterface.isVisible())
                || mudclient.isShowDialogBank();

		if (verticalSwipe && !inScrollable && zoomable && (Config.S_ZOOM_VIEW_TOGGLE || mudclient.getLocalPlayer().isStaff())) {
			if (osConfig.C_SWIPE_TO_ZOOM_MODE != 0) {
				int dir = osConfig.C_SWIPE_TO_ZOOM_MODE == 2 ? -1 : 1;
				int zoomDistance = (int) (-distanceY * 5);
				int newZoom = osConfig.C_LAST_ZOOM + dir * zoomDistance;
				// Keep C_LAST_ZOOM aka the zoom increments on the range of [0, 255]
				if (newZoom >= 0 && newZoom <= 255) {
					osConfig.C_LAST_ZOOM = newZoom;
				}
			}
		} else if (!inScrollable && mudclient.cameraAllowPitchModification) {
			mudclient.cameraPitch = (mudclient.cameraPitch + (int) (-distanceY * 10)) & 1023;
		}

		if (!inScrollable && osConfig.C_SWIPE_TO_ROTATE_MODE != 0) {
			// camera set to auto does not like manual like rotation
			if (!mudclient.getOptionCameraModeAuto()) {
				int dir = osConfig.C_SWIPE_TO_ROTATE_MODE == 2 ? -1 : 1;
				float clientDist = distanceX / getClientScale() * 0.5F;
				mudclient.cameraRotation = (255 & mudclient.cameraRotation + (int) (dir * clientDist));
			} else {
				// swipe to right gives negative distanceX, to left positive
				int dir = osConfig.C_SWIPE_TO_ROTATE_MODE == 2 ? -1 : 1;
				boolean toLeft = dir * distanceX > 0;
				if (toLeft) {
					mudclient.keyLeft = true;
				} else {
					mudclient.keyRight = true;
				}
			}
		}
		if (inScrollable || !zoomable) {
			if (osConfig.C_SWIPE_TO_SCROLL_MODE != 0) {
				int dir = osConfig.C_SWIPE_TO_SCROLL_MODE == 2 ? -1 : 1;
				mudclient.runScroll((int) (dir * distanceY));
			}
		}

        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        if (!osConfig.C_HOLD_AND_CHOOSE) {
            return;
        }
        setMousePosition(e);
        if (triggerRightClick()) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

        return false;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (osConfig.C_VOLUME_FUNCTION == 0) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                mudclient.keyLeft = event.getAction() == KeyEvent.ACTION_DOWN;
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                mudclient.keyRight = event.getAction() == KeyEvent.ACTION_DOWN;
                return true;
            }
        }
        // If we are not volume to rotate, then we are volume to zoom...
        else if (osConfig.C_VOLUME_FUNCTION == 1) {
            if (Config.S_ZOOM_VIEW_TOGGLE) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    mudclient.keyUp = event.getAction() == KeyEvent.ACTION_DOWN;
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    mudclient.keyDown = event.getAction() == KeyEvent.ACTION_DOWN;
                    return true;
                }
            }
        }
        //
		else if (osConfig.C_VOLUME_FUNCTION == 2) {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND | AudioManager.FLAG_SHOW_UI);
				return true;
			}
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND | AudioManager.FLAG_SHOW_UI);
				return true;
			}
		}

		int key = event.getUnicodeChar();
		String chars = event.getCharacters();
		checkSpecialKeys(keyCode, chars);

        if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            return handleAndroidTextInput(chars);
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                key = 8;
            }
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                key = 10;
            }

            checkSpecialKeys(keyCode, chars);

            if (mudclient.worldMapPanel != null && mudclient.worldMapPanel.isSearchFocused()) {
                if (mudclient.handleAndroidSmokeWorldMapSearchKey((char) key, key)) {
                    return true;
                }
                mudclient.worldMapPanel.handleSearchKey((char) key, key);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_M && mudclient.openAndroidSmokeWorldMapFromInput()) {
                return true;
            }
            if ((keyCode == KeyEvent.KEYCODE_EQUALS || keyCode == KeyEvent.KEYCODE_PLUS)
                    && mudclient.zoomAndroidSmokeWorldMapFromInput()) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && mudclient.panAndroidSmokeWorldMapFromInput(80, 0)) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && mudclient.panAndroidSmokeWorldMapFromInput(-80, 0)) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && mudclient.panAndroidSmokeWorldMapFromInput(0, 55)) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && mudclient.panAndroidSmokeWorldMapFromInput(0, -55)) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_S && mudclient.focusAndroidSmokeWorldMapSearchFromInput()) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_O && mudclient.openAndroidSmokeSettingsFromInput()) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_1 && mudclient.toggleAndroidSmokeSettingsFromInput(0)) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_2 && mudclient.toggleAndroidSmokeSettingsFromInput(1)) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_3 && mudclient.toggleAndroidSmokeSettingsFromInput(2)) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_G && mudclient.dropAndroidSmokeGroundLootFromInput()) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_B && mudclient.startAndroidSmokeWildernessTargetFromInput()) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_C && mudclient.stopAndroidSmokeWildernessTargetFromInput()) {
                return true;
            }

            if (key == 0 && handleAndroidTextInput(chars)) {
                return true;
            }
            handleAndroidKeyInput(key);
            return true;
        }
        return false;
    }

    private boolean handleAndroidTextInput(String chars) {
        if (chars == null || chars.length() == 0) {
            return false;
        }
        for (int i = 0; i < chars.length(); i++) {
            handleAndroidKeyInput(chars.charAt(i));
        }
        return true;
    }

    private void handleAndroidKeyInput(int key) {
        if (key == 0) {
            return;
        }

        boolean hitInputFilter = false;

        for (int var5 = 0; var5 < Fonts.inputFilterChars.length(); ++var5) {
            if (Fonts.inputFilterChars.charAt(var5) == key) {
                hitInputFilter = true;
                break;
            }
        }

        mudclient.handleKeyPress((byte) 126, key);
        if (hitInputFilter && mudclient.inputTextCurrent.length() < 20) {
            mudclient.inputTextCurrent = mudclient.inputTextCurrent + (char) key;
        }

        if (hitInputFilter && mudclient.chatMessageInput.length() < 80 && !mudclient.getIsSleeping()) {
            mudclient.chatMessageInput = mudclient.chatMessageInput + (char) key;
        }

        if (key == '\b' && mudclient.inputTextCurrent.length() > 0) {
            mudclient.inputTextCurrent = mudclient.inputTextCurrent.substring(0,
                    mudclient.inputTextCurrent.length() - 1);
        }

        if (key == '\b' && mudclient.chatMessageInput.length() > 0) {
            mudclient.chatMessageInput = mudclient.chatMessageInput.substring(0,
                    mudclient.chatMessageInput.length() - 1);
        }

        if (key == 10 || key == 13) {
            mudclient.inputTextFinal = mudclient.inputTextCurrent;
            mudclient.chatMessageInputCommit = mudclient.chatMessageInput;
        }
    }

    public void checkSpecialKeys(int keyCode, String chars) {
		if (keyCode == KeyEvent.KEYCODE_F1 || (chars != null && chars.equalsIgnoreCase("¹"))) {
			mudclient.interlace = !mudclient.interlace;
		}

		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || (chars != null && chars.equalsIgnoreCase("←"))) {
			mudclient.keyLeft = true;
			new Timer().schedule(new TimerTask() {
				// debounce to avoid infinite rotation
				@Override
				public void run() {
					mudclient.keyLeft = false;
				}
			}, 100);
		}

		if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || (chars != null && chars.equalsIgnoreCase("→"))) {
			mudclient.keyRight = true;
			new Timer().schedule(new TimerTask() {
				// debounce to avoid infinite rotation
				@Override
				public void run() {
					mudclient.keyRight = false;
				}
			}, 100);
		}

		if (keyCode == KeyEvent.KEYCODE_DPAD_UP || (chars != null && chars.equalsIgnoreCase("↑"))) {
			mudclient.keyUp = true;
			new Timer().schedule(new TimerTask() {
				// debounce to avoid infinite zooming
				@Override
				public void run() {
					mudclient.keyUp = false;
				}
			}, 100);
		}

		if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || (chars != null && chars.equalsIgnoreCase("↓"))) {
			mudclient.keyDown = true;
			new Timer().schedule(new TimerTask() {
				// debounce to avoid infinite zooming
				@Override
				public void run() {
					mudclient.keyDown = false;
				}
			}, 100);
		}

		if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
			mudclient.pageUp = true;
			new Timer().schedule(new TimerTask() {
				// debounce to avoid infinite scrolling
				@Override
				public void run() {
					mudclient.pageUp = false;
				}
			}, 100);
		}

		if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
			mudclient.pageDown = true;
			new Timer().schedule(new TimerTask() {
				// debounce to avoid infinite scrolling
				@Override
				public void run() {
					mudclient.pageDown = false;
				}
			}, 100);
		}
	}

	@Override
	public boolean onGenericMotion(View v, MotionEvent e) {
		if (!isMouseEvent(e)) {
			return false;
		}

		setMousePosition(e);
		mudclient.lastMouseAction = 0;

		int action = e.getActionMasked();
		if (action == MotionEvent.ACTION_BUTTON_PRESS && isSecondaryMouseAction(e)) {
			return triggerRightClick();
		}

		if (action == MotionEvent.ACTION_BUTTON_RELEASE && isSecondaryMouseAction(e)) {
			clearRightClickState();
			return true;
		}

		return false;
	}

    public boolean onTouch(View v, MotionEvent e) {
        setMousePosition(e);
        mudclient.lastMouseAction = 0;

        if (isMouseEvent(e) && isSecondaryMouseAction(e)) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_BUTTON_RELEASE) {
                clearRightClickState();
                return true;
            }
            return triggerRightClick();
        }

        if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                && (mudclient.closeWelcomeDialogAt(mudclient.mouseX, mudclient.mouseY)
                || mudclient.closeServerMessageDialogAt(mudclient.mouseX, mudclient.mouseY)
                || mudclient.closeWildWarningDialogAt(mudclient.mouseX, mudclient.mouseY))) {
            return true;
        }

        if (handleWorldMapTouch(e)) {
            return true;
        }

        if (!gestureDetector.onTouchEvent(e)) {
            if (osConfig.C_HOLD_AND_CHOOSE) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_UP:
                        boolean wasLongPress = longPressTriggered;
                        isLongPress = false;
                        longPressTriggered = false;
                        if (wasLongPress) {
                            mudclient.lastMouseButtonDown = mudclient.currentMouseButtonDown = 0;
                            return true;
                        }
                        if (mudclient.topMouseMenuVisible) {
                            int width = mudclient.menuCommon.getWidth();
                            int height = mudclient.menuCommon.getHeight();
                            if (mudclient.menuX - 10 <= mudclient.mouseX && mudclient.menuY - 10 <= mudclient.mouseY
                                    && width + mudclient.menuX + 10 >= mudclient.mouseX
                                    && mudclient.mouseY <= 10 + mudclient.menuY + height) {
                                mudclient.lastMouseButtonDown = mudclient.currentMouseButtonDown = 1;
                                return true;
                            } else {
                                mudclient.topMouseMenuVisible = false;
                                return true;
                            }
                        }
                        if (System.currentTimeMillis() - lastScrollOrRotate < 100) {
                            return true;
                        }
                        mudclient.recordAndroidTap(mudclient.mouseX, mudclient.mouseY);
                        mudclient.lastMouseButtonDown = mudclient.currentMouseButtonDown = 1;
                        break;
                    case MotionEvent.ACTION_DOWN:
                        mudclient.lastMouseButtonDown = mudclient.currentMouseButtonDown = 0;
                        longPressTriggered = false;
                        if (!isLongPress) {
                            isLongPress = true;
                            view.postDelayed(() -> {
                                if (System.currentTimeMillis() - lastScrollOrRotate < 100) {
                                    return;
                                }
                                if (isLongPress) {
                                    triggerRightClick();
                                }
                            }, osConfig.C_LONG_PRESS_TIMER * 50L);
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (e.getDownTime() > 0) {
                            mudclient.currentMouseButtonDown = 1;
                        }
                        break;
                }
            }
        }

        return false;
    }

	private boolean handleWorldMapTouch(MotionEvent e) {
		if (mudclient.worldMapPanel == null || !mudclient.worldMapPanel.isVisible()) {
			worldMapTouchActive = false;
			return false;
		}

		int action = e.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN) {
			worldMapTouchActive = mudclient.worldMapPanel.containsWindow(mudclient.mouseX, mudclient.mouseY);
			worldMapTouchMoved = false;
			worldMapTouchStartX = mudclient.mouseX;
			worldMapTouchStartY = mudclient.mouseY;
		}
		if (!worldMapTouchActive) {
			return false;
		}

		if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
			if (worldMapTouchMoved || action == MotionEvent.ACTION_CANCEL) {
				mudclient.currentMouseButtonDown = 0;
			} else {
				mudclient.currentMouseButtonDown = 1;
				view.postDelayed(() -> {
					if (mudclient.worldMapPanel == null || !mudclient.worldMapPanel.isVisible()) {
						mudclient.currentMouseButtonDown = 0;
						return;
					}
					mudclient.currentMouseButtonDown = 0;
				}, 90L);
			}
			mudclient.lastMouseButtonDown = 0;
			worldMapTouchActive = false;
			return true;
		}

		if (Math.abs(mudclient.mouseX - worldMapTouchStartX) > 3
			|| Math.abs(mudclient.mouseY - worldMapTouchStartY) > 3) {
			worldMapTouchMoved = true;
		}
		mudclient.currentMouseButtonDown = 1;
		mudclient.lastMouseButtonDown = 0;
		lastScrollOrRotate = System.currentTimeMillis();
		return true;
	}

	private boolean triggerRightClick() {
		if (longPressTriggered || mudclient.topMouseMenuVisible) {
			return false;
		}
		longPressTriggered = true;
		mudclient.lastMouseAction = 0;
		mudclient.lastMouseButtonDown = mudclient.currentMouseButtonDown = 2;
		return true;
	}

	private void clearRightClickState() {
		isLongPress = false;
		longPressTriggered = false;
		mudclient.lastMouseButtonDown = mudclient.currentMouseButtonDown = 0;
	}

	private boolean isMouseEvent(MotionEvent e) {
		return (e.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
	}

	private boolean isSecondaryMouseAction(MotionEvent e) {
		return (e.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0
			|| e.getActionButton() == MotionEvent.BUTTON_SECONDARY;
	}

    private float getHeight() {
        return view.getHeight();
    }

    private float getWidth() {
        return view.getWidth();
    }

	private void setMousePosition(MotionEvent e) {
		mudclient.mouseX = screenToClientX(e.getX());
		mudclient.mouseY = screenToClientY(e.getY());
	}

	private int screenToClientX(float screenX) {
		float clientX = (screenX - getClientOffsetX()) / getClientScale();
		return clamp(Math.round(clientX), 0, mudclient.getGameWidth() - 1);
	}

	private int screenToClientY(float screenY) {
		float clientY = (screenY - getClientOffsetY()) / getClientScale();
		return clamp(Math.round(clientY), 0, mudclient.getGameHeight() + 11);
	}

	private float getClientScale() {
		float gameWidth = Math.max(1, mudclient.getGameWidth());
		float gameHeight = Math.max(1, mudclient.getGameHeight() + 12);
		return Math.max(0.01f, Math.min(getWidth() / gameWidth, getHeight() / gameHeight));
	}

	private float getClientOffsetX() {
		return (getWidth() - mudclient.getGameWidth() * getClientScale()) / 2.0f;
	}

	private float getClientOffsetY() {
		return (getHeight() - (mudclient.getGameHeight() + 12) * getClientScale()) / 2.0f;
	}

	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
