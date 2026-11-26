package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;

/** Tone buzzer (부저): sounds a steady tone while current flows; pitch set on its front panel. */
public class BuzzerBlock extends DeviceBlock<BuzzerBlockEntity> {
    static final double[][] TERMINALS = {
            {3, 1.5, 12, 6, 3.5, 15},
            {10, 1.5, 12, 13, 3.5, 15},
    };

    public BuzzerBlock(Properties properties) {
        super(properties, TERMINALS,
                new Component[]{IDecoratedTerminal.CONNECTOR, IDecoratedTerminal.CONNECTOR},
                new double[][]{{3, 0, 3, 13, 1.5, 15}, {4, 1.5, 4, 12, 7, 11}, TERMINALS[0], TERMINALS[1]},
                BuzzerBlockEntity.class, PgTelegraph.BUZZER_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(CoilDeviceBlockEntity.ENERGIZED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CoilDeviceBlockEntity.ENERGIZED);
    }
}
