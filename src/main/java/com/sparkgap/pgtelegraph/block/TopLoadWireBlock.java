package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Umbrella / top-load wire (우산 소자): 1 block = 1 m of wire. Every piece connected to the top of a
 * loaded mast (also diagonally, so ribs can slope down like a real umbrella) adds capacitance and
 * effective height. No collision, like a real wire overhead.
 */
public class TopLoadWireBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(6, 6, 6, 10, 10, 10);

    public TopLoadWireBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }
}
