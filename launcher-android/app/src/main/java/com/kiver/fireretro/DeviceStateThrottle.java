package com.kiver.fireretro;

final class DeviceStateThrottle {
    static final long HEARTBEAT_MS = 6L * 60L * 60L * 1000L;
    private DeviceStateThrottle() { }
    static boolean shouldReport(String previousFingerprint, long previousAt, String fingerprint, long now) {
        return previousFingerprint == null || previousFingerprint.isEmpty() || !previousFingerprint.equals(fingerprint) || now - previousAt >= HEARTBEAT_MS;
    }
}
