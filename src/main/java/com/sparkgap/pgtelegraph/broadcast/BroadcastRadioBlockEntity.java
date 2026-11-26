package com.sparkgap.pgtelegraph.broadcast;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sparkgap.pgtelegraph.block.RadioBlockEntity;
import com.sparkgap.pgtelegraph.block.ToneSource;
import com.sparkgap.pgtelegraph.block.Tunable;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.Reception;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Broadcast radio (라디오) with a loudspeaker: AM below 30 MHz (9 kHz channel), FM above. It has a
 * ferrite rod built in (-20 dB against a proper antenna); coax to an outside antenna does much better.
 * The server works out every signal in the channel as it arrives here - path loss (free space,
 * ground wave, sky wave, terrain), fading, noise (atmospheric, rain, thunderstorms) - and the client
 * rebuilds the actual radio waveform from that (each station modulated by its own programme, plus
 * noise, interferers and storm crashes) and demodulates it like a real set (see ClientRadio).
 * Empty hand: on/off; sneak + empty hand: tune.
 */
public class BroadcastRadioBlockEntity extends com.sparkgap.pgtelegraph.block.PoweredStationBlockEntity implements IHaveGoggleInformation, Tunable, ToneSource, com.sparkgap.pgtelegraph.block.AmpFed {
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

    public static final int BW = 9000, FM_BW = 200_000;
    private long freqHz = 1_000_000L;
    private boolean on = true;
    private AntennaLink outside;
    private int rescan;
    private final Reception.TerrainCache cache = new Reception.TerrainCache();
    // synced
    public String programKey = "", title = "";
    public long startTick;
    public int lengthTicks;
    public float snr = -99;
    private final Object[] sounds = new Object[2];
    /** client: the playing programme sound */
    public Object clientProgram;

    public BroadcastRadioBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    /** 13.8 V DC, like the rest of the station (or a car battery) */
    public static final com.sparkgap.pgtelegraph.block.Spec SPEC = spec(13.8, 25);

    @Override
    public double nominalV() {
        return 13.8;
    }

    @Override
    public double ratedW() {
        return 25;
    }

    @Override
    public com.sparkgap.pgtelegraph.block.Spec spec() {
        return SPEC;
    }

    @Override
    public long freqHz() {
        return freqHz;
    }

    @Override
    public void setFreq(long hz) {
        freqHz = Math.max(RadioBlockEntity.MIN_HZ, Math.min(RadioBlockEntity.MAX_HZ, hz));
        setChanged();
        sendData();
    }

    /** above 30 MHz it is an FM set: 200 kHz wide, capture effect, quiet once the signal is strong */
    public boolean fm() {
        return freqHz >= 30_000_000L;
    }

    public boolean isOn() {
        return on;
    }

    public void toggle() {
        on = !on;
        setChanged();
        sendData();
    }

    /** what the antenna brings in, synced to the client for the simulated loudspeaker */
    public final com.sparkgap.pgtelegraph.radio.RxChannel channel = new com.sparkgap.pgtelegraph.radio.RxChannel();
    /** receiver noise floor in the 500 Hz reference bandwidth (about a 10 dB noise figure) */
    public static final double RX_NOISE_DBM = -137;

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (!level.isClientSide) ampTick();
        if (level.isClientSide) {
            if (on && isPowered()) com.sparkgap.pgtelegraph.client.ClientAccess.radioTick(this);
            return;
        }
        if (!powerTick() || !on) return;
        boolean dirty = channel.track(level); // keying and FSK shifts, the tick they happen
        if (level.getGameTime() % 10 == Math.floorMod(worldPosition.asLong(), 10)) {
            if (--rescan <= 0) {
                rescan = 4;
                outside = AntennaLink.scan(level, worldPosition);
            }
            double f = freqHz / 1e6;
            AntennaLink rl = outside != null && outside.amplifier() == null ? outside
                    : new AntennaLink(worldPosition, AntennaLink.Kind.WHIP, 0.2, f, 1, 0, 0, Double.MAX_VALUE, List.of(), null,
                    RadioMath.DEFAULT_Q, 20, null, 0, null);
            int bw = fm() ? FM_BW : BW;
            dirty |= channel.refresh(level, rl, freqHz, null, worldPosition.asLong(), cache, RX_NOISE_DBM,
                    fm() ? FM_BW / 2 : BW, fm() ? com.sparkgap.pgtelegraph.radio.RxChannel.FM_WIDE : com.sparkgap.pgtelegraph.radio.RxChannel.AM,
                    bw, true);
            var top = channel.strongestInBand();
            double inBand = 10 * Math.log10(RadioMath.fromDb(channel.extDbmHz) + RadioMath.fromDb(channel.rxDbmHz)) + 10 * Math.log10(bw);
            float s = top == null ? -99 : (float) (top.dbm() - inBand);
            BlockPos src = channel.strongestSource();
            if (src != null && s > -6) com.sparkgap.pgtelegraph.media.VoiceAir.listen(level, worldPosition, src, fm() ? com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_WFM : com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_AM, s);
            String key = top == null ? "" : top.key();
            dirty |= !key.equals(programKey) || Math.abs(s - snr) > 1;
            programKey = key;
            title = top == null ? "" : top.title();
            startTick = top == null ? 0 : top.start();
            lengthTicks = top == null ? 0 : top.len();
            snr = s;
        }
        if (dirty) sendData();
    }

    @Override
    public int soundHz() {
        return 700;
    }

    @Override
    public float soundTone() {
        return 0;
    }

    @Override
    public float soundNoise() {
        return 0; // the static is part of the simulated receiver output
    }

    @Override
    public boolean soundOn() {
        return false;
    }

    @Override
    public Object[] clientSounds() {
        return sounds;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal("     " + RadioBlockEntity.formatFreq(freqHz) + (on ? "" : "  OFF")).withStyle(ChatFormatting.AQUA));
        if (!title.isEmpty()) tip.add(Component.literal("     ♫ " + title + "  "
                + Broadcasts.position(level == null ? 0 : level.getGameTime(), startTick, lengthTicks)).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (snr > -99) tip.add(Component.literal(String.format(Locale.ROOT, "     SNR %.0f dB", snr)).withStyle(ChatFormatting.GRAY));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putLong("Freq", freqHz);
        tag.putBoolean("On", on);
        if (clientPacket) {
            tag.putString("Prog", programKey);
            tag.putString("Title", title);
            tag.putLong("Start", startTick);
            tag.putInt("Len", lengthTicks);
            tag.putFloat("Snr", snr);
            tag.put("Rx", channel.save());
            tag.putBoolean("AmpMute", ampMuted);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Freq")) freqHz = tag.getLong("Freq");
        on = !tag.contains("On") || tag.getBoolean("On");
        if (clientPacket) {
            programKey = tag.getString("Prog");
            title = tag.getString("Title");
            startTick = tag.getLong("Start");
            lengthTicks = tag.getInt("Len");
            snr = tag.getFloat("Snr");
            channel.load(tag.getCompound("Rx"));
            ampMuted = tag.getBoolean("AmpMute");
        }
    }
}
