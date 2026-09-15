# Diagnóstico operacional

## Comandos seguros

```powershell
adb devices
adb connect <IP_DO_FIRE_TV>:5555
adb shell getprop ro.product.model
adb shell getprop ro.product.cpu.abi
adb shell df -h /sdcard
```

Use o Manager para executar essas verificações pela interface. Não publique a saída se ela revelar IP, identificador do aparelho ou nome de usuário.

## Evidência visual

O menu de ferramentas do Fire TV pode ser aberto pelo controle ou por ADB, conforme a documentação oficial da Amazon em [Developer Tools Menu](https://developer.amazon.com/docs/fire-tv/developer-tools.html).
