package com.vipercn.viper4android_v2.activity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.util.Log;

public class IrsUtils {
    public static long hashIrs(byte[] mArray, int mLength) {
        if (mArray == null)
            return 0;
        if (mArray.length < mLength)
            return 0;
        if (mLength <= 0)
            return 0;

        // Generate CRC table
        long[] crcTable = new long[256];
        for (int i = 0; i < 256; i++) {
            long crcTblVal = i;
            for (int j = 8; j > 0; j--) {
                if ((crcTblVal & 0x01) != 0)
                    crcTblVal = (crcTblVal >> 1) ^ 0xEDB88320L;
                else
                    crcTblVal >>= 1;
            }
            crcTable[i] = crcTblVal;
        }

        // Hash array
        long crcResult = 0xFFFFFFFF;
        for (int i = 0; i < mLength; i++) {
            long mData = (long) (mArray[i] & 0xFF);
            int tableIndex = (int) (crcResult ^ mData) & 0xFF;
            crcResult = ((crcResult >> 8) & 0x00FFFFFF) ^ crcTable[tableIndex];
        }
        return ~crcResult;
    }

    private static final int WAV_HEADER_CHUNK_ID = 0x52494646; // "RIFF"
    private static final int WAV_FORMAT = 0x57415645; // "WAVE"
    private static final int WAV_FORMAT_CHUNK_ID = 0x666d7420; // "fmt "
    private static final int WAV_DATA_CHUNK_ID = 0x64617461; // "data"

    private FileInputStream m_fsiIRSStream = null;
    private BufferedInputStream m_bisInputStream = null;

    private long m_nSamplesCount = 0;
    private long m_nBytesCount = 0;
    private int m_nChannels = 0;
    // 0: Unknown, 1: s16le, 2: s24le, 3: s32le, 4: f32
    private int m_nSampleType = 0;
    private int m_nSampleBits = 0;

    public IrsUtils() {
        m_fsiIRSStream = null;
        m_bisInputStream = null;
    }

    protected void finalize() {
        Release();
    }

    public void Release() {
        if (m_bisInputStream != null) {
            try {
                m_bisInputStream.close();
            } catch (IOException e) {
            }
            m_bisInputStream = null;
        }
        if (m_fsiIRSStream != null) {
            try {
                m_fsiIRSStream.close();
            } catch (IOException e) {
            }
            m_fsiIRSStream = null;
        }
    }

