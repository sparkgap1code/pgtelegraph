package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.block.DeviceBlock;
import com.sparkgap.pgtelegraph.block.Geometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * TV camera (TV 카메라): looks the way you faced when you placed it. Empty hand: tilt it
 * (-45° .. +45° in 22.5° steps). Next to an SSTV transmitter it is the live picture source.
 */
public class TvCameraBlock extends Block {
    public static final IntegerProperty TILT = IntegerProperty.create("tilt", 0, 4);
    private static final VoxelShape[] SHAPES = Geometry.shapes(new double[][]{{4, 0, 2, 12, 9, 14}});

    public TvCameraBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(DeviceBlock.FACING, Direction.NORTH).setValue(TILT, 2));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DeviceBlock.FACING, TILT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(DeviceBlock.FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(DeviceBlock.FACING).get2DDataValue()];
    }

    /** degrees above the horizon */
    public static double elevation(BlockState s) {
        return (s.getValue(TILT) - 2) * 22.5;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState n = state.setValue(TILT, (state.getValue(TILT) + 1) % 5);
            level.setBlock(pos, n, 3);
            player.displayClientMessage(Component.translatable("pgtelegraph.tv_camera.tilt",
                    String.format(java.util.Locale.ROOT, "%+.1f°", elevation(n))), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** what the camera at {@code pos} sees */
    public static CameraRender.View view(Level level, BlockPos pos, BlockState s) {
        Direction f = s.getValue(DeviceBlock.FACING);
        Vec3 eye = pos.getCenter().add(f.getStepX() * 0.55, 0.05, f.getStepZ() * 0.55);
        return new CameraRender.View(level, eye, f.toYRot(), (float) -elevation(s), null);
    }
}
