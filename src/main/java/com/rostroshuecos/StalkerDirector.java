package com.rostroshuecos;

import com.rostroshuecos.entity.AcosadorEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;

/**
 * Director del acoso. Segun donde estes (casa, exterior, cueva) lanza eventos: golpes en la puerta,
 * rostros en ventanas, figuras tras los arboles o en las cuevas, pasos, cajas de musica, susurros,
 * apagones y cacerias. Solo puede existir una entidad de cada tipo a la vez.
 */
public final class StalkerDirector {
    private StalkerDirector() {}

    private static final Direction[] DIRS = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    // que monstruos prefiere cada evento (si ya existen, se usa otro libre)
    private static final List<EntityType<? extends MobEntity>> AGGRESSIVE = List.of(
            ModEntities.RAIZ, ModEntities.SUDARIO, ModEntities.HUECO, ModEntities.MANDIBULA, ModEntities.DOBLE_BOCA);
    private static final List<EntityType<? extends MobEntity>> TREE_PREF = List.of(ModEntities.RAIZ, ModEntities.SUDARIO);
    private static final List<EntityType<? extends MobEntity>> CAVE_PREF = List.of(
            ModEntities.SOMBRA_HUECA, ModEntities.HUECO, ModEntities.MANDIBULA);
    private static final List<EntityType<? extends MobEntity>> WINDOW_PREF = List.of(
            ModEntities.HUECO, ModEntities.DOBLE_BOCA, ModEntities.MANDIBULA);
    private static final List<EntityType<? extends MobEntity>> DOOR_PREF = List.of(
            ModEntities.DOBLE_BOCA, ModEntities.MANDIBULA, ModEntities.SUDARIO, ModEntities.SOMBRA_HUECA);

    private static final Map<UUID, Integer> NEXT_EVENT = new HashMap<>();
    private static final Map<UUID, Integer> HIDE_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> LOOKED = new HashMap<>();
    private static final Map<UUID, BlockPos> DOOR_OF = new HashMap<>();

    private static final SoundEvent[] DISTANT = {
        SoundEvents.ENTITY_GHAST_SCREAM, SoundEvents.ENTITY_ENDERMAN_SCREAM,
        SoundEvents.ENTITY_PHANTOM_AMBIENT, SoundEvents.ENTITY_WITCH_AMBIENT, SoundEvents.BLOCK_BELL_USE
    };
    private static final float[] MUSIC = {1.0F, 1.26F, 1.5F, 1.26F, 1.19F, 1.0F, 0.89F, 0.84F, 0.75F, 0.71F};

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

