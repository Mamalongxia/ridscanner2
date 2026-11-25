#!/usr/bin/env sh
# Gradle wrapper 启动脚本（云端 Actions 用独立安装的 gradle，不依赖此脚本）。
# 本机若缺少 gradle-wrapper.jar，可用 Android Studio 打开自动补全，
# 或装好 Gradle 8.9 后执行 `gradle wrapper` 生成 jar。
DIR=$(cd "$(dirname "$0")" && pwd)
exec java -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
