# Sincronização online independente

Cada Fire Stick pode manter sua própria cópia local da biblioteca e consultar um manifesto em um repositório privado do GitHub. Depois da configuração inicial, o Windows não precisa permanecer ligado.

Ao abrir **Jogos Retro**, e enquanto a tela estiver ativa, o aplicativo consulta o manifesto. Jogos novos são baixados um por vez quando há espaço livre suficiente. O arquivo é salvo primeiro como temporário, passa por validação de tamanho e SHA-256 e só então é movido para a pasta de ROMs. Uma falha de rede, credencial ou espaço não remove o que já funciona.

Durante o download, a galeria continua navegável. O topo mostra o jogo atual, porcentagem e fila; cada card informa se está instalado, baixando, pendente por espaço ou com erro. Depois da instalação, o card recebe **Novo** por sete dias naquele Fire Stick.

## Configuração

1. No Manager, publique o manifesto privado e os assets em um GitHub Release.
2. Instale a versão atualizada do launcher em cada Fire Stick.
3. No card **Sincronização online**, informe a URL HTTPS do `library.manifest.json` e um token GitHub de leitura restrito ao repositório privado.
4. Salve. O token é protegido pelo Android Keystore e não é incluído no catálogo, nos logs ou nos intents do RetroArch.

O Pages público continua sendo apenas uma galeria de metadados e capas autorizadas. ROMs comerciais, saves e links privados ficam no repositório privado.

## Limites

O Fire Stick precisa de internet para descobrir e baixar novidades, mas os jogos já instalados continuam funcionando offline. O armazenamento reservado para o sistema é mantido durante cada download. Arquivos incompletos não aparecem como jogos jogáveis.