        // Doble Boca repite en el chat lo que escribes (si existe en el mundo)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            ServerWorld sw = sender.getServerWorld();
            if (!HorrorUtil.exists(sw, ModEntities.DOBLE_BOCA)) return;
            String txt = message.getContent().getString();
            if (txt.isBlank()) return;
            sw.getServer().execute(() -> {
                int now = sw.getServer().getTicks();
                TASKS.add(new Task(now + 30 + sw.getRandom().nextInt(40), () -> {
                    if (sender.isRemoved()) return;
                    sender.sendMessage(Text.literal("<Doble Boca> " + txt + "… " + txt)
                            .formatted(Formatting.DARK_RED), false);
                    sw.playSound(null, sender.getX(), sender.getY(), sender.getZ(),
                            SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.HOSTILE, 1.2F, 0.5F);
                }));
            });
        });

        // Comandos de prueba (necesitan trucos): /rostros evento  y  /rostros susto
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("rostros")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("evento").executes(ctx -> {
                    ServerPlayerEntity pl = ctx.getSource().getPlayerOrThrow();
                    boolean ok = triggerEvent(pl.getServerWorld(), pl, true);
                    ctx.getSource().sendFeedback(() -> Text.literal(ok
                            ? "Evento lanzado."
                            : "No se pudo lanzar un evento aquí (quizá ya existen esas entidades); prueba otra vez o muévete."), false);
                    return ok ? 1 : 0;
                }))
                .then(CommandManager.literal("susto").executes(ctx -> {
                    Jumpscare.send(ctx.getSource().getPlayerOrThrow(), 0, 0);
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
                    NEXT_EVENT.put(id, now + 500 + w.getRandom().nextInt(800)); // 25 - 65 s
                }
            }
        }
    }

    // ------------------------------------------------------------------ eventos
    public static boolean triggerEvent(ServerWorld w, ServerPlayerEntity p, boolean force) {
        BlockPos pos = p.getBlockPos();
        Random r = w.getRandom();
        boolean sky = w.isSkyVisible(pos);
        List<BlockPos> doors = sky ? List.of() : scan(w, pos, 8, s -> s.isIn(BlockTags.WOODEN_DOORS));
        List<BlockPos> beds = sky ? List.of() : scan(w, pos, 8, s -> s.isIn(BlockTags.BEDS));
        boolean house = !doors.isEmpty() || !beds.isEmpty();
        boolean cave = !sky && !house;
        long t = w.getTimeOfDay() % 24000L;
        boolean night = t >= 13000 && t <= 23000;

        if (!force) {
            int den = house ? (night ? 2 : 4) : sky ? (night ? 3 : 10) : 3;
            if (r.nextInt(den) != 0) return false;
            if (cave && w.getLightLevel(pos) > 7) return false;
        }

        List<BlockPos> glass = house
                ? scan(w, pos, 8, s -> s.isOf(Blocks.GLASS_PANE) || s.isOf(Blocks.GLASS))
                : List.of();

        List<Supplier<Boolean>> evs = new ArrayList<>();
        if (house) {
            if (!doors.isEmpty()) {
                evs.add(() -> knock(w, p, doors.get(r.nextInt(doors.size()))));
                evs.add(() -> knock(w, p, doors.get(r.nextInt(doors.size()))));
                evs.add(() -> doorOpens(w, doors));
            }
            if (!glass.isEmpty()) {
                evs.add(() -> window(w, p, glass));
                evs.add(() -> window(w, p, glass));
            }
            evs.add(() -> footsteps(w, p, 0));
        } else if (sky) {
            evs.add(() -> tree(w, p));
            evs.add(() -> tree(w, p));
            evs.add(() -> footsteps(w, p, 2));
            if (night) evs.add(() -> hunt(w, p));
        } else {
            evs.add(() -> cave(w, p));
            evs.add(() -> cave(w, p));
            evs.add(() -> footsteps(w, p, 1));
            evs.add(() -> hunt(w, p));
        }
        evs.add(() -> musicBox(w, p));
        evs.add(() -> whisper(w, p));
        evs.add(() -> distantScream(w, p));
        if (!sky || night) evs.add(() -> blackout(w, p));

        Collections.shuffle(evs, new java.util.Random(r.nextLong()));
        for (Supplier<Boolean> ev : evs) {
            if (ev.get()) return true;
        }
        return false;
    }

    /** Alguien llama a la puerta y se queda esperando fuera. */
    private static boolean knock(ServerWorld w, ServerPlayerEntity p, BlockPos any) {
        BlockPos low = lowerDoor(w, any);
        BlockState ls = w.getBlockState(low);
        if (!(ls.getBlock() instanceof DoorBlock) || ls.get(DoorBlock.OPEN)) return false;
        Direction f = ls.get(DoorBlock.FACING);
        BlockPos a = low.offset(f);
        BlockPos b = low.offset(f.getOpposite());
        BlockPos out = dist2(a, p) > dist2(b, p) ? a : b;   // el lado mas lejano al jugador
        if (!(isFree(w, out) && isFree(w, out.up()) && solidBelow(w, out))) return false;

        MobEntity visitor = spawnSpy(w, p, out, HorrorUtil.TAG_PUERTA, false, DOOR_PREF, false);
        if (visitor == null) return false;
        DOOR_OF.put(visitor.getUuid(), low);

        int now = w.getServer().getTicks();
        for (int i = 0; i < 3; i++) {
            TASKS.add(new Task(now + i * 14, () -> knockSound(w, low)));
        }
        return true;
    }

    private static void knockSound(ServerWorld w, BlockPos door) {
        w.playSound(null, door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5,
                SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, 1.6F, 0.7F);
    }

    /** Una puerta se abre sola, con su sonido. */
    private static boolean doorOpens(ServerWorld w, List<BlockPos> doors) {
        Random r = w.getRandom();
        for (int i = 0; i < 6; i++) {
            BlockPos low = lowerDoor(w, doors.get(r.nextInt(doors.size())));
            BlockState ls = w.getBlockState(low);
            if (!(ls.getBlock() instanceof DoorBlock) || ls.get(DoorBlock.OPEN)) continue;
            w.setBlockState(low, ls.with(DoorBlock.OPEN, true), Block.NOTIFY_ALL);
            BlockPos up = low.up();
            BlockState us = w.getBlockState(up);
            if (us.getBlock() instanceof DoorBlock) {
                w.setBlockState(up, us.with(DoorBlock.OPEN, true), Block.NOTIFY_ALL);
            }
            w.playSound(null, low, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 0.7F);
            return true;
        }
        return false;
    }

    /** Un rostro aparece justo al otro lado de una ventana. */
    private static boolean window(ServerWorld w, ServerPlayerEntity p, List<BlockPos> glass) {
        Random r = w.getRandom();
        for (int i = 0; i < 6; i++) {
            BlockPos g = glass.get(r.nextInt(glass.size()));
            if (spawnSpy(w, p, beyond(w, p, g, 2.0), HorrorUtil.TAG_ESPIA, false, WINDOW_PREF, false) != null) return true;
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
                        && spawnSpy(w, p, s, HorrorUtil.TAG_ESPIA, false, TREE_PREF, false) != null) {
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
            if (spawnSpy(w, p, c, HorrorUtil.TAG_ESPIA, true, CAVE_PREF, false) != null) return true;
        }
        return false;
    }

    /** Un monstruo agresivo aparece a 16-26 bloques y te persigue (desaparece solo tras un rato). */
    private static boolean hunt(ServerWorld w, ServerPlayerEntity p) {
        if (w.getDifficulty() == Difficulty.PEACEFUL) return false;
        Random r = w.getRandom();
        for (int i = 0; i < 40; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = 16 + r.nextInt(10);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            BlockPos c = groundNear(w, new BlockPos(x, p.getBlockY(), z), 6);
            if (c == null) continue;
            MobEntity m = spawnMob(w, c, AGGRESSIVE, false);
            if (m == null) return false;   // ya existen todas las agresivas
            m.setTarget(p);
            w.playSound(null, m.getX(), m.getY(), m.getZ(),
                    SoundEvents.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 3.0F, 0.5F);
            return true;
        }
        return false;
    }

    /** Pasos que se acercan por detras y se detienen. kind: 0 madera, 1 piedra, 2 hierba. */
    private static boolean footsteps(ServerWorld w, ServerPlayerEntity p, int kind) {
        Vec3d look = p.getRotationVec(1.0F);
        Vec3d flat = new Vec3d(-look.x, 0, -look.z);
        if (flat.lengthSquared() < 0.01) return false;
        final Vec3d back = flat.normalize();
        final SoundEvent step = kind == 0 ? SoundEvents.BLOCK_WOOD_STEP
                : kind == 1 ? SoundEvents.BLOCK_STONE_STEP : SoundEvents.BLOCK_GRASS_STEP;
        int now = w.getServer().getTicks();
        for (int i = 0; i < 8; i++) {
            final double dist = 9.0 - i * 1.1;
            TASKS.add(new Task(now + i * 9, () -> {
                if (p.isRemoved()) return;
                Vec3d pos = p.getPos().add(back.multiply(dist));
                w.playSound(null, pos.x, pos.y, pos.z, step, SoundCategory.HOSTILE, 1.4F, 0.75F);
            }));
        }
        return true;
    }

    /** Una melodia de caja de musica lejana, desafinada al final. */
    private static boolean musicBox(ServerWorld w, ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0F);
        Vec3d flat = new Vec3d(-look.x, 0, -look.z);
        if (flat.lengthSquared() < 0.01) return false;
        final Vec3d at = p.getPos().add(flat.normalize().multiply(10));
        int now = w.getServer().getTicks();
        for (int i = 0; i < MUSIC.length; i++) {
            final float pitch = MUSIC[i];
            TASKS.add(new Task(now + i * 10, () ->
                    w.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                            SoundCategory.RECORDS, 2.0F, pitch)));
        }
        return true;
    }

    /** Un mensaje susurrado en el chat. */
    private static boolean whisper(ServerWorld w, ServerPlayerEntity p) {
        String line = Lore.WHISPERS[w.getRandom().nextInt(Lore.WHISPERS.length)];
        p.sendMessage(Text.literal(line).formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDERMAN_AMBIENT,
                SoundCategory.AMBIENT, 1.0F, 0.5F);
        return true;
    }

    /** Un grito muy lejano. */
    private static boolean distantScream(ServerWorld w, ServerPlayerEntity p) {
        Random r = w.getRandom();
        double ang = r.nextDouble() * Math.PI * 2;
        double x = p.getX() + Math.cos(ang) * 30;
        double z = p.getZ() + Math.sin(ang) * 30;
        w.playSound(null, x, p.getY(), z, DISTANT[r.nextInt(DISTANT.length)],
                SoundCategory.HOSTILE, 4.0F, 0.5F + r.nextFloat() * 0.3F);
        return true;
    }

    /** Oscuridad repentina con latidos. */
    private static boolean blackout(ServerWorld w, ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 160, 0, false, false));
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                SoundCategory.AMBIENT, 1.5F, 1.0F);
        int now = w.getServer().getTicks();
        for (int i = 1; i <= 3; i++) {
            TASKS.add(new Task(now + i * 40, () -> {
                if (!p.isRemoved()) {
                    w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                            SoundCategory.AMBIENT, 1.5F, 1.0F);
                }
            }));
        }
        return true;
    }

    // ------------------------------------------------------------------ aparecer entidades
    /**
     * Genera un monstruo de un tipo que aun no exista en el mundo (solo uno de cada).
     * Prefiere los de 'prefer'; si 'strict' y no hay ninguno libre de esos, no genera nada.
     */
    private static MobEntity spawnMob(ServerWorld w, BlockPos pos, List<EntityType<? extends MobEntity>> prefer, boolean strict) {
        if (w.getDifficulty() == Difficulty.PEACEFUL) return null;   // los monstruos no existen en pacifico
        List<EntityType<? extends MobEntity>> all = new ArrayList<>(AGGRESSIVE);
        all.add(ModEntities.SOMBRA_HUECA);
        all.removeIf(type -> HorrorUtil.exists(w, type));
        List<EntityType<? extends MobEntity>> pool = new ArrayList<>(all);
        pool.removeIf(type -> !prefer.contains(type));
        if (pool.isEmpty()) {
            if (strict) return null;
            pool = all;
        }
        if (pool.isEmpty()) return null;
        EntityType<? extends MobEntity> type = pool.get(w.getRandom().nextInt(pool.size()));
        return type.spawn(w, pos, SpawnReason.EVENT);
    }

    /** Genera un monstruo quieto (espia) con una etiqueta de comportamiento. */
    private static MobEntity spawnSpy(ServerWorld w, ServerPlayerEntity p, BlockPos pos, String tag, boolean needSight,
                                      List<EntityType<? extends MobEntity>> prefer, boolean strict) {
        if (pos == null || p.isRemoved()) return null;
        MobEntity e = spawnMob(w, pos, prefer, strict);
        if (e == null) return null;
        e.addCommandTag(tag);
        e.setAiDisabled(true);
        e.setNoGravity(true);
        e.lookAtEntity(p, 360.0F, 360.0F);
        if (needSight && !p.canSee(e)) {
            e.discard();
            return null;
        }
        return e;
    }

    // ------------------------------------------------------------------ comportamiento de espias
    private static void handleSpies(ServerWorld w, ServerPlayerEntity p) {
        List<MobEntity> spies = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(64.0), HorrorUtil::isSpy);
        for (MobEntity spy : spies) {
            spy.setAiDisabled(true);
            spy.lookAtEntity(p, 60.0F, 60.0F);

            if (spy.getCommandTags().contains(HorrorUtil.TAG_PUERTA)) {
                handleVisitor(w, p, spy);
                continue;
            }

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

    /** El que llamo a la puerta: si abres, el agresivo ataca; el pacifico te deja una carta y se va. */
    private static void handleVisitor(ServerWorld w, ServerPlayerEntity p, MobEntity v) {
        BlockPos low = DOOR_OF.get(v.getUuid());
        if (low == null) {
            vanish(w, v, p, false);
            return;
        }
        if (v.squaredDistanceTo(p) > 400.0) return;   // jugador demasiado lejos

        BlockState ds = w.getBlockState(low);
        boolean opened = !(ds.getBlock() instanceof DoorBlock) || ds.get(DoorBlock.OPEN);
        if (opened) {
            DOOR_OF.remove(v.getUuid());
            v.removeCommandTag(HorrorUtil.TAG_PUERTA);
            if (HorrorUtil.isAggressive(v)) {
                v.setAiDisabled(false);
                v.setNoGravity(false);
                v.setTarget(p);
                Jumpscare.send(p, 0, Jumpscare.mobOf(v));
            } else {
                giveLetter(w, p, v);
                HorrorUtil.poof(v);
            }
        } else {
            int phase = v.age % 100;
            if (phase >= 40 && phase < 42) {
                int now = w.getServer().getTicks();
                TASKS.add(new Task(now, () -> knockSound(w, low)));
                TASKS.add(new Task(now + 12, () -> knockSound(w, low)));
            }
            if (v.age > 700) vanish(w, v, p, false);   // se cansa de esperar
        }
    }

    private static void giveLetter(ServerWorld w, ServerPlayerEntity p, MobEntity v) {
        p.giveItemStack(new ItemStack(ModItems.NOTA_ROTA));
        int page = w.getRandom().nextInt(Lore.PAGES.length);
        p.sendMessage(Text.literal("<").append(v.getName()).append("> ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(Lore.PAGES[page]).formatted(Formatting.GRAY, Formatting.ITALIC)), false);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN,
                SoundCategory.PLAYERS, 1.0F, 0.6F);
    }

    /** Los pacificos nunca asustan; solo los agresivos tienen un susto glitch al esfumarse. */
    private static void vanish(ServerWorld w, MobEntity spy, ServerPlayerEntity p, boolean maybeScare) {
        UUID id = spy.getUuid();
        LOOKED.remove(id);
        DOOR_OF.remove(id);
        boolean aggressive = HorrorUtil.isAggressive(spy);
        int mob = Jumpscare.mobOf(spy);
        HorrorUtil.poof(spy);
        if (maybeScare && aggressive && w.getRandom().nextInt(5) == 0) {
            Jumpscare.send(p, 1, mob);
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
                e -> e.getTarget() == p && e instanceof AcosadorEntity && !HorrorUtil.isSpy(e))) {
            m.setTarget(null);
        }
        // a los 8 s escondido: un monstruo agresivo entra a buscarte
        if (h == 160) {
            Random r = w.getRandom();
            for (int i = 0; i < 30; i++) {
                int dx = r.nextInt(11) - 5;
                int dz = r.nextInt(11) - 5;
                if (dx * dx + dz * dz < 9) continue;
                BlockPos c = p.getBlockPos().add(dx, 0, dz);
                if (!isFree(w, c) || !isFree(w, c.up()) || !solidBelow(w, c) || w.isSkyVisible(c)) continue;
                if (spawnSpy(w, p, c, HorrorUtil.TAG_ESPIA_CAMA, false, AGGRESSIVE, true) != null) break;
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

    private static BlockPos lowerDoor(ServerWorld w, BlockPos any) {
        BlockState s = w.getBlockState(any);
        if (s.contains(DoorBlock.HALF) && s.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return any.down();
        return any;
    }

    private static double dist2(BlockPos b, ServerPlayerEntity p) {
        double dx = b.getX() + 0.5 - p.getX();
        double dz = b.getZ() + 0.5 - p.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean isFree(ServerWorld w, BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        return s.getCollisionShape(w, pos).isEmpty() && s.getFluidState().isEmpty();
    }

    private static boolean solidBelow(ServerWorld w, BlockPos pos) {
        BlockPos b = pos.down();
        return !w.getBlockState(b).getCollisionShape(w, b).isEmpty();
    }

    /** Suelo libre cerca de 'start' (hasta 2 bloques arriba y 'down' abajo). */
    private static BlockPos groundNear(ServerWorld w, BlockPos start, int down) {
        for (int i = -2; i <= down; i++) {
            BlockPos c = start.down(i);
            if (isFree(w, c) && isFree(w, c.up()) && solidBelow(w, c)) return c;
        }
        return null;
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
