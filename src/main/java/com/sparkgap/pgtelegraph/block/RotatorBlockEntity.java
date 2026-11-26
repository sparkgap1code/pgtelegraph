package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Antenna rotator (안테나 로테이터): a vertical shaft from below turns the beam antenna (yagi, dish,
 * helix, loop) standing on top through a 100:1 worm gear. 1 RPM turns it 0.036 °/s, so 64 RPM
 * swings it round in about 2.5 minutes; reversing the shaft turns it back. Stress 1 SU per RPM.
 */
public class RotatorBlockEntity extends KineticBlockEntity {
    public static final float STRESS_PER_RPM = 1f;
    public static final double REDUCTION = 100;

    private double pending;

    public RotatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        float rpm = getSpeed();
        if (rpm == 0) return;
        pending += rpm * 360.0 / 60 / 20 / REDUCTION;
        // nudge the antenna in 1° steps so it does not resync every tick
        if (Math.abs(pending) >= 1 && level.getBlockEntity(worldPosition.above()) instanceof VhfAntennaBlockEntity ant) {
            double step = Math.floor(Math.abs(pending)) * Math.signum(pending);
            pending -= step;
            ant.manualAim(ant.azimuth() + step, ant.elevation());
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        super.addToGoggleTooltip(tip, sneaking);
        if (level != null && level.getBlockEntity(worldPosition.above()) instanceof VhfAntennaBlockEntity ant) {
            tip.add(Component.literal(String.format(Locale.ROOT, "    az %.0f°  el %.0f°", ant.azimuth(), ant.elevation()))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tip.add(Component.literal("    ").append(Component.translatable("pgtelegraph.rotator.no_antenna"))
                    .withStyle(ChatFormatting.RED));
        }
        return true;
    }
}
