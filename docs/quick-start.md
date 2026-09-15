# Quick Start

Este caminho usa o mínimo de passos para testar o valor principal do produto.

## Requisitos

Tenha Windows 10/11, PowerShell 7, Platform Tools, Java 8+, um Fire TV na mesma rede e um RetroArch compatível. Habilite `ADB Debugging` nas opções de desenvolvedor do Fire TV.

## 1. Obter e preparar

```powershell
git clone https://github.com/kivervinicius/games-tvbox.git
cd games-tvbox
adb version
```

Instale o ADB conforme a [documentação oficial do Android](https://developer.android.com/tools/adb).

## 2. Conectar

Abra o Manager:

```powershell
manager-windows\Start-FireRetroManager.cmd
```

Informe o endereço do Fire TV, conecte por ADB e aceite a autorização exibida na televisão na primeira vez.

## 3. Gerar o launcher

Configure `ANDROID_HOME` ou passe `-SdkRoot`. O build precisa de um keystore fora do repositório:

```powershell
$env:FIRERETRO_KEYSTORE = 'C:\caminho\seguro\games-tvbox.jks'
$env:FIRERETRO_KEY_ALIAS = 'games-tvbox'
$env:FIRERETRO_KEY_PASSWORD = '<senha-local>'
pwsh -File scripts\Build-FireRetro.ps1
```

## 4. Validar

```powershell
pwsh -File scripts\Verify-PublicRelease.ps1
```

O resultado esperado contém `PASS` para layout, launcher, Manager e auditoria pública.

## 5. Usar

No Manager, escolha a pasta do seu acervo autorizado e instale o APK gerado. Abra `Jogos Retro`, selecione uma plataforma, escolha uma capa e pressione A para iniciar.

Se o controle não estiver mapeado, siga [controle e saída](user/controller.md). Se algo falhar, consulte [troubleshooting](troubleshooting.md).
