package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;
import java.util.Locale;

/**
 * Vacuum-tube linear amplifier (진공관 선형 증폭기), grounded-grid style: 20 dB gain, so 100 W of
 * drive gives 10 kW. Terminals: HV+ / HV- (plate supply, rated 2400 V), FIL+ / FIL- (12.6 V
 * filament, ~55 W). The filament needs 30 s to warm up; until then, or with the plate supply too
 * low, the bypass relay just passes the drive through to the antenna. RF input is on the
 * operator's left side, output on the right. Plate efficiency 65 %; the rest - plus anything the
 * antenna reflects - heats the tube. Create fans blowing on it help, as with any Power Grid device.
 */
public class LinearAmplifierBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    /** the standard tier's ratings, for the tooltip */
    public static final Spec SPEC = AmpTier.STANDARD.spec();
    private static final double BYPASS_DB = 0.3;

    private final AmpTier tier;

    private ScrollValueBehaviour limit;
    private SwitchedWire plate;
    private ElectricWire bleeder, filament;

    private double driveW;
    private long driveHz;
    private long driveTick = -10;
    private net.minecraft.core.BlockPos driveFrom;
    private int warm;
    private AntennaLink outLink;
    private int rescan, coaxOverload;
    private float plateR; // set in buildCircuit (runs inside the super constructor)

    // synced
    private float shownOut, shownDrive;
    private int shownWarm;
    private boolean bypass = true;
    private String outText = "";

    public LinearAmplifierBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tier = state.getBlock() instanceof LinearAmplifierBlock b ? b.tier : AmpTier.STANDARD;
    }

    private AmpTier tier() {
        // addBehaviours runs inside the super constructor, before 'tier' is assigned
        return tier != null ? tier : getBlockState().getBlock() instanceof LinearAmplifierBlock b ? b.tier : AmpTier.STANDARD;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        limit = new ScrollValueBehaviour(Component.translatable("pgtelegraph.amplifier.limit"), this,
                ValueBoxes.front(8, 5, 2, 0.35f))
                .between(0, tier().levels().length - 1)
                .withFormatter(v -> fmtW(tier().levels()[Math.max(0, Math.min(tier().levels().length - 1, v))]));
        limit.value = tier().defaultLevel();
        behaviours.add(limit);
    }

    static String fmtW(double w) {
        return w >= 1000 ? String.format(Locale.ROOT, "%.1f kW", w / 1000) : String.format(Locale.ROOT, "%.0f W", w);
    }

    public double limitW() {
        int[] lv = tier().levels();
        return lv[limit == null ? tier().defaultLevel() : Math.max(0, Math.min(lv.length - 1, limit.getValue()))];
    }

    @Override
    public Spec spec() {
        return tier().spec();
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(4);
        plateR = (float) (tier().plateV() * tier().plateV() / (tier().levels()[0] / tier().efficiency()));
        plate = builder.connectSwitch(plateR, builder.terminalNode(0), builder.terminalNode(1), false);
        bleeder = builder.connect(1_000_000f, builder.terminalNode(0), builder.terminalNode(1)); // HV bleeder resistor
        filament = builder.connect(tier().filamentOhms(), builder.terminalNode(2), builder.terminalNode(3));
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (filament != null) applyPower(filament);
        if (bleeder != null) applyPower(bleeder);
    }

    @Override
    protected void guards(Guard g) {
        g.check(plate, tier().spec().maxVolts(), tier().plateMaxA());
        g.check(filament, tier().filamentMaxV(), tier().spec().part("filament").maxAmps());
    }

    @Override
    protected void openAll() {
        if (plate != null && plate.getState()) plate.setState(false);
    }

    /** Called by the exciter's RF chain each tick it is keyed. */
    public void drive(double watts, long hz) {
        drive(watts, hz, null);
    }

    /** {@code from}: the stage driving this one (so its coax is never taken for the output) */
    public void drive(double watts, long hz, net.minecraft.core.BlockPos from) {
        if (from != null && !from.equals(driveFrom)) {
            driveFrom = from;
            rescan = 0;
        }
        driveW = watts;
        driveHz = hz;
        driveTick = level == null ? 0 : level.getGameTime();
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

    public AntennaLink outLink() {
        return outLink;
    }

    private net.minecraft.core.Direction outSide() {
        return getBlockState().getValue(DeviceBlock.FACING).getCounterClockWise();
    }

    /**
     * The output: the operator's right side first, but any side whose coax leads to an antenna
     * (or to a further amplifier) works. The input run leads back to the exciter, which is no
     * endpoint, so it is never picked; a run back to the amplifier driving this one is skipped.
     */
    private AntennaLink findOutput() {
        AntennaLink amp = null;
        java.util.List<net.minecraft.core.Direction> order = new java.util.ArrayList<>();
        order.add(outSide());
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) if (d != outSide()) order.add(d);
        for (net.minecraft.core.Direction d : order) {
            AntennaLink l = AntennaLink.scan(level, worldPosition, d);
            if (l == null) continue;
            if (l.amplifier() == null) return l;
            if (!l.amplifier().equals(driveFrom) && amp == null) amp = l;
        }
        return amp;
    }

    /** Every amplifier the RF passes through after {@code link}, in order (at most 4 stages). */
    public static java.util.List<net.minecraft.core.BlockPos> chain(net.minecraft.world.level.Level level, AntennaLink link) {
        java.util.List<net.minecraft.core.BlockPos> out = new java.util.ArrayList<>();
        AntennaLink l = link;
        while (l != null && l.amplifier() != null && out.size() < 4 && !out.contains(l.amplifier())) {
            out.add(l.amplifier());
            l = level.getBlockEntity(l.amplifier()) instanceof LinearAmplifierBlockEntity a ? a.outLink() : null;
        }
        return out;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (--rescan <= 0) {
            rescan = 40;
            outLink = findOutput();
            claimChunks(outLink);
            String t = outLink == null ? "" : outLink.describe() + ", " + outLink.coaxMeters() + " m coax";
            if (!t.equals(outText)) {
                outText = t;
                sendData();
            }
        }
        boolean filOn = !isBurnt() && filament != null && Math.abs(filament.potentialDifference()) >= tier().filamentMinV();
        int warmup = tier().warmupTicks();
        warm = filOn ? Math.min(warmup, warm + 1) : Math.max(0, warm - 2);
        updateGlow(warm > warmup / 6);

        boolean driven = level.getGameTime() - driveTick <= 1 && driveW > 0;
        double hv = plate == null ? 0 : Math.abs(plate.potentialDifference());
        boolean ready = !isBurnt() && warm >= warmup && hv >= tier().minPlateV();
        double heat = 0, out = 0;

        if (!driven || outLink == null) {
            if (plate != null && plate.getState()) plate.setState(false);
            RadioWorld.silence(level, worldPosition);
            if (driven) heat += driveW; // driven with no antenna on the output
        } else if (!ready) {
            // bypass relay: drive straight through
            if (plate != null && plate.getState()) plate.setState(false);
            out = driveW * Math.pow(10, -BYPASS_DB / 10);
            heat += RfChain.deliver(level, worldPosition, outLink, driveHz, out);
        } else {
            double target = Math.min(driveW * tier().gain(), limitW());
            float r = (float) (tier().plateV() * tier().plateV() / Math.max(1, target / tier().efficiency()));
            if (plate != null) {
                if (Math.abs(r - plateR) > plateR * 0.05f) {
                    plate.setResistance(r);
                    plateR = r;
                }
                if (!plate.getState()) plate.setState(true);
            }
            double pin = plate == null ? 0 : Math.abs(plate.potentialDifference() * plate.current());
            out = Math.min(pin * tier().efficiency(), Math.min(driveW * tier().gain(), limitW() * 1.05));
            heat += pin - out + driveW;
            heat += RfChain.deliver(level, worldPosition, outLink, driveHz, out);
        }
        if (outLink != null && out > outLink.coaxMaxW()) {
            if (++coaxOverload >= 20) {
                coaxOverload = 0;
                RfChain.burnWeakest(level, outLink);
                outLink = null;
            }
        } else coaxOverload = Math.max(0, coaxOverload - 1);
        if (thermalBehaviour != null && heat > 0) thermalBehaviour.applyTickPower(heat);
        publish((float) out, (float) (driven ? driveW : 0), !ready);
    }

    private void updateGlow(boolean glow) {
        BlockState s = getBlockState();
        if (s.hasProperty(LinearAmplifierBlock.LIT) && s.getValue(LinearAmplifierBlock.LIT) != glow) {
            level.setBlock(worldPosition, s.setValue(LinearAmplifierBlock.LIT, glow), 3);
        }
    }

    private void publish(float out, float drive, boolean byp) {
        int w = warm * 100 / tier().warmupTicks();
        boolean changed = byp != bypass || Math.abs(out - shownOut) > Math.max(5, shownOut * 0.1f)
                || Math.abs(drive - shownDrive) > Math.max(1, shownDrive * 0.1f) || (w / 10) != (shownWarm / 10)
                || ((out == 0) != (shownOut == 0));
        if (!changed) return;
        shownOut = out;
        shownDrive = drive;
        shownWarm = w;
        bypass = byp;
        sendData();
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) RadioWorld.silence(level, worldPosition);
        releaseChunks();
        super.remove();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(line("pgtelegraph.amplifier.filament", Component.literal(shownWarm >= 100 ? "OK" : shownWarm + " %")
                .withStyle(shownWarm >= 100 ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        tip.add(line("pgtelegraph.amplifier.mode", Component.translatable(bypass ? "pgtelegraph.amplifier.bypass"
                : "pgtelegraph.amplifier.operate").withStyle(bypass ? ChatFormatting.GRAY : ChatFormatting.GOLD)));
        tip.add(line("pgtelegraph.amplifier.drive", Component.literal(fmtW(shownDrive)).withStyle(ChatFormatting.AQUA)));
        tip.add(line("pgtelegraph.transmitter.rf_out", Component.literal(fmtW(shownOut)).withStyle(ChatFormatting.GOLD)));
        tip.add(line("pgtelegraph.radio.antenna", outText.isEmpty()
                ? Component.translatable("pgtelegraph.radio.no_antenna").withStyle(ChatFormatting.RED)
                : Component.literal(outText).withStyle(ChatFormatting.GRAY)));
        if (isBurnt()) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.burnt")
                .withStyle(ChatFormatting.DARK_RED)));
        return true;
    }

    private static Component line(String key, Component value) {
        return Component.literal("     ").append(Component.translatable(key)).append(": ").append(value);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Warm", warm);
        if (clientPacket) {
            tag.putFloat("Out", shownOut);
            tag.putFloat("Drive", shownDrive);
            tag.putInt("WarmPct", shownWarm);
            tag.putBoolean("Bypass", bypass);
            tag.putString("OutText", outText);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        warm = tag.getInt("Warm");
        if (clientPacket) {
            shownOut = tag.getFloat("Out");
            shownDrive = tag.getFloat("Drive");
            shownWarm = tag.getInt("WarmPct");
            bypass = tag.getBoolean("Bypass");
            outText = tag.getString("OutText");
        }
    }
}
