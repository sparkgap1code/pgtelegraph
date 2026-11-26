package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.TransmitterBlock;
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

/** SSTV transmitter. Photo in hand: put it on (sends at once); sneak + empty hand: take the photo off,
 *  or with no photo send one frame from the camera next to it; empty hand: tune. */
public class SstvTransmitterBlock extends TransmitterBlock {
    public SstvTransmitterBlock(Properties properties) {
        super(properties, new Component[]{
                Component.translatable("pgtelegraph.terminal.pwr_pos"),
                Component.translatable("pgtelegraph.terminal.pwr_neg"),
                Component.translatable("pgtelegraph.terminal.key"),
                Component.translatable("pgtelegraph.terminal.key_com")}, PgTelegraph.SSTV_TX_BE);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.has(PgTelegraph.PHOTO.get()) && level.getBlockEntity(pos) instanceof SstvTransmitterBlockEntity be && !be.hasPhoto()) {
            if (!level.isClientSide) {
                be.insert(stack.copyWithCount(1));
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof SstvTransmitterBlockEntity be) {
            if (!level.isClientSide) {
                if (be.hasPhoto()) {
                    ItemStack d = be.eject();
                    if (!player.getInventory().add(d)) player.drop(d, false);
                } else {
                    player.displayClientMessage(Component.translatable(be.startFrame()
                            ? "pgtelegraph.sstv_tx.sending" : "pgtelegraph.sstv_tx.no_source"), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SstvTransmitterBlockEntity be && be.hasPhoto()) {
            Block.popResource(level, pos, be.eject());
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
