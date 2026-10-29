package id.dynamicisland.music;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public final class Http {
    private Http() {}

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static HttpRequest.Builder req(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "DynamicIslandMod/1.0 (Minecraft Fabric)");
    }

    /** @return body sebagai String, atau null kalau status bukan 2xx */
    public static String getString(String url) throws Exception {
        HttpResponse<String> r = CLIENT.send(req(url).GET().build(), HttpResponse.BodyHandlers.ofString());
        return r.statusCode() / 100 == 2 ? r.body() : null;
    }

    public static byte[] getBytes(String url) throws Exception {
        HttpResponse<byte[]> r = CLIENT.send(req(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        return r.statusCode() / 100 == 2 ? r.body() : null;
    }

    /** Buka stream (untuk playback langsung tanpa download penuh). */
    public static InputStream openStream(String url) throws Exception {
        HttpResponse<InputStream> r = CLIENT.send(req(url).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() / 100 != 2) throw new java.io.IOException("HTTP " + r.statusCode());
        return r.body();
    }

    public static void download(String url, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = openStream(url)) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
