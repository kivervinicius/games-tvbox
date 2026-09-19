package com.kiver.fireretro;

public final class WindowSizeClassTest {
    public static void main(String[] args) {
        assertEquals(WindowSizeClass.COMPACT, WindowSizeClass.widthClass(360), "Phone width is compact");
        assertEquals(WindowSizeClass.MEDIUM, WindowSizeClass.widthClass(600), "600dp starts medium");
        assertEquals(WindowSizeClass.MEDIUM, WindowSizeClass.widthClass(839), "839dp stays medium");
        assertEquals(WindowSizeClass.EXPANDED, WindowSizeClass.widthClass(840), "840dp starts expanded");
        assertEquals(WindowSizeClass.COMPACT, WindowSizeClass.heightClass(479), "Short height is compact");
        assertEquals(WindowSizeClass.MEDIUM, WindowSizeClass.heightClass(480), "480dp starts medium");
        assertEquals(WindowSizeClass.EXPANDED, WindowSizeClass.heightClass(900), "900dp starts expanded");
        assertTrue(!WindowSizeClass.supportsDetailPane(600), "Medium has no detail pane");
        assertTrue(WindowSizeClass.supportsDetailPane(1280), "Expanded supports rail+grid+detail");
        System.out.println("PASS: WindowSizeClass tests passed");
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
