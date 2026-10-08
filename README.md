# MapLibre Native plugins

Build, run, test and release [MapLibre Native](https://github.com/maplibre/maplibre-native) plugins for
Android and iOS from a single plugin repository.

A plugin is a C/C++ library that registers new style layer types through MapLibre Native's
[C plugin API](https://github.com/maplibre/maplibre-native/blob/main/include/mln/plugin/plugin_api.h).
These tools take care of everything around it: the Android library and its Java registration class, the
Swift package and its Objective-C registration class, the release artifacts, demo apps and render tests.

<table>
  <tr>
    <td width="50%" align="center"><img src="https://maplibre.org/_astro/waves.LnK_Z0mD.webp" alt="Waves plugin demo" width="100%"></td>
    <td width="50%" align="center"><img src="https://maplibre.org/_astro/polygon-layer.Cc0SPOVt.webp" alt="Polygon layer plugin demo" width="100%"></td>
  </tr>
  <tr>
    <td width="50%" align="center"><img src="https://maplibre.org/_astro/location-indicator.DiIRPZPt.png" alt="Location indicator plugin demo" width="100%"></td>
    <td width="50%" align="center"><img src="https://maplibre.org/_astro/eifel-tower._ZAhKWVJ.png" alt="Eiffel Tower plugin demo" width="100%"></td>
  </tr>
  <tr>
    <td width="50%" align="center"><img src="https://maplibre.org/_astro/fireworks.gDOwa5uy.webp" alt="Fireworks plugin demo" width="100%"></td>
    <td width="50%" align="center"><img src="https://maplibre.org/_astro/flow-direction.XQfEVhER.gif" alt="Flow direction plugin demo" width="100%"></td>
  </tr>
  <tr>
    <td width="50%" align="center"><img src="https://maplibre.org/_astro/snowflakes.DROlU6WQ.webp" alt="Snowflakes plugin demo" width="100%"></td>
    <td width="50%" align="center"><img src="https://maplibre.org/_astro/weather.DOLRWOZz.gif" alt="Weather plugin demo" width="100%"></td>
  </tr>
</table>

<sub>Examples of plugins that the plugin API enables: waves, an n-gon layer, custom location indicators, 3D models,
fireworks, flow direction, snow and animated symbols. From the
[MapLibre newsletter of September 2026](https://maplibre.org/news/2026-10-01-maplibre-newsletter-september-2026/);
thanks to Sargun Vohra for sharing these experiments. Map data from OpenStreetMap and styles based on OpenMapTiles.</sub>

## Plugins

| Plugin | Description |
| --- | --- |
| [maplibre-native-ngon-layer-plugin](https://github.com/louwers/maplibre-native-ngon-layer-plugin) | Regular polygons (n-gons) at point features, with data-driven corners, size, rotation, stroke, blur and opacity. A complete example of a plugin built with these tools. |
| [maplibre-native-plugin-template](https://github.com/louwers/maplibre-native-plugin-template) | Template with a minimal `square` layer. Start your plugin here. |

> [!NOTE]
> The plugin API is experimental. It is available in plugin-enabled pre-releases of the SDKs only:
> MapLibre Android `org.maplibre.gl:android-sdk-{opengl,vulkan}` (see [`versions.json`](versions.json)) and
> MapLibre iOS from [`maplibre-ios-with-plugin-api`](https://github.com/louwers/maplibre-ios-with-plugin-api).
> Its ABI can change between pre-releases, so plugins and apps must use the SDK versions pinned here.

## Create a plugin

1. **Create your repository from the template.** On
   [maplibre-native-plugin-template](https://github.com/louwers/maplibre-native-plugin-template), select
   **Use this template > Create a new repository**, or fork it. With the GitHub CLI:

   ```sh
   gh repo create my-layer-plugin --public --clone \
     --template louwers/maplibre-native-plugin-template
   cd my-layer-plugin
   git submodule update --init   # check out the tools at tools/
   ```

   The template contains a minimal `square` layer, this repository as the `tools` submodule, and the CI and
   release workflows.

2. **Make it yours.** Edit `plugin.json`: set `id`, `displayName`, `description` and `version`, rename the
   header, sources and `registerFunction`, and point `demo.style` at a style that uses your layer type. Then
   regenerate the Swift package and the Objective-C wrapper:

   ```sh
   tools/bin/plugin sync
   tools/bin/plugin config   # check the derived class and package names
   ```

3. **Write the layer.** The registration function describes your layer types (properties, shaders for
   OpenGL, Vulkan and Metal, and the layout callbacks that turn features into vertices) and calls
   `register_plugin`. The template's `src/square_layer.cpp` is a small commented starting point; the
   [n-gon layer](https://github.com/louwers/maplibre-native-ngon-layer-plugin) shows data-driven properties.

4. **Run it in the demo apps.** They load `demo.style` with your plugin registered:

   ```sh
   tools/bin/plugin run-android --renderer vulkan   # needs a device or a running emulator
   tools/bin/plugin run-ios                          # boots an iOS simulator
   ```

5. **Add render tests.** Put fixtures under `render-tests/<suite>/<case>/style.json` and list them in
   `render-tests/manifest.json`. The runner builds MapLibre Native from source (the first run fetches it),
   records expected images with `--update default`, and compares against them otherwise. Review the
   recorded images before committing them. See [render tests](docs/render-tests.md), including how to take
   the OpenGL and Vulkan images from CI.

   ```sh
   tools/bin/plugin render-tests -- --update default   # record expected.png files
   tools/bin/plugin render-tests                       # compare (Metal on macOS, OpenGL on Linux)
   ```

6. **Push.** CI builds the Android library and the iOS XCFramework, checks that generated files are up to
   date, and runs the render tests on Metal, OpenGL and Vulkan.

7. **Release.** Set `version` in `plugin.json`, run `tools/bin/plugin generate`, commit, and run the
   **Release** workflow. Apps then use the plugin through JitPack on Android and Swift Package Manager on iOS;
   the release notes explain how. See [releases](docs/releases.md).

## How a plugin repository uses the tools

The tools are a git submodule at `tools/` in the plugin repository. The submodule commit pins everything:
the build logic, the SDK versions in [`versions.json`](versions.json), the GitHub Actions the plugin's
workflows call, and the demo apps and render-test runner.

```
plugin.json                 # describes the plugin; see below
include/<name>.h            # declares the registration function
src/                        # plugin sources
examples/                   # demo styles
render-tests/               # render-test fixtures
tools/                      # this repository, as a submodule
Package.swift, apple/       # generated by `tools/bin/plugin generate` and committed
settings.gradle.kts, build.gradle.kts, jitpack.yml, .github/workflows/  # installed by `tools/bin/plugin sync`
```

To update the tools:

```sh
git -C tools fetch --tags
git -C tools checkout v0.2.0
tools/bin/plugin sync
git add tools && git commit -am "Update plugin tools to v0.2.0"
```

`sync` refreshes the tool-owned files (workflows and Gradle boilerplate) and regenerates the Swift package.
CI runs `sync --check`, so a submodule bump without `sync` fails.

## plugin.json

```json
{
  "schemaVersion": 1,
  "id": "square-layer",
  "displayName": "Square layer",
  "version": "0.1.0",
  "registerFunction": "mln_square_layer_register",
  "header": "include/square_layer.h",
  "sources": ["src/square_layer.cpp"],
  "demo": {
    "style": "examples/square.json",
    "camera": {"latitude": 0, "longitude": 0, "zoom": 4}
  }
}
```

| Key | Meaning |
| --- | --- |
| `id` | Kebab-case identifier. Default for the Android artifact and library name and the bundle identifiers. |
| `version` | The plugin version. Releases use it as tag, asset version and `MLN_PLUGIN_VERSION`. |
| `registerFunction` | C function declared in `header`: `mln_plugin_status f(mln_plugin_register_function_v1 register_plugin, char* error, size_t capacity)`. It describes the plugin's layer types and calls `register_plugin`. |
| `header`, `sources` | Plugin sources, relative to the repository root. |
| `includeDirectories` | Optional. Defaults to the directory of `header`. |
| `android` | Optional overrides: `namespace`, `className`, `groupId`, `artifactId`, `libraryName`. |
| `apple` | Optional overrides: `product`, `className`, `swiftName`, `errorDomain`. |
| `demo` | Optional: the `style` the demo apps load, and an initial `camera`. |
| `renderTests` | Optional. Directory with the render-test `manifest.json`, default `render-tests`. |

Run `tools/bin/plugin config` to see the resolved configuration, including the derived names. For an `id` of
`square-layer`, apps register the plugin with:

```java
org.maplibre.plugins.squarelayer.SquareLayerPlugin.register(); // Android, after MapLibre.getInstance()
```

```objc
#import <SquareLayer/SquareLayer.h>
[MLNSquareLayerPlugin registerPluginWithError:&error];       // iOS; Swift: try SquareLayerPlugin.registerPlugin()
```

Register before loading a style that uses the plugin's layer types.

## Commands

Run `tools/bin/plugin` from anywhere in the plugin repository:

| Command | What it does |
| --- | --- |
| `config [key]` | Print the resolved plugin configuration. |
| `sync [--check]` | Install tool-owned files, then `generate`. |
| `generate [--check]` | Generate `Package.swift` and the Objective-C wrapper in `apple/`. |
| `android-aar [--output DIR] [--abis LIST]` | Build the Android library, by default for all ABIs. |
| `ios-xcframework [--output DIR]` | Build the static iOS XCFramework. |
| `run-android [--renderer opengl\|vulkan] [--screenshot FILE]` | Build, install and start the Android demo app on a device or emulator. |
| `run-ios [--simulator UDID] [--screenshot FILE]` | Build and start the iOS demo app in a simulator. |
| `render-tests [--backend metal\|opengl\|vulkan] [-- ARGS]` | Build MapLibre Native from source and run the plugin's render tests. |
| `release-notes` | Print the usage instructions the Release workflow puts in the release notes. |
| `jitpack` | Build and publish the Android library on JitPack (called by `jitpack.yml`). |

Details: [Android](docs/android.md), [iOS](docs/ios.md), [render tests](docs/render-tests.md), [releases](docs/releases.md).

## GitHub Actions

`sync` installs two workflows in the plugin repository. Their steps call composite actions in
[`actions/`](actions), so their behavior comes from the pinned tools.

- **CI**: checks that tool-owned and generated files are up to date, builds the Android library and the iOS
  XCFramework, starts the iOS demo app, and runs the render tests on Metal, OpenGL and Vulkan.
- **Release** (run manually): releases the version in `plugin.json`. It builds the AAR and XCFramework,
  creates a GitHub release with usage instructions and both assets, tags the version for Swift Package
  Manager, and has [JitPack](https://jitpack.io) build the Android library from the tag.

## Why not Bazel?

Plugin users consume plugins through Gradle and Swift Package Manager, so the tools build with those directly.
Only the render tests need MapLibre Native's source; they build it with CMake, which supports Metal, OpenGL and
Vulkan on the GitHub-hosted runners.
