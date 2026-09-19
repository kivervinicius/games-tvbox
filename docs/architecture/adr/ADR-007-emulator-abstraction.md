# ADR-007: Emulator Provider Abstraction

## Contexto
O aplicativo continha referências fixas ao RetroArch 32-bit (`com.retroarch.ra32`), assumindo caminhos fixos de configuração e playlists. Dispositivos de 64-bit exigem `com.retroarch` ou `com.retroarch.a64`, e certos sistemas rodam com desempenho superior em emuladores dedicados (*standalone*), como DuckStation, PPSSPP ou Mupen64Plus.

## Decisão
Definir o contrato `EmulatorProvider`:
```java
public interface EmulatorProvider {
    String getId();
    String getDisplayName();
    boolean isAvailable(Context context);
    boolean canRun(Game game, DeviceProfile profile);
    Intent createLaunchIntent(Context context, Game game, File romFile);
}
```
Implementações iniciais:
1. `RetroArchProvider`: detecta automaticamente variantes instaladas (`com.retroarch.ra32`, `com.retroarch.a64`, `com.retroarch`) e monta a Intent correta passando o core Libretro adequado.
2. `StandaloneEmulatorProvider`: mapeia emuladores independentes específicos para sistemas que demandem execução dedicada.

## Consequências
- Desacoplamento estrutural: a UI nunca faz chamadas diretas com strings hardcoded do pacote do RetroArch.
- Compatibilidade nativa com aparelhos 32-bit (Fire Stick básico) e 64-bit (TVs modernas, tablets e celulares gamer).
- Capacidade de expansão futura para novos emuladores sem alterar a aplicação central.

## Status
Aceito.
