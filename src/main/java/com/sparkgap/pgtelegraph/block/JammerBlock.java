package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.network.chat.Component;

/** Jammer: the broadcast case, HV supply, INHIBIT / COM in front. */
public class JammerBlock extends TransmitterBlock {
    public JammerBlock(Properties properties) {
        super(properties, new Component[]{
                Component.translatable("pgtelegraph.terminal.hv_pos"),
                Component.translatable("pgtelegraph.terminal.hv_neg"),
                Component.translatable("pgtelegraph.terminal.inhibit"),
                Component.translatable("pgtelegraph.terminal.key_com")}, () -> PgTelegraph.JAMMER_BE.get());
    }
}
