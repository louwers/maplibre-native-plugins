cmake_minimum_required(VERSION 3.25 FATAL_ERROR)

# Builds MapLibre Native's render-test harness with one plugin linked in.
# Usually driven by `tools/bin/plugin render-tests`; it can also be configured directly:
#   cmake -S tools/render-tests -B build/render-tests-metal -G Ninja \
#     -DMLN_PLUGIN_ROOT=$PWD -DMAPLIBRE_NATIVE_SOURCE_DIR=/path/to/maplibre-native -DMLN_WITH_METAL=ON

project(maplibre-native-plugin-render-tests LANGUAGES C CXX)

set(MLN_PLUGIN_ROOT "" CACHE PATH "Plugin repository root (the directory containing plugin.json)")
set(MAPLIBRE_NATIVE_SOURCE_DIR "" CACHE PATH "MapLibre Native checkout with the plugin API")
set(MLN_PLUGIN_VERSION_OVERRIDE "" CACHE STRING "Value for MLN_PLUGIN_VERSION (defaults to plugin.json version)")

if(NOT MLN_PLUGIN_ROOT OR NOT EXISTS "${MLN_PLUGIN_ROOT}/plugin.json")
    message(FATAL_ERROR "Set MLN_PLUGIN_ROOT to the plugin repository root")
endif()
get_filename_component(MLN_PLUGIN_ROOT "${MLN_PLUGIN_ROOT}" ABSOLUTE)
if(NOT MAPLIBRE_NATIVE_SOURCE_DIR OR NOT EXISTS "${MAPLIBRE_NATIVE_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Set MAPLIBRE_NATIVE_SOURCE_DIR to a MapLibre Native checkout")
endif()
get_filename_component(MAPLIBRE_NATIVE_SOURCE_DIR "${MAPLIBRE_NATIVE_SOURCE_DIR}" ABSOLUTE)

set(backend_count 0)
foreach(backend OPENGL VULKAN METAL)
    if(MLN_WITH_${backend})
        math(EXPR backend_count "${backend_count} + 1")
    endif()
endforeach()
if(NOT backend_count EQUAL 1)
    message(FATAL_ERROR "Select exactly one backend: MLN_WITH_OPENGL, MLN_WITH_VULKAN or MLN_WITH_METAL")
endif()

# Resolve plugin.json through the same loader as every other tool.
get_filename_component(tools_root "${CMAKE_CURRENT_LIST_DIR}/.." ABSOLUTE)
find_package(Python3 REQUIRED COMPONENTS Interpreter)
execute_process(
    COMMAND "${CMAKE_COMMAND}" -E env "MLN_PLUGIN_ROOT=${MLN_PLUGIN_ROOT}"
            "${Python3_EXECUTABLE}" "${tools_root}/lib/config.py"
    OUTPUT_VARIABLE plugin_config
    ERROR_VARIABLE plugin_config_error
    RESULT_VARIABLE plugin_config_result
)
if(NOT plugin_config_result EQUAL 0)
    message(FATAL_ERROR "Invalid plugin.json: ${plugin_config_error}")
endif()
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${MLN_PLUGIN_ROOT}/plugin.json"
                                                               "${tools_root}/lib/config.py")

string(JSON plugin_id GET "${plugin_config}" id)
string(JSON plugin_version GET "${plugin_config}" version)
string(JSON plugin_register_function GET "${plugin_config}" registerFunction)
string(JSON plugin_header GET "${plugin_config}" header)
string(JSON plugin_render_tests ERROR_VARIABLE no_render_tests GET "${plugin_config}" renderTests)
if(no_render_tests OR plugin_render_tests STREQUAL "" OR plugin_render_tests MATCHES "^(null|NOTFOUND)$")
    message(FATAL_ERROR "${plugin_id} has no render tests: add render-tests/manifest.json")
endif()

set(plugin_sources)
string(JSON source_count LENGTH "${plugin_config}" sources)
math(EXPR source_last "${source_count} - 1")
foreach(index RANGE ${source_last})
    string(JSON source GET "${plugin_config}" sources ${index})
    list(APPEND plugin_sources "${MLN_PLUGIN_ROOT}/${source}")
endforeach()

set(plugin_include_directories)
string(JSON include_count LENGTH "${plugin_config}" includeDirectories)
if(include_count GREATER 0)
    math(EXPR include_last "${include_count} - 1")
    foreach(index RANGE ${include_last})
        string(JSON include_directory GET "${plugin_config}" includeDirectories ${index})
        list(APPEND plugin_include_directories "${MLN_PLUGIN_ROOT}/${include_directory}")
    endforeach()
endif()

if(MLN_PLUGIN_VERSION_OVERRIDE)
    set(plugin_version "${MLN_PLUGIN_VERSION_OVERRIDE}")
endif()

set(MLN_WITH_PLUGINS ON CACHE BOOL "" FORCE)
set(MLN_WITH_GLFW OFF CACHE BOOL "" FORCE)
set(MLN_WITH_WERROR OFF CACHE BOOL "" FORCE)
add_subdirectory("${MAPLIBRE_NATIVE_SOURCE_DIR}" "${CMAKE_BINARY_DIR}/maplibre-native" EXCLUDE_FROM_ALL)

# The registration translation unit is generated, so plugins need no render-test code.
set(MLN_PLUGIN_HEADER "${MLN_PLUGIN_ROOT}/${plugin_header}")
set(MLN_PLUGIN_REGISTER_FUNCTION "${plugin_register_function}")
configure_file(register.cpp.in "${CMAKE_CURRENT_BINARY_DIR}/generated/register.cpp" @ONLY)

add_executable(plugin-render-tests main.cpp "${CMAKE_CURRENT_BINARY_DIR}/generated/register.cpp" ${plugin_sources})
target_compile_features(plugin-render-tests PRIVATE cxx_std_20)
target_include_directories(plugin-render-tests PRIVATE ${plugin_include_directories})
target_compile_definitions(
    plugin-render-tests
    PRIVATE
        MLN_PLUGIN_VERSION="${plugin_version}"
        MLN_PLUGIN_RENDER_TEST_MANIFEST="${MLN_PLUGIN_ROOT}/${plugin_render_tests}/manifest.json"
)
target_link_libraries(plugin-render-tests PRIVATE mbgl-render-test)

message(STATUS "Plugin render tests: ${plugin_id} ${plugin_version} (${MLN_PLUGIN_ROOT}/${plugin_render_tests})")
