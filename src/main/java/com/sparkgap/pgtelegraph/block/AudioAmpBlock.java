package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** Audio amplifier: PWR+ / PWR- at the back, the speaker line (SPK) at the front; the radio set touches it. */
public class AudioAmpBlock extends DeviceBlock<AudioAmpBlockEntity> implements org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord {
    public AudioAmpBlock(Properties properties) {
        super(properties, RadioTerminals.BOXES, new Component[]{
                        Component.translatable("pgtelegraph.terminal.pwr_pos"),
                        Component.translatable("pgtelegraph.terminal.pwr_neg"),
                        Component.literal("SPK 1"), Component.literal("SPK 2")},
                RadioTerminals.shape(), AudioAmpBlockEntity.class, PgTelegraph.AUDIO_AMP_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }
}
