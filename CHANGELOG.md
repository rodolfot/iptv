# Changelog

Todas as mudanças notáveis deste projeto.

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Unreleased]

## [2.0.0] — 2026-07-27

### Added

- **Auto-update em app**: worker periódico (12h) checa a última release no GitHub, baixa o APK em segundo plano e notifica — só falta o toque de confirmação do instalador do sistema (Android não permite pular essa etapa sem privilégio de sistema). O diálogo de atualização em primeiro plano ganhou o mesmo fluxo com barra de progresso, e Config → Sobre tem um botão "Verificar atualização agora" pra forçar a checagem numa TV específica.
- **3 visualizações no Ao Vivo** (Config/botão na tela): grade com PIP (original), lista com coluna de categorias + preview, e lista com preview ocupando o resto da tela (sem coluna de categorias — Voltar abre um seletor de categoria em tela cheia). Em todos os modos, o preview só começa a tocar quando o usuário confirma o canal com OK/Enter — mover o foco com o D-pad não dispara stream nenhum; confirmar de novo o canal já em preview abre em tela cheia.
- **Player externo (MX Player / VLC…)**: opção "Abrir em player externo" no painel de opções do player e seleção do app preferido em Config → Player avançado. Faz handoff do stream atual via `ACTION_VIEW` (com fallback pro seletor do sistema). Saída prática pro erro de HEVC/4K não suportado pelo decoder interno.
- **Painel de opções do player** (engrenagem na overlay): proporção da tela (Ajustar/Zoom/Preencher), velocidade (0.5×–2×), sleep timer (15/30/60/90 min), áudio-only, estilo/tamanho de legenda e formato do canal — tudo navegável por D-pad.
- **Seleção de decoder** (Config → Player avançado): Automático / Forçar hardware / Forçar software, via `MediaCodecSelector` na `RenderersFactory`. Forçar software resolve a maioria dos casos de "formato não suportado".
- **Formato do stream HLS ↔ TS**: padrão global em Config + override por canal direto no player (salvo por canal e reaplicado).
- **Customização de legenda**: tamanho (75–200%) e presets de estilo (padrão/contorno/fundo preto/amarelo) aplicados ao `SubtitleView`.
- **Modo rádio / áudio-only**: desliga o renderer de vídeo (economiza banda/CPU) e mostra a marca + título no lugar da tela preta.
- **EPG externa + correção de fuso**: URL XMLTV própria e offset em minutos (Config → EPG), aplicados no `EpgRepository`.
- **Integração Android TV**: canal de recomendações na tela inicial do launcher (canais recentes + filmes em andamento) com deep links `tartatv://play?…` que abrem direto no player, e sugestões na busca global do sistema via `SearchSuggestionsProvider`.

### Changed

- **Tela de Configurações reorganizada**: as configurações viviam num fluxo contínuo de títulos soltos, sem separação visual — agora cada tema (Servidor, Controle parental, Geral, Sessão, Catálogo, Player avançado, EPG, Histórico, Sobre) fica num card próprio. A opção de visualização do Ao Vivo saiu do botão na própria tela e passou a morar em Config → EPG, junto do formato do canal.

### Fixed

- **Vídeo pausando sozinho em alguns canais Ao Vivo**: streams de alguns provedores emitem `STATE_ENDED` sem `#EXT-X-ENDLIST` (hiccup do encoder ou atraso no refresh do manifesto), e como Ao Vivo não tem "próximo item" na playlist, o player simplesmente ficava congelado sem nenhum erro disparado. Agora reconecta automaticamente (até 3 tentativas, mesmo fluxo do tratamento de erro existente) antes de mostrar uma mensagem definitiva.
- **Tela preta ao reabrir o app depois de desligar/religar a TV**: o publicador do canal de recomendações da Android TV (`HomeChannelPublisher`) fazia chamadas síncronas de `ContentResolver` direto na thread principal durante `onCreate`, antes do primeiro frame. Se o processo foi recriado do zero (comum após TV ficar muito tempo desligada) e o launcher/TvProvider do sistema ainda estivesse acordando do standby, a UI thread travava esperando essa chamada — sem ANR, sem input pendente pro watchdog cronometrar, só resolvia forçando parada do app. Agora roda em `Dispatchers.IO`.
- **Teclado do PIN de conteúdo protegido**: o diálogo de PIN parental usava `OutlinedTextField` cru do Material3, o único lugar do app que ainda não seguia o padrão "OK pra editar, Voltar pra sair" já usado no resto da UI pra D-pad de TV — o teclado virtual abria/fechava sozinho ao passar o foco por cima do campo, e Voltar fechava o diálogo inteiro em vez de só sair da edição. Trocado pelo `TvSafeTextField` já usado em Login/Perfis/Configurações.

