package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Antenna tuner in the coax run (coax may enter from any side). Desk automatic (자동 안테나 튜너,
 * 10:1), remote automatic at the antenna (원격 안테나 튜너, 100:1), or manual (수동 안테나 튜너,
 * {@link ManualTunerBlock}). See {@link com.sparkgap.pgtelegraph.radio.AntennaLink.Tuner}.
 */
public class AntennaTunerBlock extends Block implements RfPort {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 8, 14);

    public final com.sparkgap.pgtelegraph.radio.AntennaLink.Tuner spec;

    public AntennaTunerBlock(Properties properties) {
        this(properties, com.sparkgap.pgtelegraph.radio.AntennaLink.Tuner.DESK);
    }

    public AntennaTunerBlock(Properties properties, com.sparkgap.pgtelegraph.radio.AntennaLink.Tuner spec) {
        super(properties);
        this.spec = spec;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
