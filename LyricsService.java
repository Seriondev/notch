package id.dynamicisland.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lirik tersinkron (format LRC) dari lrclib.net. Kalau tidak ada, hasilnya null. */
public final class LyricsService {
    private LyricsService() {}

    public record Line(long timeMs, String text) {}

    private static final Pattern LRC = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]\\s*(.*)");

    /** Blocking — panggil dari thread background. */
    public static List<Line> fetch(Track t) {
        try {
            String base = "https://lrclib.net/api/";
            String a = URLEncoder.encode(t.artist == null ? "" : t.artist, StandardCharsets.UTF_8);
            String n = URLEncoder.encode(t.title == null ? "" : t.title, StandardCharsets.UTF_8);

            String body = Http.getString(base + "get?artist_name=" + a + "&track_name=" + n
                    + (t.durationSec > 0 ? "&duration=" + t.durationSec : ""));
            String synced = null;
            if (body != null) synced = syncedOf(JsonParser.parseString(body).getAsJsonObject());

            if (synced == null) {
                String sb = Http.getString(base + "search?artist_name=" + a + "&track_name=" + n);
                if (sb != null) {
                    JsonArray arr = JsonParser.parseString(sb).getAsJsonArray();
                    for (JsonElement e : arr) {
                        synced = syncedOf(e.getAsJsonObject());
                        if (synced != null) break;
                    }
                }
            }
            return synced == null ? null : parseLrc(synced);
        } catch (Exception e) {
            return null;
        }
    }

    private static String syncedOf(JsonObject o) {
        if (!o.has("syncedLyrics") || o.get("syncedLyrics").isJsonNull()) return null;
        String s = o.get("syncedLyrics").getAsString();
        return s.isBlank() ? null : s;
    }

    public static List<Line> parseLrc(String lrc) {
        List<Line> lines = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = LRC.matcher(raw.trim());
            if (!m.matches()) continue;
            long min = Long.parseLong(m.group(1));
            long sec = Long.parseLong(m.group(2));
            long ms = 0;
            if (m.group(3) != null) {
                String f = (m.group(3) + "00").substring(0, 3);
                ms = Long.parseLong(f);
            }
            String text = m.group(4).trim();
            lines.add(new Line(min * 60_000 + sec * 1000 + ms, text));
        }
        return lines.isEmpty() ? null : lines;
    }

    /** Baris yang sedang dinyanyikan pada posisi tertentu ("" kalau jeda instrumental). */
    public static String lineAt(List<Line> lines, long posMs) {
        if (lines == null) return null;
        String cur = "";
        for (Line l : lines) {
            if (l.timeMs() <= posMs) cur = l.text(); else break;
        }
        return cur;
    }
}
