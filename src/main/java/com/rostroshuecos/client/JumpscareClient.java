package com.rostroshuecos.client;

import com.rostroshuecos.RostrosHuecos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

public final class JumpscareClient {
    private JumpscareClient() {}

    // Tamano de la imagen jumpscare.png (la que subiste): 455 x 295
    private static final int IMG_W = 455;
    private static final int IMG_H = 295;
    private static final Identifier IMG = RostrosHuecos.id("textures/gui/jumpscare.png");
    private static final Identifier IMG_GLITCH = RostrosHuecos.id("textures/gui/jumpscare_glitch.png");

    private static final Random RNG = Random.create();
    private static int ticks = 0;
    private static int total = 1;
    private static int type = 0;

    public static void trigger(int t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        type = t;
        total = ticks = (t == 0) ? 45 : 28;
        if (t == 0) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_GHAST_SCREAM, 0.5F, 1.0F));
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.6F, 1.0F));
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.5F, 1.0F));
        } else {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_ENDERMAN_SCREAM, 0.5F, 1.0F));
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.7F, 1.0F));
        }
    }

    public static void tick() {
        if (ticks > 0) ticks--;
    }

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        if (ticks <= 0) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int elapsed = total - ticks;

        ctx.fill(0, 0, w, h, 0xFF000000);

        // parpadeo oscuro (como luz que falla)
        if (elapsed > 6 && elapsed % 6 == 5) return;

        // imagen "cover": llena la pantalla sin deformarla
        float scale = Math.max((float) w / IMG_W, (float) h / IMG_H) * 1.08F;
        int dw = (int) (IMG_W * scale);
        int dh = (int) (IMG_H * scale);
        int shake = (type == 0) ? 10 : 5;
        int x = (w - dw) / 2 + RNG.nextBetween(-shake, shake);
        int y = (h - dh) / 2 + RNG.nextBetween(-shake, shake);

        boolean glitch = (type == 1) || (elapsed % 8 > 5);
        ctx.drawTexture(RenderLayer::getGuiTextured, glitch ? IMG_GLITCH : IMG,
                x, y, 0.0F, 0.0F, dw, dh, IMG_W, IMG_H, IMG_W, IMG_H);

        // tinte rojo que crece al final
        int alpha = Math.min(150, elapsed * 4);
        ctx.fill(0, 0, w, h, (alpha << 24) | 0x880000);
    }
}
