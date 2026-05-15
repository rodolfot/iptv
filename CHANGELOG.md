# Changelog

Todas as mudanças notáveis deste projeto.

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Unreleased]

### Added

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

- **Spinner fantasma** sobre o grid de categorias em TV: `PullToRefreshBox` ignorava o flag `enabled`, então refresh em background acionava o indicador mesmo em plataforma sem gesto de pull.
- **Episódios de séries** caindo em temporadas vazias: alguns provedores Xtream enviam todos os episódios sob a chave `"0"` mas trazem o número da temporada no campo `season` de cada episódio. Agrupamento agora usa o `seasonNumber` do episódio.
- **Episódios sem título/sinopse/poster**: `EpisodeInfo` aceita as variantes mais comuns dos provedores (`name`/`title`/`overview`/`cover_big`/`cover`/`image`).

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
