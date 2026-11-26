package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Dummy load (더미 로드): a 50 Ω resistor in an oil can. Plug coax into it instead of an antenna
 * to test a transmitter on the air without radiating (it leaks about -60 dB). The power becomes
 * heat; above {@value DummyLoadBlockEntity#BURN_C} °C the resistor opens and the load is gone.
 */
public class DummyLoadBlock extends Block implements EntityBlock, RfPort {
    public static final BooleanProperty BURNT = BooleanProperty.create("burnt");
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public DummyLoadBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BURNT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BURNT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DummyLoadBlockEntity(PgTelegraph.DUMMY_LOAD_BE.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != PgTelegraph.DUMMY_LOAD_BE.get() ? null
                : (l, p, s, be) -> ((DummyLoadBlockEntity) be).serverTick();
    }
}
