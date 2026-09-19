package com.kiver.fireretro;

public final class DeviceProfileTest {
    public static void main(String[] args) {
        testFireTvInference();
        testTclTvInference();
        testTabletAndPhoneInference();
        testGamerInference();
        System.out.println("PASS: DeviceProfile tests passed");
    }

    private static void testFireTvInference() {
        DeviceProfile fireProfile = DeviceProfile.inferProfile("Amazon", "AFTMM", 25, "armeabi-v7a", true, false, false);
        assertEquals(DeviceType.FIRE_TV, fireProfile.getDeviceType(), "Amazon brand infers FIRE_TV");
        assertTrue(fireProfile.isTelevision(), "Fire TV is television");
        assertTrue(fireProfile.isLandscapeOnly(), "Fire TV is landscape only");
        assertTrue(!fireProfile.hasTouchscreen(), "Fire TV has no touchscreen");
        assertEquals(48, fireProfile.getPreferredSafeZoneMarginDp(), "Fire TV safe zone margin is 48dp");
    }

    private static void testTclTvInference() {
        DeviceProfile tclProfile = DeviceProfile.inferProfile("TCL", "BeyondTV", 28, "armeabi-v7a", true, false, false);
        assertEquals(DeviceType.ANDROID_TV_TCL, tclProfile.getDeviceType(), "TCL TV infers ANDROID_TV_TCL");
        assertTrue(tclProfile.isTelevision(), "TCL is television");
        assertEquals(40, tclProfile.getPreferredSafeZoneMarginDp(), "TCL safe zone margin is 40dp");
    }

    private static void testTabletAndPhoneInference() {
        DeviceProfile tablet = DeviceProfile.inferProfile("Samsung", "SM-T870", 30, "arm64-v8a", false, false, true);
        assertEquals(DeviceType.ANDROID_TABLET, tablet.getDeviceType(), "Touchscreen without TV mode infers tablet/phone");
        assertTrue(tablet.hasTouchscreen(), "Tablet has touchscreen");
        assertTrue(!tablet.isTelevision(), "Tablet is not television");
    }

    private static void testGamerInference() {
        DeviceProfile gamer = DeviceProfile.inferProfile("AYN", "Odin 2", 33, "arm64-v8a", false, false, true);
        assertEquals(DeviceType.ANDROID_GAMER, gamer.getDeviceType(), "Odin infers ANDROID_GAMER");
        assertTrue(gamer.isHandheldGamer(), "Gamer handheld identified");
        assertTrue(gamer.isLandscapeOnly(), "Gamer handheld enforces landscape");
        assertEquals("arm64-v8a", gamer.getAbi(), "Modern gamer handheld is 64-bit");

        DeviceProfile gamerFlavor = DeviceProfile.inferProfile("Generic", "Test", 31, "arm64-v8a", false, true, false);
        assertEquals(DeviceType.ANDROID_GAMER, gamerFlavor.getDeviceType(), "Gamer flavor overrides model");
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
