// Applied by the build.gradle.kts of every plugin repository. Turns the repository root
// into the plugin's Android library, configured entirely from plugin.json.
import com.android.build.api.variant.LibraryAndroidComponentsExtension

plugins {
    id("com.android.library")
    `maven-publish`
}

val pluginConfig = MlnPluginConfig.load(layout.projectDirectory.asFile, providers)
val pluginRoot = pluginConfig.root
val tools = pluginConfig.toolsRoot
val pluginId = pluginConfig.string("id")
val pluginVersion = pluginConfig.string("version")
val qualifiedClassName = pluginConfig.string("android.qualifiedClassName")
val libraryName = pluginConfig.string("android.libraryName")
// Comma-separated ABI subset for faster local builds, for example -PmaplibrePluginAbis=arm64-v8a.
val pluginAbis = providers.gradleProperty("maplibrePluginAbis").orNull
    ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)

group = pluginConfig.string("android.groupId")
version = pluginVersion

// R8 must keep the class name: the native library binds it by name in JNI_OnLoad.
val consumerRules = layout.buildDirectory.file("generated/mln-plugin/consumer-rules.pro").get().asFile
"-keep class $qualifiedClassName { *; }\n".let { rules ->
    if (!consumerRules.isFile || consumerRules.readText() != rules) {
        consumerRules.parentFile.mkdirs()
        consumerRules.writeText(rules)
    }
}

android {
    namespace = pluginConfig.string("android.namespace")
    compileSdk = pluginConfig.number("versions.androidCompileSdk")!!.toInt()
    ndkVersion = pluginConfig.string("versions.androidNdk")

    defaultConfig {
        minSdk = pluginConfig.number("versions.androidMinSdk")!!.toInt()
        consumerProguardFiles(consumerRules)
        if (pluginAbis != null) {
            ndk { abiFilters += pluginAbis }
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DMLN_PLUGIN_LIBRARY_NAME=$libraryName",
                    "-DMLN_PLUGIN_SOURCES=" + pluginConfig.strings("sources")
                        .joinToString(";") { pluginRoot.resolve(it).absolutePath },
                    "-DMLN_PLUGIN_INCLUDE_DIRECTORIES=" + pluginConfig.strings("includeDirectories")
                        .joinToString(";") { pluginRoot.resolve(it).absolutePath },
                    "-DMLN_PLUGIN_HEADER=" + pluginRoot.resolve(pluginConfig.string("header")).absolutePath,
                    "-DMLN_PLUGIN_REGISTER_FUNCTION=" + pluginConfig.string("registerFunction"),
                    "-DMLN_PLUGIN_JAVA_CLASS=" + qualifiedClassName.replace('.', '/'),
                    "-DMLN_PLUGIN_VERSION=$pluginVersion",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = tools.resolve("android/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        // The MapLibre SDK AAR exposes <mln/plugin/plugin_api.h> as a header-only Prefab module.
        prefab = true
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(tools.resolve("android/library/AndroidManifest.xml"))
            java.setSrcDirs(emptyList<Any>())
            res.setSrcDirs(emptyList<Any>())
            assets.setSrcDirs(emptyList<Any>())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // The application provides MapLibre and its renderer library.
    packaging.jniLibs.excludes += setOf("**/libc++_shared.so", "**/libmaplibre.so", "**/libmaplibre-opengl.so")

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

val generateRegistrationClass = tasks.register<GenerateRegistrationClass>("generateMlnPluginRegistrationClass") {
    // Read from pluginConfig: the script's values of the same names are shadowed by the task's properties here.
    qualifiedClassName.set(pluginConfig.string("android.qualifiedClassName"))
    pluginId.set(pluginConfig.string("id"))
    pluginVersion.set(pluginConfig.string("version"))
    displayName.set(pluginConfig.string("displayName"))
    libraryName.set(pluginConfig.string("android.libraryName"))
    outputDirectory.set(layout.buildDirectory.dir("generated/mln-plugin/java"))
}

extensions.getByType<LibraryAndroidComponentsExtension>().onVariants { variant ->
    variant.sources.java?.addGeneratedSourceDirectory(generateRegistrationClass, GenerateRegistrationClass::outputDirectory)
}

dependencies {
    // Compile against a plugin-enabled MapLibre SDK for LibraryLoader and the Prefab header.
    // The application picks the renderer artifact (OpenGL or Vulkan).
    compileOnly("org.maplibre.gl:android-sdk-opengl:${pluginConfig.string("versions.maplibreAndroid")}")
}

// JitPack publishes under its own coordinates (com.github.<owner>:<repository>), passed by
// `tools/bin/plugin jitpack` as -PmlnPublishGroupId and -PmlnPublishArtifactId.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = providers.gradleProperty("mlnPublishGroupId").orNull ?: pluginConfig.string("android.groupId")
            artifactId = providers.gradleProperty("mlnPublishArtifactId").orNull ?: pluginConfig.string("android.artifactId")
            version = pluginVersion
            afterEvaluate { from(components["release"]) }
            pom {
                name.set(pluginConfig.string("displayName"))
                pluginConfig.stringOrNull("description")?.takeIf(String::isNotEmpty)?.let { description.set(it) }
            }
        }
    }
}
