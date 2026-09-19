# ADR-004: Android Storage Abstraction

## Contexto
O launcher Android possuía caminhos codificados diretamente em código para `/sdcard/roms`, `/sdcard/RetroArch/...` e dependia das permissões legadas `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` em `targetSdkVersion="28"`. Dispositivos mais recentes (Android 10+, 11+, 12+) implementam Scoped Storage e restringem acesso arbitrário a `/sdcard`.

## Decisão
Criar uma abstração `RomStorage` com estratégias desacopladas:
1. `AppStorageStrategy`: Armazena ROMs no diretório padrão do aplicativo (`context.getExternalFilesDir("roms")`), dispensando permissões especiais em qualquer versão do Android.
2. `LegacyExternalStorageStrategy`: Mantém suporte retrocompatível a `/sdcard/roms` em aparelhos antigos (API <= 28) ou quando concedida permissão explícita.
3. `RemovableStorageStrategy` e `UsbStorageStrategy`: Detecta e utiliza pendrives USB ou cartões SD formatados como armazenamento removível.

A aplicação preserva as ROMs existentes no local legado sem apagá-las e seleciona dinamicamente a melhor estratégia com base nas capabilities do dispositivo.

## Consequências
- Compatibilidade com versões modernas do Android (API 29 a 34+) sem quebrar instalações legadas.
- Zero risco de perda de arquivos ou saves existentes.
- Suporte fluido a expansão via armazenamento USB.

## Status
Aceito.
