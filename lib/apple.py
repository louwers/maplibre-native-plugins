"""Generates a plugin's Swift package manifest and Objective-C registration wrapper.

Usage: python3 apple.py [--check]   (MLN_PLUGIN_ROOT selects the plugin repository)

The outputs are committed in the plugin repository, because Swift Package Manager builds
plugins from source and does not run code generators.
"""

import json
import os
import sys
from pathlib import Path, PurePosixPath

sys.path.insert(0, str(Path(__file__).resolve().parent))
import config as plugin_config  # noqa: E402

TEMPLATES = plugin_config.TOOLS_ROOT / "templates" / "apple"
GENERATED_DIRECTORY = "apple"


def _fill(template, values):
    text = (TEMPLATES / template).read_text()
    for key, value in values.items():
        text = text.replace("@@" + key + "@@", value)
    if "@@" in text:
        raise plugin_config.ConfigError(f"Unfilled placeholder in {template}")
    return text


def _swift_string(value):
    return json.dumps(value)  # JSON string escaping is valid Swift string escaping for these values.


def _objc_string(value):
    return value.replace("\\", "\\\\").replace('"', '\\"')


def _header_include(config):
    header = PurePosixPath(config["header"])
    for directory in config["includeDirectories"]:
        try:
            return str(header.relative_to(PurePosixPath(directory)))
        except ValueError:
            continue
    raise plugin_config.ConfigError(
        f"plugin.json: header {header} must be inside one of includeDirectories {config['includeDirectories']}")


def outputs(config):
    apple = config["apple"]
    product = apple["product"]
    wrapper_source = f"{GENERATED_DIRECTORY}/src/{product}.mm"
    sources = config["sources"] + [wrapper_source]
    # Top-level directories holding sources or headers; root-level sources are kept by name.
    kept = sorted({PurePosixPath(path).parts[0] for path in sources if len(PurePosixPath(path).parts) > 1}
                  | {PurePosixPath(path).parts[0] for path in config["includeDirectories"] if path != "."}
                  | {GENERATED_DIRECTORY})
    settings = [f"                .headerSearchPath({_swift_string(d)})," for d in config["includeDirectories"]]
    settings.append(f'                .define("MLN_PLUGIN_VERSION", to: {_swift_string(json.dumps(config["version"]))}),')
    package_url = config["versions"]["maplibreIosPackage"]
    values = {
        "ID": config["id"],
        "PRODUCT": product,
        "CLASS": apple["className"],
        "SWIFT_NAME": apple["swiftName"],
        "ERROR_DOMAIN": _objc_string(apple["errorDomain"]),
        "DISPLAY_NAME": _objc_string(config["displayName"]),
        "VERSION": _objc_string(config["version"]),
        "REGISTER_FUNCTION": config["registerFunction"],
        "HEADER_INCLUDE": _header_include(config),
        "MAPLIBRE_IOS_PACKAGE": package_url,
        "MAPLIBRE_IOS_PACKAGE_NAME": package_url.rstrip("/").removesuffix(".git").rsplit("/", 1)[-1],
        "MAPLIBRE_IOS_VERSION": config["versions"]["maplibreIos"],
        "IOS_DEPLOYMENT_TARGET": config["versions"]["iosDeploymentTarget"],
        "SOURCES": "\n".join(f"    {_swift_string(s)}," for s in sources),
        "KEPT_DIRECTORIES": ", ".join(_swift_string(d) for d in kept),
        "SETTINGS": "\n".join(settings),
    }
    return {
        "Package.swift": _fill("Package.swift.in", values),
        f"{GENERATED_DIRECTORY}/include/{product}/{product}.h": _fill("Plugin.h.in", values),
        wrapper_source: _fill("Plugin.mm.in", values),
    }


def generate(root, check):
    config = plugin_config.resolve(root)
    root = Path(config["root"])
    files = outputs(config)
    stale = [path for path, content in files.items()
             if not (root / path).is_file() or (root / path).read_text() != content]
    # Everything under apple/ is generated; leftovers come from a renamed product.
    generated_root = root / GENERATED_DIRECTORY
    leftovers = sorted(str(p.relative_to(root)) for p in generated_root.rglob("*")
                       if p.is_file() and str(p.relative_to(root)) not in files) if generated_root.is_dir() else []
    if check:
        if stale or leftovers:
            message = "\n".join([f"  stale: {p}" for p in stale] + [f"  not generated: {p}" for p in leftovers])
            raise plugin_config.ConfigError(
                "Generated Apple files are out of date. Run tools/bin/plugin generate and commit the result:\n"
                + message)
        return []
    for path in stale:
        (root / path).parent.mkdir(parents=True, exist_ok=True)
        (root / path).write_text(files[path])
    for path in leftovers:
        (root / path).unlink()
    # SwiftPM rejects an include directory with a second (even empty) product directory.
    for directory in sorted((p for p in generated_root.rglob("*") if p.is_dir()), reverse=True):
        if not any(directory.iterdir()):
            directory.rmdir()
    return stale + leftovers


if __name__ == "__main__":
    arguments = sys.argv[1:]
    if any(argument != "--check" for argument in arguments):
        sys.exit("Usage: tools/bin/plugin generate [--check]")
    check = "--check" in arguments
    try:
        changed = generate(os.environ.get("MLN_PLUGIN_ROOT", "."), check)
    except plugin_config.ConfigError as error:
        sys.exit(f"error: {error}")
    if check:
        print("Generated Apple files are up to date.")
    else:
        print(f"Generated Package.swift and {GENERATED_DIRECTORY}/ ({len(changed)} files changed).")
        for path in changed:
            print(f"  {path}")
