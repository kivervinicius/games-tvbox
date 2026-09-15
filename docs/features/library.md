# Biblioteca visual

## Status

Disponível.

## Para que serve

Mostra os jogos em cards com capa, nome e seção da plataforma, reduzindo a necessidade de navegar pelos menus internos do RetroArch.

## Implementação e evidência

O layout está em `launcher-android/app/src/main/java/com/kiver/fireretro/MainActivity.java`, nos métodos `renderSections` e `addCard`. O contrato de 99 itens e as capas são verificados em `launcher-android/tests/project.tests.ps1`.

## Limitações

Os arquivos de jogos continuam sendo responsabilidade do usuário e precisam existir no caminho do catálogo.
