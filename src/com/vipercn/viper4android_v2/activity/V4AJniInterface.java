package com.vipercn.viper4android_v2.activity;

import android.util.Log;

import java.nio.charset.Charset;

public class V4AJniInterface {
    private static boolean m_JniLoadOK = false;

    static {
        try {
            System.loadLibrary("V4AJniUtils");
            m_JniLoadOK = true;
            Log.i("ViPER4Android_Utils", "libV4AJniUtils.so loaded");
        } catch (UnsatisfiedLinkError e) {
            m_JniLoadOK = false;
            Log.e("ViPER4Android_Utils", "[Fatal] Can't load libV4AJniUtils.so");
        }
    }

    /* Library Check Utils */
    private native static int checkLibraryUsable();

    /* CPU Check Utils */
    private native static int checkCPUHasNEON();
    private native static int checkCPUHasVFP();

    /* Impulse Response Utils */
    private native static int[] getImpulseResponseInfo(byte[] mIRFileName);
    private native static byte[] readImpulseResponse(byte[] mIRFileName);
    private native static int[] hashImpulseResponse(byte[] mBuffer, int mBufferSize);

    /* This method is just making sure jni has been loaded */
    public static boolean checkLibrary() {
        if (!m_JniLoadOK) return false;
        int mUsable = checkLibraryUsable();
        return mUsable == 1;
    }

    public static boolean isLibraryUsable() {
        return m_JniLoadOK;
    }

    public static boolean isCPUSupportNEON() {
        if (!m_JniLoadOK) return false;
        int result = checkCPUHasNEON();
        Log.i("ViPER4Android_Utils", "CpuInfo[jni] = NEON:" + result);
        if (result == 0) return false;
        return true;
    }

    public static boolean isCPUSupportVFP() {
        if (!m_JniLoadOK) return false;
        int result = checkCPUHasVFP();
        Log.i("ViPER4Android_Utils", "CpuInfo[jni] = VFP:" + result);
        if (result == 0) return false;
        return true;
    }

    public static int[] getImpulseResponseInfoArray(String mIRFileName) {
        if (!m_JniLoadOK) return null;
        // Convert unicode string to multi-byte string
        byte[] stringBytes = mIRFileName.getBytes(Charset.forName("US-ASCII"));
        if (stringBytes == null) return null;
        // Call native
        return getImpulseResponseInfo(stringBytes);
    }

    public static byte[] readImpulseResponseToArray(String mIRFileName) {
        if (!m_JniLoadOK) return null;
        // Convert unicode string to multi-byte string
        byte[] stringBytes = mIRFileName.getBytes(Charset.forName("US-ASCII"));
        if (stringBytes == null) return null;
        // Call native
        return readImpulseResponse(stringBytes);
    }

    public static int[] getHashImpulseResponseArray(byte[] mBuffer) {
        if (!m_JniLoadOK) return null;
        if (mBuffer == null) return null;
        // Call native
        return hashImpulseResponse(mBuffer, mBuffer.length);
    }
}
