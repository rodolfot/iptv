# TartaTV Web (LG webOS + Samsung Tizen) — Plano de Projeto

Este documento descreve, passo a passo, como criar uma versão web do TartaTV
que rode nas Smart TVs **LG (webOS)** e **Samsung (Tizen)** a partir de uma
única base de código. O app Android atual (Compose/ExoPlayer/Kotlin) continua
existindo em paralelo, sem alterações.

O alvo visual é **idêntico ao layout TV/Tablet do app Android atual**: tema
escuro, drawer de categorias à esquerda, grid de pôsteres à direita, header
com busca e tabs no topo.

---

## 1. Stack escolhida

| Camada | Tecnologia | Por quê |
|---|---|---|
| Linguagem | TypeScript | Mesmas garantias que o Kotlin oferece hoje. |
| UI | React 18 | Maturidade, comunidade, todos os exemplos de webOS/Tizen são em React. |
| Build | Vite | Geração de bundle estático rápida; saída funciona nos dois SDKs. |
| Navegação espacial (D-pad) | `@noriginmedia/norigin-spatial-navigation` | Cuida do foco com teclas, funciona idêntico em LG e Samsung. |
| Player | `shaka-player` (HLS) + `mpegts.js` (fallback TS) | Cobre os formatos que o Xtream serve. |
| HTTP | `axios` (ou `fetch` puro) | API Xtream é JSON simples. |
| Estado | Zustand | Leve, sem boilerplate; equivalente aos `StateFlow` que usamos hoje. |
| Storage local | `localStorage` + IndexedDB (`idb-keyval`) | Substitui DataStore + Room. |
| i18n | `react-i18next` | Substitui `values-*` do Android. |
| Empacotamento Tizen | `tizen-cli` (Tizen Studio) | Gera `.wgt`. |
| Empacotamento webOS | `@webosose/ares-cli` | Gera `.ipk`. |

**Não usar:** React Native, react-native-web, Capacitor, Enact. Razões:
- React Native for TV não roda em webOS/Tizen.
- Enact é um framework completo da LG — funciona, mas amarra à filosofia da
  LG e torna o porte pra Tizen mais trabalhoso.
- Capacitor cobriria mobile, mas você já tem o app Android nativo.

---

## 2. Estrutura do repositório

Criar repositório separado (sugestão: `tartatv-web`).

