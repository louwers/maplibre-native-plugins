// Registers the plugin, then runs its render-test manifest with MapLibre Native's harness.
// Arguments are forwarded to the harness (--filter, --update, --recycle-map, --online, ...).
// Without --manifestPath, it runs the plugin's own manifest.

#include <mln/plugin/plugin_api.h>
#include <mln/render_test.hpp>

#include <cstddef>
#include <iostream>
#include <string>
#include <string_view>
#include <vector>

namespace mln::plugin::test {
extern const char* const registerFunctionName;
mln_plugin_status registerPlugin(char* errorMessage, std::size_t errorMessageCapacity);
} // namespace mln::plugin::test

int main(int argc, char** argv) {
    std::vector<std::string> arguments{argc > 0 ? argv[0] : "plugin-render-tests"};
    bool hasManifest = false;
    for (int i = 1; i < argc; ++i) {
        const std::string_view argument{argv[i]};
        hasManifest = hasManifest || argument == "-p" || argument == "--manifestPath" ||
                      argument.starts_with("-p=") || argument.starts_with("--manifestPath=");
        arguments.emplace_back(argument);
    }
    if (!hasManifest) {
        arguments.emplace_back("--manifestPath");
        arguments.emplace_back(MLN_PLUGIN_RENDER_TEST_MANIFEST);
    }

    char error[512]{};
    const auto status = mln::plugin::test::registerPlugin(error, sizeof(error));
    if (status != MLN_PLUGIN_STATUS_OK && status != MLN_PLUGIN_STATUS_ALREADY_REGISTERED) {
        std::cerr << "Unable to register the plugin through " << mln::plugin::test::registerFunctionName << " ("
                  << status << "): " << error << '\n';
        return 4;
    }

    std::vector<char*> rawArguments;
    rawArguments.reserve(arguments.size());
    for (auto& argument : arguments) rawArguments.push_back(argument.data());
    return mln::runRenderTests(static_cast<int>(rawArguments.size()), rawArguments.data(), {});
}
