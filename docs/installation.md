# Instalação detalhada

## Fire TV

Ative as opções de desenvolvedor e `ADB Debugging`. A Amazon documenta o caminho de conexão e o endereço de rede no [Developer Tools Menu](https://developer.amazon.com/docs/fire-tv/developer-tools.html).

## Windows

Instale PowerShell 7, Java, Platform Tools e o SDK Android. Clone o repositório e abra o Manager. A rede local é usada somente para ADB; o projeto não cria um servidor.

## ADB

```powershell
adb connect <IP_DO_FIRE_TV>:5555
adb devices
```

Aceite a chave RSA na TV. Se o aparelho aparecer como `unauthorized`, aceite a tela e repita `adb devices`.

## APK

Gere o APK com SDK/API 28 e um keystore externo. Depois, no Manager, instale-o no aparelho. A instalação via `adb install` é o fluxo documentado pela Amazon em [Install and Run Your App](https://developer.amazon.com/docs/fire-tv/installing-and-running-your-app.html).

## Primeira execução

Abra `Jogos Retro`, selecione uma plataforma, confirme se as capas aparecem e teste um jogo autorizado. Em seguida mapeie o controle no RetroArch e valide o atalho de saída.