```
tartatv-web/
├── README.md
├── package.json
├── tsconfig.json
├── vite.config.ts
├── index.html                          # Entry point web (mesmo para LG/Samsung)
├── public/
│   ├── icon-512.png
│   └── favicon.png
├── platform/
│   ├── tizen/                          # Empacotamento Samsung
│   │   ├── config.xml                  # Manifest Tizen
│   │   └── icon.png
│   └── webos/                          # Empacotamento LG
│       ├── appinfo.json                # Manifest webOS
│       └── icon.png
├── scripts/
│   ├── build-tizen.sh                  # vite build → tizen package
│   └── build-webos.sh                  # vite build → ares-package
└── src/
    ├── main.tsx                        # ReactDOM.createRoot
    ├── App.tsx                         # Router + theme
    ├── theme/
    │   ├── colors.ts                   # #0B0F14, #141A22, #00B7FF, etc.
    │   ├── typography.ts               # labelSmall=10sp, titleSmall=14sp...
    │   └── dimens.ts                   # ScreenPadding, CardSpacing
    ├── platform/
    │   ├── detect.ts                   # isWebOS() / isTizen() / isBrowser()
    │   ├── keys.ts                     # Mapeia keyCodes (LG≠Samsung) → BACK/OK/UP
    │   └── exit.ts                     # webOS.platformBack() / tizen.application.exit()
    ├── data/
    │   ├── xtream/
    │   │   ├── client.ts               # baseUrl + username + password
    │   │   ├── live.ts                 # get_live_streams, categories
    │   │   ├── vod.ts                  # get_vod_streams, vod_info
    │   │   ├── series.ts               # get_series, series_info
    │   │   └── types.ts                # DTOs (LiveChannel, Movie, Series…)
    │   ├── cache/
    │   │   ├── store.ts                # IndexedDB schema (favorites, watchlist…)
    │   │   ├── history.ts              # Histórico de canais (últimos 10)
    │   │   └── progress.ts             # Progresso de filmes/episódios
    │   └── settings.ts                 # localStorage (host, user, idioma, perfil)
    ├── state/
    │   ├── auth.store.ts               # Zustand: credenciais, isLoggedIn
    │   ├── catalog.store.ts            # categorias, canais, filmes, séries
    │   ├── playback.store.ts           # canal/filme/episódio atual
    │   └── ui.store.ts                 # tab ativa, busca, drawer aberto
    ├── ui/
    │   ├── common/
    │   │   ├── PosterCard.tsx          # 2:3, marquee no foco, barra preta
    │   │   ├── ChannelCard.tsx         # Mesma 2:3, logo + nome
    │   │   ├── CategoryRow.tsx         # Item do drawer
    │   │   ├── SortMenu.tsx
    │   │   ├── SearchField.tsx
    │   │   ├── Loading.tsx
    │   │   └── FocusRoot.tsx           # Wrapper Norigin
    │   ├── tabs/
    │   │   └── TopBar.tsx              # Pílula azul + foco contorno
    │   ├── home/
    │   │   └── HomeScreen.tsx          # Canais recentes + continuar filmes/séries
    │   ├── live/
    │   │   ├── LiveScreen.tsx          # Drawer + grid + busca
    │   │   └── ChannelPreviewPip.tsx
    │   ├── movies/
    │   │   ├── MoviesScreen.tsx
    │   │   └── MovieDetailScreen.tsx
    │   ├── series/
    │   │   ├── SeriesScreen.tsx
    │   │   ├── SeriesDetailScreen.tsx
    │   │   └── EpisodesDialog.tsx
    │   ├── favorites/
    │   │   └── FavoritesScreen.tsx     # 3 seções compactas
    │   ├── watchlist/
    │   │   └── WatchlistScreen.tsx
    │   ├── search/
    │   │   └── SearchScreen.tsx
    │   ├── settings/
    │   │   └── SettingsScreen.tsx
    │   ├── login/
    │   │   └── LoginScreen.tsx
    │   ├── onboarding/
    │   │   └── OnboardingScreen.tsx    # Termos + idioma + form factor
    │   └── player/
    │       ├── PlayerScreen.tsx        # Shaka + overlay back/title
    │       ├── PlayerOverlay.tsx       # Próximo em Xs / reconectando
    │       └── shaka-wrapper.ts
    ├── i18n/
    │   ├── index.ts
    │   ├── pt.json                     # Idêntico ao values/strings.xml atual
    │   ├── en.json
    │   ├── es.json
    │   ├── it.json
    │   ├── fr.json
    │   └── de.json
    └── routes.tsx                      # React Router (hash-based, funciona em wgt/ipk)
```

---

## 3. Funcionalidades a implementar (paridade com Android atual)

### 3.1 Onboarding
- Tela de termos + política de privacidade.
- Seleção de idioma (sistema, pt, en, es, it, fr, de).
- Seleção de tipo de dispositivo: TV / Tablet / Phone (mesma escolha do Android — define
  densidade de cards).
- Persistir `termsAccepted=true` no localStorage; ao abrir o app, pular onboarding.

### 3.2 Login
- Campos: host (URL Xtream), usuário, senha.
- Botão "Testar conexão" — chama `player_api.php?action=get_account_info` e mostra OK/erro.
- Botão "Salvar credenciais" (só habilita após teste OK).
- Persistir em localStorage. Não usar criptografia em smart TV (sandbox é estrito;
  basta separar por origin/app id).

### 3.3 Home (tela "Início")
**Layout (idêntico ao Android TV atual):**
- TopBar fixo no alto com tabs: Início, Buscar, Ao Vivo, Filmes, Séries, Favoritos, Lista, Configurações.
- Tab ativa em **pílula azul preenchida** (`#00B7FF`).
- Tab em **foco D-pad** ganha contorno azul, fundo transparente.
- Logo TartaTV à esquerda.

**Conteúdo:**
- **Canais recentes** — LazyRow horizontal com até 10 últimos canais assistidos
  (vem do `history` no IndexedDB).
- **Continuar séries** — itens com progresso `>0%`, ordenados por mais recente.
- **Continuar filmes** — idem.
- **Recomendados (filmes)** — algoritmo simples baseado em categoria do último filme assistido.
- **Recomendados (séries)** — idem.

