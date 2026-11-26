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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.network.chat.Component;
import org.patryk3211.powergrid.electricity.base.ElectricBlock;
import org.patryk3211.powergrid.electricity.base.IDecoratedTerminal;
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

/**
 * Iambic paddle (패들 키): just two contacts, like the real thing.
 * DIT–COMMON closes while the dit paddle is held, DAH–COMMON while the dah paddle is held.
 * Timing comes from a separate keyer. Right-click (empty hand) opens focus mode.
 */
public class PaddleBlock extends ElectricBlock implements IBE<PaddleBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty DIT = BooleanProperty.create("dit");
    public static final BooleanProperty DAH = BooleanProperty.create("dah");

    /** Terminal order: 0 = DIT (operator's left), 1 = COMMON (centre), 2 = DAH (right). */
    static final double[][] TERMINALS = {
            {3, 2, 12, 6, 4, 15},
            {6.5, 2, 12, 9.5, 4, 15},
            {10, 2, 12, 13, 4, 15},
    };
    static final Component[] TERMINAL_NAMES = {
            Component.translatable("pgtelegraph.terminal.dit"),
            IDecoratedTerminal.COMMON,
            Component.translatable("pgtelegraph.terminal.dah"),
    };
    private static final VoxelShape[] SHAPES = Geometry.shapes(new double[][]{
            {3, 0, 3, 13, 2, 15},  // base
            {5, 2, 1, 11, 7, 12},  // paddles, levers, pivot
            TERMINALS[0], TERMINALS[1], TERMINALS[2],
    });

    public PaddleBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(DIT, false).setValue(DAH, false));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(s -> Geometry.terminals(TERMINALS, TERMINAL_NAMES, s.getValue(FACING)))
                .withShapeMapper(s -> SHAPES[s.getValue(FACING).get2DDataValue()])
                .build());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, DIT, DAH);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) return super.useWithoutItem(state, level, pos, player, hit);
        if (level.isClientSide) ClientAccess.openFocus(pos, true);
        return InteractionResult.sidedSuccess(level.isClientSide);
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
    public Class<PaddleBlockEntity> getBlockEntityClass() {
        return PaddleBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PaddleBlockEntity> getBlockEntityType() {
        return PgTelegraph.PADDLE_BE.get();
    }
}
