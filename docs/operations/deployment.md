# Operação e deployment

## Sideload

O fluxo suportado é instalar o APK por ADB Wi-Fi no Fire TV. A Amazon documenta `adb connect` e `adb install` no guia [Install and Run Your App](https://developer.amazon.com/docs/fire-tv/installing-and-running-your-app.html).

## Atualização

Use instalação de atualização mantendo o mesmo pacote e assinatura. O launcher e o Manager não removem ROMs ou saves. O tema externo pode ser reenviado pelo Manager após uma reinstalação limpa.

## Rollback

Mantenha o APK anterior assinado pela mesma chave. Instale-o sobre a versão atual; se a assinatura for diferente, remova somente o app depois de confirmar que os dados do usuário estão preservados.

## Autostart

O launcher é exposto como atividade Leanback. O Fire OS decide a apresentação do card na Home; o projeto não substitui o launcher da Amazon.
