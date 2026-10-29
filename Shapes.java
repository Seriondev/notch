package id.dynamicisland.render;

import net.minecraft.client.gui.DrawContext;

/** Menggambar bentuk membulat memakai garis-garis horizontal (DrawContext tidak punya rounded rect). */
public final class Shapes {
    private Shapes() {}

    /**
     * Persegi dengan radius sudut atas dan bawah terpisah.
     * NOTCH: topR = 0 (menempel di atas layar), botR besar.
     * PILL : topR = botR = h/2.
     */
    public static void roundedRect(DrawContext ctx, int x, int y, int w, int h, int topR, int botR, int argb) {
        if (w <= 0 || h <= 0) return;
        topR = Math.min(topR, Math.min(w, h) / 2);
        botR = Math.min(botR, Math.min(w, h) / 2);
        for (int row = 0; row < h; row++) {
            int inset = 0;
            if (row < topR) inset = Math.max(inset, cornerInset(topR, topR - row));
            int fromBottom = h - 1 - row;
            if (fromBottom < botR) inset = Math.max(inset, cornerInset(botR, botR - fromBottom));
            ctx.fill(x + inset, y + row, x + w - inset, y + row + 1, argb);
        }
    }

    /** Jarak horizontal yang "terpotong" pada baris ke-dy (dihitung dari pusat lingkaran sudut). */
    private static int cornerInset(int r, int dyFromCenterLine) {
        double dy = dyFromCenterLine - 0.5;
        double dx = Math.sqrt(Math.max(0, (double) r * r - dy * dy));
        return (int) Math.round(r - dx);
    }

    /** Warna ARGB dengan alpha dikalikan faktor 0..1. */
    public static int withAlpha(int argb, float factor) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
