package com.rostroshuecos;

import com.rostroshuecos.entity.AcosadorEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

/**
 * EL FINAL (Tierra de Ceniza). Con los 6 fragmentos en el altar del laboratorio, San Mudo revela quien es.
 * Fases: 1) revelacion (dialogo), 2) 20 s de ventaja para huir, 3) San Mudo te caza durante 120 s mientras suplica.
 * Si sobrevives, las almas lo reclaman: FIN, Sello de San Mudo y vuelves al Overworld.
 */
public final class FinalEvent {
    private FinalEvent() {}

    public static boolean active = false;

    private static final int REVEAL = 540;      // 6 lineas x 90 ticks
    private static final int GRACE = 400;       // 20 s para escapar
    private static final int CHASE = 2400;      // 120 s

    private static UUID playerId;
    private static UUID bossId;
    private static ServerWorld world;
    private static BlockPos altar;
    private static int startTick;
    private static int phase;

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
        return Quest.stage(p) >= 5;
    }

    private static void bar(ServerPlayerEntity p, String msg) {
        p.sendMessage(Text.literal(msg).formatted(Formatting.GRAY, Formatting.ITALIC), true);
    }

    /** Clic derecho en la obsidiana llorosa del Pozo de San Mudo. */
    public static ActionResult useAltar(ServerPlayerEntity p, ServerWorld w, BlockPos pos) {
        if (HorrorUtil.dimIndex(w) == 1) return Awakening.useAltar(p, w, pos);   // el altar de la Pradera es el del despertar
        if (active) return ActionResult.SUCCESS;
        if (finished(p)) {
            bar(p, "El pozo está sellado y en silencio.");
            return ActionResult.SUCCESS;
        }
        if (Quest.stage(p) < 2) {
            bar(p, "El altar no responde todavía.");
            return ActionResult.SUCCESS;
        }
        if (w.getDifficulty() == Difficulty.PEACEFUL) {
            bar(p, "Los espíritus no despiertan en dificultad pacífica. Sube la dificultad.");
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
            start(w, p, pos);   // reintento tras morir
        } else {
            bar(p, "El altar espera los fragmentos (" + n + "/6).");
        }
        return ActionResult.SUCCESS;
    }

    private static void start(ServerWorld w, ServerPlayerEntity p, BlockPos altarPos) {
        if (active) return;
        active = true;
        phase = 1;
        playerId = p.getUuid();
        world = w;
        altar = altarPos.toImmutable();
        bossId = null;
        startTick = w.getServer().getTicks();

        p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("La voz ya no suena amable.").formatted(Formatting.GRAY)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("LOS FRAGMENTOS").formatted(Formatting.DARK_RED)));
        w.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.FINAL_START, SoundCategory.HOSTILE, 5.0F, 1.0F);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 600, 0, false, false));
    }

    private static void tick(MinecraftServer server) {
        if (!active || world == null) return;
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
        int el = server.getTicks() - startTick;
        if (p == null || !p.isAlive() || p.getServerWorld() != world) {
            end(server, false, p);
            return;
        }
        Vec3d c = Vec3d.ofCenter(altar);

        if (phase == 1) {
            // revelacion: una linea cada 90 ticks, con almas subiendo del altar
            if (el % 90 == 0 && el / 90 < Lore.REVEAL.length) SanMudo.say(p, Lore.REVEAL[el / 90]);
            if (el % 5 == 0) {
                world.spawnParticles(ParticleTypes.SOUL, c.x, c.y + 0.5, c.z, 6, 0.6, 0.2, 0.6, 0.06);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 1.0, c.z, 3, 0.4, 0.4, 0.4, 0.02);
            }
            if (el >= REVEAL) {
                phase = 2;
                p.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 10));
                p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Tienes 20 segundos.").formatted(Formatting.GRAY)));
                p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("CORRE").formatted(Formatting.DARK_RED)));
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 3.0F, 0.7F);
            }
        } else if (phase == 2) {
            int g = el - REVEAL;
            if (g % 40 == 0) {
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2.0F, 0.8F);
            }
            if (g % 100 == 0 && g < GRACE) bar(p, "Quedan " + ((GRACE - g) / 20) + " segundos...");
            if (g >= GRACE) {
                phase = 3;
                spawnSanMudo(p);
            }
        } else {
            int ch = el - REVEAL - GRACE;
            Entity be = bossId == null ? null : world.getEntity(bossId);
            if (be instanceof MobEntity boss) {
                if (ch % 40 == 0) boss.setTarget(p);
            } else if (ch > 20) {
                spawnSanMudo(p);   // por si desaparece
            }
            if (ch > 0 && ch % 400 == 0 && ch < CHASE) bar(p, "Aguanta... los niños lo están reteniendo (" + ((CHASE - ch) / 20) + " s)");
            if (ch >= CHASE) end(server, true, p);
        }
    }

    private static void spawnSanMudo(ServerPlayerEntity p) {
        BlockPos spot = StalkerDirector.groundNear(world, altar.add(2, 0, 2), 3);
        if (spot == null) spot = altar.up();
        MobEntity m = ModEntities.SAN_MUDO.spawn(world, spot, net.minecraft.entity.SpawnReason.EVENT);
        if (m == null) return;
        m.setPersistent();
        m.setTarget(p);
        bossId = m.getUuid();
        world.playSound(null, spot.getX(), spot.getY(), spot.getZ(), ModSounds.SCARE[6], SoundCategory.HOSTILE, 3.0F, 1.0F);
        world.spawnParticles(ParticleTypes.SOUL, spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5, 60, 0.5, 1.2, 0.5, 0.05);
        Jumpscare.send(p, 1, 6);
    }

    private static void end(MinecraftServer server, boolean success, ServerPlayerEntity p) {
        active = false;
        if (world != null && altar != null) {
            Box box = new Box(altar).expand(200.0);
            for (MobEntity m : world.getEntitiesByClass(MobEntity.class, box, e -> e instanceof AcosadorEntity)) {
                if (success && m.getType() == ModEntities.SAN_MUDO) {
                    world.spawnParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 1.5, m.getZ(), 120, 0.7, 1.4, 0.7, 0.12);
                    world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, m.getX(), m.getY() + 1.5, m.getZ(), 80, 0.6, 1.2, 0.6, 0.3);
                }
                HorrorUtil.poof(m);
            }
        }
        if (p == null) return;
        if (success) {
            for (int i = 0; i < Lore.END.length; i++) {
                final String line = Lore.END[i];
                StalkerDirector.later(world, i * 70, () -> {
                    if (!p.isRemoved()) SanMudo.say(p, line);
                });
            }
            Quest.setStage(p, 5);
            p.addCommandTag("rh_final");
            p.giveItemStack(new ItemStack(ModItems.SELLO));
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(20, 120, 40));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("El pozo vuelve a estar sellado.").formatted(Formatting.GRAY)));
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("FIN").formatted(Formatting.WHITE)));
            world.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.FINAL_END, SoundCategory.MASTER, 4.0F, 1.0F);
            p.sendMessage(Text.literal("San Mudo descansa. Guardas el Sello: mientras lo sostengas, nada te verá.")
                    .formatted(Formatting.GOLD, Formatting.ITALIC), false);
            StalkerDirector.later(world, 260, () -> {
                if (!p.isRemoved()) DimensionTravel.leaveForce(p);   // de vuelta al Overworld
            });
        } else {
            p.sendMessage(Text.literal("El pozo guarda silencio... por ahora. Vuelve al altar cuando estés listo.")
                    .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
        }
    }
}
