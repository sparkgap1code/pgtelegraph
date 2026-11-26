package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.sparkgap.pgtelegraph.client.ClientAccess;
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
 * CW receiver with BFO. Terminals: PWR+, PWR- (needs ≥ 9 V, draws ~1 W), OUT1, OUT2: a contact
 * that closes while a signal in the 500 Hz CW filter is above the squelch, so a sounder, inker or
 * relay can copy radio traffic. The speaker plays the strongest signal within ±1.5 kHz as a tone
 * of 700 Hz + offset, over band noise.
 */
public class ReceiverBlockEntity extends RadioBlockEntity implements AmpFed, PowerLamp {
    @Override
    public int lamp() {
        return isBurnt() ? FAULT : isPowered() ? ON : OFF;
    }

    // ---- an audio amplifier takes this set's audio: its own speaker goes quiet
    private long ampAt = -1000;
    private boolean ampMuted;

    @Override
    public void ampFed(long tick) {
        ampAt = tick;
    }

    @Override
    public boolean ampMuted() {
        return ampMuted;
    }

    private void ampTick() {
        boolean m = level.getGameTime() - ampAt < 40;
        if (m != ampMuted) {
            ampMuted = m;
            sendData();
        }
    }

    public static final float LOAD_OHMS = 144f;
    public static final double MIN_V = 9;
    public static final Spec SPEC = new Spec(85f, 5f, 1f, 30f,
            Spec.contact(0.05f, 0.5f, 1), new Spec.Part("load", LOAD_OHMS, 0, 1));

    private ScrollValueBehaviour squelch;
    private SwitchedWire standby;
    private SwitchedWire out;

    // synced to the client
    private boolean powered;
    public int toneHz = 700;
    public float toneVol, noiseVol;
    private float shownDbm = -200;
    /** client-only sound instances */
    public Object clientTone, clientNoise;
    // front-panel knobs: attenuator (0 / 10 / 20 dB), AGC (fast / slow / off), noise blanker, filter (0 = the fitted part, 1, 2 narrower)
    public int att, agc, nb, filterSel;
    public static final int KNOB_ATT = 0, KNOB_AGC = 1, KNOB_NB = 2, KNOB_FILTER = 3;

    public void setKnob(int knob, int value) {
        switch (knob) {
            case KNOB_ATT -> att = Math.floorMod(value, 3);
            case KNOB_AGC -> agc = Math.floorMod(value, 3);
            case KNOB_NB -> nb = Math.floorMod(value, 2);
            case KNOB_FILTER -> filterSel = Math.floorMod(value, 3);
            default -> {}
        }
        setChanged();
        sendData();
    }

    /** the passband in use: the fitted filter, or narrower by the DSP selection */
    @Override
    protected int maxMode() {
        return WFM;
    }

    public int passbandHz() {
        int part = stats().filterHz();
        return switch (mode) {
            case AM -> filterSel == 0 ? 6000 : filterSel == 1 ? 4000 : 3000;
            case FM -> 12_500;
            case WFM -> 200_000;
            case SSB -> Math.min(part, filterSel == 0 ? 2700 : filterSel == 1 ? 2400 : 1800);
            default -> Math.min(part, filterSel == 0 ? part : filterSel == 1 ? 500 : 250);
        };
    }

    /** what the antenna brings in, synced for the simulated loudspeaker */
    public final com.sparkgap.pgtelegraph.radio.RxChannel channel = new com.sparkgap.pgtelegraph.radio.RxChannel();

    /**
     * A receiver only keeps its station awake while it is copying traffic onto something that
     * writes it down (inker, decoder, teleprinter on its OUT wires, or a tape recorder against it).
     */
    @Override
    protected boolean keepsStationAwake() {
        return level != null && Recorders.attached(level, worldPosition, 2, 3);
    }

