# ADR-R02: Estratégia de Resolução de Aquisição (API > HTML > BROWSER)

## Contexto
O portal Retrostic está protegido por camadas de borda (Cloudflare) que podem impor desafios TLS/JavaScript a clientes não navegadores em ambientes de nuvem/datacenter, enquanto clientes residenciais ou navegadores padrão trafegam sem impedimentos. Além disso, o Retrostic evolui de um catálogo HTML para uma API estruturada v1. Precisamos de uma estratégia de aquisição que funcione de forma resiliente em qualquer ambiente, sem emperrar o pipeline.

## Decisão
Implementar uma cadeia de resolução em 3 camadas (`API > HTML > BROWSER`) orquestrada pelo `RetrosticSourceProvider`:
1. **Tier 1 - RetrosticApiResolver (Prioridade Máxima)**:
   - Se configurado endpoint da API oficial (`RETROSTIC_API_V1.md`) ou token de parceiro, consulta diretamente os endpoints JSON REST.
   - Fornece respostas determinísticas em milissegundos com tickets de download assinados e metadados estruturados.
2. **Tier 2 - RetrosticHtmlResolver (Fallback Padrão)**:
   - Utiliza `HttpClient` com cabeçalhos realistas de navegador (User-Agent, Accept, Referer, Cookies).
   - Realiza raspagem das páginas de busca, detalhes e contagem regressiva de download.
   - Suportado por testes com fixtures HTML/JSON sanitizadas determinísticas.
3. **Tier 3 - RetrosticBrowserResolver (Fallback de Segurança)**:
   - Contrato para ponte de automação de navegador (headless Playwright ou WebView2 do sistema).
   - Ativado se o resolver HTML detectar bloqueio de bot (Cloudflare Turnstile/challenge) ou JavaScript complexo para gerar o ticket de download.

## Consequências
- Máxima robustez: ambientes com API utilizam o caminho rápido; ambientes com desafio de borda utilizam a ponte de navegador.
- Independência de ambiente: pipelines de CI rodam com fixtures determinísticas sem falhas espúrias causadas por bloqueio de IP.

## Status
Aceito.
