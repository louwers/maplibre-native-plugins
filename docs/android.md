# Android

The Android build turns the plugin repository into an Android library (AAR) configured entirely by `plugin.json`. A plugin repository contains no Android-specific code.

## Plugin repository boilerplate

Copy these from `tools/templates/android/` into the repository root. They are the same for every plugin:

- `settings.gradle.kts` includes the convention plugins from `tools/gradle` and applies `org.maplibre.native-plugin.settings`.
- `build.gradle.kts` applies `org.maplibre.native-plugin`.
- Add the entries in `gitignore` to the repository's `.gitignore`.

There is no Gradle wrapper in the plugin repository. The commands below use `tools/gradle/gradlew`, so updating the tools also updates Gradle and the Android Gradle Plugin.

## What the build does

- **Native library** `lib<android.libraryName>.so` for all four ABIs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`), built with CMake (`tools/android/jni/CMakeLists.txt`).
  - It compiles `sources`, with `includeDirectories` and `MLN_PLUGIN_VERSION` defined.
  - It links the C++ runtime statically (`c++_static`) and keeps it private. The library exports only `JNI_OnLoad` and the register function.
  - The plugin API header comes from the header-only Prefab module of the plugin-enabled MapLibre SDK (`versions.maplibreAndroid`). The plugin never links MapLibre.
- **Generated Java class** `<android.qualifiedClassName>` with `ID`, `VERSION` and `register()`.
  - `register()` loads MapLibre's native library and then the plugin's.
  - The generic JNI glue (`tools/android/jni/plugin_jni.cpp`) binds the class in `JNI_OnLoad`, finds `mln_plugin_register_v1` in the already loaded `libmaplibre-opengl.so` or `libmaplibre.so`, and calls `registerFunction`.
  - `ALREADY_REGISTERED` counts as success; any other failure throws `IllegalStateException`.
- **Consumer R8 rule** that keeps the registration class name, which the JNI glue looks up.
- **Maven publication** `release` with `android.groupId:android.artifactId:version`, for example `tools/gradle/gradlew -p . publishToMavenLocal`.

The AAR depends on MapLibre only at compile time. Applications add `org.maplibre.gl:android-sdk-opengl` or `android-sdk-vulkan` in a plugin-enabled version, then:

```java
MapLibre.getInstance(context);
NgonLayerPlugin.register(); // before loading a style that uses the plugin's layers
```

## Commands

```sh
tools/bin/plugin android-aar [--output DIR] [--abis arm64-v8a,x86_64]
```

Builds `DIR/<artifactId>-<version>.aar`. The default output is `build/outputs`, and all ABIs are built unless `--abis` is given.

```sh
tools/bin/plugin run-android [--renderer opengl|vulkan] [--screenshot FILE] [--timeout SECONDS]
```

- Builds the generic demo app (`tools/android/demo`, Gradle project `:demo`) for the connected device's ABI.
- Installs it as `org.maplibre.plugins.demo.<id>.<renderer>` and launches it.
- The app registers the plugin, loads `demo.style` and applies `demo.camera`.
- The command waits until the map is idle, prints the app's log, and optionally saves a screenshot.
- It fails if registration or style loading fails, or on timeout.
- It needs one connected device or running emulator (or `ANDROID_SERIAL`).

The Android SDK is found through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `local.properties`, or the default SDK location on macOS and Linux.

## GitHub Actions

```yaml
- uses: actions/checkout@v7
  with:
    submodules: recursive
- uses: ./tools/actions/android
  with:
    output: release-assets
```

The action sets up JDK 17, the Android SDK, the NDK from `versions.json` and Gradle caching, then runs `android-aar`. Its `aar` output is the path of the built file.
