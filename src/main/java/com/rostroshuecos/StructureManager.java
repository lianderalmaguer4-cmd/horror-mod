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
 * Tipos: 0 Pozo Sellado, 1 Casa, 2 Arbol del Vecino, 3 Capilla, 4 Cementerio, 5 Pozo FINAL (aparece al completar
 * las 10 notas). Todas guardan Notas Rotas o provisiones.
 */
public final class StructureManager {
    private StructureManager() {}

    private record Placed(int x, int z, int type, int dim) {}
    private static final List<Placed> PLACED = new ArrayList<>();
    private static boolean loaded = false;

    private static final String[] NAMES = {"Pozo Sellado", "Casa de San Mudo", "Árbol del Vecino",
            "Capilla de San Mudo", "Cementerio de San Mudo", "Pozo de San Mudo (final)",
            "Puerta Corrupta", "Casa inicial", "Escuela de San Mudo"};
    private static final String[] DIM_NAMES = {"Overworld", "Pradera", "Tierra de Ceniza"};

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
                if (p.length >= 3) {
                    PLACED.add(new Placed(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                            p.length >= 4 ? Integer.parseInt(p[3]) : 0));
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        }
    }

    private static void save(MinecraftServer s) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Placed p : PLACED) sb.append(p.x()).append(' ').append(p.z()).append(' ').append(p.type()).append(' ').append(p.dim()).append('\n');
            Files.writeString(file(s), sb.toString());
        } catch (IOException ignored) {
        }
    }

    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Placed p : PLACED) {
            if (p.type() < 100) out.add(NAMES[p.type()] + " [" + DIM_NAMES[p.dim()] + "]: x=" + p.x() + " z=" + p.z());
        }
        return out;
    }

    // ------------------------------------------------------------------ aparicion
    /** Llamado cada 10 s (5 s en las dimensiones del mod) por jugador en la superficie. */
    public static void tick(ServerWorld w, ServerPlayerEntity p) {
        boolean overworld = w.getRegistryKey() == World.OVERWORLD;
        boolean modDim = HorrorUtil.isModDim(w);
        if (!loaded || !(overworld || modDim)) return;
        int dim = HorrorUtil.dimIndex(w);
        Random r = w.getRandom();

        // el Pozo de San Mudo (final) se abre donde estes al completar las notas
        if (p.getCommandTags().contains("rh_final_pending") && dim == 1 && !hasType(5, 1)) {
            if (placeFinal(w, p)) {
                p.removeCommandTag("rh_final_pending");
                int[] c = finalCoords();
                if (c != null) {
                    p.sendMessage(net.minecraft.text.Text.literal("Las notas se ordenan solas. Algo se abrió en la tierra ("
                                    + DIM_NAMES[c[2]] + "): x=" + c[0] + " z=" + c[1] + ".")
                            .formatted(net.minecraft.util.Formatting.DARK_RED, net.minecraft.util.Formatting.ITALIC), false);
                }
            }
            return;
        }
        if (!w.isSkyVisible(p.getBlockPos())) return;

        // la Puerta Corrupta tiene que existir en el Overworld
        if (overworld && !hasType(6, 0)) {
            if (r.nextInt(2) == 0) tryPlaceNear(w, p, 6);
            return;
        }
        if (overworld && r.nextInt(3) != 0) return;

        // en la Pradera y la Ceniza hay muchas mas ruinas (cada ~110 bloques) que en el Overworld (cada ~300)
        double spacing = modDim ? 110.0 : 300.0;
        for (Placed s : PLACED) {
            if (s.dim() != dim) continue;
            double dx = s.x() - p.getX();
            double dz = s.z() - p.getZ();
            if (dx * dx + dz * dz < spacing * spacing) return;
        }
        int[] kinds = {0, 1, 2, 3, 4, 8};   // pozo sellado, casa, arbol, capilla, cementerio, escuela
        tryPlaceNear(w, p, kinds[r.nextInt(kinds.length)]);
    }

    static boolean tryPlaceNear(ServerWorld w, ServerPlayerEntity p, int type) {
        Random r = w.getRandom();
        for (int i = 0; i < 12; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = 50 + r.nextInt(30);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            if (place(w, x, z, type, false)) return true;
        }
        return false;
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
            case 3 -> buildChapel(w, x, gy, z);
            case 4 -> buildGraveyard(w, x, gy, z);
            case 5 -> buildFinalWell(w, x, gy, z);
            case 6 -> buildCorruptDoor(w, x, gy, z);
            case 8 -> buildSchool(w, x, gy, z);
            default -> buildTree(w, x, gy, z);
        }
        if (RITUAL_R[type] > 0) buildRitualAltar(w, x, gy, z + RITUAL_R[type]);
        PLACED.add(new Placed(x, z, type, HorrorUtil.dimIndex(w)));
        save(w.getServer());
        return true;
    }

    public static boolean hasType(int type) {
        for (Placed p : PLACED) if (p.type() == type) return true;
        return false;
    }

    public static boolean hasType(int type, int dim) {
        for (Placed p : PLACED) if (p.type() == type && p.dim() == dim) return true;
        return false;
    }

    /** {x, z, dimension} del Pozo de San Mudo (final), o null si aun no existe. */
    public static int[] finalCoords() {
        for (Placed p : PLACED) if (p.type() == 5) return new int[] {p.x(), p.z(), p.dim()};
        return null;
    }

    /** {x, z} de la Puerta Corrupta del Overworld, o null. */
    public static int[] doorCoords() {
        for (Placed p : PLACED) if (p.type() == 6) return new int[] {p.x(), p.z()};
        return null;
    }

    public static String dimName(int dim) {
        return DIM_NAMES[Math.max(0, Math.min(2, dim))];
    }

    /** ¿Esta posicion esta en el Pozo final? (el altar de obsidiana llorosa esta en su centro) */
    public static boolean nearFinal(ServerWorld w, BlockPos pos) {
        int dim = HorrorUtil.dimIndex(w);
        for (Placed p : PLACED) {
            if (p.type() != 5 || p.dim() != dim) continue;
            int dx = p.x() - pos.getX();
            int dz = p.z() - pos.getZ();
            if (dx * dx + dz * dz <= 16) return true;
        }
        return false;
    }

    /** Distancia (en filas z) del altar de ritual al centro de cada estructura. 0 = no tiene altar de ritual. */
    private static final int[] RITUAL_R = {6, 7, 7, 8, 9, 0, 0, 0, 8};

    /** Tipo de estructura (0-4, 8) si 'pos' es el altar de ritual (magnetita) de una de ellas; -1 si no. */
    public static int ritualAltarType(ServerWorld w, BlockPos pos) {
        if (!w.getBlockState(pos).isOf(Blocks.LODESTONE)) return -1;
        int dim = HorrorUtil.dimIndex(w);
        for (Placed p : PLACED) {
            if (p.type() >= RITUAL_R.length || RITUAL_R[p.type()] == 0 || p.dim() != dim) continue;
            if (p.x() == pos.getX() && p.z() + RITUAL_R[p.type()] == pos.getZ()) return p.type();
        }
        return -1;
    }

    // ---- monstruos vencidos (se guardan como registros 100+indice) ----
    public static boolean isDefeated(int idx) {
        for (Placed p : PLACED) if (p.type() == 100 + idx) return true;
        return false;
    }

    public static void markDefeated(MinecraftServer server, int idx) {
        if (isDefeated(idx)) return;
        PLACED.add(new Placed(0, 0, 100 + idx, 0));
        save(server);
    }

    public static int[] finalCoords(int dim) {
        for (Placed p : PLACED) if (p.type() == 5 && p.dim() == dim) return new int[] {p.x(), p.z(), p.dim()};
        return null;
    }

    /** Construye el pozo en unas coordenadas concretas (para la version de ceniza del pozo de la Pradera). */
    public static boolean placeFinalAt(ServerWorld w, int x, int z) {
        int dim = HorrorUtil.dimIndex(w);
        if (hasType(5, dim)) return true;
        for (int cx = (x - 9) >> 4; cx <= (x + 9) >> 4; cx++) {
            for (int cz = (z - 9) >> 4; cz <= (z + 9) >> 4; cz++) w.getChunk(cx, cz);
        }
        int gy = HorrorUtil.isModDim(w) ? DimTerrain.STAND_Y : w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        buildFinalWell(w, x, gy, z);
        PLACED.add(new Placed(x, z, 5, dim));
        save(w.getServer());
        return true;
    }

    private static Placed nearest(int dim, int type, ServerPlayerEntity p) {
        Placed best = null;
        double bd = Double.MAX_VALUE;
        for (Placed s : PLACED) {
            if (s.type() != type || s.dim() != dim) continue;
            double dx = s.x() - p.getX();
            double dz = s.z() - p.getZ();
            double d = dx * dx + dz * dz;
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    /** "Lo siento hacia el este, a unos 90 bloques." Si no hay ninguna de ese tipo cerca, hace aparecer una. */
    public static String directionHint(ServerWorld w, ServerPlayerEntity p, int type) {
        int dim = HorrorUtil.dimIndex(w);
        Placed best = nearest(dim, type, p);
        if (best == null) {
            tryPlaceNear(w, p, type);
            best = nearest(dim, type, p);
        }
        if (best == null) return "No lo siento todavía... sigue explorando.";
        double dx = best.x() - p.getX();
        double dz = best.z() - p.getZ();
        String[] dirs = {"este", "sureste", "sur", "suroeste", "oeste", "noroeste", "norte", "noreste"};
        int k = (((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4))) % 8 + 8) % 8;
        int dist = (int) (Math.round(Math.sqrt(dx * dx + dz * dz) / 10.0) * 10);
        return "Lo siento hacia el " + dirs[k] + ", a unos " + dist + " bloques.";
    }

    /** ¿Hay alguna estructura del mod a menos de 'r' bloques de (x,z)? (el terreno se deja plano a su alrededor) */
    public static boolean nearAny(ServerWorld w, int x, int z, int r) {
        int dim = HorrorUtil.dimIndex(w);
        for (Placed p : PLACED) {
            if (p.type() >= 100 || p.dim() != dim) continue;
            int dx = p.x() - x;
            int dz = p.z() - z;
            if (dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }

    /** La puerta carmesi de la Puerta Corrupta (siempre en el Overworld). */
    public static boolean isCorruptDoor(ServerWorld w, BlockPos pos) {
        if (HorrorUtil.dimIndex(w) != 0) return false;
        for (Placed p : PLACED) if (p.type() == 6 && p.x() == pos.getX() && p.z() == pos.getZ()) return true;
        return false;
    }

    /** La puerta trasera (carmesi) de la casa inicial: vuelve al Overworld. */
    public static boolean isReturnDoor(ServerWorld w, BlockPos pos) {
        int dim = HorrorUtil.dimIndex(w);
        if (dim == 0) return false;
        for (Placed p : PLACED) {
            if (p.type() == 7 && p.dim() == dim && p.x() == pos.getX() && p.z() == pos.getZ()) return true;
        }
        return false;
    }

    /** ¿Hay una casa inicial a menos de 'r' bloques de (x,z) en esta dimension? (el terreno se mantiene plano a su alrededor) */
    public static boolean nearHouse(ServerWorld w, int x, int z, int r) {
        int dim = HorrorUtil.dimIndex(w);
        for (Placed p : PLACED) {
            if (p.type() != 7 || p.dim() != dim) continue;
            int dx = p.x() - x;
            int dz = p.z() - (z - 3);
            if (dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }

    /**
     * Devuelve {x, y, z} de la casa inicial del jugador en esa dimension, construyendola la primera vez.
     * Cada jugador tiene la suya (separadas 120 bloques en el eje X).
     */
    public static int[] ensureHouse(ServerWorld dw, ServerPlayerEntity p, int dim) {
        String prefix = "rhh_" + dim + "_";
        for (String t : p.getCommandTags()) {
            if (t.startsWith(prefix)) {
                String[] a = t.substring(prefix.length()).split("_");
                return new int[] {Integer.parseInt(a[0]), DimTerrain.STAND_Y, Integer.parseInt(a[1])};
            }
        }
        int n = 0;
        for (Placed s : PLACED) if (s.type() == 7 && s.dim() == dim) n++;
        int hx = n * 120;
        int hz = 0;
        // fuerza la generacion de los chunks de la casa
        for (int cx = (hx - 9) >> 4; cx <= (hx + 9) >> 4; cx++) {
            for (int cz = (hz - 9) >> 4; cz <= (hz + 9) >> 4; cz++) dw.getChunk(cx, cz);
        }
        buildStarterHouse(dw, hx, DimTerrain.STAND_Y, hz, dim == 2);
        PLACED.add(new Placed(hx, hz - 3, 7, dim));   // se anota la puerta trasera (la de regreso)
        save(dw.getServer());
        p.addCommandTag(prefix + hx + "_" + hz);
        return new int[] {hx, DimTerrain.STAND_Y, hz};
    }

    private static boolean placeFinal(ServerWorld w, ServerPlayerEntity p) {
        Random r = w.getRandom();
        for (int i = 0; i < 40; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = 60 + r.nextInt(30);
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            if (place(w, x, z, 5, true)) return true;
        }
        return false;
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
            c.setStack(3, new ItemStack(ModItems.SAL, 1 + w.getRandom().nextInt(2)));
            if (w.getRandom().nextBoolean()) c.setStack(4, new ItemStack(ModItems.VELA, 1));
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
        put(w, cx, gy, cz - 2, Blocks.BOOKSHELF.getDefaultState());
        put(w, cx, gy + 1, cz - 2, Blocks.BOOKSHELF.getDefaultState());
        put(w, cx + 2, gy, cz + 2, Blocks.CRAFTING_TABLE.getDefaultState());
        put(w, cx - 2, gy, cz + 2, Blocks.CAULDRON.getDefaultState());
        put(w, cx + 1, gy, cz, Blocks.RED_CARPET.getDefaultState());
        put(w, cx + 1, gy, cz + 1, Blocks.RED_CARPET.getDefaultState());
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

    // ------------------------------------------------------------------ CAPILLA DE SAN MUDO
    private static void buildChapel(ServerWorld w, int cx, int gy, int cz) {
        Random r = w.getRandom();
        BlockState sb = Blocks.STONE_BRICKS.getDefaultState();
        BlockState msb = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        BlockState csb = Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        prepare(w, cx, gy, cz, 7);
        fill(w, cx - 3, gy - 1, cz - 5, cx + 3, gy - 1, cz + 5, sb);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (Math.abs(dx) != 3 && Math.abs(dz) != 5) continue;
                for (int y = 0; y <= 4; y++) {
                    int k = r.nextInt(3);
                    BlockState s = k == 0 ? msb : (k == 1 ? csb : sb);
                    if (y >= 2 && r.nextInt(12) == 0) s = air;
                    put(w, cx + dx, gy + y, cz + dz, s);
                }
            }
        }
        fill(w, cx - 3, gy + 5, cz - 5, cx + 3, gy + 5, cz + 5, Blocks.DEEPSLATE_BRICKS.getDefaultState());
        put(w, cx, gy + 6, cz - 5, Blocks.BELL.getDefaultState());
        // puerta al sur
        putQuiet(w, cx, gy, cz + 5, Blocks.DARK_OAK_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        putQuiet(w, cx, gy + 1, cz + 5, Blocks.DARK_OAK_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // vidrieras rotas
        for (int sx : new int[] {-3, 3}) {
            for (int dz : new int[] {-2, 2}) {
                for (int y = 2; y <= 3; y++) {
                    put(w, cx + sx, gy + y, cz + dz,
                            r.nextInt(3) < 2 ? Blocks.GRAY_STAINED_GLASS_PANE.getDefaultState() : air);
                }
            }
        }
        // bancos
        for (int dz : new int[] {-1, 1, 3}) {
            for (int dx : new int[] {-2, -1, 1, 2}) {
                put(w, cx + dx, gy, cz + dz, Blocks.SPRUCE_SLAB.getDefaultState());
            }
        }
        // altar
        put(w, cx, gy, cz - 4, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
        put(w, cx, gy, cz - 3, Blocks.LECTERN.getDefaultState());
        put(w, cx - 1, gy, cz - 4, Blocks.CANDLE.getDefaultState());
        put(w, cx + 1, gy, cz - 4, Blocks.CANDLE.getDefaultState());
        chest(w, cx + 2, gy, cz - 4, Direction.SOUTH, 2);
        for (int i = 0; i < 8; i++) {
            put(w, cx - 2 + r.nextInt(5), gy + 4, cz - 4 + r.nextInt(9), Blocks.COBWEB.getDefaultState());
        }
    }

    // ------------------------------------------------------------------ CEMENTERIO DE SAN MUDO
    private static void buildGraveyard(ServerWorld w, int cx, int gy, int cz) {
        Random r = w.getRandom();
        BlockState wall = Blocks.COBBLESTONE_WALL.getDefaultState();
        BlockState mwall = Blocks.MOSSY_COBBLESTONE_WALL.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState dirt = Blocks.COARSE_DIRT.getDefaultState();
        prepare(w, cx, gy, cz, 7);
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 6) continue;
                if (dz == 6 && Math.abs(dx) <= 1) continue;   // entrada al sur
                put(w, cx + dx, gy, cz + dz, r.nextInt(5) == 0 ? air : wall);
            }
        }
        int[][] graves = {{-4, -3}, {0, -3}, {4, -3}, {-4, 1}, {4, 1}, {-3, 4}, {3, 4}, {0, 3}};
        for (int i = 0; i < graves.length; i++) {
            int gx = cx + graves[i][0];
            int gz = cz + graves[i][1];
            put(w, gx, gy, gz, mwall);
            put(w, gx, gy + 1, gz, mwall);
            if (i == 1 || i == 5) chest(w, gx, gy - 1, gz + 1, Direction.SOUTH, 1);   // enterrado bajo el monticulo
            put(w, gx, gy, gz + 1, dirt);
            put(w, gx, gy, gz + 2, dirt);
        }
        // arbol seco en el centro
        for (int y = gy; y <= gy + 5; y++) put(w, cx, y, cz - 1, Blocks.OAK_LOG.getDefaultState());
        for (int i = 1; i <= 2; i++) {
            put(w, cx + i, gy + 4, cz - 1, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
            put(w, cx - i, gy + 5, cz - 1, Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
        }
        for (int i = 0; i < 6; i++) {
            put(w, cx - 5 + r.nextInt(11), gy + 1, cz - 5 + r.nextInt(11), Blocks.COBWEB.getDefaultState());
        }
    }

    // ------------------------------------------------------------------ POZO DE SAN MUDO (FINAL)
    private static void buildFinalWell(ServerWorld w, int cx, int gy, int cz) {
        Random r = w.getRandom();
        BlockState db = Blocks.DEEPSLATE_BRICKS.getDefaultState();
        BlockState cdb = Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        prepare(w, cx, gy, cz, 6);
        // macizo de ladrillo, camara de 7x7 y pozo de 3x3 hasta la superficie
        fill(w, cx - 4, gy - 14, cz - 4, cx + 4, gy - 1, cz + 4, db);
        for (int x = cx - 4; x <= cx + 4; x++)
            for (int y = gy - 14; y <= gy - 1; y++)
                for (int z = cz - 4; z <= cz + 4; z++)
                    if (r.nextInt(8) == 0) put(w, x, y, z, cdb);
        fill(w, cx - 3, gy - 13, cz - 3, cx + 3, gy - 9, cz + 3, air);
        fill(w, cx - 1, gy - 8, cz + 1, cx + 1, gy - 1, cz + 3, air);
        // brocal abierto (sin tapa) de 2 de alto alrededor del pozo
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
                    put(w, cx + dx, gy, cz + 2 + dz, r.nextInt(3) == 0 ? cdb : db);
                    if (r.nextInt(4) != 0) put(w, cx + dx, gy + 1, cz + 2 + dz, db);
                }
            }
        }
        // escalera por la pared sur (z = cz+4 es pared solida)
        for (int y = gy - 13; y <= gy - 1; y++) {
            put(w, cx, y, cz + 3, Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH));
        }
        // altar de obsidiana llorosa y suelo de arena de almas
        fill(w, cx - 1, gy - 14, cz - 1, cx + 1, gy - 14, cz + 1, Blocks.SOUL_SAND.getDefaultState());
        put(w, cx, gy - 13, cz, Blocks.CRYING_OBSIDIAN.getDefaultState());
        for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            for (int y = gy - 13; y <= gy - 10; y++) put(w, cx + c[0], y, cz + c[1], Blocks.BLACKSTONE.getDefaultState());
        }
        // provisiones para el final: velas y antorchas
        BlockPos cp = new BlockPos(cx - 3, gy - 13, cz);
        w.setBlockState(cp, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.EAST));
        BlockEntity be = w.getBlockEntity(cp);
        if (be instanceof ChestBlockEntity c) {
            c.setStack(0, new ItemStack(ModItems.VELA, 3));
            c.setStack(1, new ItemStack(Items.TORCH, 12));
            c.setStack(2, new ItemStack(Items.BREAD, 8));
        }
        for (int i = 0; i < 10; i++) {
            put(w, cx - 3 + r.nextInt(7), gy - 12, cz - 3 + r.nextInt(7), Blocks.COBWEB.getDefaultState());
        }
    }

    // ------------------------------------------------------------------ PUERTA CORRUPTA (Overworld)
    private static void buildCorruptDoor(ServerWorld w, int cx, int gy, int cz) {
        Random r = w.getRandom();
        BlockState pb = Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        BlockState cry = Blocks.CRYING_OBSIDIAN.getDefaultState();
        prepare(w, cx, gy, cz, 4);
        fill(w, cx - 3, gy - 1, cz - 3, cx + 3, gy - 1, cz + 3, Blocks.POLISHED_BLACKSTONE.getDefaultState());
        for (int y = gy; y <= gy + 3; y++) {
            put(w, cx - 1, y, cz, pb);
            put(w, cx + 1, y, cz, pb);
        }
        put(w, cx, gy + 2, cz, pb);
        put(w, cx, gy + 3, cz, pb);
        putQuiet(w, cx, gy, cz, Blocks.CRIMSON_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        putQuiet(w, cx, gy + 1, cz, Blocks.CRIMSON_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        put(w, cx - 2, gy, cz, cry);
        put(w, cx + 2, gy, cz, cry);
        put(w, cx - 1, gy + 4, cz, cry);
        put(w, cx + 1, gy + 4, cz, cry);
        for (int i = 0; i < 5; i++) {
            put(w, cx - 2 + r.nextInt(5), gy, cz - 2 + r.nextInt(5), Blocks.COBWEB.getDefaultState());
        }
    }

    // ------------------------------------------------------------------ CASA INICIAL (Pradera / Ceniza)
    private static void buildStarterHouse(ServerWorld w, int cx, int gy, int cz, boolean burnt) {
        Random r = w.getRandom();
        BlockState plank = (burnt ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS).getDefaultState();
        BlockState log = (burnt ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG).getDefaultState();
        BlockState base = (burnt ? Blocks.BLACKSTONE : Blocks.MOSSY_COBBLESTONE).getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        prepare(w, cx, gy, cz, 6);
        fill(w, cx - 3, gy - 1, cz - 3, cx + 3, gy - 1, cz + 3, plank);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 3) continue;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                for (int y = 0; y <= 3; y++) {
                    put(w, cx + dx, gy + y, cz + dz, corner ? log : (y == 0 ? base : plank));
                }
            }
        }
        fill(w, cx - 3, gy + 4, cz - 3, cx + 3, gy + 4, cz + 3, plank);
        // puerta de entrada (sur)
        putQuiet(w, cx, gy, cz + 3, (burnt ? Blocks.DARK_OAK_DOOR : Blocks.SPRUCE_DOOR).getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        putQuiet(w, cx, gy + 1, cz + 3, (burnt ? Blocks.DARK_OAK_DOOR : Blocks.SPRUCE_DOOR).getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // puerta trasera carmesi (norte): vuelve al Overworld
        putQuiet(w, cx, gy, cz - 3, Blocks.CRIMSON_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.NORTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        putQuiet(w, cx, gy + 1, cz - 3, Blocks.CRIMSON_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.NORTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        put(w, cx - 1, gy + 2, cz - 3, Blocks.CRYING_OBSIDIAN.getDefaultState());
        put(w, cx + 1, gy + 2, cz - 3, Blocks.CRYING_OBSIDIAN.getDefaultState());
        // ventanas (en la ceniza, casi todas rotas)
        for (int sx : new int[] {-3, 3}) {
            for (int dz : new int[] {-1, 1}) {
                boolean glass = burnt ? r.nextInt(5) == 0 : r.nextInt(5) != 0;
                put(w, cx + sx, gy + 1, cz + dz, glass ? Blocks.GLASS_PANE.getDefaultState() : air);
            }
        }
        // cama, cofre con provisiones, estante, mesa y horno
        putQuiet(w, cx - 2, gy, cz - 2, Blocks.RED_BED.getDefaultState()
                .with(BedBlock.FACING, Direction.EAST).with(BedBlock.PART, BedPart.FOOT));
        putQuiet(w, cx - 1, gy, cz - 2, Blocks.RED_BED.getDefaultState()
                .with(BedBlock.FACING, Direction.EAST).with(BedBlock.PART, BedPart.HEAD));
        BlockPos cp = new BlockPos(cx + 2, gy, cz - 2);
        w.setBlockState(cp, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH));
        BlockEntity be = w.getBlockEntity(cp);
        if (be instanceof ChestBlockEntity c) {
            c.setStack(0, new ItemStack(Items.BREAD, 12));
            c.setStack(1, new ItemStack(Items.TORCH, 16));
            c.setStack(2, new ItemStack(ModItems.VELA, 2));
            c.setStack(3, new ItemStack(Items.IRON_SWORD, 1));
            c.setStack(4, new ItemStack(Items.COOKED_BEEF, 8));
            c.setStack(5, new ItemStack(ModItems.SAL, 2));
        }
        put(w, cx + 1, gy, cz - 2, Blocks.BOOKSHELF.getDefaultState());
        put(w, cx + 1, gy + 1, cz - 2, Blocks.BOOKSHELF.getDefaultState());
        put(w, cx + 2, gy, cz + 2, Blocks.CRAFTING_TABLE.getDefaultState());
        put(w, cx - 2, gy, cz + 2, Blocks.FURNACE.getDefaultState());
        put(w, cx, gy, cz, Blocks.RED_CARPET.getDefaultState());
        put(w, cx, gy, cz + 1, Blocks.RED_CARPET.getDefaultState());
        if (burnt) {
            for (int i = 0; i < 10; i++) {
                put(w, cx - 2 + r.nextInt(5), gy + 3, cz - 2 + r.nextInt(5), Blocks.COBWEB.getDefaultState());
            }
        }
    }

    // ------------------------------------------------------------------ ALTAR DE RITUAL (magnetita sobre placa de ladrillo negro)
    private static void buildRitualAltar(ServerWorld w, int ax, int gy, int az) {
        prepare(w, ax, gy, az, 3);
        fill(w, ax - 1, gy - 1, az - 1, ax + 1, gy - 1, az + 1, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
        put(w, ax, gy, az, Blocks.LODESTONE.getDefaultState());
        for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            put(w, ax + c[0], gy, az + c[1], Blocks.CRYING_OBSIDIAN.getDefaultState());
            put(w, ax + c[0], gy + 1, az + c[1], Blocks.CANDLE.getDefaultState());
        }
    }

    // ------------------------------------------------------------------ ESCUELA DE SAN MUDO
    private static void buildSchool(ServerWorld w, int cx, int gy, int cz) {
        Random r = w.getRandom();
        BlockState brick = Blocks.BRICKS.getDefaultState();
        BlockState base = Blocks.STONE_BRICKS.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        prepare(w, cx, gy, cz, 6);
        fill(w, cx - 4, gy - 1, cz - 3, cx + 4, gy - 1, cz + 3, Blocks.SPRUCE_PLANKS.getDefaultState());
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) != 4 && Math.abs(dz) != 3) continue;
                boolean corner = Math.abs(dx) == 4 && Math.abs(dz) == 3;
                for (int y = 0; y <= 3; y++) {
                    BlockState s = (corner || y == 0) ? base : brick;
                    if (!corner && y >= 2 && r.nextInt(14) == 0) s = air;
                    put(w, cx + dx, gy + y, cz + dz, s);
                }
            }
        }
        fill(w, cx - 4, gy + 4, cz - 3, cx + 4, gy + 4, cz + 3, Blocks.SPRUCE_PLANKS.getDefaultState());
        putQuiet(w, cx, gy, cz + 3, Blocks.SPRUCE_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        putQuiet(w, cx, gy + 1, cz + 3, Blocks.SPRUCE_DOOR.getDefaultState()
                .with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int x : new int[] {cx - 2, cx + 2}) {
            for (int y = 1; y <= 2; y++) put(w, x, gy + y, cz - 3, r.nextInt(3) < 2 ? Blocks.GLASS_PANE.getDefaultState() : air);
        }
        for (int sx : new int[] {-4, 4}) {
            for (int dz : new int[] {-1, 1}) put(w, cx + sx, gy + 1, cz + dz, r.nextInt(3) < 2 ? Blocks.GLASS_PANE.getDefaultState() : air);
        }
        // pupitres, pizarra y estantes
        for (int x : new int[] {cx - 2, cx, cx + 2}) {
            for (int z : new int[] {cz, cz + 2}) put(w, x, gy, z, Blocks.SPRUCE_SLAB.getDefaultState());
        }
        fill(w, cx - 1, gy + 1, cz - 2, cx + 1, gy + 2, cz - 2, Blocks.BLACKSTONE.getDefaultState());
        put(w, cx - 3, gy, cz - 2, Blocks.BOOKSHELF.getDefaultState());
        put(w, cx - 3, gy + 1, cz - 2, Blocks.BOOKSHELF.getDefaultState());
        chest(w, cx + 3, gy, cz - 2, Direction.SOUTH, 1);
        for (int i = 0; i < 8; i++) {
            put(w, cx - 3 + r.nextInt(7), gy + 3, cz - 2 + r.nextInt(5), Blocks.COBWEB.getDefaultState());
        }
    }
}
