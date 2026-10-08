// Convention plugins for MapLibre Native plugin repositories.
// Plugin repositories include this build from their settings.gradle.kts.
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "maplibre-native-plugin-gradle"
