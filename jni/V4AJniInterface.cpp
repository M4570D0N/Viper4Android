#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <cpu-features.h>
#include <android/log.h>
#include "V4AJniInterface.h"
#include "sndfile.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ViPER4Android_v2", __VA_ARGS__)

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("Loading JNI ...");
    return JNI_VERSION_1_1;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("Unloading JNI ...");
}

JNIEXPORT jint JNICALL Java_com_vipercn_viper4android_1v2_activity_V4AJniInterface_CheckLibraryUsable (
    JNIEnv *env, jclass cls) {
        return (jint)1;
}

JNIEXPORT jint JNICALL Java_com_vipercn_viper4android_1v2_activity_V4AJniInterface_CheckCPUHasNEON (
    JNIEnv *env, jclass cls) {
    if (android_getCpuFamily() != ANDROID_CPU_FAMILY_ARM) return (jint)0;
    uint64_t uiCPUFeatures = android_getCpuFeatures();
    if ((uiCPUFeatures & ANDROID_CPU_ARM_FEATURE_NEON) == 0) return (jint)0;
    return (jint)1;
}

JNIEXPORT jint JNICALL Java_com_vipercn_viper4android_1v2_activity_V4AJniInterface_CheckCPUHasVFP (
    JNIEnv *env, jclass cls) {
    if (android_getCpuFamily() != ANDROID_CPU_FAMILY_ARM) return (jint)0;
    uint64_t uiCPUFeatures = android_getCpuFeatures();
    if ((uiCPUFeatures & ANDROID_CPU_ARM_FEATURE_ARMv7) == 0) {
        if ((uiCPUFeatures & ANDROID_CPU_ARM_FEATURE_VFPv2) == 0) return (jint)0;
            return (jint)1;
    } else {
        if ((uiCPUFeatures & ANDROID_CPU_ARM_FEATURE_VFPv3) == 0) return (jint)0;
            return (jint)1;
    }
}

JNIEXPORT jintArray JNICALL Java_com_vipercn_viper4android_1v2_activity_V4AJniInterface_GetImpulseResponseInfo (
    JNIEnv *env, jclass cls, jbyteArray jbaIrFileName ) {
    /* return: [0] = Valid, [1] = Channels, [2] = Frames, [3] = Byte Length */

    // Get multi-bytes string
    jsize mIrFileNameLength = env->GetArrayLength(jbaIrFileName);
    if (mIrFileNameLength > 4095) return NULL;
    jbyte *pIRFileNameBuffer = env->GetByteArrayElements(jbaIrFileName, 0);
    if (pIRFileNameBuffer == NULL) return NULL;
    char mIrFileName[4096];
    memset(mIrFileName, 0, sizeof(mIRFileName));
    memcpy(mIrFileName, pIRFileNameBuffer, mIrFileNameLength);
    env->ReleaseByteArrayElements(jbaIrFileName, pIRFileNameBuffer, 0);
    if (strlen(mIrFileName) <= 0) return NULL;

    // Prepare return array
    jint iaIRInfo[4] = {0, 0, 0, 0};
    jintArray jiaIRInfo = env->NewIntArray(4);
    if (jiaIRInfo == NULL) return NULL;
    env->SetIntArrayRegion(jiaIRInfo, 0, 4, iaIRInfo);

    // Lets deal with mIRFileName, use libsndfile
    SF_INFO sfiIRInfo;
    memset(&sfiIRInfo, 0, sizeof(SF_INFO));
    SNDFILE *sfIRFile = sf_open(mIRFileName, SFM_READ, &sfiIRInfo);
    if (sfIRFile == NULL) {
        // Open failed or invalid wave file
        return jiaIRInfo;
    }
    sf_close(sfIRFile);  /* We only expect SF_INFO here */

    // Sanity check
    if ((sfiIRInfo.channels != 1) && (sfiIRInfo.channels != 2)) {
        // Convolver supports mono or stereo ir for now
        return jiaIRInfo;
    }
    if ((sfiIRInfo.samplerate <= 0) || (sfiIRInfo.frames <= 0)) {
        // Negative sampling rate or empty data ?
        return jiaIRInfo;
    }

    // Everything fine here, just make a nice return
    iaIRInfo[0] = 1;
    iaIRInfo[1] = (jint)sfiIRInfo.channels;
    /* Note: APK needs a total frame count, means element count in buffer */
    iaIRInfo[2] = (jint)(sfiIRInfo.frames * sfiIRInfo.channels);
    /* Note: element type is native float */
    iaIRInfo[3] = (jint)(sfiIRInfo.frames * sfiIRInfo.channels * sizeof(float));
    env->SetIntArrayRegion(jiaIRInfo, 0, 4, iaIRInfo);  /* Copy again */
    return jiaIRInfo;
}