## [1.3.2] — 2026-06-07

### Added

- **Zapping por D-pad no player Ao Vivo**: com um canal aberto, esquerda/direita do controle troca para o canal anterior/seguinte dentro da categoria (mesma ordem do drawer, com wrap-around nas pontas). O `PlayerViewModel` carrega os canais irmãos via `LiveCacheDao.getByCategory()` e o `zapLive(±1)` recompõe a reprodução sem voltar pra lista. Seek por D-pad (±10s) continua valendo para filmes/séries; timeshift (`tv_archive`) fica fora do zapping.

### Changed

- **Sessão do PIN parental dura 1 hora e sobrevive à navegação**: o desbloqueio era guardado num `remember` recriado a cada tela, então trocar de canal/categoria pedia a senha de novo; agora o timestamp é estático (escopo de processo) e a janela subiu de 15min para 1h. Digitou uma vez, vale 1h desde a última vez; reiniciar o app sempre tranca.

### Fixed

- **Conteúdo adulto/protegido fora dos históricos**: canais, filmes e séries em categorias adultas não entram mais em "Canais recentes" nem em "Continuar assistindo". Filtra em duas camadas — não grava no histórico ao reproduzir (`PlayerScreen`) e também esconde na leitura (`ContinueWatchingViewModel`), removendo entradas adultas gravadas antes do filtro existir. A marca de "adulto" usa a mesma regra das categorias (regex de nome + categorias marcadas manualmente).
- **Testes desatualizados voltaram a passar**: `MigrationCoverageTest` esperava o range de migrações até a versão 9, mas o schema já estava em 10 (com `MIGRATION_9_10` registrada) — agora alinhado. `XtreamDtoParsingTest` esperava `durationSecs` como `Int`, mas o campo virou `@Loose String?` na 1.3.0 — asserção corrigida para a string.

## [1.3.1] — 2026-05-20

### Fixed

- **Tela de detalhe de filme/série quebrava** com `No JsonAdapter for class String annotated [@Loose()]` para `durationSecs`/`rating`/`releaseDate`: na 1.3.0 esses campos viraram `@Loose String?` para tolerar tipos heterogêneos dos provedores, mas o `LooseStringAdapter` estava registrado apenas no Moshi local de `normalizedEpisodes()`. O Moshi global injetado por `AppModule` (usado pelo `MoshiConverterFactory` do Retrofit) agora também tem o adapter, então `get_vod_info` e `get_series_info` parseiam corretamente.

## [1.3.0] — 2026-05-19

### Added

- **Contagem por categoria**: Filmes, Séries e Ao Vivo mostram `Nome (N)` em cada categoria do drawer — número reativo via `observeCountByCategory()` nos DAOs, atualiza sozinho enquanto o Worker popula o cache.
- **PIN parental requer PIN atual** ao trocar: enquanto há PIN configurado, a tela de Config exibe um campo "PIN atual" extra; alteração só é aceita se bater com o salvo.
- **Nota (rating) nos pôsteres** de filmes e séries: chip "★ X.X" no canto superior esquerdo, escondido quando o provedor não retorna nota válida (0.0 = desconhecido).
- **Autoplay de episódios**: ao terminar um episódio, o próximo da série inicia automaticamente. A fila já carregava todos os episódios — agora `playWhenReady` é forçado em cada transição (caso o usuário tenha pausado o anterior).
- **Painel lateral de EPG** nas listas de canais ao vivo (TV/Tablet): a lista virou coluna única; ao mover o foco, o painel direito mostra programa atual + próximos do canal focado. OK arma o canal (indicador ▶), OK de novo abre o player.
- **Combo box D-pad friendly** (`ComboBox` + `ComboColumn`): botão que abre diálogo com lista de opções, usado em Config para Intervalo de atualização e Idioma do aplicativo (substituem linhas de botões que estouravam a largura da tela).
- **`TvSafeTextField`** reutilizável: campo de texto que não abre o IME apenas porque recebeu foco pelo D-pad. OK entra em edição (teclado aparece), Voltar sai. Adotado em login e editor de perfis.
- Botão **Favoritar** na tela de detalhe de séries (antes só existia o botão Lista, o que confundia o usuário a achar que estava favoritando).
- Snackbars de confirmação ao trocar Intervalo de atualização e Idioma em Config.

