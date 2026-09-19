package com.kiver.fireretro;

public interface EmulatorProvider {
    String getProviderId();
    String getPackageName();
    String getActivityName();
    String getDefaultConfigPath();
    String getDefaultCoresDirectory();
    String resolveCorePath(String platformId, String coreFileName);

    /** Whether this runtime package is installed on the device. Defaults to true for legacy callers. */
    default boolean isAvailable() {
        return true;
    }

    /** Extracts the logical coreId from a physical core path. */
    default String resolveCoreId(String corePath) {
        return RetroArchCoreCatalog.parseCoreId(corePath);
    }
}
