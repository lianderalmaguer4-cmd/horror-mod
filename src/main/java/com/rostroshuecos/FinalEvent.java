package com.rostroshuecos;

import com.rostroshuecos.entity.AcosadorEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;

/**
 * EL FINAL. Hay que reunir los 6 fragmentos (uno por monstruo), ponerlos en el altar de obsidiana llorosa del
 * Pozo de San Mudo (aparece al completar las 10 notas) y resistir 90 segundos mientras los seis te cazan.
 * Si sobrevives: el pozo se sella, recibes el Sello de San Mudo y los eventos se calman.
 */
public final class FinalEvent {
    private FinalEvent() {}

    public static boolean active = false;
    public static final int DURATION = 1800;   // 90 s

    private static UUID playerId;
    private static ServerWorld world;
    private static BlockPos altar;
    private static int startTick;
    private static boolean spawned;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FinalEvent::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            active = false;
            world = null;
        });
    }

    public static int placedCount(ServerPlayerEntity p) {
        int n = 0;
        for (int i = 0; i < ModItems.FRAGMENTOS.length; i++) {
            if (p.getCommandTags().contains("rh_frag_" + i)) n++;
        }
        return n;
    }

    public static boolean finished(ServerPlayerEntity p) {
        return p.getCommandTags().contains("rh_final");
    }

    private static void bar(ServerPlayerEntity p, String msg) {
        p.sendMessage(Text.literal(msg).formatted(Formatting.GRAY, Formatting.ITALIC), true);
    }

    /** Clic derecho en el altar (obsidiana llorosa del Pozo de San Mudo). */
    public static ActionResult useAltar(ServerPlayerEntity p, ServerWorld w, BlockPos pos) {
        if (active) return ActionResult.SUCCESS;
        if (finished(p)) {
            bar(p, "El pozo está sellado y en silencio.");
            return ActionResult.SUCCESS;
        }
        if (w.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            bar(p, "Los monstruos no despiertan en dificultad pacífica. Sube la dificultad.");
            return ActionResult.SUCCESS;
        }
        ItemStack held = p.getMainHandStack();
        int idx = HorrorUtil.fragmentIndex(held);
        if (idx >= 0) {
            String tag = "rh_frag_" + idx;
            if (p.getCommandTags().contains(tag)) {
                bar(p, "Ya colocaste ese fragmento.");
                return ActionResult.SUCCESS;
            }
            held.decrement(1);
            p.addCommandTag(tag);
            w.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 2.0F, 0.5F);
            int n = placedCount(p);
            bar(p, "Fragmento colocado en el altar (" + n + "/6).");
            if (n >= 6) start(w, p, pos);
            return ActionResult.SUCCESS;
        }
        int n = placedCount(p);
        if (n >= 6) {
            start(w, p, pos);   // por si moriste durante el final: se reinicia
        } else {
            bar(p, "El altar espera fragmentos (" + n + "/6).");
        }
        return ActionResult.SUCCESS;
    }

    public static void start(ServerWorld w, ServerPlayerEntity p, BlockPos altarPos) {
        if (active) return;
        active = true;
        playerId = p.getUuid();
        world = w;
        altar = altarPos.toImmutable();
        startTick = w.getServer().getTicks();
        spawned = false;

        p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 80, 30));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(
                Text.literal("Resiste hasta que el pozo se calle.").formatted(Formatting.GRAY)));
        p.networkHandler.sendPacket(new TitleS2CPacket(
                Text.literal("EL POZO DESPIERTA").formatted(Formatting.DARK_RED)));
        w.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.FINAL_START, SoundCategory.HOSTILE, 5.0F, 1.0F);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 600, 0, false, false));
        p.sendMessage(Text.literal("Los fragmentos se funden con el altar. Las piedras del pozo empiezan a moverse...")
                .formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
    }

    private static void tick(MinecraftServer server) {
        if (!active || world == null) return;
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
        int el = server.getTicks() - startTick;
        if (p == null || !p.isAlive() || p.getServerWorld() != world) {
            end(server, false, p);
            return;
        }
        if (el == 60 && !spawned) {
            spawned = true;
            StalkerDirector.finalWave(world, p);
        }
        if (el > 60 && el % 40 == 0) {
            StalkerDirector.finalWave(world, p);   // los reencuentra a todos
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.HOSTILE, 2.0F, 0.8F);
        }
        if (el > 0 && el % 300 == 0 && el < DURATION) {
            int left = (DURATION - el) / 20;
            bar(p, "Faltan " + left + " segundos...");
        }
        if (el >= DURATION) end(server, true, p);
    }

    private static void end(MinecraftServer server, boolean success, ServerPlayerEntity p) {
        active = false;
        if (world != null && altar != null) {
            Box box = new Box(altar).expand(200.0);
            for (MobEntity m : world.getEntitiesByClass(MobEntity.class, box, e -> e instanceof AcosadorEntity)) {
                HorrorUtil.poof(m);
            }
        }
        if (p == null) return;
        if (success) {
            p.addCommandTag("rh_final");
            p.giveItemStack(new ItemStack(ModItems.SELLO));
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(20, 120, 40));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(
                    Text.literal("El pozo vuelve a estar sellado.").formatted(Formatting.GRAY)));
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("FIN").formatted(Formatting.WHITE)));
            if (world != null) {
                world.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.FINAL_END, SoundCategory.MASTER, 4.0F, 1.0F);
            }
            p.sendMessage(Text.literal("San Mudo descansa. Guardas el Sello: mientras lo sostengas, nada te verá.")
                    .formatted(Formatting.GOLD, Formatting.ITALIC), false);
        } else {
            p.sendMessage(Text.literal("El pozo guarda silencio... por ahora. Vuelve al altar cuando estés listo.")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
        }
    }
}
