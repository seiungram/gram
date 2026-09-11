FROM gradle:8.14.5-jdk17-jammy

ENV ANDROID_CMDLINE_TOOLS_VERSION=15859902
ENV ANDROID_SDK_URL=https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip

ENV ANDROID_HOME=/usr/local/android-sdk-linux

ENV ANDROID_API_LEVEL=android-36
ENV ANDROID_VERSION=36
ENV ANDROID_BUILD_TOOLS_VERSION=36.0.0

ENV ANDROID_NDK_VERSION=27.2.12479018
ENV ANDROID_NDK_HOME=${ANDROID_HOME}/ndk/${ANDROID_NDK_VERSION}

ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools
ENV PATH=${PATH}:${ANDROID_NDK_HOME}
ENV PATH=${PATH}:${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" /home/gradle/.android && \
    cd /tmp && \
    curl -fL "${ANDROID_SDK_URL}" -o commandlinetools.zip && \
    unzip commandlinetools.zip && \
    mv cmdline-tools "${ANDROID_HOME}/cmdline-tools/latest" && \
    rm commandlinetools.zip

# ccache is referenced by CMAKE_C/CXX_COMPILER_LAUNCHER in TMessagesProj.
RUN apt-get update && apt-get install -y ccache && rm -rf /var/lib/apt/lists/*

RUN yes | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses
RUN sdkmanager \
    --sdk_root="${ANDROID_HOME}" \
    "build-tools;36.0.0" \
    "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" \
    "platforms;android-${ANDROID_VERSION}" \
    "platform-tools" \
    "ndk;${ANDROID_NDK_VERSION}" \
    "cmake;3.22.1"

# CMD mkdir -p /home/source/TMessagesProj/build/outputs/apk && \
#     mkdir -p /home/gradle/TMessagesProj/build/outputs/bundle && \
#     mkdir -p /home/source/TMessagesProj/build/outputs/native-debug-symbols && \
#     cp -R /home/source/. /home/gradle && \
#     cd /home/gradle && \
#     gradle --parallel \
#         :TMessagesProj_App:bundleBundleAfat_SDK23Release \
#         :TMessagesProj_App:bundleBundleAfatRelease \
#         :TMessagesProj_AppStandalone:assembleAfatStandalone \
#         :TMessagesProj_App:assembleAfatRelease \
#         :TMessagesProj_AppHuawei:assembleAfatRelease --stacktrace && \
#     cp -R /home/gradle/TMessagesProj_App/build/outputs/apk/. /home/source/TMessagesProj/build/outputs/apk && \
#     cp -R /home/gradle/TMessagesProj_AppHuawei/build/outputs/apk/. /home/source/TMessagesProj/build/outputs/apk && \
#     cp -R /home/gradle/TMessagesProj_AppStandalone/build/outputs/apk/. /home/source/TMessagesProj/build/outputs/apk && \
#     cp -R /home/gradle/TMessagesProj_App/build/outputs/bundle/. /home/source/TMessagesProj/build/outputs/bundle

# gradle-daemon-jvm.properties pins a local-only JDK, so it is deleted here:
# the image provides JDK 17 and Gradle 8.11 cannot provision another one.
CMD mkdir -p /home/source/TMessagesProj/build/outputs/apk && \
    mkdir -p /home/gradle/TMessagesProj/build/outputs/bundle && \
    mkdir -p /home/source/TMessagesProj/build/outputs/native-debug-symbols && \
    cp -R /home/source/. /home/gradle && \
    cd /home/gradle && \
    rm -f gradle/gradle-daemon-jvm.properties && \
    gradle --parallel \
        :TMessagesProj_AppStandalone:assembleAfatStandalone --stacktrace && \
    cp -R /home/gradle/TMessagesProj_AppStandalone/build/outputs/apk/. /home/source/TMessagesProj/build/outputs/apk