package com.kiver.fireretro;

/**
 * Resolves Platform -&gt; DeviceCapabilities -&gt; DeviceProfile -&gt;
 * ExperienceMode without branching on manufacturer names for behavior.
 *
 * <p>The legacy {@link DeviceProfile#inferProfile} is preserved untouched;
 * this engine is the strangler path and additionally distinguishes PHONE
 * from TABLET via smallest-width instead of touch-only.
 */
public final class DeviceProfileEngine {
    private DeviceProfileEngine() { }

    public static DeviceProfile resolve(DeviceCapabilities caps) {
        if (caps == null) {
            return DeviceProfile.forAndroidTv("Generic", "Generic", 28);
        }
        if (caps.isGamerBuildFlavor()) {
            return DeviceProfile.forAndroidGamer(caps.getModel(), caps.getAndroidApi(), primaryAbi(caps));
        }
        if (caps.getPlatform() == Platform.FIRE_OS || isFireHardware(caps)) {
            return DeviceProfile.forFireTv(caps.getModel(), caps.getAndroidApi());
        }
        if (caps.isTelevision()) {
            return DeviceProfile.forAndroidTv(caps.getModel(), caps.getManufacturer(), caps.getAndroidApi());
        }
        if (isHandheldGamerModel(caps.getModel())) {
            return DeviceProfile.forAndroidGamer(caps.getModel(), caps.getAndroidApi(), primaryAbi(caps));
        }
        if (caps.hasTouchscreen()) {
            if (caps.isWideTablet()) {
                return DeviceProfile.forTablet(caps.getModel(), caps.getAndroidApi());
            }
            return DeviceProfile.forPhone(caps.getModel(), caps.getAndroidApi());
        }
        return DeviceProfile.forAndroidTv(caps.getModel(), caps.getManufacturer(), caps.getAndroidApi());
    }

    public static ExperienceMode experienceFor(DeviceProfile profile, DeviceCapabilities caps) {
        // DOCKED is a gamer device projecting outward; a television profile
        // already owns the TV shell even when an external display is attached.
        boolean docked = caps != null && caps.isDocked()
                && (profile == null || !profile.isTelevision());
        DeviceType type = profile != null ? profile.getDeviceType() : null;
        return ExperienceMode.fromProfile(type, docked);
    }

    public static ExperienceMode experienceFor(DeviceProfile profile, DeviceCapabilities caps, String manualOverride) {
        ExperienceMode override = ExperienceMode.parseOverride(manualOverride);
        if (override != null) {
            return override;
        }
        return experienceFor(profile, caps);
    }

    private static boolean isFireHardware(DeviceCapabilities caps) {
        String brand = caps.getManufacturer().toLowerCase();
        String model = caps.getModel().toLowerCase();
        return brand.contains("amazon") || model.startsWith("aft");
    }

    private static boolean isHandheldGamerModel(String model) {
        if (model == null) {
            return false;
        }
        String normalized = model.toLowerCase();
        return normalized.contains("odin") || normalized.contains("retroid")
                || normalized.contains("ayaneo") || normalized.contains("rg-");
    }

    private static String primaryAbi(DeviceCapabilities caps) {
        String[] abis = caps.getAbis();
        if (abis.length > 0 && abis[0] != null && !abis[0].trim().isEmpty()) {
            return abis[0];
        }
        return "arm64-v8a";
    }
}
