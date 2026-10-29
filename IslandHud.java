package id.dynamicisland.render;

import id.dynamicisland.config.IslandConfig;
import id.dynamicisland.config.IslandConfig.AnimMode;
import id.dynamicisland.config.IslandConfig.Shape;
import id.dynamicisland.music.AudioPlayer;
import id.dynamicisland.music.LyricsService;
import id.dynamicisland.music.Track;
import id.dynamicisland.reminder.ReminderService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;

import java.util.List;

/** Menggambar Island di tengah atas layar. Dipanggil dari HUD (in-game) dan dari semua Screen. */
public final class IslandHud {
    private IslandHud() {}

    private static final Anim W = new Anim(96);
    private static final Anim H = new Anim(14);
    private static float musicFade, notifFade;
    private static long lastNs;

    private static float[] target = new float[7];
    private static float[] shown = new float[7];
    private static long lastBandNs;

    private static String lyricNow = "", lyricPrev = "";
    private static float lyricFade = 1f;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        IslandConfig cfg = IslandConfig.get();
        if (!cfg.enabled || mc.getWindow() == null) return;
        if (mc.currentScreen == null && mc.options.hudHidden) return;
        if (cfg.hideInChat && mc.currentScreen instanceof ChatScreen) return;

        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;

        ReminderService.Notification note = ReminderService.current();
        AudioPlayer ap = AudioPlayer.get();
        Track track = ap.track();
        boolean music = track != null && ap.state() != AudioPlayer.State.IDLE;
        boolean notif = note != null;
        List<LyricsService.Line> lyrics = ap.lyrics();
        boolean hasLyrics = music && cfg.showLyrics && lyrics != null;
        boolean pill = cfg.shape == Shape.PILL;

        // ----- ukuran target -----
        float tw, th;
        if (notif) { tw = 270; th = 46; }
        else if (music) { tw = 214; th = hasLyrics ? 52 : 38; }
        else if (cfg.showWhenIdle) { tw = pill ? 64 : 96; th = pill ? 16 : 14; }
        else { tw = 0; th = 0; }
        W.set(tw);
        H.set(th);
        W.update(dt);
        H.update(dt);

        float mt = (music && !notif) ? 1f : 0f;
        float nt = notif ? 1f : 0f;
        if (cfg.animMode == AnimMode.SUPER_LIGHT) {
            musicFade = mt;
            notifFade = nt;
        } else {
            float k = Math.min(1f, dt * 10f * cfg.animSpeed);
            musicFade += (mt - musicFade) * k;
            notifFade += (nt - notifFade) * k;
        }

        int w = Math.round(W.get());
        int h = Math.round(H.get());
        if (w < 2 || h < 2) return;

