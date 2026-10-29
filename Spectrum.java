package id.dynamicisland.music;

/** Analisis spektrum sederhana: ring buffer sampel mono + FFT radix-2 -> N pita log-spaced (0..1). */
public final class Spectrum {
    private static final int N = 1024;
    private final float[] ring = new float[N];
    private int head;
    private final float[] re = new float[N];
    private final float[] im = new float[N];
    private final float[] window = new float[N];

    public Spectrum() {
        for (int i = 0; i < N; i++) {
            window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1)));
        }
    }

    /** @param s sampel interleaved 16-bit, @param len jumlah short valid, @param ch jumlah channel */
    public synchronized void push(short[] s, int len, int ch) {
        for (int i = 0; i + ch <= len; i += ch) {
            float m = 0;
            for (int c = 0; c < ch; c++) m += s[i + c];
            ring[head] = (m / ch) / 32768f;
            head = (head + 1) % N;
        }
    }

    public synchronized void clear() {
        java.util.Arrays.fill(ring, 0f);
    }

    /** @return array berukuran count, tiap nilai 0..1 */
    public synchronized float[] bands(int count) {
        for (int i = 0; i < N; i++) {
            re[i] = ring[(head + i) % N] * window[i];
            im[i] = 0f;
        }
        fft();

        float[] out = new float[count];
        int minBin = 2, maxBin = 300; // ~86 Hz .. ~13 kHz pada 44.1 kHz
        double ratio = Math.pow((double) maxBin / minBin, 1.0 / count);
        double lo = minBin;
        for (int b = 0; b < count; b++) {
            double hi = lo * ratio;
            int from = (int) Math.floor(lo);
            int to = Math.max(from + 1, (int) Math.floor(hi));
            float peak = 0f;
            for (int k = from; k < to && k < N / 2; k++) {
                float mag = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]) / (N / 4f);
                if (mag > peak) peak = mag;
            }
            double db = 20 * Math.log10(peak + 1e-6);
            out[b] = (float) Math.max(0, Math.min(1, (db + 62) / 52)); // -62 dB..-10 dB
            lo = hi;
        }
        return out;
    }

    private void fft() {
        for (int i = 1, j = 0; i < N; i++) {
            int bit = N >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= N; len <<= 1) {
            double ang = -2 * Math.PI / len;
            float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
            for (int i = 0; i < N; i += len) {
                float cr = 1f, ci = 0f;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    float xr = re[b] * cr - im[b] * ci;
                    float xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    float ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
    }
}
