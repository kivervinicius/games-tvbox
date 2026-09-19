package com.kiver.fireretro;

import java.util.Set;

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

    /**
     * Selects the best installed runtime: standard package first, then the
     * ABI-matched split package. Falls back to the ABI rule when nothing is
     * installed so legacy callers keep working.
     */
    public static RetroArchProvider selectBest(String abi, Set<String> installedPackages) {
        if (installedPackages != null) {
            if (installedPackages.contains(PACKAGE_STANDARD)) {
                return new RetroArchProvider(PACKAGE_STANDARD);
            }
            if (installedPackages.contains(PACKAGE_RA64)
                    && ("arm64-v8a".equalsIgnoreCase(abi) || "x86_64".equalsIgnoreCase(abi))) {
                return new RetroArchProvider(PACKAGE_RA64);
            }
            if (installedPackages.contains(PACKAGE_RA32)) {
                return new RetroArchProvider(PACKAGE_RA32);
            }
        }
        return forAbi(abi);
    }

    /** Pure check against a PackageManager-derived set; MainActivity passes the live set. */
    public boolean isAvailable(Set<String> installedPackages) {
        return installedPackages != null && installedPackages.contains(packageName);
    }

    @Override
    public boolean isAvailable() {
        return true;
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
        String name = coreFileName.trim();
        if (name.startsWith("/")) {
            return name;
        }
        if (!name.toLowerCase().endsWith(".so") && RetroArchCoreCatalog.isKnownCoreId(name)) {
            name = RetroArchCoreCatalog.coreFileNameFor(name);
        }
        String base = getDefaultCoresDirectory();
        if (!base.endsWith("/")) {
            base += "/";
        }
        return base + name;
    }

    @Override
    public String resolveCoreId(String corePath) {
        return RetroArchCoreCatalog.parseCoreId(corePath);
    }
}