### Changed

- **Categorias** (Live/Filmes/Séries): grade passou para 4 colunas em TV e cards mais compactos; categorias agora vêm ordenadas alfabeticamente com as marcadas como adultas no final.
- **Filmes/Séries/Favoritos/Lista**: grids passaram a usar `GridCells.Adaptive` com pôsteres `fillWidth=true`, mantendo proporção 2:3. Em telas que reportavam tamanho de tablet, os pôsteres ficavam gigantes (2 por linha); agora se adaptam à largura disponível.
- **Filtro inline nas listas dentro de categoria**: na TV/Tablet, o filtro fica na mesma linha do título e da ordenação, à direita.
- **Filtro das listas** (`LocalFilterField`): foco do D-pad não abre mais o IME; só ao apertar OK. Voltar/Escape sai do modo edição mantendo o foco.
- **Player**: tecla Back é interceptada antes do PlayerView para sair de primeira (antes ela primeiro fechava o controller e exigia um segundo Back). Auto-hide forçado de 5s mesmo com player pausado/buffering. Título do canal/filme some 5s após a abertura.
- **Player na TV/Tablet**: voltar libera o playback ao invés de minimizar — o mini-player não é alcançável com D-pad. Em phone segue como antes.
- **OK do controle no player** respeita o foco: se um botão da overlay (Voltar/Faixas) está focado, OK aciona o botão; senão, alterna play/pause.
- **Tela de detalhe de filme/série**: foco inicial vai para o botão Assistir/Continuar. Sinopse virou focável + `BringIntoViewRequester` para o D-pad rolar até ela.
- **Tela de detalhe de série**: usa o botão Voltar do top bar (`RegisterHeaderBack`) em vez do botão inline. Mostra poster + metadados ao lado do título, igual ao detalhe de filme.
- **Config** virou layout de 2 colunas em TV/Tablet (Servidor/Parental/Catálogo à esquerda; Notificações/Idioma/Sobre/Sessão à direita) para reduzir scroll.
- **Modo Kids** agora oculta categorias marcadas como adultas mesmo quando o adulto não configurou um allowlist explícito (antes Kids sem allowlist era um no-op).
- **Tela de login**: rola e empurra o conteúdo com `imePadding` + `BringIntoViewRequester` — antes o teclado virtual cobria os campos.

### Fixed

- **Idioma do app não trocava em Android 12**: `AppCompatDelegate.setApplicationLocales` só aplica em runtime quando a Activity é `AppCompatActivity`; nossa `MainActivity` é `ComponentActivity`. Agora `IptvApp` e `MainActivity` sobrescrevem `attachBaseContext` lendo o tag de `SharedPreferences` e criando um `Context` com `Configuration.setLocale(...)` via `createConfigurationContext`. Strings de `values-en/`, `values-es/`, `values-it/`, `values-fr/`, `values-de/` agora pegam de verdade.
- **The Boys T3/T4 vazias** (e qualquer série com campos numéricos malformados): `EpisodeInfo.rating` / `durationSecs` / `releaseDate` vinham como `String`, `Int`, `Double` ou string vazia dependendo do episódio. Sem `@Loose`, Moshi falhava o parse de uma temporada inteira ao encontrar `"rating": ""`. Tipos agora são `@Loose String?` e convertidos em `toModel()`.
- **Continuar séries** ia para a tela de detalhe mesmo quando o último episódio já tinha sido concluído. Agora calcula e abre o próximo episódio.
- **Tela preta ao trocar de temporada** no detalhe de série + áudio do preview ao vivo vazando para o player principal.
- **Botão Atualizar catálogo** ficava silencioso: agora exibe snackbar "Atualizando catálogo." ao acionar, barra de progresso por fase enquanto o Worker roda, e snackbar "Catálogo atualizado." ao terminar.
- **Spinner fantasma** sobre o grid de categorias em TV: `PullToRefreshBox` ignorava o flag `enabled`, então refresh em background acionava o indicador mesmo em plataforma sem gesto de pull.
- **Episódios de séries** caindo em temporadas vazias: alguns provedores Xtream enviam todos os episódios sob a chave `"0"` mas trazem o número da temporada no campo `season` de cada episódio. Agrupamento agora usa o `seasonNumber` do episódio.
- **Episódios sem título/sinopse/poster**: `EpisodeInfo` aceita as variantes mais comuns dos provedores (`name`/`title`/`overview`/`cover_big`/`cover`/`image`).
- **Standby da TV durante playback**: `ExoPlayer.setWakeMode(WAKE_MODE_NETWORK)` + `FLAG_KEEP_SCREEN_ON` na janela do player mantêm CPU/tela acordadas enquanto está tocando.
- **`SQLITE_CONSTRAINT` (code 19) em Filmes Marvel/DC**: `INSERT OR REPLACE` não funciona em tabelas FTS5 — DAOs agora fazem `DELETE` + `INSERT` na transação.

