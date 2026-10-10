#!/usr/bin/env bash
# YooFree - ambiente de build local (source este arquivo)
export PROJ="$HOME/YooFree"
export JAVA_HOME="$PROJ/.toolchain/jdk"
export ANDROID_HOME="$PROJ/.toolchain/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PROJ/.toolchain/gradle/bin:$PATH"
