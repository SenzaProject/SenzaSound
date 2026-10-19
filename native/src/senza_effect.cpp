// Senza Sound - efek audio native (audio effect library untuk AudioFlinger).
// Didaftarkan di vendor/etc/audio_effects.xml; dikendalikan aplikasi lewat AudioEffect.setParameter().
//
// Protokol parameter (param = int32 key, value = data):
//   1 ENABLE   int32 0/1
//   2 EQ       float[10]  gain dB per band
//   3 SPATIAL  float 0..1
//   4 LEVELER  float 0..1
//   5 LOUDNESS float dB (0..8)
//   6 LIMITER  float dB (ambang)
#include <errno.h>
#include <stdint.h>
#include <string.h>
#include <new>

#if __has_include(<hardware/audio_effect.h>)
#include <hardware/audio_effect.h>
#else
#include <system/audio_effect.h>
#endif

#include "senza_dsp.h"

namespace {

const effect_descriptor_t kDescriptor = {
    // type
    {0x634b1d63, 0x68d0, 0x4951, 0x9fde, {0xbb, 0xd2, 0xbf, 0x82, 0xaf, 0x18}},
    // uuid (implementasi)
    {0x861f6b79, 0xb150, 0x4566, 0x9994, {0xc2, 0xa5, 0x82, 0x91, 0xef, 0xa1}},
    EFFECT_CONTROL_API_VERSION,
    (EFFECT_FLAG_TYPE_INSERT | EFFECT_FLAG_INSERT_LAST),
    0,  // cpuLoad
    1,  // memoryUsage
    "Senza Sound",
    "SenzaProject",
};

enum Key : int32_t { kEnable = 1, kEq = 2, kSpatial = 3, kLeveler = 4, kLoudness = 5, kLimiter = 6 };

struct Context {
    const struct effect_interface_s* itfe;
    effect_config_t config;
    senza::Processor dsp;
    senza::Params params;
    bool active = false;      // di-ENABLE oleh AudioFlinger
    bool supported = false;   // konfigurasi didukung (stereo, 16-bit / float)
};

inline bool isFormatOk(uint32_t f) {
    return f == AUDIO_FORMAT_PCM_16_BIT || f == AUDIO_FORMAT_PCM_FLOAT;
}

inline bool uuidEq(const effect_uuid_t* a, const effect_uuid_t* b) {
    return memcmp(a, b, sizeof(effect_uuid_t)) == 0;
}

int32_t doSetConfig(Context* c, effect_config_t* cfg) {
    if (cfg->inputCfg.samplingRate != cfg->outputCfg.samplingRate) return -EINVAL;
    if (cfg->inputCfg.channels != AUDIO_CHANNEL_OUT_STEREO ||
        cfg->outputCfg.channels != AUDIO_CHANNEL_OUT_STEREO) return -EINVAL;
    if (!isFormatOk(cfg->inputCfg.format) || cfg->inputCfg.format != cfg->outputCfg.format) return -EINVAL;
    if (cfg->outputCfg.accessMode != EFFECT_BUFFER_ACCESS_WRITE &&
        cfg->outputCfg.accessMode != EFFECT_BUFFER_ACCESS_ACCUMULATE) return -EINVAL;
    c->config = *cfg;
    c->dsp.setSampleRate((float)cfg->inputCfg.samplingRate);
    c->dsp.setParams(c->params);
    c->supported = true;
    return 0;
}

int32_t doSetParam(Context* c, effect_param_t* p) {
    if (p->psize != sizeof(int32_t)) return -EINVAL;
    const int32_t key = *reinterpret_cast<int32_t*>(p->data);
    const char* v = p->data + ((p->psize + 3) & ~3u);
    switch (key) {
        case kEnable:
            if (p->vsize < sizeof(int32_t)) return -EINVAL;
            c->params.enabled = *reinterpret_cast<const int32_t*>(v) != 0;
            break;
        case kEq:
            if (p->vsize < sizeof(float) * senza::kBands) return -EINVAL;
            for (int i = 0; i < senza::kBands; ++i) {
                float g;
                memcpy(&g, v + i * sizeof(float), sizeof(float));
                c->params.gainsDb[i] = g < -15.f ? -15.f : (g > 15.f ? 15.f : g);
            }
            break;
        case kSpatial: case kLeveler: case kLoudness: case kLimiter: {
            if (p->vsize < sizeof(float)) return -EINVAL;
            float f;
            memcpy(&f, v, sizeof(float));
            if (key == kSpatial) c->params.spatial = f < 0.f ? 0.f : (f > 1.f ? 1.f : f);
            else if (key == kLeveler) c->params.leveler = f < 0.f ? 0.f : (f > 1.f ? 1.f : f);
            else if (key == kLoudness) c->params.loudnessDb = f < 0.f ? 0.f : (f > 8.f ? 8.f : f);
            else c->params.limiterDb = f < -24.f ? -24.f : (f > 0.f ? 0.f : f);
            break;
        }
        default:
            return -EINVAL;
    }
    c->dsp.setParams(c->params);
    return 0;
}

// ---- Antarmuka efek ----
int32_t Process(effect_handle_t self, audio_buffer_t* in, audio_buffer_t* out) {
    Context* c = reinterpret_cast<Context*>(self);
    if (c == nullptr || in == nullptr || out == nullptr || in->raw == nullptr || out->raw == nullptr)
        return -EINVAL;
    if (!c->active || !c->supported) return -ENODATA;
    size_t frames = in->frameCount < out->frameCount ? in->frameCount : out->frameCount;
    const bool acc = c->config.outputCfg.accessMode == EFFECT_BUFFER_ACCESS_ACCUMULATE;
    float tmpIn[2 * 512], tmpOut[2 * 512];

    if (c->config.inputCfg.format == AUDIO_FORMAT_PCM_FLOAT) {
        const float* src = reinterpret_cast<const float*>(in->raw);
        float* dst = reinterpret_cast<float*>(out->raw);
        size_t done = 0;
        while (done < frames) {
            size_t n = frames - done < 512 ? frames - done : 512;
            c->dsp.process(src + 2 * done, tmpOut, n);
            for (size_t i = 0; i < 2 * n; ++i) dst[2 * done + i] = acc ? dst[2 * done + i] + tmpOut[i] : tmpOut[i];
            done += n;
        }
    } else {
        const int16_t* src = reinterpret_cast<const int16_t*>(in->raw);
        int16_t* dst = reinterpret_cast<int16_t*>(out->raw);
        size_t done = 0;
        while (done < frames) {
            size_t n = frames - done < 512 ? frames - done : 512;
            for (size_t i = 0; i < 2 * n; ++i) tmpIn[i] = src[2 * done + i] * (1.f / 32768.f);
            c->dsp.process(tmpIn, tmpOut, n);
            for (size_t i = 0; i < 2 * n; ++i) {
                float s = tmpOut[i] * 32767.f;
                if (acc) s += dst[2 * done + i];
                dst[2 * done + i] = (int16_t)(s > 32767.f ? 32767.f : (s < -32768.f ? -32768.f : s));
            }
            done += n;
        }
    }
    return 0;
}

int32_t Command(effect_handle_t self, uint32_t cmdCode, uint32_t cmdSize, void* pCmdData,
                uint32_t* replySize, void* pReplyData) {
    Context* c = reinterpret_cast<Context*>(self);
    if (c == nullptr) return -EINVAL;
    switch (cmdCode) {
        case EFFECT_CMD_INIT:
            if (pReplyData == nullptr || replySize == nullptr || *replySize != sizeof(int32_t)) return -EINVAL;
            *reinterpret_cast<int32_t*>(pReplyData) = 0;
            return 0;
        case EFFECT_CMD_SET_CONFIG:
            if (pCmdData == nullptr || cmdSize != sizeof(effect_config_t) || pReplyData == nullptr ||
                replySize == nullptr || *replySize != sizeof(int32_t)) return -EINVAL;
            *reinterpret_cast<int32_t*>(pReplyData) = doSetConfig(c, reinterpret_cast<effect_config_t*>(pCmdData));
            return 0;
        case EFFECT_CMD_GET_CONFIG:
            if (pReplyData == nullptr || replySize == nullptr || *replySize != sizeof(effect_config_t)) return -EINVAL;
            memcpy(pReplyData, &c->config, sizeof(effect_config_t));
            return 0;
        case EFFECT_CMD_RESET:
            c->dsp.reset();
            return 0;
        case EFFECT_CMD_ENABLE:
            if (pReplyData == nullptr || replySize == nullptr || *replySize != sizeof(int32_t)) return -EINVAL;
            c->active = true;
            *reinterpret_cast<int32_t*>(pReplyData) = 0;
            return 0;
        case EFFECT_CMD_DISABLE:
            if (pReplyData == nullptr || replySize == nullptr || *replySize != sizeof(int32_t)) return -EINVAL;
            c->active = false;
            *reinterpret_cast<int32_t*>(pReplyData) = 0;
            return 0;
        case EFFECT_CMD_SET_PARAM: {
            if (pCmdData == nullptr || cmdSize < sizeof(effect_param_t) || pReplyData == nullptr ||
                replySize == nullptr || *replySize != sizeof(int32_t)) return -EINVAL;
            effect_param_t* p = reinterpret_cast<effect_param_t*>(pCmdData);
            *reinterpret_cast<int32_t*>(pReplyData) = doSetParam(c, p);
            return 0;
        }
        case EFFECT_CMD_GET_PARAM: {
            // Tidak ada parameter yang bisa dibaca; jawab dengan status galat yang sah.
            if (pCmdData == nullptr || cmdSize < sizeof(effect_param_t) || pReplyData == nullptr ||
                replySize == nullptr || *replySize < sizeof(effect_param_t)) return -EINVAL;
            effect_param_t* p = reinterpret_cast<effect_param_t*>(pReplyData);
            memcpy(p, pCmdData, sizeof(effect_param_t) < cmdSize ? sizeof(effect_param_t) : cmdSize);
            p->status = -EINVAL;
            p->vsize = 0;
            return 0;
        }
        case EFFECT_CMD_SET_DEVICE:
        case EFFECT_CMD_SET_VOLUME:
        case EFFECT_CMD_SET_AUDIO_MODE:
        case EFFECT_CMD_SET_INPUT_DEVICE:
            return 0;
        default:
            return -EINVAL;
    }
}

int32_t GetDescriptor(effect_handle_t self, effect_descriptor_t* desc) {
    if (self == nullptr || desc == nullptr) return -EINVAL;
    *desc = kDescriptor;
    return 0;
}

const struct effect_interface_s kInterface = {Process, Command, GetDescriptor, nullptr};

// ---- Pustaka ----
int32_t CreateEffect(const effect_uuid_t* uuid, int32_t /*sessionId*/, int32_t /*ioId*/, effect_handle_t* pHandle) {
    if (uuid == nullptr || pHandle == nullptr || !uuidEq(uuid, &kDescriptor.uuid)) return -EINVAL;
    Context* c = new (std::nothrow) Context();
    if (c == nullptr) return -ENOMEM;
    c->itfe = &kInterface;
    memset(&c->config, 0, sizeof(c->config));
    *pHandle = reinterpret_cast<effect_handle_t>(c);
    return 0;
}

int32_t ReleaseEffect(effect_handle_t handle) {
    Context* c = reinterpret_cast<Context*>(handle);
    if (c == nullptr) return -EINVAL;
    delete c;
    return 0;
}

int32_t LibGetDescriptor(const effect_uuid_t* uuid, effect_descriptor_t* desc) {
    if (uuid == nullptr || desc == nullptr || !uuidEq(uuid, &kDescriptor.uuid)) return -EINVAL;
    *desc = kDescriptor;
    return 0;
}

}  // namespace

extern "C" {
__attribute__((visibility("default"))) audio_effect_library_t AUDIO_EFFECT_LIBRARY_INFO_SYM = {
    AUDIO_EFFECT_LIBRARY_TAG,
    EFFECT_LIBRARY_API_VERSION,
    "Senza Sound Library",
    "SenzaProject",
    CreateEffect,
    ReleaseEffect,
    LibGetDescriptor,
};
}
