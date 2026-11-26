package com.sparkgap.pgtelegraph.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * FM broadcast transmitter (FM 방송 송신기): the VHF sister of the AM set. Same record deck, same
 * microphone and INHIBIT, same 1 kW tube PA on 250 V - but frequency-modulated (75 kHz deviation,
 * 75 us pre-emphasis) and built for the FM broadcast band, 87.5-108 MHz, so it needs no band
 * module. Being VHF it reaches as far as line of sight: put the antenna high.
 */
public class FmBroadcastTransmitterBlockEntity extends BroadcastTransmitterBlockEntity {
    public static final long LOW_HZ = 87_500_000L, HIGH_HZ = 108_000_000L;

    public FmBroadcastTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        freqHz = 98_100_000L;
    }

    @Override
    public boolean voiceFm() {
        return true;
    }

    /** broadcast FM: wide (+-75 kHz), not the narrow FM of a two-way set */
    @Override
    public int airModulation() {
        return com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_WFM;
    }

    // the band: the AM transmitter's setFreq / bandOk use these (it used to clamp FM into the AM band)
    @Override
    protected long lowHz() {
        return LOW_HZ;
    }

    @Override
    protected long highHz() {
        return HIGH_HZ;
    }

    @Override
    protected double paEff() {
        return 0.65; // class-C at VHF is a little less efficient than at MF
    }
}
