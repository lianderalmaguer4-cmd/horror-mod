package com.rostroshuecos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;

/**
 * RITUALES. Cada monstruo se vence con un ritual propio en el altar (magnetita) de su estructura. El ritual
 * invoca al monstruo ENCADENADO: no puede alejarse del altar y ataca por rachas mientras cumples su condicion.
 * Al terminar suelta su fragmento (su alma) y no vuelve a aparecer jamas.
 *   0 Sombra (Cementerio): no la mires.        3 Hueco (Pozo Sellado): no le quites la mirada.
 *   1 Raiz (Arbol): rompe 5 raices.             4 Mandibula (Capilla): toca la campana 7 veces.
 *   2 Sudario (Casa): manten 3 velas de 4.      5 Doble Boca (Escuela): dile 3 veces "ya no hay niños".
 */
public final class Rituals {
    private Rituals() {}

    public static boolean active = false;
    private static int idx;
    private static ServerWorld world;
    private static BlockPos altar;
    private static UUID playerId;
    private static UUID bossId;
    private static int startTick;
    private static int progress;
    private static int goal;
    private static int lastRing;
    private static BlockPos bellPos;
    private static final List<BlockPos> nodes = new ArrayList<>();
    private static final int[] GOALS = {600, 5, 800, 600, 7, 3};

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Rituals::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            active = false;
            nodes.clear();
            world = null;
        });
    }

    private static void bar(ServerPlayerEntity p, String msg) {
        p.sendMessage(Text.literal(msg).formatted(Formatting.GRAY, Formatting.ITALIC), true);
    }

    /** Clic derecho en una magnetita. PASS si no es un altar de ritual (asi la magnetita normal sigue funcionando). */
    public static ActionResult useAltar(ServerPlayerEntity p, ServerWorld w, BlockPos pos) {
        int type = StructureManager.ritualAltarType(w, pos);
        if (type < 0) return ActionResult.PASS;
        int i = Quest.ritualFor(type);
        if (i < 0) return ActionResult.PASS;
        if (Quest.stage(p) < 2) {
            bar(p, "El altar está dormido. Todavía no es el momento.");
            return ActionResult.SUCCESS;
        }
        if (active) {
            bar(p, "Ya hay un ritual en curso.");
            return ActionResult.SUCCESS;
        }
        if (StructureManager.isDefeated(i)) {
            bar(p, "Este espíritu ya descansa.");
            return ActionResult.SUCCESS;
        }
        if (i != Quest.nextRitual()) {
            bar(p, "San Mudo aún no te ha pedido este ritual.");
            return ActionResult.SUCCESS;
        }
        if (w.getDifficulty() == Difficulty.PEACEFUL) {
            bar(p, "Los espíritus no se manifiestan en dificultad pacífica.");
            return ActionResult.SUCCESS;
        }
        if (!Quest.take(p, ModItems.SAL, 1)) {
            bar(p, "Necesitas 1 Sal de San Mudo (búscala en los cofres de las ruinas).");
            return ActionResult.SUCCESS;
        }
        start(w, p, pos, i);
        return ActionResult.SUCCESS;
    }

    private static void start(ServerWorld w, ServerPlayerEntity p, BlockPos pos, int i) {
        Random r = w.getRandom();
        active = true;
        idx = i;
        world = w;
        altar = pos.toImmutable();
        playerId = p.getUuid();
        startTick = w.getServer().getTicks();
        progress = 0;
        goal = GOALS[i];
        lastRing = startTick;
        nodes.clear();
        bellPos = null;

        EntityType<? extends MobEntity> type = ModEntities.byIndex(i);
        for (var e : w.getEntitiesByType(type, x -> true)) e.discard();   // solo uno de cada tipo

        BlockPos spot = null;
        for (int k = 0; k < 60 && spot == null; k++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double d = 5 + r.nextInt(3);
            spot = StalkerDirector.groundNear(w, BlockPos.ofFloored(pos.getX() + 0.5 + Math.cos(ang) * d, pos.getY(), pos.getZ() + 0.5 + Math.sin(ang) * d), 4);
        }
        if (spot == null) spot = pos.north(4);
        MobEntity boss = type.spawn(w, spot, SpawnReason.EVENT);
        if (boss == null) {
            active = false;
            p.giveItemStack(new ItemStack(ModItems.SAL));
            bar(p, "El ritual no responde. Te devuelven la Sal.");
            return;
        }
        boss.addCommandTag(HorrorUtil.TAG_ENCADENADO);
        boss.setPersistent();
        boss.setTarget(p);
        bossId = boss.getUuid();

        // preparacion propia de cada ritual
        switch (i) {
            case 1 -> {
                for (int k = 0; k < 5; k++) {
                    double ang = k * Math.PI * 2 / 5 + r.nextDouble() * 0.5;
                    double d = 4 + r.nextInt(3);
                    BlockPos g = StalkerDirector.groundNear(w, BlockPos.ofFloored(pos.getX() + 0.5 + Math.cos(ang) * d, pos.getY(), pos.getZ() + 0.5 + Math.sin(ang) * d), 4);
                    if (g != null) {
                        w.setBlockState(g, Blocks.MANGROVE_ROOTS.getDefaultState());
                        nodes.add(g);
                    }
                }
                goal = Math.max(1, nodes.size());
            }
            case 2 -> {
                int[][] off = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}};
                for (int[] o : off) {
                    BlockPos g = StalkerDirector.groundNear(w, pos.add(o[0], 0, o[1]), 4);
                    if (g != null) {
                        w.setBlockState(g, Blocks.CANDLE.getDefaultState().with(CandleBlock.LIT, true));
                        nodes.add(g);
                    }
                }
                p.giveItemStack(new ItemStack(Items.FLINT_AND_STEEL));
            }
            case 4 -> {
                BlockPos g = StalkerDirector.groundNear(w, pos.add(3, 0, 0), 4);
                if (g != null) {
                    w.setBlockState(g, Blocks.BELL.getDefaultState());
                    bellPos = g;
                }
            }
            default -> { }
        }

        // efectos de inicio
        Vec3d c = Vec3d.ofCenter(pos);
        w.spawnParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0.0);
        w.spawnParticles(ParticleTypes.SOUL, c.x, c.y + 1, c.z, 60, 2.5, 0.5, 2.5, 0.03);
        w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 3.0F, 0.6F);
        w.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.8F, 0.7F);
        SanMudo.say(p, Lore.HINTS[i]);
        if (i == 5) SanMudo.say(p, "Escríbelo en el chat, tal cual: «" + Lore.PHRASE + "».");
    }

    private static void harm(ServerPlayerEntity p, String msg) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.INSTANT_DAMAGE, 1, 0));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0, false, false));
        bar(p, msg);
    }

    private static void tick(MinecraftServer server) {
        if (!active || world == null) return;
        int now = server.getTicks();
        int el = now - startTick;
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(playerId);
        Entity be = world.getEntity(bossId);
        MobEntity boss = be instanceof MobEntity m ? m : null;
        if (p == null || !p.isAlive() || p.getServerWorld() != world || p.squaredDistanceTo(Vec3d.ofCenter(altar)) > 900.0) {
            fail(server, p, "El ritual se rompe.");
            return;
        }
        if (el > 4800 || boss == null || boss.isRemoved()) {
            fail(server, p, "El ritual se apaga.");
            return;
        }

        // la cadena: no se aleja del altar
        Vec3d c = Vec3d.ofCenter(altar);
        if (boss.squaredDistanceTo(c) > 100.0) {
            BlockPos g = StalkerDirector.groundNear(world, BlockPos.ofFloored(c.x + (boss.getX() - c.x) * 0.4, altar.getY(), c.z + (boss.getZ() - c.z) * 0.4), 4);
            if (g != null) boss.setPosition(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 2.0F, 0.6F);
        }
        // ataca por rachas: 2 s de cada 7 s
        boss.setAiDisabled(el % 140 > 40);
        boss.setTarget(p);

        if (el % 4 == 0) vfx(boss, c, el);

        switch (idx) {
            case 0 -> {
                boolean near = boss.squaredDistanceTo(p) < 196.0;
                boolean looking = HorrorUtil.isLookingAt(p, boss);
                if (near && !looking) progress++;
                else if (looking && el % 40 == 0) harm(p, "¡No la mires!");
            }
            case 1 -> {
                int broken = 0;
                for (BlockPos n : nodes) if (!world.getBlockState(n).isOf(Blocks.MANGROVE_ROOTS)) broken++;
                progress = broken;
            }
            case 2 -> {
                List<BlockPos> lit = new ArrayList<>();
                for (BlockPos n : nodes) {
                    BlockState st = world.getBlockState(n);
                    if (st.isOf(Blocks.CANDLE) && st.get(CandleBlock.LIT)) lit.add(n);
                }
                if (lit.size() >= 3) progress++;
                if (el % 160 == 80 && !lit.isEmpty()) {
                    BlockPos out = lit.get(world.getRandom().nextInt(lit.size()));
                    world.setBlockState(out, world.getBlockState(out).with(CandleBlock.LIT, false));
                    world.playSound(null, out, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.5F, 0.7F);
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, out.getX() + 0.5, out.getY() + 0.5, out.getZ() + 0.5, 12, 0.1, 0.2, 0.1, 0.02);
                    bar(p, "¡Una vela se apagó! Enciéndela de nuevo.");
                }
            }
            case 3 -> {
                boolean looking = boss.squaredDistanceTo(p) < 256.0 && HorrorUtil.isLookingAt(p, boss);
                if (looking) progress++;
                else if (el % 40 == 0) harm(p, "¡Mírala! No apartes la vista.");
            }
            case 4 -> {
                if (progress > 0 && el % 300 == 0) progress--;
            }
            default -> { }
        }

        if (el > 0 && el % 100 == 0 && progress < goal) {
            bar(p, "Ritual: " + (progress * 100 / goal) + "%");
        }
        if (progress >= goal) succeed(server, p, boss);
    }

    private static void vfx(MobEntity boss, Vec3d c, int el) {
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12 + el * 0.02;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x + Math.cos(a) * 5, altar.getY() + 0.2, c.z + Math.sin(a) * 5, 1, 0, 0.05, 0, 0.0);
        }
        for (int y = 0; y < 8; y++) {
            world.spawnParticles(ParticleTypes.END_ROD, c.x, altar.getY() + 1 + y * 0.6, c.z, 1, 0.05, 0, 0.05, 0.0);
        }
        Vec3d b = boss.getPos().add(0, boss.getHeight() / 2, 0);
        for (int i = 1; i < 10; i++) {
            Vec3d q = c.add(0, 1, 0).lerp(b, i / 10.0);
            world.spawnParticles(ParticleTypes.ENCHANTED_HIT, q.x, q.y, q.z, 1, 0, 0, 0, 0.0);
        }
    }

    // ------------------------------------------------------------------ eventos de los rituales
    public static boolean onBell(ServerPlayerEntity p, BlockPos pos) {
        if (!active || idx != 4 || bellPos == null || !pos.equals(bellPos) || !p.getUuid().equals(playerId)) return false;
        int now = world.getServer().getTicks();
        if (now - lastRing < 15) return false;
        lastRing = now;
        progress++;
        world.spawnParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.5);
        bar(p, "Campana " + Math.min(progress, goal) + "/" + goal);
        return true;
    }

    public static void onChat(ServerPlayerEntity p, String text) {
        if (!active || idx != 5 || !p.getUuid().equals(playerId)) return;
        String t = text.toLowerCase().replaceAll("[^\\p{L} ]", "").trim();
        if (!t.equals(Lore.PHRASE)) return;
        progress++;
        Vec3d c = Vec3d.ofCenter(altar);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1.5, c.z, 40, 0.5, 0.8, 0.5, 0.2);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 2.0F, 0.6F);
        bar(p, "Lo escuchan... (" + Math.min(progress, goal) + "/" + goal + ")");
    }

    // ------------------------------------------------------------------ fin
    private static void cleanup() {
        if (world == null) return;
        for (BlockPos n : nodes) {
            BlockState st = world.getBlockState(n);
            if (st.isOf(Blocks.MANGROVE_ROOTS) || st.isOf(Blocks.CANDLE)) world.setBlockState(n, Blocks.AIR.getDefaultState());
        }
        if (bellPos != null && world.getBlockState(bellPos).isOf(Blocks.BELL)) {
            world.setBlockState(bellPos, Blocks.AIR.getDefaultState());
        }
        nodes.clear();
        bellPos = null;
    }

    private static void succeed(MinecraftServer server, ServerPlayerEntity p, MobEntity boss) {
        int done = idx;
        Vec3d at = boss.getPos();
        world.spawnParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0.0);
        world.spawnParticles(ParticleTypes.SOUL, at.x, at.y + 1, at.z, 90, 0.6, 1.0, 0.6, 0.08);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y + 1, at.z, 70, 0.5, 1.0, 0.5, 0.3);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_TOTEM_USE, SoundCategory.HOSTILE, 2.5F, 0.7F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 3.0F, 0.6F);
        HorrorUtil.dropFragment(boss, done);
        StructureManager.markDefeated(server, done);
        boss.discard();
        cleanup();
        active = false;
        SanMudo.say(p, Lore.DONE[done]);
        if (done == 5) {
            SanMudo.say(p, StructureManager.finalCoords(2) == null ? "El altar está bajo el pozo." : "El pozo está en x=" + StructureManager.finalCoords(2)[0] + " z=" + StructureManager.finalCoords(2)[1] + ".");
        } else {
            StalkerDirector.later(world, 120, () -> {
                if (!p.isRemoved()) SanMudo.hint(world, p);
            });
        }
    }

    private static void fail(MinecraftServer server, ServerPlayerEntity p, String msg) {
        active = false;
        if (world != null) {
            Entity be = world.getEntity(bossId);
            if (be instanceof MobEntity m) HorrorUtil.poof(m);
        }
        cleanup();
        if (p != null) {
            bar(p, msg + " Perdiste la Sal.");
            SanMudo.say(p, "No... otra vez no. Reúne otra Sal e inténtalo de nuevo.");
        }
    }
}
