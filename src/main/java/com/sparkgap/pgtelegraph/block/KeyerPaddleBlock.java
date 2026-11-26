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
import org.patryk3211.powergrid.electricity.base.terminals.BlockStateTerminalCollection;

/**
 * Keyer paddle (전자식 패들): a paddle with an iambic keyer built into its base, powered by an
 * internal cell. Two output terminals behind the levers close in Morse timing, like the keyer
 * block's OUT1/OUT2. Right-click (empty hand) opens focus mode; speed is set on the back.
 */
public class KeyerPaddleBlock extends ElectricBlock implements IBE<KeyerPaddleBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty DIT = PaddleBlock.DIT;
    public static final BooleanProperty DAH = PaddleBlock.DAH;
    public static final BooleanProperty KEYED = KeyerBlock.KEYED;

    /** Terminal order: 0 = OUT, 1 = OUT (on top of the keyer case, behind the levers). */
    static final double[][] TERMINALS = {
            {3.5, 5, 12, 6.5, 6.5, 14.5},
            {9.5, 5, 12, 12.5, 6.5, 14.5},
    };
    static final Component[] TERMINAL_NAMES = {
            Component.translatable("pgtelegraph.terminal.out"),
            Component.translatable("pgtelegraph.terminal.out"),
    };
    private static final VoxelShape[] SHAPES = Geometry.shapes(new double[][]{
            {3, 0, 3, 13, 2, 15},  // base
            {3, 2, 10, 13, 5, 15}, // keyer case
            {5, 2, 1, 11, 7, 12},  // paddles, levers, pivot
            TERMINALS[0], TERMINALS[1],
    });

    public KeyerPaddleBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(DIT, false).setValue(DAH, false).setValue(KEYED, false));
        setTerminalCollection(BlockStateTerminalCollection.builder(this)
                .forAllStates(s -> Geometry.terminals(TERMINALS, TERMINAL_NAMES, s.getValue(FACING)))
                .withShapeMapper(s -> SHAPES[s.getValue(FACING).get2DDataValue()])
                .build());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, DIT, DAH, KEYED);
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
    public Class<KeyerPaddleBlockEntity> getBlockEntityClass() {
        return KeyerPaddleBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends KeyerPaddleBlockEntity> getBlockEntityType() {
        return PgTelegraph.KEYER_PADDLE_BE.get();
    }
}
