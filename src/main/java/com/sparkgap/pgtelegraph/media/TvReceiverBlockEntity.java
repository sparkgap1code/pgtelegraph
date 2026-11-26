package com.sparkgap.pgtelegraph.media;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sparkgap.pgtelegraph.block.RadioBlockEntity;
import com.sparkgap.pgtelegraph.block.Tunable;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import com.sparkgap.pgtelegraph.radio.Reception;
import com.sparkgap.pgtelegraph.radio.Terrain;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/**
 * Analog TV set (TV 수상기). The server finds the TV station on the dial, works out how the picture
 * arrives - signal over noise in the 4.2 MHz video band, ghosts from paths bent round hills,
 * another station on the same channel - and passes on the station's frames to the players watching.
 * The set itself (client) shows the picture with what an analog set does to a poor signal: snow,
 * colour fading out, ghosts, lines tearing sideways, the picture rolling, venetian-blind bars.
 */
public class TvReceiverBlockEntity extends com.sparkgap.pgtelegraph.block.PoweredStationBlockEntity implements IHaveGoggleInformation, Tunable {
    public static final double VIDEO_BW = 4_200_000;
    private long freqHz = 55_250_000L;
    private boolean on = true;
    private AntennaLink outside;
    private int rescan, terrainScan;
    private double obstruction;
    private final Reception.TerrainCache cache = new Reception.TerrainCache();
    private BlockPos tx;
    private int sentSeq = -1;
    // synced: what the picture goes through
    public boolean tuned;
    public float snr = -99, ghost, interf = -99;
    public int interfDf;
    // client
    public int[] picture;
    public int picW, picH, picSeq;
    public Object clientTex;

    public TvReceiverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    /** mains: 230 V, 80 W */
    public static final com.sparkgap.pgtelegraph.block.Spec SPEC = spec(230, 80);

    @Override
    public double nominalV() {
        return 230;
    }

    @Override
    public double ratedW() {
        return 80;
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
        sentSeq = -1;
        setChanged();
        sendData();
    }

    public boolean isOn() {
        return on;
    }

