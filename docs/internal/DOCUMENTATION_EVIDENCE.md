# Livro de evidências

| Afirmação | Evidência de implementação | Evidência de teste ou visual |
|---|---|---|
| Launcher é Leanback | `launcher-android/app/src/main/AndroidManifest.xml` | `project.tests.ps1` verifica a categoria |
| Catálogo tem cinco plataformas | `launcher-android/app/src/main/assets/games.json` | `project.tests.ps1` verifica NES, SNES, Mega Drive, GBA e PlayStation |
| Cards têm capas sem corte | `MainActivity.addCard` usa `FIT_CENTER` | `project.tests.ps1` verifica escala e assets |
| Slides são configuráveis | `MainActivity.readTheme`, `loadSlideBitmap`, `ThemeState` | `ThemeStateTest`, screenshot `docs/assets/screenshots/launcher-interface.png` |
| Manager envia tema | `manager-windows/FireRetroManager.ps1`, `Push-ThemeToFireStick` | `manager-windows/tests/Test-Manager.ps1` |
| Dados pessoais ficam fora do projeto | `.gitignore`, cache `%LOCALAPPDATA%` | `scripts/Test-PublicLayout.ps1` e auditoria de extensões |
| Handoff para RetroArch | `MainActivity.launch` e constantes `ROM`, `LIBRETRO`, `CONFIGFILE` | `project.tests.ps1` |
| Saída configurável | `examples/retroarch.cfg.example` | `project.tests.ps1` |

Referências externas usadas na documentação: [Android Debug Bridge](https://developer.android.com/tools/adb), [Install and Run Your App on Fire TV](https://developer.amazon.com/docs/fire-tv/installing-and-running-your-app.html), [Developer Tools Menu](https://developer.amazon.com/docs/fire-tv/developer-tools.html) e [Setting Up Your Development Environment](https://developer.amazon.com/docs/fire-tv/setting-up-your-development-environment.html).
