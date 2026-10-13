package com.rostroshuecos;

import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Viaje por la Puerta Corrupta. Al principio lleva a la Pradera; cuando tienes las 10 notas, a la Tierra de Ceniza.
 * La primera vez en cada dimension se construye una casa inicial para el jugador. La puerta trasera de esa casa vuelve al Overworld.
 */
public final class DimensionTravel {
    private DimensionTravel() {}

    private static void tp(MinecraftServer server, ServerPlayerEntity p, String dimension, double x, double y, double z) {
        String cmd = String.format(Locale.ROOT, "execute as %s in %s run tp @s %.2f %.2f %.2f",
                p.getUuidAsString(), dimension, x, y, z);
        server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), cmd);
    }

    public static void enter(ServerPlayerEntity p) {
        enter(p, Diary.collected(p) >= Lore.PAGES.length);
    }

    /** ash = false: la Pradera; true: la Tierra de Ceniza. */
    public static void enter(ServerPlayerEntity p, boolean ash) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        int dim = ash ? 2 : 1;
        String name = ash ? "ceniza" : "pradera";
        ServerWorld dw = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, RostrosHuecos.id(name)));
        if (dw == null) {
            p.sendMessage(Text.literal("La puerta no responde. (La dimensión no está disponible.)"), true);
            return;
        }
        // recordar por donde volver
        for (String t : new ArrayList<>(p.getCommandTags())) {
            if (t.startsWith("rhr_")) p.removeCommandTag(t);
        }
        p.addCommandTag("rhr_" + Math.round(p.getX()) + "_" + Math.round(p.getY()) + "_" + Math.round(p.getZ()));

        int[] house = StructureManager.ensureHouse(dw, p, dim);
        p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1.5F, 0.5F);
        tp(server, p, RostrosHuecos.MOD_ID + ":" + name, house[0] + 0.5, house[1], house[2] + 0.5);

        p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(ash
                ? "Todo lo verde se quemó. La ceniza no deja de caer."
                : "Un llano sin fin. Alguien construyó una casa para ti.").formatted(Formatting.GRAY)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(ash ? "TIERRA DE CENIZA" : "LA PRADERA")
                .formatted(ash ? Formatting.DARK_GRAY : Formatting.DARK_GREEN)));
    }

    public static void leave(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        double x, y, z;
        String found = null;
        for (String t : p.getCommandTags()) {
            if (t.startsWith("rhr_")) found = t;
        }
        if (found != null) {
            String[] a = found.substring(4).split("_");
            x = Double.parseDouble(a[0]) + 0.5;
            y = Double.parseDouble(a[1]);
            z = Double.parseDouble(a[2]) + 0.5;
        } else {
            BlockPos sp = server.getOverworld().getSpawnPos();
            x = sp.getX() + 0.5;
            y = sp.getY();
            z = sp.getZ() + 0.5;
        }
        p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1.5F, 0.5F);
        tp(server, p, World.OVERWORLD.getValue().toString(), x, y, z);
    }
}
