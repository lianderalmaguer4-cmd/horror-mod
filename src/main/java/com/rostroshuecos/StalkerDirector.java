package com.rostroshuecos;

import com.rostroshuecos.entity.ChicaRotaEntity;
import com.rostroshuecos.entity.SombraHuecaEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;

/**
 * Director del acoso: decide cuando y donde aparecen "espias" (monstruos quietos que te observan)
 * segun donde estes: casa, exterior o cueva. Los espias desaparecen si te acercas o los miras mucho.
 */
public final class StalkerDirector {
    private StalkerDirector() {}

    private static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final Map<UUID, Integer> NEXT_EVENT = new HashMap<>();
    private static final Map<UUID, Integer> HIDE_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> LOOKED = new HashMap<>();

    private record Task(int at, Runnable run) {}
    private static final List<Task> TASKS = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (TASKS.isEmpty()) return;
            int now = server.getTicks();
            List<Runnable> due = new ArrayList<>();
            TASKS.removeIf(t -> {
                if (t.at() <= now) { due.add(t.run()); return true; }
                return false;
            });
            due.forEach(Runnable::run);
        });
        ServerTickEvents.END_WORLD_TICK.register(StalkerDirector::tickWorld);

        // Comandos de prueba (necesitan permisos / trucos activados): /rostros evento  y  /rostros susto
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("rostros")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("evento").executes(ctx -> {
                    ServerPlayerEntity pl = ctx.getSource().getPlayerOrThrow();
                    boolean ok = triggerEvent(pl.getServerWorld(), pl, true);
                    ctx.getSource().sendFeedback(() -> Text.literal(ok
                            ? "Evento lanzado."
                            : "No encontré un sitio válido aquí; muévete a otro lugar."), false);
                    return ok ? 1 : 0;
                }))
                .then(CommandManager.literal("susto").executes(ctx -> {
                    Jumpscare.send(ctx.getSource().getPlayerOrThrow(), 0);
                    return 1;
                }))));
    }

    // ------------------------------------------------------------------ bucle principal
    private static void tickWorld(ServerWorld w) {
        long time = w.getTime();
        int now = w.getServer().getTicks();
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator()) continue;
            UUID id = p.getUuid();

            if (time % 2 == 0) handleSpies(w, p);

            if (time % 10 == 0) handleHiding(w, p);

            if (time % 100 == 0) {
                Integer next = NEXT_EVENT.get(id);
                if (next != null && now < next) continue;
                if (triggerEvent(w, p, false)) {
                    NEXT_EVENT.put(id, now + 1800 + w.getRandom().nextInt(2400)); // 1.5 - 3.5 min
                }
            }
        }
    }

    // ------------------------------------------------------------------ eventos
    public static boolean triggerEvent(ServerWorld w, ServerPlayerEntity p, boolean force) {
        BlockPos pos = p.getBlockPos();
        Random r = w.getRandom();
        boolean sky = w.isSkyVisible(pos);
        List<BlockPos> doors = sky ? List.of() : scan(w, pos, 8, s -> s.isIn(BlockTags.DOORS));
        List<BlockPos> beds = sky ? List.of() : scan(w, pos, 8, s -> s.isIn(BlockTags.BEDS));
        boolean house = !doors.isEmpty() || !beds.isEmpty();
        long t = w.getTimeOfDay() % 24000L;
        boolean night = t >= 13000 && t <= 23000;

        if (house) {
            if (!force && r.nextInt(night ? 3 : 8) != 0) return false;
            if (r.nextInt(2) == 0 && !doors.isEmpty()) {
                return knock(w, p, doors.get(r.nextInt(doors.size())));
            }
            List<BlockPos> glass = scan(w, pos, 8, s -> s.isOf(Blocks.GLASS_PANE) || s.isOf(Blocks.GLASS));
            if (!glass.isEmpty() && window(w, p, glass)) return true;
            if (!doors.isEmpty()) return knock(w, p, doors.get(r.nextInt(doors.size())));
            return false;
        }
        if (sky) {
            if (!force && r.nextInt(night ? 4 : 25) != 0) return false;
            return tree(w, p);
        }
        // subterraneo
        if (!force && (r.nextInt(6) != 0 || w.getLightLevel(pos) > 7)) return false;
        return cave(w, p);
    }

    /** Golpes en la puerta (3 toques) y, a veces, algo aparece fuera despues. */
    private static boolean knock(ServerWorld w, ServerPlayerEntity p, BlockPos door) {
        int now = w.getServer().getTicks();
        for (int i = 0; i < 3; i++) {
            TASKS.add(new Task(now + i * 14, () -> w.playSound(null,
                    door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5,
                    SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, 1.4F, 0.7F)));
        }
        if (w.getRandom().nextInt(5) < 3) {
            TASKS.add(new Task(now + 70, () -> spawnSpy(w, p, beyond(w, p, door, 2.0), HorrorUtil.TAG_ESPIA, false, false)));
        }
        return true;
    }

    /** Un rostro aparece justo al otro lado de una ventana. */
    private static boolean window(ServerWorld w, ServerPlayerEntity p, List<BlockPos> glass) {
        Random r = w.getRandom();
        for (int i = 0; i < 6; i++) {
            BlockPos g = glass.get(r.nextInt(glass.size()));
            if (spawnSpy(w, p, beyond(w, p, g, 2.0), HorrorUtil.TAG_ESPIA, false, false)) return true;
        }
        return false;
    }

    /** Al otro lado de un tronco, a 14-28 bloques, medio escondido. */
    private static boolean tree(ServerWorld w, ServerPlayerEntity p) {
        Random r = w.getRandom();
        for (int i = 0; i < 40; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = 14 + r.nextInt(14);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos base = new BlockPos(x, top - 1, z);
            if (!w.getBlockState(base).isIn(BlockTags.LOGS)) continue;
            while (w.getBlockState(base.down()).isIn(BlockTags.LOGS)) base = base.down();
            Vec3d away = new Vec3d(base.getX() + 0.5 - p.getX(), 0, base.getZ() + 0.5 - p.getZ()).normalize();
            for (Direction d : DIRS) {
                if (d.getOffsetX() * away.x + d.getOffsetZ() * away.z < 0.3) continue;
                BlockPos s = base.offset(d);
                if (isFree(w, s) && isFree(w, s.up()) && solidBelow(w, s)
                        && spawnSpy(w, p, s, HorrorUtil.TAG_ESPIA, false, false)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** En una cueva: pegado a una pared, con linea de vision. */
    private static boolean cave(ServerWorld w, ServerPlayerEntity p) {
        Random r = w.getRandom();
        for (int i = 0; i < 60; i++) {
            int dx = r.nextInt(33) - 16;
            int dz = r.nextInt(33) - 16;
            int dy = r.nextInt(9) - 4;
            if (dx * dx + dz * dz < 100) continue;
            BlockPos c = p.getBlockPos().add(dx, dy, dz);
            if (!w.isChunkLoaded(c.getX() >> 4, c.getZ() >> 4)) continue;
            if (!isFree(w, c) || !isFree(w, c.up()) || !solidBelow(w, c)) continue;
            if (w.getLightLevel(c) > 7) continue;
            boolean wall = false;
            for (Direction d : DIRS) {
                BlockPos n = c.offset(d);
                if (!w.getBlockState(n).getCollisionShape(w, n).isEmpty()) wall = true;
            }
            if (!wall) continue;
            if (spawnSpy(w, p, c, HorrorUtil.TAG_ESPIA, true, false)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ espias
    private static boolean spawnSpy(ServerWorld w, ServerPlayerEntity p, BlockPos pos, String tag,
                                    boolean needSight, boolean hostileOnly) {
        if (pos == null || p.isRemoved()) return false;
        boolean peaceful = w.getDifficulty() == Difficulty.PEACEFUL;
        List<EntityType<? extends MobEntity>> pool;
        if (hostileOnly) {
            if (peaceful) return false;
            pool = List.of(ModEntities.CHICA_ROTA, ModEntities.SOMBRA_HUECA);
        } else if (peaceful) {
            pool = List.of(ModEntities.NINO_GRIS, ModEntities.MUNECO_PERDIDO);
        } else {
            pool = List.of(ModEntities.CHICA_ROTA, ModEntities.SOMBRA_HUECA,
                    ModEntities.NINO_GRIS, ModEntities.MUNECO_PERDIDO);
        }
        EntityType<? extends MobEntity> type = pool.get(w.getRandom().nextInt(pool.size()));
        MobEntity e = type.spawn(w, pos, SpawnReason.EVENT);
        if (e == null) return false;
        e.addCommandTag(tag);
        e.setAiDisabled(true);
        e.setNoGravity(true);
        e.lookAtEntity(p, 360.0F, 360.0F);
        if (needSight && !p.canSee(e)) {
            e.discard();
            return false;
        }
        return true;
    }

    private static void handleSpies(ServerWorld w, ServerPlayerEntity p) {
        List<MobEntity> spies = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(64.0), HorrorUtil::isSpy);
        for (MobEntity spy : spies) {
            spy.setAiDisabled(true);
            spy.lookAtEntity(p, 60.0F, 60.0F);

            if (spy.getCommandTags().contains(HorrorUtil.TAG_ESPIA_CAMA)) {
                if (!isHiding(w, p)) {
                    // saliste de tu escondite: te ataca
                    spy.removeCommandTag(HorrorUtil.TAG_ESPIA_CAMA);
                    spy.setAiDisabled(false);
                    spy.setNoGravity(false);
                    spy.setTarget(p);
                    Jumpscare.send(p, 0, Jumpscare.mobOf(spy));
                } else if (spy.age > 240) {
                    vanish(w, spy, p, false);
                }
                continue;
            }

            double d2 = spy.squaredDistanceTo(p);
            UUID id = spy.getUuid();
            int looked = LOOKED.getOrDefault(id, 0);
            if (HorrorUtil.isLookingAt(p, spy)) {
                looked += 2;
                LOOKED.put(id, looked);
            }
            // se esconde si te acercas (< 9 bloques), lo miras ~2.5 s, o lleva demasiado tiempo
            if (d2 < 81.0 || looked >= 50 || spy.age > 2400) {
                vanish(w, spy, p, true);
            }
        }
    }

    private static void vanish(ServerWorld w, MobEntity spy, ServerPlayerEntity p, boolean maybeScare) {
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, spy.getX(), spy.getY() + spy.getHeight() / 2, spy.getZ(),
                20, 0.3, 0.5, 0.3, 0.02);
        w.playSound(null, spy.getX(), spy.getY(), spy.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 0.5F);
        LOOKED.remove(spy.getUuid());
        spy.discard();
        if (maybeScare && w.getRandom().nextInt(5) == 0) {
            Jumpscare.send(p, 1, Jumpscare.mobOf(spy));
        }
    }

    // ------------------------------------------------------------------ esconderse junto a la cama
    /** Agachado (Shift) a menos de 2 bloques de una cama, en interior = escondido. */
    private static boolean isHiding(ServerWorld w, ServerPlayerEntity p) {
        if (!p.isSneaking() || w.isSkyVisible(p.getBlockPos())) return false;
        BlockPos c = p.getBlockPos();
        for (BlockPos q : BlockPos.iterate(c.add(-2, -1, -2), c.add(2, 1, 2))) {
            if (w.getBlockState(q).isIn(BlockTags.BEDS)) return true;
        }
        return false;
    }

    private static void handleHiding(ServerWorld w, ServerPlayerEntity p) {
        UUID id = p.getUuid();
        if (!isHiding(w, p)) {
            HIDE_TICKS.remove(id);
            return;
        }
        int h = HIDE_TICKS.merge(id, 10, Integer::sum);
        if (h % 40 == 0 || h == 10) {
            w.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 1.0F, 1.0F);
        }
        // mientras te escondes, los monstruos agresivos pierden tu rastro
        for (MobEntity m : w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(32.0),
                e -> e.getTarget() == p && (e instanceof ChicaRotaEntity || e instanceof SombraHuecaEntity)
                        && !HorrorUtil.isSpy(e))) {
            m.setTarget(null);
        }
        // a los 8 s escondido: un monstruo entra a buscarte
        if (h == 160) {
            Random r = w.getRandom();
            for (int i = 0; i < 30; i++) {
                int dx = r.nextInt(11) - 5;
                int dz = r.nextInt(11) - 5;
                if (dx * dx + dz * dz < 9) continue;
                BlockPos c = p.getBlockPos().add(dx, 0, dz);
                if (!isFree(w, c) || !isFree(w, c.up()) || !solidBelow(w, c) || w.isSkyVisible(c)) continue;
                if (spawnSpy(w, p, c, HorrorUtil.TAG_ESPIA_CAMA, false, true)) break;
            }
        }
    }

    // ------------------------------------------------------------------ utilidades de mundo
    private static List<BlockPos> scan(ServerWorld w, BlockPos c, int r, Predicate<BlockState> f) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos q : BlockPos.iterate(c.add(-r, -3, -r), c.add(r, 3, r))) {
            if (f.test(w.getBlockState(q))) out.add(q.toImmutable());
        }
        return out;
    }

    private static boolean isFree(ServerWorld w, BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        return s.getCollisionShape(w, pos).isEmpty() && s.getFluidState().isEmpty();
    }

    private static boolean solidBelow(ServerWorld w, BlockPos pos) {
        BlockPos b = pos.down();
        return !w.getBlockState(b).getCollisionShape(w, b).isEmpty();
    }

    /** Punto en el suelo a 'dist' bloques mas alla de 'target', visto desde el jugador. */
    private static BlockPos beyond(ServerWorld w, ServerPlayerEntity p, BlockPos target, double dist) {
        Vec3d d = new Vec3d(target.getX() + 0.5 - p.getX(), 0, target.getZ() + 0.5 - p.getZ());
        if (d.lengthSquared() < 0.01) return null;
        d = d.normalize().multiply(dist);
        BlockPos q = BlockPos.ofFloored(target.getX() + 0.5 + d.x, target.getY() + 1, target.getZ() + 0.5 + d.z);
        for (int i = 0; i <= 4; i++) {
            BlockPos c = q.down(i);
            if (isFree(w, c) && isFree(w, c.up()) && solidBelow(w, c)) return c;
        }
        return null;
    }
}
