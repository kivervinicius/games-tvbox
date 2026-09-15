# Troubleshooting

## ADB não conecta

Confirme a mesma rede, depuração ADB habilitada e autorização aceita na TV. Use `adb devices` e `adb connect <IP_DO_FIRE_TV>:5555`.

## APK não compila

Verifique `ANDROID_HOME`, API 28, Build Tools 35.0.0, Java e os três parâmetros do keystore externo. O projeto não fornece uma chave privada.

## Launcher abre sem imagens

Confira os nomes em `slides.json`, a existência das capas e a pasta remota `/sdcard/Android/data/com.kiver.fireretro/files/theme`. Reinicie o app depois de salvar o tema.

## Controle não responde

Refaça `Set All Controls` no RetroArch e salve o perfil. O pareamento Bluetooth e autorizações do Fire OS precisam ser confirmados na televisão.

## Jogo não abre

Valide caminho, extensão, plataforma e core no catálogo. Teste um item pequeno da mesma plataforma e preserve a configuração anterior antes de alterar arquivos.

## Falta espaço

Remova somente caches conhecidos e mova a biblioteca para USB quando possível. Não apague saves ou pastas de configuração para liberar espaço.
