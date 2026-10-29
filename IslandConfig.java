package id.dynamicisland.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Semua pengaturan Island. Disimpan di config/dynamicisland.json */
public class IslandConfig {
    public enum Shape { NOTCH, PILL }
    public enum AnimMode { SUPER_LIGHT, LIGHT, HEAVY }

    // Tampilan
    public boolean enabled = true;
    public Shape shape = Shape.NOTCH;
    public int offsetX = 0;            // geser dari tengah (px)
    public int offsetY = 0;            // jarak dari atas layar (px); NOTCH menempel di atas, PILL biasanya 4-8
    public float scale = 1.0f;         // 0.6 - 2.0
    public int opacity = 100;          // 20 - 100 (%)
    public int accentColor = 0x1DB954; // warna equalizer / progress (RGB)
    public boolean hideInChat = false; // sembunyikan saat chat terbuka
    public boolean showWhenIdle = true; // tampilkan notch kosong saat tidak ada musik/notifikasi

    // Animasi
    public AnimMode animMode = AnimMode.LIGHT;
    public float animSpeed = 1.0f;     // 0.5 - 2.0

    // Musik
    public boolean showThumbnail = true;
    public boolean showEqualizer = true;
    public boolean showLyrics = true;
    public int volume = 70;            // 0 - 100
    public String jamendoClientId = "56d30c95"; // ganti dengan client_id gratis milikmu di devportal.jamendo.com kalau hasil pencarian kosong

    // Pengingat
    public boolean reminderEnabled = true;
    public String bedtime = "20:30";   // HH:mm, notch melebar & mengingatkan selesai main
    public int notifySeconds = 8;      // lama notifikasi tampil

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("dynamicisland.json");
    private static IslandConfig instance;

    public static IslandConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                instance = GSON.fromJson(Files.readString(FILE), IslandConfig.class);
            }
        } catch (Exception ignored) { }
        if (instance == null) instance = new IslandConfig();
        instance.clamp();
    }

    public void save() {
        clamp();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (Exception ignored) { }
    }

    private void clamp() {
        scale = Math.max(0.6f, Math.min(2.0f, scale));
        animSpeed = Math.max(0.5f, Math.min(2.0f, animSpeed));
        opacity = Math.max(20, Math.min(100, opacity));
        volume = Math.max(0, Math.min(100, volume));
        notifySeconds = Math.max(3, Math.min(30, notifySeconds));
        if (shape == null) shape = Shape.NOTCH;
        if (animMode == null) animMode = AnimMode.LIGHT;
        if (bedtime == null || !bedtime.matches("\\d{1,2}:\\d{2}")) bedtime = "20:30";
    }
}
