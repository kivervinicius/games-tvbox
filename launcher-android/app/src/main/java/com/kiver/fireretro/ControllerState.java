package com.kiver.fireretro;

/** UI-facing controller health state, independent from Android input classes. */
final class ControllerState {
    private final String name, descriptor;
    private final int deviceId, vendorId, productId;
    private boolean connected = true, profileApplied, tested;
    ControllerState(String name, String descriptor, int deviceId, int vendorId, int productId) {
        this.name = name == null || name.trim().isEmpty() ? "Controle sem nome" : name;
        this.descriptor = descriptor == null ? "" : descriptor;
        this.deviceId = deviceId; this.vendorId = vendorId; this.productId = productId;
    }
    String displayName() { return name; }
    String descriptor() { return descriptor; }
    int deviceId() { return deviceId; }
    int vendorId() { return vendorId; }
    int productId() { return productId; }
    void setConnected(boolean value) { connected = value; }
    void setProfileApplied(boolean value) { profileApplied = value; if (!value) tested = false; }
    void setTested(boolean value) { tested = value && profileApplied; }
    String connectionLabel() { return connected ? "CONECTADO" : "DESCONECTADO"; }
    String profileLabel() { return tested ? "Perfil confirmado" : (profileApplied ? "Perfil aplicado" : "Aguardando teste"); }
    String analogLabel() { return "Analógicos → direcionais"; }
    static boolean profileHealthy(boolean bindingsPresent, boolean compatibilityMode) { return bindingsPresent && !compatibilityMode; }
}
