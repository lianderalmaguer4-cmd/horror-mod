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

    private record Img(Identifier normal, Identifier glitch, int w, int h) {}

    // 0 Pollita, 1 Sombra, 2 Niño, 3 Muñeco (910x590) y, al final, la imagen original que subiste (455x295)
    private static final Img[] IMGS = {
        new Img(RostrosHuecos.id("textures/gui/jumpscare_0.png"), RostrosHuecos.id("textures/gui/jumpscare_0_glitch.png"), 910, 590),
        new Img(RostrosHuecos.id("textures/gui/jumpscare_1.png"), RostrosHuecos.id("textures/gui/jumpscare_1_glitch.png"), 910, 590),
        new Img(RostrosHuecos.id("textures/gui/jumpscare_2.png"), RostrosHuecos.id("textures/gui/jumpscare_2_glitch.png"), 910, 590),
        new Img(RostrosHuecos.id("textures/gui/jumpscare_3.png"), RostrosHuecos.id("textures/gui/jumpscare_3_glitch.png"), 910, 590),
        new Img(RostrosHuecos.id("textures/gui/jumpscare.png"), RostrosHuecos.id("textures/gui/jumpscare_glitch.png"), 455, 295)
    };

    private static final Random RNG = Random.create();
    private static int ticks = 0;
    private static int total = 1;
    private static int type = 0;
    private static Img current = IMGS[0];

    /** type: 0 = fuerte, 1 = glitch. mob: 0-3 = cara de ese monstruo, otro = al azar. */
    public static void trigger(int t, int mob) {
        MinecraftClient mc = MinecraftClient.getInstance();
        type = t;
        total = ticks = (t == 0) ? 45 : 28;
        current = (mob >= 0 && mob < 4) ? IMGS[mob] : IMGS[RNG.nextInt(IMGS.length)];
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
        float scale = Math.max((float) w / current.w(), (float) h / current.h()) * 1.08F;
        int dw = (int) (current.w() * scale);
        int dh = (int) (current.h() * scale);
        int shake = (type == 0) ? 10 : 5;
        int x = (w - dw) / 2 + RNG.nextBetween(-shake, shake);
        int y = (h - dh) / 2 + RNG.nextBetween(-shake, shake);

        boolean glitch = (type == 1) || (elapsed % 8 > 5);
        ctx.drawTexture(RenderLayer::getGuiTextured, glitch ? current.glitch() : current.normal(),
                x, y, 0.0F, 0.0F, dw, dh, current.w(), current.h(), current.w(), current.h());

        // tinte rojo que crece al final
        int alpha = Math.min(150, elapsed * 4);
        ctx.fill(0, 0, w, h, (alpha << 24) | 0x880000);
    }
}
