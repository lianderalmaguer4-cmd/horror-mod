package com.rostroshuecos.client;

import com.rostroshuecos.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;

/** Pantalla "debajo de la cama": todo negro salvo una rendija a la altura del suelo, y respiracion. */
public final class HideClient {
    private HideClient() {}

    private static boolean hidden = false;
    private static int ticks = 0;

    public static void set(boolean h) {
        hidden = h;
        ticks = 0;
    }

    public static void tick() {
        if (!hidden) return;
        if (++ticks % 55 == 1) {
            MinecraftClient.getInstance().getSoundManager()
                    .play(PositionedSoundInstance.master(ModSounds.BREATH, 1.0F, 0.9F));
        }
    }

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        if (!hidden) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int top = (int) (h * 0.46);
        int bottom = (int) (h * 0.58);
        ctx.fill(0, 0, w, top, 0xFF000000);
        ctx.fill(0, bottom, w, h, 0xFF000000);
        // los lados de la rendija tambien se oscurecen
        ctx.fill(0, top, w / 7, bottom, 0xD0000000);
        ctx.fill(w - w / 7, top, w, bottom, 0xD0000000);
        ctx.fill(w / 7, top, w - w / 7, bottom, 0x60000000);
    }
}
