package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Morse decoder (자동 해독기). Empty hand: show the text; sneak + empty hand: English/Korean;
 * book and quill: copy the text into it (and clear the buffer).
 */
public class DecoderBlock extends DeviceBlock<DecoderBlockEntity> {
    static final double[][] TERMINALS = {
            {3, 6, 11, 6, 8, 14},
            {10, 6, 11, 13, 8, 14},
    };
    private static final int PAGE_CHARS = 250;

    public DecoderBlock(Properties properties) {
        super(properties, TERMINALS,
                new Component[]{Component.translatable("pgtelegraph.terminal.line"), Component.translatable("pgtelegraph.terminal.line")},
                new double[][]{{2, 0, 3, 14, 6, 14}, {3, 6, 4, 13, 9, 9}, TERMINALS[0], TERMINALS[1]},
                DecoderBlockEntity.class, PgTelegraph.DECODER_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(CoilDeviceBlockEntity.ENERGIZED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CoilDeviceBlockEntity.ENERGIZED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DecoderBlockEntity be)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                be.toggleLanguage();
                player.displayClientMessage(Component.literal(be.korean() ? "한국어 (KO)" : "English (EN)"), true);
            } else {
                String t = be.text();
                player.displayClientMessage(Component.literal(t.isEmpty() ? "-" : t.length() > 400 ? "..." + t.substring(t.length() - 400) : t)
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.WRITABLE_BOOK) || !(level.getBlockEntity(pos) instanceof DecoderBlockEntity be)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide) {
            String t = be.text();
            if (t.isEmpty()) return ItemInteractionResult.SUCCESS;
            WritableBookContent book = stack.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
            List<Filterable<String>> pages = new ArrayList<>(book.pages());
            for (int i = 0; i < t.length() && pages.size() < WritableBookContent.MAX_PAGES; i += PAGE_CHARS) {
                pages.add(Filterable.passThrough(t.substring(i, Math.min(t.length(), i + PAGE_CHARS))));
            }
            stack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages));
            be.clear();
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
