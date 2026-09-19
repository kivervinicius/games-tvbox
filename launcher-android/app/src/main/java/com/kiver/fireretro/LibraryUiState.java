package com.kiver.fireretro;

/** Friendly, serializable UI state for online library synchronization. */
final class LibraryUiState {
    enum Status { IDLE, SYNCING, UPDATED, OFFLINE, INVALID_CREDENTIAL, NO_SPACE, ERROR }
    final Status status;
    final int progress;
    final String message;
    final String lastSync;
    final String action;

    LibraryUiState(Status status, int progress, String message, String lastSync, String action) {
        this.status = status == null ? Status.IDLE : status;
        this.progress = Math.max(0, Math.min(100, progress));
        this.message = message == null ? "" : message;
        this.lastSync = lastSync == null ? "" : lastSync;
        this.action = action == null ? "" : action;
    }
    static LibraryUiState syncing(int progress, String item) { return new LibraryUiState(Status.SYNCING, progress, item == null || item.isEmpty() ? "Sincronizando catálogo" : "Baixando " + item, "", "Em segundo plano"); }
    static LibraryUiState updated(String when) { return new LibraryUiState(Status.UPDATED, 100, "Catálogo atualizado", when, "Tentar novamente"); }
    static LibraryUiState offline(String when) { return new LibraryUiState(Status.OFFLINE, 0, "Sem internet. Jogos baixados continuam disponíveis.", when, "Tentar novamente"); }
    static LibraryUiState invalidCredential() { return new LibraryUiState(Status.INVALID_CREDENTIAL, 0, "Credencial da biblioteca inválida ou expirada.", "", "Parear novamente"); }
    static LibraryUiState noSpace() { return new LibraryUiState(Status.NO_SPACE, 0, "Espaço insuficiente para este download.", "", "Liberar espaço"); }
    static LibraryUiState error(String message) { return new LibraryUiState(Status.ERROR, 0, message == null || message.isEmpty() ? "Erro temporário na biblioteca." : message, "", "Tentar novamente"); }
}
