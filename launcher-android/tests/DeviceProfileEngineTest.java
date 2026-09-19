package com.kiver.fireretro;

public final class DeviceProfileEngineTest {
    public static void main(String[] args) {
        testFireTvResolution();
        testTclResolution();
        testPhoneVsTabletSplit();
        testHandheldResolution();
        testNullCapsFallback();
        testExperienceMapping();
        testDockedExperience();
        testManualOverride();
        testLowMemoryIsPerformanceClass();
        System.out.println("PASS: DeviceProfileEngine tests passed");
    }

    private static DeviceCapabilities caps(Platform platform, int api, String brand, String model,
            boolean tv, boolean touch, int swDp, boolean externalDisplay, boolean gamepad,
            boolean lowRam, boolean gamerFlavor, String[] abis) {
        return new DeviceCapabilities(platform, api, brand, model, tv, touch,
                false, false, tv, tv, gamepad, gamepad, abis, swDp, 2.0f, 1.0f,
                DeviceCapabilities.ORIENTATION_LANDSCAPE, externalDisplay, false, 4L * 1024 * 1024 * 1024,
                true, platform == Platform.FIRE_OS, lowRam, gamerFlavor);
    }

    private static void testFireTvResolution() {
        DeviceProfile profile = DeviceProfileEngine.resolve(
                caps(Platform.FIRE_OS, 25, "Amazon", "AFTMM", true, false, 0, false, true, false, false, new String[]{"armeabi-v7a"}));
        assertEquals(DeviceType.FIRE_TV, profile.getDeviceType(), "Fire OS resolves FIRE_TV");
        assertTrue(profile.isTelevision(), "Fire TV is television");
    }

    private static void testTclResolution() {
        DeviceProfile profile = DeviceProfileEngine.resolve(
                caps(Platform.ANDROID, 28, "TCL", "BeyondTV", true, false, 0, false, true, false, false, new String[]{"armeabi-v7a"}));
        assertEquals(DeviceType.ANDROID_TV_TCL, profile.getDeviceType(), "TCL resolves ANDROID_TV_TCL");
    }

    private static void testPhoneVsTabletSplit() {
        DeviceProfile phone = DeviceProfileEngine.resolve(
                caps(Platform.ANDROID, 33, "Samsung", "SM-A546", false, true, 360, false, false, false, false, new String[]{"arm64-v8a"}));
        assertEquals(DeviceType.ANDROID_PHONE, phone.getDeviceType(), "sw360 touch resolves PHONE (legacy gap fixed)");
        DeviceProfile tablet = DeviceProfileEngine.resolve(
                caps(Platform.ANDROID, 33, "Samsung", "SM-T870", false, true, 800, false, false, false, false, new String[]{"arm64-v8a"}));
        assertEquals(DeviceType.ANDROID_TABLET, tablet.getDeviceType(), "sw800 touch resolves TABLET");
    }

    private static void testHandheldResolution() {
        DeviceProfile gamer = DeviceProfileEngine.resolve(
                caps(Platform.ANDROID, 33, "AYN", "Odin 2", false, true, 0, false, true, false, false, new String[]{"arm64-v8a"}));
        assertEquals(DeviceType.ANDROID_GAMER, gamer.getDeviceType(), "Odin resolves ANDROID_GAMER");
        DeviceProfile flavor = DeviceProfileEngine.resolve(
                caps(Platform.ANDROID, 31, "Generic", "Test", false, false, 0, false, false, false, true, new String[]{"arm64-v8a"}));
        assertEquals(DeviceType.ANDROID_GAMER, flavor.getDeviceType(), "Gamer flavor overrides model");
    }

    private static void testNullCapsFallback() {
        DeviceProfile fallback = DeviceProfileEngine.resolve(null);
        assertTrue(fallback != null, "Null caps never returns null");
    }

    private static void testExperienceMapping() {
        assertEquals(ExperienceMode.TV, DeviceProfileEngine.experienceFor(DeviceProfile.forFireTv("AFTMM", 25), null), "TV profile maps to TV");
        assertEquals(ExperienceMode.GAMER, DeviceProfileEngine.experienceFor(DeviceProfile.forPhone("Phone", 33),
                caps(Platform.ANDROID, 33, "Samsung", "SM-A546", false, true, 360, false, false, false, false, new String[]{"arm64-v8a"})), "Phone maps to GAMER");
    }

    private static void testDockedExperience() {
        DeviceCapabilities docked = caps(Platform.ANDROID, 33, "Samsung", "SM-A546", false, true, 360, true, true, false, false, new String[]{"arm64-v8a"});
        assertTrue(docked.isDocked(), "Phone + external display + gamepad is docked");
        assertEquals(ExperienceMode.DOCKED, DeviceProfileEngine.experienceFor(DeviceProfile.forPhone("Phone", 33), docked), "Docked caps map to DOCKED");
        assertEquals(ExperienceMode.TV, DeviceProfileEngine.experienceFor(DeviceProfile.forFireTv("AFTMM", 25), docked), "Television never counts as docked");
    }

    private static void testManualOverride() {
        DeviceProfile tv = DeviceProfile.forFireTv("AFTMM", 25);
        assertEquals(ExperienceMode.GAMER, DeviceProfileEngine.experienceFor(tv, null, "gamer"), "Manual override wins for debug");
        assertEquals(ExperienceMode.TV, DeviceProfileEngine.experienceFor(tv, null, "bogus"), "Unknown override keeps automatic mode");
        assertEquals(null, ExperienceMode.parseOverride("bogus"), "Unknown override parses to null");
    }

    private static void testLowMemoryIsPerformanceClass() {
        DeviceCapabilities low = caps(Platform.ANDROID, 28, "Generic", "Box", true, false, 0, false, true, true, false, new String[]{"armeabi-v7a"});
        assertTrue(low.isLowMemory(), "lowRam flag marks low memory");
        DeviceCapabilities oldApi = caps(Platform.FIRE_OS, 25, "Amazon", "AFTMM", true, false, 0, false, true, false, false, new String[]{"armeabi-v7a"});
        assertTrue(oldApi.isLowMemory(), "API 25 class is low memory without naming a model");
        DeviceCapabilities modern = caps(Platform.ANDROID, 33, "Samsung", "SM-A546", false, true, 360, false, false, false, false, new String[]{"arm64-v8a"});
        assertTrue(!modern.isLowMemory(), "Modern phone is not low memory");
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
