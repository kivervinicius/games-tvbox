package com.kiver.fireretro;

public final class AccessibilityAnnouncerTest {
    public static void main(String[] args) {
        testMilestoneCrossings();
        testNoSpam();
        testStageChanges();
        testThrottleGate();
        System.out.println("PASS: AccessibilityAnnouncer tests passed");
    }

    private static void testMilestoneCrossings() {
        assertTrue(AccessibilityAnnouncer.shouldAnnounceProgress(0, 10), "First milestone announces");
        assertTrue(AccessibilityAnnouncer.shouldAnnounceProgress(9, 26), "Crossing 10 and 25 announces once");
        assertTrue(AccessibilityAnnouncer.shouldAnnounceProgress(99, 100), "Completion announces");
    }

    private static void testNoSpam() {
        assertTrue(!AccessibilityAnnouncer.shouldAnnounceProgress(11, 12), "No milestone, no announcement");
        assertTrue(!AccessibilityAnnouncer.shouldAnnounceProgress(26, 30), "Between milestones stays silent");
        assertTrue(!AccessibilityAnnouncer.shouldAnnounceProgress(50, 49), "Backward drift stays silent");
        assertTrue(AccessibilityAnnouncer.shouldAnnounceProgress(49, 0), "Reset to zero announces");
    }

    private static void testStageChanges() {
        assertTrue(AccessibilityAnnouncer.shouldAnnounceStageChange("downloading", "verifying"), "Stage change announces");
        assertTrue(!AccessibilityAnnouncer.shouldAnnounceStageChange("ready", "ready"), "Same stage silent");
        assertTrue(!AccessibilityAnnouncer.shouldAnnounceStageChange("ready", null), "Null stage silent");
        assertTrue(AccessibilityAnnouncer.shouldAnnounceStageChange(null, "ready"), "First stage announces");
    }

    private static void testThrottleGate() {
        assertTrue(AccessibilityAnnouncer.canAnnounce(1000L, 3000L, 2000L), "Interval elapsed passes");
        assertTrue(!AccessibilityAnnouncer.canAnnounce(1000L, 2500L, 2000L), "Interval pending blocks");
        assertTrue(AccessibilityAnnouncer.canAnnounce(5000L, 1000L, 2000L), "Clock skew passes");
    }

    private static void assertTrue(boolean val, String scenario) {
        if (!val) throw new AssertionError(scenario);
    }
}
