# Facts Ledger — Android Universal Gaming (HEAD 5812d13 + branch delta)

Facts need evidence. Hypotheses are not listed here.

| ID | Fact | Evidence |
|---|---|---|
| FACT-A001 | `MainActivity.java` has 1631 lines on `5812d13` | `wc -l launcher-android/.../MainActivity.java` |
| FACT-A002 | Manifest pins `minSdk 28/targetSdk 28`; leanback+touchscreen `required=false`; no gamepad/television decl (added this branch) | `AndroidManifest.xml:2-4` + branch diff |
| FACT-A003 | Legacy typography uses `COMPLEX_UNIT_PX` in 11 sites | `MainActivity.java:230,407,602,606,967,1024,1176,1388,1402,1496,1501` |
| FACT-A004 | Pixel scaling is `screenWidth/1920` with `scaled()` used 240× | `MainActivity.java:200-202,1564` |
| FACT-A005 | `decodeFile` hits UI thread at 3 sites | `MainActivity.java:629,1595,1603` |
| FACT-A006 | `/sdcard` hardcoded in 8 `MainActivity` sites; ra32 in 2 | `MainActivity.java:59,61-65,1462,1569` / `:60-61` |
| FACT-A007 | Gradle: AGP 8.2.2, compile/target 34, min 28, flavors tv/gamer, no explicit buildTypes | `launcher-android/app/build.gradle` |
| FACT-A008 | Cloud origin injected by regex over Java source at build | `launcher-android/scripts/Build-FireRetro.ps1:48` |
| FACT-A009 | `PendingIntent` lacked mutability flag | `AndroidAppInstaller.java:56` pre-fix; harness now guards `FLAG_IMMUTABLE` |
| FACT-A010 | Legacy `inferProfile` can never return PHONE (touch->tablet) | `DeviceProfile.java:177-179` + `DeviceProfileTest:28-33` |
| FACT-A011 | No on-device contrast guard existed; server validates only | `admin-api.mjs:182-189,216` vs 0 hits in `launcher-android/**/*.java` pre-branch |
| FACT-A012 | `ThemeCustomizationTest` existed but never executed | `project.tests.ps1` pre-branch had no `java -cp ...ThemeCustomizationTest` |
| FACT-A013 | No SAF strategy, no `contentId` catalog key, no coreId map existed | `grep SAF`=0, `CatalogStore.merge` keyed by path, `grep pcsx_rearmed`=0 pre-branch |
| FACT-A014 | 23/23 launcher suites green on Linux (pwsh+javac 11) | `pwsh -File launcher-android/tests/project.tests.ps1`, exit 0 |
| FACT-A015 | Gradle APK build blocked here: AGP needs Java 17 (only 11 present), no Android SDK | `gradle :app:assembleDebug` output |
| FACT-A016 | `feature/cloud-admin` = `5812d13`, 8 commits ahead of `068bf51` | `git log --oneline 068bf51..feature/cloud-admin` |
| FACT-A017 | Branch `feat/android-universal-gaming-redesign` created from `5812d13`; `MainActivity` untouched | `git status` / branch diff |
| FACT-A018 | Increment 2 (same branch per maestro): SafLocation, DownloadStateMachine, AccessibilityAnnouncer, FavoriteIdentity, WindowSizeClass, ThemeTokens + SAF_TREE/CLOUD_CACHE types + manifest `<queries>` for RetroArch | branch diff, `AndroidManifest.xml` |
| FACT-A019 | 29/29 launcher suites green on Linux (pwsh+javac 11) | `pwsh -File launcher-android/tests/project.tests.ps1`, exit 0 |
