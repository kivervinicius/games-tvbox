# ADR-005: Android Build System Migration

## Contexto
O build Android era executado através do script PowerShell `Build-FireRetro.ps1`, que chamava diretamente `aapt2.exe`, `javac.exe`, `d8.bat`, `zipalign.exe` e `apksigner.bat` com caminhos Windows fixos. Isso impedia compilação em servidores de CI Linux, exigia catalogar manualmente todos os arquivos `.java` no script e impedia uso de bibliotecas padrão via Gradle.

## Decisão
Migrar o projeto Android para o sistema de build padrão da indústria: Gradle com o Android Gradle Plugin (AGP).
- Configuração de `build.gradle` na raiz do launcher e no módulo `app`.
- Suporte a variantes de build (flavors: `tv` e `gamer`, tipos: `debug` e `release`).
- O script legado PowerShell é mantido como wrapper para retrocompatibilidade local onde necessário, mas a fonte oficial de compilação torna-se o Gradle reproduzível.

## Consequências
- Compilação consistente em qualquer ambiente (Linux, macOS, Windows).
- Integração facilitada em pipelines de CI/CD (GitHub Actions).
- Suporte nativo a testes unitários via JUnit/Robolectric.

## Status
Aceito.
