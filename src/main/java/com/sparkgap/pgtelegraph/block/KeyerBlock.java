package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.block.IBE;
import com.sparkgap.pgtelegraph.PgTelegraph;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

/**
 * Electronic iambic keyer (전자 키어). Rear row: paddle inputs DIT / COM / DAH (5 V pull-ups inside,
 * the paddle shorts an input to COM). Front row: keyed output, a switch between OUT1 and OUT2.
 */
public class KeyerBlock extends ElectricBlock implements IBE<KeyerBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty KEYED = BooleanProperty.create("keyed");

    /** 0 DIT, 1 COM, 2 DAH (rear, top), 3 OUT1, 4 OUT2 (front, top). Authored facing NORTH. */
    static final double[][] TERMINALS = {
            {3, 7, 10, 6, 9, 13},
            {6.5, 7, 10, 9.5, 9, 13},
            {10, 7, 10, 13, 9, 13},
            {4, 7, 4, 7, 9, 7},
            {9, 7, 4, 12, 9, 7},
    };
    static final Component[] TERMINAL_NAMES = {
            Component.translatable("pgtelegraph.terminal.dit"),
            IDecoratedTerminal.COMMON,
            Component.translatable("pgtelegraph.terminal.dah"),
            Component.translatable("pgtelegraph.terminal.out"),
            Component.translatable("pgtelegraph.terminal.out"),
    };
    private static final VoxelShape[] SHAPES = Geometry.shapes(new double[][]{
            {2, 0, 3, 14, 7, 14},
            TERMINALS[0], TERMINALS[1], TERMINALS[2], TERMINALS[3], TERMINALS[4],
    });

    public KeyerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(KEYED, false));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(s -> Geometry.terminals(TERMINALS, TERMINAL_NAMES, s.getValue(FACING)))
                .withShapeMapper(s -> SHAPES[s.getValue(FACING).get2DDataValue()])
                .build());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, KEYED);
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
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
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
    public Class<KeyerBlockEntity> getBlockEntityClass() {
        return KeyerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends KeyerBlockEntity> getBlockEntityType() {
        return PgTelegraph.KEYER_BE.get();
    }
}
