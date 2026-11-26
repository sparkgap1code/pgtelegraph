package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.List;
import java.util.Locale;

/**
 * CW transmitter. Terminals: PWR+, PWR-, KEY, KEY COM. The key input is sensed like the keyer's
 * (5 V through 1 kΩ). While keyed the PA really draws power from the supply: its load is sized for
 * the chosen output at 13.8 V and 60 % efficiency, so a weaker supply gives less RF.
 * Everything that is not radiated becomes heat: PA losses, power reflected by a mismatched antenna,
 * and all of it when keyed with nothing connected.
 */
public class TransmitterBlockEntity extends RadioBlockEntity implements RfMeterable {
    public static final double NOMINAL_V = 13.8, EFFICIENCY = 0.6, MIN_V = 8;
    public static final Spec SPEC = new Spec(120f, 60f, 3f, 30f,
            new Spec.Part("rf_out", 100, 0, 1), Spec.pullup(1000f, 1), Spec.rail(5f));

    private ScrollValueBehaviour power;
    private SwitchedWire load;
    private SwitchedWire standby;
    private SwitchedWire pullup;

    private boolean keyed;
    private double rfOut;
    private int coaxOverload;
    /** synced for the goggles / lamp */
    private float shownRf;

    public TransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        power = makePowerBox();
        power.withCallback(v -> {
            if (load != null) load.setResistance(loadOhms());
        });
        behaviours.add(power);
    }

    @Override
    public com.sparkgap.pgtelegraph.parts.PartSlot.Layout layout() {
        return com.sparkgap.pgtelegraph.parts.PartSlot.Layout.TRANSMITTER;
    }

    /** the output-power value box */
    protected ScrollValueBehaviour makePowerBox() {
        ScrollValueBehaviour b = new StepScroll.Plain(Component.translatable("pgtelegraph.transmitter.power"), this,
                powerSlot(), StepScroll.powers(maxPanelW())).stepFormat(v -> v + " W")
                .between(1, maxPanelW())
                .withFormatter(v -> v + " W");
        b.value = 10;
        return b;
    }

    /** where the output-power value box sits */
    protected com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform powerSlot() {
        return ValueBoxes.front(8, 4, 3, 0.3f);
    }

    /** the panel's power setting (W) */
    protected int panelW() {
        return power == null ? 10 : power.getValue();
    }

    // ---- hooks for the beacon, spark transmitter and computer control ----
    protected int maxPanelW() {
        return 100;
    }

    protected double paMaxW() {
        return stats().paMaxW();
    }

    protected double paEff() {
        return stats().paEfficiency();
    }

    protected boolean bandOk() {
        return stats().covers(freqHz);
    }

    protected double nominalV() {
        return NOMINAL_V;
    }

    protected double minV() {
        return MIN_V;
    }

    /** broadband emission width, 0 for a clean carrier */
    protected double spreadHz() {
        return 0;
    }

    /** where the carrier sits */
    protected long emitHz() {
        return freqHz + driftHz();
    }

    /** 2nd/3rd harmonic levels (dBc) */
    protected double[] harmonics() {
        return new double[]{stats().harmonicDbc(2, freqHz), stats().harmonicDbc(3, freqHz)};
    }

    /** Text sent by a computer (CC: Tweaked), keyed like a beacon; null when idle. */
    public final BeaconSequencer computerText = new BeaconSequencer();

    /** the key input: KEY shorted to KEY COM, or a computer sending */
    protected boolean keyInput() {
        return keyContact() || computerText.tick() || (acceptsVoice() && com.sparkgap.pgtelegraph.media.VoiceAir.talking(level, worldPosition));
    }

    /** a microphone next to it can talk through it (AM or FM voice) */
    public boolean acceptsVoice() {
        return voiceMode();
    }

    /** the voice goes out as FM (else AM) */
    public boolean voiceFm() {
        return mode == FM;
    }

    /** modulation on the air, as receivers rebuild it: see {@link com.sparkgap.pgtelegraph.radio.RadioWorld#MOD_CARRIER} */
    public int airModulation() {
        if (voiceFm()) return com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_FM;
        if (mode == SSB) return com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_SSB;
        if (acceptsVoice()) return com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_AM;
        return com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_CARRIER;
    }

    /** KEY shorted to KEY COM */
    protected boolean keyContact() {
        return pullup != null && Math.abs(pullup.current()) > 0.0025;
    }

    public double forwardW() {
        return rfOut;
    }

    public double reflectedW() {
        return reflected;
    }

    public boolean isKeyed() {
        return keyed;
    }

    private double reflected;
    private boolean fskSpace;
    private int wearTicks;

    /** output the PA is asked for: the panel setting, limited by the fitted PA */
    protected double targetW() {
        int w = power == null ? 10 : power.getValue();
        return Math.min(w, paMaxW());
    }

    private float loadOhms() {
        double eff = paEff();
        double v = nominalV();
        return (float) (v * v / Math.max(0.5, targetW() / eff));
    }

    @Override
    protected void onPartsChanged() {
        if (load != null) load.setResistance(loadOhms());
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(4);
        load = builder.connectSwitch(loadOhms(), builder.terminalNode(0), builder.terminalNode(1), false);
        standby = builder.connectSwitch(1000f, builder.terminalNode(0), builder.terminalNode(1), true);
        FloatingNode rail = builder.addInternalNode();
        builder.add(new VoltageSourceCoupling(rail, builder.terminalNode(3), 1f, 5f));
        pullup = builder.connectSwitch(1000f, rail, builder.terminalNode(2), !isBurnt());
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (standby != null) applyPower(standby);
    }

    @Override
    protected void guards(Guard g) {
        g.check(load, spec().maxVolts(), 0);
        g.check(pullup, spec().maxVolts(), 0.05f);
    }

    @Override
    protected void openAll() {
        if (load != null && load.getState()) load.setState(false);
        if (pullup != null && pullup.getState()) pullup.setState(false);
        keyed = false;
        if (level != null) RadioWorld.silence(level, worldPosition);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (isBurnt()) {
            setShown(0);
            return;
        }
        double supplyA = (load != null && load.getState() ? Math.abs(load.current()) : 0)
                + (standby != null ? Math.abs(standby.current()) : 0);
        boolean supplied = fuseOk(supplyA);
        if (standby != null && standby.getState() != supplied) standby.setState(supplied);
        boolean canSend = supplied && paMaxW() > 0 && bandOk();
        boolean in = keyInput();
        boolean useFsk = mode == FSK && spreadHz() <= 0;
        fskSpace = useFsk && !in;
        boolean keyNow = (useFsk || in) && canSend;
        if (keyNow != keyed) {
            keyed = keyNow;
            if (load != null) load.setState(keyed);
        }
        if (!keyed || load == null) {
            RadioWorld.silence(level, worldPosition);
            rfOut = 0;
            reflected = 0;
            setShown(0);
            return;
        }
        double v = creative() ? nominalV() : load.potentialDifference();
        if (v < -3) { // reversed supply: the protection diode takes it, then the fuse goes
            burn();
            return;
        }
        double pin = creative() ? targetW() : Math.abs(v * load.current());
        rfOut = v >= minV() ? Math.min(pin * paEff(), targetW() * 1.2) : 0;
        double heat = pin - rfOut;
        // a low-pass filter cut below the carrier turns the carrier into heat
        double lpfLoss = spreadHz() > 0 ? 0 : stats().carrierLossDb(freqHz);
        if (lpfLoss > 0) {
            double kept = rfOut * Math.pow(10, -lpfLoss / 10);
            heat += rfOut - kept;
            rfOut = kept;
        }
        wearPa();
        if (rfOut > 0 && link != null) {
            reflected = RfChain.deliver(level, worldPosition, link, emitHz() - (fskSpace ? FSK_SHIFT : 0), rfOut, spreadHz(), spreadHz() > 0 ? null : harmonics());
            heat += reflected;
            int modulation = airModulation();
            com.sparkgap.pgtelegraph.radio.RadioWorld.setModulation(level, worldPosition, modulation);
            com.sparkgap.pgtelegraph.radio.Ctcss.mark(level, worldPosition, ctcss);
            for (BlockPos a : LinearAmplifierBlockEntity.chain(level, link)) {
                com.sparkgap.pgtelegraph.radio.RadioWorld.setModulation(level, a, modulation);
                com.sparkgap.pgtelegraph.radio.Ctcss.mark(level, a, ctcss);
            }
            if (acceptsVoice()) {
                com.sparkgap.pgtelegraph.media.VoiceAir.onAir(level, worldPosition, worldPosition, modulation);
                for (BlockPos a : LinearAmplifierBlockEntity.chain(level, link)) com.sparkgap.pgtelegraph.media.VoiceAir.onAir(level, a, worldPosition, modulation);
            }
            if (rfOut > link.coaxMaxW() && !creative()) {
                if (++coaxOverload >= 20) {
                    coaxOverload = 0;
                    RfChain.burnWeakest(level, link);
                    link = null;
                }
            } else coaxOverload = Math.max(0, coaxOverload - 1);
        } else {
            reflected = rfOut;
            heat += rfOut; // keyed into nothing: all of it comes back
            RadioWorld.silence(level, worldPosition);
        }
        if (thermalBehaviour != null && !creative()) thermalBehaviour.applyTickPower(heat);
        setShown((float) rfOut);
    }

    /** keyed time wears the PA; at the end of its life it fails */
    private void wearPa() {
        if (++wearTicks < 20) return;
        wearTicks = 0;
        int i = layout().indexOf(com.sparkgap.pgtelegraph.parts.PartSlot.PA);
        if (i < 0) return;
        net.minecraft.world.item.ItemStack pa = parts().get(i);
        if (com.sparkgap.pgtelegraph.parts.PartItem.def(pa) == null) return;
        int t = pa.getOrDefault(com.sparkgap.pgtelegraph.PgTelegraph.PART_WEAR.get(), 0) + 20;
        pa.set(com.sparkgap.pgtelegraph.PgTelegraph.PART_WEAR.get(), t);
        if (t >= com.sparkgap.pgtelegraph.parts.PartItem.PA_LIFE_TICKS) {
            java.util.List<net.minecraft.world.item.ItemStack> p = new java.util.ArrayList<>(parts());
            p.set(i, com.sparkgap.pgtelegraph.parts.Wreckage.burnt(pa));
            setParts(p);
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.5f);
        } else if (t % 2400 == 0) invalidateStats();
    }

    private void setShown(float w) {
        if (Math.abs(w - shownRf) > Math.max(0.5f, shownRf * 0.1f) || (w == 0) != (shownRf == 0)) {
            shownRf = w;
            BlockState s = getBlockState();
            boolean on = w > 0;
            if (s.hasProperty(TransmitterBlock.ON) && s.getValue(TransmitterBlock.ON) != on) {
                level.setBlock(worldPosition, s.setValue(TransmitterBlock.ON, on), 2);
            }
            sendData();
        }
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) RadioWorld.silence(level, worldPosition);
        super.remove();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        super.addToGoggleTooltip(tip, sneaking);
        if (mode == FSK) tip.add(Component.literal("     FSK 170 Hz").withStyle(ChatFormatting.LIGHT_PURPLE));
        else if (mode == AM || mode == FM || mode == SSB)
            tip.add(Component.literal(mode == AM ? "     AM" : mode == FM ? "     FM" : "     SSB (USB)").withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.transmitter.rf_out"))
                .append(": ").append(Component.literal(String.format(Locale.ROOT, "%.1f W", shownRf))
                        .withStyle(shownRf > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) tag.putFloat("Rf", shownRf);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) shownRf = tag.getFloat("Rf");
    }
}
