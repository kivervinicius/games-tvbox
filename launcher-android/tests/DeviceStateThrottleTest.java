package com.kiver.fireretro;

public final class DeviceStateThrottleTest {
    public static void main(String[] args) {
        long sixHours = 6L * 60L * 60L * 1000L;
        if (!DeviceStateThrottle.shouldReport("", 0L, "a", 1_000L)) throw new AssertionError("first state must be reported");
        if (DeviceStateThrottle.shouldReport("a", 1_000L, "a", 1_000L + sixHours - 1L)) throw new AssertionError("identical state must be suppressed for six hours");
        if (!DeviceStateThrottle.shouldReport("a", 1_000L, "b", 1_001L)) throw new AssertionError("changed state must be reported immediately");
        if (!DeviceStateThrottle.shouldReport("a", 1_000L, "a", 1_000L + sixHours)) throw new AssertionError("heartbeat must be allowed after six hours");
        System.out.println("PASS: device state throttle");
    }
}
