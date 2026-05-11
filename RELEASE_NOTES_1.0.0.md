# TartaTV 1.0.0

Primeira versão pronta para instalação. Cliente Android TV / tablet / celular
para listas IPTV no padrão **Xtream Codes** (e também **M3U** como provedor
alternativo). Você fornece a URL do servidor, usuário e senha — **o TartaTV
não distribui, hospeda nem comercializa conteúdo**. Toda a reprodução depende
exclusivamente da lista que você inserir.

## Como instalar

1. Baixe o `tartatv-1.0.0.apk` desta página
2. **Android TV / TV Box**: copie o APK para um pendrive e abra com o
   gerenciador de arquivos da TV, ou instale via ADB:

   ```bash
   adb connect <ip-da-tv>:5555
   adb install tartatv-1.0.0.apk
   ```

3. **Celular ou tablet**: abra o APK pelo navegador ou app de arquivos. Pode
   ser necessário liberar **Fontes desconhecidas** nas configurações de
   segurança.

Compatível com **Android 6.0 (API 23)** ou superior. Para conteúdo 4K/HDR é
preciso decoder HEVC em hardware (TVs modernas e celulares topo de linha
têm; emuladores normalmente não).

## Funcionalidades

### Catálogo

- **Login Xtream Codes** com URL, usuário e senha; também aceita listas
  **M3U** via URL como provedor alternativo
- **Canais ao vivo** organizados por categoria, com **EPG XMLTV**: programa
  atual com barra de progresso e programação dos próximos dias
- **Filmes (VOD)** com pôster, sinopse, elenco, direção, gênero, avaliação,
  duração e retomada de onde parou
- **Séries** com temporadas, episódios, autoplay do próximo, marcação de
  assistidos e progresso por episódio
- **Continuar assistindo** na tela inicial, consolidando filmes e
  episódios em andamento
- **Recomendações** "porque você viu X" — calculadas localmente, sem
  telemetria, com base em gênero, elenco, categoria e década
- **Watchlist** ("Lista para assistir") separada de Favoritos
- **Favoritos** para canais, filmes e séries com filtro por tipo

### Busca

- **Busca global** por nome, gênero, elenco e sinopse — usando índice
  **FTS4** local, responde em menos de 200 ms para catálogos de até 20 mil
  itens
- **Histórico de busca** persistente com chips de termos recentes
- **Filtro local dentro de uma categoria** (sem nova consulta à rede)
- **Filtros avançados** em Filmes e Séries: intervalo de ano, avaliação
  mínima e multi-seleção de gêneros derivados do catálogo

### Reprodução

- **ExoPlayer** com suporte a HLS, DASH, MP4, TS e HEVC (quando o
  dispositivo suporta)
- **Track picker**: troca de áudio, legenda e qualidade durante a
  reprodução
- **Picture-in-Picture** ao sair do app durante a reprodução (Android 8+)
- **Mini-player persistente** ao voltar do player para navegar — uma
  faixa fixa no rodapé com play/pause, título e botão fechar
- **Time-shift** em canais com `tv_archive`: voltar 30 min, 1 h ou 2 h
- **Erros amigáveis** no player: codec não suportado, formato 4K em
  device incompatível, falha de rede ou recusa do servidor são mostrados
  como mensagens compreensíveis, não como tela preta

### Multi-perfil

- Vários servidores Xtream salvos com nome próprio e troca rápida
- **PIN parental** opcional por perfil, com criação obrigatória no
  primeiro acesso a conteúdo adulto
- **Multi-perfil v2**: favoritos, watchlist e progresso ficam isolados
  por perfil — cada um vê só os próprios dados
- **Perfil Kids**: esconde Configurações, Busca e Watchlist; mostra
  apenas as categorias que o responsável liberou

### Configuração

- **Idioma do app**: seletor em 7 opções (Sistema, Português, English,
  Español, Italiano, Français, Deutsch) na primeira execução e em
  Configurações
- **Tema dark** otimizado para TV 55" e visualização à distância
- **Edge-to-edge** com respeito ao notch / Dynamic Island / gesture bar
  em celulares modernos
- **Pull-to-refresh** nas categorias quando rodando em celular
- **Cache offline** do catálogo (TTL 6 h, atualizado em background) e
  cache de detalhe (TTL 24 h)
- **Atualização automática**: o app detecta novos Releases no GitHub e
  oferece download com changelog completo no diálogo

### Notificações

- 4 canais: catálogo atualizado, novos episódios em séries favoritas,
  lembrete "continuar assistindo" e erros do servidor
- Permissão `POST_NOTIFICATIONS` solicitada nas configurações no
  Android 13+

### Diagnóstico

- Log local de erros (máx. 256 KB, rotativo) em **Configurações → Sobre
  → Diagnóstico**, com botões **Copiar** e **Compartilhar**
- **Opt-in estrito**: nada sai do dispositivo automaticamente — você
  decide quando e para onde enviar

## Privacidade

- **Zero telemetria** — o app não envia dados de uso, crashes, IPs ou
  identificação para o desenvolvedor ou terceiros
- **Credenciais criptografadas**: URL, usuário, senha e PIN ficam em
  `EncryptedSharedPreferences` (Tink AES-256), em área privada do app
- **Sem backup automático em nuvem**: `allowBackup=false` e regras
  explícitas de extração
- **Permissões mínimas**: `INTERNET`, `ACCESS_NETWORK_STATE`,
  `WAKE_LOCK`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`
- **Tráfego de rede**: requisições saem direto do seu dispositivo para
  o servidor IPTV cuja URL você cadastrou; o desenvolvedor não tem
  visibilidade
- **`network_security_config`**: cleartext permitido para o host
  Xtream do seu provedor, mas bloqueado em cleartext para endpoints
  próprios de atualização (GitHub, Google) — defesa contra
  interceptação de DNS

## Idiomas

- Português (Brasil) — padrão
- English
- Español
- Italiano
- Français
- Deutsch

## Suporte

Reporte bugs e sugestões em
https://github.com/rodolfot/iptv/issues

Diagnóstico local: **Config → Sobre → Diagnóstico → Copiar / Compartilhar**.

---

A marca e o nome **TartaTV** são propriedade de seu titular. Este
aplicativo usa diversas bibliotecas open source — a lista completa
está em **Config → Sobre**.
