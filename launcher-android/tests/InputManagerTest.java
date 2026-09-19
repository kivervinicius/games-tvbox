package com.kiver.fireretro;

public final class InputManagerTest {
    public static void main(String[] args) {
        testStandardMapping();
        testNintendoLayoutMapping();
        testCustomMapping();
        testRepeatHandling();
        testSwipeGestures();
        System.out.println("PASS: InputManager tests passed");
    }

    private static void testStandardMapping() {
        InputManager manager = new InputManager();
        assertEquals(GameAction.UP, manager.mapKeyToAction(InputManager.KEYCODE_DPAD_UP), "DPAD_UP maps to UP");
        assertEquals(GameAction.DOWN, manager.mapKeyToAction(InputManager.KEYCODE_DPAD_DOWN), "DPAD_DOWN maps to DOWN");
        assertEquals(GameAction.LEFT, manager.mapKeyToAction(InputManager.KEYCODE_DPAD_LEFT), "DPAD_LEFT maps to LEFT");
        assertEquals(GameAction.RIGHT, manager.mapKeyToAction(InputManager.KEYCODE_DPAD_RIGHT), "DPAD_RIGHT maps to RIGHT");
        assertEquals(GameAction.ACCEPT, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_A), "Standard BUTTON_A is ACCEPT");
        assertEquals(GameAction.BACK, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_B), "Standard BUTTON_B is BACK");
        assertEquals(GameAction.FAVORITE, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_X), "BUTTON_X is FAVORITE");
        assertEquals(GameAction.SEARCH, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_Y), "BUTTON_Y is SEARCH");
        assertEquals(GameAction.QUICK_SETTINGS, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_START), "BUTTON_START is QUICK_SETTINGS");
        assertEquals(GameAction.TAB_PREV, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_L1), "BUTTON_L1 is TAB_PREV");
        assertEquals(GameAction.TAB_NEXT, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_R1), "BUTTON_R1 is TAB_NEXT");
    }

    private static void testNintendoLayoutMapping() {
        InputManager manager = new InputManager();
        manager.setNintendoLayout(true);
        assertEquals(GameAction.BACK, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_A), "Nintendo layout BUTTON_A is BACK");
        assertEquals(GameAction.ACCEPT, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_B), "Nintendo layout BUTTON_B is ACCEPT");
    }

    private static void testCustomMapping() {
        InputManager manager = new InputManager();
        manager.setCustomMapping(InputManager.KEYCODE_BUTTON_L2, GameAction.MENU);
        assertEquals(GameAction.MENU, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_L2), "Custom mapping overrides default");
        manager.setCustomMapping(InputManager.KEYCODE_BUTTON_L2, null);
        assertEquals(null, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_L2), "Cleared mapping returns null");
    }

    private static void testRepeatHandling() {
        InputManager manager = new InputManager();
        int dev = 1;
        long t = 1000L;

        // First press: accepted
        GameAction a1 = manager.onKeyEvent(dev, InputManager.KEYCODE_DPAD_RIGHT, InputManager.ACTION_DOWN, 0, t);
        assertEquals(GameAction.RIGHT, a1, "First press accepted");

        // Fast repeat before HOLD_DELAY_MS (450ms): rejected
        GameAction a2 = manager.onKeyEvent(dev, InputManager.KEYCODE_DPAD_RIGHT, InputManager.ACTION_DOWN, 1, t + 100);
        assertEquals(null, a2, "Fast repeat rejected");

        // Repeat after HOLD_DELAY_MS: accepted
        GameAction a3 = manager.onKeyEvent(dev, InputManager.KEYCODE_DPAD_RIGHT, InputManager.ACTION_DOWN, 2, t + 500);
        assertEquals(GameAction.RIGHT, a3, "Repeat after hold delay accepted");

        // Release
        GameAction a4 = manager.onKeyEvent(dev, InputManager.KEYCODE_DPAD_RIGHT, InputManager.ACTION_UP, 0, t + 550);
        assertEquals(null, a4, "Key release produces null action");
    }

    private static void testSwipeGestures() {
        InputManager manager = new InputManager();
        float threshold = 50.0f;

        assertEquals(null, manager.onSwipe(10.0f, -5.0f, threshold), "Sub-threshold swipe ignored");
        assertEquals(GameAction.RIGHT, manager.onSwipe(100.0f, 10.0f, threshold), "Swipe right produces RIGHT");
        assertEquals(GameAction.LEFT, manager.onSwipe(-100.0f, 10.0f, threshold), "Swipe left produces LEFT");
        assertEquals(GameAction.UP, manager.onSwipe(10.0f, -100.0f, threshold), "Swipe up produces UP");
        assertEquals(GameAction.DOWN, manager.onSwipe(10.0f, 100.0f, threshold), "Swipe down produces DOWN");
    }

    private static void assertEquals(Object expected, Object actual, String scenario) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(scenario + ": expected <" + expected + ">, got <" + actual + ">");
        }
    }
}
