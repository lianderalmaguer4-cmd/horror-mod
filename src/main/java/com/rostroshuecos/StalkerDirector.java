package com.rostroshuecos;

import com.rostroshuecos.entity.AcosadorEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
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
    private static final Map<UUID, BlockPos> HIDDEN = new HashMap<>();      // jugador escondido -> su cama
    private static final Map<UUID, Double> WINDOW_Y = new HashMap<>();       // visitante que sube -> altura final
    private static final Map<UUID, BlockPos> WINDOW_G = new HashMap<>();    // visitante de ventana -> su cristal
    private static final Map<UUID, UUID> BED_OWNER = new HashMap<>();        // monstruo bajo la cama -> jugador que duerme
    private static final Set<UUID> BED_DONE = new HashSet<>();               // ya asusto/mato
    private static final Map<UUID, Vec3d> HIDE_RETURN = new HashMap<>();     // donde estaba el jugador antes de esconderse

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

        // Al entrar: el diario inicial (una sola vez) y se limpia cualquier escondite viejo
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity pl = handler.getPlayer();
            HIDDEN.remove(pl.getUuid());
            ServerPlayNetworking.send(pl, new HidePayload(false));
            Diary.giveIfNeeded(pl);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> unhide(handler.getPlayer(), false));

        // Esconderse: Shift + clic derecho en una cama (bajo techo). Clic derecho de nuevo para salir.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity sp)) {
                return ActionResult.PASS;
            }
            if (HIDDEN.containsKey(sp.getUuid())) {   // escondido: cualquier clic derecho te saca
                Integer ht = HIDE_TICKS.get(sp.getUuid());
                if (ht != null && ht < 10) return ActionResult.SUCCESS;   // ignora el clic que te acaba de esconder
                unhide(sp);
                return ActionResult.SUCCESS;
            }
            BlockPos pos = hit.getBlockPos();
            if (world.getBlockState(pos).getBlock() instanceof DoorBlock) {
                ServerWorld dw = sp.getServerWorld();
                if (StructureManager.isCorruptDoor(dw, pos)) {
                    DimensionTravel.enter(sp);
                    return ActionResult.SUCCESS;
                }
                if (StructureManager.isReturnDoor(dw, pos)) {
                    DimensionTravel.leave(sp);
                    return ActionResult.SUCCESS;
                }
            }
            if (world.getBlockState(pos).isOf(Blocks.CRYING_OBSIDIAN) && StructureManager.nearFinal(sp.getServerWorld(), pos)) {
                return FinalEvent.useAltar(sp, sp.getServerWorld(), pos);
            }
            if (!world.getBlockState(pos).isIn(BlockTags.BEDS)) return ActionResult.PASS;
            if (!sp.isSneaking()) return ActionResult.PASS;   // sin Shift: dormir como siempre
            ServerWorld sw = sp.getServerWorld();
            if (sw.isSkyVisible(pos.up())) {
                sp.sendMessage(Text.literal("Necesitas un techo sobre la cama para esconderte."), true);
                return ActionResult.SUCCESS;
            }
            if (FinalEvent.active) {
                sp.sendMessage(Text.literal("No puedes esconderte ahora."), true);
                return ActionResult.SUCCESS;
            }
            hide(sw, sp, pos.toImmutable());
            return ActionResult.SUCCESS;
        });

        // Comandos: /rostros diario (todos), el resto necesita trucos
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("rostros")
                .then(CommandManager.literal("diario").executes(ctx -> {
                    ctx.getSource().getPlayerOrThrow().giveItemStack(new ItemStack(ModItems.DIARIO));
                    return 1;
                }))
                .then(CommandManager.literal("evento").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity pl = ctx.getSource().getPlayerOrThrow();
                    boolean ok = triggerEvent(pl.getServerWorld(), pl, true);
                    ctx.getSource().sendFeedback(() -> Text.literal(ok
                            ? "Evento lanzado."
                            : "No se pudo lanzar un evento aquí (quizá ya existen esas entidades); prueba otra vez o muévete."), false);
                    return ok ? 1 : 0;
                }))
                .then(CommandManager.literal("susto").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    Jumpscare.send(ctx.getSource().getPlayerOrThrow(), 0);
                    return 1;
                }))
                .then(CommandManager.literal("estructura").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity pl = ctx.getSource().getPlayerOrThrow();
                    ServerWorld sw = pl.getServerWorld();
                    boolean ok = StructureManager.forcePlace(sw, pl, sw.getRandom().nextInt(3));
                    ctx.getSource().sendFeedback(() -> Text.literal(ok
                            ? "Ruina creada delante de ti."
                            : "No se pudo crear aquí (terreno sin cargar o con agua)."), false);
                    return ok ? 1 : 0;
                }))
                .then(CommandManager.literal("fragmentos").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity pl = ctx.getSource().getPlayerOrThrow();
                    for (Item fr : ModItems.FRAGMENTOS) pl.giveItemStack(new ItemStack(fr));
                    return 1;
                }))
                .then(CommandManager.literal("notas").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    ctx.getSource().getPlayerOrThrow().giveItemStack(new ItemStack(ModItems.NOTA_ROTA, 10));
                    return 1;
                }))
                .then(CommandManager.literal("puerta").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity pl = ctx.getSource().getPlayerOrThrow();
                    boolean ok = StructureManager.forcePlace(pl.getServerWorld(), pl, 6);
                    ctx.getSource().sendFeedback(() -> Text.literal(ok
                            ? "Puerta Corrupta creada delante de ti."
                            : "No se pudo crear aquí (terreno sin cargar o con agua)."), false);
                    return ok ? 1 : 0;
                }))
                .then(CommandManager.literal("pradera").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    DimensionTravel.enter(ctx.getSource().getPlayerOrThrow(), false);
                    return 1;
                }))
                .then(CommandManager.literal("ceniza").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    DimensionTravel.enter(ctx.getSource().getPlayerOrThrow(), true);
                    return 1;
                }))
                .then(CommandManager.literal("final").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity pl = ctx.getSource().getPlayerOrThrow();
                    boolean ok = StructureManager.forcePlace(pl.getServerWorld(), pl, 5);
                    ctx.getSource().sendFeedback(() -> Text.literal(ok
                            ? "Pozo de San Mudo creado delante de ti."
                            : "No se pudo crear aquí (terreno sin cargar o con agua)."), false);
                    return ok ? 1 : 0;
                }))
                .then(CommandManager.literal("estructuras").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                    List<String> l = StructureManager.describe();
                    ctx.getSource().sendFeedback(() -> Text.literal(l.isEmpty() ? "Aún no hay ruinas." : String.join("\n", l)), false);
                    return l.size();
                }))));
    }

    // ------------------------------------------------------------------ bucle principal
    private static void tickWorld(ServerWorld w) {
        long time = w.getTime();
        int now = w.getServer().getTicks();
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator()) continue;
            UUID id = p.getUuid();

            // rara vez, al empezar a dormir: algo sale de debajo de la cama
            if (p.isSleeping() && p.getSleepTimer() == 10 && w.getRandom().nextInt(5) == 0) underBedEvent(w, p);

            if (time % 2 == 0) handleSpies(w, p);
            if (time % 10 == 0) handleHiding(w, p);

            if (time % (HorrorUtil.isModDim(w) ? 100 : 200) == 0) StructureManager.tick(w, p);

            if (time % 100 == 0) {
                if (FinalEvent.active) continue;
                if (FinalEvent.finished(p) && w.getRandom().nextInt(6) != 0) continue;   // tras el final, el mundo se calma
                Integer next = NEXT_EVENT.get(id);
                if (next != null && now < next) continue;
                if (triggerEvent(w, p, false)) {
                    int cd = 500 + w.getRandom().nextInt(800);   // 25 - 65 s
                    if (HorrorUtil.isModDim(w)) cd /= 2;          // el doble de seguido en la Pradera y la Ceniza
                    NEXT_EVENT.put(id, now + cd);
                }
            }
        }
    }

    // ------------------------------------------------------------------ eventos
    public static boolean triggerEvent(ServerWorld w, ServerPlayerEntity p, boolean force) {
        BlockPos pos = p.getBlockPos();
        Random r = w.getRandom();
        boolean sky = w.isSkyVisible(pos);
        List<BlockPos> doors = sky ? List.of() : scan(w, pos, 8, s -> s.isIn(BlockTags.WOODEN_DOORS) && !s.isOf(Blocks.CRIMSON_DOOR));
        List<BlockPos> beds = sky ? List.of() : scan(w, pos, 8, s -> s.isIn(BlockTags.BEDS));
        boolean house = !doors.isEmpty() || !beds.isEmpty();
        boolean cave = !sky && !house;
        long t = w.getTimeOfDay() % 24000L;
        boolean night = t >= 13000 && t <= 23000;

        if (!force) {
            int den = p.isSleeping() ? 2 : house ? (night ? 2 : 4) : sky ? (night ? 3 : 10) : 3;
            if (HorrorUtil.isModDim(w)) den = Math.max(1, den / 2);   // monstruos mas activos
            if (r.nextInt(den) != 0) return false;
            if (cave && w.getLightLevel(pos) > 7) return false;
        }

        List<BlockPos> glass = house
                ? scan(w, pos, 8, s -> s.isOf(Blocks.GLASS_PANE) || s.isOf(Blocks.GLASS))
                : List.of();

        // mientras duermes, a veces: algo sube lentamente en la ventana y te obliga a mirarla
        if (p.isSleeping() && !glass.isEmpty() && r.nextInt(3) == 0 && window(w, p, glass, true)) return true;

        List<Supplier<Boolean>> evs = new ArrayList<>();
        if (house) {
            if (!doors.isEmpty()) {
                evs.add(() -> knock(w, p, doors.get(r.nextInt(doors.size()))));
                evs.add(() -> knock(w, p, doors.get(r.nextInt(doors.size()))));
                evs.add(() -> doorOpens(w, doors));
            }
            if (!glass.isEmpty()) {
                evs.add(() -> window(w, p, glass, false));
                evs.add(() -> window(w, p, glass, false));
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
        evs.add(() -> bell(w, p));
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

    private static boolean setDoorOpen(ServerWorld w, BlockPos low) {
        BlockState ls = w.getBlockState(low);
        if (!(ls.getBlock() instanceof DoorBlock) || ls.get(DoorBlock.OPEN)) return false;
        w.setBlockState(low, ls.with(DoorBlock.OPEN, true), Block.NOTIFY_ALL);
        BlockPos up = low.up();
        BlockState us = w.getBlockState(up);
        if (us.getBlock() instanceof DoorBlock) {
            w.setBlockState(up, us.with(DoorBlock.OPEN, true), Block.NOTIFY_ALL);
        }
        w.playSound(null, low, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1.4F, 0.6F);
        return true;
    }

    private static void knockSound(ServerWorld w, BlockPos door) {
        w.playSound(null, door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5,
                ModSounds.KNOCK, SoundCategory.HOSTILE, 3.0F, 1.0F);
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

    /**
     * Algo sube LENTAMENTE desde abajo de la ventana (sale del suelo) y golpea el vidrio 3 veces antes de irse.
     * forced (solo mientras duermes): te despierta, te obliga a mirar la ventana y susurra.
     */
    private static boolean window(ServerWorld w, ServerPlayerEntity p, List<BlockPos> glass, boolean forced) {
        Random r = w.getRandom();
        for (int i = 0; i < 6; i++) {
            BlockPos g = glass.get(r.nextInt(glass.size()));
            BlockPos out = beyond(w, p, g, 1.0);
            MobEntity e = spawnSpy(w, p, out, HorrorUtil.TAG_VENTANA, false, WINDOW_PREF, false);
            if (e == null) continue;
            WINDOW_Y.put(e.getUuid(), (double) out.getY());
            WINDOW_G.put(e.getUuid(), g.toImmutable());
            e.setPosition(e.getX(), out.getY() - e.getHeight(), e.getZ());   // empieza bajo tierra
            if (forced) {
                forceLook(w, p, Vec3d.ofCenter(g));
                int now = w.getServer().getTicks();
                TASKS.add(new Task(now + 60, () -> {
                    if (e.isRemoved() || p.isRemoved()) return;
                    w.playSound(null, e.getX(), e.getY() + 1.0, e.getZ(),
                            ModSounds.AMBIENT[Jumpscare.mobOf(e)], SoundCategory.HOSTILE, 2.0F, 0.8F);
                    String line = Lore.WHISPERS[w.getRandom().nextInt(Lore.WHISPERS.length)];
                    p.sendMessage(Text.literal(line).formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
                }));
            }
            return true;
        }
        return false;
    }

    /** Gira la camara del jugador hacia 'target' durante ~4.5 s (lo despierta si duerme). */
    private static void forceLook(ServerWorld w, ServerPlayerEntity p, Vec3d target) {
        if (p.isSleeping()) p.wakeUp(false, true);
        int now = w.getServer().getTicks();
        for (int i = 0; i < 45; i++) {
            TASKS.add(new Task(now + i * 2, () -> {
                if (p.isRemoved() || !p.isAlive()) return;
                Vec3d eye = p.getEyePos();
                double dx = target.x - eye.x;
                double dy = target.y - eye.y;
                double dz = target.z - eye.z;
                float yaw = (float) (MathHelper.atan2(dz, dx) * 57.2957795) - 90.0F;
                float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 57.2957795);
                p.networkHandler.requestTeleport(p.getX(), p.getY(), p.getZ(), yaw, pitch);
            }));
        }
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
        w.playSound(null, p.getX(), p.getY(), p.getZ(),
                ModSounds.AMBIENT[w.getRandom().nextInt(ModSounds.AMBIENT.length)],
                SoundCategory.AMBIENT, 1.3F, 1.0F);
        return true;
    }

    /** Siete campanadas a lo lejos, de noche, sin que haya ninguna iglesia. */
    private static boolean bell(ServerWorld w, ServerPlayerEntity p) {
        long t = w.getTimeOfDay() % 24000L;
        if (!(t >= 13000 && t <= 23000)) return false;
        double ang = w.getRandom().nextDouble() * Math.PI * 2;
        final double x = p.getX() + Math.cos(ang) * 45;
        final double z = p.getZ() + Math.sin(ang) * 45;
        int now = w.getServer().getTicks();
        for (int i = 0; i < 7; i++) {
            TASKS.add(new Task(now + i * 50, () ->
                    w.playSound(null, x, p.getY(), z, SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 8.0F, 0.6F)));
        }
        TASKS.add(new Task(now + 7 * 50 + 20, () -> {
            if (!p.isRemoved()) {
                p.sendMessage(Text.literal("Siete campanadas… y no hay ninguna iglesia cerca.")
                        .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
            }
        }));
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

    /** Para el final: asegura que los seis esten presentes cerca de ti y todos te persigan. */
    public static void finalWave(ServerWorld w, ServerPlayerEntity p) {
        List<EntityType<? extends MobEntity>> all = new ArrayList<>(AGGRESSIVE);
        all.add(ModEntities.SOMBRA_HUECA);
        Random r = w.getRandom();
        for (EntityType<? extends MobEntity> type : all) {
            if (HorrorUtil.exists(w, type)) continue;
            BlockPos spot = null;
            for (int i = 0; i < 80 && spot == null; i++) {
                int dx = r.nextInt(25) - 12;
                int dz = r.nextInt(25) - 12;
                if (dx * dx + dz * dz < 4) continue;
                spot = groundNear(w, p.getBlockPos().add(dx, 0, dz), 5);
            }
            if (spot == null) spot = p.getBlockPos().up();
            type.spawn(w, spot, SpawnReason.EVENT);
        }
        for (MobEntity m : w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(128.0),
                e -> e instanceof AcosadorEntity && !HorrorUtil.isSpy(e))) {
            m.setTarget(p);
        }
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

            if (spy.getCommandTags().contains(HorrorUtil.TAG_VENTANA)) {
                handleWindowVisitor(w, p, spy);
                continue;
            }

            if (spy.getCommandTags().contains(HorrorUtil.TAG_BAJO_CAMA)) {
                handleBedVisitor(w, p, spy);
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

    /** Visitante de ventana: sube despacio (5 s), se queda, golpea el vidrio 3 veces y se va. No huye si te acercas. */
    private static void handleWindowVisitor(ServerWorld w, ServerPlayerEntity p, MobEntity v) {
        UUID id = v.getUuid();
        Double ty = WINDOW_Y.get(id);
        if (ty != null && v.getY() < ty) {
            v.setPosition(v.getX(), Math.min(ty, v.getY() + 0.04), v.getZ());
        }
        int a = v.age;
        if ((a >= 130 && a < 132) || (a >= 146 && a < 148) || (a >= 162 && a < 164)) {
            w.playSound(null, v.getX(), v.getY() + 1.2, v.getZ(),
                    ModSounds.GLASS_KNOCK, SoundCategory.HOSTILE, 3.0F, 1.0F);
        }
        if (a >= 200) {
            BlockPos g = WINDOW_G.get(id);
            // a veces (1 de 4) el agresivo rompe el cristal y entra a buscarte
            if (g != null && HorrorUtil.isAggressive(v) && w.getRandom().nextInt(4) == 0 && breakIn(w, p, v, g)) {
                return;
            }
            vanish(w, v, p, false);
        }
    }

    private static boolean breakIn(ServerWorld w, ServerPlayerEntity p, MobEntity v, BlockPos g) {
        double dx = g.getX() + 0.5 - v.getX();
        double dz = g.getZ() + 0.5 - v.getZ();
        boolean alongX = Math.abs(dx) > Math.abs(dz);
        BlockPos inside = BlockPos.ofFloored(
                g.getX() + 0.5 + (alongX ? Math.signum(dx) : 0.0), g.getY(), g.getZ() + 0.5 + (alongX ? 0.0 : Math.signum(dz)));
        BlockPos ground = groundNear(w, inside, 3);
        if (ground == null) return false;
        w.breakBlock(g, false);
        w.playSound(null, g, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 2.0F, 0.8F);
        UUID id = v.getUuid();
        WINDOW_Y.remove(id);
        WINDOW_G.remove(id);
        v.removeCommandTag(HorrorUtil.TAG_VENTANA);
        v.setPosition(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        v.setAiDisabled(false);
        v.setNoGravity(false);
        v.setTarget(p);
        return true;
    }

    // ------------------------------------------------------------------ monstruo bajo la cama (mientras duermes)
    /** Rara vez, al dormir: el monstruo sale LENTAMENTE de debajo de la cama. Si no te levantas a tiempo, te asusta y te mata. */
    private static boolean underBedEvent(ServerWorld w, ServerPlayerEntity p) {
        Optional<BlockPos> sleeping = p.getSleepingPosition();
        if (sleeping.isEmpty()) return false;
        BlockPos head = sleeping.get();
        BlockState bs = w.getBlockState(head);
        if (!(bs.getBlock() instanceof BedBlock)) return false;
        BlockPos other = head.offset(BedBlock.getOppositePartDirection(bs));
        MobEntity e = spawnSpy(w, p, other, HorrorUtil.TAG_BAJO_CAMA, false, AGGRESSIVE, true);
        if (e == null) return false;
        UUID id = e.getUuid();
        BED_OWNER.put(id, p.getUuid());
        WINDOW_Y.put(id, (double) other.getY());
        e.setPosition(e.getX(), other.getY() - e.getHeight(), e.getZ());   // empieza bajo el suelo
        int now = w.getServer().getTicks();
        for (int i = 0; i < 3; i++) {
            TASKS.add(new Task(now + i * 25, () -> {
                if (!p.isRemoved()) {
                    w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                            SoundCategory.HOSTILE, 1.8F, 0.9F);
                }
            }));
        }
        w.playSound(null, e.getX(), e.getY() + 0.5, e.getZ(), ModSounds.AMBIENT[Jumpscare.mobOf(e)],
                SoundCategory.HOSTILE, 1.8F, 0.8F);
        return true;
    }

    private static void handleBedVisitor(ServerWorld w, ServerPlayerEntity near, MobEntity v) {
        UUID id = v.getUuid();
        Double ty = WINDOW_Y.get(id);
        if (ty != null && v.getY() < ty) {
            v.setPosition(v.getX(), Math.min(ty, v.getY() + 0.09), v.getZ());
        }
        UUID oid = BED_OWNER.get(id);
        ServerPlayerEntity owner = oid == null ? null : w.getServer().getPlayerManager().getPlayer(oid);
        if (owner == null || !owner.isAlive()) {
            vanish(w, v, near, false);
            return;
        }
        if (BED_DONE.contains(id)) {
            if (v.age >= 130) vanish(w, v, owner, false);
            return;
        }
        if (!owner.isSleeping()) {   // te levantaste a tiempo: se esconde otra vez
            vanish(w, v, owner, false);
            return;
        }
        if (v.age >= 74) {           // ya se alzo completamente y sigues en la cama
            BED_DONE.add(id);
            Jumpscare.send(owner, 2, Jumpscare.mobOf(v));
            int now = w.getServer().getTicks();
            TASKS.add(new Task(now + 40, () -> {
                if (!owner.isRemoved() && owner.isAlive()) {
                    var server = owner.getServer();
                    server.getCommandManager().executeWithPrefix(
                            server.getCommandSource().withSilent(), "kill " + owner.getUuidAsString());
                }
            }));
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
            if (HorrorUtil.isAggressive(v) && v.age >= 300 && v.age < 302 && w.getRandom().nextInt(3) == 0) {
                setDoorOpen(w, low);   // abre la puerta el mismo; en la siguiente comprobacion entra a atacar
                return;
            }
            if (v.age > 700) vanish(w, v, p, false);   // se cansa de esperar
        }
    }

    private static void giveLetter(ServerWorld w, ServerPlayerEntity p, MobEntity v) {
        p.giveItemStack(new ItemStack(ModItems.NOTA_ROTA));
        if (Jumpscare.mobOf(v) == 0 && !p.getCommandTags().contains("rh_frag_0")) {
            p.giveItemStack(new ItemStack(ModItems.FRAGMENTOS[0]));   // la Sombra deja su fragmento junto a la carta
        }
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
        WINDOW_Y.remove(id);
        WINDOW_G.remove(id);
        BED_OWNER.remove(id);
        BED_DONE.remove(id);
        boolean aggressive = HorrorUtil.isAggressive(spy);
        int mob = Jumpscare.mobOf(spy);
        HorrorUtil.poof(spy);
        if (maybeScare && aggressive && w.getRandom().nextInt(5) == 0) {
            Jumpscare.send(p, 1, mob);
        }
    }

    // ------------------------------------------------------------------ esconderse bajo la cama
    private static boolean isHiding(ServerWorld w, ServerPlayerEntity p) {
        return HIDDEN.containsKey(p.getUuid());
    }

    private static void applyHideEffects(ServerPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 60, 0, false, false));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 6, false, false));
    }

    /** La camara baja al suelo reduciendo el tamano del jugador (atributo de escala): ve desde debajo de la cama. */
    private static void setHideScale(ServerPlayerEntity p, boolean on) {
        EntityAttributeInstance inst = p.getAttributeInstance(EntityAttributes.SCALE);
        if (inst == null) return;
        Identifier mid = RostrosHuecos.id("hide_scale");
        inst.removeModifier(mid);
        if (on) {
            inst.addTemporaryModifier(new EntityAttributeModifier(mid, -0.9, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void hide(ServerWorld w, ServerPlayerEntity p, BlockPos bed) {
        UUID id = p.getUuid();
        HIDDEN.put(id, bed);
        HIDE_TICKS.put(id, 0);
        HIDE_RETURN.put(id, p.getPos());
        applyHideEffects(p);
        setHideScale(p, true);
        p.networkHandler.requestTeleport(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, p.getYaw(), p.getPitch());
        ServerPlayNetworking.send(p, new HidePayload(true));
        p.sendMessage(Text.literal("Estás bajo la cama. Aguanta 10 segundos. Clic derecho para salir."), true);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 1.0F, 0.6F);
    }

    private static void unhide(ServerPlayerEntity p) {
        unhide(p, true);
    }

    private static void unhide(ServerPlayerEntity p, boolean notify) {
        UUID id = p.getUuid();
        if (HIDDEN.remove(id) == null) return;
        HIDE_TICKS.remove(id);
        Vec3d back = HIDE_RETURN.remove(id);
        setHideScale(p, false);
        if (back != null && p.isAlive()) {
            if (notify) {
                p.networkHandler.requestTeleport(back.x, back.y, back.z, p.getYaw(), p.getPitch());
            } else {
                p.requestTeleport(back.x, back.y, back.z);
            }
        }
        p.removeStatusEffect(StatusEffects.INVISIBILITY);
        p.removeStatusEffect(StatusEffects.SLOWNESS);
        if (notify) ServerPlayNetworking.send(p, new HidePayload(false));
    }

    private static void handleHiding(ServerWorld w, ServerPlayerEntity p) {
        UUID id = p.getUuid();
        BlockPos bed = HIDDEN.get(id);
        if (bed == null) {
            HIDE_TICKS.remove(id);
            return;
        }
        int h = HIDE_TICKS.merge(id, 10, Integer::sum);
        if (!p.isAlive() || p.squaredDistanceTo(Vec3d.ofCenter(bed)) > 25.0 || h > 1800) {
            unhide(p);
            return;
        }
        applyHideEffects(p);
        if (h % 40 == 0 || h == 10) {
            w.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 1.0F, 1.0F);
        }
        // mientras te escondes, los monstruos pierden tu rastro
        for (MobEntity m : w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(32.0),
                e -> e.getTarget() == p && e instanceof AcosadorEntity && !HorrorUtil.isSpy(e))) {
            m.setTarget(null);
        }
        // a los 3 s escondido: un monstruo agresivo entra a buscarte
        if (h == 60 && !FinalEvent.active) {
            Random r = w.getRandom();
            for (int i = 0; i < 30; i++) {
                int dx = r.nextInt(11) - 5;
                int dz = r.nextInt(11) - 5;
                if (dx * dx + dz * dz < 9) continue;
                BlockPos c = bed.add(dx, 0, dz);
                if (!isFree(w, c) || !isFree(w, c.up()) || !solidBelow(w, c) || w.isSkyVisible(c)) continue;
                if (spawnSpy(w, p, c, HorrorUtil.TAG_ESPIA_CAMA, false, AGGRESSIVE, true) != null) break;
            }
        }
        // a los 10 s escondido: TODOS se rinden y desaparecen
        if (h == 200 && !FinalEvent.active) {
            for (MobEntity m : w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(64.0),
                    e -> e instanceof AcosadorEntity)) {
                vanish(w, m, p, false);
            }
            p.sendMessage(Text.literal("Los pasos se alejan... se han ido."), true);
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
