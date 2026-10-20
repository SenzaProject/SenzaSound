// Uji inti DSP di host:  clang++ -std=c++17 -O2 -I../src dsp_test.cpp -o dsp_test && ./dsp_test
#include "senza_dsp.h"
#include <cstdio>
#include <vector>

static double rmsDb(const std::vector<float>& v, size_t skip) {
    double s = 0; size_t n = 0;
    for (size_t i = skip; i < v.size(); i += 2) { s += (double)v[i] * v[i]; ++n; }
    return 10.0 * std::log10(s / n + 1e-20);
}

static std::vector<float> sine(float f, float amp, size_t frames, float fs = 48000.f) {
    std::vector<float> v(frames * 2);
    for (size_t i = 0; i < frames; ++i) {
        float x = amp * std::sin(2.f * 3.14159265f * f * i / fs);
        v[2 * i] = x; v[2 * i + 1] = x;
    }
    return v;
}

int main() {
    int fails = 0;
    auto check = [&](const char* name, bool ok, double val) {
        std::printf("%-34s %s (%.3f)\n", name, ok ? "OK " : "GAGAL", val);
        if (!ok) ++fails;
    };
    const size_t N = 48000;

    { // EQ +6 dB di 1 kHz
        senza::Processor p; senza::Params pr; pr.gainsDb[18] = 6.f; pr.limiterDb = 0.f; p.setParams(pr);
        auto in = sine(senza::bandFreq(18), 0.1f, N); std::vector<float> out(in.size());
        p.process(in.data(), out.data(), N);
        double g = rmsDb(out, N / 2) - rmsDb(in, N / 2);
        check("EQ +6dB di band 18 (~1.1kHz)", std::fabs(g - 6.0) < 0.5, g);
    }
    { // EQ band 1 kHz tidak mengubah 62 Hz banyak
        senza::Processor p; senza::Params pr; pr.gainsDb[18] = 6.f; pr.limiterDb = 0.f; p.setParams(pr);
        auto in = sine(62.f, 0.1f, N); std::vector<float> out(in.size());
        p.process(in.data(), out.data(), N);
        double g = rmsDb(out, N / 2) - rmsDb(in, N / 2);
        check("EQ band 18 tak bocor ke 62Hz", std::fabs(g) < 0.6, g);
    }
    { // bypass
        senza::Processor p; senza::Params pr; pr.enabled = false; pr.gainsDb[18] = 6.f; p.setParams(pr);
        auto in = sine(1000.f, 0.3f, 4096); std::vector<float> out(in.size());
        p.process(in.data(), out.data(), 4096);
        double d = 0; for (size_t i = 0; i < in.size(); ++i) d += std::fabs(in[i] - out[i]);
        check("Bypass identik", d == 0.0, d);
    }
    { // limiter menahan puncak
        senza::Processor p; senza::Params pr; pr.loudnessDb = 8.f; pr.limiterDb = -6.f; p.setParams(pr);
        auto in = sine(1000.f, 0.9f, N); std::vector<float> out(in.size());
        p.process(in.data(), out.data(), N);
        float pk = 0; for (size_t i = N; i < out.size(); ++i) pk = std::fmax(pk, std::fabs(out[i]));
        check("Limiter puncak <= ~-6dB", pk <= 0.55f, pk);
    }
    { // kompresor mengecilkan sinyal keras relatif terhadap sinyal pelan
        senza::Processor p; senza::Params pr; pr.leveler = 1.f; pr.limiterDb = 0.f; p.setParams(pr);
        auto loud = sine(1000.f, 0.8f, N); std::vector<float> o1(loud.size());
        p.process(loud.data(), o1.data(), N);
        senza::Processor q; q.setParams(pr);
        auto soft = sine(1000.f, 0.05f, N); std::vector<float> o2(soft.size());
        q.process(soft.data(), o2.data(), N);
        double gl = rmsDb(o1, N / 2) - rmsDb(loud, N / 2);
        double gs = rmsDb(o2, N / 2) - rmsDb(soft, N / 2);
        check("Leveler: pelan terangkat > keras", gs > gl + 3.0, gs - gl);
    }
    { // widener: sinyal mono murni tidak berubah; side naik
        senza::Processor p; senza::Params pr; pr.spatial = 1.f; pr.limiterDb = 0.f; p.setParams(pr);
        std::vector<float> in(2 * N), out(2 * N);
        for (size_t i = 0; i < N; ++i) { float x = 0.2f * std::sin(0.05f * i); in[2*i] = x; in[2*i+1] = -x; }
        p.process(in.data(), out.data(), N);
        double g = rmsDb(out, N / 2) - rmsDb(in, N / 2);
        check("Widener: side +5.6dB (x1.9)", std::fabs(g - 5.58) < 0.5, g);
    }
    { // bass harmonik: 60 Hz harus menghasilkan energi di 180 Hz (harmonik ke-3)
        auto goertzel = [](const std::vector<float>& v, float f, size_t skip) {
            double w = 2.0 * 3.14159265 * f / 48000.0, c = 2.0 * std::cos(w), s1 = 0, s2 = 0; size_t n = 0;
            for (size_t i = skip; i < v.size(); i += 2) { double s = v[i] + c * s1 - s2; s2 = s1; s1 = s; ++n; }
            double p = s1 * s1 + s2 * s2 - c * s1 * s2; return 10.0 * std::log10(p / ((double)n * n) + 1e-20);
        };
        auto in = sine(60.f, 0.3f, N);
        std::vector<float> o0(in.size()), o1(in.size());
        senza::Processor p0; senza::Params a; a.limiterDb = 0.f; p0.setParams(a); p0.process(in.data(), o0.data(), N);
        senza::Processor p1; senza::Params b; b.limiterDb = 0.f; b.virtualBass = 1.f; p1.setParams(b); p1.process(in.data(), o1.data(), N);
        double d = goertzel(o1, 180.f, N / 2) - goertzel(o0, 180.f, N / 2);
        check("Bass harmonik: energi 180Hz naik", d > 15.0, d);
    }
    { // 32 band: frekuensi band naik monoton dari 20 Hz ke 20 kHz
        bool ok = std::fabs(senza::bandFreq(0) - 20.f) < 0.01f && std::fabs(senza::bandFreq(31) - 20000.f) < 5.f;
        check("Band 0=20Hz, band 31=20kHz", ok, senza::bandFreq(31));
    }
    std::printf(fails ? "\n%d uji GAGAL\n" : "\nSemua uji lolos\n", fails);
    return fails ? 1 : 0;
}
