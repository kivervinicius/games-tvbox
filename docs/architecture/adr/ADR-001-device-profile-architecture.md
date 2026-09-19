# ADR-001: Device Profile Architecture

## Contexto
O launcher foi originalmente projetado para Amazon Fire TV Stick. Com a expansão para Android TV, TVs TCL, tablets, smartphones e a variante Android Gamer, não podemos recorrer a dezenas de condicionais `if (isFireStick)` ou `if (isTablet)` espalhadas pelo código. Dispositivos diferem em tamanho de tela, métodos de entrada (D-pad vs Gamepad vs Touch), APIs do sistema e capacidades de armazenamento.

## Decisão
Implementar uma arquitetura de perfis de dispositivo (`DeviceProfile`) baseada em capacidades reais descobertas em tempo de execução:
```text
Operating System / Platform (Android, Fire OS, futuros: Linux/Windows)
        ↓
Runtime Capabilities (ABI, API level, touch, gamepad, dpad, storage, display)
        ↓
Device Profile (FIRE_TV, ANDROID_TV, ANDROID_TV_TCL, ANDROID_TABLET, ANDROID_PHONE, ANDROID_GAMER)
        ↓
UX / Input / Storage / Runtime Adaptations
```

## Consequências
- Código compartilhado entre todas as variantes Android (`shared core`).
- Adição de novos perfis ou suporte a novos fabricantes sem modificar a lógica central.
- As UIs adaptam-se com base nas capacidades (ex: suporte a touch fallback quando gamepad não está conectado).
- Backend pode sugerir ou atribuir perfis sem amarrar a marca/modelo específico.

## Status
Aceito.
