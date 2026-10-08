// Generic JNI glue for MapLibre Native plugins. JNI_OnLoad binds nativeRegister to the
// generated Java registration class, so plugins need no JNI code of their own.
#include <jni.h>

#include <dlfcn.h>

#include <cstdio>

#include MLN_PLUGIN_HEADER

namespace {

jint nativeRegister(JNIEnv* env, jclass, jobjectArray message) {
    char error[512]{};
    mln_plugin_status status = MLN_PLUGIN_STATUS_NOT_FOUND;

    // Resolve the entry point from the renderer MapLibre already loaded; never load a second copy.
    // Multi-backend SDKs load the OpenGL renderer as libmaplibre-opengl.so, all others as libmaplibre.so.
    constexpr const char* libraries[] = {"libmaplibre-opengl.so", "libmaplibre.so"};
    void* host = nullptr;
    for (const char* library : libraries) {
        if ((host = dlopen(library, RTLD_NOW | RTLD_NOLOAD))) break;
    }
    if (!host) {
        std::snprintf(error, sizeof(error), "MapLibre is not loaded; call MapLibre.getInstance() first");
    } else if (auto* registerPlugin = reinterpret_cast<mln_plugin_register_function_v1>(
                   dlsym(host, "mln_plugin_register_v1"))) {
        status = MLN_PLUGIN_REGISTER_FUNCTION(registerPlugin, error, sizeof(error));
    } else {
        std::snprintf(error, sizeof(error), "This MapLibre SDK build does not enable the plugin API");
    }
    if (host) dlclose(host); // MapLibre keeps its own reference for the process lifetime.

    if (message && env->GetArrayLength(message) > 0) {
        jstring text = env->NewStringUTF(error);
        env->SetObjectArrayElement(message, 0, text);
        env->DeleteLocalRef(text);
    }
    return static_cast<jint>(status);
}

} // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass type = env->FindClass(MLN_PLUGIN_JAVA_CLASS);
    if (!type) return JNI_ERR;
    const JNINativeMethod methods[] = {
        {"nativeRegister", "([Ljava/lang/String;)I", reinterpret_cast<void*>(&nativeRegister)},
    };
    const jint result = env->RegisterNatives(type, methods, sizeof(methods) / sizeof(methods[0]));
    env->DeleteLocalRef(type);
    return result == JNI_OK ? JNI_VERSION_1_6 : JNI_ERR;
}
