package com.kiver.fireretro;

/** TV overscan margins, persisted per device and constrained for safe focus targets. */
final class SafeAreaProfile {
    static final int MIN_PERCENT = 2;
    static final int MAX_PERCENT = 8;
    final int horizontalPercent;
    final int verticalPercent;

    SafeAreaProfile() { this(5, 4); }
    SafeAreaProfile(int horizontalPercent, int verticalPercent) {
        this.horizontalPercent = clamp(horizontalPercent);
        this.verticalPercent = clamp(verticalPercent);
    }
    SafeAreaProfile withHorizontal(int value) { return new SafeAreaProfile(value, verticalPercent); }
    SafeAreaProfile withVertical(int value) { return new SafeAreaProfile(horizontalPercent, value); }
    int horizontalInset(int width) { return Math.round(width * horizontalPercent / 100f); }
    int verticalInset(int height) { return Math.round(height * verticalPercent / 100f); }
    private static int clamp(int value) { return Math.max(MIN_PERCENT, Math.min(MAX_PERCENT, value)); }
}
