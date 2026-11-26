package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** Battery management system (BMS). Pack posts at the back, load posts in front. */
public class BmsBlock extends DeviceBlock<BmsBlockEntity> {
    static final double[][] TERMINALS = {
            {10, 6, 11, 13, 8, 14},   // BAT+
            {3, 6, 11, 6, 8, 14},     // BAT-
            {10, 6, 4, 13, 8, 7},     // LOAD+
            {3, 6, 4, 6, 8, 7},       // LOAD-
    };

    public BmsBlock(Properties properties) {
        super(properties, TERMINALS, new Component[]{
                        Component.translatable("pgtelegraph.terminal.bat_pos"),
                        Component.translatable("pgtelegraph.terminal.bat_neg"),
                        Component.translatable("pgtelegraph.terminal.load_pos"),
                        Component.translatable("pgtelegraph.terminal.load_neg")},
                new double[][]{{2, 0, 3, 14, 6, 14}, TERMINALS[0], TERMINALS[1], TERMINALS[2], TERMINALS[3]},
                BmsBlockEntity.class, PgTelegraph.BMS_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }
}