Cards: 110dp de largura (`px` equivalente — ver §6), aspect ratio 2:3, nome em barra
preta (`rgba(0,0,0,0.8)`) na base do card, fonte `labelSmall` (10sp ≈ 13px no web TV).

### 3.4 Ao Vivo
- **Drawer** à esquerda (280px), lista de categorias, scroll vertical.
- **Header** à direita: campo de busca preenchendo toda a largura disponível.
- **Grid** abaixo do header: cards 2:3 adaptativos (95px de largura ≈ 16 colunas em 1080p).
- **PIP de preview** no canto inferior direito: mini-player do canal em foco
  (com delay de 600ms pra não trocar a cada D-pad).
- **OK** no card → toca direto (sem tela intermediária).
- **Long-OK** no card → toggle favorito (com snackbar).
- Auto-seleção da 1ª categoria; foco inicial no item selecionado do drawer.

### 3.5 Filmes
- Mesma estrutura do Ao Vivo (drawer + grid + busca).
- **OK** no card → tela de detalhe (sinopse, "Continuar de X:XX" se houver progresso,
  "Reiniciar", "Adicionar favorito", botão bandeirinha (watchlist) só com ícone).
- **Long-OK** no card → toca direto, pulando o detalhe.
- Marquee no nome quando focado.

### 3.6 Séries
- Mesma estrutura.
- Detalhe da série: poster compacto (160×240px na TV), título + metadados (release, rating,
  gênero, diretor, elenco) em `bodySmall`, sinopse limitada a 4 linhas.
- **Temporadas em FlowRow** (quebra de linha automática quando muitas, como Naruto).
  Foco inicial vai pro botão da temporada selecionada.
- Clicar em uma temporada abre **dialog** com lista de episódios (capa pequena + "T2E33 — Nome do episódio" + sinopse curta).
- OK no episódio → player com auto-próximo no fim.

### 3.7 Favoritos
- 3 seções horizontais: Canais, Filmes, Séries.
- Cards compactos (110px, `labelSmall`).
- Header de cada seção: `"Canais (N)"`, etc.

### 3.8 Lista (Watchlist)
- Estrutura idêntica a Favoritos, 3 seções separadas.

### 3.9 Busca
- Campo de busca grande no topo.
- Resultados em 3 seções (canais, filmes, séries).
- FTS no client: filtra os caches do IndexedDB por `name LIKE %query%` quando o
  catálogo cabe em memória; senão paginar.

### 3.10 Configurações
- Servidor (mostrar host/user, botão "Trocar servidor" abre form).
- PIN parental (input numérico).
- Intervalo de atualização (combo: 1h, 4h, 12h, 1d, 4d, 7d, 30d).
- **Última atualização** — timestamp + botão "Atualizar agora" (background, snackbar).
- Idioma (combo). **Trocar idioma abre dialog "Reiniciar para aplicar idioma?"** —
  ao confirmar, faz `location.reload()` (em web é equivalente ao restart Android).
- Reset de progresso (com confirmação).
- Sobre + perfis.

### 3.11 Player
- **Shaka Player** para HLS, **mpegts.js** como fallback para `.ts` direto.
- Overlay no topo esquerdo: ícone "Voltar" + título (fade após 5s sem input).
- D-pad esquerda/direita: seek -10s / +10s.
- OK: pause/play.
- Banner "Próximo em Xs" no canto inferior direito durante os últimos 10s
  (só pra séries quando há próximo episódio na playlist).
- Auto-próximo episódio ao final.
- Em canais Live: 3 tentativas automáticas de reconexão + overlay "Reconectando (N/3)…";
  se falhar, tela com botão "Tentar novamente" e mensagem "Tente novamente em instantes
  ou contate o dono da lista."
- Saída do player (Back) → retorna pra origem (Home/Filmes/Séries).

### 3.12 Idiomas
- 6 locales (mesmas chaves do Android: `tab_home`, `continue_series`, `player_next_in` etc.).
- Copiar literalmente os JSONs equivalentes aos `values-*/strings.xml`.

### 3.13 Controle remoto
- **LG webOS**: Magic Remote (cursor + scroll) e D-pad. Tratar as duas formas.
- **Samsung Tizen**: D-pad puro. Suporte opcional ao "Smart Touch".
- Tecla **VOLTAR** (back):
  - webOS: keyCode 461 (`KEY_GOBACK`).
  - Tizen: keyCode 10009.
  - Normalizar ambos em `keys.ts` → emite evento `back` global.