    public ReceiverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        squelch = new ScrollValueBehaviour(Component.translatable("pgtelegraph.receiver.squelch"), this,
                ValueBoxes.front(8, 4, 3, 0.3f))
                .between(0, 30)
                .withFormatter(v -> "SNR " + v + " dB");
        squelch.value = 6;
        behaviours.add(squelch);
    }

    @Override
    public com.sparkgap.pgtelegraph.parts.PartSlot.Layout layout() {
        return com.sparkgap.pgtelegraph.parts.PartSlot.Layout.RECEIVER;
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(4);
        standby = builder.connectSwitch(LOAD_OHMS, builder.terminalNode(0), builder.terminalNode(1), true);
        out = builder.connectSwitch(0.05f, builder.terminalNode(2), builder.terminalNode(3), false);
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (standby != null) applyPower(standby);
        if (out != null) applyPower(out);
    }

    @Override
    protected void guards(Guard g) {
        g.check(standby, SPEC.maxVolts(), 0);
        g.check(out, SPEC.maxVolts(), SPEC.part("contact").maxAmps());
    }

    @Override
    protected void openAll() {
        if (out != null && out.getState()) out.setState(false);
    }

    public boolean isPowered() {
        return powered;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (!level.isClientSide) ampTick();
        if (level.isClientSide) {
            if (powered && !isBurnt()) ClientAccess.ensureReceiverSound(this);
            return;
        }
        boolean supplied = standby != null && fuseOk(Math.abs(standby.current()));
        if (standby != null && standby.getState() != supplied) standby.setState(supplied);
        boolean pw = creative() ? !isBurnt() : !isBurnt() && supplied && Math.abs(standby.potentialDifference()) >= MIN_V;
        com.sparkgap.pgtelegraph.radio.AntennaLink rl = link;
        double bypassDb = 0;
        if (rl != null && rl.amplifier() != null) {
            rl = level.getBlockEntity(rl.amplifier()) instanceof LinearAmplifierBlockEntity amp ? amp.outLink() : null;
            bypassDb = 0.3 + (link.coaxLossDb10() * Math.sqrt(fMHz() / 10));
        }
        if (!pw || rl == null || !stats().covers(freqHz)) {
            setOut(false);
            if (!channel.signals.isEmpty()) {
                channel.clear();
                sendData();
            }
            publish(pw, toneHz, 0, pw ? 0.05f : 0, -200);
            return;
        }
        if (--emiScan <= 0) {
            emiScan = 40;
            emiDbm = com.sparkgap.pgtelegraph.radio.Emi.noiseDbm(level, worldPosition, freqHz) - stats().emiRejectDb();
        }
        double rxNoise = 10 * Math.log10(RadioMath.fromDb(stats().rxNoiseDbm()) + RadioMath.fromDb(emiDbm));
        int bw = passbandHz();
        bypassDb += 10 * att; // the attenuator: signals and outside noise go down, the receiver's own noise does not
        var r = com.sparkgap.pgtelegraph.radio.Reception.listen(level, rl, freqHz + driftHz(), bypassDb, null, worldPosition.asLong(), terrainCache,
                rxNoise, bw);
        boolean toneOk = com.sparkgap.pgtelegraph.radio.Ctcss.passes(level, ctcss, r.bestEmission());
        lastSignalDbm = r.best();
        lastNoiseDbm = r.noiseDbm();
        int sq = squelch == null ? 6 : squelch.getValue();
        if (mode == FSK) {
            // two narrow tone filters: mark on the dial, space 170 Hz below
            long f0 = freqHz + driftHz();
            var mk = com.sparkgap.pgtelegraph.radio.Reception.listen(level, rl, f0, bypassDb, null, worldPosition.asLong(), terrainCache, rxNoise, 120);
            var sp = com.sparkgap.pgtelegraph.radio.Reception.listen(level, rl, f0 - FSK_SHIFT, bypassDb, null, worldPosition.asLong(), terrainCache, rxNoise, 120);
            setOut(mk.hasSignal() && mk.best() - mk.noiseDbm() >= sq && mk.best() > sp.best() + 3);
        } else setOut(toneOk && r.hasSignal() && r.best() - r.noiseDbm() >= sq);
        // the loudspeaker: the channel as the antenna brings it in, rebuilt and demodulated on the client
        boolean dirty = channel.track(level);
        if (level.getGameTime() % 10 == Math.floorMod(worldPosition.asLong(), 10)) {
            int rxMode = mode == AM ? com.sparkgap.pgtelegraph.radio.RxChannel.AM
                    : mode == FM ? com.sparkgap.pgtelegraph.radio.RxChannel.FM_NARROW
                    : mode == WFM ? com.sparkgap.pgtelegraph.radio.RxChannel.FM_WIDE
                    : mode == SSB ? com.sparkgap.pgtelegraph.radio.RxChannel.SSB : com.sparkgap.pgtelegraph.radio.RxChannel.CW;
            int span = mode == WFM ? 100_000 : Math.max(bw, mode == FM ? 12_500 : 3000);
            dirty |= channel.refresh(level, rl, freqHz + driftHz(), bypassDb, null, worldPosition.asLong(), terrainCache,
                    rxNoise, span, rxMode, bw, (mode != FM && mode != WFM) || squelchOpen());
        } else if ((mode == FM || mode == WFM) && channel.open != squelchOpen()) {
            channel.open = squelchOpen(); // FM squelch opens and shuts with the carrier
            dirty = true;
        }
        if (dirty) sendData();
        if (squelchOpen() && level instanceof net.minecraft.server.level.ServerLevel sl)
            com.sparkgap.pgtelegraph.radio.Reception.awardHeard(sl, r, rl, null, worldPosition);
        if (voiceMode()) {
            // AM / FM: no BFO tone; speech comes through VoiceAir, the speaker hisses under it
            float snr = r.hasSignal() ? (float) (r.best() - r.noiseDbm()) : -99;
            if (toneOk && r.bestEmission() != null && r.hasSignal()) { // for a DTMF decoder on the audio output
                heardSource = r.bestEmission().source();
                heardSnr = snr;
                heardTick = level.getGameTime();
            }
            if (toneOk && r.bestEmission() != null && r.hasSignal())
                com.sparkgap.pgtelegraph.media.VoiceAir.listen(level, worldPosition, r.bestEmission().source(),
                        com.sparkgap.pgtelegraph.radio.RadioWorld.modulation(level, r.bestEmission().source()), snr);
            float hiss = mode == AM ? (float) Math.max(0.04, Math.min(0.35, (26 - snr) / 70))
                    : snr >= sq && toneOk ? (float) Math.max(0, Math.min(0.3, (18 - snr) / 40)) : 0; // FM squelch: silent until a carrier opens it
            publish(true, toneHz, 0, hiss, (float) Math.max(r.best(), r.floorDbm()));
            return;
        }
        publish(true, com.sparkgap.pgtelegraph.radio.Reception.toneHz(r, toneHz), com.sparkgap.pgtelegraph.radio.Reception.toneVolume(r),
                com.sparkgap.pgtelegraph.radio.Reception.noiseVolume(r), (float) Math.max(r.best(), r.floorDbm()));
    }

    /** server: the voice signal the speaker is giving out (AM / FM / SSB modes), for a DTMF decoder */
    public net.minecraft.core.BlockPos heardSource;
    public float heardSnr;
    public long heardTick = -100;

    private int emiScan;
    private double emiDbm = -300;
    /** last reading, for computers */
    public double lastSignalDbm = Double.NEGATIVE_INFINITY, lastNoiseDbm = -140;

    public double emiDbm() {
        return emiDbm;
    }

    public boolean squelchOpen() {
        return out != null && out.getState();
    }

    private final com.sparkgap.pgtelegraph.radio.Reception.TerrainCache terrainCache = new com.sparkgap.pgtelegraph.radio.Reception.TerrainCache();

    private void setOut(boolean on) {
        boolean closed = on && !isBurnt();
        if (out != null && out.getState() != closed) out.setState(closed);
    }

    private void publish(boolean pw, int tone, float vol, float nv, float dbm) {
        boolean changed = pw != powered || tone != toneHz || (vol > 0) != (toneVol > 0)
                || Math.abs(vol - toneVol) > 0.08f || Math.abs(nv - noiseVol) > 0.04f || Math.abs(dbm - shownDbm) > 3;
        powered = pw;
        toneHz = tone;
        toneVol = vol;
        noiseVol = nv;
        if (Math.abs(dbm - shownDbm) > 3) shownDbm = dbm;
        if (changed) sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        super.addToGoggleTooltip(tip, sneaking);
        if (!powered) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.receiver.no_power")
                    .withStyle(ChatFormatting.RED)));
        } else {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.receiver.signal"))
                    .append(new String[]{"", " [FSK]", " [AM]", " [FM]"}[mode]).append(": ").append(Component.literal(RadioMath.sMeter(shownDbm)
                            + String.format(Locale.ROOT, " (%.0f dBm)", shownDbm)).withStyle(ChatFormatting.GOLD)));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putByte("Att", (byte) att);
        tag.putByte("Agc", (byte) agc);
        tag.putByte("Nb", (byte) nb);
        tag.putByte("FilSel", (byte) filterSel);
        if (clientPacket) {
            tag.putBoolean("Powered", powered);
            tag.putInt("Tone", toneHz);
            tag.put("Rx", channel.save());
            tag.putFloat("ToneVol", toneVol);
            tag.putFloat("NoiseVol", noiseVol);
            tag.putFloat("Dbm", shownDbm);
            tag.putBoolean("AmpMute", ampMuted);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        att = tag.getByte("Att");
        agc = tag.getByte("Agc");
        nb = tag.getByte("Nb");
        filterSel = tag.getByte("FilSel");
        channel.agcMode = agc;
        channel.blanker = nb == 1;
        if (clientPacket) {
            powered = tag.getBoolean("Powered");
            toneHz = tag.getInt("Tone");
            if (tag.contains("Rx")) channel.load(tag.getCompound("Rx"));
            toneVol = tag.getFloat("ToneVol");
            noiseVol = tag.getFloat("NoiseVol");
            shownDbm = tag.getFloat("Dbm");
            ampMuted = tag.getBoolean("AmpMute");
        }
    }
}
