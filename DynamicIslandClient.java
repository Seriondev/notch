package id.dynamicisland;

import id.dynamicisland.config.IslandConfig;
import id.dynamicisland.music.AudioPlayer;
import id.dynamicisland.reminder.ReminderService;
import id.dynamicisland.render.IslandHud;
import id.dynamicisland.screen.MusicScreen;
import id.dynamicisland.screen.PauseMenuButton;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GameMenuScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class DynamicIslandClient implements ClientModInitializer {
    public static final String MOD_ID = "dynamicisland";

    private static KeyBinding openMusic;
    private static KeyBinding playPause;
    private static KeyBinding nextTrack;

    @Override
    public void onInitializeClient() {
        IslandConfig.load();

        // HUD in-game (saat tidak ada screen). Saat ada screen, Island digambar lewat hook di bawah.
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "island"), (ctx, tickCounter) -> {
            if (MinecraftClient.getInstance().currentScreen == null) IslandHud.render(ctx);
        });

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "keys"));
        openMusic = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dynamicisland.music", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
        playPause = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dynamicisland.playpause", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        nextTrack = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dynamicisland.next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ReminderService.tick();
            while (openMusic.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new MusicScreen(null));
            }
            while (playPause.wasPressed()) AudioPlayer.get().togglePause();
            while (nextTrack.wasPressed()) AudioPlayer.get().next();
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Island tampil di atas semua screen (menu utama, pause, inventory, dll)
            ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> IslandHud.render(ctx));
            if (screen instanceof GameMenuScreen) PauseMenuButton.install(screen);
        });
    }
}
