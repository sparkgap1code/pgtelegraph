package com.sparkgap.pgtelegraph.broadcast;

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

/** AM broadcast transmitter. Record in hand: put it on; sneak + empty hand: take it off; empty hand: tune. */
public class BroadcastTransmitterBlock extends TransmitterBlock {
    public BroadcastTransmitterBlock(Properties properties) {
        this(properties, PgTelegraph.BROADCAST_TX_BE);
    }

    /** the FM set shares the case, terminals and record deck */
    public BroadcastTransmitterBlock(Properties properties,
                                     java.util.function.Supplier<? extends net.minecraft.world.level.block.entity.BlockEntityType<? extends BroadcastTransmitterBlockEntity>> type) {
        super(properties, new Component[]{
                Component.translatable("pgtelegraph.terminal.hv_pos"),
                Component.translatable("pgtelegraph.terminal.hv_neg"),
                Component.translatable("pgtelegraph.terminal.inhibit"),
                Component.translatable("pgtelegraph.terminal.key_com")}, type);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof BroadcastTransmitterBlockEntity be && !be.full()) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            if (be.insert(stack)) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("pgtelegraph.broadcast.queued", be.records(),
                        BroadcastTransmitterBlockEntity.RACK), true);
                return ItemInteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof BroadcastTransmitterBlockEntity be && be.hasDisc()) {
            if (!level.isClientSide) {
                ItemStack d = be.eject();
                if (!player.getInventory().add(d)) player.drop(d, false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BroadcastTransmitterBlockEntity be && be.hasDisc()) {
            for (ItemStack d : be.ejectAll()) Block.popResource(level, pos, d);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
