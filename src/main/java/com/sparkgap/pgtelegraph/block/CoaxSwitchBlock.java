package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Coax switch (동축 스위치): the common port faces you when placed (its back), port A is on its
 * left, port B on its right. Without redstone A is connected, with redstone B. The other sides
 * take no coax.
 */
public class CoaxSwitchBlock extends Block implements RfPort {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 6, 13);

    public CoaxSwitchBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(POWERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        boolean p = level.hasNeighborSignal(pos);
        if (p != state.getValue(POWERED)) level.setBlock(pos, state.setValue(POWERED, p), 3);
    }

    public static Direction common(BlockState s) {
        return s.getValue(FACING);
    }

    /** the port in use: A (left, facing counter-clockwise) or B (right) */
    public static Direction selected(BlockState s) {
        Direction f = s.getValue(FACING);
        return s.getValue(POWERED) ? f.getClockWise() : f.getCounterClockWise();
    }

    /** where a signal entering through {@code entry} leaves, or null if that side is not connected */
    public static Direction through(BlockState s, Direction entry) {
        if (entry == common(s)) return selected(s);
        if (entry == selected(s)) return common(s);
        return null;
    }
}
