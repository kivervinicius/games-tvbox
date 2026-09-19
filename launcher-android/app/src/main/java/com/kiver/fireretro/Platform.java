package com.kiver.fireretro;

/**
 * Physical OS platform.
 *
 * <p>Fire OS is an Android-derived platform, never a separate UI branch.
 * UI adaptation must key off {@link DeviceCapabilities} /
 * {@link DeviceProfile} / {@link ExperienceMode}, never off this enum alone.
 */
public enum Platform {
    ANDROID,
    FIRE_OS,
    UNKNOWN;

    public static Platform fromBrandModel(String brand, String model) {
        String safeBrand = brand != null ? brand.toLowerCase() : "";
        String safeModel = model != null ? model.toLowerCase() : "";
        if (safeBrand.contains("amazon") || safeModel.startsWith("aft")) {
            return FIRE_OS;
        }
        if (safeBrand.isEmpty() && safeModel.isEmpty()) {
            return UNKNOWN;
        }
        return ANDROID;
    }

    public boolean isAndroidDerived() {
        return this == ANDROID || this == FIRE_OS;
    }
}
