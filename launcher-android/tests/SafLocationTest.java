package com.kiver.fireretro;

public final class SafLocationTest {
    public static void main(String[] args) {
        testTreeParsing();
        testInvalidInput();
        testLogicalPath();
        System.out.println("PASS: SafLocation tests passed");
    }

    private static void testTreeParsing() {
        SafLocation usb = SafLocation.parse(
                "content://com.android.externalstorage.documents/tree/1234-ABCD%3Aroms", "USB");
        assertTrue(usb.isValid(), "Encoded USB tree is valid");
        assertEquals("1234-ABCD", usb.getVolumeId(), "Volume decoded");
        assertEquals("USB", usb.getDisplayName(), "Display name kept");
        SafLocation primary = SafLocation.parse(
                "content://com.android.externalstorage.documents/tree/primary%3Aroms", null);
        assertTrue(primary.isValid(), "Primary tree is valid");
        assertEquals("primary", primary.getVolumeId(), "Primary volume");
        assertEquals("Armazenamento externo", primary.getDisplayName(), "Fallback display name");
    }

    private static void testInvalidInput() {
        assertTrue(!SafLocation.parse(null, "X").isValid(), "Null URI invalid, never throws");
        assertTrue(!SafLocation.parse("content://other/authority/doc/1", "X").isValid(), "Non-tree URI invalid");
        assertTrue(!SafLocation.isTreeUri("/sdcard/roms"), "Raw path is not a tree URI");
        assertTrue(SafLocation.isTreeUri("content://com.android.externalstorage.documents/tree/primary%3A"), "Tree URI detected");
    }

    private static void testLogicalPath() {
        SafLocation usb = SafLocation.parse(
                "content://com.android.externalstorage.documents/tree/1234-ABCD%3A", "USB");
        assertEquals("saf://1234-ABCD/snes/zelda.smc", usb.toLogicalPath("/snes/zelda.smc"), "Logical path strips slashes");
        assertEquals("saf://1234-ABCD/", usb.toLogicalPath(null), "Null relative maps to root");
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
