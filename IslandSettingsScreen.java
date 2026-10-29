package id.dynamicisland.screen;

import id.dynamicisland.config.IslandConfig;
import id.dynamicisland.config.IslandConfig.AnimMode;
import id.dynamicisland.config.IslandConfig.Shape;
import id.dynamicisland.reminder.ReminderService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Pengaturan Island: posisi, bentuk, animasi (3 tingkat performa), dan pengingat. */
public class IslandSettingsScreen extends Screen {
    private static final String[] ACCENT_NAMES = {"Hijau", "Biru", "Pink", "Oranye", "Putih", "Ungu"};
    private static final int[] ACCENT_COLORS = {0x1DB954, 0x0A84FF, 0xFF2D55, 0xFF9F0A, 0xFFFFFF, 0xBF5AF2};

    private final Screen parent;
    private final IslandConfig cfg = IslandConfig.get();
    private static int tab = 0; // 0 tampilan, 1 animasi, 2 pengingat

    public IslandSettingsScreen(Screen parent) {
        super(Text.literal("Island Settings"));
        this.parent = parent;
    }

    private int colX(int i) { return i % 2 == 0 ? width / 2 - 154 : width / 2 + 4; }
    private int rowY(int i) { return 56 + (i / 2) * 24; }

    @Override
    protected void init() {
        int cx = width / 2;
        String[] names = {"Tampilan", "Animasi", "Pengingat"};
        for (int i = 0; i < 3; i++) {
            final int t = i;
            ButtonWidget b = ButtonWidget.builder(Text.literal(names[i]), x -> { tab = t; clearAndInit(); })
                    .dimensions(cx - 153 + i * 104, 28, 98, 20).build();
            b.active = tab != i;
            addDrawableChild(b);
        }

        switch (tab) {
            case 0 -> buildLook();
            case 1 -> buildAnim();
            default -> buildReminder();
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Selesai"), b -> close())
                .dimensions(cx - 100, height - 28, 200, 20).build());
    }

    // ---------------------------------------------------------------- tab 0

    private void buildLook() {
        int i = 0;
        add(toggle("Island", () -> cfg.enabled, v -> cfg.enabled = v), i++);
        add(cycleShape(), i++);
        add(new IntSlider(colX(i), rowY(i), 150, 20, "Posisi X", -400, 400, cfg.offsetX, "", v -> cfg.offsetX = v), i++);
        add(new IntSlider(colX(i), rowY(i), 150, 20, "Posisi Y", 0, 80, cfg.offsetY, "", v -> cfg.offsetY = v), i++);
        add(new IntSlider(colX(i), rowY(i), 150, 20, "Ukuran", 60, 200, Math.round(cfg.scale * 100), "%", v -> cfg.scale = v / 100f), i++);
        add(new IntSlider(colX(i), rowY(i), 150, 20, "Opacity", 20, 100, cfg.opacity, "%", v -> cfg.opacity = v), i++);
        add(cycleAccent(), i++);
        add(toggle("Notch saat kosong", () -> cfg.showWhenIdle, v -> cfg.showWhenIdle = v), i++);
        add(toggle("Sembunyi di chat", () -> cfg.hideInChat, v -> cfg.hideInChat = v), i++);
    }

    private ButtonWidget cycleShape() {
        return ButtonWidget.builder(Text.literal("Bentuk: " + (cfg.shape == Shape.NOTCH ? "Notch" : "Pil")), b -> {
            cfg.shape = cfg.shape == Shape.NOTCH ? Shape.PILL : Shape.NOTCH;
            b.setMessage(Text.literal("Bentuk: " + (cfg.shape == Shape.NOTCH ? "Notch" : "Pil")));
        }).dimensions(colX(1), rowY(1), 150, 20).build();
    }

    private ButtonWidget cycleAccent() {
        return ButtonWidget.builder(Text.literal("Warna aksen: " + accentName()), b -> {
            int idx = 0;
            for (int k = 0; k < ACCENT_COLORS.length; k++) if (ACCENT_COLORS[k] == cfg.accentColor) idx = k;
            cfg.accentColor = ACCENT_COLORS[(idx + 1) % ACCENT_COLORS.length];
            b.setMessage(Text.literal("Warna aksen: " + accentName()));
        }).dimensions(colX(6), rowY(6), 150, 20).build();
    }

