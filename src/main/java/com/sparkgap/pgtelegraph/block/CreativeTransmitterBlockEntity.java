package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Creative transmitter (크리에이티브 송신기): needs no supply and no parts, never burns (nor does its
 * coax), clean carrier on any frequency. Output: one value box whose board has a row per
 * multiplier (x1 / x10 / x100 / x1000), 1-250 in each: up to 250 kW. Keyed like the normal one (KEY to KEY COM, microphone,
 * computer) or by a redstone signal.
 */
public class CreativeTransmitterBlockEntity extends TransmitterBlockEntity {
    public static final int MAX_BASE = 250;
    public static final int[] MULTS = {1, 10, 100, 1000};
    public static final double MAX_W = 250_000;

    public CreativeTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected ScrollValueBehaviour makePowerBox() {
        ScrollValueBehaviour b = new StepScroll.Rows(Component.translatable("pgtelegraph.transmitter.power"), this,
                ValueBoxes.front(8, 4, 3, 0.3f), MAX_BASE, MULTS, "W");
        b.value = 10;
        return b;
    }

    @Override
    public boolean creative() {
        return true;
    }

    @Override
    protected int maxPanelW() {
        return (int) MAX_W;
    }

    @Override
    protected double paMaxW() {
        return MAX_W;
    }

    @Override
    protected double targetW() {
        return Math.min(MAX_W, panelW());
    }

    @Override
    protected double[] harmonics() {
        return new double[]{-120, -120};
    }

    @Override
    protected boolean keyInput() {
        return super.keyInput() || (level != null && level.hasNeighborSignal(worldPosition));
    }
}
