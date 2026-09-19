package com.kiver.fireretro;
public final class TvNavigationStateTest {
    public static void main(String[] args) {
        TvNavigationState state = new TvNavigationState();
        if (!"jogos".equals(state.sectionId()) || !"TODOS".equals(state.platformId())) throw new AssertionError();
        state.moveSection(-1); if (!"biblioteca".equals(state.sectionId())) throw new AssertionError();
        state.setSection(0); state.movePlatform(-1); if (!"PlayStation".equals(state.platformId())) throw new AssertionError();
        state.rememberFocus("card-1"); if (!"card-1".equals(state.lastFocus())) throw new AssertionError();
        System.out.println("PASS: TV navigation state");
    }
}
