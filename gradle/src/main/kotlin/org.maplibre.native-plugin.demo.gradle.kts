// The generic Android demo app (tools/android/demo), included as :demo by the settings plugin.
// It registers the plugin and shows the demo style from plugin.json.
import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    id("com.android.application")
}

val pluginRoot = rootProject.layout.projectDirectory.asFile
val pluginConfig = MlnPluginConfig.load(pluginRoot, providers)
val pluginId = pluginConfig.string("id")
val maplibreVersion = pluginConfig.string("versions.maplibreAndroid")
val demoStyle = pluginConfig.stringOrNull("demo.style")?.let(pluginRoot::resolve)

// Build output belongs to the plugin repository, not to the tools checkout.
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("demo"))

fun javaString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "org.maplibre.plugins.demo"
    compileSdk = pluginConfig.number("versions.androidCompileSdk")!!.toInt()

    defaultConfig {
        applicationId = "org.maplibre.plugins.demo." + pluginId.replace("-", "")
        minSdk = pluginConfig.number("versions.androidMinSdk")!!.toInt()
        targetSdk = compileSdk
        versionCode = 1
        versionName = pluginConfig.string("version")
        buildConfigField("String", "PLUGIN_CLASS", javaString(pluginConfig.string("android.qualifiedClassName")))
        buildConfigField("String", "PLUGIN_NAME", javaString(pluginConfig.string("displayName")))
        buildConfigField("String", "STYLE_URI", javaString(
            if (demoStyle != null) "asset://demo-style.json" else "https://demotiles.maplibre.org/style.json"))
        val camera = pluginConfig.lookup("demo.camera") as? Map<*, *>
        buildConfigField("boolean", "HAS_CAMERA", (camera != null).toString())
        for ((field, key) in listOf("LATITUDE" to "latitude", "LONGITUDE" to "longitude", "ZOOM" to "zoom")) {
            buildConfigField("double", field, ((camera?.get(key) as? Number)?.toDouble() ?: 0.0).toString())
        }
    }

    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "renderer"
    productFlavors {
        create("opengl") {
            dimension = "renderer"
            applicationIdSuffix = ".opengl"
        }
        create("vulkan") {
            dimension = "renderer"
            applicationIdSuffix = ".vulkan"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val copyDemoStyle = tasks.register<CopyDemoStyle>("copyMlnPluginDemoStyle") {
    if (demoStyle != null) style.set(demoStyle)
    outputDirectory.set(layout.buildDirectory.dir("generated/mln-plugin/assets"))
}

extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(copyDemoStyle, CopyDemoStyle::outputDirectory)
}

dependencies {
    implementation(project(":"))
    "openglImplementation"("org.maplibre.gl:android-sdk-opengl:$maplibreVersion")
    "vulkanImplementation"("org.maplibre.gl:android-sdk-vulkan:$maplibreVersion")
}