    public boolean loadIRS(String mIRSPathName) {
        if (mIRSPathName == null)
            return false;
        if (mIRSPathName.equals(""))
            return false;
        if (!(new File(mIRSPathName).exists()))
            return false;
        Release();

        // Open irs file
        try {
            m_fsiIRSStream = new FileInputStream(mIRSPathName);
        } catch (FileNotFoundException e) {
            m_fsiIRSStream = null;
            m_bisInputStream = null;
            Log.i("ViPER4Android",
                    "loadIRS, FileNotFoundException, msg = " + e.getMessage());
            return false;
        }
        long m_nFileLength = new File(mIRSPathName).length();
        if (m_nFileLength <= 16) {
            Release();
            return false;
        }

        // Read file header
        m_bisInputStream = new BufferedInputStream(m_fsiIRSStream, 4096);
        int headerId = readUnsignedInt(m_bisInputStream);
        if (headerId != WAV_HEADER_CHUNK_ID) {
            Release();
            return false;
        }
        m_nFileLength = readUnsignedIntLE(m_bisInputStream);
        if (m_nFileLength <= 16) {
            Release();
            return false;
        }
        int mFormat = readUnsignedInt(m_bisInputStream);
        if (mFormat != WAV_FORMAT) {
            Release();
            return false;
        }

        // Read wave header
        int mFormatId = readUnsignedInt(m_bisInputStream);
        if (mFormatId != WAV_FORMAT_CHUNK_ID) {
            Release();
            return false;
        }
        int mFormatSize = readUnsignedIntLE(m_bisInputStream);
        if (mFormatSize < 16) {
            Release();
            return false;
        }
        int mAudioFormat = readUnsignedShortLE(m_bisInputStream);
        if ((mAudioFormat != 0x0001) && (mAudioFormat != 0x0003)) {
            // We only accept WINDOWS_PCM_WAV and PCM_IEEE_FLOAT
            Release();
            return false;
        }
        m_nChannels = readUnsignedShortLE(m_bisInputStream);
        if ((m_nChannels < 1) || (m_nChannels > 2)) {
            // We only accept mono and stereo
            Release();
            return false;
        }
        int mSampleRate = readUnsignedIntLE(m_bisInputStream);
        if ((mSampleRate < 8000) || (mSampleRate > 192000)) {
            // We only accept standard sampling rate
            Release();
            return false;
        }
        int mByteRate = readUnsignedIntLE(m_bisInputStream);
        Log.i("ViPER4Android", "IRS byterate = " + mByteRate);
        int nBlockAlign = readUnsignedShortLE(m_bisInputStream);
        Log.i("ViPER4Android", "IRS blockalign = " + nBlockAlign);
        m_nSampleBits = readUnsignedShortLE(m_bisInputStream);
        // Calculate sample type
        {
            m_nSampleType = 0;
            if (mAudioFormat == 0x0001) {
                if (m_nSampleBits == 16)
                    m_nSampleType = 1;
                else if (m_nSampleBits == 24)
                    m_nSampleType = 2;
                else if (m_nSampleBits == 32)
                    m_nSampleType = 3;
                else {
                    // We only accept s16le, s24le and s32le in integer format
                    Release();
                    return false;
                }
            } else {
                if (m_nSampleBits == 32)
                    m_nSampleType = 4;
                else {
                    // We only accept f32 in floating format
                    Release();
                    return false;
                }
            }
        }

        // Read data header
        int mDataId = readUnsignedInt(m_bisInputStream);
        if (mDataId != WAV_DATA_CHUNK_ID) {
            Release();
            return false;
        }
        int mDataSize = readUnsignedIntLE(m_bisInputStream);
        if ((mDataSize <= 0) || (mDataSize > 4194304)) {
            // Too many data, may cause dalvik exception
            Release();
            return false;
        }

        // Calculate samples count
        {
            m_nBytesCount = mDataSize;
            m_nSamplesCount = m_nBytesCount / m_nChannels / (m_nSampleBits / 8);
            if (m_nSamplesCount < 16) {
                // Convolver needs at least 16 samples
                Release();
                return false;
            }
            if (m_nBytesCount % (m_nChannels * (m_nSampleBits / 8)) != 0) {
                Release();
                return false;
            }
        }

        Log.i("ViPER4Android", "IRS [" + mIRSPathName + "] opened");
        Log.i("ViPER4Android", "IRS attr = [" + m_nSampleType + ","
                + m_nChannels + "," + m_nSamplesCount + "]");

        return true;
    }

    public byte[] readEntireData() {
        if ((m_bisInputStream == null) || (m_fsiIRSStream == null))
            return null;
        if ((m_nSampleType < 1) || (m_nSampleType > 4))
            return null;

        // Read raw bytes
        byte[] mData = new byte[4096];
        int mReadLength = 0;
        while (true) {
            try {
                int nRead = m_bisInputStream.read(mData, mReadLength, 4096);
                if (nRead < 0)
                    break;
                mReadLength += nRead;
                // Realloc for next
                byte[] newData = new byte[mReadLength + 4096];
                System.arraycopy(mData, 0, newData, 0, mReadLength);
                mData = newData;
            } catch (IOException e) {
                break;
            }
        }

        // Arrange byte array
        byte[] newData = new byte[mReadLength];
        System.arraycopy(mData, 0, newData, 0, mReadLength);
        mData = newData;

        // Update samples count according to read result
        if (m_nBytesCount > mData.length) {
            // If we got less data then header described, then use what we read
            m_nBytesCount = mData.length;
            m_nSamplesCount = m_nBytesCount / m_nChannels / (m_nSampleBits / 8);
            if (m_nBytesCount % (m_nChannels * (m_nSampleBits / 8)) != 0) {
                Release();
                return null;
            }
        } else if (m_nBytesCount < mData.length) {
            // If we got more data then header described, then use header
            // described
            Log.i("ViPER4Android",
                    "IrsUtils: We got some garbage data, header = "
                            + m_nBytesCount + ", read = " + mData.length);
            Log.i("ViPER4Android",
                    "IrsUtils: So lets discard some data, length = "
                            + (mData.length - m_nBytesCount));
            byte[] mActualData = new byte[(int) m_nBytesCount];
            System.arraycopy(mData, 0, mActualData, 0, (int) m_nBytesCount);
            mData = mActualData;
        }

        // Convert format
        switch (m_nSampleType) {
            case 1:
                return convert_S16LE_F32(mData);
            case 2:
                return convert_S24LE_F32(mData);
            case 3:
                return Convert_S32LE_F32(mData);
        }

        return mData;
    }

