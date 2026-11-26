package com.sparkgap.pgtelegraph.sat;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Launch pad (발사대). Faces the way you look when placing it - that is the launch azimuth.
 * Rocket, then CubeSat onto it; empty hand (or a redstone pulse) starts the countdown, sneak +
 * empty hand takes them back off.
 */
public class LaunchPadBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");

    public LaunchPadBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(LOADED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOADED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(PgTelegraph.LAUNCH_PAD_BE.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != PgTelegraph.LAUNCH_PAD_BE.get() ? null
                : (l, p, s, be) -> ((LaunchPadBlockEntity) be).serverTick();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity be) be.redstone(level.hasNeighborSignal(pos));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity be) || be.busy()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int kind = stack.is(PgTelegraph.ROCKET.get()) ? 1 : stack.is(PgTelegraph.ROCKET_MEDIUM.get()) ? 2
                : stack.is(PgTelegraph.ROCKET_HEAVY.get()) ? 3 : 0;
        if (kind > 0 && !be.hasRocket()) {
            if (!level.isClientSide) {
                be.loadRocket(kind);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.getItem() instanceof CubeSatItem && be.payload().isEmpty()) {
            if (!be.hasRocket()) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("pgtelegraph.launch.need_rocket")
                        .withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                be.loadPayload(stack);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity be) || be.busy()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                for (ItemStack s : be.unload()) if (!s.isEmpty() && !player.getInventory().add(s)) player.drop(s, false);
            } else if (!be.start()) {
                player.displayClientMessage(Component.translatable("pgtelegraph.launch.not_ready").withStyle(ChatFormatting.RED), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LaunchPadBlockEntity be && !be.busy()) {
            for (ItemStack s : be.contents()) if (!s.isEmpty()) Block.popResource(level, pos, s);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
