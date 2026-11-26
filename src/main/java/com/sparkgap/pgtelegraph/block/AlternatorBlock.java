package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.block.IBE;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;
import org.patryk3211.powergrid.kinetics.base.ElectricKineticBlock;

/**
 * Alexanderson alternator (알렉산더슨 교류발전기). Driven by a Create shaft at the back; the output
 * frequency is set by the shaft speed (256 RPM = 17.2 kHz, the Grimeton frequency). Key terminals
 * on top switch the field excitation. RF leaves through coax on any other side.
 */
public class AlternatorBlock extends ElectricKineticBlock implements IBE<AlternatorBlockEntity>, RfPort {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    static final double[][] TERMINALS = {
            {9, 14, 2.5, 12, 16, 5.5},  // KEY
            {4, 14, 2.5, 7, 16, 5.5},   // KEY COM
    };
    private static final VoxelShape[] SHAPES = Geometry.shapes(new double[][]{{0, 0, 1, 16, 14, 16}, TERMINALS[0], TERMINALS[1]});

    public AlternatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
        Component[] names = {Component.translatable("pgtelegraph.terminal.key"), Component.translatable("pgtelegraph.terminal.key_com")};
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(s -> Geometry.terminals(TERMINALS, names, s.getValue(FACING)))
                .withShapeMapper(s -> SHAPES[s.getValue(FACING).get2DDataValue()])
                .build());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
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
    public Class<AlternatorBlockEntity> getBlockEntityClass() {
        return AlternatorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AlternatorBlockEntity> getBlockEntityType() {
        return PgTelegraph.ALTERNATOR_BE.get();
    }
}
