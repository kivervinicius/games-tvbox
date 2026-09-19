# Registro de Riscos Arquiteturais — Gaming Platform Multi-Device

Este registro cataloga os riscos técnicos associados à evolução do projeto, sua severidade, probabilidade e estratégias ativas de mitigação.

---

| ID | Risco | Severidade | Probabilidade | Mitigação |
| :--- | :--- | :--- | :--- | :--- |
| **RSK-001** | Quebra de retrocompatibilidade com dispositivos Fire TV existentes já instalados | Alta | Baixa | Preservar estruturas existentes de arquivos JSON locais (`games.json`, `apps.json`, `prefs`). Manter suporte a RetroArch 32-bit (`com.retroarch.ra32`). |
| **RSK-002** | Perda de dados ou sobrescrita acidental de saves e configurações RetroArch | Crítica | Baixa | Proibição explícita de comandos destrutivos (`rm`, `wipe`). Storage strategies escrevem apenas em pastas dedicadas a ROMs, nunca tocando na pasta `saves` ou `states` do RetroArch. |
| **RSK-003** | Incompatibilidade de armazenamento no Android 11+ (Scoped Storage / API 30+) | Alta | Média | Implementar `RomStorage` com fallback de `AppStorageStrategy` (armazenamento específico do app que não exige `MANAGE_EXTERNAL_STORAGE`) somado a `LegacyExternalStorageStrategy` e `RemovableStorageStrategy`. |
| **RSK-004** | Bloqueio de instalação em smartphones/tablets devido a leanback flag | Alta | Baixa | Alterar `<uses-feature android:name="android.software.leanback" android:required="false" />` no manifesto, permitindo instalação universal. |
| **RSK-005** | Race condition e inconsistência no controle de cota e pareamento no Cloudflare KV | Alta | Média | Implementar `LibraryCoordinator` para transações atômicas com consistência forte, mantendo KV apenas como camada de leitura rápida. |
| **RSK-006** | Esgotamento de limites gratuitos da Cloudflare (Workers KV writes / R2 Class A requests) | Média | Alta | Eliminar scan integral do R2 em cada upload (`calculateStorage`). Fazer contagem transacional incremental de bytes e objetos. |
| **RSK-007** | Falha de compilação em ambientes de desenvolvimento sem Windows | Média | Alta | Introduzir build Gradle padronizado independente de sistema operacional, substituindo o script artesanal PowerShell com caminhos Win32. |
| **RSK-008** | Inconsistência entre plataformas suportadas no Importer vs Backend | Média | Média | Fonte única de verdade no `PlatformRegistry` canônico, compartilhado e consumido por Importer e Cloud. |
| **RSK-009** | Download corrompido ou arquivo truncado em conexões instáveis de TV | Alta | Média | Verificação rigorosa de SHA-256 pós-download antes de renomear o arquivo temporário `.part` para o nome final da ROM. |
| **RSK-010** | Bypass de autorização por tokens forjados ou sem escopo | Crítica | Baixa | Tokens criptograficamente aleatórios assinados/hasheados, associados a scopes validados em cada endpoint do backend. |
