package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Creative antenna (크리에이티브 안테나): matched (SWR 1:1) on every frequency with no loss of its own,
 * a plain 2.15 dBi omni pattern, and a height above ground set on its value box (1-2500 m) instead of
 * where it stands - for trying out a station or a path. The coax to it still has its loss.
 */
public class CreativeAntennaBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private ScrollValueBehaviour height;

    public CreativeAntennaBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        height = new StepScroll.Rows(Component.translatable("pgtelegraph.creative_antenna.height"), this,
                ValueBoxes.front(8, 4, 6, 0.25f), 250, new int[]{1, 10}, "m");
        height.value = 10;
        behaviours.add(height);
    }

    public double heightM() {
        return height == null ? 10 : height.getValue();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal("     SWR 1.00:1  2.15 dBi  ").append(Component.translatable("pgtelegraph.creative_antenna.at", (int) heightM()))
                .withStyle(ChatFormatting.GOLD));
        return true;
    }
}
