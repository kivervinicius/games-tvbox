package com.kiver.fireretro;

import java.util.HashMap;
import java.util.Map;

public final class InputManager {
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_ESCAPE = 111;
    public static final int KEYCODE_MENU = 82;
    public static final int KEYCODE_SEARCH = 84;
    public static final int KEYCODE_BUTTON_A = 96;
    public static final int KEYCODE_BUTTON_B = 97;
    public static final int KEYCODE_BUTTON_X = 99;
    public static final int KEYCODE_BUTTON_Y = 100;
    public static final int KEYCODE_BUTTON_L1 = 102;
    public static final int KEYCODE_BUTTON_R1 = 103;
    public static final int KEYCODE_BUTTON_L2 = 104;
    public static final int KEYCODE_BUTTON_R2 = 105;
    public static final int KEYCODE_BUTTON_START = 108;
    public static final int KEYCODE_BUTTON_SELECT = 109;

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;

    private final ControllerInputRouter router = new ControllerInputRouter();
    private boolean nintendoLayout = false;
    private final Map<Integer, GameAction> customMappings = new HashMap<>();

    public InputManager() { }

    public void setNintendoLayout(boolean nintendoLayout) {
        this.nintendoLayout = nintendoLayout;
    }

    public boolean isNintendoLayout() {
        return nintendoLayout;
    }

    public void setCustomMapping(int keyCode, GameAction action) {
        if (action == null) {
            customMappings.remove(keyCode);
        } else {
            customMappings.put(keyCode, action);
        }
    }

    public GameAction mapKeyToAction(int keyCode) {
        GameAction custom = customMappings.get(keyCode);
        if (custom != null) {
            return custom;
        }

        switch (keyCode) {
            case KEYCODE_DPAD_UP:
                return GameAction.UP;
            case KEYCODE_DPAD_DOWN:
                return GameAction.DOWN;
            case KEYCODE_DPAD_LEFT:
                return GameAction.LEFT;
            case KEYCODE_DPAD_RIGHT:
                return GameAction.RIGHT;

            case KEYCODE_DPAD_CENTER:
            case KEYCODE_ENTER:
                return GameAction.ACCEPT;

            case KEYCODE_BUTTON_A:
                return nintendoLayout ? GameAction.BACK : GameAction.ACCEPT;
            case KEYCODE_BUTTON_B:
                return nintendoLayout ? GameAction.ACCEPT : GameAction.BACK;

            case KEYCODE_BACK:
            case KEYCODE_ESCAPE:
                return GameAction.BACK;

            case KEYCODE_BUTTON_X:
                return GameAction.FAVORITE;

            case KEYCODE_BUTTON_Y:
            case KEYCODE_SEARCH:
                return GameAction.SEARCH;

            case KEYCODE_MENU:
                return GameAction.MENU;

            case KEYCODE_BUTTON_START:
                return GameAction.QUICK_SETTINGS;

            case KEYCODE_BUTTON_L1:
                return GameAction.TAB_PREV;
            case KEYCODE_BUTTON_R1:
                return GameAction.TAB_NEXT;

            default:
                return null;
        }
    }

    public GameAction onKeyEvent(int deviceId, int keyCode, int action, int repeatCount, long eventTimeMs) {
        GameAction gameAction = mapKeyToAction(keyCode);
        if (gameAction == null) return null;

        if (action == ACTION_UP) {
            if (isDirectional(gameAction)) {
                router.shouldMove(deviceId, gameAction.ordinal(), ACTION_UP, 0, eventTimeMs);
            }
            return null;
        }

        if (action == ACTION_DOWN) {
            if (isDirectional(gameAction)) {
                boolean move = router.shouldMove(deviceId, gameAction.ordinal(), ACTION_DOWN, repeatCount, eventTimeMs);
                return move ? gameAction : null;
            }
            if (repeatCount == 0) {
                return gameAction;
            }
        }
        return null;
    }

    public GameAction onSwipe(float deltaX, float deltaY, float minSwipeDistancePx) {
        float absX = Math.abs(deltaX);
        float absY = Math.abs(deltaY);
        if (absX < minSwipeDistancePx && absY < minSwipeDistancePx) {
            return null;
        }
        if (absX > absY) {
            return deltaX > 0 ? GameAction.RIGHT : GameAction.LEFT;
        } else {
            return deltaY > 0 ? GameAction.DOWN : GameAction.UP;
        }
    }

    private static boolean isDirectional(GameAction action) {
        return action == GameAction.UP || action == GameAction.DOWN
                || action == GameAction.LEFT || action == GameAction.RIGHT;
    }
}