    public int getChannels() {
        return m_nChannels;
    }

    public int getSampleCount() {
        return (int) m_nSamplesCount;
    }

    public int getByteCount() {
        return (int) m_nBytesCount;
    }

    private static byte[] convert_S16LE_F32(byte[] s16LEData) {
        int mSamplesCount = s16LEData.length / 2; // 2 means sizeof(short)
        byte[] f32Data = new byte[mSamplesCount * 4]; // 4 means sizeof(float)
        double invscale = 0.000030517578125;

        ByteBuffer mS16Buffer = ByteBuffer.wrap(s16LEData);
        ByteBuffer mF32Buffer = ByteBuffer.wrap(f32Data);
        mS16Buffer.order(ByteOrder.nativeOrder());
        mF32Buffer.order(ByteOrder.nativeOrder());
        for (int i = 0; i < mSamplesCount; i++) {
            short s16 = mS16Buffer.getShort();
            float f32 = (float) (s16 * invscale);
            mF32Buffer.putFloat(f32);
        }
        return f32Data;
    }

    private static byte[] convert_S24LE_F32(byte[] s24LEData) {
        int mSamplesCount = s24LEData.length / 3; // 2 means sizeof(int24)
        byte[] f32Data = new byte[mSamplesCount * 4]; // 4 means sizeof(float)
        double invscale = 0.00000011920928955078125;

        ByteBuffer mF32Buffer = ByteBuffer.wrap(f32Data);
        mF32Buffer.order(ByteOrder.nativeOrder());
        for (int i = 0, idx = 0; i < mSamplesCount; i++, idx += 3) {
            byte s24_b1 = s24LEData[idx];
            byte s24_b2 = s24LEData[idx + 1];
            byte s24_b3 = s24LEData[idx + 2];
            int s24 = s24_b1 & 0xFF | ((s24_b2 & 0xFF) << 8) | ((s24_b3 & 0xFF) << 16);
            if (s24 > 0x7FFFFF) {
                s24 &= 0x7FFFFF;
                s24 = 0x7FFFFF - s24;
                s24 = -s24;
            }
            float f32 = (float) (s24 * invscale);
            mF32Buffer.putFloat(f32);
        }
        return f32Data;
    }

    private static byte[] Convert_S32LE_F32(byte[] s32LEData) {
        int mSamplesCount = s32LEData.length / 4; // 2 means sizeof(int)
        byte[] f32Data = new byte[mSamplesCount * 4]; // 4 means sizeof(float)
        double invscale = 0.0000000004656612873077392578125;

        ByteBuffer mS32Buffer = ByteBuffer.wrap(s32LEData);
        ByteBuffer mF32Buffer = ByteBuffer.wrap(f32Data);
        mS32Buffer.order(ByteOrder.nativeOrder());
        mF32Buffer.order(ByteOrder.nativeOrder());
        for (int i = 0; i < mSamplesCount; i++) {
            int s32 = mS32Buffer.getInt();
            float f32 = (float) (s32 * invscale);
            mF32Buffer.putFloat(f32);
        }
        return f32Data;
    }

    private static short byteToShortLE(byte b1, byte b2) {
        return (short) (b1 & 0xFF | ((b2 & 0xFF) << 8));
    }

    private static int readUnsignedInt(BufferedInputStream bufferInput) {
        byte[] buffer = new byte[4];
        int dwReturn;
        try {
            dwReturn = bufferInput.read(buffer);
        } catch (IOException e) {
            return 0;
        }
        if (dwReturn == -1)
            return -1;
        else {
            return (((buffer[0] & 0xFF) << 24) | ((buffer[1] & 0xFF) << 16)
                    | ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF));
        }
    }

    private static int readUnsignedIntLE(BufferedInputStream bufferInput) {
        byte[] buffer = new byte[4];
        int dwReturn;
        try {
            dwReturn = bufferInput.read(buffer);
        } catch (IOException e) {
            return 0;
        }
        if (dwReturn == -1)
            return -1;
        else {
            return ((buffer[0] & 0xFF) | ((buffer[1] & 0xFF) << 8)
                    | ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24));
        }
    }

    private static short readUnsignedShortLE(BufferedInputStream bufferInput) {
        byte[] buffer = new byte[2];
        int dwReturn;
        try {
            dwReturn = bufferInput.read(buffer, 0, 2);
        } catch (IOException e) {
            return 0;
        }
        if (dwReturn == -1)
            return -1;
        else
            return byteToShortLE(buffer[0], buffer[1]);
    }
}
