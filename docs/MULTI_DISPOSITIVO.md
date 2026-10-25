# Suporte multi-dispositivo (#20, #21, #22, #23)

Um único APK atende TV/TV box, celular, tablet e central multimídia. Regras de negócio
(ViewModels, repositórios, player) são as mesmas em todos; só a camada de UI, entrada e
ciclo de vida se adapta.

## 1. Detecção do dispositivo

`ui/common/TvDimensions.kt` → `rememberTvDim()` devolve as medidas (`FormFactor`) e a
classe de hardware (`DeviceClass`: `TV`, `PHONE`, `TABLET`, `AUTOMOTIVE`). Ordem:

1. Escolha manual do usuário (onboarding / Configurações → Tipo de aparelho), incluindo
   **Central multimídia (carro)** — necessário em centrais Android de painel, que não
   se anunciam como carro;
2. `UI_MODE_TYPE_CAR` → `AUTOMOTIVE` (touch, medidas de celular/tablet);
3. `UI_MODE_TYPE_TELEVISION` → `TV`;
4. `smallestScreenWidthDp` (< 600 celular, < 840 tablet, senão medidas de TV).

`isRealTv()` (`TvScreenAdapter.kt`) distingue TV de verdade de tablet grande.

## 2. Camada de UI adaptativa

- **TV (#17, #21)** — `TvScreenAdapter` na raiz (`MainActivity.AppNav`):
  densidade normalizada para 960dp de largura lógica (o layout fica na mesma proporção
  em 32"–100", 720p/1080p/4K, qualquer DPI reportado pelo aparelho) e margem de overscan
  configurável (padrão 5%) exposta em `LocalOverscanPadding` — aplicada nas telas de
  navegação e nos controles do player (o vídeo segue de borda a borda).
  `CollapsibleDrawerLayout` dá a gaveta retrátil (280dp ↔ trilho de 64dp) em Ao Vivo,
  Filmes, Séries e Favoritos; `FocusMemory` devolve o foco ao último item ao alternar
  entre gaveta e grade.
- **Celular/tablet (#22)** — UI touch (`useTouchUi`); grades `Adaptive` mostram mais
  colunas no tablet; visualização em duas colunas (lista de canais + player/EPG) nos
  modos de Ao Vivo "Lista com categorias" e "Lista com prévia" (Config → EPG →
  Visualização); player com gestos (`PlayerGestureLayer`), paisagem automática no
  celular (`LandscapeWhilePlaying`) e PiP automático (`AutoEnterPip`).
- **Central multimídia (#23)** — UI touch com medidas de tablet; layouts ultra-wide e
  verticais usam as mesmas grades adaptativas e o player em `RESIZE_MODE_FIT` (sem
  distorcer o vídeo).

## 3. Abstração de entrada

`ui/common/InputMapper.kt`:

- `InputAction` + `InputMapper.actionOf(keyCode)` — ↑↓←→, OK, Voltar, Play/Pause,
  ⏭/⏮, CH+/CH−, avançar/retroceder, de controle remoto, teclado, gamepad e botões de
  mídia. O player trata ações (CH+/⏭ = próximo canal no Ao Vivo, próximo
  episódio/filme no VOD).
- `InputMapper.normalize()` — knob rotativo (`NAVIGATE_NEXT/PREVIOUS/IN/OUT`) vira
  D-pad no `MainActivity.dispatchKeyEvent`, então listas e menus funcionam sem código
  por tela.
- `DpadRepeatThrottle` — limita ↑↓←→ a ~11/s ao segurar a tecla (#25).
- **MediaSession** (`rememberPlayerMediaSession`) — recebe comandos que não passam pela
  janela do app: botões do volante (a central converte o CANBUS em comandos de mídia),
  Bluetooth, fones. No Ao Vivo ⏭/⏮ trocam de canal.

## 4. Ciclo de vida e áudio

- Foco de áudio pelo ExoPlayer (`handleAudioFocus = true`): GPS abaixa o volume
  (ducking) e volta; chamada pausa e retoma; fone desconectado pausa.
- Regra de direção (`car/DrivingMonitor.kt`): com "Só áudio com o veículo em
  movimento" ligado, acima de 10 km/h (GPS) o vídeo é desligado e o áudio continua;
  volta após 8 s abaixo de 5 km/h.

## Limitações conhecidas (central NAVPRO / HR-V 2017)

- O volante fala CANBUS com a central; o app só recebe o que o firmware da central
  repassa como comando de mídia (normalmente ⏭, ⏮, ⏯ e volume). Botões que o firmware
  não mapeia não chegam a nenhum app.
- Sem sinal de câmbio/freio de mão acessível, a regra de direção depende do GPS da
  central (precisa de permissão de localização e sinal de satélite).
- Android Auto/CarPlay projetado (celular espelhado na central) não é suportado: o
  Android Auto não permite apps de vídeo.
