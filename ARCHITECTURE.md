# ARCHITECTURE

Visão técnica do TartaTV.

## Camadas

```text
┌────────────────────────────────────────────────┐
│                     UI                         │
│  Compose · TouchableButton/Card adaptativos   │
│  HomeScreen → tabs → Sections → Detail        │
│  PlayerScreen (ExoPlayer + AndroidView)       │
└────────────────────────────────────────────────┘
                       │
                       ▼  observa StateFlow
┌────────────────────────────────────────────────┐
│                  ViewModels                    │
│  HomeViewModel · LoginViewModel · etc.        │
│  Hilt-injected, expõem StateFlow              │
└────────────────────────────────────────────────┘
                       │
                       ▼ chama suspend fun
┌────────────────────────────────────────────────┐
│             Repositórios / Domain              │
│  XtreamRepository · CatalogCacheRepository    │
│  EpgRepository · UpdateChecker                │
└────────────────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
┌───────────┐  ┌────────────┐  ┌──────────────┐
│ XtreamApi │  │   Room DB  │  │ SettingsStore│
│ Retrofit  │  │  + FTS4    │  │ EncryptedSP  │
│  + OkHttp │  │            │  │  + DataStore │
└───────────┘  └────────────┘  └──────────────┘
```

## Pacotes

```text
app/src/main/java/com/iptv/app/
├─ MainActivity.kt + IptvApp.kt + AppNav
├─ data/
│  ├─ api/         # Retrofit (XtreamApi) + DTOs + XtreamRepository
│  ├─ cache/       # CatalogCacheRepository (stale-while-revalidate)
│  ├─ db/          # AppDatabase, entidades, DAOs, migrations
│  ├─ epg/         # XmltvParser + EpgRepository
│  └─ prefs/       # SettingsStore + SecureStore + Profile
├─ di/             # Hilt modules (Network, Database, etc.)
├─ domain/         # Modelos puros (Channel, Movie, Series...)
├─ notify/         # NotificationChannels + Notifications helper
├─ ui/             # Compose: home, login, live, movies, series, search,
│                  #          favorites, settings, legal, player, parental,
│                  #          continueWatching, update
├─ update/         # UpdateChecker (GitHub Releases)
├─ work/           # CatalogRefreshWorker, ResumeReminderWorker
└─ diag/           # CrashLog (local-only, opt-in)
```

## Fluxos principais

### Boot

1. `MainActivity.onCreate` instala splash e habilita `enableEdgeToEdge`
2. `IptvApp.onCreate` cria canais de notificação e agenda os Workers
3. `AppNav` lê `SettingsStore.flow` e decide rota inicial:
   - `!termsAccepted` → `OnboardingScreen`
   - `loggedIn` → `HomeScreen`
   - else → `LoginScreen`

### Login

1. `LoginScreen` coleta host/user/pass
2. `LoginViewModel.login()` chama `XtreamRepository.login()`
3. Se `auth==1`, salva em `SettingsStore.saveCredentials`, que também faz upsert do perfil ativo
4. App navega pra `HomeScreen` e dispara `bootstrapCatalog`

### Home + bootstrap de catálogo

1. `HomeScreen` mostra `CatalogLoadingScreen` se `initialLoading`
2. `HomeViewModel.bootstrapCatalog` carrega categorias de Live/Movies/Series via `CatalogCacheRepository`
3. Cache strategy: **SWR** — devolve cache instantaneamente, refaz fetch em background, atualiza `Flow`
4. Após bootstrap, tabs viram navegáveis. Cada Section (Movies/Live/Series) carrega itens da categoria sob demanda

### Reprodução

1. Usuário toca em filme/episódio/canal → `PlayerArgs` codificado pra rota
2. `PlayerScreen` cria `ExoPlayer` + `MediaSource` (HLS/DASH/Progressive conforme container)
3. `PlayerView` exibe controles padrão; **Phone**: `controllerHideOnTouch=true` + double-tap zones para seek; **TV**: D-pad (`onPreviewKeyEvent`)
4. Progresso é salvo no `MovieProgressDao`/`EpisodeProgressDao` no `DisposableEffect` ao sair
5. Erros de codec/rede → `friendlyPlaybackError()` traduz pra mensagem visível

