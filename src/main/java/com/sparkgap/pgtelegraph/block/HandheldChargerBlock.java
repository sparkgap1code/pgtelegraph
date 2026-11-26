package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.radio.HandheldRadioItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;

/** Handheld radio charger (무전기 충전기). Right-click with the radio to dock it, empty hand to take it. */
public class HandheldChargerBlock extends DeviceBlock<HandheldChargerBlockEntity> implements org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord {
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");
    static final double[][] TERMINALS = {{3, 1, 11, 6, 3, 14}, {10, 1, 11, 13, 3, 14}};

    public HandheldChargerBlock(Properties properties) {
        super(properties, TERMINALS, new Component[]{IDecoratedTerminal.POSITIVE, IDecoratedTerminal.NEGATIVE},
                new double[][]{{3, 0, 4, 13, 4, 11}, TERMINALS[0], TERMINALS[1]},
                HandheldChargerBlockEntity.class, PgTelegraph.HANDHELD_CHARGER_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(LOADED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOADED);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        boolean ok = stack.getItem() instanceof HandheldRadioItem || (com.sparkgap.pgtelegraph.parts.PartItem.def(stack) != null
                && com.sparkgap.pgtelegraph.parts.PartItem.def(stack).slot() == com.sparkgap.pgtelegraph.parts.PartSlot.BATTERY);
        if (!ok) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HandheldChargerBlockEntity be && be.insert(stack)) {
            stack.shrink(1);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.4f);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HandheldChargerBlockEntity be) || !be.hasRadio()) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!level.isClientSide) {
            TapeBlocks.give(player, be.take());
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HandheldChargerBlockEntity be && be.hasRadio()) {
            Block.popResource(level, pos, be.takeRaw());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
