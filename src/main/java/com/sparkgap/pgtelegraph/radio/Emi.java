package com.sparkgap.pgtelegraph.radio;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;

/**
 * Electrical noise from machines near a receiver: Power Grid machines that hum (motors,
 * generators, transformers under load) and electric machines that spin. Each one is a broadband
 * source of -90 dBm (in 500 Hz, at 1 MHz, 1 block away), falling 20 dB per decade of distance and
 * 10 dB per decade of frequency; nothing is heard from more than {@value #RANGE} blocks.
 */
public final class Emi {
    private Emi() {}

    public static final int RANGE = 8;
    private static final double AT_1M_DBM = -90;

    /** total machine noise at the receiver (dBm in 500 Hz), -300 when quiet */
    public static double noiseDbm(Level level, BlockPos pos, long hz) {
        double mw = 0;
        double fDb = 10 * Math.log10(Math.max(1, hz / 1e6));
        int cx0 = (pos.getX() - RANGE) >> 4, cx1 = (pos.getX() + RANGE) >> 4;
        int cz0 = (pos.getZ() - RANGE) >> 4, cz1 = (pos.getZ() + RANGE) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk ch = level.getChunk(cx, cz);
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    double d2 = be.getBlockPos().distSqr(pos);
                    if (d2 > RANGE * RANGE || d2 < 0.5 || !noisy(be)) continue;
                    double d = Math.sqrt(d2);
                    mw += RadioMath.fromDb(AT_1M_DBM - 20 * Math.log10(d) - fDb);
                }
            }
        }
        return mw <= 0 ? -300 : 10 * Math.log10(mw);
    }

    static boolean noisy(BlockEntity be) {
        if (!(be instanceof ElectricBlockEntity e)) return false;
        if (e.isNoisy()) return true;
        return be instanceof KineticBlockEntity k && Math.abs(k.getSpeed()) > 0;
    }
}
