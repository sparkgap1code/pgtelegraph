package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.network.chat.Component;

/** Repeater: transmitter case; empty hand tunes the input frequency. */
public class RepeaterBlock extends TransmitterBlock {
    public RepeaterBlock(Properties properties) {
        super(properties, new Component[]{
                Component.translatable("pgtelegraph.terminal.pwr_pos"),
                Component.translatable("pgtelegraph.terminal.pwr_neg"),
                Component.translatable("pgtelegraph.terminal.inhibit"),
                Component.translatable("pgtelegraph.terminal.key_com")}, PgTelegraph.REPEATER_BE);
    }
}
