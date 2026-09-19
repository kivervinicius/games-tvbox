# Roteiro de Aceite — Android Gamer / Handheld Mode

## 1. Contexto e Equipamentos
- **Perfis Alvo**: `ANDROID_GAMER`, `ANDROID_TABLET`, `ANDROID_PHONE`.
- **Dispositivos de Referência**: Portáteis dedicados (AYN Odin 2, Retroid Pocket 4/5, Anbernic Android), Tablets Gamer (Lenovo Legion Y700, Galaxy Tab), Celulares com controle telescópico (Razer Kishi, GameSir G8, Backbone).

## 2. Pré-requisitos
1. Launcher instalado (`com.kiver.fireretro` ou variante `gamer`).
2. RetroArch Plus (`com.retroarch.a64` para 64-bit ou `com.retroarch.ra32` para 32-bit).
3. Cartão MicroSD ou armazenamento interno configurado.

## 3. Passo a Passo de Validação (Passo / Ação / Resultado Esperado)

| Passo | Ação | Resultado Esperado | Status |
|---|---|---|---|
| 1 | Instalação universal sem bloqueio Leanback | O APK instala sem erro em smartphones, tablets e consoles portáteis modernos (Android 10 a 14) devido a `leanback required="false"` e `touchscreen required="false"` | OK |
| 2 | Detecção do Perfil Gamer | O motor infere `DeviceProfile.inferProfile(...)` identificando `ANDROID_GAMER` (ou tablet/phone), configurando orientação paisagem e margens compactas (8-16dp) | OK |
| 3 | Reconhecimento de Hardware de Input | O sistema detecta se há controle físico conectado. Caso não haja, habilita o fallback touch para gestos (swipes para navegação, toques para aceitar) | OK |
| 4 | Pareamento com Scopes Estendidos | Ao parear, o administrador no Cloudflare Access concede os escopos `gamer` (`catalog.read`, `asset.download`, `device.state.write`, `apps.install`, `updates.read`) | OK |
| 5 | Gamer Dashboard & Indicadores | Pressionando o botão Start ou botão HUD na tela, abre-se o overlay de status com nível de bateria, carregamento, espaço livre formatado e alternância de layout (Xbox vs Nintendo) | OK |
| 6 | Sincronização e Filtragem por Compatibilidade | O `CompatibilityEngine` filtra conteúdos nativos por ABI (e.g. `arm64-v8a`) e verifica espaço disponível antes de sugerir downloads | OK |
| 7 | Armazenamento Flexível via `RomStorageResolver` | Downloads priorizam cartão MicroSD (`RemovableStorageStrategy`) ou armazenamento em sandbox de app (`AppStorageStrategy`), contornando restrições de Scoped Storage do Android moderno | OK |
| 8 | Resolução Automática do Provedor de Emulação | O `RetroArchProvider` detecta automaticamente a instalação de `com.retroarch.a64` em dispositivos 64-bit, ajustando o diretório de cores para `/data/data/com.retroarch.a64/cores/` | OK |
| 9 | Execução e Retorno ao Hub | O jogo roda em taxa de quadros plena; ao acionar a combinação de saída configurada (combo 7 / combo 4), o jogador retorna ao hub sem perda de estado | OK |
| 10 | Lista de Jogos Recentes e Favoritos | O jogo jogado entra para o topo da lista de recentes e a marcação de favoritos persiste no estado local | OK |
| 11 | Resiliência e Operação 100% Offline | Todas as funcionalidades essenciais (jogar, trocar temas, gerenciar favoritos, abrir apps nativos) continuam operando sem conexão com a internet | OK |
