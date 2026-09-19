package com.kiver.fireretro;

public interface EmulatorProvider {
    String getProviderId();
    String getPackageName();
    String getActivityName();
    String getDefaultConfigPath();
    String getDefaultCoresDirectory();
    String resolveCorePath(String platformId, String coreFileName);
}
