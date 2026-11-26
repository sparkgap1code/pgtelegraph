package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Power distribution panel: IN+ and GND at the back, OUT1-OUT4 along the front. */
public class PowerPanelBlock extends DeviceBlock<PowerPanelBlockEntity> {
    static final double[][] TERMINALS = {
            {2, 4, 8.5, 4.5, 5.5, 11},
            {11.5, 4, 8.5, 14, 5.5, 11},
            {1.5, 4, 4.5, 4, 5.5, 7},
            {5, 4, 4.5, 7.5, 5.5, 7},
            {8.5, 4, 4.5, 11, 5.5, 7},
            {12, 4, 4.5, 14.5, 5.5, 7},
    };

    public PowerPanelBlock(Properties properties) {
        super(properties, TERMINALS, new Component[]{
                        Component.translatable("pgtelegraph.terminal.in_pos"),
                        Component.translatable("pgtelegraph.terminal.gnd"),
                        Component.translatable("pgtelegraph.terminal.out_n", 1),
                        Component.translatable("pgtelegraph.terminal.out_n", 2),
                        Component.translatable("pgtelegraph.terminal.out_n", 3),
                        Component.translatable("pgtelegraph.terminal.out_n", 4)},
                new double[][]{{1, 0, 4, 15, 4, 12}, TERMINALS[0], TERMINALS[1], TERMINALS[2], TERMINALS[3], TERMINALS[4], TERMINALS[5]},
                PowerPanelBlockEntity.class, PgTelegraph.POWER_PANEL_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PowerPanelBlockEntity be)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                be.nextFuse();
                player.displayClientMessage(Component.translatable("pgtelegraph.panel.fuse", be.fuseA()), true);
            } else if (be.replaceFuses())
                player.displayClientMessage(Component.translatable("pgtelegraph.panel.replaced"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
