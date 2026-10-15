package com.rostroshuecos;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** La voz de San Mudo: texto en el chat mas un susurro. */
public final class SanMudo {
    private SanMudo() {}

    public static void say(ServerPlayerEntity p, String text) {
        p.sendMessage(Text.literal("<San Mudo> ").formatted(Formatting.DARK_RED)
                .append(Text.literal(text).formatted(Formatting.GRAY, Formatting.ITALIC)), false);
        ServerWorld w = p.getServerWorld();
        w.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.AMBIENT[6], SoundCategory.HOSTILE, 1.6F, 1.0F);
    }

    /** Recuerda el siguiente ritual (o el altar final) con la direccion de la estructura que hace falta. */
    public static void hint(ServerWorld w, ServerPlayerEntity p) {
        int idx = Quest.nextRitual();
        if (idx < 6) {
            say(p, Lore.HINTS[idx]);
            say(p, StructureManager.directionHint(w, p, Quest.structureFor(idx)));
        } else {
            int[] c = StructureManager.finalCoords(2);
            say(p, c == null ? "El altar está bajo el pozo, en mi laboratorio. Explora la ceniza."
                    : "El altar está bajo el pozo: x=" + c[0] + " z=" + c[1] + ". Tráeme los fragmentos.");
        }
    }
}
