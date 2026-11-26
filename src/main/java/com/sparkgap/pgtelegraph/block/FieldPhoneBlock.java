package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** Field telephone: L1 / L2 on top at the back. Empty hand: handset up / down; sneak + empty hand: crank the magneto. */
public class FieldPhoneBlock extends DeviceBlock<FieldPhoneBlockEntity> {
    static final double[][] TERMINALS = {
            {3.5, 6, 10, 6.5, 7.5, 13},
            {9.5, 6, 10, 12.5, 7.5, 13},
    };

    public FieldPhoneBlock(Properties p) {
        super(p, TERMINALS, new Component[]{Component.translatable("pgtelegraph.terminal.l1"), Component.translatable("pgtelegraph.terminal.l2")},
                new double[][]{{3, 0, 4, 13, 6, 13}, TERMINALS[0], TERMINALS[1]}, FieldPhoneBlockEntity.class, PgTelegraph.FIELD_PHONE_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(FieldPhoneBlockEntity.OFF_HOOK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FieldPhoneBlockEntity.OFF_HOOK);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FieldPhoneBlockEntity be)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) be.crank();
            else {
                be.setHook(!be.offHook());
                if (be.offHook()) player.displayClientMessage(Component.translatable("pgtelegraph.phone.talk_hint"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
