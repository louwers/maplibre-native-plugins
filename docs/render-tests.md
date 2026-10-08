# Render tests

Render tests draw a style that uses the plugin's layer type and compare the result with
reviewed images. They use MapLibre Native's own render-test harness, built from source
with the plugin API enabled and with the plugin linked in. Plugins only provide fixtures;
the runner, its build and the registration code all live in the tools.

```sh
tools/bin/plugin render-tests --backend metal      # macOS
tools/bin/plugin render-tests --backend opengl     # Linux (runs under xvfb-run without a display)
tools/bin/plugin render-tests --backend vulkan     # Linux
```

The first run fetches MapLibre Native at `versions.maplibreNative` (the commit whose plugin
API matches the SDK versions in `versions.json`) with its submodules, then builds it. That
takes a while; later runs reuse the checkout and build directory, and use ccache when it is
installed.

| Option | Meaning |
| --- | --- |
| `--backend metal\|opengl\|vulkan` | Renderer. Defaults to metal on macOS, opengl elsewhere. |
| `--native-source DIR` | Use an existing MapLibre Native checkout, for example to test unreleased API changes. `MLN_NATIVE_SOURCE` does the same. |
| `--build-dir DIR` | Build directory. Default: `build/render-tests-<backend>` in the plugin repository. |
| `--build-only` | Configure and build without running. |
| `--fetch-only` | Fetch MapLibre Native if needed and print its path. |
| `-- ARGS` | Arguments for MapLibre's harness, see below. |

The fetched checkout goes to `$MLN_PLUGIN_TOOLS_CACHE/maplibre-native-<commit>`. The cache
directory defaults to `~/.cache/maplibre-native-plugins`, and `MLN_NATIVE_REPOSITORY`
selects another Git remote.

## Fixtures

`plugin.json`'s `renderTests` names the fixture directory (default `render-tests`). It must
contain a MapLibre render-test manifest:

```text
render-tests/
├── manifest.json
├── results/.gitkeep          # the harness requires result_path to exist; the command creates it
└── <suite>/<case>/
    ├── style.json
    └── expected.png          # plus optional alternatives, see below
```

```json
{
  "base_test_path": ".",
  "cache_path": "cache.db",
  "expectation_paths": [],
  "ignore_paths": [],
  "result_path": "results"
}
```

A case's test ID is its path relative to `base_test_path`, for example `square/basic`.
Each `style.json` is a normal style using the plugin's layer type, with test options under
`metadata.test` (`width`, `height`, `pixelRatio`, `allowed`, `operations`, ...), exactly as
in MapLibre Native's own render tests. Sources must be inline or local: the harness runs
offline unless you pass `--online`.

Add these to the plugin repository's `.gitignore`; the harness writes them into the fixture
directory:

```gitignore
render-tests/cache.db
render-tests/results/*
!render-tests/results/.gitkeep
render-tests/**/actual.png
render-tests/**/diff.png
```

## How expected images are chosen

For each case, the harness builds a list of expectation directories: the case's own directory
first, followed by `<path>/<test id>` for every entry in the manifest's `expectation_paths`.
It searches that list **from the end**, and uses the first directory that contains at least
one file matching `expected*.png`.

Within that directory, every `expected*.png` is a valid alternative. The case passes if the
rendered image differs from **any** of them by no more than `allowed` (the fraction of
mismatched pixels, default 0.00015; set it per case in `metadata.test.allowed`). Pixels are
compared with pixelmatch at threshold 0.1285, as in MapLibre GL JS.

There are two ways to deal with rendering differences between backends and platforms:

1. **Alternatives in the case directory.** Keep `expected.png` and add for example
   `expected-metal.png`, `expected-opengl.png` or `expected-vulkan.png`. All of them are tried
   on every backend, so this is lenient: a Metal render that happens to match the Vulkan
   image also passes. This is the simplest option and is enough for small anti-aliasing
   differences.
