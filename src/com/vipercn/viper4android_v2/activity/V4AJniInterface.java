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

    /* CPU Check Utils */
    private native static int checkCPUHasNEON();

    private native static int checkCPUHasVFP();

    /* Impulse Response Utils */
    private native static int[] getImpulseResponseInfo(byte[] szIRFileName);

    private native static byte[] readImpulseResponse(byte[] szIRFileName);

    private native static int[] hashImpulseResponse(byte[] baBuffer, int nBufferSize);

    public static boolean isCPUSupportNEON() {
        if (!m_JniLoadOK) return false;
        int nResult = checkCPUHasNEON();
        Log.i("ViPER4Android_Utils", "CpuInfo[jni] = NEON:" + nResult);
        if (nResult == 0) return false;
        return true;
    }

    public static boolean isCPUSupportVFP() {
        if (!m_JniLoadOK) return false;
        int nResult = checkCPUHasVFP();
        Log.i("ViPER4Android_Utils", "CpuInfo[jni] = VFP:" + nResult);
        if (nResult == 0) return false;
        return true;
    }

    public static int[] getImpulseResponseInfoArray(String szIRFileName) {
        // Convert unicode string to multi-byte string
        byte[] stringBytes = szIRFileName.getBytes(Charset.forName("US-ASCII"));
        if (stringBytes == null) return null;
        // Call native
        return getImpulseResponseInfo(stringBytes);
    }

    public static byte[] readImpulseResponseToArray(String szIRFileName) {
        // Convert unicode string to multi-byte string
        byte[] stringBytes = szIRFileName.getBytes(Charset.forName("US-ASCII"));
        if (stringBytes == null) return null;
        // Call native
        return readImpulseResponse(stringBytes);
    }

    public static int[] getHashImpulseResponseArray(byte[] baBuffer) {
        if (baBuffer == null) return null;
        // Call native
        return hashImpulseResponse(baBuffer, baBuffer.length);
    }
}