        // ----- transformasi: titik (0,0) = tengah atas Island -----
        float y0 = cfg.offsetY + (pill ? 4 : 0);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ctx.getScaledWindowWidth() / 2f + cfg.offsetX, y0);
        m.scale(cfg.scale, cfg.scale);

        int x0 = -w / 2;
        int bg = Math.round(255 * cfg.opacity / 100f) << 24;
        if (pill) {
            int r = Math.min(h / 2, 22);
            Shapes.roundedRect(ctx, x0, 0, w, h, r, r, bg);
        } else {
            int r = Math.min(14, h / 2);
            Shapes.roundedRect(ctx, x0, 0, w, h, 0, r, bg);
            drawFlares(ctx, x0, w, bg);
        }

        TextRenderer tr = mc.textRenderer;
        if (musicFade > 0.05f && music) drawMusic(ctx, tr, cfg, ap, track, lyrics, hasLyrics, x0, w, h, musicFade, dt, now);
        if (notifFade > 0.05f && note != null) drawNotification(ctx, tr, note, x0, w, notifFade);

        m.popMatrix();
    }

    /** Lengkung cekung kecil di kiri-kanan atas supaya terlihat menyatu dengan tepi layar (gaya notch). */
    private static void drawFlares(DrawContext ctx, int x0, int w, int color) {
        int fr = 6;
        for (int dy = 0; dy < fr; dy++) {
            double cy = fr - dy - 0.5;
            int dx = (int) Math.round(Math.sqrt(Math.max(0, (double) fr * fr - cy * cy)));
            ctx.fill(x0 - fr + dx, dy, x0, dy + 1, color);
            ctx.fill(x0 + w, dy, x0 + w + fr - dx, dy + 1, color);
        }
    }

    // ------------------------------------------------------------------ musik

    private static void drawMusic(DrawContext ctx, TextRenderer tr, IslandConfig cfg, AudioPlayer ap, Track track,
                                  List<LyricsService.Line> lyrics, boolean hasLyrics,
                                  int x0, int w, int h, float alpha, float dt, long nowNs) {
        int pad = 8;
        int a255 = Math.round(255 * Math.min(1f, alpha));
        int white = (a255 << 24) | 0xFFFFFF;
        int gray = (Math.round(a255 * 0.65f) << 24) | 0xFFFFFF;
        int accent = (a255 << 24) | (cfg.accentColor & 0xFFFFFF);

        // thumbnail
        int cover = 24, cx = x0 + pad, cy = 7;
        if (cfg.showThumbnail) {
            Identifier tex = Thumbnails.get(track);
            if (tex != null) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, cx, cy, 0f, 0f, cover, cover,
                        Thumbnails.SIZE, Thumbnails.SIZE, Thumbnails.SIZE, Thumbnails.SIZE);
            } else {
                Shapes.roundedRect(ctx, cx, cy, cover, cover, 5, 5, Shapes.withAlpha(0xFF3A3A3C, alpha));
            }
        }

        // equalizer
        int bars = switch (cfg.animMode) { case SUPER_LIGHT -> 4; case LIGHT -> 5; case HEAVY -> 7; };
        int eqW = bars * 3 - 1;
        int eqRight = x0 + w - pad;
        int eqLeft = eqRight - eqW;
        if (cfg.showEqualizer) drawEqualizer(ctx, cfg, ap, bars, eqLeft, 19, accent, dt, nowNs);

        // judul + artis
        int textX = cfg.showThumbnail ? cx + cover + 7 : x0 + pad;
        int maxW = (cfg.showEqualizer ? eqLeft - 6 : eqRight) - textX;
        String sub;
        switch (ap.state()) {
            case LOADING -> sub = "Memuat...";
            case ERROR -> sub = "Gagal memutar";
            default -> sub = track.artist == null ? "" : track.artist;
        }
        if (alpha > 0.1f) {
            ctx.drawText(tr, fit(tr, track.title == null ? "" : track.title, maxW), textX, 8, white, false);
            ctx.drawText(tr, fit(tr, sub, maxW), textX, 18, gray, false);
        }

        // lirik
        if (hasLyrics && alpha > 0.1f) {
            String line = LyricsService.lineAt(lyrics, ap.positionMs());
            if (line == null || line.isBlank()) line = "• • •";
            updateLyric(line, cfg, dt);
            int lyW = w - pad * 2;
            if (cfg.animMode == AnimMode.HEAVY && lyricFade < 1f && !lyricPrev.isEmpty()) {
                drawCentered(ctx, tr, lyricPrev, x0 + w / 2, 31, lyW, withA(white, alpha * (1f - lyricFade)));
                drawCentered(ctx, tr, lyricNow, x0 + w / 2, 33, lyW, withA(white, alpha * lyricFade));
            } else {
                drawCentered(ctx, tr, lyricNow, x0 + w / 2, 33, lyW, white);
            }
        }

        // progress
        long dur = ap.durationMs();
        if (dur > 0) {
            int px = x0 + pad, pw = w - pad * 2, py = h - 5;
            ctx.fill(px, py, px + pw, py + 2, (Math.round(a255 * 0.22f) << 24) | 0xFFFFFF);
            int filled = (int) (pw * Math.min(1.0, ap.positionMs() / (double) dur));
            if (filled > 0) ctx.fill(px, py, px + filled, py + 2, accent);
        }
    }

    private static void drawEqualizer(DrawContext ctx, IslandConfig cfg, AudioPlayer ap, int bars,
                                      int left, int centerY, int color, float dt, long nowNs) {
        if (shown.length != bars) { shown = new float[bars]; target = new float[bars]; }

        long interval = switch (cfg.animMode) {
            case SUPER_LIGHT -> 125_000_000L; // 8 fps, tanpa smoothing
            case LIGHT -> 50_000_000L;        // 20 fps
            case HEAVY -> 0L;                 // tiap frame
        };
        if (nowNs - lastBandNs >= interval) {
            lastBandNs = nowNs;
            if (ap.state() == AudioPlayer.State.PLAYING) {
                float[] b = ap.bands(bars);
                System.arraycopy(b, 0, target, 0, bars);
            } else {
                java.util.Arrays.fill(target, 0f);
            }
        }
        float attack = 1f - (float) Math.exp(-28f * cfg.animSpeed * dt);
        float release = 1f - (float) Math.exp(-9f * cfg.animSpeed * dt);
        int maxH = 16;
        for (int i = 0; i < bars; i++) {
            if (cfg.animMode == AnimMode.SUPER_LIGHT) shown[i] = target[i];
            else shown[i] += (target[i] - shown[i]) * (target[i] > shown[i] ? attack : release);
            int bh = Math.max(2, Math.round(shown[i] * maxH));
            int bx = left + i * 3;
            ctx.fill(bx, centerY - bh / 2, bx + 2, centerY - bh / 2 + bh, color);
        }
    }

    private static void updateLyric(String line, IslandConfig cfg, float dt) {
        if (!line.equals(lyricNow)) {
            lyricPrev = lyricNow;
            lyricNow = line;
            lyricFade = cfg.animMode == AnimMode.HEAVY ? 0f : 1f;
        }
        if (lyricFade < 1f) lyricFade = Math.min(1f, lyricFade + dt * 6f * cfg.animSpeed);
    }

    // ------------------------------------------------------------- notifikasi

    private static void drawNotification(DrawContext ctx, TextRenderer tr, ReminderService.Notification n,
                                         int x0, int w, float alpha) {
        int a255 = Math.round(255 * Math.min(1f, alpha));
        int white = (a255 << 24) | 0xFFFFFF;
        int gray = (Math.round(a255 * 0.7f) << 24) | 0xFFFFFF;
        int accent = Shapes.withAlpha(n.accent() | 0xFF000000, alpha);

        int ix = x0 + 10, iy = 10, is = 26;
        Shapes.roundedRect(ctx, ix, iy, is, is, is / 2, is / 2, accent);
        ctx.drawText(tr, "!", ix + is / 2 - 2, iy + is / 2 - 4, white, false);

        int textX = ix + is + 9;
        int maxW = x0 + w - 10 - textX;
        if (alpha > 0.1f) {
            ctx.drawText(tr, fit(tr, n.title(), maxW), textX, 11, white, false);
            ctx.drawText(tr, fit(tr, n.body(), maxW), textX, 25, gray, false);
        }
    }

    // ----------------------------------------------------------------- helper

    private static String fit(TextRenderer tr, String s, int maxW) {
        if (s == null || maxW <= 0) return "";
        if (tr.getWidth(s) <= maxW) return s;
        String cut = tr.trimToWidth(s, Math.max(0, maxW - tr.getWidth("...")));
        return cut + "...";
    }

    private static void drawCentered(DrawContext ctx, TextRenderer tr, String s, int cx, int y, int maxW, int color) {
        if ((color >>> 24) < 8) return;
        String t = fit(tr, s, maxW);
        ctx.drawText(tr, t, cx - tr.getWidth(t) / 2, y, color, false);
    }

    private static int withA(int argb, float f) {
        return Shapes.withAlpha(argb, f);
    }
}
