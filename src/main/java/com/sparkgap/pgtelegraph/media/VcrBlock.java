package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.StationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** VCR: tape in hand puts it in; empty hand: STOP / PLAY / LOOP / REC; sneak + empty hand: take the tape out. */
public class VcrBlock extends StationBlock<VcrBlockEntity> {
    public VcrBlock(Properties p) {
        super(p, new double[]{1, 0, 2, 15, 5, 14}, VcrBlockEntity.class, () -> PgTelegraph.VCR_BE.get(), (be, player, level, pos) -> {
            if (level.isClientSide) return true;
            if (player.isShiftKeyDown()) {
                if (be.hasTape()) {
                    ItemStack t = be.eject();
                    if (!player.getInventory().add(t)) player.drop(t, false);
                }
                return true;
            }
            String m = be.cycle();
            if (m != null) player.displayClientMessage(Component.literal("VCR: " + m), true);
            else player.displayClientMessage(Component.translatable("pgtelegraph.vcr.no_tape"), true);
            return true;
        });
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof VideoTapeItem && level.getBlockEntity(pos) instanceof VcrBlockEntity be && !be.hasTape()) {
            if (!level.isClientSide) {
                be.insert(stack);
                stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VcrBlockEntity be && be.hasTape())
            Block.popResource(level, pos, be.eject());
        super.onRemove(state, level, pos, newState, moving);
    }
}
