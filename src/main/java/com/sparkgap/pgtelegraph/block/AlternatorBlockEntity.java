package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;
import org.patryk3211.powergrid.kinetics.base.ElectricKineticBlockEntity;

import java.util.List;
import java.util.Locale;

/**
 * Alexanderson alternator: a toothed rotor spinning past the stator makes RF directly, no tubes.
 * f = shaft RPM × 67.19 Hz (the internal step-up gear and pole count of the Grimeton machine
 * scaled to Create speeds: 256 RPM → 17.2 kHz). Output 200 kW at full speed, scaling with the
 * square of speed (EMF ∝ speed). Keying the field gives CW. Stress impact 256 SU per RPM.
 */
public class AlternatorBlockEntity extends ElectricKineticBlockEntity {
    public static final double HZ_PER_RPM = 17200.0 / 256;
    public static final double RATED_W = 200_000, FULL_RPM = 256, LOSS = 0.08;
    public static final float STRESS_PER_RPM = 256f;

    private SwitchedWire pullup;
    private AntennaLink link;
    private int rescan, coaxOverload;
    private boolean keyed;
    // synced
    private float shownOut;
    private int shownHz;
    private String linkText = "";

    public AlternatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public ThermalBehaviour specifyThermalBehaviour() {
        return ThermalBehaviour.forMaxPower(this, 2000f, 30000f, 150f);
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        FloatingNode rail = builder.addInternalNode();
        builder.add(new VoltageSourceCoupling(rail, builder.terminalNode(1), 1f, 5f));
        pullup = builder.connectSwitch(1000f, rail, builder.terminalNode(0), true);
    }

    @Override
    public float calculateStressApplied() {
        this.lastStressApplied = STRESS_PER_RPM;
        return STRESS_PER_RPM;
    }

    private it.unimi.dsi.fastutil.longs.LongSet heldChunks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

    protected void claimChunks(com.sparkgap.pgtelegraph.radio.AntennaLink l) {
        heldChunks = com.sparkgap.pgtelegraph.radio.StationChunks.update(level, worldPosition, heldChunks,
                com.sparkgap.pgtelegraph.radio.StationChunks.wanted(worldPosition, l));
    }

    protected void releaseChunks() {
        if (level != null && !level.isClientSide) com.sparkgap.pgtelegraph.radio.StationChunks.release(level, worldPosition, heldChunks);
        heldChunks = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    }

    public int freqHz() {
        return (int) Math.round(Math.abs(getSpeed()) * HZ_PER_RPM);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (--rescan <= 0) {
            rescan = 40;
            link = AntennaLink.scan(level, worldPosition);
            claimChunks(link);
            linkText = link == null ? "" : link.describe() + ", " + link.coaxMeters() + " m coax";
        }
        keyed = pullup != null && Math.abs(pullup.current()) > 0.0025;
        double rpm = Math.abs(getSpeed());
        int hz = freqHz();
        double out = 0;
        if (keyed && rpm > 0 && !isOverStressed()) {
            out = RATED_W * Math.pow(Math.min(rpm, FULL_RPM) / FULL_RPM, 2);
            double heat = out * LOSS;
            if (link != null) {
                heat += RfChain.deliver(level, worldPosition, link, hz, out);
                if (out > link.coaxMaxW()) {
                    if (++coaxOverload >= 20) {
                        coaxOverload = 0;
                        RfChain.burnWeakest(level, link);
                        link = null;
                    }
                } else coaxOverload = Math.max(0, coaxOverload - 1);
            } else {
                heat += out;
                RadioWorld.silence(level, worldPosition);
            }
            if (thermalBehaviour != null) thermalBehaviour.applyTickPower(heat);
        } else {
            RadioWorld.silence(level, worldPosition);
        }
        if (Math.abs(out - shownOut) > Math.max(100, shownOut * 0.1) || (out == 0) != (shownOut == 0) || Math.abs(hz - shownHz) > 5) {
            shownOut = (float) out;
            shownHz = hz;
            sendData();
        }
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) RadioWorld.silence(level, worldPosition);
        releaseChunks();
        super.remove();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        super.addToGoggleTooltip(tip, sneaking);
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.freq")).append(": ")
                .append(Component.literal(RadioBlockEntity.formatFreq(shownHz)).withStyle(ChatFormatting.AQUA)));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.transmitter.rf_out")).append(": ")
                .append(Component.literal(LinearAmplifierBlockEntity.fmtW(shownOut))
                        .withStyle(shownOut > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.antenna")).append(": ")
                .append(linkText.isEmpty() ? Component.translatable("pgtelegraph.radio.no_antenna").withStyle(ChatFormatting.RED)
                        : Component.literal(linkText).withStyle(ChatFormatting.GRAY)));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putFloat("Out", shownOut);
            tag.putInt("Hz", shownHz);
            tag.putString("LinkText", linkText);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            shownOut = tag.getFloat("Out");
            shownHz = tag.getInt("Hz");
            linkText = tag.getString("LinkText");
        }
    }

    public static String describe(double rpm) {
        return String.format(Locale.ROOT, "%.0f Hz", rpm * HZ_PER_RPM);
    }
}
