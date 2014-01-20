package com.vipercn.viper4android_v2.activity;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
                    crcTblVal = crcTblVal >> 1 ^ 0xEDB88320L;
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
            crcResult = crcResult >> 8 & 0x00FFFFFF ^ crcTable[tableIndex];
        }
        return ~crcResult;
    }

    private static final int WAV_HEADER_CHUNK_ID = 0x52494646; // "RIFF"
    private static final int WAV_FORMAT = 0x57415645; // "WAVE"
    private static final int WAV_FORMAT_CHUNK_ID = 0x666d7420; // "fmt "
    private static final int WAV_DATA_CHUNK_ID = 0x64617461; // "data"

    private FileInputStream mIrsStream;
    private BufferedInputStream bufferInput;

    private long mSamplesCount;
    private long mBytesCount;
    private int mChannels;
    // 0: Unknown, 1: s16le, 2: s24le, 3: s32le, 4: f32
    private int mSampleType;
    private int mSampleBits;

    public IrsUtils() {
        mIrsStream = null;
        bufferInput = null;
    }

    protected void finalize() {
        Release();
    }

    public void Release() {
        if (bufferInput != null) {
            try {
                bufferInput.close();
            } catch (IOException e) {
            }
            bufferInput = null;
        }
        if (mIrsStream != null) {
            try {
                mIrsStream.close();
            } catch (IOException e) {
            }
            mIrsStream = null;
        }
    }

    public boolean loadIRS(String mIrsPathName) {
        if (mIrsPathName == null)
            return false;
        if (mIrsPathName.equals(""))
            return false;
        if (!new File(mIrsPathName).exists())
            return false;
        Release();

        // Open irs file
        try {
            mIrsStream = new FileInputStream(mIrsPathName);
        } catch (FileNotFoundException e) {
            mIrsStream = null;
            bufferInput = null;
            Log.i("ViPER4Android",
                    "loadIRS, FileNotFoundException, msg = " + e.getMessage());
            return false;
        }
        long m_mFileLength = new File(mIrsPathName).length();
        if (m_mFileLength <= 16) {
            Release();
            return false;
        }

        // Read file header
        bufferInput = new BufferedInputStream(mIrsStream, 4096);
        int headerId = readUnsignedInt(bufferInput);
        if (headerId != WAV_HEADER_CHUNK_ID) {
            Release();
            return false;
        }
        m_mFileLength = readUnsignedIntLE(bufferInput);
        if (m_mFileLength <= 16) {
            Release();
            return false;
        }
        int mFormat = readUnsignedInt(bufferInput);
        if (mFormat != WAV_FORMAT) {
            Release();
            return false;
        }

        // Read wave header
        int mFormatId = readUnsignedInt(bufferInput);
        if (mFormatId != WAV_FORMAT_CHUNK_ID) {
            Release();
            return false;
        }
        int mFormatSize = readUnsignedIntLE(bufferInput);
        if (mFormatSize < 16) {
            Release();
            return false;
        }
        int mAudioFormat = readUnsignedShortLE(bufferInput);
        if (mAudioFormat != 0x0001 && mAudioFormat != 0x0003) {
            // We only accept WINDOWS_PCM_WAV and PCM_IEEE_FLOAT
            Release();
            return false;
        }
        mChannels = readUnsignedShortLE(bufferInput);
        if (mChannels < 1 || mChannels > 2) {
            // We only accept mono and stereo
            Release();
            return false;
        }
        int mSampleRate = readUnsignedIntLE(bufferInput);
        if (mSampleRate < 8000 || mSampleRate > 192000) {
            // We only accept standard sampling rate
            Release();
            return false;
        }
        int mByteRate = readUnsignedIntLE(bufferInput);
        Log.i("ViPER4Android", "IRS byterate = " + mByteRate);
        int nBlockAlign = readUnsignedShortLE(bufferInput);
        Log.i("ViPER4Android", "IRS blockalign = " + nBlockAlign);
        mSampleBits = readUnsignedShortLE(bufferInput);
        // Calculate sample type
        {
            mSampleType = 0;
            if (mAudioFormat == 0x0001) {
                if (mSampleBits == 16)
                    mSampleType = 1;
                else if (mSampleBits == 24)
                    mSampleType = 2;
                else if (mSampleBits == 32)
                    mSampleType = 3;
                else {
                    // We only accept s16le, s24le and s32le in integer format
                    Release();
                    return false;
                }
            } else {
                if (mSampleBits == 32)
                    mSampleType = 4;
                else {
                    // We only accept f32 in floating format
                    Release();
                    return false;
                }
            }
        }

        // Read data header
        int mDataId = readUnsignedInt(bufferInput);
        if (mDataId != WAV_DATA_CHUNK_ID) {
            Release();
            return false;
        }
        int mDataSize = readUnsignedIntLE(bufferInput);
        if (mDataSize <= 0 || mDataSize > 4194304) {
            // Too many data, may cause dalvik exception
            Release();
            return false;
        }

        // Calculate samples count
        {
            mBytesCount = mDataSize;
            mSamplesCount = mBytesCount / mChannels / (mSampleBits / 8);
            if (mSamplesCount < 16) {
                // Convolver needs at least 16 samples
                Release();
                return false;
            }
            if (mBytesCount % (mChannels * mSampleBits / 8) != 0) {
                Release();
                return false;
            }
        }

        Log.i("ViPER4Android", "IRS [" + mIrsPathName + "] opened");
        Log.i("ViPER4Android", "IRS attr = [" + mSampleType + ","
                + mChannels + "," + mSamplesCount + "]");

        return true;
    }

    public byte[] readEntireData() {
        if (bufferInput == null || mIrsStream == null)
            return null;
        if (mSampleType < 1 || mSampleType > 4)
            return null;

        // Read raw bytes
        byte[] mData = new byte[4096];
        int mReadLength = 0;
        while (true) {
            try {
                int mRead = bufferInput.read(mData, mReadLength, 4096);
                if (mRead < 0)
                    break;
                mReadLength += mRead;
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
        if (mBytesCount > mData.length) {
            // If we got less data then header described, then use what we read
            mBytesCount = mData.length;
            mSamplesCount = mBytesCount / mChannels / (mSampleBits / 8);
            if (mBytesCount % (mChannels * mSampleBits / 8) != 0) {
                Release();
                return null;
            }
        } else if (mBytesCount < mData.length) {
            // If we got more data then header described, then use header
            // described
            Log.i("ViPER4Android",
                    "IrsUtils: We got some garbage data, header = "
                            + mBytesCount + ", read = " + mData.length);
            Log.i("ViPER4Android",
                    "IrsUtils: So lets discard some data, length = "
                            + (mData.length - mBytesCount));
            byte[] mActualData = new byte[(int) mBytesCount];
            System.arraycopy(mData, 0, mActualData, 0, (int) mBytesCount);
            mData = mActualData;
        }

        // Convert format
        switch (mSampleType) {
            case 1:
                return convert_S16LE_F32(mData);
            case 2:
                return convert_S24LE_F32(mData);
            case 3:
                return convert_S32LE_F32(mData);
        }

        return mData;
    }

    public int getChannels() {
        return mChannels;
    }

    public int getSampleCount() {
        return (int) mSamplesCount;
    }

    public int getByteCount() {
        return (int) mBytesCount;
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
            int s24 = s24_b1 & 0xFF | (s24_b2 & 0xFF) << 8 | (s24_b3 & 0xFF) << 16;
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

    private static byte[] convert_S32LE_F32(byte[] s32LEData) {
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
        return (short) (b1 & 0xFF | (b2 & 0xFF) << 8);
    }

    private static int readUnsignedInt(BufferedInputStream bufferInput) {
        byte[] buffer = new byte[4];
        int mReturn;
        try {
            mReturn = bufferInput.read(buffer);
        } catch (IOException e) {
            return 0;
        }
        if (mReturn == -1)
            return -1;
        else {
            return (buffer[0] & 0xFF) << 24 | (buffer[1] & 0xFF) << 16
                    | (buffer[2] & 0xFF) << 8 | buffer[3] & 0xFF;
        }
    }

    private static int readUnsignedIntLE(BufferedInputStream bufferInput) {
        byte[] buffer = new byte[4];
        int mReturn;
        try {
            mReturn = bufferInput.read(buffer);
        } catch (IOException e) {
            return 0;
        }
        if (mReturn == -1)
            return -1;
        else {
            return buffer[0] & 0xFF | (buffer[1] & 0xFF) << 8
                    | (buffer[2] & 0xFF) << 16 | (buffer[3] & 0xFF) << 24;
        }
    }

    private static short readUnsignedShortLE(BufferedInputStream bufferInput) {
        byte[] buffer = new byte[2];
        int mReturn;
        try {
            mReturn = bufferInput.read(buffer, 0, 2);
        } catch (IOException e) {
            return 0;
        }
        if (mReturn == -1)
            return -1;
        else
            return byteToShortLE(buffer[0], buffer[1]);
    }
}
