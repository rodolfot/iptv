# IPTV Android TV

App Android TV (instalável via APK) para listas IPTV no padrão **Xtream Codes**: canais ao vivo, filmes (VOD) e séries, com favoritos, bloqueio parental por PIN, autoplay do próximo episódio e ordenação configurável. UI calibrada para TV 55".

## Requisitos
- JDK 17
- Android SDK (compileSdk 34, minSdk 23)
- Android Studio (Hedgehog ou superior) — recomendado

## Build local

```bash
# (opcional) defina ANDROID_HOME e local.properties apontando para o SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties

# APK debug
./gradlew :app:assembleDebug

# APK release (assinado com debug keystore por padrão)
./gradlew :app:assembleRelease
```

APKs ficam em `app/build/outputs/apk/{debug,release}/`.

## Instalar na TV
```bash
adb connect <ip-da-tv>:5555
adb install app/build/outputs/apk/release/app-release.apk
```
Ou copie o APK para um pendrive e instale via gerenciador de arquivos da TV.

## Build via GitHub Actions
O workflow `Build APK` (`.github/workflows/build-apk.yml`) compila debug e release a cada push e disponibiliza os APKs como artifacts.

## Login
Ao abrir o app:
- **URL do servidor**: ex. `http://bscx.one`
- **Usuário** e **Senha** do Xtream Codes

## Funcionalidades
- Canais ao vivo agrupados por categoria (com mais de 19k canais)
- Filmes (VOD) e séries com posters
- Favoritos (canais, filmes, séries)
- **Bloqueio parental** com senha padrão `3258` para canais e filmes adultos (alterável em Configurações)
- **Autoplay do próximo episódio** em séries (toda a temporada na fila, salta para próxima ao acabar)
- **Ordenação**: nome, data de lançamento, data de adição, ID, avaliação
- Player baseado em ExoPlayer (HLS, DASH, MP4, TS)
- UI Compose for TV otimizada para D-pad e tela 55"

## Stack
Kotlin · Jetpack Compose for TV · Media3 ExoPlayer · Retrofit/OkHttp/Moshi · Room · DataStore · Hilt · Coil
