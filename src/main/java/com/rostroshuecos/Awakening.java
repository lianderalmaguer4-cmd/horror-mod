package com.rostroshuecos;

import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * EL DESPERTAR. Al llegar al altar del Pozo (Pradera) con las diez notas, la voz del pozo (San Mudo, haciendose
 * pasar por la victima) cuenta la historia. Despues se lee la ultima pagina y empieza todo: te manda a la Tierra de Ceniza,
 * donde los monstruos ya atacan y San Mudo te encarga los rituales.
 */
public final class Awakening {
    private Awakening() {}

    private static boolean running = false;

    public static ActionResult useAltar(ServerPlayerEntity p, ServerWorld w, BlockPos pos) {
        if (running) return ActionResult.SUCCESS;
        if (Quest.stage(p) >= 2) {
            p.sendMessage(Text.literal("El altar está frío y vacío.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
            return ActionResult.SUCCESS;
        }
        int n = Diary.collected(p);
        if (n < Lore.PAGES.length) {
            p.sendMessage(Text.literal("El altar guarda silencio. Aún te faltan notas (" + n + "/" + Lore.PAGES.length + ").")
                    .formatted(Formatting.GRAY, Formatting.ITALIC), true);
            return ActionResult.SUCCESS;
        }
        begin(w, p, pos);
        return ActionResult.SUCCESS;
    }

    private static void begin(ServerWorld w, ServerPlayerEntity p, BlockPos pos) {
        running = true;
        Vec3d c = Vec3d.ofCenter(pos);
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Una voz sube desde el fondo.").formatted(Formatting.GRAY)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("EL ALTAR").formatted(Formatting.DARK_RED)));

        int t = 80;
        for (int i = 0; i < Lore.ALTAR.length; i++) {
            final String line = Lore.ALTAR[i];
            StalkerDirector.later(w, t, () -> {
                if (!p.isRemoved()) SanMudo.say(p, line);
            });
            t += 110;
        }
        // la ultima pagina del diario
        StalkerDirector.later(w, t, () -> {
            if (p.isRemoved()) return;
            p.sendMessage(Text.literal("[Última página] " + Lore.PAGES[Lore.PAGES.length - 1])
                    .formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
        });
        t += 160;
        final int total = t;
        // efectos: remolino de almas alrededor del altar durante toda la escena
        for (int k = 0; k < total; k += 8) {
            final int kk = k;
            StalkerDirector.later(w, k, () -> {
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI / 5 + kk * 0.05;
                    double r = 3.5 - (kk / (double) total) * 2.5;
                    w.spawnParticles(ParticleTypes.SOUL, c.x + Math.cos(a) * r, c.y + 0.5 + (kk / (double) total) * 3, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
                }
            });
        }
        // el despertar
        StalkerDirector.later(w, total, () -> {
            running = false;
            if (p.isRemoved() || !p.isAlive()) return;
            Quest.setStage(p, 2);
            var server = w.getServer();
            ServerWorld ash = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, RostrosHuecos.id("ceniza")));
            int[] well = StructureManager.finalCoords(1);
            if (ash != null && well != null) StructureManager.placeFinalAt(ash, well[0], well[1]);
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 30));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Ahora, los monstruos ya no solo miran.").formatted(Formatting.GRAY)));
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("TODO COMIENZA").formatted(Formatting.DARK_RED)));
            DimensionTravel.enter(p, true);
            StalkerDirector.later(ash != null ? ash : w, 120, () -> {
                if (!p.isRemoved()) SanMudo.say(p, Lore.ARRIVAL);
            });
            StalkerDirector.later(ash != null ? ash : w, 260, () -> {
                if (!p.isRemoved()) SanMudo.hint(p.getServerWorld(), p);
            });
        });
    }
}
