package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * UPS (무정전 전원장치): the 13.8 V supply with sealed lead-acid batteries inside (500 Wh). While the
 * grid is up it runs the station and charges at up to 120 W; when IN drops below 90 V the batteries
 * take over without a break, and it beeps every 5 s until the grid returns or the batteries run flat.
 */
public class UpsBlockEntity extends PowerSupplyBlockEntity {
    public static final double CAPACITY_WH = 500, CHARGER_W = 120;

    public UpsBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public double capacityWh() {
        return CAPACITY_WH;
    }

    @Override
    public double chargerW() {
        return CHARGER_W;
    }

    @Override
    protected void batteryEdge(boolean nowOnBattery) {
        if (level != null) level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS,
                0.6f, nowOnBattery ? 1.6f : 2.0f);
    }

    @Override
    protected void batteryTick() {
        if (level != null && level.getGameTime() % 100 == 0)
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.5f,
                    chargeFraction() < 0.2 ? 2.0f : 1.6f);
    }
}
