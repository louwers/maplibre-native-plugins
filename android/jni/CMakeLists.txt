# Builds a plugin's shared library for Android. Configured by the
# org.maplibre.native-plugin Gradle plugin from plugin.json.
cmake_minimum_required(VERSION 3.22.1)
project(maplibre_native_plugin LANGUAGES C CXX)

foreach(variable MLN_PLUGIN_LIBRARY_NAME MLN_PLUGIN_SOURCES MLN_PLUGIN_HEADER
                 MLN_PLUGIN_REGISTER_FUNCTION MLN_PLUGIN_JAVA_CLASS MLN_PLUGIN_VERSION)
    if(NOT DEFINED ${variable})
        message(FATAL_ERROR "${variable} is required; build through the org.maplibre.native-plugin Gradle plugin")
    endif()
endforeach()

# Header-only Prefab module from the MapLibre SDK AAR with <mln/plugin/plugin_api.h>.
# Plugins never link MapLibre; they find mln_plugin_register_v1 at runtime.
find_package(MapLibreAndroid REQUIRED CONFIG)

add_library(${MLN_PLUGIN_LIBRARY_NAME} SHARED ${MLN_PLUGIN_SOURCES} "${CMAKE_CURRENT_LIST_DIR}/plugin_jni.cpp")
target_compile_features(${MLN_PLUGIN_LIBRARY_NAME} PRIVATE cxx_std_20)
set_target_properties(${MLN_PLUGIN_LIBRARY_NAME} PROPERTIES
    C_VISIBILITY_PRESET hidden
    CXX_VISIBILITY_PRESET hidden
)
target_include_directories(${MLN_PLUGIN_LIBRARY_NAME} PRIVATE ${MLN_PLUGIN_INCLUDE_DIRECTORIES})
target_compile_definitions(${MLN_PLUGIN_LIBRARY_NAME} PRIVATE
    MLN_PLUGIN_VERSION="${MLN_PLUGIN_VERSION}"
    MLN_PLUGIN_HEADER="${MLN_PLUGIN_HEADER}"
    MLN_PLUGIN_REGISTER_FUNCTION=${MLN_PLUGIN_REGISTER_FUNCTION}
    MLN_PLUGIN_JAVA_CLASS="${MLN_PLUGIN_JAVA_CLASS}"
)
target_link_libraries(${MLN_PLUGIN_LIBRARY_NAME} PRIVATE MapLibreAndroid::maplibre android dl log)

# Keep the statically linked C++ runtime private, and support 16 KB page sizes.
target_link_options(${MLN_PLUGIN_LIBRARY_NAME} PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,-z,max-page-size=16384")
