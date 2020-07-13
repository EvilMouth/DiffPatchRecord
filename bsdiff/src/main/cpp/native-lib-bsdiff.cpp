#include <jni.h>
#include <string>

extern "C" {
int diff(int argc, char *argv[]);
int patch(int argc, char *argv[]);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_zyhang_bsdiff_BSDiff_diff(JNIEnv *env, jobject /* this */, jstring old_apk_path,
                                jstring new_apk_path, jstring patch_output_path) {
    const char *oldApkPath = env->GetStringUTFChars(old_apk_path, nullptr);
    const char *newApkPath = env->GetStringUTFChars(new_apk_path, nullptr);
    const char *patchOutputPath = env->GetStringUTFChars(patch_output_path, nullptr);
    const char *arg[] = {"bsdiff", oldApkPath, newApkPath, patchOutputPath};
    return diff(4, const_cast<char **>(arg));
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_zyhang_bsdiff_BSDiff_patch(JNIEnv *env, jobject /* this */, jstring old_apk_path,
                                 jstring patch_path, jstring new_apk_output_path) {
    const char *oldApkPath = env->GetStringUTFChars(old_apk_path, nullptr);
    const char *patchPath = env->GetStringUTFChars(patch_path, nullptr);
    const char *newApkOutputPath = env->GetStringUTFChars(new_apk_output_path, nullptr);
    const char *arg[] = {"bspatch", oldApkPath, newApkOutputPath, patchPath};
    return patch(4, const_cast<char **>(arg));
}