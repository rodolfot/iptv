# TartaTV

Cliente Android TV para listas no padrão **Xtream Codes**. Reproduz canais ao vivo, filmes (VOD) e séries fornecidos pelo servidor cuja URL, usuário e senha o usuário insere no app. **TartaTV não distribui, hospeda nem comercializa conteúdo** — toda a exibição depende exclusivamente da lista que você inserir.

UI calibrada para TV 55", controle remoto e D-pad. Também roda em telefones e tablets.

---

## Sumário

- [Requisitos para usar](#requisitos-para-usar)
- [Como instalar](#como-instalar)
- [Como usar](#como-usar)
- [Funcionalidades](#funcionalidades)
- [Requisitos funcionais](#requisitos-funcionais)
- [Requisitos não funcionais](#requisitos-não-funcionais)
- [Para desenvolvedores](#para-desenvolvedores)
- [Privacidade e segurança](#privacidade-e-segurança)
- [Licenças](#licenças)

---

## Requisitos para usar

- Android TV, TV box, smartphone ou tablet com **Android 6.0 (API 23) ou superior**
- Conexão com internet (preferencialmente cabo / Wi-Fi 5 GHz para conteúdo HD/4K)
- **Uma lista IPTV no padrão Xtream Codes** — você precisa ter por conta própria: URL do servidor, usuário e senha. O TartaTV **não fornece** lista
- Para 4K/HDR: dispositivo com decoder HEVC em hardware (a maioria das TVs e celulares topo de linha tem; emuladores não)

---

## Como instalar

### Opção A — via APK (sideload)

1. Baixe o APK mais recente em [Releases](../../releases) (`tartatv-X.Y.Z.apk`)
2. **Em uma TV / TV box**:
   - Habilite **Fontes desconhecidas** em Configurações → Segurança
   - Copie o APK para um pendrive e abra com o gerenciador de arquivos da TV, **ou**
   - Instale via ADB de outro dispositivo na mesma rede:
     ```bash
     adb connect <ip-da-tv>:5555
     adb install tartatv-X.Y.Z.apk
     ```
3. **Em um celular/tablet**:
   - Abra o APK pelo navegador ou app de arquivos e confirme a instalação

### Opção B — build local

Veja [Para desenvolvedores](#para-desenvolvedores).

---

## Como usar

### 1. Primeira abertura — termos de uso

Na primeira execução, você verá a tela **Bem-vindo ao TartaTV**. Leia os Termos de Uso e a Política de Privacidade e clique em **Concordo e quero continuar**. Se não concordar, o app fecha. Esse aceite é solicitado uma única vez.

### 2. Login

Preencha:

- **URL do servidor**: ex. `http://meuservidor.com` (com `http://` ou `https://`)
- **Usuário**: o usuário fornecido pelo seu provedor Xtream
- **Senha**: a senha fornecida pelo seu provedor Xtream

Use o controle remoto para navegar (setas ↑↓←→ + OK). O teclado virtual aparece ao focar nos campos. **Após o primeiro login bem-sucedido, suas credenciais ficam salvas criptografadas e o app abre direto na Home.**

### 3. Navegação principal

A barra superior tem 7 abas. Use as setas para alternar:

| Aba | O que faz |
|---|---|
| **Início** | Continuar de onde parou (filmes/episódios em andamento) com barras de progresso |
| **Buscar** | Busca global em canais, filmes e séries (busca por nome, gênero, elenco e sinopse) |
| **Ao Vivo** | Lista de canais por categoria, com EPG (programa atual) e barra de progresso ao vivo |
| **Filmes** | Catálogo de VOD por categoria |
| **Séries** | Catálogo de séries por categoria |
| **Favoritos** | Canais, filmes e séries marcados como favoritos |
| **Config** | Servidor, PIN parental, atualizar catálogo, sair, **Sobre** |

### 4. Fluxos de uso

**Assistir a um canal ao vivo**
1. Aba **Ao Vivo** → escolha categoria → selecione o canal
2. Abre a tela de detalhe do canal: programa atual ("Agora") + grade dos próximos programas agrupados por dia
3. Botões: **Assistir** · **Favoritar** · **Voltar 30 min / 1h / 2h** (apenas para canais com `tv_archive`)
4. Se o canal não estiver em formato suportado pelo dispositivo (ex: 4K HEVC em emulador), o player mostra uma mensagem amigável

**Assistir a um filme**
1. Aba **Filmes** → escolha categoria → selecione o filme
2. Abre a tela de detalhe: poster, sinopse, elenco, direção, gênero, lançamento, avaliação
3. Botões: **Assistir** ou **Continuar de HH:MM:SS** + **Favoritar**

**Assistir a uma série**
1. Aba **Séries** → escolha categoria → selecione a série
2. Tela de temporadas. Se há um episódio em andamento ou um próximo a assistir, aparece o botão **Continuar T2E5** no topo
3. Selecione a temporada → escolha o episódio
4. Episódios assistidos aparecem com ✓; episódios pausados mostram porcentagem
5. **Autoplay**: ao acabar um episódio, o próximo da temporada começa automaticamente

**Buscar**
1. Aba **Buscar** → digite ao menos 2 letras
2. Resultados separados por **Canais / Filmes / Séries**
3. Filtros para limitar à categoria desejada
4. **Filtro local dentro de uma categoria**: ao abrir uma categoria, use o campo de busca acima da grade para filtrar pelos itens visíveis sem chamar a rede

**PIN parental**
1. Na primeira tentativa de abrir um canal/filme em categoria adulta, o app pede para criar um PIN (4–8 dígitos numéricos com confirmação)
2. PIN fica criptografado no dispositivo
3. Sessão desbloqueada vale por 15 minutos
4. Para alterar/redefinir, vá em **Config → Senha parental**

**Trocar de servidor sem fazer logout**
1. **Config → Servidor → Trocar servidor**
2. Edite URL/usuário/senha → **Testar conexão**
3. Quando o teste passar, **Salvar e reconectar** atualiza tudo e recarrega o catálogo

**Gerenciar múltiplos perfis (servidores)**
1. **Config → Servidor → Gerenciar perfis**
2. Adicione um novo perfil informando nome (ex: "Família"), URL, usuário, senha e — se quiser separar acesso adulto — um PIN parental dedicado a esse perfil
3. Use **Usar** para trocar de perfil; o app reconecta e recarrega o catálogo automaticamente

**Picture-in-Picture**
- Durante reprodução, pressione o botão Home do dispositivo. O player desliza para o canto da tela em PiP. (Requer Android 8.0+ e dispositivo com PiP habilitado.)

**Atualizar o app**
- Quando uma nova versão é publicada no GitHub Releases, o app mostra um diálogo na Home com botão **Abrir página de download**
- Você baixa o APK manualmente e reinstala (Android sideload)

### 5. Diagnóstico

Se algo der errado, vá em **Config → Sobre → Diagnóstico**. O app mantém um log local de crashes (até 256 KB, rotativo) que **nunca é enviado para nós**. Você pode copiar e enviar manualmente se quiser ajuda.

---

## Funcionalidades

- **Lista IPTV Xtream Codes**: login com URL/usuário/senha, validação de credenciais
- **Canais ao vivo** agrupados por categoria, com **EPG (XMLTV)**: programa atual com barra de progresso, programação dos próximos dias
- **Filmes (VOD)** com poster, sinopse, elenco, direção, gênero, avaliação, duração, retomada de onde parou
- **Séries** com temporadas, episódios, autoplay do próximo, retomada do último visto, marcações de assistido / em progresso
- **Continue Watching** (Início) consolidando filmes e séries em andamento
- **Busca global** por nome, gênero, elenco, sinopse — sobre cache local com índice FTS4
- **Filtro local dentro de categoria** (sem hit de rede)
- **Favoritos** para canais, filmes e séries, com filtro por tipo
- **PIN parental** com setup obrigatório no primeiro acesso a conteúdo adulto
- **Player ExoPlayer**: HLS, DASH, MP4, TS, HEVC (quando suportado pelo dispositivo)
- **Track picker**: troca de áudio, legenda e qualidade de vídeo durante reprodução
- **Picture-in-Picture** (Android 8+)
- **Time-shift** (`tv_archive`): voltar 30 min / 1 h / 2 h em canais que suportam
- **Cache offline** de catálogo (TTL 6h, atualizado em background) e metadados de detalhe (TTL 24h)
- **Auto-update** via GitHub Releases (notifica nova versão e abre a página de download)
- **Multi-perfil**: vários servidores Xtream salvos com troca rápida e PIN parental por perfil
- **Notificações**: novos episódios em séries favoritas, atualização de catálogo concluída, lembrete "continuar assistindo", erros do servidor
- **i18n**: pt-BR, en e es
- **Splash screen** com identidade do app
- **Diagnóstico local** opt-in (sem telemetria, sem analytics, sem coleta externa)
- **Edge-to-edge + safe-area**: layout respeita notch/Dynamic Island/gesture bar em celulares modernos

---

## Requisitos funcionais

| ID | Requisito |
|---|---|
| **RF-01** | O usuário deve aceitar Termos de Uso e Política de Privacidade antes do primeiro uso |
| **RF-02** | O usuário deve poder fazer login com URL, usuário e senha de uma lista Xtream Codes |
| **RF-03** | Após login bem-sucedido, as credenciais devem ser persistidas criptografadas e a sessão preservada entre execuções |
| **RF-04** | O usuário deve poder fazer logout, retornando à tela de login |
| **RF-05** | O usuário deve poder editar credenciais sem fazer logout, com validação prévia (Testar conexão) |
| **RF-06** | O catálogo de canais ao vivo, filmes e séries deve ser apresentado por categoria |
| **RF-07** | O sistema deve cachear o catálogo localmente e atualizá-lo em background a cada 6 horas quando online |
| **RF-08** | O sistema deve apresentar a programação atual (EPG) por canal quando disponível, com barra de progresso |
| **RF-09** | O usuário deve poder reproduzir canais ao vivo, filmes e episódios |
| **RF-10** | O sistema deve registrar o ponto de parada de filmes e episódios e oferecer retomada |
| **RF-11** | Após o término de um episódio, o próximo da temporada deve iniciar automaticamente |
| **RF-12** | O usuário deve poder marcar/desmarcar canais, filmes e séries como favoritos |
| **RF-13** | O usuário deve poder buscar conteúdo por nome, com escopo global ou por tipo (canais/filmes/séries) |
| **RF-14** | A busca em filmes e séries deve incluir gênero, elenco e sinopse além do título |
| **RF-15** | Dentro de uma categoria aberta, o usuário deve poder filtrar a lista visível por nome |
| **RF-16** | O acesso a categorias adultas deve exigir PIN parental, com criação obrigatória no primeiro acesso |
| **RF-17** | O usuário deve poder alterar o PIN parental nas Configurações |
| **RF-18** | O usuário deve poder ordenar listas (canais/filmes/séries/favoritos) por nome, data de adição, data de lançamento, ID ou avaliação |
| **RF-19** | Para canais com `tv_archive` ativo, o usuário deve poder retroceder 30 min, 1 h ou 2 h |
| **RF-20** | Durante a reprodução, o usuário deve poder selecionar faixas de áudio, legenda e qualidade de vídeo |
| **RF-21** | Ao sair do app durante a reprodução, o player deve entrar em modo Picture-in-Picture (em dispositivos compatíveis) |
| **RF-22** | O sistema deve verificar atualizações do app via GitHub Releases e oferecer redirecionamento para download |
| **RF-23** | O sistema deve registrar localmente exceções não capturadas e disponibilizá-las ao usuário em Configurações → Sobre → Diagnóstico |
| **RF-24** | O usuário deve poder visualizar Termos de Uso, Política de Privacidade e versão instalada na tela Sobre |

---

## Requisitos não funcionais

| ID | Requisito |
|---|---|
| **RNF-01** | **Compatibilidade**: minSdk 23 (Android 6.0), targetSdk 35 (Android 15) |
| **RNF-02** | **Plataforma**: deve rodar em Android TV (Leanback) e em telefones/tablets sem alteração de instalação |
| **RNF-03** | **Acessibilidade D-pad**: toda navegação deve ser viável via controle remoto / D-pad sem necessidade de toque |
| **RNF-04** | **Internacionalização**: textos da UI extraídos para `strings.xml` com traduções pt-BR e en |
| **RNF-05** | **Segurança de credenciais**: URL, usuário, senha e PIN parental devem ser persistidos com criptografia AES-256 (EncryptedSharedPreferences / Tink) |
| **RNF-06** | **Privacidade**: o app não deve enviar dados de uso, telemetria, analytics ou identificação para o desenvolvedor ou terceiros |
| **RNF-07** | **Backup**: backup automático do Android e device transfer não devem incluir credenciais ou cache |
| **RNF-08** | **Permissões**: o app só pode solicitar `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK` e `FOREGROUND_SERVICE` |
| **RNF-09** | **Desempenho de listas**: catálogos de até 20 mil itens devem ser navegáveis sem travamento perceptível, usando paginação por categoria + cache local |
| **RNF-10** | **Latência de busca**: busca textual deve responder em menos de 200 ms para um catálogo de 20 mil itens, usando índice FTS4 |
| **RNF-11** | **Resiliência de rede**: timeouts curtos (8 s connect, 20 s read), retry automático em conexões instáveis e fallback para cache stale quando uma chamada falhar |
| **RNF-12** | **Operação offline parcial**: após primeiro login bem-sucedido, navegação no catálogo deve funcionar mesmo sem rede usando o cache local |
| **RNF-13** | **Migrações de dados**: alterações de schema do banco devem usar migrações Room explícitas, preservando favoritos e progresso |
| **RNF-14** | **Tamanho do APK release**: minificado com R8 + `shrinkResources` |
| **RNF-15** | **Distribuição**: build de release assinado deve ser reproduzível via CI (GitHub Actions) com keystore via secret |
| **RNF-16** | **Atualizações de segurança**: dependências críticas (ExoPlayer, OkHttp, Tink) devem permitir atualização sem mudanças estruturais |
| **RNF-17** | **Logs locais**: registro de crashes não deve exceder 256 KB; nunca deixa o dispositivo automaticamente |
| **RNF-18** | **Tema**: UI dark fixa, otimizada para TV 55" e visualização à distância |
| **RNF-19** | **Player robusto**: erros de codec ou rede devem ser apresentados ao usuário como mensagens compreensíveis, não como crash ou tela preta |

---

## Para desenvolvedores

### Stack

Kotlin · Jetpack Compose for TV · Media3 ExoPlayer · Retrofit / OkHttp / Moshi · Room (com FTS4) · DataStore · EncryptedSharedPreferences (Tink) · Hilt · WorkManager · Coil

### Pré-requisitos

- **JDK 17** disponível para o Gradle (Android Studio Ladybug+ vem com JBR 21 embutido; se for buildar pelo terminal sem `JAVA_HOME` definido, descomente `org.gradle.java.home` no `gradle.properties`)
- **Android SDK** (compileSdk 35)
- **Android Studio Ladybug (2024.2)** ou superior — recomendado
- **AGP 8.7.3 · Gradle 8.9 · Kotlin 1.9.24 · Compose Compiler 1.5.14**

### Build local

```bash
# (opcional) defina ANDROID_HOME e local.properties apontando para o SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties

# APK debug
./gradlew :app:assembleDebug

# APK release (assinado com debug keystore por padrão)
./gradlew :app:assembleRelease

# Testes
./gradlew :app:testDebugUnitTest
```

APKs em `app/build/outputs/apk/{debug,release}/`.

### Release assinado

Para gerar APK assinado com sua própria chave:

1. Gere keystore (uma vez):
   ```bash
   keytool -genkey -v -keystore tartatv.jks -alias tartatv -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Em `~/.gradle/gradle.properties` (não commitar):
   ```
   RELEASE_KEYSTORE_FILE=/caminho/para/tartatv.jks
   RELEASE_KEYSTORE_PASSWORD=...
   RELEASE_KEY_ALIAS=tartatv
   RELEASE_KEY_PASSWORD=...
   ```
3. `./gradlew :app:assembleRelease` → APK assinado com sua chave permanente

### Release via GitHub Actions

O workflow `.github/workflows/release.yml` é disparado por tags `v*` e:

1. Decodifica o keystore do secret `RELEASE_KEYSTORE_BASE64`
2. Builda APK release assinado com versão derivada do tag
3. Cria GitHub Release e anexa o APK

Configure os secrets do repo:
- `RELEASE_KEYSTORE_BASE64` (saída de `base64 -w 0 tartatv.jks`)
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

E em `gradle.properties` (commitado), defina `UPDATE_REPO_OWNER` e `UPDATE_REPO_NAME` para que o app verifique atualizações no repositório correto. Depois:

```bash
git tag v1.0.0 && git push --tags
```

### Versionamento automático

`versionCode` é derivado de `git rev-list --count HEAD` e `versionName` de `git describe --tags`. Para sobrescrever, use `-PVERSION_CODE=N` e `-PVERSION_NAME=X.Y.Z`.

### Estrutura

```
app/src/main/java/com/iptv/app/
├─ data/
│  ├─ api/        # Retrofit (XtreamApi) + DTOs
│  ├─ cache/      # CatalogCacheRepository (SWR)
│  ├─ db/         # Room: entidades, DAOs, migrations
│  ├─ epg/        # XmltvParser + EpgRepository
│  └─ prefs/      # SettingsStore + SecureStore
├─ domain/        # Modelos (Channel, Movie, Series...)
├─ ui/            # Compose: home, login, live, movies, series, search,
│                 #   favorites, settings, legal, player, parental, update
├─ work/          # CatalogRefreshWorker
├─ update/        # UpdateChecker
├─ diag/          # CrashLog
└─ MainActivity.kt + IptvApp.kt
```

### Testes

Unidade: `app/src/test/java/com/iptv/app/`. Cobertura inicial em parsing de DTOs Xtream e parser XMLTV.

```bash
./gradlew :app:testDebugUnitTest
```

---

## Privacidade e segurança

- **Sem telemetria**: o app não envia dados de uso, crashes, IPs ou identificação para o desenvolvedor ou terceiros
- **Credenciais criptografadas**: URL, usuário, senha e PIN parental ficam em `EncryptedSharedPreferences` (Tink AES-256), em área privada do app
- **Sem backup automático em nuvem**: `allowBackup=false` + regras de extração explícitas
- **Sem acesso a dados sensíveis**: o app não pede permissão de contatos, localização, microfone, câmera, contas, SMS, etc.
- **Tráfego de rede**: requisições saem direto do seu dispositivo para o servidor IPTV cuja URL você cadastrou; nós não temos visibilidade

Veja `Configurações → Sobre → Política de Privacidade` para o texto completo.

---

## Licenças

Este projeto usa diversas bibliotecas open source, listadas em `Configurações → Sobre`. Cada uma mantém sua licença original.

A marca e nome **TartaTV** são propriedade de seu titular.

---

## Suporte

Para reportar bugs ou sugerir melhorias, abra uma [issue no GitHub](../../issues).
