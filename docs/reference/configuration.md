# Referência de configuração

| Variável / arquivo | Obrigatório | Default | Descrição | Exemplo |
|---|---:|---|---|---|
| `ANDROID_HOME` | não | `./android-sdk` | raiz do SDK Android | `C:\Android\Sdk` |
| `FIRERETRO_KEYSTORE` | para release | nenhum | caminho externo do keystore | `C:\seguro\app.jks` |
| `FIRERETRO_KEY_ALIAS` | para release | nenhum | alias da chave | `games-tvbox` |
| `FIRERETRO_KEY_PASSWORD` | para release | nenhum | senha local da chave | mantida fora do Git |
| `examples/catalog.example.json` | não | catálogo embutido | modelo de jogos | copiar e editar |
| `examples/theme/slides.example.json` | não | tema embutido | modelo de carrossel | copiar e editar |
| `%LOCALAPPDATA%\FireRetroManager\metadata-provider.json` | não | nenhum | configuração local do provedor de metadados e capas | copiar o exemplo e configurar localmente |
| `%LOCALAPPDATA%\FireRetroManager\metadata.json` | não | nenhum | cache de metadados normalizados | gerado pelo Manager |
| `%LOCALAPPDATA%\FireRetroManager\covers\` | não | nenhum | cache local das capas baixadas | gerado pelo Manager |

O Manager grava dados reais em `%LOCALAPPDATA%\FireRetroManager`; esse diretório não deve ser commitado.

Para enriquecer o catálogo, copie `examples/metadata-provider.example.json` para `%LOCALAPPDATA%\FireRetroManager\metadata-provider.json` e informe a URL do seu provedor. `apiKey` é opcional e deve ficar somente nesse arquivo local; ela não entra no catálogo sincronizado, no Fire Stick ou em `catalog.public.json`. Sem esse arquivo, ou quando a rede falhar, o Manager conserva o nome, as tags e a capa já existentes e continua a sincronização.
