package com.liminalspace;

import com.liminalspace.entity.WhisperEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LiminalSpaceMod implements ModInitializer {
    public static final String MOD_ID = "liminalspace";
    public static final RegistryKey<World> LIMINAL_WORLD = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(MOD_ID, "liminal"));
    public static final RegistryKey<EntityType<?>> WHISPER_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(MOD_ID, "whisper"));
    public static final EntityType<WhisperEntity> WHISPER = Registry.register(
            Registries.ENTITY_TYPE,
            WHISPER_KEY,
            EntityType.Builder.create(WhisperEntity::new, SpawnGroup.MONSTER)
                    .dimensions(1.5f, 5.8f)
                    .maxTrackingRange(64)
                    .trackingTickInterval(1)
                    .build(WHISPER_KEY)
    );

    // La dimension usa un dimension_type propio con min_y = 0, asi que el suelo plano
    // (bedrock, 2 de tierra, 1 de hierba) ocupa y = 0..3 y la superficie queda en y = 4.
    private static final int GROUND_Y = 4;
    private static final int PILLAR_TOP = 78;
    private static final double STAIR_END_ANGLE = -Math.PI / 2;
    private static final BlockPos BUILT_MARKER = new BlockPos(0, 40, 0);

    private static final Map<UUID, ReturnPoint> RETURN_POINTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> WHISPER_PENDING = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        // Sin atributos por defecto la entidad crashea al aparecer.
        FabricDefaultAttributeRegistry.register(WHISPER, MobEntity.createMobAttributes());
        CommandRegistrationCallback.EVENT.register(LiminalSpaceMod::registerCommands);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(LiminalSpaceMod::handleWhisperChat);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                         CommandRegistryAccess registryAccess,
                                         CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("liminal")
                .requires(source -> source.hasPermissionLevel(0))
                .executes(context -> enterLiminal(context.getSource())));

        dispatcher.register(CommandManager.literal("liminalbuild")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> buildForCommand(context.getSource())));
    }

    private static int enterLiminal(ServerCommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) return 0;
        MinecraftServer server = source.getServer();
        ServerWorld world = server.getWorld(LIMINAL_WORLD);
        if (world == null) {
            source.sendError(Text.literal("The Liminal Space dimension could not be loaded."));
            return 0;
        }

        buildWorld(world, false);
        if (!isInLiminal(player)) {
            RETURN_POINTS.put(player.getUuid(), new ReturnPoint(player.getWorld().getRegistryKey(), player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()));
        }
        double[] spawn = StairPath.spawn(StairPath.BASE_Y, PILLAR_TOP + 2, PILLAR_TOP, STAIR_END_ANGLE);
        player.teleport(world, spawn[0], GROUND_Y, spawn[1], Set.of(), (float) spawn[2], 0.0f, true);
        source.sendFeedback(() -> Text.literal("§7You enter the Liminal Space..."), false);
        return 1;
    }

    private static int buildForCommand(ServerCommandSource source) {
        ServerWorld world = source.getServer().getWorld(LIMINAL_WORLD);
        if (world == null) {
            source.sendError(Text.literal("The Liminal Space dimension could not be loaded."));
            return 0;
        }
        buildWorld(world, true);
        source.sendFeedback(() -> Text.literal("§aLiminal Space generated."), true);
        return 1;
    }

    /**
     * Construye la dimension. Sin force solo se construye una vez por mundo: se comprueba un bloque
     * del pilar en lugar de una variable estatica, que se perdia al reiniciar y duplicaba la criatura.
     */
    private static void buildWorld(ServerWorld world, boolean force) {
        if (!force && !world.getBlockState(BUILT_MARKER).isAir()) return;

        // Colinas bajas y dispersas para romper el horizonte.
        makeHill(world, -48, -35, 2, 9, 6);
        makeHill(world, 42, -22, 2, 7, 5);
        makeHill(world, -35, 36, 1, 8, 5);
        makeHill(world, 51, 35, 3, 10, 7);
        makeHill(world, 78, 2, 2, 7, 6);

        int baseY = GROUND_Y;
        int pillarTop = PILLAR_TOP;

        // Pilar de roca gigante, irregular y deliberadamente poco natural.
        for (int y = baseY; y <= pillarTop; y++) {
            int radius = StairPath.pillarRadius(y, pillarTop);
            double wobble = Math.sin(y * 0.41) * 1.7 + Math.cos(y * 0.19) * 1.3;
            double zShift = Math.sin(y * 0.27) * 1.2;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double dx = x - wobble;
                    double dz = z + zShift;
                    if (dx * dx + dz * dz <= radius * radius) {
                        Block block = ((x + z + y) % 7 == 0) ? Blocks.COBBLESTONE : Blocks.STONE;
                        set(world, x, y, z, block);
                    }
                }
            }
        }

        // Tapa de tierra y hierba, lo bastante ancha para la casa.
        for (int x = -9; x <= 9; x++) {
            for (int z = -8; z <= 8; z++) {
                if (x * x / 81.0 + z * z / 64.0 <= 1.0) {
                    set(world, x, pillarTop + 1, z, Blocks.DIRT);
                    set(world, x, pillarTop + 2, z, Blocks.GRASS_BLOCK);
                }
            }
        }

        buildStaircase(world);

        // La casa se apoya sobre la hierba: el suelo sustituye a la capa de hierba y queda a ras.
        buildHouse(world, -4, pillarTop + 2, -3);

        // Detalles inquietantes junto a la casa.
        set(world, 8, pillarTop + 3, 3, Blocks.COBBLESTONE);
        set(world, 8, pillarTop + 4, 3, Blocks.COBBLESTONE);
        set(world, 8, pillarTop + 5, 3, Blocks.COBBLESTONE);

        // La criatura espera dentro de la casa (solo si no hay ya una).
        Box houseArea = new Box(-20, pillarTop - 5, -20, 20, pillarTop + 25, 20);
        if (world.getEntitiesByType(WHISPER, houseArea, e -> !e.isRemoved()).isEmpty()) {
            WhisperEntity whisper = new WhisperEntity(WHISPER, world);
            whisper.refreshPositionAndAngles(-0.5, pillarTop + 3, 2.0, 180.0f, 0.0f);
            world.spawnEntity(whisper);
        }
    }

    /** Escalera helicoidal alrededor del pilar, con un puente hasta la tapa. */
    private static void buildStaircase(ServerWorld world) {
        int lastY = PILLAR_TOP + 2;
        List<StairPath.Cell> cells = StairPath.compute(StairPath.BASE_Y, lastY, PILLAR_TOP, STAIR_END_ANGLE);

        for (int i = 0; i < cells.size(); i++) {
            StairPath.Cell c = cells.get(i);
            if (c.kind() == 1) {
                set(world, c.x(), c.y(), c.z(), Blocks.STONE_BRICKS);
                continue;
            }
            boolean last = i == cells.size() - 1;
            int dx = last ? 0 : c.dx();
            int dz = last ? 1 : c.dz();
            Direction facing = dx > 0 ? Direction.EAST : dx < 0 ? Direction.WEST : dz > 0 ? Direction.SOUTH : Direction.NORTH;
            BlockState stair = Blocks.STONE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);

            placeStep(world, c.x(), c.y(), c.z(), stair);

            // Segundo ancho de escalon, hacia el pilar.
            double len = Math.hypot(c.x(), c.z());
            if (len > 1.0) {
                int ix = (int) Math.round(c.x() - c.x() / len);
                int iz = (int) Math.round(c.z() - c.z() / len);
                if (ix != c.x() || iz != c.z()) placeStep(world, ix, c.y(), iz, stair);
            }
        }

        // Puente desde el ultimo escalon hasta la tapa, frente a la puerta de la casa.
        StairPath.Cell end = cells.get(cells.size() - 1);
        int cx = end.x();
        int cz = end.z();
        while (cx != 0) {
            cx += cx > 0 ? -1 : 1;
            bridge(world, cx, end.y(), cz);
        }
        while (cz < -7) {
            cz++;
            bridge(world, cx, end.y(), cz);
        }
    }

    private static void placeStep(ServerWorld world, int x, int y, int z, BlockState stair) {
        setState(world, x, y, z, stair);
        if (y - 1 > GROUND_Y - 1) setState(world, x, y - 1, z, Blocks.STONE_BRICKS.getDefaultState());
    }

    private static void bridge(ServerWorld world, int x, int y, int z) {
        set(world, x, y, z, Blocks.STONE_BRICKS);
        set(world, x + 1, y, z, Blocks.STONE_BRICKS);
    }

    private static void buildHouse(ServerWorld world, int x0, int y0, int z0) {
        int w = 11, d = 9, h = 7;
        int atticFloor = y0 + h + 1;

        // Suelo.
        for (int x = x0; x < x0 + w; x++)
            for (int z = z0; z < z0 + d; z++)
                set(world, x, y0, z, Blocks.OAK_PLANKS);

        // Paredes.
        for (int y = y0 + 1; y <= y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                set(world, x, y, z0, Blocks.DARK_OAK_PLANKS);
                set(world, x, y, z0 + d - 1, Blocks.DARK_OAK_PLANKS);
            }
            for (int z = z0; z < z0 + d; z++) {
                set(world, x0, y, z, Blocks.DARK_OAK_PLANKS);
                set(world, x0 + w - 1, y, z, Blocks.DARK_OAK_PLANKS);
            }
        }

        // Techo de la planta baja = suelo del atico.
        for (int x = x0; x < x0 + w; x++)
            for (int z = z0; z < z0 + d; z++)
                set(world, x, atticFloor, z, Blocks.DARK_OAK_PLANKS);

        // Atico: paredes bajas y tejado.
        for (int y = atticFloor + 1; y <= atticFloor + 2; y++) {
            for (int x = x0; x < x0 + w; x++) {
                set(world, x, y, z0, Blocks.DARK_OAK_PLANKS);
                set(world, x, y, z0 + d - 1, Blocks.DARK_OAK_PLANKS);
            }
            for (int z = z0; z < z0 + d; z++) {
                set(world, x0, y, z, Blocks.DARK_OAK_PLANKS);
                set(world, x0 + w - 1, y, z, Blocks.DARK_OAK_PLANKS);
            }
        }
        for (int x = x0; x < x0 + w; x++)
            for (int z = z0; z < z0 + d; z++)
                set(world, x, atticFloor + 3, z, Blocks.DARK_OAK_PLANKS);

        // Puerta de roble (las dos mitades) en el hueco de la pared norte.
        int doorX = x0 + w / 2;
        setState(world, doorX, y0 + 1, z0, Blocks.OAK_DOOR.getDefaultState());
        setState(world, doorX, y0 + 2, z0, Blocks.OAK_DOOR.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

        // Ventanas.
        for (int x : new int[]{x0 + 2, x0 + 3, x0 + 7, x0 + 8}) {
            set(world, x, y0 + 2, z0, Blocks.GLASS_PANE);
            set(world, x, y0 + 2, z0 + d - 1, Blocks.GLASS_PANE);
        }
        for (int z : new int[]{z0 + 2, z0 + 6}) {
            set(world, x0, y0 + 2, z, Blocks.GLASS_PANE);
            set(world, x0 + w - 1, y0 + 2, z, Blocks.GLASS_PANE);
        }

        // Divisor interior: salon + dormitorio + pasillo extrano.
        for (int z = z0 + 1; z < z0 + d - 1; z++) {
            if (z != z0 + 4) {
                set(world, x0 + 6, y0 + 1, z, Blocks.OAK_PLANKS);
                set(world, x0 + 6, y0 + 2, z, Blocks.OAK_PLANKS);
            }
        }

        // Salon: mesa, sillas y chimenea.
        set(world, x0 + 2, y0 + 1, z0 + 3, Blocks.OAK_PLANKS);
        set(world, x0 + 3, y0 + 1, z0 + 3, Blocks.OAK_PLANKS);
        set(world, x0 + 2, y0 + 2, z0 + 3, Blocks.OAK_FENCE);
        set(world, x0 + 3, y0 + 2, z0 + 3, Blocks.OAK_FENCE);
        set(world, x0 + 1, y0 + 1, z0 + 3, Blocks.OAK_STAIRS);
        set(world, x0 + 4, y0 + 1, z0 + 3, Blocks.OAK_STAIRS);
        set(world, x0 + 5, y0 + 1, z0 + 1, Blocks.COBBLESTONE);
        set(world, x0 + 5, y0 + 2, z0 + 1, Blocks.COBBLESTONE);
        set(world, x0 + 5, y0 + 3, z0 + 1, Blocks.SOUL_LANTERN);

        // Dormitorio: cama y mesilla.
        set(world, x0 + 8, y0 + 1, z0 + 2, Blocks.RED_WOOL);
        set(world, x0 + 9, y0 + 1, z0 + 2, Blocks.RED_WOOL);
        set(world, x0 + 8, y0 + 1, z0 + 4, Blocks.OAK_PLANKS);
        set(world, x0 + 9, y0 + 1, z0 + 4, Blocks.CHEST);

        // Rincon de cocina.
        for (int x = x0 + 7; x <= x0 + 9; x++) set(world, x, y0 + 1, z0 + 6, Blocks.SMOOTH_STONE);
        set(world, x0 + 8, y0 + 2, z0 + 6, Blocks.FURNACE);
        set(world, x0 + 9, y0 + 2, z0 + 6, Blocks.CAULDRON);

        // Luces escasas, colgadas del techo (una linterna sin apoyo se rompe).
        BlockState hanging = Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true);
        setState(world, x0 + 3, y0 + h, z0 + 4, hanging);
        setState(world, x0 + 8, y0 + h, z0 + 5, hanging);

        // Acceso al atico: hueco en el suelo del atico y escalera de mano pegada a la pared sur.
        int ladderX = x0 + 1;
        int ladderZ = z0 + 7;
        set(world, ladderX, atticFloor, ladderZ, Blocks.AIR);
        for (int y = y0 + 1; y <= atticFloor + 1; y++) {
            set(world, ladderX, y, ladderZ, Blocks.LADDER);
        }

        // Atico: un cofre y telarañas.
        set(world, x0 + 8, atticFloor + 1, z0 + 5, Blocks.CHEST);
        set(world, x0 + 5, atticFloor + 1, z0 + 2, Blocks.COBWEB);
        set(world, x0 + 5, atticFloor + 1, z0 + 6, Blocks.COBWEB);
    }

    private static void makeHill(ServerWorld world, int cx, int cz, int height, int radius, int seed) {
        for (int y = GROUND_Y; y < GROUND_Y + height; y++) {
            double frac = (y - (GROUND_Y - 1)) / (double) height;
            int r = Math.max(1, (int) Math.ceil(radius * (1.0 - frac)));
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    double dx = x - cx;
                    double dz = z - cz;
                    if (dx * dx + dz * dz <= r * r + ((x * 31L + z * 17L + seed) & 2)) {
                        set(world, x, y, z, y == GROUND_Y + height - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
                    }
                }
            }
        }
    }

    public static boolean isInLiminal(ServerPlayerEntity player) {
        return player.getServerWorld().getRegistryKey().equals(LIMINAL_WORLD);
    }

    public static boolean canAskWhisper(ServerPlayerEntity player) {
        Long time = WHISPER_PENDING.get(player.getUuid());
        return time == null || player.getServerWorld().getTime() - time > 600;
    }

    public static void markWhisperPending(ServerPlayerEntity player) {
        WHISPER_PENDING.put(player.getUuid(), player.getServerWorld().getTime());
    }

    private static boolean handleWhisperChat(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) {
        Long pending = WHISPER_PENDING.get(sender.getUuid());
        if (pending == null || !isInLiminal(sender)) return true;

        String answer = message.getContent().getString().trim().toLowerCase(Locale.ROOT);
        boolean yes = answer.equals("si") || answer.equals("sí") || answer.equals("yes") || answer.equals("s");
        boolean no = answer.equals("no") || answer.equals("n");
        if (!yes && !no) {
            sender.sendMessage(Text.literal("§8La cosa susurra: §7¿sí... o no?"), false);
            return false;
        }

        WhisperEntity nearest = null;
        double best = Double.MAX_VALUE;
        List<WhisperEntity> candidates = sender.getServerWorld().getEntitiesByType(
                WHISPER, sender.getBoundingBox().expand(8.0), candidate -> !candidate.isRemoved());
        for (WhisperEntity candidate : candidates) {
            double dist = candidate.squaredDistanceTo(sender);
            if (dist < best) { best = dist; nearest = candidate; }
        }
        if (nearest != null) nearest.answer(sender, yes);
        WHISPER_PENDING.remove(sender.getUuid());
        return false;
    }

    public static void returnPlayerToNormalWorld(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ReturnPoint point = RETURN_POINTS.remove(player.getUuid());
        ServerWorld overworld = server.getOverworld();
        if (point != null) {
            ServerWorld destination = server.getWorld(point.worldKey());
            if (destination == null) destination = overworld;
            player.teleport(destination, point.x(), point.y(), point.z(), Set.of(), point.yaw(), point.pitch(), true);
        } else {
            BlockPos spawn = overworld.getSpawnPos();
            player.teleport(overworld, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, Set.of(), 0.0f, 0.0f, true);
        }
    }

    private record ReturnPoint(RegistryKey<World> worldKey, double x, double y, double z, float yaw, float pitch) {}

    private static void set(ServerWorld world, int x, int y, int z, Block block) {
        world.setBlockState(new BlockPos(x, y, z), block.getDefaultState());
    }

    private static void setState(ServerWorld world, int x, int y, int z, BlockState state) {
        world.setBlockState(new BlockPos(x, y, z), state);
    }
}
