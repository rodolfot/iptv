# Auditoria de features IPTV — TartaTV 4.0.0 (#19)

Estado do código na versão 4.0.0. Legenda: **[X]** implementado · **[~]** parcial · **[ ]** ausente.

## Resumo da arquitetura atual

App Android único (`:app`, Kotlin + Jetpack Compose) com um módulo JVM puro (`:core`, parser M3U e utilitários).
Camadas: UI Compose (`ui/*`, componentes TV/touch adaptativos em `ui/common`) → ViewModels Hilt → repositórios
(`data/api` Xtream via Retrofit/OkHttp, `data/cache` com Room em stale-while-revalidate, `data/epg` XMLTV em Room)
→ armazenamento (`SettingsStore` DataStore, `SecureStore` EncryptedSharedPreferences). Player: Media3/ExoPlayer
(`ui/player`), com um `ActivePlaybackHolder` por Activity compartilhado entre tela cheia e mini-player.
Novos na 4.0: `premium/` (licença), `ads/` (VAST), `car/` (regras de direção), `ui/common/TvScreenAdapter`,
`FocusMemory`, `CollapsibleDrawerLayout`, `InputMapper`. Detalhes em [ARCHITECTURE.md](../ARCHITECTURE.md) e
[MULTI_DISPOSITIVO.md](MULTI_DISPOSITIVO.md).

## 1. Features essenciais

| Status | Feature | Onde / observação |
|---|---|---|
| [~] | Multi-protocolo | Xtream completo (`data/api/XtreamRepository.kt`), M3U/M3U8 (`core/m3u/M3uParser.kt`, `data/m3u/M3uRepository.kt`). **Stalker**: só o handshake (`data/stalker/StalkerClient.kt`), sem catálogo. |
| [X] | EPG | XMLTV do provedor ou URL externa (`data/epg/XmltvParser.kt`, `EpgRepository.kt`), programa atual/progresso na lista e guia em grade (`ui/live/EpgGuideScreen.kt`). |
| [X] | Estrutura de conteúdo | Ao Vivo, Filmes e Séries com temporadas/episódios (`ui/live`, `ui/movies`, `ui/series`). |
| [X] | Engine do player & codecs | Media3 com decoder auto/hardware/software (`ui/player/PlaybackFactory.kt`); H.264/HEVC em TS/MKV/MP4/HLS/DASH conforme o hardware. |
| [~] | Áudio e legendas | Troca de faixas de áudio e legendas internas (`TrackPickerDialog.kt`). **Falta**: legendas externas SRT/VTT por URL/arquivo. |
| [X] | Navegação D-pad / Smart TV | Foco visível, gaveta retrátil, memória de foco, Voltar tratado, throttle de teclas (4.0). |
| [~] | Catch-up | Timeshift Xtream de 30/60 min no detalhe do canal (`ChannelDetailScreen.kt`, `XtreamRepository.timeshiftUrl`). **Falta**: abrir programas passados a partir da EPG/guia. |
| [X] | Busca global e favoritos | FTS4 em Room (`ui/search`), favoritos com gaveta e miniplayer (`ui/favorites`). |
| [X] | Continuar assistindo | `MovieProgressDao`/`EpisodeProgressDao`, seção na Início (`ui/continueWatching`). |

## 2. Features diferenciais

| Status | Feature | Onde / observação |
|---|---|---|
| [X] | Passthrough / bitstream | 4.0: `buildRenderersFactory(passthrough)` + opção em Configurações. |
| [~] | Codecs HD e fallback seguro | 4.0: identificação Atmos/TrueHD/DD+/DTS/DTS-HD/DTS:X com selo (`ImmersiveAudio.kt`), seleção automática de faixa suportada e aviso anti-mudo com player externo. **Falta**: decodificação de DTS/TrueHD por software (ver plano). |
| [ ] | AFR (troca de taxa de quadros) | Ausente. |
| [ ] | Multi-View | Ausente. |
| [X] | Picture-in-Picture | `MainActivity.onUserLeaveHint` + auto-enter no Android 12+ (4.0). |
| [ ] | Gravação local / PVR / timeshift local | Ausente (só o timeshift do servidor). |
| [~] | Mapeamento/edição manual de EPG | URL de EPG alternativa e offset de fuso (Configurações → EPG); marcar categoria como adulta. **Falta**: associar canal↔EPG manualmente, ocultar e renomear categorias. |
| [X] | UI responsiva 32"–100" | 4.0: `TvScreenAdapter` (densidade normalizada + overscan configurável). |
| [ ] | Controle de buffer e latência | Buffers fixos em `PlaybackFactory.kt`; sem modo baixa latência/buffer expandido. |
| [~] | Multiperfis e sincronização | Perfis com PIN (`ProfilesScreen.kt`, `SecureStore`). **Falta**: isolamento de favoritos/progresso por perfil e sync em nuvem (`SyncBackend` é só a interface, `NoopSyncBackend`). |

