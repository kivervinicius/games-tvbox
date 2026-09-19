package com.kiver.fireretro;

final class AndroidAppEntry {
    final String title, packageName, source, sourceType, category, storeType;
    private boolean installed;
    AndroidAppEntry(String title, String packageName, String source, String sourceType, String category) {
        this(title, packageName, source, sourceType, category, "");
    }
    AndroidAppEntry(String title, String packageName, String source, String sourceType, String category, String storeType) {
        this.title = title == null ? "Aplicativo" : title; this.packageName = packageName == null ? "" : packageName;
        this.source = source == null ? "" : source; this.sourceType = sourceType == null ? "apk" : sourceType; this.category = category == null ? "Outros" : category; this.storeType = storeType == null ? "" : storeType;
    }
    void setInstalled(boolean value) { installed = value; }
    boolean isInstalled() { return installed; }
    String statusLabel() { return installed ? "Instalado" : "Disponível"; }
    String sourceLabel() { return "store".equalsIgnoreCase(sourceType) ? "Loja oficial" : "APK disponível"; }
}
