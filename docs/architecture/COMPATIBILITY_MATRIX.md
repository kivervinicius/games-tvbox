# Matriz de Compatibilidade de Dispositivos e Perfis

## 1. Perfis Oficiais Homologados

| Perfil | Plataforma / OS | ABI Típica | Input Primário | Fallback Input | UI Mode | Storage Primário |
|---|---|---|---|---|---|---|
| **FIRE_TV** | Fire OS 5 / 6 / 7 | `armeabi-v7a` | Controle Remoto D-pad | Gamepad BT | TV 10-foot (48dp margin) | Legacy External / USB |
| **ANDROID_TV** | Android TV 9 a 13 | `armeabi-v7a` / `arm64-v8a` | D-pad / Gamepad | Gamepad BT | TV 10-foot (36dp margin) | Legacy External / USB |
| **ANDROID_TV_TCL** | Android TV / Google TV (TCL) | `armeabi-v7a` / `arm64-v8a` | D-pad / Gamepad | Gamepad BT | TV 10-foot (40dp margin) | USB Mass Storage / External |
| **ANDROID_TABLET** | Android 10 a 14 | `arm64-v8a` | Touchscreen | Gamepad BT / USB | Adaptive Grid (16dp margin) | Scoped App Storage / MicroSD |
| **ANDROID_PHONE** | Android 10 a 14 | `arm64-v8a` | Touchscreen | Gamepad Telescópico | Compact Grid (8dp margin) | Scoped App Storage / MicroSD |
| **ANDROID_GAMER** | Android 11 a 14 | `arm64-v8a` | Gamepad Físico Integrado | On-screen Touch HUD | Immersive Landscape Dashboard | Removable MicroSD / Scoped |

---

## 2. Matriz de Emuladores e Plataformas

| Plataforma | ID Canônico | Extensões | Core Libretro Recomendado | Provider 32-bit | Provider 64-bit | Status do Pipeline |
|---|---|---|---|---|---|---|
| PlayStation 1 | `ps1` | `.cue`, `.bin`, `.iso`, `.chd` | `pcsx_rearmed_libretro_android.so` | `com.retroarch.ra32` | `com.retroarch.a64` | **Ativo (Produção)** |
| Super Nintendo | `snes` | `.smc`, `.sfc` | `snes9x_libretro_android.so` | `com.retroarch.ra32` | `com.retroarch.a64` | **Ativo (Produção)** |
| NES | `nes` | `.nes` | `fceumm_libretro_android.so` | `com.retroarch.ra32` | `com.retroarch.a64` | **Ativo (Produção)** |
| Sega Mega Drive | `megadrive` | `.bin`, `.gen`, `.smd`, `.md` | `genesis_plus_gx_libretro_android.so` | `com.retroarch.ra32` | `com.retroarch.a64` | **Ativo (Produção)** |
| Game Boy Advance | `gba` | `.gba` | `mgba_libretro_android.so` | `com.retroarch.ra32` | `com.retroarch.a64` | **Ativo (Produção)** |
| Nintendo 64 | `n64` | `.z64`, `.n64`, `.v64` | `mupen64plus_next_libretro_android.so` | `com.retroarch.ra32` | `com.retroarch.a64` | **Planejado** |
| Android Native | `android` | `.apk` | N/A (Execução Nativa Android) | PackageManager | PackageManager | **Ativo (Produção)** |

---

## 3. Matriz de Storage por Versão do Android

| Versão Android | API Level | Comportamento de Permissão | Estratégia Recomendada |
|---|---|---|---|
| Android 5.0 – 9.0 | 21 – 28 | `WRITE_EXTERNAL_STORAGE` irrestrito | `LegacyExternalStorageStrategy` (`/sdcard/roms`) |
| Android 10 (Q) | 29 | Scoped Storage opcional (`requestLegacyExternalStorage=true`) | `LegacyExternalStorageStrategy` com fallback para `AppStorageStrategy` |
| Android 11 – 14 | 30 – 34 | Scoped Storage obrigatório | `AppStorageStrategy` (`/Android/data/.../files/roms`) e `RemovableStorageStrategy` |
| Android TV com USB | Qualquer | Volumes externos montados em `/storage/*` | `UsbStorageStrategy` ou `RemovableStorageStrategy` |
