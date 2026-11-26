package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.TransmitterBlock;
import net.minecraft.network.chat.Component;

/** Analog TV transmitter: the broadcast case with HV terminals and INHIBIT; a TV camera beside it is the picture. */
public class TvTransmitterBlock extends TransmitterBlock {
    public TvTransmitterBlock(Properties properties) {
        super(properties, new Component[]{
                Component.translatable("pgtelegraph.terminal.hv_pos"),
                Component.translatable("pgtelegraph.terminal.hv_neg"),
                Component.translatable("pgtelegraph.terminal.inhibit"),
                Component.translatable("pgtelegraph.terminal.key_com")}, () -> PgTelegraph.TV_TX_BE.get());
    }
}
