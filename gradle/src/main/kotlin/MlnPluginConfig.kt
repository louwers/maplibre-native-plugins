import groovy.json.JsonSlurper
import org.gradle.api.provider.ProviderFactory
import java.io.File

/**
 * The resolved plugin.json, as printed by tools/lib/config.py. Resolving through the shared
 * Python resolver keeps naming defaults identical for Gradle, SwiftPM and the CLI.
 */
class MlnPluginConfig(private val values: Map<String, Any?>) {
    val root: File get() = File(string("root"))
    val toolsRoot: File get() = File(string("toolsRoot"))

    fun string(key: String): String = lookup(key) as? String
        ?: error("plugin.json: missing string value '$key'")

    fun stringOrNull(key: String): String? = lookup(key) as? String

    @Suppress("UNCHECKED_CAST")
    fun strings(key: String): List<String> = lookup(key) as? List<String>
        ?: error("plugin.json: missing list value '$key'")

    fun number(key: String): Number? = lookup(key) as? Number

    fun lookup(key: String): Any? = key.split('.').fold(values as Any?) { value, part ->
        (value as? Map<*, *>)?.get(part)
    }

    companion object {
        /** The tools checkout: `tools/` in the plugin repository unless overridden. */
        fun toolsRoot(pluginRoot: File, providers: ProviderFactory): File =
            providers.gradleProperty("mlnPluginTools").orNull?.let(::File)
                ?: providers.environmentVariable("MLN_PLUGIN_TOOLS").orNull?.let(::File)
                ?: File(pluginRoot, "tools")

        @Suppress("UNCHECKED_CAST")
        fun load(pluginRoot: File, providers: ProviderFactory): MlnPluginConfig {
            val tools = toolsRoot(pluginRoot, providers)
            val output = providers.exec {
                commandLine("python3", File(tools, "lib/config.py").absolutePath)
                environment("MLN_PLUGIN_ROOT", pluginRoot.absolutePath)
                isIgnoreExitValue = true
            }
            val result = output.result.get()
            if (result.exitValue != 0) {
                throw IllegalStateException(
                    "Could not resolve ${File(pluginRoot, "plugin.json")}: " +
                        output.standardError.asText.get().trim()
                )
            }
            return MlnPluginConfig(JsonSlurper().parseText(output.standardOutput.asText.get()) as Map<String, Any?>)
        }
    }
}
