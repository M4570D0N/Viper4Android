#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <inttypes.h>
#include "WavReader.h"

#define FILE_BEGIN SEEK_SET
#define FILE_END SEEK_END

WavReader_R32::WavReader_R32() {
    fpSamples = NULL;
    mFileHandle = NULL;
    mUiFrameCount = 0;
    uiSamplingRate = 0;
    mUiChannels = 0;
}

WavReader_R32::~WavReader_R32() {
    if (fpSamples != NULL) delete [] fpSamples;
    if (mFileHandle != NULL) fclose(mFileHandle);
    fpSamples = NULL;
    mFileHandle = NULL;
    mUiFrameCount = 0;
    uiSamplingRate = 0;
    mUiChannels = 0;
}

bool WavReader_R32::ReadFOURCC(int8_t cCode[4]) {
    if (mFileHandle == NULL) return false;
    uint32_t mReadSize = (uint32_t)fread(cCode, sizeof(int8_t), 4, mFileHandle);
    if (mReadSize != 4) return false;
    return true;
}

uint32_t WavReader_R32::SeekToChunk(int8_t cCode[4], uint8_t uiCodeLen) {
    if (mFileHandle == NULL) return false;
    fseek(mFileHandle, 0, FILE_END);
    uint32_t uiFileLength = ftell(mFileHandle);
    fseek(mFileHandle, 12, FILE_BEGIN);
    uint32_t uiCurrentPos = ftell(mFileHandle);
    while (true) {
        int8_t cReadCode[4] = {0, 0, 0, 0};
        if (!ReadFOURCC(cReadCode)) return 0;
        if (memcmp(cReadCode, cCode, uiCodeLen) == 0) {
            uint32_t mChunkLength = ReadUINT32();
            uint32_t mRestBytes = uiFileLength - ftell(mFileHandle);
            if (mChunkLength > mRestBytes) {
                uiCurrentPos++;
                fseek(mFileHandle, uiCurrentPos, FILE_BEGIN);
                continue;
            }
            return mChunkLength;
        }
        uiCurrentPos++;
        fseek(mFileHandle, uiCurrentPos, FILE_BEGIN);
    }
    return 0;
}

uint32_t WavReader_R32::ReadUINT32() {
    if (mFileHandle == NULL) return 0;
    uint8_t mBuffer[4];
    uint32_t mReadSize = (uint32_t)fread(mBuffer, sizeof(int8_t), 4, mFileHandle);
    if (mReadSize != 4) return 0;
    return ((uint32_t)mBuffer[0]) |
           ((uint32_t)mBuffer[1] <<  8) |
           ((uint32_t)mBuffer[2] << 16) |
           ((uint32_t)mBuffer[3] << 24);
}

uint16_t WavReader_R32::ReadUINT16() {
    if (mFileHandle == NULL) return 0;
    uint8_t mBuffer[2];
    uint32_t mReadSize = (uint32_t)fread(mBuffer, sizeof(int8_t), 2, mFileHandle);
    if (mReadSize != 2) return 0;
    return ((uint16_t)mBuffer[0]) | ((uint16_t)mBuffer[1] <<  8);
}

void WavReader_R32::ConvertInt8ToFloat32(uint8_t *ptrInput, int32_t mSamplesCount, int32_t mChannels, float *ptrOutput) {
    for (int32_t i = 0; i < mSamplesCount * mChannels; i++) {
        int32_t nValue = (uint8_t)(ptrInput[i]) - 0x80;
        ptrOutput[i] = (float)((double)nValue * 0.0078125);
    }
}

void WavReader_R32::ConvertInt16ToFloat32(int16_t *ptrInput, int32_t mSamplesCount, int32_t mChannels, float *ptrOutput) {
    for (int32_t i = 0; i < mSamplesCount * mChannels; i++)
        ptrOutput[i] = (float)((double)((int16_t)(ptrInput[i])) * 0.000030517578125);
}

void WavReader_R32::ConvertInt24ToFloat32(uint8_t *ptrInput, int32_t mSamplesCount, int32_t mChannels, float *ptrOutput) {
    for (int32_t i = 0; i < mSamplesCount * mChannels; i++) {
        int32_t mBits24Int = (ptrInput[0]) + (ptrInput[1] << 8) + (ptrInput[2] << 16);
        if (mBits24Int > 0x7FFFFF) {
            mBits24Int &= 0x7FFFFF;
            mBits24Int  = 0x7FFFFF - mBits24Int;
            mBits24Int  = -mBits24Int;
        }
        ptrOutput[i] = (float)((double)mBits24Int * 0.00000011920928955078125);
        ptrInput += 3;
    }
}

void WavReader_R32::ConvertInt32ToFloat32(int32_t *ptrInput, int32_t mSamplesCount, int32_t mChannels, float *ptrOutput) {
    for (int32_t i = 0; i < mSamplesCount * mChannels; i++)
        ptrOutput[i] = (float)((double)((int32_t)(ptrInput[i])) * 0.0000000004656612873077392578125);
}

