# DEVELOPMENT

Guia para contribuir com o TartaTV.

## Setup

Pré-requisitos:

- **JDK 17** (Android Studio Ladybug+ traz embutido como JBR 21; ambos servem)
- **Android SDK** com `compileSdk 35` instalado
- **Android Studio Ladybug (2024.2)** ou superior — recomendado

Versões fixadas no projeto (ver `gradle/libs.versions.toml` e `build.gradle.kts`):

- AGP 8.7.3 · Gradle 8.9 · Kotlin 1.9.24 · Compose Compiler 1.5.14

```bash
git clone https://github.com/<owner>/iptv.git
cd iptv
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
```

APK em `app/build/outputs/apk/debug/`.

## Run

Conecte um device/emulador (`adb devices` deve listar):

```bash
./gradlew :app:installDebug
adb shell am start -n com.iptv.app/.MainActivity
```

Para Android TV emulator, use AVD com system image **Android TV**, não Android padrão.

## Testes

```bash
./gradlew :app:testDebugUnitTest
```

Testes unitários em `app/src/test/java/com/iptv/app/`. Cobertura inicial: parsing de DTOs Xtream e parser XMLTV.

Não há testes instrumentados (UI/Compose) ainda — bem-vindos.

## Estilo de código

- **Kotlin**: sem ktlint formal hoje; siga o estilo do código existente (4 espaços, imports organizados).
- **Compose**: prefira recompor por estado, evite `remember` sem chave de invalidação quando o valor depender de input.
- **Imports**: nada de `*` em pacotes internos do app.
- **Comentários**: só explique o **por quê** quando não for óbvio. Não comente o quê.

## Convenções de UI

- **Form factor**: `rememberTvDim()` retorna `FormFactor.Phone | Tablet | Tv`. Use isso para escolher dimensões e variantes de componentes.
- **Componentes**: NÃO use `androidx.tv.material3.Button` ou `Card` direto — use `TouchableButton` / `TouchableCard` em `ui/common/`. Eles fazem o pareamento certo entre TV (D-pad) e Phone (touch).
- **Safe area**: telas top-level fora de `HomeScreen` precisam de `safeDrawingPadding()`. `HomeScreen` já aplica nos lados horizontais e bottom; o `TopBar` no Phone aplica `statusBars` no topo.
- **Strings**: tudo em `res/values/strings.xml` (pt-BR padrão), com paridade obrigatória em `values-en/` e `values-es/`. Verifique:

```bash
diff <(grep -oE 'name="[^"]+"' app/src/main/res/values/strings.xml | sort) \
     <(grep -oE 'name="[^"]+"' app/src/main/res/values-en/strings.xml | sort)
```

## Banco de dados (Room)

`AppDatabase` em `data/db/AppDatabase.kt`. Para alterar schema:

1. Bump `version`
2. Crie um `Migration(N → N+1)` em `data/db/Migrations.kt`
3. Adicione na lista de `addMigrations(...)` no `RoomModule`
4. **Nunca** use `fallbackToDestructiveMigration()` — favoritos e progresso são preciosos

## Fluxo de release

1. Atualize `CHANGELOG.md` com a versão
2. Tag: `git tag v1.X.Y && git push --tags`
3. GitHub Actions builda APK release assinado e cria release com APK anexado
4. App em produção detecta a nova tag pelo `UpdateChecker` e mostra prompt de atualização

Versionamento automático: `versionCode` = `git rev-list --count HEAD`, `versionName` = `git describe --tags`. Override com `-PVERSION_CODE=N -PVERSION_NAME=X.Y.Z`.

## Ferramentas úteis

- `./gradlew dependencies` — árvore de deps
- `./gradlew :app:lintDebug` — Android lint
- `adb logcat -s TartaTV ExoPlayerImpl` — filtrar logs relevantes
- `adb shell run-as com.iptv.app cat databases/app.db` — inspecionar SQLite (debug only)

## Onde adicionar coisas

| Tipo | Lugar |
|------|-------|
| Novo endpoint Xtream | `data/api/XtreamApi.kt` + DTO + método em `XtreamRepository` |
| Nova entidade Room | `data/db/Entities.kt` + DAO em `data/db/Daos.kt` + bump versão + migração |
| Nova tela | `ui/<area>/<TelaScreen>.kt` + ViewModel `@HiltViewModel` no mesmo arquivo |
| Novo canal de notificação | `notify/Notifications.kt` (constante + `ensureChannels`) + chamada em algum Worker |
| Novo Worker | `work/<NomeWorker>.kt` + `@HiltWorker` + `schedule()` chamado no `IptvApp.onCreate` |
| Nova string | `res/values/strings.xml` + `values-en/` + `values-es/` (sempre os três) |

## Hilt cheat sheet

- `@HiltAndroidApp` no `IptvApp`
- `@AndroidEntryPoint` em Activities
- `@HiltViewModel` em ViewModels (com `@Inject constructor(...)`)
- `@HiltWorker` em CoroutineWorkers (com `@AssistedInject` e `@Assisted` para `Context`/`WorkerParameters`)
- Módulos em `di/` provêem singletons (Room, Retrofit, OkHttp, etc.)

## Privacidade & política

- **Não** adicione SDKs de analytics/telemetria/crash reporting de terceiros (Firebase, Sentry, Crashlytics, etc.) — viola a política do app
- **Não** adicione permissões além das listadas no `AndroidManifest.xml` sem discussão
- Crashes ficam apenas no log local (`diag/CrashLog`) e nunca saem do device automaticamente

## CI

`.github/workflows/release.yml` builda em release assinado com tag `v*`. Secrets necessários:

- `RELEASE_KEYSTORE_BASE64` — `base64 -w 0 tartatv.jks`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Em `gradle.properties` (commitado), defina `UPDATE_REPO_OWNER` e `UPDATE_REPO_NAME` para o auto-update apontar pro repositório correto.
