# Referência de configuração

| Variável / arquivo | Obrigatório | Default | Descrição | Exemplo |
|---|---:|---|---|---|
| `ANDROID_HOME` | não | `./android-sdk` | raiz do SDK Android | `C:\Android\Sdk` |
| `FIRERETRO_KEYSTORE` | para release | nenhum | caminho externo do keystore | `C:\seguro\app.jks` |
| `FIRERETRO_KEY_ALIAS` | para release | nenhum | alias da chave | `games-tvbox` |
| `FIRERETRO_KEY_PASSWORD` | para release | nenhum | senha local da chave | mantida fora do Git |
| `examples/catalog.example.json` | não | catálogo embutido | modelo de jogos | copiar e editar |
| `examples/theme/slides.example.json` | não | tema embutido | modelo de carrossel | copiar e editar |

O Manager grava dados reais em `%LOCALAPPDATA%\FireRetroManager`; esse diretório não deve ser commitado.
