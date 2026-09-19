package com.kiver.fireretro;
public final class LibraryUiStateTest {
    public static void main(String[] args) {
        LibraryUiState sync = LibraryUiState.syncing(140, "Sonic");
        if (sync.status != LibraryUiState.Status.SYNCING || sync.progress != 100 || !sync.message.contains("Sonic")) throw new AssertionError();
        if (LibraryUiState.offline("ontem").status != LibraryUiState.Status.OFFLINE) throw new AssertionError();
        if (LibraryUiState.invalidCredential().action.length() == 0) throw new AssertionError();
        System.out.println("PASS: library UI state");
    }
}
