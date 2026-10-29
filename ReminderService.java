package id.dynamicisland.reminder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import id.dynamicisland.config.IslandConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Mengecek jam sistem dan memunculkan notifikasi di Island (bedtime + jadwal kegiatan). */
public final class ReminderService {
    private ReminderService() {}

    public record Notification(String title, String body, int accent, int seconds) {}

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dynamicisland-schedule.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final List<ScheduleEntry> ENTRIES = new ArrayList<>();
    private static final Deque<Notification> QUEUE = new ArrayDeque<>();
    private static final Set<String> FIRED = new HashSet<>();
    private static Notification current;
    private static long currentUntilMs;
    private static int tickCounter;
    private static boolean loaded;

    // ---------- jadwal ----------

    public static synchronized List<ScheduleEntry> entries() {
        ensureLoaded();
        return ENTRIES;
    }

    public static synchronized void saveEntries() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(ENTRIES));
        } catch (Exception ignored) { }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                List<ScheduleEntry> read = GSON.fromJson(Files.readString(FILE), new TypeToken<List<ScheduleEntry>>() {}.getType());
                if (read != null) ENTRIES.addAll(read);
            }
        } catch (Exception ignored) { }
    }

    // ---------- tick (dipanggil tiap client tick) ----------

    public static synchronized void tick() {
        ensureLoaded();
        if (++tickCounter % 20 != 0) return; // cek ~1x per detik

        LocalDateTime now = LocalDateTime.now();
        String hhmm = String.format("%02d:%02d", now.getHour(), now.getMinute());
        String today = LocalDate.now().toString();
        IslandConfig cfg = IslandConfig.get();

        if (cfg.reminderEnabled) {
            String bed = normalize(cfg.bedtime);
            if (bed.equals(hhmm) && FIRED.add(today + "|bed|" + hhmm)) {
                push(new Notification("Waktunya selesai main",
                        "Sudah jam " + bed + ". Simpan dulu dunia kamu, istirahat ya!",
                        0xFFFF9F0A, cfg.notifySeconds));
            }
            for (ScheduleEntry e : ENTRIES) {
                if (!e.enabled || !e.runsOn(now.getDayOfWeek())) continue;
                if (normalize(e.time).equals(hhmm) && FIRED.add(today + "|" + e.id + "|" + hhmm)) {
                    push(new Notification(e.label, "Sekarang jam " + normalize(e.time), 0xFF0A84FF, cfg.notifySeconds));
                }
            }
        }
        if (FIRED.size() > 200) FIRED.removeIf(k -> !k.startsWith(today));

        long nowMs = System.currentTimeMillis();
        if (current != null && nowMs >= currentUntilMs) current = null;
        if (current == null && !QUEUE.isEmpty()) {
            current = QUEUE.poll();
            currentUntilMs = nowMs + current.seconds() * 1000L;
        }
    }

    public static synchronized void push(Notification n) { QUEUE.add(n); }

    /** Untuk tombol "Tes notifikasi" di Island Settings. */
    public static void test() {
        push(new Notification("Tes notifikasi", "Beginilah pengingat tampil di notch.", 0xFF30D158, 5));
    }

    public static synchronized Notification current() { return current; }

    public static synchronized void dismiss() { current = null; }

    private static String normalize(String t) {
        try {
            String[] p = t.trim().split(":");
            return String.format("%02d:%02d", Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        } catch (Exception e) {
            return "";
        }
    }
}
