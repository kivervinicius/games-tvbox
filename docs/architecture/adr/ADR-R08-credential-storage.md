# ADR-R08: Armazenamento Seguro de Credenciais Cross-Platform

## Contexto
O importador necessita armazenar o token de autenticação de dispositivo obtido no fluxo de pareamento com a nuvem (`CloudPublisherClient`). A implementação legada utilizava a classe `System.Security.Cryptography.ProtectedData`, que recorre à API DPAPI do Windows. No Linux, a chamada a essa API lança uma exceção `PlatformNotSupportedException`, quebrando a execução do importador.

## Decisão
Criar uma abstração `ICredentialStore` e adotar provedores por plataforma com fallback criptografado seguro:
```text
                    ICredentialStore
                           │
       ┌───────────────────┴───────────────────┐
       ↓                                       ↓
WindowsCredentialStore                 LinuxCredentialStore
(DPAPI via ProtectedData)               (FreeDesktop Secret Service
                                         ou AES-GCM com chave derivada
                                         de máquina/usuário)
```

1. **Interface `ICredentialStore`**:
   - Métodos: `Save(string key, string secret)`, `Load(string key)`, `Clear(string key)`.
2. **Implementação Windows**:
   - Continua utilizando DPAPI com escopo `DataProtectionScope.CurrentUser` e entropia adicional.
3. **Implementação Linux / Portátil**:
   - Utiliza cifra autenticada AES-256-GCM.
   - Derivação de chave usando PBKDF2/Argon2 baseada em identificador de máquina (`/etc/machine-id` ou `dbus-uuid`) combinado com o UID do usuário (`getuid`) e salt fixo de aplicação.
   - Permissões de arquivo estritas (`0600` - apenas leitura/escrita pelo proprietário).
   - Suporte transparente para ambientes de container ou CI onde o Secret Service de desktop não esteja em execução.

## Consequências
- Código unificado: a camada de aplicação utiliza apenas `ICredentialStore`.
- Segurança preservada em ambos os sistemas operacionais sem comprometer a portabilidade.

## Status
Aceito.
