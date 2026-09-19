package com.kiver.fireretro;

public final class ControllerStateTest {
    public static void main(String[] args) {
        ControllerState state = new ControllerState("Xbox Wireless Controller", "abc", 1, 0456, 0123);
        assertEquals("CONECTADO", state.connectionLabel(), "connected label");
        assertEquals("Aguardando teste", state.profileLabel(), "unverified profile label");
        state.setProfileApplied(true);
        assertEquals("Perfil aplicado", state.profileLabel(), "applied profile label");
        state.setTested(true);
        assertEquals("Perfil confirmado", state.profileLabel(), "tested profile label");
        assertEquals("Analógicos → direcionais", state.analogLabel(), "analog navigation label");
        assertTrue(ControllerState.profileHealthy(true, false), "complete profile is healthy");
        assertTrue(!ControllerState.profileHealthy(false, false), "missing bindings are unhealthy");
        assertTrue(!ControllerState.profileHealthy(true, true), "compatibility mode is unhealthy");
        assertEquals("Xbox Wireless Controller", state.displayName(), "display name");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    private static void assertTrue(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