## 3. Plano de implementação

| Prioridade | Item | Estratégia |
|---|---|---|
| Alta | Catch-up pela EPG | No guia em grade, programas passados de canais com `tv_archive=1` abrem `PlayerArgs(timeshiftStartMs = programme.startMs, timeshiftDurationMin = duração)`. Reaproveita `timeshiftUrl`. |
| Alta | Controle de buffer/latência | Preferência `BufferProfile { LOW_LATENCY, BALANCED, EXTENDED }` → `DefaultLoadControl` com durações diferentes e, em HLS ao vivo, `MediaItem.LiveConfiguration(targetOffsetMs)`. |
| Alta | Legendas externas | `MediaItem.SubtitleConfiguration` para SRT/VTT (URL ou arquivo via SAF no celular); linha "Carregar legenda" no painel de opções. |
| Média | Edição de EPG e categorias | Tabela Room `channel_overrides(streamId, epgChannelId, hidden, customName)` e `category_overrides`; aplicada no `CatalogCacheRepository` ao mapear para o domínio. |
| Média | Stalker Portal | Completar `StalkerClient` (get_genres, get_ordered_list, create_link) e um `StalkerRepository` implementando a mesma interface do Xtream; login com MAC virtual. |
| Média | AFR | Android 11+: `Surface.setFrameRate`/`setFrameRateCompat` a partir do `Format.frameRate` do vídeo; Android 6–10: trocar `Display.Mode` via `WindowManager.LayoutParams.preferredDisplayModeId`. Opção liga/desliga (padrão desligado). |
| Média | Isolamento por perfil + sync | Coluna `profileId` já existe nas tabelas de progresso; completar favoritos/watchlist e implementar `SyncBackend` (ex.: Supabase/Firestore REST) com resolução por `updatedAt`. |
| Baixa | Decodificação DTS/TrueHD por software | Extensão FFmpeg do Media3 compilada com licença LGPL (só decoders de áudio) — evitar o build do Jellyfin, que é GPL-3.0 e incompatível com a licença Premium. |
| Baixa | Multi-View | Tela 2×2 com até 4 `ExoPlayer` em prévia (buffer 8 MB, SD), áudio só do quadro focado; limitar pelo `max_connections` da conta. |
| Baixa | PVR / gravação local | `ForegroundService` gravando o TS (OkHttp → arquivo) com agendamento WorkManager; exige permissão de armazenamento e checar `max_connections`. |

## 4. Issues sugeridas (gh issue create)

