# Android Environment

Verified on 2026-09-16 (Arch Linux, x86_64).

## Installed

- JDK 17.0.20.1: `/usr/lib/jvm/java-17-openjdk`
- Android Studio: `~/.local/opt/android-studio`, command `android-studio`
- SDK: `~/Android/Sdk`
- Command-line tools 22.0
- SDK Platforms 35 and 36; the Studio wizard also installed 37.0
- Build-Tools 35.0.0 and 36.0.0
- Platform-Tools 37.0.1
- Emulator 37.1.11, KVM acceleration usable
- Android 36 AOSP x86_64 system image, revision 2
- AVD: `WakeUpPure_API_36`
- System USB rules: `android-udev`

Java and Android paths are configured in `~/.zshrc`. Open a new terminal
to use them. No additional global Gradle installation is required: the app
will use a project-specific Gradle Wrapper. An existing cached Gradle 8.3
distribution was verified to run on JDK 17, but is not a selected app build version.

## Verification

- SDK package inventory: no duplicate-location or corrupted-package warnings.
- Emulator boot: `sys.boot_completed=1`.
- Temporary Java test APK compiled with SDK 36, dexed, aligned and signed.
- APK signature verification passed (v2 and v3).
- ADB installation succeeded.
- Activity launch returned `Status: ok`; activity was resumed.
- UI Automator confirmed the text `Android environment verified` on screen.

The temporary test files are in `~/.cache/wakeup-setup/smoke`.
This verifies the native Android toolchain, not a Kotlin/Compose Gradle app
build. App dependencies and the project-specific build remain development work.

## Start the Test Device

```sh
emulator -avd WakeUpPure_API_36 -gpu swiftshader_indirect
```

Duplicate and incomplete SDK directories were moved to
`~/.cache/wakeup-setup/sdk-backups` instead of deleted.
Official downloaded archives are cached in `~/.cache/wakeup-setup`.
