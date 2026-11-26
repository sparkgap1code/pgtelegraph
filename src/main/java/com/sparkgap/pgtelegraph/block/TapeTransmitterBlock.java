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

/**
 * Automatic transmitter (자동 송신기). Right-click with tape: load and start.
 * Empty hand: pause / resume. Sneak + empty hand: take the tape out.
 */
public class TapeTransmitterBlock extends DeviceBlock<TapeTransmitterBlockEntity> {
    public static final BooleanProperty HAS_TAPE = InkerBlock.HAS_TAPE;

    static final double[][] TERMINALS = {
            {3, 2, 12, 6, 4, 15},
            {10, 2, 12, 13, 4, 15},
    };

    public TapeTransmitterBlock(Properties properties) {
        super(properties, TERMINALS,
                new Component[]{
                        Component.translatable("pgtelegraph.terminal.out"),
                        Component.translatable("pgtelegraph.terminal.out")},
                new double[][]{{1, 0, 2, 15, 2, 15}, {3, 2, 6, 13, 9, 12}, TERMINALS[0], TERMINALS[1]},
                TapeTransmitterBlockEntity.class, PgTelegraph.TAPE_TRANSMITTER_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HAS_TAPE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HAS_TAPE);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof PaperTapeItem)) return super.useItemOn(stack, state, level, pos, player, hand, hit);
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof TapeTransmitterBlockEntity be) {
            if (be.insert(stack.copyWithCount(1))) {
                stack.shrink(1);
                level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 1.2f);
            } else {
                player.displayClientMessage(Component.translatable(
                        be.hasTape() ? "pgtelegraph.tape.occupied" : "pgtelegraph.tape.blank_rejected"), true);
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TapeTransmitterBlockEntity be) || !be.hasTape()) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                TapeBlocks.give(player, be.extract());
                level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 0.9f);
            } else {
                be.toggle();
                player.displayClientMessage(Component.translatable(
                        be.isRunning() ? "pgtelegraph.transmitter.running" : "pgtelegraph.transmitter.paused"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TapeTransmitterBlockEntity be) {
            Block.popResource(level, pos, be.takeTape());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
