package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.tape.PaperTapeItem;
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

/** Morse inker (잉커). Right-click with paper tape to load it; empty hand takes the tape out. */
public class InkerBlock extends DeviceBlock<InkerBlockEntity> {
    public static final BooleanProperty HAS_TAPE = BooleanProperty.create("has_tape");

    static final double[][] TERMINALS = {
            {3, 2, 12, 6, 4, 15},
            {10, 2, 12, 13, 4, 15},
    };

    public InkerBlock(Properties properties) {
        super(properties, TERMINALS,
                new Component[]{IDecoratedTerminal.CONNECTOR, IDecoratedTerminal.CONNECTOR},
                new double[][]{{1, 0, 2, 15, 2, 15}, {3, 2, 3, 14, 8, 11}, TERMINALS[0], TERMINALS[1]},
                InkerBlockEntity.class, PgTelegraph.INKER_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(CoilDeviceBlockEntity.ENERGIZED, false).setValue(HAS_TAPE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CoilDeviceBlockEntity.ENERGIZED, HAS_TAPE);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof PaperTapeItem)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof InkerBlockEntity be) {
            ItemStack one = stack.copyWithCount(1);
            if (be.insert(one)) {
                stack.shrink(1);
                level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 1.2f);
            } else {
                player.displayClientMessage(Component.translatable(
                        be.hasTape() ? "pgtelegraph.tape.occupied" : "pgtelegraph.tape.inker_rejects"), true);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof InkerBlockEntity be) || !be.hasTape()) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!level.isClientSide) {
            TapeBlocks.give(player, be.extract());
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 0.9f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof InkerBlockEntity be) {
            Block.popResource(level, pos, be.takeTape());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