- Tecla **PLAY/PAUSE**, **AVANÇAR**, **VOLTAR** (mídia): mapeadas no player.
- Tecla **EXIT** (sair): webOS chama `window.close()` ou `webOS.platformBack`;
  Tizen chama `tizen.application.getCurrentApplication().exit()`.

---

## 4. Passo a passo de implementação

### Fase 0 — Preparação (1 dia)
1. Criar repositório `tartatv-web` (GitHub/GitLab).
2. Instalar **Tizen Studio** (Samsung — https://developer.samsung.com/smarttv/develop/tools/tizen-studio.html).
   - Criar conta de desenvolvedor Samsung.
   - Gerar certificado de autor (Samsung Certificate Manager).
3. Instalar **LG webOS TV CLI** (`npm install -g @webosose/ares-cli`).
   - Criar conta de desenvolvedor LG (https://webostv.developer.lge.com/).
   - Habilitar **Developer Mode** na TV LG (app Developer Mode da loja LG).
4. Definir IPs das TVs na rede local.

### Fase 1 — Esqueleto (2 dias)
1. `npm create vite@latest tartatv-web -- --template react-ts`.
2. Instalar dependências:
   ```
   npm i react-router-dom zustand axios shaka-player mpegts.js
   npm i @noriginmedia/norigin-spatial-navigation
   npm i react-i18next i18next
   npm i idb-keyval
   ```
3. Criar `src/theme/` com as cores/typography copiadas do `Theme.kt` atual.
4. Criar `src/platform/detect.ts` e `keys.ts`.
5. Configurar React Router (hash-based: `HashRouter` — necessário em `wgt`/`ipk` porque
   não há servidor, apenas `file://`).
6. Renderizar TopBar + Roteamento básico (telas vazias).

### Fase 2 — Auth + Login (2 dias)
1. `data/xtream/client.ts` com `axios.create({ baseURL })`.
2. Implementar `getAccountInfo()` e validar resposta.
3. Tela de Login + Onboarding.
4. Persistência em `localStorage`.

### Fase 3 — Catálogo + caching (3 dias)
1. Implementar endpoints `get_live_categories`, `get_live_streams`, idem movies/series.
2. Schema do IndexedDB:
   - `categories` (key: `type-id`)
   - `live_channels` (key: streamId)
   - `movies`
   - `series`
   - `favorites` (key: `type-itemId`)
   - `watchlist`
   - `live_history`
   - `movie_progress`
   - `episode_progress`
   - `cache_meta` (key: scope; valor: lastUpdatedAt)
3. Stale-while-revalidate: ler do cache imediatamente, refetch em background.

### Fase 4 — Componentes comuns (3 dias)
1. `PosterCard` com marquee no foco (usar CSS `animation` ou Norigin's `useFocusable`).
2. `ChannelCard`.
3. `CategoriesDrawer` com `useFocusable` em cada item, foco inicial no selecionado.
4. `SearchField`.
5. `TopBar` com pílula azul + outline no foco.

### Fase 5 — Telas principais (5-7 dias)
- Home, Live, Movies, Series, Favorites, Watchlist, Search.
- Cada uma usando os componentes comuns + estado Zustand.

### Fase 6 — Detalhes + Dialogs (3 dias)
- MovieDetailScreen, SeriesDetailScreen, EpisodesDialog.

### Fase 7 — Player (4-5 dias)
1. Wrapper do Shaka Player.
2. Fallback mpegts.js (detecta `.ts` na URL).
3. Overlay completo (back, title, próximo episódio, reconectando).
4. Auto-próximo episódio (playlist no Shaka — funciona similar ao ExoPlayer).
5. Salvar progresso a cada 10s.

### Fase 8 — Settings + i18n (2 dias)
- Telas de configuração.
- Carregar idiomas, switch com dialog de restart.

### Fase 9 — Empacotamento Tizen (1-2 dias)
1. Criar `platform/tizen/config.xml`:
   ```xml
   <widget xmlns="http://www.w3.org/ns/widgets"
           xmlns:tizen="http://tizen.org/ns/widgets"
           id="http://tartatv.app/web"
           version="1.0.0"
           viewmodes="maximized">
     <tizen:application id="TartaTVWeb.tartatv"
                        package="TartaTVWeb"
                        required_version="6.0"/>
     <content src="index.html"/>
     <tizen:profile name="tv"/>
     <icon src="icon.png"/>
     <name>TartaTV</name>
     <tizen:privilege name="http://tizen.org/privilege/internet"/>
   </widget>
   ```
2. Script `build-tizen.sh`:
   ```bash
   vite build
   cp -r dist/* tizen-build/
   cp platform/tizen/config.xml tizen-build/
   cp platform/tizen/icon.png tizen-build/
   tizen package -t wgt -s <CERT_NAME> -- tizen-build/
   ```
3. Instalar na TV: `tizen install -n TartaTV.wgt -t <DEVICE_NAME>`.

### Fase 10 — Empacotamento webOS (1-2 dias)
1. Criar `platform/webos/appinfo.json`:
   ```json
   {
     "id": "com.tartatv.web",
     "version": "1.0.0",
     "vendor": "TartaTV",
     "type": "web",
     "main": "index.html",
     "title": "TartaTV",
     "icon": "icon.png",
     "uiRevision": 2
   }
   ```
2. Script `build-webos.sh`:
   ```bash
   vite build
   cp -r dist/* webos-build/
   cp platform/webos/appinfo.json webos-build/
   cp platform/webos/icon.png webos-build/
   ares-package webos-build/
   ```
3. Instalar na TV:
   ```
   ares-setup-device                       # uma vez
   ares-install --device <NAME> com.tartatv.web_1.0.0_all.ipk
   ```

### Fase 11 — QA cross-platform (3-5 dias)
- Testar D-pad em ambas TVs.
- Testar player com canais reais (HLS, MPEG-TS).
- Testar reconexão.
- Testar memória em modelos antigos (LG 2018, Samsung 2019) — pode precisar limitar
  tamanho do catálogo em memória.

---

## 5. Layout — referência visual

### 5.1 Paleta (copiar do Theme.kt atual)
```ts
export const colors = {
  background: '#0B0F14',
  surface:    '#141A22',
  surfaceVariant: '#1C2430',
  primary:    '#00B7FF',
  onPrimary:  '#001620',
  textPrimary:   '#F2F4F8',
  textSecondary: '#9AA4B2',
  accent:     '#FFB800',
  blackOverlay: 'rgba(0,0,0,0.8)',
};
```

### 5.2 Tipografia (copiar do Theme.kt atual — reduzida)
```ts
export const typography = {
  displayLarge:   { fontSize: 52, fontWeight: 700 },
  displayMedium:  { fontSize: 44, fontWeight: 700 },
  headlineLarge:  { fontSize: 36, fontWeight: 600 },
  headlineMedium: { fontSize: 28, fontWeight: 600 },
  headlineSmall:  { fontSize: 24, fontWeight: 600 },
  titleLarge:     { fontSize: 22, fontWeight: 600 },
  titleMedium:    { fontSize: 18, fontWeight: 500 },
  titleSmall:     { fontSize: 14, fontWeight: 500 },
  bodyLarge:      { fontSize: 18 },
  bodyMedium:     { fontSize: 14 },
  bodySmall:      { fontSize: 12 },
  labelLarge:     { fontSize: 14, fontWeight: 500 },
  labelMedium:    { fontSize: 12, fontWeight: 500 },
  labelSmall:     { fontSize: 10, fontWeight: 500 },
};
```

### 5.3 Wireframe — Home / Filmes / Séries / Ao Vivo

```
┌──────────────────────────────────────────────────────────────────────────┐
│ TartaTV  [Início][Buscar][Ao Vivo][Filmes][Séries][Favs][Lista][Config]  │  ← TopBar
├──────────────────────────────────────────────────────────────────────────┤
│ ┌────────────┐  ┌─────────────────────────────────────────────────────┐ │
│ │ Categorias │  │ [Buscar...........................][Ordenar ▾]      │ │  ← Header direito
│ │            │  │                                                      │ │
│ │ ▸ Filmes   │  ├─────────────────────────────────────────────────────┤ │
│ │   Animação │  │ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ │ │
│ │   4K       │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │ │  ← Grid 95px
│ │   Lançam.  │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │ │     aspect 2:3
│ │   Ação     │  │ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ │ │     barra preta
│ │   Comédia  │  │  Nm.  Nm.  Nm.  Nm.  Nm.  Nm.  Nm.  Nm.  Nm.  Nm. │ │     com nome
│ │   ...      │  │                                                      │ │
│ │            │  │ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ ┌──┐ │ │
│ │            │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │ │
│ │  280px     │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │  │ │ │
│ │  drawer    │  │ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ └──┘ │ │
│ │  fixo      │  │  Nm.  Nm.  Nm.  Nm.  Nm.  Nm.  Nm.  Nm.  Nm.  Nm. │ │
│ │            │  │                                                      │ │
│ │            │  │            (Em Ao Vivo: PIP do canal focado aqui ↘) │ │
│ │            │  │                                          ┌────────┐  │ │
│ │            │  │                                          │ Preview │  │ │
│ │            │  │                                          │  Live   │  │ │
│ │            │  │                                          │  16:9   │  │ │
│ │            │  │                                          └────────┘  │ │
│ └────────────┘  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```

### 5.4 Wireframe — Detalhe de Série
```
┌──────────────────────────────────────────────────────────────────────────┐
│ ←  TartaTV  [tabs...]                                                    │
├──────────────────────────────────────────────────────────────────────────┤
│  ┌──────┐  Título da Série                                               │
│  │      │  Lançamento: 2018                                              │
│  │ 160  │  Nota: 8.5                                                     │
│  │  x   │  Gênero: Animação, Aventura                                    │
│  │ 240  │  Diretor: ...                                                  │
│  │  px  │  Elenco: ...                                                   │
│  └──────┘  [Continuar T1E5] [♡ Favorito] [🔖]                            │
│                                                                          │
│  Sinopse                                                                 │
│  Lorem ipsum dolor sit amet, consectetur adipiscing elit...              │
│                                                                          │
│  Temporadas                                                              │
│  [T1 (12)] [T2 (24)] [T3 (24)] [T4 (24)] [T5 (24)] [T6 (24)]            │  ← FlowRow
│  [T7 (24)] [T8 (24)] [T9 (24)] [T10 (24)] [T11 (24)] [T12 (24)]         │     quebra
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘

Ao clicar numa temporada:
                ┌─────────────────────────────────────┐
                │  Episódios — T2                     │
                ├─────────────────────────────────────┤
                │  [img] T2E1 — Nome do episódio      │
                │        Sinopse curta (até 2 linhas) │
                │  [img] T2E2 — Nome do episódio      │
                │        Sinopse curta                │
                │  [img] T2E3 — Nome do episódio      │
                │  ...                                │
                └─────────────────────────────────────┘
```

### 5.5 Wireframe — Player
```
┌──────────────────────────────────────────────────────────────────────────┐
│  ← Título do vídeo                                                       │  ← Overlay top
│                                                                          │     (fade 5s)
│                                                                          │
│                                                                          │
│                       [vídeo full screen]                                │
│                                                                          │
│                                                                          │
│                                                                          │
│                                                                          │
│                                                                          │
│                                                                          │
│                                              ┌───────────────────────┐   │
│                                              │ Próximo em 7s         │   │  ← Banner
│                                              │ T2E34 — Nome do prox  │   │     fim-de-ep
│                                              └───────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
   Controles: D-pad ← / → seek; OK play/pause; Back sair
```

---

## 6. Conversão dp → px

Smart TVs renderizam em 1920×1080 (1×) ou 3840×2160 (2×). Use rem ou px diretos
baseando 1dp ≈ 1px em TVs HD; multiplicar por 2 para 4K (CSS `transform: scale`
ou media query). Ponto de partida:

| dp (Android) | px (TV web HD) |
|---|---|
| 95dp | 95px (largura mínima do card) |
| 280dp | 280px (drawer) |
| 110dp | 110px (favoritos) |
| 14sp | 14px (titleSmall) |
| 10sp | 10px (labelSmall) |

> Atenção: TVs com modo Game ou "fitness" podem alterar zoom — usar `viewport`
> fixo em `<meta name="viewport" content="width=1920, initial-scale=1">`.

---

## 7. Player Xtream — pontos de atenção

| Tipo de URL | Formato | Player a usar |
|---|---|---|
| `…/live/USER/PASS/CHANNEL.ts` | MPEG-TS | mpegts.js |
| `…/live/USER/PASS/CHANNEL.m3u8` | HLS | shaka |
| `…/movie/USER/PASS/MOVIE.mp4` | MP4 | `<video>` nativo |
| `…/movie/USER/PASS/MOVIE.mkv` | MKV | mpegts.js (alguns codecs) ou nativo |
| `…/series/USER/PASS/EP.mp4` | MP4 | nativo |
| `…/series/USER/PASS/EP.mkv` | MKV | mpegts.js |

Estratégia: detectar extensão na URL e instanciar o player apropriado. Encapsular
em `PlayerWrapper` com a mesma interface.

**Codec na TV antiga:** se a URL servir HEVC e a TV não suportar, o `<video>`
nem dispara erro — só fica preto. Capturar `loadedmetadata` timeout (5s) e
mostrar "Codec não suportado, contate o dono da lista." Isso é caso de borda
real em TVs LG/Samsung pré-2020.

---

## 8. Submissão nas lojas (opcional)

Pra distribuir além do "modo desenvolvedor":

- **LG Content Store**: criar conta no LG Seller Lounge, submeter o `.ipk`, passar
  por revisão (~2 semanas). Conta gratuita.
- **Samsung Apps Store**: conta no Samsung Apps TV Seller Office, submeter `.wgt`.
  Conta gratuita; revisão ~3 semanas. Aplicativos de IPTV podem ser barrados
  dependendo da política da Samsung — recomendo deixar a app "agnóstica"
  (usuário fornece a URL).

> Esses processos não impedem que o app rode em modo desenvolvedor para uso pessoal.

---

## 9. Estimativa total

| Fase | Dias |
|---|---|
| 0. Preparação | 1 |
| 1. Esqueleto | 2 |
| 2. Login | 2 |
| 3. Catálogo + cache | 3 |
| 4. Componentes comuns | 3 |
| 5. Telas principais | 5-7 |
| 6. Detalhes + dialogs | 3 |
| 7. Player | 4-5 |
| 8. Settings + i18n | 2 |
| 9. Tizen packaging | 1-2 |
| 10. webOS packaging | 1-2 |
| 11. QA cross-platform | 3-5 |
| **Total** | **30-37 dias úteis** |

---

## 10. Checklist final antes de publicar

- [ ] App carrega offline (cache do bundle).
- [ ] Catálogo carrega offline (cache do IndexedDB).
- [ ] Back funciona em todas as telas (LG=461, Samsung=10009).
- [ ] Exit funciona (Tizen `tizen.application.exit()`, webOS `webOS.platformBack`).
- [ ] D-pad foca o item correto em todas as telas (drawer, grid, dialog).
- [ ] Marquee aparece no foco e desaparece quando perde.
- [ ] Pílula azul + contorno azul funcionam (selected ≠ focused).
- [ ] Player toca HLS e MPEG-TS sem freezes.
- [ ] Auto-próximo episódio funciona.
- [ ] Banner "Próximo em Xs" aparece nos últimos 10s.
- [ ] Reconexão (3x) + mensagem de erro com botão Tentar/Voltar.
- [ ] Idioma reinicia o app com confirmação.
- [ ] Refresh do catálogo na abertura respeita o intervalo configurado.
- [ ] Favoritos/Watchlist persistem entre reinicializações.
- [ ] Histórico de canais persiste (últimos 10).
- [ ] Tipografia coincide visualmente com o Android atual.
- [ ] Cores idênticas ao Android atual.
- [ ] Memória estável (sem leaks no player) após 1h de uso contínuo.

---

## Apêndice — Comandos úteis

```bash
# Rodar local no browser (Chrome funciona muito bem como TV simulator)
npm run dev

# Build estático
npm run build

# Empacotar e instalar Samsung Tizen
./scripts/build-tizen.sh
tizen install -n dist-tizen/TartaTV.wgt -t <DEVICE_ID>

# Empacotar e instalar LG webOS
./scripts/build-webos.sh
ares-install --device tv com.tartatv.web_1.0.0_all.ipk

# Emulador LG (sem TV física)
ares-launch --device emulator com.tartatv.web

# DevTools webOS
ares-inspect --device tv com.tartatv.web
```
