package com.kiver.fireretro;

/**
 * Accessibility announcement policy for dynamic states (download, pairing,
 * sync, install, offline, error). Progress announces only on milestone
 * crossings (10/25/50/75/100%) so TalkBack never spams per-byte updates;
 * stage changes always announce, throttled by minimum intervals.
 */
public final class AccessibilityAnnouncer {
    public static final int[] PROGRESS_MILESTONES = {0, 10, 25, 50, 75, 100};
    /** Minimum gap between progress announcements. */
    public static final long PROGRESS_MIN_INTERVAL_MS = 2000L;
    /** Minimum gap between stage announcements. */
    public static final long STAGE_MIN_INTERVAL_MS = 500L;

    private AccessibilityAnnouncer() { }

    /**
     * True when moving from prevPct to newPct crosses a milestone upward or
     * completes. Backward movement (retry/restart) re-announces only when it
     * lands exactly on a milestone or reset to zero with a new stage.
     */
    public static boolean shouldAnnounceProgress(int prevPct, int newPct) {
        int prev = clamp(prevPct);
        int next = clamp(newPct);
        if (next <= prev) {
            return next == 0 && prev != 0;
        }
        for (int milestone : PROGRESS_MILESTONES) {
            if (prev < milestone && next >= milestone) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldAnnounceStageChange(String oldStage, String newStage) {
        if (newStage == null || newStage.trim().isEmpty()) {
            return false;
        }
        if (oldStage == null) {
            return true;
        }
        return !oldStage.equals(newStage);
    }

    public static boolean canAnnounce(long lastAnnouncementMs, long nowMs, long minIntervalMs) {
        if (nowMs < lastAnnouncementMs) {
            return true;
        }
        return nowMs - lastAnnouncementMs >= minIntervalMs;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
