package com.kiver.fireretro;

public final class RetroArchProvider implements EmulatorProvider {
    public static final String PACKAGE_RA32 = "com.retroarch.ra32";
    public static final String PACKAGE_RA64 = "com.retroarch.a64";
    public static final String PACKAGE_STANDARD = "com.retroarch";
    public static final String ACTIVITY_RETRO_ACTIVITY_FUTURE = "com.retroarch.browser.retroactivity.RetroActivityFuture";

    private final String packageName;
    private final String activityName;
    private final String customCoresDir;
    private final String customConfigPath;

    public RetroArchProvider() {
        this(PACKAGE_RA32, ACTIVITY_RETRO_ACTIVITY_FUTURE, null, null);
    }

    public RetroArchProvider(String packageName) {
        this(packageName, ACTIVITY_RETRO_ACTIVITY_FUTURE, null, null);
    }

    public RetroArchProvider(String packageName, String activityName, String customCoresDir, String customConfigPath) {
        this.packageName = packageName != null ? packageName : PACKAGE_RA32;
        this.activityName = activityName != null ? activityName : ACTIVITY_RETRO_ACTIVITY_FUTURE;
        this.customCoresDir = customCoresDir;
        this.customConfigPath = customConfigPath;
    }

    public static RetroArchProvider forAbi(String abi) {
        if ("arm64-v8a".equalsIgnoreCase(abi) || "x86_64".equalsIgnoreCase(abi)) {
            return new RetroArchProvider(PACKAGE_RA64);
        }
        return new RetroArchProvider(PACKAGE_RA32);
    }

    @Override
    public String getProviderId() {
        return "retroarch";
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public String getActivityName() {
        return activityName;
    }

    @Override
    public String getDefaultConfigPath() {
        if (customConfigPath != null && !customConfigPath.isEmpty()) {
            return customConfigPath;
        }
        return "/sdcard/Android/data/" + packageName + "/files/retroarch.cfg";
    }

    @Override
    public String getDefaultCoresDirectory() {
        if (customCoresDir != null && !customCoresDir.isEmpty()) {
            return customCoresDir;
        }
        return "/data/data/" + packageName + "/cores/";
    }

    @Override
    public String resolveCorePath(String platformId, String coreFileName) {
        if (coreFileName == null || coreFileName.trim().isEmpty()) {
            return null;
        }
        if (coreFileName.startsWith("/")) {
            return coreFileName;
        }
        String base = getDefaultCoresDirectory();
        if (!base.endsWith("/")) {
            base += "/";
        }
        return base + coreFileName;
    }
}
