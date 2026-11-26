package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Oscilloscope. Empty hand: open the screen. Probe terminals on top, at the back. */
public class OscilloscopeBlock extends DeviceBlock<OscilloscopeBlockEntity> {
    static final double[][] TERMINALS = {
            {11, 9, 11, 13, 11, 13},
            {8.5, 9, 11, 10.5, 11, 13},
            {5.5, 9, 11, 7.5, 11, 13},
            {3, 9, 11, 5, 11, 13},
    };

    public OscilloscopeBlock(Properties properties) {
        super(properties, TERMINALS, new Component[]{
                        Component.literal("CH1 +"), Component.literal("CH1 -"),
                        Component.literal("CH2 +"), Component.literal("CH2 -")},
                new double[][]{{2, 0, 3, 14, 9, 14}, TERMINALS[0], TERMINALS[1], TERMINALS[2], TERMINALS[3]},
                OscilloscopeBlockEntity.class, PgTelegraph.OSCILLOSCOPE_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) ClientAccess.openScope(pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
