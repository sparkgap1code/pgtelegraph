package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Telegraph line relay (전신 계전기). Rear terminals: 150 Ω line coil. Front terminals: a contact
 * that closes while the coil is pulled in, used to switch a local battery + sounder circuit.
 */
public class RelayBlock extends DeviceBlock<RelayBlockEntity> {
    static final double[][] TERMINALS = {
            {3, 2, 12, 6, 4, 15},   // 0 coil
            {10, 2, 12, 13, 4, 15}, // 1 coil
            {3, 2, 1, 6, 4, 4},     // 2 contact
            {10, 2, 1, 13, 4, 4},   // 3 contact
    };

    public RelayBlock(Properties properties) {
        super(properties, TERMINALS,
                new Component[]{
                        Component.translatable("pgtelegraph.terminal.coil"),
                        Component.translatable("pgtelegraph.terminal.coil"),
                        Component.translatable("pgtelegraph.terminal.contact"),
                        Component.translatable("pgtelegraph.terminal.contact")},
                new double[][]{{2, 0, 1, 14, 2, 15}, {4, 2, 5, 12, 8, 12}, TERMINALS[0], TERMINALS[1], TERMINALS[2], TERMINALS[3]},
                RelayBlockEntity.class, PgTelegraph.RELAY_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(CoilDeviceBlockEntity.ENERGIZED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CoilDeviceBlockEntity.ENERGIZED);
    }
}
