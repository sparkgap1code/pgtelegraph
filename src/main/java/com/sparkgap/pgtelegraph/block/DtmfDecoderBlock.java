package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/** DTMF decoder box: a powered station that also gives out a redstone signal on every side. */
public class DtmfDecoderBlock extends PoweredStationBlock<DtmfDecoderBlockEntity> {
    public DtmfDecoderBlock(Properties p, double[] box, Supplier<? extends BlockEntityType<? extends DtmfDecoderBlockEntity>> type,
                            StationBlock.Use<DtmfDecoderBlockEntity> use) {
        super(p, box, DtmfDecoderBlockEntity.class, type, use);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return level.getBlockEntity(pos) instanceof DtmfDecoderBlockEntity be ? be.output() : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }
}
