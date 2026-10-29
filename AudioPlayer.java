package id.dynamicisland.music;

import id.dynamicisland.config.IslandConfig;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Pemutar MP3: streaming online atau file offline, lewat javax.sound + JLayer. */
public final class AudioPlayer {
    public enum State { IDLE, LOADING, PLAYING, PAUSED, ERROR }

    private static final AudioPlayer INSTANCE = new AudioPlayer();
    public static AudioPlayer get() { return INSTANCE; }

    private volatile State state = State.IDLE;
    private volatile Track track;
    private volatile List<Track> queue = List.of();
    private volatile int queueIndex = -1;
    private volatile boolean paused;
    private volatile int generation;
    private volatile SourceDataLine line;
    private volatile long posMs;
    private volatile List<LyricsService.Line> lyrics;
    private volatile String error;
    private final Spectrum spectrum = new Spectrum();

    private AudioPlayer() {}

    // ---------- kontrol ----------

    public synchronized void play(Track t, List<Track> q) {
        stopInternal();
        int gen = ++generation;
        track = t;
        queue = (q == null || q.isEmpty()) ? List.of(t) : q;
        queueIndex = indexOf(queue, t);
        lyrics = null;
        error = null;
        posMs = 0;
        paused = false;
        state = State.LOADING;
        Thread th = new Thread(() -> run(t, gen), "DynamicIsland-Audio");
        th.setDaemon(true);
        th.start();
        Thread lt = new Thread(() -> {
            List<LyricsService.Line> l = Library.loadLyrics(t);
            if (gen == generation) lyrics = l;
        }, "DynamicIsland-Lyrics");
        lt.setDaemon(true);
        lt.start();
    }

    public synchronized void togglePause() {
        if (track == null) return;
        SourceDataLine l = line;
        if (state == State.PLAYING) {
            paused = true;
            state = State.PAUSED;
            if (l != null) l.stop();
        } else if (state == State.PAUSED) {
            paused = false;
            state = State.PLAYING;
            if (l != null) l.start();
        }
    }

    public synchronized void next() {
        if (queue.isEmpty() || track == null) return;
        int i = queueIndex + 1;
        if (i >= queue.size()) i = 0;
        play(queue.get(i), queue);
    }

    public synchronized void previous() {
        if (queue.isEmpty() || track == null) return;
        if (posMs > 4000) { play(track, queue); return; }
        int i = queueIndex - 1;
        if (i < 0) i = queue.size() - 1;
        play(queue.get(i), queue);
    }

    public synchronized void stop() {
        stopInternal();
        track = null;
        queue = List.of();
        queueIndex = -1;
        lyrics = null;
        posMs = 0;
        state = State.IDLE;
        spectrum.clear();
    }

    public void setVolume(int percent) {
        IslandConfig.get().volume = Math.max(0, Math.min(100, percent));
        SourceDataLine l = line;
        if (l != null) applyVolume(l);
    }

    // ---------- getter untuk UI ----------

    public State state() { return state; }
    public Track track() { return track; }
    public boolean isActive() { return track != null && state != State.IDLE; }
    public long positionMs() { return posMs; }
    public long durationMs() { Track t = track; return t == null ? 0 : t.durationSec * 1000L; }
    public List<LyricsService.Line> lyrics() { return lyrics; }
    public String error() { return error; }
    public float[] bands(int count) { return spectrum.bands(count); }

    // ---------- internal ----------

    private void stopInternal() {
        generation++;
        SourceDataLine l = line;
        line = null;
        if (l != null) {
            try { l.stop(); l.flush(); l.close(); } catch (Exception ignored) { }
        }
    }

    private static int indexOf(List<Track> list, Track t) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id != null && list.get(i).id.equals(t.id)) return i;
        }
        return 0;
    }

    private InputStream openAudio(Track t) throws Exception {
        Path local = Library.audioPath(t);
        if (local != null && Files.exists(local)) return Files.newInputStream(local);
        return Http.openStream(t.streamUrl);
    }

    private void run(Track t, int gen) {
        SourceDataLine sdl = null;
        try (InputStream raw = openAudio(t)) {
            Bitstream bs = new Bitstream(new BufferedInputStream(raw, 1 << 16));
            Decoder dec = new Decoder();
            byte[] bytes = new byte[0];
            int rate = 44100;
            boolean opened = false;
            Header h;
            while (gen == generation && (h = bs.readFrame()) != null) {
                SampleBuffer out = (SampleBuffer) dec.decodeFrame(h, bs);
                if (!opened) {
                    rate = out.getSampleFrequency();
                    int ch = out.getChannelCount();
                    AudioFormat fmt = new AudioFormat(rate, 16, ch, true, false);
                    sdl = AudioSystem.getSourceDataLine(fmt);
                    sdl.open(fmt, rate * ch * 2 / 5); // buffer ~200 ms supaya equalizer tetap sinkron
                    applyVolume(sdl);
                    sdl.start();
                    synchronized (this) {
                        if (gen != generation) { sdl.close(); return; }
                        line = sdl;
                        state = paused ? State.PAUSED : State.PLAYING;
                    }
                    opened = true;
                }
                short[] s = out.getBuffer();
                int n = out.getBufferLength();
                if (bytes.length < n * 2) bytes = new byte[n * 2];
                for (int i = 0; i < n; i++) {
                    bytes[2 * i] = (byte) s[i];
                    bytes[2 * i + 1] = (byte) (s[i] >> 8);
                }
                while (paused && gen == generation) Thread.sleep(40);
                if (gen != generation) break;
                spectrum.push(s, n, out.getChannelCount());
                sdl.write(bytes, 0, n * 2);
                posMs = sdl.getLongFramePosition() * 1000L / rate;
                bs.closeFrame();
            }
            if (gen == generation && sdl != null) {
                sdl.drain();
            }
            if (gen == generation) onEnded(gen);
        } catch (Exception e) {
            if (gen == generation) {
                error = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                state = State.ERROR;
            }
        } finally {
            if (sdl != null && gen != generation) {
                try { sdl.close(); } catch (Exception ignored) { }
            }
        }
    }

    private synchronized void onEnded(int gen) {
        if (gen != generation) return;
        int i = queueIndex + 1;
        if (i < queue.size()) {
            play(queue.get(i), queue);
        } else {
            stop();
        }
    }

    private void applyVolume(SourceDataLine l) {
        try {
            if (!l.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
            FloatControl c = (FloatControl) l.getControl(FloatControl.Type.MASTER_GAIN);
            float v = IslandConfig.get().volume / 100f;
            float db = v <= 0.001f ? c.getMinimum() : (float) (20 * Math.log10(v));
            c.setValue(Math.max(c.getMinimum(), Math.min(c.getMaximum(), db)));
        } catch (Exception ignored) { }
    }
}