```bash
gh issue create --title "[Feature] Catch-up a partir da EPG" --label enhancement --body $'Abrir programas passados direto do guia em grade/lista em canais com tv_archive.\n\n## Critérios de aceite\n- [ ] Programa passado aparece marcado (ícone de replay) quando o canal tem tv_archive=1\n- [ ] OK abre o player no início do programa (timeshift)\n- [ ] Canais sem arquivo mostram "Replay indisponível"\n\n## Arquivos\n- ui/live/EpgGuideScreen.kt, ui/live/ChannelListWithEpg.kt\n- data/api/XtreamRepository.kt (timeshiftUrl)\n- ui/player/PlayerArgs.kt'
gh issue create --title "[Feature] Controle de buffer e latência" --label enhancement --body $'Modo baixa latência (esportes) e buffer expandido.\n\n## Critérios de aceite\n- [ ] Configurações → Player: Baixa latência / Equilibrado / Buffer expandido\n- [ ] Baixa latência começa em <1s e fica perto da borda ao vivo em HLS\n- [ ] Buffer expandido tolera quedas de rede de 30s sem travar\n\n## Arquivos\n- ui/player/PlaybackFactory.kt (DefaultLoadControl, LiveConfiguration)\n- data/prefs/SettingsStore.kt, ui/settings/SettingsScreen.kt'
gh issue create --title "[Feature] Legendas externas (SRT/VTT)" --label enhancement --body $'Carregar legenda externa por URL ou arquivo.\n\n## Critérios de aceite\n- [ ] Painel de opções do player: "Carregar legenda"\n- [ ] SRT e VTT, com sincronia ajustável (±)\n- [ ] Legenda lembrada por filme/episódio\n\n## Arquivos\n- ui/player/PlayerScreen.kt, ui/player/TrackPickerDialog.kt\n- data/db (tabela subtitle_overrides)'
gh issue create --title "[Feature] Edição manual de EPG e categorias" --label enhancement --body $'Associar EPG alternativa por canal e ocultar/renomear categorias.\n\n## Critérios de aceite\n- [ ] Segurar OK no canal → "Associar EPG" com busca por canal da XMLTV\n- [ ] Categorias podem ser ocultadas e renomeadas (Configurações → Categorias)\n- [ ] Sobrevive ao refresh do catálogo\n\n## Arquivos\n- data/db (channel_overrides, category_overrides + migração)\n- data/cache/CatalogCacheRepository.kt, ui/live, ui/settings'
gh issue create --title "[Feature] Stalker Portal" --label enhancement --body $'Completar o suporte a portais Stalker/Ministra.\n\n## Critérios de aceite\n- [ ] Login com URL do portal + MAC\n- [ ] Ao Vivo, Filmes e Séries carregados do portal\n- [ ] create_link para reprodução\n\n## Arquivos\n- data/stalker/StalkerClient.kt (+ StalkerRepository)\n- ui/login/LoginScreen.kt, data/cache/CatalogCacheRepository.kt'
gh issue create --title "[Feature] AFR — troca automática da taxa de quadros" --label enhancement --body $'Casar a taxa do display com o FPS do vídeo (24/25/30/50/60 Hz).\n\n## Critérios de aceite\n- [ ] Opção em Configurações (padrão desligado)\n- [ ] Filme 23.976 fps troca a TV para 24 Hz e volta ao sair\n- [ ] Sem efeito em aparelhos sem modos compatíveis\n\n## Arquivos\n- ui/player/PlayerScreen.kt (Format.frameRate), MainActivity.kt (preferredDisplayModeId / Surface.setFrameRate)'
gh issue create --title "[Feature] Perfis isolados e sincronização na nuvem" --label enhancement --body $'Favoritos/progresso por perfil e sync entre aparelhos.\n\n## Critérios de aceite\n- [ ] Cada perfil tem favoritos, watchlist e progresso próprios\n- [ ] Login opcional de sync; mudanças aparecem no outro aparelho em até 1 min\n\n## Arquivos\n- data/db (profileId em favorites/watchlist + migração), data/sync/SyncBackend.kt'
gh issue create --title "[Feature] Decodificação de DTS/TrueHD por software (FFmpeg LGPL)" --label enhancement --body $'Tocar DTS/TrueHD em aparelhos sem decoder nem receiver.\n\n## Critérios de aceite\n- [ ] Extensão FFmpeg do Media3 compilada só com decoders de áudio, licença LGPL\n- [ ] Conteúdo só-DTS toca com som em celular/TV sem DTS\n- [ ] Passthrough continua tendo prioridade quando disponível\n\n## Arquivos\n- app/build.gradle.kts, ui/player/PlaybackFactory.kt (EXTENSION_RENDERER_MODE_ON)'
gh issue create --title "[Feature] Multi-View (2 a 4 canais)" --label enhancement --body $'Assistir vários canais ao mesmo tempo.\n\n## Critérios de aceite\n- [ ] Layout 2 e 2x2, áudio do quadro focado\n- [ ] Respeita max_connections da conta e avisa quando excede\n\n## Arquivos\n- ui/player (MultiViewScreen.kt), ui/player/PlaybackFactory.kt'
gh issue create --title "[Feature] Gravação local (PVR)" --label enhancement --body $'Gravar canais ao vivo no armazenamento local/externo.\n\n## Critérios de aceite\n- [ ] Gravar agora / agendar pela EPG\n- [ ] Lista de gravações reproduzível offline\n- [ ] Avisa quando a conta não permite conexão extra\n\n## Arquivos\n- novo pacote record/ (ForegroundService + WorkManager), ui/live, AndroidManifest.xml'
```
