package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Antenna element: 1 block = 1 m of conductor. Stand them up from the feed for a vertical
 * (resonant near 75 / L MHz), or lay them in a line through the feed for a dipole (150 / L MHz).
 * The feed is the element the coax (or the radio) touches.
 */
public class AntennaRodBlock extends RotatedPillarBlock {
    private static final VoxelShape Y = Block.box(7, 0, 7, 9, 16, 9);
    private static final VoxelShape X = Block.box(0, 7, 7, 16, 9, 9);
    private static final VoxelShape Z = Block.box(7, 7, 0, 9, 9, 16);

    public AntennaRodBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(AXIS)) {
            case X -> X;
            case Z -> Z;
            default -> Y;
        };
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        // continue the element you click on, otherwise follow the clicked face like logs do
        BlockState against = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(ctx.getClickedFace().getOpposite()));
        if (against.getBlock() instanceof AntennaRodBlock && against.getValue(AXIS) == ctx.getClickedFace().getAxis()) {
            return defaultBlockState().setValue(AXIS, against.getValue(AXIS));
        }
        Direction.Axis axis = ctx.getClickedFace().getAxis();
        return defaultBlockState().setValue(AXIS, axis);
    }
}
