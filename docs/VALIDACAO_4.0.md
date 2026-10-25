# Como validar a 4.0.0

Roteiro por issue. Os itens marcados ✅ foram verificados no emulador Android TV 4K
(3840×2160, 640 dpi) com um servidor Xtream falso servindo o `xtream-dump/`.

## #17 / #21 — TV de 32" a 100", overscan e D-pad

1. Configurações → Tela → **Área segura da TV (overscan)**: 5% (padrão). Nas bordas
   nada pode ficar cortado; ✅ a margem aparece como fundo, sem moldura.
2. Simular TVs com densidades diferentes (o layout deve ficar idêntico) ✅:
   ```bash
   adb shell wm density 320   # TV que se anuncia como 1920dp de largura
   adb shell wm density 213   # tvdpi
   adb shell wm density reset
   adb shell wm size 1280x720 # 720p
   adb shell wm size reset
   ```
3. Ao Vivo/Filmes/Séries/Favoritos ✅: a gaveta abre (280dp) com o foco nela e recolhe
   num trilho com o nome da categoria quando o foco vai para a grade.
4. O app inteiro é operável só com setas, OK e Voltar.

## #18 — Dolby Atmos / DTS

- Amostras: Dolby (https://developer.dolby.com/tools-media/sample-media/), Kodi
  samples (https://kodi.wiki/view/Samples — DTS-HD, TrueHD Atmos, E-AC-3 JOC em MKV/TS).
- Com receiver/soundbar via HDMI (TV box com passthrough habilitado no sistema):
  selo **Dolby Atmos · 7.1 · Passthrough** ao lado do título; o receiver indica Atmos/DTS.
- Configurações → Player → **Passthrough de áudio** desligado: o mesmo arquivo toca em
  PCM (selo sem "Passthrough").
- Arquivo só com DTS num celular sem decoder: aviso "O áudio deste conteúdo (DTS) não
  é suportado…" com botão **Player externo** (antes: vídeo mudo).
- Painel de opções → Áudio: faixas listadas com codec ("Inglês · Dolby Atmos · 7.1").

## #22 — Celular e tablet

- Player: deslizar à esquerda (brilho) e à direita (volume) mostra o indicador;
  toque duplo nas laterais = ±10 s (desligado no Ao Vivo); toque simples alterna controles.
- Celular gira para paisagem ao abrir o player e volta ao sair.
- Botão Home com o player aberto: entra em PiP com áudio e vídeo (Android 12+ sem
  esperar animação).
- Tablet: Config → EPG → Visualização → "Lista com categorias" = lista + player/EPG lado a lado.

## #23 — Central multimídia

- Onboarding/Configurações → Tipo de aparelho → **Central multimídia (carro)**.
- Abrir o Waze/Maps com instrução de voz durante um canal: o volume abaixa e volta.
  Receber chamada via Bluetooth: pausa e retoma no fim.
- Botões ⏭/⏮ do volante (ou `adb shell input keyevent KEYCODE_MEDIA_NEXT`): trocam
  de canal no Ao Vivo.
- Knob: `adb shell input keyevent KEYCODE_NAVIGATE_NEXT` move o foco para baixo.
- Configurações → Tela → **Só áudio com o veículo em movimento** (pede localização).
  Teste no emulador: Extended Controls → Location → Routes, reproduzindo uma rota
  (o `geo fix` avulso não informa velocidade). Acima de 10 km/h aparece "Vídeo
  desligado com o veículo em movimento — o áudio continua".

## #24 — Favoritos

1. Favoritos → Canais: OK num canal abre o **miniplayer** no canto; OK de novo, tela cheia.
2. No player, ←/→ (ou CH+/CH−) percorrem **só os favoritos**.
3. Voltar ao Ao Vivo e abrir um canal: o zapping volta a ser pela categoria.

## #25 — Rolagem rápida

Segurar ↓ por 10 s numa categoria grande. Teste de estresse usado ✅ (160 teclas
disparadas em paralelo; antes: ANR e o app encerrado; agora: responsivo):
```bash
adb shell 'for i in $(seq 1 80); do input keyevent 20 & done; wait'
adb shell 'for i in $(seq 1 80); do input keyevent 19 & done; wait'
adb shell pidof com.iptv.app   # continua vivo
```

## #26 — Adulto em Assistidos recentemente

1. Assistir um canal de categoria adulta (com PIN).
2. Início → Canais recentes: ele **não** aparece (padrão).
3. Configurações → Controle parental → ligar **Mostrar conteúdo adulto em Assistidos
   recentemente** (pede o PIN). O canal aparece na hora; desligar esconde de novo.
4. A tela inicial da Android TV (canal de recomendações) nunca mostra adulto.

## #27 — Foco entre categorias e canais

Ao Vivo ✅: focar um canal no meio da grade, ← até a gaveta (cai na categoria
selecionada), navegar na gaveta, → volta exatamente ao canal anterior. Trocar de
categoria e entrar na grade: foco no primeiro canal visível.

## #28 — Atualização automática

✅ Build com versão antiga (`./gradlew :app:assembleDebug -PVERSION_NAME=3.0.0`): o
diálogo "Nova versão" aparece ao abrir, com **Atualizar agora** focado; baixa o APK e
abre o instalador (ou pede "instalar apps desconhecidos"). Configurações → Verificar
atualização agora: abre o mesmo diálogo, ou "Você já tem a versão mais recente".

## #29 — Anúncios e Premium

Ver [server/README.md](../server/README.md). Com servidor configurado:
- Gratuito: banner na Início e no detalhe do filme; pre-roll ao abrir conteúdo (no
  máximo um a cada `minIntervalMinutes`); post-roll ao fim do filme.
- Configurações → Premium: ID do aparelho (MAC); chave válida → "Premium ativado" e os
  banners somem na hora. A mesma chave em outro aparelho → "Licença já vinculada a
  outro dispositivo".
- Adulteração: `adb shell run-as com.iptv.app` e editar/copiar
  `shared_prefs/tartatv_license.xml` → no próximo início o app volta para gratuito.

## #30 — Identidade visual

✅ Splash, carregamento do catálogo e conexão do player com a marca "T" no anel
animado; ícone (inclusive adaptativo), banner da Android TV e marca no menu superior.
Assets gerados por `python branding/generate-assets.py`.
