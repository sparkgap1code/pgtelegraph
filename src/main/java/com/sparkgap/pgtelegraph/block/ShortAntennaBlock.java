package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.block.IBE;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One-block (1 m) antenna; whip or dipole chosen on its Create value panel. */
public class ShortAntennaBlock extends Block implements IBE<ShortAntennaBlockEntity> {
    public static final BooleanProperty DIPOLE = BooleanProperty.create("dipole");
    private static final VoxelShape WHIP = Shapes.or(Block.box(5, 0, 5, 11, 3, 11), Block.box(7.5, 3, 7.5, 8.5, 16, 8.5));
    private static final VoxelShape DIP = Shapes.or(Block.box(5, 0, 5, 11, 3, 11), Block.box(7.5, 3, 7.5, 8.5, 8, 8.5),
            Block.box(0, 8, 7.5, 16, 9, 8.5));

    public ShortAntennaBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DIPOLE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIPOLE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(DIPOLE) ? DIP : WHIP;
    }

    @Override
    public Class<ShortAntennaBlockEntity> getBlockEntityClass() {
        return ShortAntennaBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ShortAntennaBlockEntity> getBlockEntityType() {
        return PgTelegraph.SHORT_ANTENNA_BE.get();
    }
}
