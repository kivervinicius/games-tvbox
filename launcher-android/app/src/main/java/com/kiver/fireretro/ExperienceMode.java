package com.kiver.fireretro;

/**
 * Experience mode selected from a resolved {@link DeviceProfile} plus
 * live capabilities (docked detection). TV and GAMER shells share the
 * domain but never share layout components.
 */
public enum ExperienceMode {
    TV,
    GAMER,
    DOCKED;

    public static ExperienceMode fromProfile(DeviceType deviceType, boolean docked) {
        if (docked) {
            return DOCKED;
        }
        if (deviceType == null) {
            return TV;
        }
        switch (deviceType) {
            case FIRE_TV:
            case ANDROID_TV:
            case ANDROID_TV_TCL:
                return TV;
            case ANDROID_PHONE:
            case ANDROID_TABLET:
            case ANDROID_GAMER:
            default:
                return GAMER;
        }
    }

    /**
     * Manual override for Settings/debug. Unknown values return null so the
     * caller keeps the automatic mode instead of guessing.
     */
    public static ExperienceMode parseOverride(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if ("tv".equals(normalized) || "television".equals(normalized)) {
            return TV;
        }
        if ("gamer".equals(normalized) || "mobile".equals(normalized)) {
            return GAMER;
        }
        if ("docked".equals(normalized)) {
            return DOCKED;
        }
        return null;
    }
}
