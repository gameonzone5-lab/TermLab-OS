#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_autokaaj_termlab_MainActivity_stringFromJNI(JNIEnv* env, jobject /* this */) {
    std::string hello = "[C++ Core] Native NDK is successfully connected!\nWaiting for PTY execution...";
    return env->NewStringUTF(hello.c_str());
}
