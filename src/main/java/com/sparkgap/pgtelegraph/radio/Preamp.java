package com.sparkgap.pgtelegraph.radio;

import com.sparkgap.pgtelegraph.block.CoaxBlock;
import com.sparkgap.pgtelegraph.block.LnaBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * An in-line LNA in a receiver's coax run: the gain it adds and the noise it brings, referred to
 * the receiver's input (Friis): coax after the LNA no longer adds to the noise figure, coax before
 * it still does (it is lost before the amplifier).
 */
public record Preamp(BlockPos pos, boolean burnt, double gainDb, double noiseDbm500) {
    /** the (first, antenna-most) LNA in the run, or null */
    public static Preamp of(Level level, AntennaLink rl, double fMHz) {
        if (rl == null || rl.coaxPath() == null || rl.amplifier() != null) return null;
        List<BlockPos> path = rl.coaxPath(); // [0] next to the antenna ... last next to the radio
        int k = -1;
        for (int i = 0; i < path.size(); i++)
            if (level.getBlockState(path.get(i)).getBlock() instanceof LnaBlock) {
                k = i;
                break;
            }
        if (k < 0) return null;
        BlockState s = level.getBlockState(path.get(k));
        if (s.getValue(LnaBlock.BURNT)) return new Preamp(path.get(k), true, -LnaBlock.BURNT_LOSS_DB, -300);
        double after = 0; // coax between the LNA and the radio
        for (int i = k + 1; i < path.size(); i++)
            if (level.getBlockState(path.get(i)).getBlock() instanceof CoaxBlock c) after += c.grade.lossDbPerM10;
        double afterDb = RadioMath.coaxLossDb(after, fMHz);
        // the LNA's own noise, input-referred kT(F-1), brought to the radio's input
        double fLin = Math.pow(10, LnaBlock.NF_DB / 10);
        double n = -174 + 10 * Math.log10(RadioMath.FILTER_HZ) + 10 * Math.log10(fLin - 1) + LnaBlock.GAIN_DB - afterDb;
        return new Preamp(path.get(k), false, LnaBlock.GAIN_DB, n);
    }

    /** the receiver's noise with the LNA's added (dBm in 500 Hz) */
    public double rxNoise(double rxNoiseDbm) {
        return burnt ? rxNoiseDbm : 10 * Math.log10(RadioMath.fromDb(rxNoiseDbm) + RadioMath.fromDb(noiseDbm500));
    }
}