2. **A manifest per platform.** Add a second manifest, for example
   `render-tests/manifest-linux.json` with `"expectation_paths": ["expected/linux"]`, and run
   it with `-- --manifestPath render-tests/manifest-linux.json`. Images under
   `expected/linux/<test id>/expected.png` then replace the case directory's images for that
   manifest only, and cases without such a directory fall back to the shared ones.

Prefer a single `expected.png` and a slightly higher `allowed`, when the differences really
are just anti-aliasing.

## Arguments for MapLibre's harness

Anything after `--` goes to the harness:

| Argument | Meaning |
| --- | --- |
| `--filter REGEX` (`-f`) | Run only test IDs matching the regular expression. |
| `--recycle-map` (`-r`) | Reuse one map for all cases (faster; CI uses it). |
| `--update default` (`-u`) | Write each rendered image to the case's `expected.png`. |
| `--update platform` | Write it to the last expectation directory (the last `expectation_paths` entry). |
| `--online` | Allow network requests. |
| `--shuffle`, `--seed N` | Randomize case order. |
| `--manifestPath PATH` (`-p`) | Run another manifest instead of the plugin's own. |

`--update` reports every case as errored because nothing is compared. Run again without it
to check the result.

## Results

The harness prints a summary and writes an HTML report with the actual, expected and diff
images to `<result_path>/<manifest name>.html` (`render-tests/results/manifest.html`). Failing
cases also leave `actual.png` and `diff.png` next to their expected images.

## Creating and updating expected images

1. Add `render-tests/<suite>/<case>/style.json`.
2. Run `tools/bin/plugin render-tests -- --filter '<suite>/<case>' --update default`.
3. Review the new `expected.png` and commit it.

### Rebaselining from CI

You can only render OpenGL and Vulkan locally on Linux. When CI fails on those backends:

1. Open the failed run and download the `render-tests-<plugin id>-<backend>` artifact. It holds
   the HTML report and each failing case's `actual.png` and `diff.png`.
2. Check in the report that the differences are acceptable.
3. Copy each `actual.png` into its case directory, either as `expected.png` (if all
   backends should match it) or as an alternative such as `expected-opengl.png`.

## CI

Plugin workflows use the composite action after checking out with submodules:

```yaml
jobs:
  render-tests:
    strategy:
      fail-fast: false
      matrix:
        include:
          - {backend: metal, os: macos-15}
          - {backend: opengl, os: ubuntu-24.04}
          - {backend: vulkan, os: ubuntu-24.04}
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v7
        with:
          submodules: recursive
      - uses: ./tools/actions/render-tests
        with:
          backend: ${{ matrix.backend }}
```

The action fetches MapLibre Native (shallow, about two minutes), caches a ccache directory, installs the
platform dependencies (Homebrew on macOS; MapLibre Native's
`.github/scripts/install-linux-deps` on Linux), builds and runs the tests, and uploads the
diagnostics artifact when they fail. Inputs: `backend` (required), `plugin-root` (default
`.`) and `runner-arguments` (default `--recycle-map`).

## Building the runner by hand

The runner is a CMake project in `tools/render-tests` that only needs the plugin root and a
MapLibre Native checkout:

```sh
cmake -S tools/render-tests -B build/render-tests-metal -G Ninja \
  -DMLN_PLUGIN_ROOT="$PWD" \
  -DMAPLIBRE_NATIVE_SOURCE_DIR=/path/to/maplibre-native \
  -DMLN_WITH_METAL=ON
cmake --build build/render-tests-metal --target plugin-render-tests
build/render-tests-metal/plugin-render-tests --recycle-map
```

At configure time it reads `plugin.json` through `tools/lib/config.py`. It compiles the
plugin's `sources` with its `includeDirectories`, defines `MLN_PLUGIN_VERSION` (the plugin
version, or `-DMLN_PLUGIN_VERSION_OVERRIDE=...`), and generates the translation unit that
calls `registerFunction` with `mln_plugin_register_v1`. Without `--manifestPath`, the runner
uses the plugin's manifest.
