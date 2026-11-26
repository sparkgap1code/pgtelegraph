package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Spark-gap transmitter, 1900s style: a 1 kV supply charges a capacitor that dumps through a
 * spark gap into the antenna. There is no oscillator - the antenna rings at its own resonance, so
 * the dial does nothing: the signal lands on the antenna's frequency and is smeared over 10 % of
 * it (at least 20 kHz), heard as a rasp across the band. 25 % efficient, 2 kW panel.
 * Needs at least 400 V; insulation good for 3 kV.
 */
public class SparkTransmitterBlockEntity extends TransmitterBlockEntity {
    public static final Spec SPARK_SPEC = new Spec(150f, 1500f, 40f, 3000f,
            new Spec.Part("rf_out", 2000, 0, 1), Spec.pullup(1000f, 1), Spec.rail(5f));

    public SparkTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPARK_SPEC;
    }

    /** a spark set is a coil, a capacitor and a gap: no PA, band module or fuse to fit */
    @Override
    public boolean needsParts() {
        return false;
    }

    @Override
    protected int maxPanelW() {
        return 2000;
    }

    @Override
    protected double paMaxW() {
        return 2000;
    }

    @Override
    protected double paEff() {
        return 0.25;
    }

    @Override
    protected boolean bandOk() {
        return true;
    }

    @Override
    protected double nominalV() {
        return 1000;
    }

    @Override
    protected double minV() {
        return 400;
    }

    @Override
    protected long emitHz() {
        if (link == null || link.amplifier() != null || !Double.isFinite(link.f0MHz())) return freqHz;
        return Math.round(link.f0MHz() * 1e6);
    }

    @Override
    protected double spreadHz() {
        return Math.max(20_000, 0.1 * emitHz());
    }

    @Override
    public long driftHz() {
        return 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level instanceof ServerLevel sl && isKeyed() && forwardW() > 0 && sl.random.nextInt(3) == 0) {
            sl.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR, SoundSource.BLOCKS,
                    0.25f, 1.6f + sl.random.nextFloat() * 0.4f);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5, worldPosition.getY() + 0.65,
                    worldPosition.getZ() + 0.5, 3, 0.15, 0.05, 0.15, 0.05);
        }
    }
}
