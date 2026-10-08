"""Loads, validates and resolves a plugin's plugin.json.

Every tool reads the plugin through resolve(), so naming defaults live in one place.
"""

import json
import os
import re
import sys
from pathlib import Path

TOOLS_ROOT = Path(__file__).resolve().parent.parent


class ConfigError(Exception):
    pass


def _require(condition, message):
    if not condition:
        raise ConfigError(message)


def _pascal(identifier):
    return "".join(part[:1].upper() + part[1:] for part in identifier.split("-"))


def _relative_file(root, path, what):
    _require(isinstance(path, str) and path and not os.path.isabs(path), f"{what} must be a relative path")
    full = (root / path).resolve()
    _require(root in full.parents or full == root, f"{what} escapes the plugin repository: {path}")
    _require(full.exists(), f"{what} does not exist: {path}")
    return path


def versions():
    return json.loads((TOOLS_ROOT / "versions.json").read_text())


def resolve(root):
    root = Path(root).resolve()
    path = root / "plugin.json"
    _require(path.is_file(), f"Missing {path}")
    raw = json.loads(path.read_text())
    _require(raw.get("schemaVersion") == 1, "plugin.json: schemaVersion must be 1")

    plugin_id = raw.get("id")
    _require(isinstance(plugin_id, str) and re.fullmatch(r"[a-z][a-z0-9]*(-[a-z0-9]+)*", plugin_id),
             "plugin.json: id must be kebab-case, for example square-layer")
    version = raw.get("version")
    _require(isinstance(version, str) and re.fullmatch(r"\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?", version),
             "plugin.json: version must be a semantic version")
    register = raw.get("registerFunction")
    _require(isinstance(register, str) and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", register),
             "plugin.json: registerFunction must be a C identifier")
    header = _relative_file(root, raw.get("header"), "plugin.json: header")
    sources = raw.get("sources")
    _require(isinstance(sources, list) and sources, "plugin.json: sources must be a non-empty list")
    sources = [_relative_file(root, source, "plugin.json: source") for source in sources]
    include_directories = raw.get("includeDirectories", [str(Path(header).parent)])
    include_directories = [_relative_file(root, d, "plugin.json: include directory") for d in include_directories]

    product = _pascal(plugin_id)
    android = dict(raw.get("android", {}))
    android.setdefault("namespace", "org.maplibre.plugins." + plugin_id.replace("-", ""))
    android.setdefault("className", product + "Plugin")
    android.setdefault("groupId", "org.maplibre.plugins")
    android.setdefault("artifactId", plugin_id)
    android.setdefault("libraryName", plugin_id)
    _require(re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", android["namespace"]),
             "plugin.json: android.namespace must be a Java package")
    _require(re.fullmatch(r"[A-Z][A-Za-z0-9_]*", android["className"]), "plugin.json: android.className")
    android["qualifiedClassName"] = android["namespace"] + "." + android["className"]

    apple = dict(raw.get("apple", {}))
    apple.setdefault("product", product)
    apple.setdefault("className", "MLN" + apple["product"] + "Plugin")
    apple.setdefault("swiftName", apple["product"] + "Plugin")
    apple.setdefault("errorDomain", "org.maplibre.plugins." + plugin_id)
    for key in ("product", "className", "swiftName"):
        _require(re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", apple[key]), f"plugin.json: apple.{key}")

    demo = dict(raw.get("demo", {}))
    if "style" in demo:
        _relative_file(root, demo["style"], "plugin.json: demo.style")
    camera = demo.get("camera")
    if camera is not None:
        _require(all(isinstance(camera.get(k), (int, float)) for k in ("latitude", "longitude", "zoom")),
                 "plugin.json: demo.camera needs latitude, longitude and zoom")

    render_tests = raw.get("renderTests", "render-tests")
    has_render_tests = (root / render_tests / "manifest.json").is_file()

    return {
        "root": str(root),
        "id": plugin_id,
        "displayName": raw.get("displayName", plugin_id),
        "description": raw.get("description", ""),
        "version": version,
        "registerFunction": register,
        "header": header,
        "sources": sources,
        "includeDirectories": include_directories,
        "android": android,
        "apple": apple,
        "demo": demo,
        "renderTests": render_tests if has_render_tests else None,
        "versions": versions(),
        "toolsRoot": str(TOOLS_ROOT),
    }


def lookup(config, key):
    value = config
    for part in key.split("."):
        _require(isinstance(value, dict) and part in value, f"Unknown configuration key: {key}")
        value = value[part]
    return value


if __name__ == "__main__":
    try:
        config = resolve(os.environ.get("MLN_PLUGIN_ROOT", "."))
        if len(sys.argv) > 1:
            value = lookup(config, sys.argv[1])
            print(value if isinstance(value, str) else json.dumps(value))
        else:
            print(json.dumps(config, indent=2))
    except ConfigError as error:
        sys.exit(f"error: {error}")
