package com.rostroshuecos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BedBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Ruinas de San Mudo. Aparecen cuando exploras (a 55-80 bloques, en terreno ya cargado) y se anotan en
 * rostroshuecos_estructuras.txt dentro del mundo para no repetirse a menos de 300 bloques.
 * Tipos: 0 Pozo Sellado, 1 Casa de San Mudo, 2 Arbol del Vecino. Todas guardan Notas Rotas.
 */
public final class StructureManager {
    private StructureManager() {}

    private record Placed(int x, int z, int type) {}
    private static final List<Placed> PLACED = new ArrayList<>();
    private static boolean loaded = false;

    private static final String[] NAMES = {"Pozo Sellado", "Casa de San Mudo", "Árbol del Vecino"};

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PLACED.clear();
            load(server);
            loaded = true;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PLACED.clear();
            loaded = false;
        });
    }

    // ------------------------------------------------------------------ persistencia
    private static Path file(MinecraftServer s) {
        return s.getSavePath(WorldSavePath.ROOT).resolve("rostroshuecos_estructuras.txt");
    }

    private static void load(MinecraftServer s) {
        try {
            Path f = file(s);
            if (!Files.exists(f)) return;
            for (String line : Files.readAllLines(f)) {
                String[] p = line.trim().split(" ");
                if (p.length == 3) PLACED.add(new Placed(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
            }
        } catch (IOException | NumberFormatException ignored) {
        }
    }

    private static void save(MinecraftServer s) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Placed p : PLACED) sb.append(p.x()).append(' ').append(p.z()).append(' ').append(p.type()).append('\n');
            Files.writeString(file(s), sb.toString());
        } catch (IOException ignored) {
        }
    }

    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Placed p : PLACED) out.add(NAMES[p.type()] + ": x=" + p.x() + " z=" + p.z());
        return out;
    }

    // ------------------------------------------------------------------ aparicion
    /** Llamado cada 10 s por jugador en la superficie. */
    public static void tick(ServerWorld w, ServerPlayerEntity p) {
        if (!loaded || w.getRegistryKey() != World.OVERWORLD || !w.isSkyVisible(p.getBlockPos())) return;
        Random r = w.getRandom();
        if (r.nextInt(3) != 0) return;
        for (Placed s : PLACED) {
            double dx = s.x() - p.getX();
            double dz = s.z() - p.getZ();
            if (dx * dx + dz * dz < 300.0 * 300.0) return;
        }
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = 55 + r.nextInt(25);
        int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
        int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
        place(w, x, z, PLACED.size() % 3, false);
    }

    /** Para el comando de prueba: delante del jugador, sin exigir terreno plano. */
    public static boolean forcePlace(ServerWorld w, ServerPlayerEntity p, int type) {
        double yaw = Math.toRadians(p.getYaw());
        int x = (int) Math.floor(p.getX() - Math.sin(yaw) * 10);
        int z = (int) Math.floor(p.getZ() + Math.cos(yaw) * 10);
        return place(w, x, z, type, true);
    }

    private static boolean place(ServerWorld w, int x, int z, int type, boolean ignoreFlat) {
        for (int ox : new int[] {-9, 0, 9}) {
            for (int oz : new int[] {-9, 0, 9}) {
                if (!w.isChunkLoaded((x + ox) >> 4, (z + oz) >> 4)) return false;
            }
        }
        int gy = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos below = new BlockPos(x, gy - 1, z);
        BlockState bs = w.getBlockState(below);
        if (!w.getFluidState(below).isEmpty() || bs.isIn(BlockTags.LOGS) || bs.isIn(BlockTags.LEAVES)
                || bs.getCollisionShape(w, below).isEmpty()) return false;
        if (!ignoreFlat) {
            for (int[] c : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
                int h = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x + c[0], z + c[1]);
                BlockPos hb = new BlockPos(x + c[0], h - 1, z + c[1]);
                if (Math.abs(h - gy) > 3 || !w.getFluidState(hb).isEmpty()) return false;
            }
        }
        switch (type) {
            case 0 -> buildWell(w, x, gy, z);
            case 1 -> buildHouse(w, x, gy, z);
            default -> buildTree(w, x, gy, z);
        }
        PLACED.add(new Placed(x, z, type));
        save(w.getServer());
        return true;
    }

    // ------------------------------------------------------------------ utilidades
    private static void put(ServerWorld w, int x, int y, int z, BlockState s) {
        w.setBlockState(new BlockPos(x, y, z), s);
    }

    /** Sin avisar a los vecinos (para puertas y camas, que se rompen si les falta la otra mitad). */
    private static void putQuiet(ServerWorld w, int x, int y, int z, BlockState s) {
        w.setBlockState(new BlockPos(x, y, z), s, 2);
    }

    private static void fill(ServerWorld w, int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) put(w, x, y, z, s);
    }

    private static void prepare(ServerWorld w, int cx, int gy, int cz, int r) {
        BlockState air = Blocks.AIR.getDefaultState();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int y = gy - 1; y >= gy - 3; y--) {
                    BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                    if (!w.getBlockState(p).isSolidBlock(w, p)) w.setBlockState(p, Blocks.DIRT.getDefaultState());
                }
                for (int y = gy; y <= gy + 10; y++) put(w, cx + dx, y, cz + dz, air);
            }
        }
    }

    private static void chest(ServerWorld w, int x, int y, int z, Direction facing, int notes) {
        BlockPos pos = new BlockPos(x, y, z);
        w.setBlockState(pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing));
        BlockEntity be = w.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity c) {
            c.setStack(0, new ItemStack(ModItems.NOTA_ROTA, notes));
            c.setStack(1, new ItemStack(Items.TORCH, 6));
            c.setStack(2, new ItemStack(Items.BREAD, 3));
        }
    }

    // ------------------------------------------------------------------ EL POZO SELLADO
    private static void buildWell(ServerWorld w, int cx, int gy, int cz) {
        Random r = w.getRandom();
        BlockState sb = Blocks.STONE_BRICKS.getDefaultState();
        BlockState msb = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        prepare(w, cx, gy, cz, 4);
        // bloque macizo de ladrillo con el hueco del pozo (3x3) y suelo en el fondo
        fill(w, cx - 2, gy - 8, cz - 2, cx + 2, gy - 1, cz + 2, sb);
        fill(w, cx - 1, gy - 7, cz - 1, cx + 1, gy - 1, cz + 1, air);
        // brocal de 2 de alto sobre el suelo
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
                    put(w, cx + dx, gy, cz + dz, r.nextInt(3) == 0 ? msb : sb);
                    put(w, cx + dx, gy + 1, cz + dz, r.nextInt(3) == 0 ? msb : sb);
                }
            }
        }
        // sellado con piedra y cal
        fill(w, cx - 1, gy, cz - 1, cx + 1, gy, cz + 1, Blocks.COBBLESTONE.getDefaultState());
        // escalera por la pared sur del pozo
        for (int y = gy - 7; y <= gy - 1; y++) {
            put(w, cx, y, cz + 1, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH));
        }
        chest(w, cx - 1, gy - 7, cz - 1, Direction.SOUTH, 2);
        put(w, cx + 1, gy - 7, cz - 1, Blocks.COBWEB.getDefaultState());
        put(w, cx + 1, gy - 7, cz, Blocks.COBWEB.getDefaultState());
    }

    // ------------------------------------------------------------------ CASA DE SAN MUDO
    private static void buildHouse(ServerWorld w, int cx, int gy, int cz) {
        Random r = w.getRandom();
        BlockState plank = Blocks.SPRUCE_PLANKS.getDefaultState();
        BlockState log = Blocks.SPRUCE_LOG.getDefaultState();
        BlockState cobble = Blocks.MOSSY_COBBLESTONE.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        prepare(w, cx, gy, cz, 5);
        fill(w, cx - 3, gy - 1, cz - 3, cx + 3, gy - 1, cz + 3, plank);          // suelo
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 3) continue;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                for (int y = 0; y <= 3; y++) {
                    BlockState s = corner ? log : (y == 0 ? cobble : plank);
                    if (!corner && y >= 2 && r.nextInt(14) == 0) s = air;       // paredes rotas
                    put(w, cx + dx, gy + y, cz + dz, s);
                }
            }
        }
        fill(w, cx - 3, gy + 4, cz - 3, cx + 3, gy + 4, cz + 3, plank);          // techo
        // puerta al sur (sin avisar a vecinos: se romperia sin su otra mitad)
        putQuiet(w, cx, gy, cz + 3, Blocks.SPRUCE_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        putQuiet(w, cx, gy + 1, cz + 3, Blocks.SPRUCE_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // ventanas (algunas rotas)
        for (int sx : new int[] {-3, 3}) {
            for (int dz : new int[] {-1, 1}) {
                put(w, cx + sx, gy + 1, cz + dz, r.nextInt(5) < 3 ? Blocks.GLASS_PANE.getDefaultState() : air);
            }
        }
        // cama y cofre
        putQuiet(w, cx - 2, gy, cz - 2, Blocks.RED_BED.getDefaultState()
                .with(BedBlock.FACING, Direction.EAST).with(BedBlock.PART, BedPart.FOOT));
        putQuiet(w, cx - 1, gy, cz - 2, Blocks.RED_BED.getDefaultState()
                .with(BedBlock.FACING, Direction.EAST).with(BedBlock.PART, BedPart.HEAD));
        chest(w, cx + 2, gy, cz - 2, Direction.SOUTH, 1);
        for (int i = 0; i < 6; i++) {
            put(w, cx - 2 + r.nextInt(5), gy + 3, cz - 2 + r.nextInt(5), Blocks.COBWEB.getDefaultState());
        }
    }

    // ------------------------------------------------------------------ EL ARBOL DEL VECINO
    private static void buildTree(ServerWorld w, int cx, int gy, int cz) {
        Random r = w.getRandom();
        BlockState log = Blocks.OAK_LOG.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        prepare(w, cx, gy, cz, 5);
        // tronco 2x2 muerto
        for (int y = gy; y <= gy + 9; y++) {
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++) put(w, cx + dx, y, cz + dz, log);
        }
        // ramas secas
        for (int[] b : new int[][] {{5, 1, 0}, {6, -1, 0}, {7, 0, 1}, {8, 0, -1}, {4, 0, 1}}) {
            int y = gy + b[0];
            for (int i = 1; i <= 3; i++) {
                int x = cx + (b[1] != 0 ? (b[1] > 0 ? 1 + i : -i) : 0);
                int z = cz + (b[2] != 0 ? (b[2] > 0 ? 1 + i : -i) : 0);
                BlockState s = log.with(PillarBlock.AXIS, b[1] != 0 ? Direction.Axis.X : Direction.Axis.Z);
                put(w, x, y + (i == 3 ? 1 : 0), z, s);
            }
        }
        // raices al pie
        for (int i = 0; i < 14; i++) {
            put(w, cx - 3 + r.nextInt(8), gy, cz - 3 + r.nextInt(8), Blocks.MANGROVE_ROOTS.getDefaultState());
        }
        // tumba: monticulo con lapida y el cofre enterrado debajo
        chest(w, cx + 4, gy - 1, cz + 3, Direction.SOUTH, 2);
        put(w, cx + 4, gy, cz + 3, Blocks.COARSE_DIRT.getDefaultState());
        put(w, cx + 4, gy + 1, cz + 3, Blocks.COBBLESTONE_WALL.getDefaultState());
        put(w, cx + 4, gy + 2, cz + 3, Blocks.COBBLESTONE_WALL.getDefaultState());
        put(w, cx + 3, gy, cz + 3, Blocks.COARSE_DIRT.getDefaultState());
        put(w, cx + 5, gy, cz + 3, air);
    }
}
