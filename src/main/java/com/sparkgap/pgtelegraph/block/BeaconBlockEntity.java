package com.sparkgap.pgtelegraph.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Automatic beacon: a small transmitter (25 W panel) that keys itself from its program - a
 * repeated message, an NDB (long carrier + ident, for direction finders) or a steady carrier.
 * KEY shorted to KEY COM holds it silent (a clock or relay can switch it on and off).
 */
public class BeaconBlockEntity extends TransmitterBlockEntity implements BeaconProgram.Holder {
    private final BeaconProgram program = new BeaconProgram();

    public BeaconBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected int maxPanelW() {
        return 25;
    }

    @Override
    protected boolean keyInput() {
        if (keyContact()) return false;
        if (computerText.busy()) return computerText.tick();
        return program.tick();
    }

    @Override
    public BeaconProgram program() {
        return program;
    }

    @Override
    public void programChanged() {
        setChanged();
        sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        super.addToGoggleTooltip(tip, sneaking);
        String mode = switch (program.mode) {
            case BeaconProgram.NDB -> "NDB";
            case BeaconProgram.CARRIER -> "CARRIER";
            default -> program.wpm + " WPM";
        };
        tip.add(Component.literal("     " + mode + ": ").append(Component.literal(program.message).withStyle(ChatFormatting.AQUA)));
        if (!program.call.isEmpty()) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.license.call"))
                .append(": " + program.call).withStyle(ChatFormatting.GRAY));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        program.save(tag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        program.load(tag);
    }
}
