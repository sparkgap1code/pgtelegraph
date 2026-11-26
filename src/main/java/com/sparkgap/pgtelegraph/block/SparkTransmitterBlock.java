package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.network.chat.Component;

/** Spark-gap transmitter: same case and terminals as the CW transmitter, high-voltage supply. */
public class SparkTransmitterBlock extends TransmitterBlock {
    public SparkTransmitterBlock(Properties properties) {
        super(properties, new Component[]{
                Component.translatable("pgtelegraph.terminal.hv_pos"),
                Component.translatable("pgtelegraph.terminal.hv_neg"),
                Component.translatable("pgtelegraph.terminal.key"),
                Component.translatable("pgtelegraph.terminal.key_com")}, PgTelegraph.SPARK_TRANSMITTER_BE);
    }
}
