package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;
import java.util.Locale;

/**
 * Line support on a pole: one tie point for an overhead wire, and the leakage from that point to
 * earth. Dry, both are good; in rain a bare wooden peg leaks badly (a few hundred ohms), a glass
 * insulator stays near 50 kΩ - why telegraph lines went quiet in storms until insulators came.
 */
public class InsulatorBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final Spec SPEC = new Spec(200f, 50f, 5f, 0f);

    private SwitchedWire leak;
    private boolean wet;

    public InsulatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private InsulatorBlock.Kind kind() {
        return getBlockState().getBlock() instanceof InsulatorBlock b ? b.kind : InsulatorBlock.Kind.GLASS;
    }

    public double leakOhms() {
        return wet ? kind().wetOhms : kind().dryOhms;
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(1);
        leak = builder.connectSwitch((float) leakOhms(), builder.terminalNode(0), null, true);
    }

    @Override
    protected void guards(Guard g) {}

    @Override
    protected void openAll() {}

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || level.getGameTime() % 20 != Math.floorMod(worldPosition.asLong(), 20)) return;
        boolean w = level.isRainingAt(worldPosition.above()) || level.isRainingAt(worldPosition);
        if (w != wet) {
            wet = w;
            if (leak != null) leak.setResistance(leakOhms());
            sendData();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        double r = leakOhms();
        tip.add(Component.literal("     ").append(Component.translatable(wet ? "pgtelegraph.insulator.wet" : "pgtelegraph.insulator.dry"))
                .append(String.format(Locale.ROOT, ": %s", r >= 1e6 ? String.format(Locale.ROOT, "%.0f MΩ", r / 1e6)
                        : r >= 1e3 ? String.format(Locale.ROOT, "%.0f kΩ", r / 1e3) : String.format(Locale.ROOT, "%.0f Ω", r)))
                .withStyle(wet ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        if (leak != null) tip.add(Component.literal(String.format(Locale.ROOT, "     %.1f mA", Math.abs(leak.current()) * 1000))
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        tag.putBoolean("Wet", wet);
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        wet = tag.getBoolean("Wet");
    }
}
