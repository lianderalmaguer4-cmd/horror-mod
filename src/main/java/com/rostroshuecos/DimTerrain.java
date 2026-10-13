package com.rostroshuecos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Da forma a las dimensiones del mod. El generador (plano) solo pone el suelo; aqui, al cargarse cada chunk cerca
 * de un jugador, se anaden los "bultos" anchos y bajos, la vegetacion y los pocos arboles (uno cada ~80 bloques).
 * Todo es determinista (depende de la semilla) y solo escribe dentro de su propio chunk, asi que las costuras no se ven.
 * En la Tierra de Ceniza cae ceniza continuamente.
 */
public final class DimTerrain {
    private DimTerrain() {}

    public static final int SURFACE = 63;            // y del ultimo bloque del suelo plano
    public static final int STAND_Y = SURFACE + 1;   // donde se pisa

    private static final Map<RegistryKey<World>, Set<Long>> DONE = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DimTerrain::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> DONE.clear());
    }

    private static void tick(ServerWorld w) {
        if (!HorrorUtil.isModDim(w) || w.getTime() % 10 != 0) return;
        boolean ash = HorrorUtil.isAsh(w);
        Set<Long> done = DONE.computeIfAbsent(w.getRegistryKey(), k -> new HashSet<>());
        for (ServerPlayerEntity p : w.getPlayers()) {
            int pcx = p.getBlockX() >> 4;
            int pcz = p.getBlockZ() >> 4;
            int budget = 6;   // pocos chunks por ciclo para no dar tirones
            for (int dx = -7; dx <= 7 && budget > 0; dx++) {
                for (int dz = -7; dz <= 7 && budget > 0; dz++) {
                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    long key = ChunkPos.toLong(cx, cz);
                    if (done.contains(key)) continue;
                    if (w.getChunkManager().getWorldChunk(cx, cz) == null) continue;   // aun no esta cargado
                    done.add(key);
                    processChunk(w, cx, cz, ash);
                    budget--;
                }
            }
            if (ash) {
                // ceniza cayendo
                w.spawnParticles(ParticleTypes.ASH, p.getX(), p.getY() + 7, p.getZ(), 50, 12, 4, 12, 0.0);
                w.spawnParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() + 7, p.getZ(), 30, 12, 4, 12, 0.0);
            }
        }
    }

    // ------------------------------------------------------------------ ruido determinista
    private static long hash(long a, long b, long s) {
        long h = a * 341873128712L + b * 132897987541L + s;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return h;
    }

    private static double rnd(long ix, long iz, long seed) {
        return (hash(ix, iz, seed) >>> 11) / (double) (1L << 53);
    }

    private static double noise2(double x, double z, long seed) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        double u = fx * fx * (3 - 2 * fx);
        double v = fz * fz * (3 - 2 * fz);
        double a = rnd(ix, iz, seed);
        double b = rnd(ix + 1, iz, seed);
        double c = rnd(ix, iz + 1, seed);
        double d = rnd(ix + 1, iz + 1, seed);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    /** Altura (0-5) del bulto en esa columna. Los bultos son muy anchos (decenas de bloques) y la mitad del suelo queda plano. */
    public static int bump(ServerWorld w, int x, int z) {
        long s = w.getSeed();
        double n = 0.7 * noise2(x / 56.0, z / 56.0, s) + 0.3 * noise2(x / 23.0, z / 23.0, s + 7);
        double t = (n - 0.52) / 0.48;
        if (t <= 0) return 0;
        double fade = Math.min(1.0, Math.max(0.0, (Math.sqrt((double) x * x + (double) z * z) - 16) / 30.0));
        if (StructureManager.nearHouse(w, x, z, 18)) fade = 0;
        return (int) Math.round(Math.min(1.0, t) * 5.0 * fade);
    }

    // ------------------------------------------------------------------ chunk
    private static void set(ServerWorld w, int x, int y, int z, BlockState s) {
        w.setBlockState(new BlockPos(x, y, z), s, 2);
    }

    private static void processChunk(ServerWorld w, int cx, int cz, boolean ash) {
        int x0 = cx << 4;
        int z0 = cz << 4;
        BlockPos marker = new BlockPos(x0, 1, z0);
        if (w.getBlockState(marker).isOf(Blocks.POLISHED_DEEPSLATE)) return;   // ya procesado (se guarda en el mundo)
        w.setBlockState(marker, Blocks.POLISHED_DEEPSLATE.getDefaultState(), 2);

        BlockState top = (ash ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK).getDefaultState();
        BlockState sub = (ash ? Blocks.GRAVEL : Blocks.DIRT).getDefaultState();
        long seed = w.getSeed();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx;
                int z = z0 + lz;
                int h = bump(w, x, z);
                if (h > 0) {
                    for (int y = SURFACE; y < SURFACE + h; y++) set(w, x, y, z, sub);
                    set(w, x, SURFACE + h, z, top);
                }
                int ty = SURFACE + h + 1;
                double r = rnd(x, z, seed + 99);
                if (!ash) {
                    if (r < 0.22) set(w, x, ty, z, Blocks.SHORT_GRASS.getDefaultState());
                    else if (r < 0.232) set(w, x, ty, z, Blocks.DANDELION.getDefaultState());
                    else if (r < 0.240) set(w, x, ty, z, Blocks.CORNFLOWER.getDefaultState());
                } else if (r < 0.015) {
                    set(w, x, ty, z, Blocks.DEAD_BUSH.getDefaultState());
                }
            }
        }

        // un arbol por celda de 5x5 chunks (80x80 bloques), con probabilidad ~60%: pocos y muy separados
        int cellX = Math.floorDiv(cx, 5);
        int cellZ = Math.floorDiv(cz, 5);
        long hh = hash(cellX, cellZ, seed ^ 0x5DEECE66DL);
        if ((hh & 0xFF) < 150) {
            int tcx = cellX * 5 + (int) ((hh >>> 8) & 0xFF) % 5;
            int tcz = cellZ * 5 + (int) ((hh >>> 16) & 0xFF) % 5;
            if (tcx == cx && tcz == cz) {
                int lx = 3 + (int) ((hh >>> 24) & 0xFF) % 10;   // 3..12: nunca cruza el borde del chunk
                int lz = 3 + (int) ((hh >>> 32) & 0xFF) % 10;
                plantTree(w, x0 + lx, z0 + lz, ash, hh);
            }
        }
    }

    private static void plantTree(ServerWorld w, int x, int z, boolean ash, long hh) {
        if (StructureManager.nearHouse(w, x, z, 20)) return;
        int base = SURFACE + bump(w, x, z) + 1;
        if (!ash) {
            int height = 5 + (int) ((hh >>> 40) & 0xFF) % 3;
            BlockState log = Blocks.OAK_LOG.getDefaultState();
            BlockState leaf = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);
            for (int y = 0; y < height; y++) set(w, x, base + y, z, log);
            int top = base + height - 1;
            for (int dy = -1; dy <= 2; dy++) {
                int r = dy == 2 ? 1 : 2;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) == r && Math.abs(dz) == r && dy != 2) continue;   // esquinas redondeadas
                        if (dx == 0 && dz == 0 && dy <= 0) continue;                        // el tronco
                        if (w.getBlockState(new BlockPos(x + dx, top + dy, z + dz)).isAir()) {
                            set(w, x + dx, top + dy, z + dz, leaf);
                        }
                    }
                }
            }
        } else {
            // arbol quemado: tronco negro con dos ramas secas
            BlockState log = Blocks.DARK_OAK_LOG.getDefaultState();
            for (int y = 0; y < 7; y++) set(w, x, base + y, z, log);
            for (int i = 1; i <= 2; i++) {
                set(w, x + i, base + 4, z, log.with(PillarBlock.AXIS, Direction.Axis.X));
                set(w, x - i, base + 5, z, log.with(PillarBlock.AXIS, Direction.Axis.X));
            }
            set(w, x + 2, base + 5, z, log);
            set(w, x - 2, base + 6, z, log);
        }
    }
}