    public void toggle() {
        on = !on;
        setChanged();
        sendData();
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide && !powerTick()) return;
        if (level == null || level.isClientSide || !on) return;
        int every = com.sparkgap.pgtelegraph.radio.RadioLoad.every(10);
        if (level.getGameTime() % every == Math.floorMod(worldPosition.asLong(), every)) look();
        // pass on the station's newest frame to whoever is watching
        if (tuned && tx != null) { // server-made frames (fallback, satellites) go out here
            TvFrames.Frame f = TvFrames.frame(level, tx);
            if (f != null) send(f);
        }
    }

    /** picture key -> the TV sets tuned to it (so a frame goes out the moment it arrives) */
    private static final java.util.Map<net.minecraft.world.level.Level, java.util.Map<BlockPos, java.util.Set<BlockPos>>> WATCH =
            new java.util.WeakHashMap<>();

    private void watch(BlockPos oldKey, BlockPos newKey) {
        if (java.util.Objects.equals(oldKey, newKey)) return;
        var m = WATCH.computeIfAbsent(level, l -> new java.util.HashMap<>());
        if (oldKey != null && m.containsKey(oldKey)) m.get(oldKey).remove(worldPosition);
        if (newKey != null) m.computeIfAbsent(newKey, k -> new java.util.HashSet<>()).add(worldPosition.immutable());
    }

    static void push(net.minecraft.world.level.Level level, BlockPos key, TvFrames.Frame f) {
        var m = WATCH.get(level);
        var sets = m == null ? null : m.get(key);
        if (sets == null) return;
        sets.removeIf(p -> !(level.isLoaded(p) && level.getBlockEntity(p) instanceof TvReceiverBlockEntity be && key.equals(be.tx)));
        for (BlockPos p : sets) if (level.getBlockEntity(p) instanceof TvReceiverBlockEntity be) be.send(f);
    }

    /** players near the set who have this stream from its last key frame on */
    private final java.util.Set<java.util.UUID> synced = new java.util.HashSet<>();
    private BlockPos syncedKey;

    private void send(TvFrames.Frame f) {
        if (!on || !tuned || snr <= -6 || f.seq() == sentSeq) return;
        if (f.seq() != sentSeq + 1 || !java.util.Objects.equals(syncedKey, tx)) synced.clear(); // a gap: everyone starts again
        syncedKey = tx;
        sentSeq = f.seq();
        boolean key = TvCodec.isKey(f.jpeg());
        java.util.Set<java.util.UUID> near = new java.util.HashSet<>();
        for (net.minecraft.server.level.ServerPlayer p : ((ServerLevel) level).players()) {
            if (p.distanceToSqr(worldPosition.getCenter()) > 48 * 48) continue;
            near.add(p.getUUID());
            if (key || synced.contains(p.getUUID())) PacketDistributor.sendToPlayer(p, new TvNet.Down(worldPosition, f.seq(), f.jpeg()));
            else for (TvFrames.Frame g : TvFrames.gop(level, tx)) // catch up from the key frame
                if (g.seq() <= f.seq()) PacketDistributor.sendToPlayer(p, new TvNet.Down(worldPosition, g.seq(), g.jpeg()));
        }
        synced.retainAll(near);
        synced.addAll(near);
    }

    /** the picture key of the station it shows (null when none) */
    public BlockPos picKey() {
        return tx;
    }

    private void look() {
        if (--rescan <= 0) {
            rescan = 4;
            outside = AntennaLink.scan(level, worldPosition);
        }
        double f = freqHz / 1e6;
        // rabbit ears when nothing better is connected
        AntennaLink rl = outside != null && outside.amplifier() == null ? outside
                : new AntennaLink(worldPosition, AntennaLink.Kind.VHF_WHIP, 0.6, f, 1.5, 0, 0, Double.MAX_VALUE, List.of(), null,
                3, 3, null, 0, null);
        Reception.Channel ch = Reception.channel(level, rl, freqHz, null, worldPosition.asLong(), cache, -134, 3_000_000, 4);
        Reception.Heard station = null, other = null;
        for (Reception.Heard h : ch.signals()) {
            boolean isTv = RadioWorld.modulation(level, h.e().source()) == RadioWorld.MOD_TV;
            if (station == null && isTv && Math.abs(h.df()) <= 500_000) station = h;
            else if (other == null && Math.abs(h.df()) <= 1_000_000) other = h;
        }
        boolean was = tuned;
        BlockPos oldTx = tx;
        float oldSnr = snr, oldGhost = ghost, oldInt = interf;
        tuned = station != null;
        if (station != null) {
            double noise = ch.noiseDbmPerHz() + 10 * Math.log10(VIDEO_BW);
            // fading (sky wave, or a moving path) exactly as the radios' loudspeakers play it
            double fade = com.sparkgap.pgtelegraph.radio.Fading.carrierGain(station.fadeSeed(), level.getGameTime() / 20.0,
                    station.dopplerHz(), station.skyShare());
            snr = (float) (station.dbm() + 10 * Math.log10(Math.max(1e-6, fade)) - noise);
            if (--terrainScan <= 0) {
                terrainScan = 4;
                obstruction = Terrain.obstructionDb(level, station.e().antenna(), rl.feed(), f);
            }
            ghost = (float) Math.max(0, Math.min(0.6, obstruction / 30 + 0.5 * station.skyShare()));
            interf = other == null ? -99 : (float) (other.dbm() - station.dbm());
            interfDf = other == null ? 0 : other.df() - station.df();
            tx = TvTransmitterBlockEntity.TvAir.camera(level, station.e().source()); // whose picture it carries
        } else {
            snr = -99;
            tx = null;
        }
        watch(oldTx, tx);
        if (was != tuned || Math.abs(oldSnr - snr) > 1 || Math.abs(oldGhost - ghost) > 0.03 || Math.abs(oldInt - interf) > 2) sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        tip.add(Component.literal("     " + RadioBlockEntity.formatFreq(freqHz) + (on ? "" : "  OFF")).withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("     ").append(tuned
                ? Component.literal(String.format(Locale.ROOT, "SNR %.0f dB", snr))
                : Component.translatable("pgtelegraph.tv.no_station")).withStyle(tuned ? ChatFormatting.GRAY : ChatFormatting.RED));
        if (tuned) {
            if (ghost > 0.02f) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.tv.ghost",
                    String.format(Locale.ROOT, "%.0f", ghost * 55))).withStyle(ChatFormatting.GRAY));
            if (interf > -40) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.tv.cochannel",
                    String.format(Locale.ROOT, "%.0f", -interf))).withStyle(ChatFormatting.GOLD));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putLong("Freq", freqHz);
        tag.putBoolean("On", on);
        if (clientPacket) {
            tag.putBoolean("Tuned", tuned);
            tag.putFloat("Snr", snr);
            tag.putFloat("Ghost", ghost);
            tag.putFloat("Int", interf);
            tag.putInt("IntDf", interfDf);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Freq")) freqHz = tag.getLong("Freq");
        on = !tag.contains("On") || tag.getBoolean("On");
        if (clientPacket) {
            tuned = tag.getBoolean("Tuned");
            snr = tag.getFloat("Snr");
            ghost = tag.getFloat("Ghost");
            interf = tag.getFloat("Int");
            interfDf = tag.getInt("IntDf");
        }
    }

    static double unused() {
        return RadioMath.LOS_MHZ;
    }
}
