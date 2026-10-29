package id.dynamicisland.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Lagu yang sudah di-download untuk diputar offline. Folder: .minecraft/dynamicisland/music */
public final class Library {
    private Library() {}

    private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("dynamicisland").resolve("music");
    private static final Path INDEX = DIR.resolve("index.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> DOWNLOADING = ConcurrentHashMap.newKeySet();
    private static List<Track> tracks;

    public static String safeId(Track t) {
        return String.valueOf(t.id).replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static synchronized List<Track> load() {
        if (tracks == null) {
            tracks = new ArrayList<>();
            try {
                if (Files.exists(INDEX)) {
                    List<Track> read = GSON.fromJson(Files.readString(INDEX), new TypeToken<List<Track>>() {}.getType());
                    if (read != null) tracks.addAll(read);
                }
            } catch (Exception ignored) { }
        }
        return tracks;
    }

    private static void saveIndex() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(INDEX, GSON.toJson(load()));
        } catch (Exception ignored) { }
    }

    public static synchronized List<Track> downloaded() { return new ArrayList<>(load()); }

    public static synchronized Track find(String id) {
        if (id == null) return null;
        for (Track t : load()) if (id.equals(t.id)) return t;
        return null;
    }

    public static boolean isDownloading(Track t) { return DOWNLOADING.contains(t.id); }

    public static Path audioPath(Track t) {
        Track local = t.localAudio != null ? t : find(t.id);
        return local == null || local.localAudio == null ? null : DIR.resolve(local.localAudio);
    }

    public static Path coverPath(Track t) {
        Track local = t.localCover != null ? t : find(t.id);
        if (local == null || local.localCover == null) return null;
        Path p = DIR.resolve(local.localCover);
        return Files.exists(p) ? p : null;
    }

    /** Download di background: mp3 + cover + lirik. onDone dipanggil dari thread background. */
    public static void download(Track t, Runnable onDone) {
        if (t.streamUrl == null || !DOWNLOADING.add(t.id)) return;
        Thread th = new Thread(() -> {
            try {
                String base = safeId(t);
                Http.download(t.streamUrl, DIR.resolve(base + ".mp3"));
                Track c = new Track();
                c.id = t.id; c.title = t.title; c.artist = t.artist; c.album = t.album;
                c.durationSec = t.durationSec; c.streamUrl = t.streamUrl; c.coverUrl = t.coverUrl;
                c.localAudio = base + ".mp3";
                try {
                    if (t.coverUrl != null) {
                        Http.download(t.coverUrl, DIR.resolve(base + ".jpg"));
                        c.localCover = base + ".jpg";
                    }
                } catch (Exception ignored) { }
                try {
                    List<LyricsService.Line> l = LyricsService.fetch(t);
                    if (l != null) Files.writeString(DIR.resolve(base + ".lrc"), toLrc(l));
                } catch (Exception ignored) { }
                synchronized (Library.class) {
                    load().removeIf(x -> t.id.equals(x.id));
                    load().add(c);
                    saveIndex();
                }
            } catch (Exception ignored) {
            } finally {
                DOWNLOADING.remove(t.id);
                if (onDone != null) onDone.run();
            }
        }, "DynamicIsland-Download");
        th.setDaemon(true);
        th.start();
    }

    public static synchronized void delete(Track t) {
        Track local = find(t.id);
        if (local == null) return;
        String base = safeId(local);
        try {
            Files.deleteIfExists(DIR.resolve(base + ".mp3"));
            Files.deleteIfExists(DIR.resolve(base + ".jpg"));
            Files.deleteIfExists(DIR.resolve(base + ".lrc"));
        } catch (Exception ignored) { }
        load().removeIf(x -> t.id.equals(x.id));
        saveIndex();
    }

    /** Lirik lokal dulu (jalan offline), kalau tidak ada baru online. Blocking. */
    public static List<LyricsService.Line> loadLyrics(Track t) {
        try {
            Path lrc = DIR.resolve(safeId(t) + ".lrc");
            if (Files.exists(lrc)) return LyricsService.parseLrc(Files.readString(lrc));
        } catch (Exception ignored) { }
        return LyricsService.fetch(t);
    }

    private static String toLrc(List<LyricsService.Line> lines) {
        StringBuilder sb = new StringBuilder();
        for (LyricsService.Line l : lines) {
            long ms = l.timeMs();
            sb.append(String.format("[%02d:%02d.%03d]%s%n", ms / 60000, (ms / 1000) % 60, ms % 1000, l.text()));
        }
        return sb.toString();
    }
}
