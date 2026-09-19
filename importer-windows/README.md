# Jogos Retro Importer para Windows

O Importer prepara jogos do seu próprio acervo e publica o resultado na biblioteca privada. Ele não move, renomeia nem apaga o arquivo escolhido. Para PlayStation, extrai `.7z`/`.zip`, valida `BIN/CUE`, converte para CHD, executa `chdman verify`, tenta localizar uma capa no projeto Libretro Thumbnails e só publica depois da revisão.

## Uso normal

1. Baixe `JogosRetroImporter-Setup.exe` ou `JogosRetroImporter-Portable.zip` na página de Releases.
2. Abra o Importer e mantenha o endereço do seu Worker Cloudflare.
3. Clique **Parear importador**. O navegador mostrará a aprovação; digite o código exibido pelo Importer.
4. Volte ao programa e clique **Concluir pareamento**. A credencial revogável fica protegida pelo DPAPI do Windows em `%LOCALAPPDATA%\JogosRetro\Importer`.
5. Escolha um arquivo, clique **Analisar e converter**, revise título, plataforma e capa e clique **Publicar na biblioteca**.
6. A TV recebe o card na próxima sincronização. A ROM só é baixada quando alguém seleciona **Instalar** na TV.

O programa envia o arquivo diretamente ao R2 por uma URL temporária. Ele nunca recebe as chaves do R2 ou a senha da Cloudflare. O Worker confere tamanho e SHA-256 antes de publicar o catálogo.

## Ferramentas incluídas

O pacote de Release inclui a biblioteca .NET de extração e `chdman.exe`. O arquivo `toolchain.lock.json` fixa origem, versão, licença e SHA-256. **Reparar ferramentas** só copia uma ferramenta incluída cuja assinatura coincida com esse arquivo. O cache fica fora da pasta do programa.

O GitHub Actions compila o chdman a partir da tag fixa `mame0289`, executa os testes, publica o aplicativo como Windows x64 autossuficiente e gera instalador, ZIP portátil, checksums, SBOM e licenças.

## Desenvolvimento

Requer .NET SDK 8 apenas para desenvolver. Usuários do instalador e do ZIP portátil não precisam instalar .NET, 7-Zip, Node, Python, PowerShell 7 ou Android SDK.

```powershell
dotnet restore JogosRetroImporter.sln --configfile NuGet.Config
dotnet run --project tests/JogosRetroImporter.Tests/JogosRetroImporter.Tests.csproj
dotnet build src/JogosRetroImporter/JogosRetroImporter.csproj -c Release
```

O arquivo original é lido com compartilhamento somente de leitura. Arquivos temporários e resultados ficam em `%LOCALAPPDATA%\JogosRetro\Importer`.
