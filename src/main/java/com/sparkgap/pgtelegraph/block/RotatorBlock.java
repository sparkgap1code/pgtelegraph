package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Antenna rotator (안테나 로테이터): shaft in from below, beam antenna on top. */
public class RotatorBlock extends KineticBlock implements IBE<RotatorBlockEntity> {
    public RotatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public Class<RotatorBlockEntity> getBlockEntityClass() {
        return RotatorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RotatorBlockEntity> getBlockEntityType() {
        return PgTelegraph.ROTATOR_BE.get();
    }
}
