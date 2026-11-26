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

/** Teleprinter (텔레타이프). Empty hand: keyboard and paper. TX pair left-back, RX pair right-back. */
public class TeleprinterBlock extends DeviceBlock<TeleprinterBlockEntity> {
    static final double[][] TERMINALS = {
            {12, 7, 12, 14, 9, 14},
            {9.5, 7, 12, 11.5, 9, 14},
            {4.5, 7, 12, 6.5, 9, 14},
            {2, 7, 12, 4, 9, 14},
    };

    public TeleprinterBlock(Properties properties) {
        super(properties, TERMINALS, new Component[]{
                        Component.literal("TX 1"), Component.literal("TX 2"),
                        Component.literal("RX 1"), Component.literal("RX 2")},
                new double[][]{{1, 0, 2, 15, 7, 15}, {2, 7, 3, 14, 7.5, 8}, {3, 7, 9, 13, 10, 11.5}, {4, 10, 9.5, 12, 12.5, 10}, TERMINALS[0], TERMINALS[1], TERMINALS[2], TERMINALS[3]},
                TeleprinterBlockEntity.class, PgTelegraph.TELEPRINTER_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) ClientAccess.openTeleprinter(pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
