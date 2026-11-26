package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;

/** Telegraph sounder (음향기): 20 Ω coil; armature clicks down on pull-in, clacks up on release. */
public class SounderBlock extends DeviceBlock<SounderBlockEntity> {
    static final double[][] TERMINALS = {
            {3, 2, 12, 6, 4, 15},
            {10, 2, 12, 13, 4, 15},
    };

    public SounderBlock(Properties properties) {
        super(properties, TERMINALS,
                new Component[]{IDecoratedTerminal.CONNECTOR, IDecoratedTerminal.CONNECTOR},
                new double[][]{{2, 0, 2, 14, 2, 15}, {3, 2, 3, 13, 10, 12}, TERMINALS[0], TERMINALS[1]},
                SounderBlockEntity.class, PgTelegraph.SOUNDER_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(CoilDeviceBlockEntity.ENERGIZED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CoilDeviceBlockEntity.ENERGIZED);
    }
}
