package com.kiver.fireretro;

public final class DeviceProfile {
    private final DeviceType deviceType;
    private final String model;
    private final String manufacturer;
    private final int apiLevel;
    private final String abi;
    private final boolean hasTouchscreen;
    private final boolean hasGamepad;
    private final boolean hasDpadRemote;
    private final boolean isTelevision;
    private final boolean isHandheldGamer;
    private final boolean landscapeOnly;
    private final int preferredSafeZoneMarginDp;
    private final int defaultColumnsCount;
    private final boolean requiresDirectStorageWorkaround;

    public DeviceProfile(
            DeviceType deviceType,
            String model,
            String manufacturer,
            int apiLevel,
            String abi,
            boolean hasTouchscreen,
            boolean hasGamepad,
            boolean hasDpadRemote,
            boolean isTelevision,
            boolean isHandheldGamer,
            boolean landscapeOnly,
            int preferredSafeZoneMarginDp,
            int defaultColumnsCount,
            boolean requiresDirectStorageWorkaround) {
        this.deviceType = deviceType != null ? deviceType : DeviceType.ANDROID_TV;
        this.model = model != null ? model : "Generic";
        this.manufacturer = manufacturer != null ? manufacturer : "Generic";
        this.apiLevel = apiLevel;
        this.abi = abi != null ? abi : "armeabi-v7a";
        this.hasTouchscreen = hasTouchscreen;
        this.hasGamepad = hasGamepad;
        this.hasDpadRemote = hasDpadRemote;
        this.isTelevision = isTelevision;
        this.isHandheldGamer = isHandheldGamer;
        this.landscapeOnly = landscapeOnly;
        this.preferredSafeZoneMarginDp = preferredSafeZoneMarginDp;
        this.defaultColumnsCount = defaultColumnsCount;
        this.requiresDirectStorageWorkaround = requiresDirectStorageWorkaround;
    }

    public static DeviceProfile forFireTv(String model, int apiLevel) {
        return new DeviceProfile(
                DeviceType.FIRE_TV,
                model != null ? model : "AFTMM",
                "Amazon",
                apiLevel > 0 ? apiLevel : 25,
                "armeabi-v7a",
                false,
                true,
                true,
                true,
                false,
                true,
                48,
                4,
                false
        );
    }

    public static DeviceProfile forAndroidTv(String model, String manufacturer, int apiLevel) {
        boolean isTcl = manufacturer != null && manufacturer.toLowerCase().contains("tcl");
        return new DeviceProfile(
                isTcl ? DeviceType.ANDROID_TV_TCL : DeviceType.ANDROID_TV,
                model != null ? model : "AndroidTV",
                manufacturer != null ? manufacturer : "Generic",
                apiLevel > 0 ? apiLevel : 28,
                "armeabi-v7a",
                false,
                true,
                true,
                true,
                false,
                true,
                isTcl ? 40 : 36,
                4,
                false
        );
    }

    public static DeviceProfile forTablet(String model, int apiLevel) {
        return new DeviceProfile(
                DeviceType.ANDROID_TABLET,
                model != null ? model : "Tablet",
                "Generic",
                apiLevel > 0 ? apiLevel : 30,
                "arm64-v8a",
                true,
                true,
                false,
                false,
                false,
                false,
                16,
                5,
                false
        );
    }

    public static DeviceProfile forPhone(String model, int apiLevel) {
        return new DeviceProfile(
                DeviceType.ANDROID_PHONE,
                model != null ? model : "Phone",
                "Generic",
                apiLevel > 0 ? apiLevel : 30,
                "arm64-v8a",
                true,
                true,
                false,
                false,
                false,
                false,
                8,
                3,
                false
        );
    }

    public static DeviceProfile forAndroidGamer(String model, int apiLevel, String abi) {
        return new DeviceProfile(
                DeviceType.ANDROID_GAMER,
                model != null ? model : "Odin-2",
                "GamerOEM",
                apiLevel > 0 ? apiLevel : 33,
                abi != null ? abi : "arm64-v8a",
                true,
                true,
                false,
                false,
                true,
                true,
                12,
                4,
                false
        );
    }

    public static DeviceProfile inferProfile(
            String brand,
            String model,
            int apiLevel,
            String abi,
            boolean isTvUiMode,
            boolean isGamerBuildFlavor,
            boolean hasTouchscreenFeature) {
        if (isGamerBuildFlavor) {
            return forAndroidGamer(model, apiLevel, abi);
        }

        String safeBrand = brand != null ? brand.toLowerCase() : "";
        String safeModel = model != null ? model.toLowerCase() : "";

        if (safeBrand.contains("amazon") || safeModel.startsWith("aft")) {
            return forFireTv(model, apiLevel);
        }

        if (safeBrand.contains("tcl") && isTvUiMode) {
            return forAndroidTv(model, "TCL", apiLevel);
        }

        if (isTvUiMode) {
            return forAndroidTv(model, brand, apiLevel);
        }

        if (safeModel.contains("odin") || safeModel.contains("retroid") || safeModel.contains("ayaneo") || safeModel.contains("rg-")) {
            return forAndroidGamer(model, apiLevel, abi);
        }

        if (hasTouchscreenFeature) {
            return forTablet(model, apiLevel);
        }

        return forAndroidTv(model, brand, apiLevel);
    }

    public DeviceType getDeviceType() { return deviceType; }
    public String getModel() { return model; }
    public String getManufacturer() { return manufacturer; }
    public int getApiLevel() { return apiLevel; }
    public String getAbi() { return abi; }
    public boolean hasTouchscreen() { return hasTouchscreen; }
    public boolean hasGamepad() { return hasGamepad; }
    public boolean hasDpadRemote() { return hasDpadRemote; }
    public boolean isTelevision() { return isTelevision; }
    public boolean isHandheldGamer() { return isHandheldGamer; }
    public boolean isLandscapeOnly() { return landscapeOnly; }
    public int getPreferredSafeZoneMarginDp() { return preferredSafeZoneMarginDp; }
    public int getDefaultColumnsCount() { return defaultColumnsCount; }
    public boolean requiresDirectStorageWorkaround() { return requiresDirectStorageWorkaround; }
}
