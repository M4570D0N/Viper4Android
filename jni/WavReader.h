#ifndef _WAVREADER_H
#define _WAVREADER_H

#include <stdio.h>
#include <stdint.h>
#include <inttypes.h>

class WavReader_R32 {
private:
    float *fpSamples;

private:
    FILE *mFileHandle;
    uint32_t mUiFrameCount;
    uint32_t uiSamplingRate;
    uint32_t mUiChannels;

private:
    bool ReadFOURCC(int8_t cCode[4]);
    uint32_t SeekToChunk(int8_t cCode[4], uint8_t uiCodeLen);
    uint32_t ReadUINT32();
    uint16_t ReadUINT16();

private:
    void ConvertInt8ToFloat32(uint8_t *ptrInput, int32_t nSamplesCount, int32_t nChannels, float *ptrOutput);
    void ConvertInt16ToFloat32(int16_t *ptrInput, int32_t nSamplesCount, int32_t nChannels, float *ptrOutput);
    void ConvertInt24ToFloat32(uint8_t *ptrInput, int32_t nSamplesCount, int32_t nChannels, float *ptrOutput);
    void ConvertInt32ToFloat32(int32_t *ptrInput, int32_t nSamplesCount, int32_t nChannels, float *ptrOutput);

public:
    WavReader_R32();
    ~WavReader_R32();

public:
    bool OpenWavFile(const char *mFilePathName);
    void ScalePCM(float rGain);
    uint32_t GetFrameCount();
    uint32_t GetSamplingRate();
    uint32_t GetChannels();
    uint32_t ReadEntirePCM(float *fpBuffer, uint32_t mFrames);
    float* GetDataBuffer();
};

#endif
