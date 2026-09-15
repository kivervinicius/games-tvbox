# Desenvolvimento e build

## Estrutura

O módulo Android está em `launcher-android/app`. O Manager é PowerShell em `manager-windows`. O wrapper `scripts/Build-FireRetro.ps1` chama o build Android usando caminhos relativos.

## Dependências

Instale Java 8+, Android SDK com plataforma API 28 e Build Tools 35.0.0. Configure `ANDROID_HOME` ou passe `-SdkRoot`. A documentação da Amazon recomenda JDK e Android Studio para desenvolvimento Fire TV: [Setting Up Your Development Environment](https://developer.amazon.com/docs/fire-tv/setting-up-your-development-environment.html).

## Assinatura

Forneça um keystore externo:

```powershell
pwsh -File scripts\Build-FireRetro.ps1 `
  -SdkRoot $env:ANDROID_HOME `
  -JavaRoot 'C:\Program Files\Java\jdk-17' `
  -KeystorePath 'C:\seguro\games-tvbox.jks' `
  -KeystoreAlias 'games-tvbox' `
  -KeystorePassword '<senha-local>'
```

O build não cria nem versiona chaves.

## Testes

```powershell
pwsh -File launcher-android\tests\project.tests.ps1
pwsh -File manager-windows\tests\Test-Manager.ps1
pwsh -File scripts\Verify-PublicRelease.ps1
```

Os testes compilam `LauncherState` e `ThemeState`, verificam o manifesto, capas, catálogo e contratos do Manager.
