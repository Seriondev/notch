package id.dynamicisland.screen;

import id.dynamicisland.reminder.ReminderService;
import id.dynamicisland.reminder.ScheduleEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Tambah / hapus / aktif-nonaktifkan kegiatan terjadwal yang muncul sebagai notifikasi di Island. */
public class ScheduleScreen extends Screen {
    private static final int ROWS = 5;
    private static final String[] DAY_NAMES = {"Setiap hari", "Sen-Jum", "Sab-Min"};
    private static final int[] DAY_MASKS = {0b1111111, 0b0011111, 0b1100000};

    private final Screen parent;
    private int page = 0;
    private int dayPreset = 0;
    private String draftLabel = "";
    private String draftTime = "06:00";
    private String error = "";

    public ScheduleScreen(Screen parent) {
        super(Text.literal("Jadwal Kegiatan"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2;

        // form tambah kegiatan
        TextFieldWidget label = new TextFieldWidget(textRenderer, cx - 180, 28, 150, 20, Text.literal("Nama"));
        label.setMaxLength(40);
        label.setText(draftLabel);
        label.setPlaceholder(Text.literal("Nama kegiatan"));
        label.setChangedListener(s -> draftLabel = s);
        addDrawableChild(label);

        TextFieldWidget time = new TextFieldWidget(textRenderer, cx - 26, 28, 50, 20, Text.literal("Jam"));
        time.setMaxLength(5);
        time.setText(draftTime);
        time.setChangedListener(s -> draftTime = s);
        addDrawableChild(time);

        addDrawableChild(ButtonWidget.builder(Text.literal(DAY_NAMES[dayPreset]), b -> {
            dayPreset = (dayPreset + 1) % DAY_NAMES.length;
            b.setMessage(Text.literal(DAY_NAMES[dayPreset]));
        }).dimensions(cx + 28, 28, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Tambah"), b -> addEntry())
                .dimensions(cx + 112, 28, 68, 20).build());

        // daftar
        List<ScheduleEntry> list = ReminderService.entries();
        int from = page * ROWS;
        for (int i = 0; i < ROWS && from + i < list.size(); i++) {
            final ScheduleEntry e = list.get(from + i);
            int y = 62 + i * 24;
            addDrawableChild(ButtonWidget.builder(Text.literal(e.enabled ? "ON" : "OFF"), b -> {
                e.enabled = !e.enabled;
                ReminderService.saveEntries();
                b.setMessage(Text.literal(e.enabled ? "ON" : "OFF"));
            }).dimensions(cx - 180, y, 40, 20).build());

            ButtonWidget info = ButtonWidget.builder(
                    Text.literal(e.time + "  " + trim(e.label, 160) + "  (" + e.daysLabel() + ")"), b -> { })
                    .dimensions(cx - 136, y, 270, 20).build();
            info.active = false;
            addDrawableChild(info);

            addDrawableChild(ButtonWidget.builder(Text.literal("Del"), b -> {
                list.remove(e);
                ReminderService.saveEntries();
                clearAndInit();
            }).dimensions(cx + 138, y, 42, 20).build());
        }

        ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> { page--; clearAndInit(); })
                .dimensions(cx - 180, 62 + ROWS * 24 + 2, 30, 20).build();
        prev.active = page > 0;
        addDrawableChild(prev);
        ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> { page++; clearAndInit(); })
                .dimensions(cx + 150, 62 + ROWS * 24 + 2, 30, 20).build();
        next.active = (page + 1) * ROWS < list.size();
        addDrawableChild(next);

        addDrawableChild(ButtonWidget.builder(Text.literal("Selesai"), b -> close())
                .dimensions(cx - 100, height - 28, 200, 20).build());
    }

    private void addEntry() {
        String t = draftTime.trim();
        if (draftLabel.isBlank()) { error = "Nama kegiatan belum diisi"; return; }
        if (!t.matches("\\d{1,2}:\\d{2}")) { error = "Format jam harus HH:mm, contoh 06:00"; return; }
        int h = Integer.parseInt(t.split(":")[0]);
        int m = Integer.parseInt(t.split(":")[1]);
        if (h > 23 || m > 59) { error = "Jam tidak valid"; return; }

        ScheduleEntry e = new ScheduleEntry();
        e.label = draftLabel.trim();
        e.time = String.format("%02d:%02d", h, m);
        e.daysMask = DAY_MASKS[dayPreset];
        ReminderService.entries().add(e);
        ReminderService.saveEntries();
        draftLabel = "";
        error = "";
        page = (ReminderService.entries().size() - 1) / ROWS;
        clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = width / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, title, cx, 10, 0xFFFFFFFF);
        int by = 62 + ROWS * 24 + 8;
        int total = Math.max(1, (ReminderService.entries().size() + ROWS - 1) / ROWS);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Hal " + (page + 1) + "/" + total), cx, by, 0xFFAAAAAA);
        if (!error.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(error), cx, by + 14, 0xFFFF6B6B);
        else if (ReminderService.entries().isEmpty())
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Belum ada kegiatan. Contoh: \"Berangkat sekolah\" jam 06:00"), cx, by + 14, 0xFFAAAAAA);
    }

    private String trim(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        return textRenderer.trimToWidth(s, maxPx - textRenderer.getWidth("...")) + "...";
    }

    @Override
    public void close() { client.setScreen(parent); }
}
