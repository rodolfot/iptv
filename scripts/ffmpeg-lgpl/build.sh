#!/usr/bin/env bash
# Gera app/libs/media3-decoder-ffmpeg-<media3>-lgpl.aar (#18).
#
# FFmpeg compilado SEM --enable-gpl/--enable-nonfree (licença LGPL 2.1+), só
# com os decoders de áudio que TVs/celulares costumam não ter: DTS (dca, inclui
# DTS-HD), Dolby TrueHD/MLP e AC-3/E-AC-3 (fallback onde não há decoder Dolby).
# Ligado dinamicamente via JNI dentro do AAR (libffmpegJNI.so), o que atende à
# LGPL: o usuário pode substituir a biblioteca. Fontes: FFmpeg release/6.0 e
# androidx/media no tag abaixo.
#
# Uso (na raiz do repositório):
#   docker build -t tartatv-ffmpeg scripts/ffmpeg-lgpl
#   docker run --rm -v "$PWD/app/libs:/out" tartatv-ffmpeg
set -euo pipefail
MEDIA3_TAG="${MEDIA3_TAG:-1.4.0}"
FFMPEG_BRANCH="${FFMPEG_BRANCH:-release/6.0}"
# API 21 = minSdk do módulo decoder_ffmpeg (o JNI linka contra o sysroot 21; o
# FFmpeg precisa da mesma API — com 23 o símbolo `stderr` não resolve). Roda
# normalmente no app (minSdk 23).
ANDROID_ABI=21
DECODERS=(dca truehd mlp ac3 eac3)
NDK_PATH="$ANDROID_SDK_ROOT/ndk/26.1.10909125"

cd /tmp
git clone -q --depth 1 --branch "$MEDIA3_TAG" https://github.com/androidx/media.git media
FFMPEG_MODULE_PATH=/tmp/media/libraries/decoder_ffmpeg/src/main
git clone -q --depth 1 --branch "$FFMPEG_BRANCH" https://git.ffmpeg.org/ffmpeg.git ffmpeg
FFMPEG_COMMIT="$(git -C ffmpeg rev-parse HEAD)"
ln -s /tmp/ffmpeg "$FFMPEG_MODULE_PATH/jni/ffmpeg"
(cd "$FFMPEG_MODULE_PATH/jni" && ./build_ffmpeg.sh "$FFMPEG_MODULE_PATH" "$NDK_PATH" linux-x86_64 "$ANDROID_ABI" "${DECODERS[@]}")
grep -q "CONFIG_GPL 0" /tmp/ffmpeg/config.h || { echo "FFmpeg saiu com GPL habilitada"; exit 1; }

cd /tmp/media
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
./gradlew -q :lib-decoder-ffmpeg:assembleRelease
AAR="$(ls libraries/decoder_ffmpeg/buildout/outputs/aar/*release*.aar | head -1)"
cp "$AAR" "/out/media3-decoder-ffmpeg-${MEDIA3_TAG}-lgpl.aar"
cat > /out/media3-decoder-ffmpeg-NOTICE.txt <<NOTICE
media3-decoder-ffmpeg-${MEDIA3_TAG}-lgpl.aar

- Código do módulo: androidx/media ${MEDIA3_TAG} (Apache License 2.0)
  https://github.com/androidx/media/tree/${MEDIA3_TAG}/libraries/decoder_ffmpeg
- FFmpeg ${FFMPEG_BRANCH} @ ${FFMPEG_COMMIT} (GNU LGPL 2.1 ou posterior)
  https://git.ffmpeg.org/gitweb/ffmpeg.git/commit/${FFMPEG_COMMIT}
  Configurado sem --enable-gpl e sem --enable-nonfree; decoders: ${DECODERS[*]}
- Para recompilar/substituir a biblioteca: scripts/ffmpeg-lgpl/build.sh
NOTICE
echo "ok: $(ls -la /out)"
