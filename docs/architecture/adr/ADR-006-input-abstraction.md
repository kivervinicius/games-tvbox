# ADR-006: Input Abstraction & Semantic GameAction

## Contexto
O tratamento de botões de controle, teclado e touch estava disperso dentro de métodos gigantes em `MainActivity.java` (`onKeyDown`, `dispatchGenericMotionEvent`). Isso tornava a interface rígida, dificultava remapeamentos e impedia suporte transparente a novos controles (gamepads Bluetooth, controles USB, controles remotos de TV, tela sensível ao toque).

## Decisão
Introduzir uma camada de abstração de entrada:
- Enum canônico `GameAction`: `UP`, `DOWN`, `LEFT`, `RIGHT`, `ACCEPT`, `BACK`, `MENU`, `SEARCH`, `FAVORITE`, `QUICK_SETTINGS`.
- `InputManager` central: recebe eventos brutos do Android (`KeyEvent`, `MotionEvent`) e consulta adaptadores especializados (`GamepadAdapter`, `RemoteAdapter`, `TouchAdapter`) para emitir ações semânticas `GameAction` para a UI.
- Suporte a remapeamento configurável por perfil de controle.

## Consequências
- Telas de UI reagem a ações semânticas e não a códigos de tecla físicos.
- Suporte simultâneo a controle remoto D-pad, gamepad analógico/digital e touch gestures.
- Facilidade de teste unitário simulando ações sem necessidade de instanciar `InputDevice` real.

## Status
Aceito.
