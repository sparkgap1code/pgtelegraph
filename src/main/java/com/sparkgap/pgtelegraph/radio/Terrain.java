package com.sparkgap.pgtelegraph.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * What the ground between two antennas does to a signal. Loaded chunks are read as they are
 * (buildings count); a stretch nobody has loaded is not taken as clear - its natural terrain is
 * asked of the world generator instead (the same height the chunk will have when it generates,
 * without loading it), so a mountain between two far stations still blocks VHF. Generated
 * heights never change, so they are cached per 4-block column.
 * <ul>
 *   <li>{@link #obstructionDb}: hills and buildings in the way of a VHF/UHF path, as single
 *       knife-edge diffraction (ITU-R P.526): for the worst obstacle the Fresnel parameter
 *       v = h sqrt(2 (d1 + d2) / (lambda d1 d2)) gives J(v) = 6.9 + 20 log(sqrt((v-0.1)^2+1) + v - 0.1).
 *       Grazing obstacles just below the line already cost a few dB.</li>
 *   <li>{@link #seaFraction}: how much of a ground-wave path runs over water (sea water conducts
 *       a thousand times better than land)</li>
 * </ul>
 */
public final class Terrain {
    private Terrain() {}

    public static double obstructionDb(Level level, BlockPos a, BlockPos b, double fMHz) {
        Vec3 pa = a.getCenter(), pb = b.getCenter();
        double len = pa.distanceTo(pb);
        if (len < 3) return 0;
        // horizontal distances follow the world's distance scale, heights stay 1 block = 1 m
        double scale = RadioMath.effectiveKm(len) * 1000 / len;
        double lambda = 299.79 / fMHz;
        int n = (int) Math.min(160, Math.max(4, len / 3));
        double worst = -10;
        for (int i = 1; i < n; i++) {
            double t = (double) i / n;
            double x = pa.x + (pb.x - pa.x) * t, y = pa.y + (pb.y - pa.y) * t, z = pa.z + (pb.z - pa.z) * t;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            int top = surface(level, bx, bz);
            if (top == Integer.MIN_VALUE) continue;
            double h = top - y;
            double d1 = t * len * scale, d2 = (1 - t) * len * scale;
            double v = h * Math.sqrt(2 * (d1 + d2) / (lambda * d1 * d2));
            worst = Math.max(worst, v);
        }
        if (worst <= -0.78) return 0;
        double j = 6.9 + 20 * Math.log10(Math.sqrt((worst - 0.1) * (worst - 0.1) + 1) + worst - 0.1);
        return Math.max(0, Math.min(80, j));
    }

    /** fraction (0-1) of the surface between a and b that is water */
    public static double seaFraction(Level level, BlockPos a, BlockPos b) {
        double len = Math.sqrt(a.distSqr(b));
        int n = (int) Math.min(48, Math.max(4, len / 8));
        int seen = 0, wet = 0;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            int x = (int) Math.floor(a.getX() + (b.getX() - a.getX()) * t), z = (int) Math.floor(a.getZ() + (b.getZ() - a.getZ()) * t);
            int w = water(level, x, z);
            if (w < 0) continue;
            seen++;
            wet += w;
        }
        return seen == 0 ? 0 : wet / (double) seen;
    }

    // ---------------------------------------------------------------- terrain where nobody is
    private static final java.util.Map<Level, it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap> GEN = new java.util.WeakHashMap<>();

    /** obstacle height at a column: the real one if loaded, else the generator's natural surface; MIN_VALUE if unknown */
    static int surface(Level level, int x, int z) {
        if (level.hasChunk(x >> 4, z >> 4)) return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        long g = generated(level, x, z);
        return g == Long.MIN_VALUE ? Integer.MIN_VALUE : (int) (g >> 32);
    }

    /** 1 if the column's surface is water, 0 if land, -1 if unknown */
    static int water(Level level, int x, int z) {
        if (level.hasChunk(x >> 4, z >> 4)) {
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            return level.getBlockState(new BlockPos(x, top, z)).is(Blocks.WATER) ? 1 : 0;
        }
        long g = generated(level, x, z);
        if (g == Long.MIN_VALUE) return -1;
        return (int) (g >> 32) > (int) g ? 1 : 0; // water surface above the sea floor
    }

    /** (surface << 32) | floor from the world generator, cached per 4x4 column; MIN_VALUE off the server */
    private static long generated(Level level, int x, int z) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return Long.MIN_VALUE;
        int qx = x & ~3, qz = z & ~3;
        long key = BlockPos.asLong(qx, 0, qz);
        var map = GEN.computeIfAbsent(level, l -> new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap());
        if (map.containsKey(key)) return map.get(key);
        var cs = sl.getChunkSource();
        var gen = cs.getGenerator();
        int top = gen.getBaseHeight(qx, qz, Heightmap.Types.WORLD_SURFACE_WG, sl, cs.randomState());
        int floor = gen.getBaseHeight(qx, qz, Heightmap.Types.OCEAN_FLOOR_WG, sl, cs.randomState());
        long v = ((long) top << 32) | (floor & 0xFFFFFFFFL);
        if (map.size() > 400_000) map.clear();
        map.put(key, v);
        return v;
    }
}
