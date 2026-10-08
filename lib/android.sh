# shellcheck shell=bash
# Shared helpers for the Android commands. Source after bin/plugin set MLN_PLUGIN_TOOLS and MLN_PLUGIN_ROOT.

# Locate the Android SDK when ANDROID_HOME is not set.
if [[ -z ${ANDROID_HOME:-} ]]; then
    if [[ -n ${ANDROID_SDK_ROOT:-} ]]; then
        export ANDROID_HOME="$ANDROID_SDK_ROOT"
    elif [[ -f $MLN_PLUGIN_ROOT/local.properties ]] && grep -q '^sdk.dir=' "$MLN_PLUGIN_ROOT/local.properties"; then
        ANDROID_HOME="$(sed -n 's/^sdk.dir=//p' "$MLN_PLUGIN_ROOT/local.properties")"
        export ANDROID_HOME
    elif [[ -d $HOME/Library/Android/sdk ]]; then
        export ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [[ -d $HOME/Android/Sdk ]]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    else
        echo "Android SDK not found; set ANDROID_HOME" >&2
        exit 1
    fi
fi

# Runs the plugin's Gradle build with the wrapper shipped in the tools.
plugin_gradle() {
    "$MLN_PLUGIN_TOOLS/gradle/gradlew" \
        -p "$MLN_PLUGIN_ROOT" \
        -Dorg.gradle.jvmargs="-Xmx4g -Dfile.encoding=UTF-8" \
        -PmlnPluginTools="$MLN_PLUGIN_TOOLS" \
        "$@"
}

plugin_config() {
    python3 "$MLN_PLUGIN_TOOLS/lib/config.py" "$1"
}

adb_command() {
    if [[ -x $ANDROID_HOME/platform-tools/adb ]]; then
        "$ANDROID_HOME/platform-tools/adb" "$@"
    else
        adb "$@"
    fi
}
