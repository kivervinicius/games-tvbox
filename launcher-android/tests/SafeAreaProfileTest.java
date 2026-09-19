package com.kiver.fireretro;
public final class SafeAreaProfileTest {
    public static void main(String[] args) {
        SafeAreaProfile profile = new SafeAreaProfile(99, 0);
        if (profile.horizontalPercent != 8 || profile.verticalPercent != 2) throw new AssertionError();
        if (profile.horizontalInset(1920) != 154 || profile.verticalInset(1080) != 22) throw new AssertionError();
        if (new SafeAreaProfile().horizontalPercent != 5 || new SafeAreaProfile().verticalPercent != 4) throw new AssertionError();
        System.out.println("PASS: safe area");
    }
}
