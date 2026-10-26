# Ambiente de build do decoder FFmpeg (LGPL) do Media3 — ver build.sh.
FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      git curl unzip ca-certificates openjdk-17-jdk-headless make python3 pkg-config \
    && rm -rf /var/lib/apt/lists/*
ENV ANDROID_SDK_ROOT=/opt/android-sdk
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && cd /tmp \
    && curl -sSLo tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q tools.zip && mv cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest && rm tools.zip
ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin
RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager "ndk;26.1.10909125" "cmake;3.22.1" "platforms;android-34" "build-tools;34.0.0" > /dev/null
COPY build.sh /build.sh
ENTRYPOINT ["bash", "/build.sh"]
