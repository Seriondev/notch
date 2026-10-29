package id.dynamicisland.screen;

import id.dynamicisland.render.Shapes;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.List;

/** Tombol "Island Settings" (dengan logo notch) di sebelah tombol Options pada pause menu. */
public final class PauseMenuButton {
    private PauseMenuButton() {}

    public static void install(Screen screen) {
        List<ClickableWidget> buttons = Screens.getButtons(screen);
        ClickableWidget options = null;
        for (ClickableWidget w : buttons) {
            if (w.getMessage().getContent() instanceof TranslatableTextContent t && t.getKey().equals("menu.options")) {
                options = w;
                break;
            }
        }
        if (options == null) return;

        int bw = 120;
        int bx = options.getX() - bw - 6; // di kiri tombol Options
        int by = options.getY();
        if (bx < 4) bx = options.getX() + options.getWidth() * 2 + 12; // layar sempit: pindah ke kanan

        ButtonWidget btn = ButtonWidget.builder(Text.literal("      Island Settings"), b ->
                        MinecraftClient.getInstance().setScreen(new IslandSettingsScreen(screen)))
                .dimensions(bx, by, bw, options.getHeight())
                .build();
        buttons.add(btn);

        final int fx = bx, fy = by, fh = options.getHeight();
        ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> {
            // logo notch: persegi dengan tepi atas rata dan sudut bawah membulat
            int ix = fx + 8, iy = fy + (fh - 9) / 2;
            Shapes.roundedRect(ctx, ix, iy, 14, 9, 0, 4, 0xFFFFFFFF);
            Shapes.roundedRect(ctx, ix + 4, iy, 6, 3, 0, 2, 0xFF000000);
        });
    }
}
