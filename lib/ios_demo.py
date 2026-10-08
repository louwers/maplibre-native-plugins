"""Prepares the iOS demo app sources for tools/bin/plugin run-ios.

Usage: python3 ios_demo.py DEMO_DIRECTORY  (MLN_PLUGIN_ROOT selects the plugin repository)
Writes DEMO_DIRECTORY/App/main.m and copies the demo style's directory to DEMO_DIRECTORY/Demo.
"""

import json
import os
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import config as plugin_config  # noqa: E402

demo = Path(sys.argv[1])
config = plugin_config.resolve(os.environ["MLN_PLUGIN_ROOT"])
root = Path(config["root"])
style = config["demo"].get("style")
if style:
    # Bundle the style's directory, so relative references keep working.
    shutil.copytree(root / Path(style).parent, demo / "Demo", dirs_exist_ok=True)
    style_name = Path(style).name
else:
    print("warning: plugin.json has no demo.style; showing an empty map", file=sys.stderr)
    style_name = "empty-style.json"
    (demo / "Demo" / style_name).write_text(json.dumps(
        {"version": 8, "sources": {}, "layers": [{"id": "background", "type": "background"}]}))

camera = config["demo"].get("camera")
camera_code = ""
if camera:
    camera_code = ("    [mapView setCenterCoordinate:CLLocationCoordinate2DMake({latitude}, {longitude}) "
                   "zoomLevel:{zoom} animated:NO];").format(**camera)

def objc(value):
    return value.replace("\\", "\\\\").replace('"', '\\"')

values = {
    "PRODUCT": config["apple"]["product"],
    "CLASS": config["apple"]["className"],
    "DISPLAY_NAME": objc(config["displayName"]),
    "STYLE": objc(style_name),
    "CAMERA": camera_code,
}
text = (Path(config["toolsRoot"]) / "ios" / "demo" / "main.m.in").read_text()
for key, value in values.items():
    text = text.replace("@@" + key + "@@", value)
(demo / "App" / "main.m").write_text(text)
