// Applied by the settings.gradle.kts of every plugin repository.
// The repository root becomes the plugin's Android library; the generic demo app
// from the tools checkout is included as :demo.

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // The plugin-enabled MapLibre Android SDK pre-releases are published to Maven Central.
        mavenCentral()
    }
}

val pluginConfig = MlnPluginConfig.load(settingsDir, providers)
rootProject.name = pluginConfig.string("id")

include(":demo")
project(":demo").projectDir = File(pluginConfig.toolsRoot, "android/demo")
