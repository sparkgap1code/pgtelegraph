package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Creative receiver (크리에이티브 수신기): needs no supply and no parts, never burns, a near-perfect front end on any frequency. */
public class CreativeReceiverBlockEntity extends ReceiverBlockEntity {
    public CreativeReceiverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean creative() {
        return true;
    }
}
