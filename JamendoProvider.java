package id.dynamicisland.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import id.dynamicisland.config.IslandConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Sumber musik online: Jamendo (jutaan lagu berlisensi bebas, stream penuh + boleh di-download).
 * Spotify/YouTube Music tidak punya API legal untuk stream & download di dalam game.
 */
public final class JamendoProvider {
    private JamendoProvider() {}

    private static final String BASE = "https://api.jamendo.com/v3.0/tracks/";

    public static List<Track> search(String query) throws Exception {
        String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = BASE + "?client_id=" + IslandConfig.get().jamendoClientId
                + "&format=json&limit=25&imagesize=300&audioformat=mp32&order=popularity_total"
                + "&search=" + q;
        return parse(Http.getString(url));
    }

    /** Lagu populer saat query kosong. */
    public static List<Track> popular() throws Exception {
        String url = BASE + "?client_id=" + IslandConfig.get().jamendoClientId
                + "&format=json&limit=25&imagesize=300&audioformat=mp32&order=popularity_week";
        return parse(Http.getString(url));
    }

    private static List<Track> parse(String body) {
        List<Track> out = new ArrayList<>();
        if (body == null) return out;
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray results = root.getAsJsonArray("results");
        if (results == null) return out;
        for (JsonElement el : results) {
            JsonObject o = el.getAsJsonObject();
            Track t = new Track();
            t.id = str(o, "id");
            t.title = str(o, "name");
            t.artist = str(o, "artist_name");
            t.album = str(o, "album_name");
            t.durationSec = o.has("duration") && !o.get("duration").isJsonNull() ? o.get("duration").getAsInt() : 0;
            String dl = str(o, "audiodownload");
            t.streamUrl = str(o, "audio");
            if (t.streamUrl == null || t.streamUrl.isEmpty()) t.streamUrl = dl;
            t.coverUrl = str(o, "image");
            if (t.id != null && t.streamUrl != null && !t.streamUrl.isEmpty()) out.add(t);
        }
        return out;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }
}