### Changed

- **Config (Configurações)**: título do topo removido (o menu já indica onde o usuário está); seção Catálogo movida para a coluna direita, abaixo de Tipo de dispositivo; "Atualizar agora" e "Resetar progresso" lado a lado.
- **Combos e botões** da Config em modo compacto: `ComboBox` agora usa `TouchableButton(compact = true)` com texto `labelMedium`; `ComboColumn` com label `bodySmall` e spacing de 2dp; campos de texto ocupam largura total; toda a tela cabe sem scroll em TV/Tablet.
- **TabRow**: pílula da aba ativa agora destacada em azul; ao entrar em uma categoria, a primeira subcategoria é auto-selecionada.

## [1.0.0] — 2026-05-11

### Added

- **Material3 puro** começou: `EmptyState` e `ErrorState` migrados de `androidx.tv.material3` para `androidx.compose.material3` puro. Wrapper `IptvText`/`IptvIcon` em `ui/common/IptvText.kt` deixa o caminho aberto pros próximos arquivos — basta trocar a linha de import. Tabs/cards focáveis pelo D-pad permanecem em `tv.material3` (D-pad é o motivo de existir).
- **Plano de modularização**: `MODULARIZATION.md` documenta o split alvo (`:app` / `:ui` / `:data` / `:domain` / `:core`), ordem de migração, e o que já está pronto pra mudar como `M3uParser` / `parseYear` (zero deps Android).
- **Skeleton Stalker (Ministra) Portal**: `StalkerClient` faz handshake nos endpoints comuns (`portal.php` e `stalker_portal/server/load.php`) com header `Cookie: mac=...`. Não integrado ao pipeline de catálogo ainda — base para o trabalho de continuidade.
- **SyncBackend ponto de extensão**: interface `SyncBackend` com push/pull para favoritos, watchlist e progresso, escopado por `profileId`. `NoopSyncBackend` é o binding padrão registrado no Hilt — comportamento atual (offline-only, zero telemetria) preservado. Adicionar uma impl real depois é só trocar o `@Provides`.
- **Suporte a M3U como provedor alternativo**: novo enum `ProviderType` no `Profile` (XTREAM / M3U); editor de perfil mostra rádio Xtream/M3U e troca o label do host + esconde usuário/senha quando M3U. `M3uParser` lê `#EXTM3U` com `tvg-id`, `tvg-logo` e `group-title`; `M3uRepository` faz fetch HTTP, popula `category_cache` (group-title vira categoria) e `live_cache` (URL bruta vai num novo campo `streamUrl`). `CatalogCacheRepository` despacha entre Xtream e M3U conforme o `provider` do perfil ativo; abas Filmes/Séries ficam vazias em perfis M3U. PlayerScreen usa `streamUrl` direto quando disponível. Migração Room 8→9 acrescenta a coluna. Cobertura: 3 testes para o parser.
- **Preparação F-Droid**: metadados Triple-T em `fastlane/metadata/android/{en-US,pt-BR}/` (title, short_description, full_description, changelogs/1.txt) e guia `FDROID.md` com checklist de submissão e manifesto sugerido para `fdroiddata`. Auditoria de dependências confirma ausência de SDKs proprietários (Firebase/GMS/Crashlytics/Sentry).
- **Testes Compose UI** rodando no `:app:testDebugUnitTest` (Robolectric, sem device/emulador): `EmptyStateTest` valida render do estado vazio, `AdvancedFiltersTest` valida parsing/aplicação dos campos do diálogo de filtros, `PortCandidatesTest` valida geração de candidatos no login. Setup: `TestApp` substitui `IptvApp` (que abre EncryptedSharedPreferences eagerly) via `robolectric.properties`; `ComponentActivity` registrada no manifest para hostear `runComposeUiTest`.
- **i18n it/fr/de**: traduções completas de `strings.xml` em italiano (`values-it`), francês (`values-fr`) e alemão (`values-de`) com paridade de chaves.
- **detekt** integrado via Gradle plugin (`./gradlew detekt`) com config minimalista em `config/detekt/detekt.yml` e build limpo. Limpeza inicial: imports não usados, lambda parameter shadowing, `IllegalStateException` → `check(...)` no fluxo de detecção de porta.
- **Teste de cobertura de migrações Room**: `MigrationCoverageTest` valida que `ALL_MIGRATIONS` cobre todo o range de versões do banco — bumpar `version` sem registrar a migração quebra o `:app:testDebugUnitTest` antes do release.
- **`network_security_config.xml` por domínio**: cleartext segue habilitado para o host Xtream do usuário (provedores comuns rodam em HTTP), mas endpoints próprios de update (`api.github.com`, `github.com`, `objects.githubusercontent.com`, `dl.google.com`) ficam bloqueados em cleartext — defesa em profundidade contra MITM/DNS spoofing.
- **Diagnóstico — exportação manual**: Config → Sobre → Diagnóstico ganhou botões **Copiar** (clipboard) e **Compartilhar** (intent ACTION_SEND). Continua estritamente opt-in: nada sai do dispositivo automaticamente, o usuário escolhe quando e para onde enviar o log.
- **Auto-update v2**: diálogo de nova versão agora mostra o changelog completo do release (área scrollable até 220dp), título "O que mudou nesta versão" e botão **Pular esta versão** que persiste a versão ignorada em DataStore — o app só notifica de novo quando outra versão for publicada.
- **Perfil Kids**: novo toggle no editor de perfil. Quando ativo, esconde as abas Busca, Lista e Config (criança não troca de perfil nem usa busca livre) e filtra Live/Filmes/Séries para mostrar só as categorias liberadas pelo responsável (campo `allowedCategories` no Profile, no formato `"kind:id"` para diferenciar canais/filmes/séries que compartilham nomes).
- **Recomendações no Home**: novas linhas "Porque você viu X" com filmes e séries similares ao último assistido (ou favoritado, ou ao melhor avaliado em primeiro acesso). Recomendações são 100% locais — `Recommender` calcula score por sobreposição de tokens (gênero, elenco, categoria, década, faixa de avaliação) sobre o cache do catálogo. Sem rede, sem telemetria.
- **Multi-perfil v2 — isolamento de dados**: favoritos, watchlist e progresso (filme/série/episódio) agora têm `profileId` no schema. Cada perfil enxerga só os próprios itens; trocar de perfil rebusca tudo automaticamente. Migração 7→8 do Room reescreve as tabelas com chaves compostas; rows existentes ficam atribuídas ao perfil sentinela `default` e seguem visíveis até o usuário ativar um perfil nomeado.
- **Mini-player persistente**: ao dar back na tela do player, em vez de fechar, o player encolhe para uma faixa fixa no rodapé das abas com play/pause, título e botão fechar. Tap reabre a tela cheia. Implementado via `ActivePlaybackHolder` activity-scoped que detém o ExoPlayer entre PlayerScreen e MiniPlayer; release real só quando o usuário fecha o mini ou a Activity é destruída.
- **Watchlist** ("Lista para assistir") separada de Favoritos: nova entidade Room `watchlist` (migração 6→7), `WatchlistDao`, nova aba dedicada entre Favoritos e Config, botão "Adicionar à lista" no detalhe de filmes e séries com snackbar de feedback. Item pode estar em Favoritos e Watchlist independentemente.
- **Filtros avançados** em Filmes e Séries: ano (intervalo de–até), avaliação mínima, e — em séries — multi-seleção de gêneros derivados do catálogo da categoria. Botão "Filtros" ao lado do "Ordenar"; indicador `•` quando há filtro ativo.
- Login com **detecção automática de porta**: quando o usuário informa só o domínio, o app testa em paralelo o host original + portas comuns de Xtream (8080, 8880, 80, 25461) e usa a primeira que autenticar. Botão mostra "Detectando porta…" enquanto sonda.
- **Histórico de busca** persistente: até 10 termos recentes (DataStore), apresentados como chips no estado pré-busca com botão "Limpar". Termos só são salvos quando produzem resultados.
- EPG no detalhe do canal agora usa **layout vertical de duas linhas no Phone** (horário em label, título em destaque, descrição compacta) — antes ficava apertado em uma linha só.
- Empty states ilustrados em Favoritos, Continuar Assistindo e Busca (estado vazio + estado pré-busca) com ícone, título e mensagem amigável.
- Snackbar host global (`LocalSnackbar`) e feedback em ações silenciosas: favoritar, salvar perfil, excluir perfil, trocar perfil, salvar credenciais, salvar PIN, atualizar catálogo.
- Pull-to-refresh nas categorias de Live/Filmes/Séries quando rodando no Phone (`PullToRefreshBox` baseado em `NestedScrollConnection`, sem dependência do Material3 1.3+).
- Perfis ordenados por último uso: o perfil ativo aparece primeiro, seguido pelos perfis usados mais recentemente. `Profile.lastUsedAt` é tocado em `activateProfile`.
- `.markdownlint.json` com regras pragmáticas para o repositório.
- Multi-perfil: vários servidores Xtream salvos com nome próprio e PIN parental opcional por perfil. Tela "Gerenciar perfis" no Settings.
- Notificações: 4 canais (catálogo, novos episódios em séries favoritas, lembrete "continuar assistindo", erros do servidor). Permissão `POST_NOTIFICATIONS` solicitada via Settings no Android 13+.
- `ResumeReminderWorker`: ping semanal quando há progresso dormente >3 dias.
- Espanhol (`values-es/`) com paridade total.
- Strings de acessibilidade (`a11y_*`) em pt/en/es; `Lock` overlay agora narrável pelo TalkBack.
- Botão de busca pelo notch: header do TartaTV no topo das tabs no Phone, sempre dentro da safe area.