### Refresh periódico

1. `CatalogRefreshWorker` (HiltWorker) roda a cada N horas (config do usuário)
2. Antes do refresh: snapshot do número de episódios das séries favoritas
3. Refresh `cache.refreshAll()` + `epg.refresh()`
4. Após sucesso: notifica "catálogo atualizado" e — se houver — "X séries com novos episódios"
5. Após `runAttemptCount >= 3` falhas: notifica "servidor inacessível"

### Lembrete "continuar assistindo"

1. `ResumeReminderWorker` (semanal) checa progresso de filmes/episódios mais antigos que 3 dias
2. Se houver, dispara notificação no canal `RESUME`

## Decisões de design

### Por que `tv.material3` + adaptadores Touchable?

O projeto começou Android-TV-only. Quando expandiu pra phone, descobrimos que `tv.material3.Button/Card` só responde a foco D-pad — toque no celular não dispara `onClick`. Em vez de migrar tudo pra `material3` puro (perderia foco/scale TV), criamos `TouchableButton`/`TouchableCard` que escolhem a variante certa por form factor.

### Por que multi-perfil em SecureStore JSON e não em Room?

Multi-perfil v1 não isola favoritos/progresso por perfil. As entidades atuais (favoritos, progresso) seriam impactadas se profiles virassem FK. Para evitar migração de DB e manter o caminho de evolução aberto, perfis vivem como JSON no `EncryptedSharedPreferences`. Quando v2 trouxer isolamento, será uma migração focada.

### Por que `safeDrawingPadding` em telas em vez de no Activity?

Player precisa ser full-bleed (vídeo edge-to-edge); Settings/About/etc. precisam respeitar notch. Aplicar no Activity forçaria todo mundo no mesmo modo. Cada tela top-level decide explicitamente.

### Por que SWR em vez de fetch sempre?

Catálogos Xtream têm 10k–50k itens. Fetch direto do servidor a cada navegação travava UX. SWR mostra cache imediatamente (latência zero), atualiza em background.

### Por que XMLTV salvo em Room e não na memória?

EPG completa de 200 canais × 7 dias passa de 30 MB. Memória crashava em devices low-end. Room + queries por janela de tempo resolveu.

### Por que `EncryptedSharedPreferences` (Tink) e não Android Keystore puro?

Tink envolve Keystore, gerencia chaves, e expõe API tipo SharedPreferences. Bem menos código boilerplate. Para o tipo de dado (credenciais Xtream + PIN), o nível de segurança é equivalente.

## Dependências críticas

| Lib | Versão | Por quê |
|-----|--------|---------|
| ExoPlayer (Media3) | 1.4.x | Player oficial do Android, suporte HLS/DASH/HEVC |
| OkHttp + Retrofit + Moshi | 4.x / 2.x | HTTP + JSON padrão Kotlin |
| Room + FTS4 | 2.6.x | Local DB + busca textual rápida |
| Hilt | 2.x | DI |
| WorkManager | 2.9.x | Background scheduling resiliente |
| Coil | 2.x | Carregamento de imagens otimizado pra Compose |
| Tink (via EncryptedSharedPreferences) | 1.x | Crypto AES-256 |
| Compose for TV (`tv.material3`) | 1.0.x | Foco/scale para D-pad |

Atualizar com cuidado: ExoPlayer e Compose costumam ter breaking changes; OkHttp/Retrofit/Moshi são estáveis há anos.

## Pontos de extensão

- **Novo provider** (M3U, Stalker, etc.): adicionar repositório irmão de `XtreamRepository` e injetar baseado em config — UI atual já é agnóstica.
- **Cast / Chromecast**: integrar `androidx.mediarouter` + `cast-framework`; PlayerScreen escolhe entre ExoPlayer local e RemotePlayer.
- **Download offline**: ExoPlayer Media3 tem `DownloadManager`; precisa UI de fila + tabela `downloads` no Room.
- **Per-profile data isolation (multi-perfil v2)**: adicionar `profileId` em `favorites`/`*_progress`/etc., migração que põe profileId do perfil atual em todos os rows.
