# iOS

iOS plugins are Swift packages. A plugin repository's `Package.swift` and the Objective-C
registration wrapper in `apple/` are generated from `plugin.json` and committed, because Swift
Package Manager builds dependencies from source and runs no code generators.

```sh
tools/bin/plugin generate          # after changing plugin.json or updating tools/
tools/bin/plugin generate --check  # CI: fails when the committed files are stale
```

## The generated package

- One library product, `apple.product` (default: the plugin ID in PascalCase, for example `NgonLayer`).
- It depends on `MapLibrePluginApi`, the header-only C API from the plugin-enabled MapLibre
  iOS package (`versions.maplibreIosPackage` at exactly `versions.maplibreIos`), so plugin sources
  include `<mln/plugin/plugin_api.h>`. The plugin never links a MapLibre binary.
- The target spans the repository root but compiles only `sources` from `plugin.json` plus
  `apple/src/<Product>.mm`. At manifest evaluation it excludes every other top-level entry
  (`tools/`, `render-tests/`, `build/`, ...) and any non-source, non-header file inside the
  directories it keeps.
- `includeDirectories` become header search paths, and `MLN_PLUGIN_VERSION` is defined as the
  `plugin.json` version.
- Set `MAPLIBRE_IOS_PATH` to a local checkout of the MapLibre iOS package (for example one whose
  binary target points at a locally built XCFramework) to build against it instead.

## Registration API

`apple/include/<Product>/<Product>.h` declares, for example:

```objc
NS_SWIFT_NAME(NgonLayerPlugin)
@interface MLNNgonLayerPlugin : NSObject
@property (class, nonatomic, readonly) NSString *version;
+ (BOOL)registerPluginWithError:(NSError **)error NS_SWIFT_NAME(registerPlugin());
@end
```

It calls `registerFunction(&mln_plugin_register_v1, ...)`. Registering again succeeds. Errors
use the `MLNNgonLayerPluginErrorDomain` domain (`apple.errorDomain` string) with the
`mln_plugin_status` as code.

Applications add the plugin package and the `MapLibre` product of the MapLibre iOS package,
then register before creating a map that uses the plugin's layer types:

```swift
import MapLibre
import NgonLayer

try NgonLayerPlugin.registerPlugin()
```

## XCFramework

```sh
tools/bin/plugin ios-xcframework [--output DIR]   # default DIR: build/outputs
```

This writes `DIR/<Product>-<version>.xcframework.zip`, a static framework for iOS devices
(arm64) and simulators (arm64, x86_64), built from the generated package with `xcodebuild`.
The framework contains only the plugin; `mln_plugin_register_v1` stays undefined and resolves
against the application's `MapLibre` framework. The command checks this for every slice. Its
module map autolinks libc++ and Foundation, so applications written only in Objective-C or
Swift need no extra linker settings. Import it with `#import <NgonLayer/NgonLayer.h>` or
`import NgonLayer`.

## Demo app

```sh
tools/bin/plugin run-ios [--simulator UDID] [--screenshot FILE] [--timeout SECONDS] [--headless]
```

This generates `build/ios-demo/PluginDemo.xcodeproj`. It requires the `xcodeproj` Ruby gem,
which the command installs for the current user if needed. The app links the plugin from the
repository as a local package and the `MapLibre` product at the version the plugin uses. It
registers the plugin, bundles the directory of `demo.style` and shows that style at
`demo.camera`.

Without `--simulator` it uses a booted iPhone simulator, or else an iPhone of the newest iOS
runtime. It opens Simulator.app unless `--headless` is given or `CI` is set.

Without `--screenshot` it follows the app's console until interrupted. With `--screenshot` it
waits until the map has fully rendered (default timeout 60 seconds), saves the screenshot and
exits. It fails when registration or style loading fails.

## GitHub Actions

```yaml
- uses: actions/checkout@v4
  with:
    submodules: recursive
- uses: ./tools/actions/ios
  with:
    output: release-assets
    demo-screenshot: build/ios-demo.png   # optional smoke test
```

Use a macOS runner. `xcode-version` optionally selects `/Applications/Xcode_<version>.app`.
