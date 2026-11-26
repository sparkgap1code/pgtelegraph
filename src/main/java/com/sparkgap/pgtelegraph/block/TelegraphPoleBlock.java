package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Telegraph pole (전신주): a stackable 6-pixel post; insulators sit on its top. */
public class TelegraphPoleBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

    public TelegraphPoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
