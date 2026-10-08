"""Writes the usage instructions that open a plugin's GitHub release notes."""

import os
import re
import subprocess
import sys

import config as plugin_config


def repository(root):
    """owner/name of the plugin repository, from GITHUB_REPOSITORY or the origin remote."""
    if os.environ.get("GITHUB_REPOSITORY"):
        return os.environ["GITHUB_REPOSITORY"]
    url = subprocess.run(["git", "-C", root, "remote", "get-url", "origin"],
                         capture_output=True, text=True).stdout.strip()
    match = re.search(r"github\.com[:/](.+?)(\.git)?$", url)
    if not match:
        raise plugin_config.ConfigError("Cannot determine the GitHub repository; set GITHUB_REPOSITORY")
    return match.group(1)


def notes(config, repo):
    version = config["version"]
    versions = config["versions"]
    android = config["android"]
    apple = config["apple"]
    package_url = versions["maplibreIosPackage"]
    package_name = package_url.rstrip("/").removesuffix(".git").rsplit("/", 1)[-1]
    plugin_package = repo.rsplit("/", 1)[-1]
    owner, name = repo.split("/", 1)
    description = f"{config['description']}\n\n" if config["description"] else ""
    return f"""{description}This plugin requires the plugin-enabled MapLibre SDKs it was built against: MapLibre Android \
`{versions['maplibreAndroid']}` and MapLibre iOS `{versions['maplibreIos']}` from \
[{package_name}]({package_url}). The plugin API is experimental, so other SDK versions may not work.

Register the plugin after initializing MapLibre and before loading a style that uses its layer types.

## Android

Add [JitPack](https://jitpack.io/#{repo}/{version}) to your repositories in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {{
    repositories {{
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }}
}}
```

Then depend on the plugin next to the SDK, choosing the OpenGL or Vulkan renderer:

```kotlin
dependencies {{
    implementation("org.maplibre.gl:android-sdk-vulkan:{versions['maplibreAndroid']}") // or android-sdk-opengl
    implementation("com.github.{owner}:{name}:{version}")
}}
```

```kotlin
MapLibre.getInstance(context)
{android['qualifiedClassName']}.register()
```

## iOS

Add the plugin-enabled MapLibre package and this plugin with Swift Package Manager (in Xcode: File > Add \
Package Dependencies):

```swift
dependencies: [
    .package(url: "{package_url}", exact: "{versions['maplibreIos']}"),
    .package(url: "https://github.com/{repo}", exact: "{version}"),
],
targets: [
    .target(name: "App", dependencies: [
        .product(name: "MapLibre", package: "{package_name}"),
        .product(name: "{apple['product']}", package: "{plugin_package}"),
    ]),
]
```

```swift
import MapLibre
import {apple['product']}

try {apple['swiftName']}.registerPlugin()
```

```objc
#import <{apple['product']}/{apple['product']}.h>

NSError *error;
[{apple['className']} registerPluginWithError:&error];
```
"""


if __name__ == "__main__":
    try:
        config = plugin_config.resolve(os.environ.get("MLN_PLUGIN_ROOT", "."))
        sys.stdout.write(notes(config, repository(config["root"])))
    except plugin_config.ConfigError as error:
        sys.exit(f"error: {error}")
