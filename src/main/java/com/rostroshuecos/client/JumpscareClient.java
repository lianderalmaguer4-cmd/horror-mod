package com.rostroshuecos.client;

import com.rostroshuecos.ModSounds;
import com.rostroshuecos.RostrosHuecos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * Susto en pantalla. type 0 = fuerte (zoom brusco), 1 = glitch (rapido, mas sucio), 2 = muerte (lento y largo).
 * Efectos: destello blanco, zoom, temblor, cinta VHS con bandas desplazadas, lineas de barrido, estatica y vineta.
 */
public final class JumpscareClient {
    private JumpscareClient() {}

    private record Img(Identifier normal, Identifier glitch, int w, int h) {}

    // 0 Sombra, 1 Raiz, 2 Sudario, 3 Hueco, 4 Mandibula, 5 Doble Boca (todas 910x590)
    private static final Img[] IMGS = new Img[6];
    static {
        for (int i = 0; i < IMGS.length; i++) {
            IMGS[i] = new Img(RostrosHuecos.id("textures/gui/jumpscare_" + i + ".png"),
                              RostrosHuecos.id("textures/gui/jumpscare_" + i + "_glitch.png"), 910, 590);
        }
    }

    private static final Random RNG = Random.create();
    private static int ticks = 0;
    private static int total = 1;
    private static int type = 0;
    private static Img current = IMGS[0];

    /** mob: 0-5 = cara de ese monstruo, otro = al azar. */
    public static void trigger(int t, int mob) {
        MinecraftClient mc = MinecraftClient.getInstance();
        type = t;
        total = ticks = (t == 0) ? 45 : (t == 1 ? 28 : 80);
        int idx = (mob >= 0 && mob < IMGS.length) ? mob : RNG.nextInt(IMGS.length);
        current = IMGS[idx];
        // cada monstruo tiene su propio sonido de susto
        if (t == 0) {
            mc.getSoundManager().play(PositionedSoundInstance.master(ModSounds.SCARE[idx], 1.0F, 1.0F));
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.7F, 0.5F));
        } else if (t == 1) {
            mc.getSoundManager().play(PositionedSoundInstance.master(ModSounds.SCARE[idx], 0.85F, 0.8F));
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.7F, 0.8F));
        } else {
            mc.getSoundManager().play(PositionedSoundInstance.master(ModSounds.SCARE[idx], 0.75F, 1.0F));
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.6F, 0.7F));
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.8F, 0.9F));
        }
    }

    public static void tick() {
        if (ticks > 0) ticks--;
    }

    /** Dibuja la imagen en franjas horizontales, algunas desplazadas (efecto de cinta VHS mal rebobinada). */
    private static void drawBands(DrawContext ctx, Identifier tex, int x, int y, int dw, int dh, int cw, int ch) {
        int bands = 12;
        int bh = Math.max(1, dh / bands);
        int rh = Math.max(1, ch / bands);
        for (int i = 0; i < bands; i++) {
            int off = RNG.nextInt(4) == 0 ? RNG.nextBetween(-70, 70) : 0;
            ctx.drawTexture(RenderLayer::getGuiTextured, tex, x + off, y + i * bh, 0.0F, (float) (i * rh),
                    dw, bh, cw, rh, cw, ch);
        }
    }

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        if (ticks <= 0) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int elapsed = total - ticks;
        float prog = elapsed / (float) total;

        // destello blanco inicial
        if (elapsed < 2) {
            ctx.fill(0, 0, w, h, 0xFFFFFFFF);
            return;
        }
        ctx.fill(0, 0, w, h, 0xFF000000);

        // parpadeo oscuro (luz que falla); en el susto de muerte no hay
        if (type != 2 && elapsed > 6 && elapsed % 6 == 5) return;

        // zoom: brusco al principio en el fuerte, lento y continuo en el de muerte
        float zoom = 1.08F + (type == 2 ? 0.30F * prog : 0.45F * Math.min(1.0F, elapsed / 10.0F));
        float scale = Math.max((float) w / current.w(), (float) h / current.h()) * zoom;
        int dw = (int) (current.w() * scale);
        int dh = (int) (current.h() * scale);
        int shake = (type == 0) ? 14 : (type == 2 ? 3 : 7);
        int x = (w - dw) / 2 + RNG.nextBetween(-shake, shake);
        int y = (h - dh) / 2 + RNG.nextBetween(-shake, shake);

        boolean glitchImg = (type == 1) || (elapsed % 8 > 5);
        Identifier tex = glitchImg ? current.glitch() : current.normal();
        if (glitchImg || elapsed % 5 == 0) {
            drawBands(ctx, tex, x, y, dw, dh, current.w(), current.h());
        } else {
            ctx.drawTexture(RenderLayer::getGuiTextured, tex, x, y, 0.0F, 0.0F, dw, dh,
                    current.w(), current.h(), current.w(), current.h());
        }

        // lineas de barrido de monitor antiguo
        for (int ly = 0; ly < h; ly += 4) {
            ctx.fill(0, ly, w, ly + 1, 0x38000000);
        }
        // estatica
        for (int i = 0; i < 140; i++) {
            int sx = RNG.nextInt(w);
            int sy = RNG.nextInt(h);
            int g = RNG.nextInt(256);
            ctx.fill(sx, sy, sx + 2, sy + 2, (0x70 << 24) | (g << 16) | (g << 8) | g);
        }
        // vineta
        int vb = Math.max(8, Math.min(w, h) / 7);
        ctx.fill(0, 0, w, vb, 0x70000000);
        ctx.fill(0, h - vb, w, h, 0x70000000);
        ctx.fill(0, 0, vb, h, 0x70000000);
        ctx.fill(w - vb, 0, w, h, 0x70000000);

        // tinte rojo que crece
        int alpha = Math.min(type == 2 ? 190 : 150, elapsed * (type == 2 ? 3 : 4));
        ctx.fill(0, 0, w, h, (alpha << 24) | 0x880000);
    }
}