### Changed

- **Toolchain bump conservador**: Kotlin 1.9.24 → 1.9.25, KSP correspondente, Compose Compiler 1.5.14 → 1.5.15, Compose BOM 2024.06 → 2024.09.03 (traz Material3 1.3+ disponível para uso futuro). AGP/Gradle mantidos para evitar quebras grandes. Build limpo, 16 testes passando, warning de `!!` redundante em `XmltvParser` corrigido de quebra.
- `androidx.tv.material3.Button` e `Card` substituídos por `TouchableButton` / `TouchableCard` em todas as telas: o toque agora funciona no celular, e o D-pad continua funcionando na TV.
- Tema agora `enableEdgeToEdge` + `safeDrawingPadding` por tela. Notch/Dynamic Island/gesture bar respeitados em Login/Onboarding/About/CrashLog/LegalViewer/CatalogLoading e nos overlays do Player.
- LoginScreen: removido host hardcoded, mensagens de erro 404/401/timeout amigáveis.
- Cards no Phone esticam `fillMaxWidth` com aspect ratio correto (sem dead space nas bordas).
- Player no Phone: `controllerHideOnTouch=true`, timeout menor, double-tap esquerda/direita para seek ±10s.
- Tabs no Phone com banner do TartaTV no topo da scroll bar.
- Back gesture do Android tratado em todas as telas detail (Movie/Channel/Series/About/CrashLog/LegalViewer) e nas seções (Movies/Live/Series ao sair de uma categoria).

### Fixed

- Botão "Sign in" não respondia ao toque no celular.
- HTTP 404 ao logar mostrava `HTTP 404` cru — agora explica que falta a porta no host.
- Conteúdo escondido pela câmera/notch nos celulares modernos.
- Categorias de filmes/séries/canais não eram clicáveis no toque.
- `Icons.Filled.Sort` deprecado migrado para `Icons.AutoMirrored.Filled.Sort`.
- Ícone de notificação na status bar agora é a marca TartaTV (T+V) em vez de um placeholder genérico de TV.
- Markdown dos docs (README/ARCHITECTURE/DEVELOPMENT/CHANGELOG) passa em `markdownlint`: code fences com linguagem, listas com blank line ao redor, subseções de fluxos de uso convertidas para `####`.

## [Versões anteriores]

Ver `git log --oneline` para histórico anterior. Releases marcadas como `v*` no GitHub Releases têm APK assinado anexado.
