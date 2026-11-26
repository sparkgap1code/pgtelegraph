package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

import java.util.function.Supplier;

/**
 * Common base for small table-top telegraph devices: horizontal facing (front toward the player
 * who placed it), must stand on a solid block, terminals/shape authored facing NORTH in pixels.
 */
public abstract class DeviceBlock<T extends BlockEntity> extends ElectricBlock implements IBE<T> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final VoxelShape[] shapes;
    private final Class<T> beClass;
    private final Supplier<? extends BlockEntityType<? extends T>> beType;

    protected DeviceBlock(Properties properties, double[][] terminals, Component[] names, double[][] shapeBoxes,
                          Class<T> beClass, Supplier<? extends BlockEntityType<? extends T>> beType) {
        super(properties);
        this.shapes = Geometry.shapes(shapeBoxes);
        this.beClass = beClass;
        this.beType = beType;
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(s -> Geometry.terminals(Rack.racked(s) ? Rack.rearTerminals(terminals) : terminals, names, s.getValue(FACING)))
                .withShapeMapper(s -> shapes[s.getValue(FACING).get2DDataValue()])
                .build());
        registerDefaultState(defaultBlockState().setValue(Rack.RACKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, Rack.RACKED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape s = shapes[state.getValue(FACING).get2DDataValue()];
        return Rack.racked(state) ? net.minecraft.world.phys.shapes.Shapes.or(s, Rack.POSTS) : s;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (Rack.racked(state)) return true; // held by the rack frame
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.DOWN && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public Class<T> getBlockEntityClass() {
        return beClass;
    }

    @Override
    public BlockEntityType<? extends T> getBlockEntityType() {
        return beType.get();
    }
}
