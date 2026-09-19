# ADR-R03: Download Manager Persistente com Suporte a Pause/Resume e Retry Inteligente

## Contexto
Jogos retrô, especialmente de consoles baseados em CD-ROM (como PlayStation 1), variam entre 200 MB e 700 MB por disco. Downloads grandes estão sujeitos a quedas de conexão, latência variável, expiração de tokens temporários de CDN e fechamentos inesperados do aplicativo. O importador original não possuía nenhum mecanismo de download de rede.

## Decisão
Criar o módulo `JogosRetro.Downloads` como uma biblioteca de engenharia de download resiliente e desacoplada:
1. **Máquina de Estados de Job**:
   - Estados: `QUEUED`, `RESOLVING`, `DOWNLOADING`, `PAUSED`, `VERIFYING`, `COMPLETED`, `FAILED`, `CANCELLED`.
2. **Sondagem HTTP Range**:
   - Antes de iniciar, emite um `HEAD` ou `GET` com Range para detectar suporte a `206 Partial Content`.
   - Se suportado, grava o progresso em `{jobId}.part` no diretório de staging.
3. **Resumption Inteligente e Renovação de Ticket**:
   - Ao retomar um download pausado ou interrompido por desconexão, inicia a partir dos bytes já gravados em disco (`Range: bytes={existingLength}-`).
   - Se o servidor responder `403 Forbidden` ou `410 Gone` (URL assinada expirou durante a pausa), o `DownloadManager` aciona o `IGameSourceProvider` para re-resolver a URL e continuar a gravação no mesmo arquivo `.part`.
4. **Retry com Backoff Exponencial e Jitter**:
   - Falhas transitórias (HTTP 500, 502, 503, 504, timeout, reset de conexão) sofrem retry automático com backoff exponencial até o limite configurado (default: 5 tentativas).
5. **Persistência de Fila**:
   - O estado dos jobs é persistido em repositório JSON local (`downloads.json`), permitindo que a aplicação seja fechada e reaberta sem perda de estado.

## Consequências
- Confiabilidade total em transferências volumosas.
- Proteção da banda do usuário e do servidor de CDN (não reinicia do zero em caso de queda).
- Integração limpa com UI gráfica via `IProgress<DownloadProgressSnapshot>` e eventos de mudança de estado.

## Status
Aceito.
