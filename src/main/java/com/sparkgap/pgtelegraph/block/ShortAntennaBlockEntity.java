package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ShortAntennaBlockEntity extends SmartBlockEntity {
    public enum Mode implements INamedIconOptions {
        WHIP(AllIcons.I_TOOL_MOVE_Y),
        DIPOLE(AllIcons.I_TOOL_MOVE_XZ);

        private final AllIcons icon;

        Mode(AllIcons icon) {
            this.icon = icon;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return "pgtelegraph.short_antenna." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private ScrollOptionBehaviour<Mode> mode;

    public ShortAntennaBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        mode = new ScrollOptionBehaviour<>(Mode.class, Component.translatable("pgtelegraph.short_antenna.mode"), this,
                ValueBoxes.baseSide(1.5, 11, 0.4f));
        mode.withCallback(i -> {
            if (level == null || level.isClientSide) return;
            BlockState s = getBlockState();
            boolean dipole = i == Mode.DIPOLE.ordinal();
            if (s.getValue(ShortAntennaBlock.DIPOLE) != dipole) {
                level.setBlock(worldPosition, s.setValue(ShortAntennaBlock.DIPOLE, dipole), 3);
            }
        });
        behaviours.add(mode);
    }

    public boolean isDipole() {
        return getBlockState().getValue(ShortAntennaBlock.DIPOLE);
    }
}
