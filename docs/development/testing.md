# Estratégia de testes

## Testes existentes

- `launcher-android/tests/project.tests.ps1`: manifesto, recursos, catálogo de 99 itens, capas, responsividade, atalhos e estado.
- `launcher-android/tests/LauncherStateTest.java`: restauração e limites do foco de jogos.
- `launcher-android/tests/ThemeStateTest.java`: troca, normalização e intervalo do carrossel.
- `manager-windows/tests/Test-Manager.ps1`: funções e controles necessários do Manager.
- `scripts/Test-PublicLayout.ps1`: estrutura e exclusão de conteúdo privado.
- `scripts/Verify-PublicRelease.ps1`: suíte pública combinada.

## Validação manual

Teste conexão ADB, autorização na TV, abertura de uma plataforma, busca, troca de slide, abertura no RetroArch e retorno pelo atalho do controle.
