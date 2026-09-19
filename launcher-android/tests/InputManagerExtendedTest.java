package com.kiver.fireretro;

public final class InputManagerExtendedTest {
    public static void main(String[] args) {
        InputManager manager = new InputManager();
        assertEquals(GameAction.MENU, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_SELECT), "SELECT opens menu");
        assertEquals(GameAction.PAGE_PREVIOUS, manager.mapKeyToAction(InputManager.KEYCODE_PAGE_UP), "PAGE_UP pages back");
        assertEquals(GameAction.PAGE_NEXT, manager.mapKeyToAction(InputManager.KEYCODE_PAGE_DOWN), "PAGE_DOWN pages forward");
        assertEquals(GameAction.ACCEPT, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_A), "A still accepts in default layout");
        assertEquals(GameAction.MENU, manager.mapKeyToAction(InputManager.KEYCODE_MENU), "MENU unchanged");
        assertEquals(null, manager.mapKeyToAction(InputManager.KEYCODE_BUTTON_L2), "L2 stays unmapped (MainActivity fast-scroll until migration)");
        assertEquals(null, manager.mapKeyToAction(999999), "Unknown key stays null");
        System.out.println("PASS: InputManager extended tests passed");
    }

    private static void assertEquals(Object expected, Object actual, String scenario) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(scenario + ": expected <" + expected + ">, got <" + actual + ">");
        }
    }
}
