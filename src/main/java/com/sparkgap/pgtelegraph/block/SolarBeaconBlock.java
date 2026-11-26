package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.block.IBE;
import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Solar beacon. Empty hand: tune; sneak + empty hand: program. Needs open sky above. */
public class SolarBeaconBlock extends Block implements IBE<SolarBeaconBlockEntity>, RfPort {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 7, 14);

    public SolarBeaconBlock(Properties properties) {
        super(properties);
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
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SolarBeaconBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) {
            if (player.isShiftKeyDown()) ClientAccess.openBeacon(pos, be.program());
            else ClientAccess.openTune(pos, be.freqHz());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<SolarBeaconBlockEntity> getBlockEntityClass() {
        return SolarBeaconBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SolarBeaconBlockEntity> getBlockEntityType() {
        return PgTelegraph.SOLAR_BEACON_BE.get();
    }
}
