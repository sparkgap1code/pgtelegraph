package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * A self-contained station box (no Power Grid terminals): horizontal facing, coax on any side,
 * one block entity, and an empty-hand action.
 */
public class StationBlock<T extends BlockEntity> extends Block implements IBE<T>, RfPort {
    @FunctionalInterface
    public interface Use<T> {
        /** called on both sides; return true when handled */
        boolean use(T be, Player player, Level level, BlockPos pos);
    }

    private final VoxelShape[] shapes;
    private final Class<T> cls;
    private final Supplier<? extends BlockEntityType<? extends T>> type;
    private final Use<T> use;

    public StationBlock(Properties p, double[] box, Class<T> cls, Supplier<? extends BlockEntityType<? extends T>> type, Use<T> use) {
        super(p);
        this.shapes = Geometry.shapes(new double[][]{box});
        this.cls = cls;
        this.type = type;
        this.use = use;
        registerDefaultState(defaultBlockState().setValue(DeviceBlock.FACING, Direction.NORTH).setValue(Rack.RACKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DeviceBlock.FACING, Rack.RACKED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(DeviceBlock.FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape s = shapes[state.getValue(DeviceBlock.FACING).get2DDataValue()];
        if (Rack.racked(state)) return net.minecraft.world.phys.shapes.Shapes.or(s, Rack.POSTS);
        return s;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (use == null || !cls.isInstance(be)) return InteractionResult.PASS;
        return use.use((T) be, player, level, pos) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    @Override
    public Class<T> getBlockEntityClass() {
        return cls;
    }

    @Override
    public BlockEntityType<? extends T> getBlockEntityType() {
        return type.get();
    }
}
