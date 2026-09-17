# 开发环境

- JDK 17
- Android SDK Platform 36、Build-Tools 36.0.0、Platform-Tools
- 仓库自带 Gradle 8.13 Wrapper
- Android Studio 可选；真机或 Android 36 模拟器用于设备测试

将 SDK 路径写入本机 `local.properties` 的 `sdk.dir`，或设置 `ANDROID_HOME`。
`local.properties`、构建产物和签名文件不进入 Git。

```sh
./gradlew test assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

设备测试会创建并清理测试课表，请仅在专用模拟器运行：

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

签名与 GitHub Release 配置见 [发布维护](docs/RELEASING.md)。
