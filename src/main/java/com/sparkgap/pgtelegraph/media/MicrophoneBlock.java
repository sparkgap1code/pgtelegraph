package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.block.DeviceBlock;
import com.sparkgap.pgtelegraph.block.Geometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Desk microphone (마이크). Put it against a transmitter set to AM or FM, or a broadcast
 * transmitter; stand within 4 blocks and hold the push-to-talk key (default V) to talk on the air
 * with your real microphone.
 */
public class MicrophoneBlock extends Block {
    private static final VoxelShape[] SHAPES = Geometry.shapes(new double[][]{{5, 0, 5, 11, 10, 11}});

    public MicrophoneBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(DeviceBlock.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DeviceBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(DeviceBlock.FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(DeviceBlock.FACING).get2DDataValue()];
    }
}
