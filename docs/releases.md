# Releases

A release publishes the version in `plugin.json`:

1. Set `version` in `plugin.json` and run `tools/bin/plugin generate` (the Swift package embeds the version).
2. Commit and push to the default branch.
3. Run the **Release** workflow (Actions tab, or `gh workflow run release.yml`).

The workflow builds the Android AAR on Linux and the iOS XCFramework on macOS. Then it creates a GitHub
release and a tag named after the version (for example `0.1.0`) at the built commit, with:

- `<artifactId>-<version>.aar`
- `<Product>-<version>.xcframework.zip`
- a `.sha256` file for each

It refuses to overwrite an existing release. Versions with a pre-release suffix, such as `0.2.0-pre1`, are
marked as pre-releases.

## Using a released plugin in an app

Apps must use the plugin-enabled SDK versions from the tools' [`versions.json`](../versions.json) that the
plugin was built with.

### Android

Download the AAR from the release and add it next to the SDK:

```kotlin
dependencies {
    implementation("org.maplibre.gl:android-sdk-opengl:<versions.maplibreAndroid>") // or android-sdk-vulkan
    implementation(files("libs/square-layer-0.1.0.aar"))
}
```

Register after initializing MapLibre and before loading a style:

```kotlin
MapLibre.getInstance(context)
SquareLayerPlugin.register()
```

### iOS

Add the plugin repository as a Swift package at the release tag, together with the plugin-enabled MapLibre:

```swift
dependencies: [
    .package(url: "https://github.com/louwers/maplibre-ios-with-plugin-api", exact: "<versions.maplibreIos>"),
    .package(url: "https://github.com/<owner>/<plugin-repository>", exact: "0.1.0"),
],
targets: [
    .target(name: "App", dependencies: [
        .product(name: "MapLibre", package: "maplibre-ios-with-plugin-api"),
        .product(name: "SquareLayer", package: "<plugin-repository>"),
    ]),
]
```

In Xcode, add both packages with File > Add Package Dependencies. Alternatively, link the release's static
`<Product>.xcframework` and add only the `MapLibre` package. The XCFramework contains only the plugin; it
resolves `mln_plugin_register_v1` from the MapLibre framework, and its module map links libc++ and Foundation.

```objc
#import <MapLibre/MapLibre.h>
#import <SquareLayer/SquareLayer.h>

NSError *error;
if (![MLNSquareLayerPlugin registerPluginWithError:&error]) { /* handle error */ }
```
