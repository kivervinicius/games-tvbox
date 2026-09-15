# Manager e ADB

## Status

Disponível no Windows.

## Operações

O Manager conecta por ADB, mostra modelo/ABI/armazenamento, abre ou reinicia o launcher, seleciona a pasta de ROMs, salva catálogo no cache e envia temas.

## Implementação e evidência

As funções estão em `manager-windows/FireRetroManager.ps1`; `manager-windows/tests/Test-Manager.ps1` verifica o contrato das operações e da interface.