JNIEXPORT jbyteArray JNICALL Java_com_vipercn_viper4android_1v2_activity_V4AJniInterface_ReadImpulseResponse (
    JNIEnv *env, jclass cls, jbyteArray jIrFileName) {
    // Get multi-bytes string
    jsize mIrFileNameLength = env->GetArrayLength(jIrFileName);
    if (mIrFileNameLength > 4095) return NULL;
    jbyte *pIrFileNameBuffer = env->GetByteArrayElements(jIrFileName, 0);
    if (pIrFileNameBuffer == NULL) return NULL;
    char mIrFileName[4096];
    memset(mIrFileName, 0, sizeof(mIrFileName));
    memcpy(mIrFileName, pIrFileNameBuffer, mIrFileNameLength);
    env->ReleaseByteArrayElements(jIrFileName, pIrFileNameBuffer, 0);
    if (strlen(mIrFileName) <= 0) return NULL;

    // Lets deal with mIrFileName, use libsndfile
    SF_INFO sfiIRInfo;
    memset(&sfiIRInfo, 0, sizeof(SF_INFO));
    SNDFILE *sfIRFile = sf_open(mIrFileName, SFM_READ, &sfiIRInfo);
    if (sfIRFile == NULL) {
        // Open failed or invalid wave file
        return NULL;
    }

    // Sanity check
    if ((sfiIRInfo.channels != 1) && (sfiIRInfo.channels != 2)) {
        // Convolver supports mono or stereo ir for now
        sf_close(sfIRFile);
        return NULL;
    }
    if ((sfiIRInfo.samplerate <= 0) || (sfiIRInfo.frames <= 0)) {
        // Negative sampling rate or empty data ?
        sf_close(sfIRFile);
        return NULL;
    }

    // Allocate memory block for reading
    float *pFrameBuffer = new float[sfiIRInfo.channels * sfiIRInfo.frames];
    if (pFrameBuffer == NULL) {
        // Memory not enough
        sf_close(sfIRFile);
        return NULL;
    }
    sf_count_t readFrames = sf_readf_float(sfIRFile, pFrameBuffer, sfiIRInfo.frames);
    sf_close(sfIRFile);

    // Sanity check
    if (readFrames != sfiIRInfo.frames) {
        delete [] pFrameBuffer;
        return NULL;
    }

    // Prepare return array
    jsize jsFrameBufferSize = sfiIRInfo.frames * sfiIRInfo.channels * sizeof(float);
    jbyteArray jFrames = env->NewByteArray(jsFrameBufferSize);
    if (jFrames == NULL) {
        delete [] pFrameBuffer;
        return NULL;
    }
    const jbyte *jbBufferPointer = (const jbyte *)pFrameBuffer;
    env->SetByteArrayRegion(jFrames, 0, jsFrameBufferSize, jbBufferPointer);
    delete [] pFrameBuffer;
    return jFrames;
}

extern uint32_t HashCRC32(uint8_t *ucpBuffer, uint32_t uiBufferSize);
JNIEXPORT jintArray JNICALL Java_com_vipercn_viper4android_1v2_activity_V4AJniInterface_HashImpulseResponse (
    JNIEnv *env, jclass cls, jbyteArray jBuffer, jint nBufferSize) {
    /* return: [0] = Valid, [2] = Hash Code */

    // Extract input buffer
    if (nBufferSize <= 0) return NULL;
    jsize nRealBufferLength = env->GetArrayLength(jBuffer);
    if (nRealBufferLength != nBufferSize) return NULL;
    jbyte *mBuffer = env->GetByteArrayElements(jBuffer, 0);
    if (mBuffer == NULL) return NULL;

    // Calc hash
    uint32_t uiHash = HashCRC32((uint8_t *)mBuffer, (uint32_t)nBufferSize);

    // Release resources
    env->ReleaseByteArrayElements(jBuffer, mBuffer, 0);

    // Prepare return array
    jintArray jHashInfo = env->NewIntArray(2);
    if (jHashInfo == NULL) return NULL;
    jint iaHashInfo[2] = {1, (jint)uiHash};
    env->SetIntArrayRegion(jHashInfo, 0, 2, iaHashInfo);
    return jHashInfo;
}
