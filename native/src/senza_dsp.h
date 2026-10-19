// Senza Sound - inti DSP (tanpa ketergantungan Android, bisa diuji di host).
// Stereo interleaved float. Rantai: EQ 10 band -> stereo widener -> kompresor -> loudness -> limiter.
#pragma once
#include <atomic>
#include <cmath>
#include <cstddef>
#include <cstring>

namespace senza {

constexpr int kBands = 10;
constexpr float kFreqs[kBands] = {31.f, 62.f, 125.f, 250.f, 500.f, 1000.f, 2000.f, 4000.f, 8000.f, 16000.f};

struct Params {
    bool enabled = true;
    float gainsDb[kBands] = {0};   // gain EQ efektif per band (dB)
    float spatial = 0.f;           // 0..1 lebar stereo
    float leveler = 0.f;           // 0..1 kekuatan kompresor
    float loudnessDb = 0.f;        // 0..8 dB
    float limiterDb = -1.f;        // ambang limiter (dB)
};

struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    float z1[2] = {0, 0}, z2[2] = {0, 0};
    bool active = false;

    void setPeaking(float fs, float f0, float gainDb, float q) {
        if (std::fabs(gainDb) < 0.01f || f0 >= fs * 0.49f) { active = false; return; }
        const float A = std::pow(10.f, gainDb / 40.f);
        const float w0 = 2.f * 3.14159265f * f0 / fs;
        const float alpha = std::sin(w0) / (2.f * q);
        const float cw = std::cos(w0);
        const float a0 = 1.f + alpha / A;
        b0 = (1.f + alpha * A) / a0;
        b1 = (-2.f * cw) / a0;
        b2 = (1.f - alpha * A) / a0;
        a1 = (-2.f * cw) / a0;
        a2 = (1.f - alpha / A) / a0;
        active = true;
    }
    inline float run(int ch, float x) {
        const float y = b0 * x + z1[ch];
        z1[ch] = b1 * x - a1 * y + z2[ch];
        z2[ch] = b2 * x - a2 * y;
        return y;
    }
    void reset() { z1[0] = z1[1] = z2[0] = z2[1] = 0.f; }
};

class Processor {
public:
    Processor() { reset(); }

    void setSampleRate(float fs) {
        fs_ = fs > 8000.f ? fs : 48000.f;
        dirty_.store(true);
    }

    // Aman dipanggil dari thread perintah; diterapkan di thread audio.
    void setParams(const Params& p) {
        while (lock_.test_and_set(std::memory_order_acquire)) {}
        pending_ = p;
        lock_.clear(std::memory_order_release);
        dirty_.store(true);
    }

    void reset() {
        for (auto& b : eq_) b.reset();
        env_ = 0.f; compGain_ = 1.f; limGain_ = 1.f;
    }

    // in/out boleh sama. frames = jumlah frame stereo.
    void process(const float* in, float* out, size_t frames) {
        applyPending();
        if (!cur_.enabled) {
            if (in != out) std::memcpy(out, in, frames * 2 * sizeof(float));
            return;
        }
        const float widen = 1.f + cur_.spatial * 0.9f;
        const float loud = std::pow(10.f, cur_.loudnessDb / 20.f);
        const float limThr = std::pow(10.f, cur_.limiterDb / 20.f);
        // kompresor
        const bool comp = cur_.leveler > 0.02f;
        const float thrDb = -12.f - 18.f * cur_.leveler;
        const float ratio = 1.5f + 2.5f * cur_.leveler;
        const float makeup = std::pow(10.f, (-thrDb * (1.f - 1.f / ratio) * 0.6f) / 20.f);
        const float atk = 1.f - std::exp(-1.f / (0.015f * fs_));
        const float rel = 1.f - std::exp(-1.f / (0.250f * fs_));
        const float limRel = 1.f - std::exp(-1.f / (0.060f * fs_));

        for (size_t i = 0; i < frames; ++i) {
            float l = in[2 * i], r = in[2 * i + 1];
            for (int b = 0; b < kBands; ++b) {
                if (eq_[b].active) { l = eq_[b].run(0, l); r = eq_[b].run(1, r); }
            }
            if (cur_.spatial > 0.01f) {
                const float m = 0.5f * (l + r);
                const float s = 0.5f * (l - r) * widen;
                l = m + s; r = m - s;
            }
            if (comp) {
                const float peak = std::fmax(std::fabs(l), std::fabs(r));
                env_ += (peak > env_ ? atk : rel) * (peak - env_);
                const float envDb = 20.f * std::log10(env_ + 1e-9f);
                float target = 1.f;
                if (envDb > thrDb) target = std::pow(10.f, -((envDb - thrDb) * (1.f - 1.f / ratio)) / 20.f);
                compGain_ += (target < compGain_ ? atk : rel) * (target - compGain_);
                const float g = compGain_ * makeup;
                l *= g; r *= g;
            }
            l *= loud; r *= loud;
            // limiter: serang cepat, lepas halus
            const float pk = std::fmax(std::fabs(l), std::fabs(r));
            const float need = pk > limThr ? limThr / pk : 1.f;
            if (need < limGain_) limGain_ = need; else limGain_ += limRel * (1.f - limGain_);
            l *= limGain_; r *= limGain_;
            out[2 * i] = l > 1.f ? 1.f : (l < -1.f ? -1.f : l);
            out[2 * i + 1] = r > 1.f ? 1.f : (r < -1.f ? -1.f : r);
        }
    }

private:
    void applyPending() {
        if (!dirty_.load()) return;
        if (lock_.test_and_set(std::memory_order_acquire)) return;  // sedang ditulis; coba lagi nanti
        cur_ = pending_;
        lock_.clear(std::memory_order_release);
        dirty_.store(false);
        for (int b = 0; b < kBands; ++b) eq_[b].setPeaking(fs_, kFreqs[b], cur_.gainsDb[b], 1.2f);
    }

    float fs_ = 48000.f;
    Params cur_, pending_;
    Biquad eq_[kBands];
    float env_ = 0.f, compGain_ = 1.f, limGain_ = 1.f;
    std::atomic<bool> dirty_{true};
    std::atomic_flag lock_ = ATOMIC_FLAG_INIT;
};

}  // namespace senza
