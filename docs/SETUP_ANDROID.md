# Android CLI Setup (No Android Studio)

`MAKO` uses the same CLI-only Android workflow as `unagi`: Gradle wrapper, Android SDK Command-line Tools, and no Android Studio dependency.

## Quick path

Run:

  scripts/setup-android-sdk

The script downloads the Android command-line tools, installs the required SDK packages, accepts licenses, and prints the environment variables to export.

If you need a different tools archive, set `ANDROID_SDK_TOOLS_URL` before running it.

## Manual fallback

1. Install JDK 17.
2. Download Android SDK Command-line Tools from Android Developers.
3. Unzip into:

  ANDROID_SDK_ROOT/
    cmdline-tools/
      latest/
        bin/
        lib/

4. Install packages:

  sdkmanager "platform-tools" "build-tools;35.0.0" "platforms;android-35"

5. Accept licenses:

  yes | sdkmanager --licenses

6. Export:

  export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
  export ANDROID_HOME="$ANDROID_SDK_ROOT"
  export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

## Doctor checklist

- `java -version` reports JDK 17
- `sdkmanager --version` works
- `adb version` works
- Installed SDK packages include `platform-tools`, `build-tools;35.0.0`, and `platforms;android-35`

## Build and stage

- `./gradlew assembleDebug`
- `./gradlew installDebug`
- `scripts/stage-apk`
- `adb devices`
