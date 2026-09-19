# Baseline UI (legacy launcher, pre-redesign)

`MainActivity` builds the whole UI programmatically (no Compose, no XML
layouts): sidebar + hero + sections + grids of cards rendered into a
`ScrollView`. Structure per `buildScreen:193`:

- `createCompactTopBar` / `createSidebar` (`JOGOS/PLATAFORMAS/FAVORITOS/APPS/...`)
- `createHero` (title/subtitle/artwork), `createGalleryNavigation`
- `renderSections:996` -> `addCard:1149` (cover via `loadGameCover:1603`,
  `decodeFile` synchronous), `configureGameCardFocus:1057`
- Settings screens (`showSettingsScreen:457`), appearance dashboard
  (`showAppearanceDashboard:566`), controller dashboards, pairing screen
  (`showCloudPairingScreen:913`, copy "PAREAR ESTA TV"), search editor
  (`showSearchEditor:1332`, custom keyboard, no IME).

## Measured characteristics

- Scaling: `uiScale=max(0.62,min(1.15,min(w/1920,h/1080)))`, `scaled()` 240 uses.
- Text: 11× `COMPLEX_UNIT_PX` (all wrapped in `scaled()`); ignores fontScale.
- Titles carry state prefixes (`★ NOVO · ONLINE · name`); no badge system.
- Focus: `setNextFocus*` IDs wired; visual = color-biased, no guaranteed
  scale/outline/shadow token; reduced-motion not honored.
- Touch targets: cards/buttons at `scaled(42/44/38)` < 48dp, shrinking further
  at `uiScale 0.62`.
- Safe area: custom `SafeAreaProfile` % (5,4 default), no `WindowInsets`;
  TV overscan and mobile cutout/gesture conflated.
- Semantics: 10× `setContentDescription`; no roles/states/live-regions;
  progress/errors via `TextView+Toast` only.
- Home state: index-based, no semantic `sectionId/contentId` persistence.

## Target direction (not yet built)

`TvActivity->TvShell` (sidebar+content+discreet status; Continue/Recentes/
Favoritos/Plataformas/Android) and `GamerActivity->GamerShell` (Material,
bottom/rail nav, adaptive grid), lazy lists, Coil-class loader, badge cards,
focus scale 1.05-1.08 + strong outline, dp/sp tokens. Screenshots/goldens
required per screen before legacy retirement behind `uiGeneration` flag.
