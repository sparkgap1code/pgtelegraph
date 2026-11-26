package com.sparkgap.pgtelegraph.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Antennas under water or under ground. Radio waves die off in a conductor within a few skin
 * depths, δ = 503·√(ρ/f) m: sea water (ρ ≈ 0.25 Ω·m) lets VLF through for metres but stops HF in
 * centimetres; rock and soil (ρ ≈ 100 Ω·m) pass VLF for tens of metres. Loss is 8.686 dB per δ.
 * Only natural ground counts (stone, dirt, sand, gravel, ores, water): a house roof does not.
 */
public final class Burial {
    private Burial() {}

    public static final double WATER_OHM_M = 0.25, EARTH_OHM_M = 100;
    private static final int MAX_DEPTH = 96;

    public static double skinDepthM(double ohmM, double hz) {
        return 503 * Math.sqrt(ohmM / Math.max(1, hz));
    }

    public static double lossDb(Level level, BlockPos p, double hz) {
        if (!level.isLoaded(p)) return 0;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
        if (top <= p.getY() + 1) return 0;
        int water = 0, earth = 0;
        BlockPos.MutableBlockPos m = p.mutable();
        for (int y = p.getY() + 1; y < top && y <= p.getY() + MAX_DEPTH; y++) {
            m.setY(y);
            BlockState s = level.getBlockState(m);
            if (s.getFluidState().is(FluidTags.WATER)) water++;
            else if (earth(s)) earth++;
        }
        if (water == 0 && earth == 0) return 0;
        double db = 8.686 * (water / skinDepthM(WATER_OHM_M, hz) + earth / skinDepthM(EARTH_OHM_M, hz));
        return Math.min(300, db);
    }

    static boolean earth(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND)
                || s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
                || s.is(BlockTags.TERRACOTTA) || s.is(net.neoforged.neoforge.common.Tags.Blocks.SANDSTONE_BLOCKS);
    }
}
