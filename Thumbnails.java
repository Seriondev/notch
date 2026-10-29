package id.dynamicisland.render;

import id.dynamicisland.music.Http;
import id.dynamicisland.music.Library;
import id.dynamicisland.music.Track;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Cover lagu -> tekstur Minecraft (64x64, sudut membulat). Dimuat async, cached. */
public final class Thumbnails {
    private Thumbnails() {}

    public static final int SIZE = 64;
    private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

    /** @return Identifier tekstur, atau null kalau belum siap / tidak ada cover. */
    public static Identifier get(Track t) {
        if (t == null || t.id == null) return null;
        Identifier id = READY.get(t.id);
        if (id != null) return id;
        if (LOADING.add(t.id)) {
            Thread th = new Thread(() -> load(t), "DynamicIsland-Cover");
            th.setDaemon(true);
            th.start();
        }
        return null;
    }

    private static void load(Track t) {
        try {
            byte[] data = null;
            Path local = Library.coverPath(t);
            if (local != null) data = Files.readAllBytes(local);
            else if (t.coverUrl != null) data = Http.getBytes(t.coverUrl);
            if (data == null) return;

            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            if (src == null) return;
            BufferedImage scaled = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, SIZE, SIZE, null);
            g.dispose();

            NativeImage img = new NativeImage(SIZE, SIZE, false);
            int r = 12; // radius sudut
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int argb = scaled.getRGB(x, y) | 0xFF000000;
                    if (!insideRounded(x, y, r)) argb = 0;
                    img.setColorArgb(x, y, argb);
                }
            }
            final String key = t.id;
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                Identifier id = Identifier.of("dynamicisland", "cover/" + Library.safeId(t).toLowerCase());
                mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture("dynamicisland_cover", img));
                READY.put(key, id);
            });
        } catch (Exception ignored) {
        } finally {
            // biarkan LOADING berisi id supaya tidak dicoba terus-menerus kalau gagal
        }
    }

    private static boolean insideRounded(int x, int y, int r) {
        int cx = x < r ? r : (x >= SIZE - r ? SIZE - r - 1 : x);
        int cy = y < r ? r : (y >= SIZE - r ? SIZE - r - 1 : y);
        int dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }
}
