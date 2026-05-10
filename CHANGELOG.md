# Changelog

Todas as mudanças notáveis deste projeto.

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Unreleased]

### Added

- Multi-perfil: vários servidores Xtream salvos com nome próprio e PIN parental opcional por perfil. Tela "Gerenciar perfis" no Settings.
- Notificações: 4 canais (catálogo, novos episódios em séries favoritas, lembrete "continuar assistindo", erros do servidor). Permissão `POST_NOTIFICATIONS` solicitada via Settings no Android 13+.
- `ResumeReminderWorker`: ping semanal quando há progresso dormente >3 dias.
- Espanhol (`values-es/`) com paridade total.
- Strings de acessibilidade (`a11y_*`) em pt/en/es; `Lock` overlay agora narrável pelo TalkBack.
- Botão de busca pelo notch: header do TartaTV no topo das tabs no Phone, sempre dentro da safe area.

### Changed

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

## [Versões anteriores]

Ver `git log --oneline` para histórico anterior. Releases marcadas como `v*` no GitHub Releases têm APK assinado anexado.