    private String accentName() {
        for (int k = 0; k < ACCENT_COLORS.length; k++) if (ACCENT_COLORS[k] == cfg.accentColor) return ACCENT_NAMES[k];
        return "Custom";
    }

    // ---------------------------------------------------------------- tab 1

    private void buildAnim() {
        add(ButtonWidget.builder(Text.literal("Mode: " + modeName()), b -> {
            AnimMode[] all = AnimMode.values();
            cfg.animMode = all[(cfg.animMode.ordinal() + 1) % all.length];
            b.setMessage(Text.literal("Mode: " + modeName()));
        }).dimensions(colX(0), rowY(0), 150, 20).build(), 0);
        add(new IntSlider(colX(1), rowY(1), 150, 20, "Kecepatan", 50, 200, Math.round(cfg.animSpeed * 100), "%", v -> cfg.animSpeed = v / 100f), 1);
        add(toggle("Thumbnail", () -> cfg.showThumbnail, v -> cfg.showThumbnail = v), 2);
        add(toggle("Equalizer", () -> cfg.showEqualizer, v -> cfg.showEqualizer = v), 3);
        add(toggle("Lirik", () -> cfg.showLyrics, v -> cfg.showLyrics = v), 4);
    }

    private String modeName() {
        return switch (cfg.animMode) { case SUPER_LIGHT -> "Super Ringan"; case LIGHT -> "Ringan"; case HEAVY -> "Berat"; };
    }

    private String modeDesc() {
        return switch (cfg.animMode) {
            case SUPER_LIGHT -> "Tanpa easing, equalizer 4 batang @8fps. Cocok untuk PC lemah.";
            case LIGHT -> "Easing halus, equalizer 5 batang @20fps. Seimbang.";
            case HEAVY -> "Efek pegas ala iOS, equalizer 7 batang tiap frame, lirik cross-fade.";
        };
    }

    // ---------------------------------------------------------------- tab 2

    private void buildReminder() {
        add(toggle("Pengingat", () -> cfg.reminderEnabled, v -> cfg.reminderEnabled = v), 0);
        add(new IntSlider(colX(1), rowY(1), 150, 20, "Lama notif", 3, 30, cfg.notifySeconds, "s", v -> cfg.notifySeconds = v), 1);

        ButtonWidget label = ButtonWidget.builder(Text.literal("Jam selesai main:"), b -> { })
                .dimensions(colX(2), rowY(2), 150, 20).build();
        label.active = false;
        add(label, 2);
        TextFieldWidget field = new TextFieldWidget(textRenderer, colX(3), rowY(2), 150, 20, Text.literal("HH:mm"));
        field.setMaxLength(5);
        field.setText(cfg.bedtime);
        field.setChangedListener(s -> { if (s.matches("\\d{1,2}:\\d{2}")) cfg.bedtime = s; });
        addDrawableChild(field);

        add(ButtonWidget.builder(Text.literal("Jadwal kegiatan..."), b -> client.setScreen(new ScheduleScreen(this)))
                .dimensions(colX(4), rowY(4), 150, 20).build(), 4);
        add(ButtonWidget.builder(Text.literal("Tes notifikasi"), b -> ReminderService.test())
                .dimensions(colX(5), rowY(4), 150, 20).build(), 5);
    }

    // ---------------------------------------------------------------- util

    /** Tambahkan widget dan taruh di slot grid dua kolom ke-i. */
    private <T extends net.minecraft.client.gui.widget.ClickableWidget> void add(T w, int i) {
        w.setX(colX(i));
        w.setY(rowY(i));
        addDrawableChild(w);
    }

    private ButtonWidget toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        return ButtonWidget.builder(Text.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(Text.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")));
        }).dimensions(0, 0, 150, 20).build();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        if (tab == 1) ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(modeDesc()), width / 2, rowY(6) + 4, 0xFFAAAAAA);
    }

    @Override
    public void removed() {
        cfg.save();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