bool WavReader_R32::OpenWavFile(const char *mFilePathName) {
    if (mFilePathName == NULL) return false;
    if (strlen(mFilePathName) <= 0) return false;
    if (fpSamples != NULL) delete [] fpSamples;
    if (mFileHandle != NULL) fclose(mFileHandle);
    fpSamples = NULL;
    mFileHandle = NULL;
    mUiFrameCount = 0;
    uiSamplingRate = 0;
    mUiChannels = 0;

    mFileHandle = fopen(mFilePathName, "rb");
    if (mFileHandle == NULL) return false;

    int8_t cCode[4];
    if (!ReadFOURCC(cCode)) return false;
    if ((cCode[0] != 'R') || (cCode[1] != 'I') || (cCode[2] != 'F') || (cCode[3] != 'F')) return false;
    uint32_t uiRiffChunkDataSize = ReadUINT32();
    if (uiRiffChunkDataSize == 0) return false;
    if (!ReadFOURCC(cCode)) return false;
    if ((cCode[0] != 'W') || (cCode[1] != 'A') || (cCode[2] != 'V') || (cCode[3] != 'E')) return false;

    cCode[0] = 'f'; cCode[1] = 'm'; cCode[2] = 't';
    uint32_t mFormatLength = SeekToChunk(cCode, 3);
    if (mFormatLength < 16) return false;
    uint16_t mFormatTag = ReadUINT16();
    if ((mFormatTag != 0x0001) && (mFormatTag != 0x0003)) return false;

    uint16_t uiChannels = ReadUINT16();
    if ((uiChannels != 1) && (uiChannels != 2)) return false;
    uint32_t uiSampleRate = ReadUINT32();
    ReadUINT32();
    ReadUINT16();
    uint16_t uiBitsPerSample = ReadUINT16();
    if ((uiBitsPerSample !=  8) &&
        (uiBitsPerSample != 16) &&
        (uiBitsPerSample != 24) &&
        (uiBitsPerSample != 32))
        return false;

    cCode[0] = 'd'; cCode[1] = 'a'; cCode[2] = 't'; cCode[3] = 'a';
    uint32_t uiDataSize = SeekToChunk(cCode, 4);
    uint32_t uiFrameCount = uiDataSize / (uiBitsPerSample / 8) / uiChannels;
    if (uiFrameCount == 0) return false;

    if (mFormatTag == 0x0003) {
        fpSamples = new float[uiFrameCount * uiChannels];
        if (fpSamples == NULL) return false;
        uint32_t mReadFrameCount = (uint32_t)fread(fpSamples, sizeof(float), uiFrameCount * uiChannels, mFileHandle);
        if (mReadFrameCount != uiFrameCount * uiChannels) {
            delete [] fpSamples;
            return false;
        }
        mUiFrameCount = uiFrameCount;
        uiSamplingRate = uiSampleRate;
        mUiChannels = uiChannels;
        return true;
    } else {
        uint32_t uiReadDataLength = uiFrameCount * uiChannels * (uiBitsPerSample / 8);
        uint8_t *ucFrameBuffer = new uint8_t[uiReadDataLength];
        if (ucFrameBuffer == NULL) return false;
        uint32_t mReadFrameCount = (uint32_t)fread(ucFrameBuffer, sizeof(uint8_t), uiReadDataLength, mFileHandle);
        if (mReadFrameCount != uiReadDataLength)
        {
            delete [] ucFrameBuffer;
            return false;
        }
        fpSamples = new float[uiFrameCount * uiChannels];
        if (fpSamples == NULL) {
            delete [] ucFrameBuffer;
            return false;
        }
        switch (uiBitsPerSample) {
            case 8:
                ConvertInt8ToFloat32(ucFrameBuffer, uiFrameCount, uiChannels, fpSamples);
                break;

            case 16:
                ConvertInt16ToFloat32((int16_t*)ucFrameBuffer, uiFrameCount, uiChannels, fpSamples);
                break;

            case 24:
                ConvertInt24ToFloat32(ucFrameBuffer, uiFrameCount, uiChannels, fpSamples);
                break;

            case 32:
                ConvertInt32ToFloat32((int32_t*)ucFrameBuffer, uiFrameCount, uiChannels, fpSamples);
                break;
        }
        delete [] ucFrameBuffer;
        mUiFrameCount = uiFrameCount;
        uiSamplingRate = uiSampleRate;
        mUiChannels = uiChannels;
        return true;
    }

    return false;
}

void WavReader_R32::ScalePCM(float rGain) {
    if (fpSamples == NULL) return;
    for (uint32_t i = 0; i < mUiFrameCount * mUiChannels; i++)
        fpSamples[i] *= rGain;
}

uint32_t WavReader_R32::GetFrameCount() {
    return mUiFrameCount;
}

uint32_t WavReader_R32::GetSamplingRate() {
    return uiSamplingRate;
}

uint32_t WavReader_R32::GetChannels() {
    return mUiChannels;
}

uint32_t WavReader_R32::ReadEntirePCM(float *fpBuffer, uint32_t mFrames) {
    if (fpSamples == NULL) return 0;
    if (fpBuffer == NULL) return 0;
    if (mFrames != mUiFrameCount) return 0;
    memcpy(fpBuffer, fpSamples, mUiFrameCount * mUiChannels * sizeof(float));
    return mFrames;
}

float* WavReader_R32::GetDataBuffer() {
    return fpSamples;
}
