package id.dynamicisland.screen;

import id.dynamicisland.config.IslandConfig;
import id.dynamicisland.music.AudioPlayer;
import id.dynamicisland.music.JamendoProvider;
import id.dynamicisland.music.Library;
import id.dynamicisland.music.Track;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Music player: cari lagu online, download untuk offline, dan kontrol pemutaran. */
public class MusicScreen extends Screen {
    private static final int ROWS = 5;

    private static List<Track> onlineResults = new ArrayList<>();
    private static String lastQuery = "";
    private static boolean offlineTab = false;
    private static String status = "Ketik judul / artis lalu tekan Cari";

    private final Screen parent;
    private int page = 0;
    private TextFieldWidget searchField;

    public MusicScreen(Screen parent) {
        super(Text.literal("Music Player"));
        this.parent = parent;
    }

    private List<Track> currentList() {
        return offlineTab ? Library.downloaded() : onlineResults;
    }

    @Override
    protected void init() {
        int cx = width / 2;

        searchField = new TextFieldWidget(textRenderer, cx - 150, 24, 200, 20, Text.literal("Cari"));
        searchField.setMaxLength(80);
        searchField.setText(lastQuery);
        searchField.setPlaceholder(Text.literal("Cari lagu, artis..."));
        searchField.setChangedListener(s -> lastQuery = s);
        addDrawableChild(searchField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Cari"), b -> { offlineTab = false; search(); })
                .dimensions(cx + 54, 24, 96, 20).build());

        ButtonWidget online = ButtonWidget.builder(Text.literal("Online"), b -> { offlineTab = false; page = 0; clearAndInit(); })
                .dimensions(cx - 150, 48, 100, 20).build();
        online.active = offlineTab;
        addDrawableChild(online);
        ButtonWidget offline = ButtonWidget.builder(Text.literal("Offline (" + Library.downloaded().size() + ")"),
                        b -> { offlineTab = true; page = 0; clearAndInit(); })
                .dimensions(cx - 46, 48, 100, 20).build();
        offline.active = !offlineTab;
        addDrawableChild(offline);

        // daftar lagu
        List<Track> list = currentList();
        int from = page * ROWS;
        for (int i = 0; i < ROWS && from + i < list.size(); i++) {
            final Track t = list.get(from + i);
            int y = 74 + i * 24;
            boolean saved = Library.find(t.id) != null;
            String label = (saved ? "[Offline] " : "") + t.displayName() + "  " + fmt(t.durationSec);
            addDrawableChild(ButtonWidget.builder(Text.literal(trim(label, 250)), b -> AudioPlayer.get().play(t, new ArrayList<>(list)))
                    .dimensions(cx - 150, y, 256, 20).build());

            String act = saved ? "Del" : (Library.isDownloading(t) ? "..." : "DL");
            ButtonWidget action = ButtonWidget.builder(Text.literal(act), b -> {
                if (saved) {
                    Library.delete(t);
                    clearAndInit();
                } else {
                    status = "Mengunduh " + t.displayName() + "...";
                    Library.download(t, () -> client.execute(() -> {
                        status = "Tersimpan: " + t.displayName();
                        if (client.currentScreen == this) clearAndInit();
                    }));
                    clearAndInit();
                }
            }).dimensions(cx + 110, y, 40, 20).build();
            action.active = !Library.isDownloading(t);
            addDrawableChild(action);
        }

        // halaman
        int py = 74 + ROWS * 24 + 2;
        ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> { page--; clearAndInit(); })
                .dimensions(cx - 150, py, 30, 20).build();
        prev.active = page > 0;
        addDrawableChild(prev);
        ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> { page++; clearAndInit(); })
                .dimensions(cx + 120, py, 30, 20).build();
        next.active = (page + 1) * ROWS < list.size();
        addDrawableChild(next);

        // transport
        int ty = height - 50;
        int tx = cx - 163;
        addDrawableChild(ButtonWidget.builder(Text.literal("Prev"), b -> AudioPlayer.get().previous())
                .dimensions(tx, ty, 52, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Play/Pause"), b -> AudioPlayer.get().togglePause())
                .dimensions(tx + 56, ty, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next"), b -> AudioPlayer.get().next())
                .dimensions(tx + 130, ty, 52, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop"), b -> AudioPlayer.get().stop())
                .dimensions(tx + 186, ty, 52, 20).build());
        addDrawableChild(new IntSlider(tx + 244, ty, 82, 20, "Vol", 0, 100, IslandConfig.get().volume, "",
                v -> AudioPlayer.get().setVolume(v)));

        addDrawableChild(ButtonWidget.builder(Text.literal("Island Settings"), b -> client.setScreen(new IslandSettingsScreen(this)))
                .dimensions(cx - 150, height - 26, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Selesai"), b -> close())
                .dimensions(cx + 4, height - 26, 146, 20).build());
    }

    private void search() {
        String q = lastQuery.trim();
        status = "Mencari...";
        page = 0;
        Thread th = new Thread(() -> {
            String msg;
            List<Track> res = new ArrayList<>();
            try {
                res = q.isEmpty() ? JamendoProvider.popular() : JamendoProvider.search(q);
                msg = res.isEmpty() ? "Tidak ada hasil. Cek jamendoClientId di config kalau selalu kosong."
                        : res.size() + " lagu ditemukan";
            } catch (Exception e) {
                msg = "Tidak bisa terhubung ke internet. Buka tab Offline untuk lagu yang sudah di-download.";
            }
            final List<Track> fres = res;
            final String fmsg = msg;
            client.execute(() -> {
                onlineResults = fres;
                status = fmsg;
                if (client.currentScreen == this) clearAndInit();
            });
        }, "DynamicIsland-Search");
        th.setDaemon(true);
        th.start();
        clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = width / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, title, cx, 10, 0xFFFFFFFF);

        int pageY = 74 + ROWS * 24 + 8;
        int total = Math.max(1, (currentList().size() + ROWS - 1) / ROWS);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Hal " + (page + 1) + "/" + total), cx, pageY, 0xFFAAAAAA);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(trim(status, 300)), cx, pageY + 14, 0xFFCCCCCC);

        AudioPlayer ap = AudioPlayer.get();
        Track t = ap.track();
        String np = t == null ? "Tidak ada lagu diputar" : switch (ap.state()) {
            case LOADING -> "Memuat: " + t.displayName();
            case ERROR -> "Gagal: " + ap.error();
            case PAUSED -> "Jeda: " + t.displayName();
            default -> "Memutar: " + t.displayName() + "  " + fmt((int) (ap.positionMs() / 1000)) + " / " + fmt(t.durationSec);
        };
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(trim(np, 320)), cx, height - 64, 0xFFFFFFFF);
    }

    private String trim(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        return textRenderer.trimToWidth(s, maxPx - textRenderer.getWidth("...")) + "...";
    }

    private static String fmt(int sec) {
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() { client.setScreen(parent); }

    @Override
    public void removed() { IslandConfig.get().save(); }
}
