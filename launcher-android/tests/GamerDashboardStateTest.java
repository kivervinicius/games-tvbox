package com.kiver.fireretro;

public final class GamerDashboardStateTest {
    public static void main(String[] args) {
        testBatteryFormatting();
        testStorageFormatting();
        testControllerAndLayout();
        testTouchFallbackEvaluation();
        System.out.println("PASS: GamerDashboardState tests passed");
    }

    private static void testBatteryFormatting() {
        GamerDashboardState state = new GamerDashboardState();
        state.setBattery(85, false);
        assertEquals("85%", state.formatBattery(), "Battery discharge display");
        state.setBattery(92, true);
        assertEquals("92% (Carregando)", state.formatBattery(), "Battery charge display");
    }

    private static void testStorageFormatting() {
        GamerDashboardState state = new GamerDashboardState();
        state.setAvailableStorageBytes(5L * 1024L * 1024L * 1024L);
        assertEquals("5.0 GB livres", state.formatStorage(), "GB storage formatting");
        state.setAvailableStorageBytes(450L * 1024L * 1024L);
        assertEquals("450 MB livres", state.formatStorage(), "MB storage formatting");
    }

    private static void testControllerAndLayout() {
        GamerDashboardState state = new GamerDashboardState();
        state.setController(true, "Xbox Wireless Controller");
        assertEquals("Xbox Wireless Controller", state.getControllerName(), "Connected controller name");
        assertTrue(state.isControllerConnected(), "Controller is connected");

        assertTrue(!state.isNintendoLayout(), "Default layout is standard");
        state.toggleNintendoLayout();
        assertTrue(state.isNintendoLayout(), "Layout toggled to Nintendo");
    }

    private static void testTouchFallbackEvaluation() {
        assertTrue(GamerDashboardState.shouldEnableTouchFallback(true, false),
                "Touch fallback enabled when touchscreen available and no gamepad connected");
        assertTrue(!GamerDashboardState.shouldEnableTouchFallback(true, true),
                "Touch fallback disabled when gamepad is connected");
        assertTrue(!GamerDashboardState.shouldEnableTouchFallback(false, false),
                "Touch fallback disabled when device has no touchscreen (TV)");
    }

    private static void assertEquals(Object expected, Object actual, String scenario) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(scenario + ": expected <" + expected + ">, got <" + actual + ">");
        }
    }

    private static void assertTrue(boolean val, String scenario) {
        if (!val) throw new AssertionError(scenario);
    }
}
