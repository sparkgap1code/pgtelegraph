package com.sparkgap.pgtelegraph.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * What a receiver's antenna brings in, signal by signal, for receivers whose loudspeaker is a
 * real-time simulation of the set (broadcast radio, station receiver, handheld). The server works
 * out the channel every half second (path loss, sky-wave share and spreads, noise densities) and
 * watches the signals in it every tick, so keying and FSK shifts reach the client the tick they
 * happen. The client rebuilds the waveform and demodulates it (ClientRadio).
 */
public final class RxChannel {
    /** demodulator of the set */
    public static final int CW = 0, AM = 1, FM_WIDE = 2, FM_NARROW = 3, SSB = 4;

    /**
     * One signal: its programme (key "" = none: a bare carrier), median power at the receiver input
     * (dBm), offset from the dial (Hz), sky-wave share and Watterson spreads, and whether it is on the
     * air this tick.
     */
    public record Sig(String key, long start, int len, float dbm, int df, float skyShare, float delayMs, float dopplerHz,
                      boolean on, long fadeSeed, float antBwHz, int modulation, String title) {
        Sig with(boolean on, int df) {
            return new Sig(key, start, len, dbm, df, skyShare, delayMs, dopplerHz, on, fadeSeed, antBwHz, modulation, title);
        }
    }

    public static final int MAX_SIGNALS = 4;

    // synced
    public volatile List<Sig> signals = List.of();
    public volatile float extDbmHz = -170, rxDbmHz = -170, atmospheric;
    public volatile boolean thunder;
    public volatile int mode = AM, bwHz = 9000;
    /** squelch: false mutes the speaker (FM / CW sets with the squelch closed) */
    public volatile boolean open = true;
    /** the set itself is keyed (handheld): its own sidetone instead of reception */
    public volatile boolean sidetone;
    /** receiver knobs (client side): AGC 0 fast / 1 slow / 2 off, noise blanker */
    public volatile int agcMode = 1;
    public volatile boolean blanker;
    /** client: bumped whenever an update arrives */
    public volatile int updates;

    // server
    private final List<BlockPos> watch = new ArrayList<>();
    private final List<Long> watchHz = new ArrayList<>();
    /** the dial frequency (synced; sets on the same frequency nearby share one simulation) */
    public volatile long dialHz;

    /**
     * Recompute the channel (call every 10 ticks or so).
     *
     * @return true when it changed enough to be worth sending
     */
    private long lastRefresh = -1000;

    public boolean refresh(Level level, AntennaLink rl, long dialHz, BlockPos ignore, long seed, Reception.TerrainCache cache,
                           double rxNoiseDbm, int spanHz, int mode, int bwHz, boolean open) {
        return refresh(level, rl, dialHz, 0, ignore, seed, cache, rxNoiseDbm, spanHz, mode, bwHz, open);
    }

    public boolean refresh(Level level, AntennaLink rl, long dialHz, double extraDb, BlockPos ignore, long seed, Reception.TerrainCache cache,
                           double rxNoiseDbm, int spanHz, int mode, int bwHz, boolean open) {
        long tNow = level.getGameTime();
        // overloaded server: the channel is worked out four times less often (same dial, same mode)
        if (RadioLoad.simple() && tNow - lastRefresh < 40 && this.dialHz == dialHz && this.mode == mode && this.open == open) return false;
        lastRefresh = tNow;
        this.dialHz = dialHz;
        Reception.Channel ch = Reception.channel(level, rl, dialHz, extraDb, ignore, seed, cache, rxNoiseDbm, spanHz, MAX_SIGNALS);
        List<Sig> now = new ArrayList<>();
        watch.clear();
        watchHz.clear();
        for (Reception.Heard h : ch.signals()) {
            com.sparkgap.pgtelegraph.broadcast.Broadcasts.Program p = com.sparkgap.pgtelegraph.broadcast.Broadcasts.get(level, h.e().source());
            now.add(new Sig(p == null ? "" : p.key(), p == null ? 0 : p.startTick(), p == null ? 0 : p.lengthTicks(),
                    (float) h.dbm(), h.df(), h.skyShare(), h.delayMs(), h.dopplerHz(), true, h.fadeSeed(), h.antBwHz(),
                    p != null ? (dialHz >= 30_000_000L ? RadioWorld.MOD_WFM : RadioWorld.MOD_AM) : RadioWorld.modulation(level, h.e().source()),
                    p == null ? "" : p.title()));
            watch.add(h.e().source());
            watchHz.add(h.e().freqHz());
        }
        boolean changed = now.size() != signals.size() || this.mode != mode || this.bwHz != bwHz || this.open != open
                || Math.abs(ch.extDbmPerHz() - extDbmHz) > 1 || Math.abs(ch.rxDbmPerHz() - rxDbmHz) > 1;
        for (int i = 0; !changed && i < now.size(); i++) {
            Sig a = now.get(i), b = signals.get(i);
            changed = !a.key().equals(b.key()) || a.start() != b.start() || Math.abs(a.dbm() - b.dbm()) > 1 || a.df() != b.df()
                    || Math.abs(a.skyShare() - b.skyShare()) > 0.1;
        }
        signals = List.copyOf(now);
        extDbmHz = (float) ch.extDbmPerHz();
        rxDbmHz = (float) ch.rxDbmPerHz();
        double f = dialHz / 1e6;
        boolean storm = level.isThundering() && f < 12;
        changed |= storm != thunder;
        thunder = storm;
        atmospheric = (float) RadioMath.atmosphericShare(f);
        this.mode = mode;
        this.bwHz = bwHz;
        this.open = open;
        return changed;
    }

    /**
     * Every tick: is each signal still on the air, and on which frequency (FSK shifts, keying)?
     *
     * @return true when something changed - send it now, so keying is heard as it happens
     */
    public boolean track(Level level) {
        if (watch.isEmpty()) return false;
        List<Sig> cur = signals;
        List<Sig> next = null;
        for (int i = 0; i < watch.size() && i < cur.size(); i++) {
            RadioWorld.Emission e = RadioWorld.current(level, watch.get(i));
            boolean on = e != null;
            int df = cur.get(i).df();
            if (on && e.freqHz() != watchHz.get(i)) {
                df += (int) (e.freqHz() - watchHz.get(i));
                watchHz.set(i, e.freqHz());
            }
            if (on != cur.get(i).on() || df != cur.get(i).df()) {
                if (next == null) next = new ArrayList<>(cur);
                next.set(i, cur.get(i).with(on, df));
            }
        }
        if (next == null) return false;
        signals = List.copyOf(next);
        return true;
    }

    public void clear() {
        signals = List.of();
        watch.clear();
        watchHz.clear();
    }

    /** the strongest signal inside the passband, or null */
    public Sig strongestInBand() {
        for (Sig s : signals) if (Math.abs(s.df()) <= bwHz / 2) return s;
        return null;
    }

    public BlockPos strongestSource() {
        List<Sig> cur = signals;
        for (int i = 0; i < cur.size() && i < watch.size(); i++) if (Math.abs(cur.get(i).df()) <= bwHz / 2) return watch.get(i);
        return null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Ne", extDbmHz);
        tag.putFloat("Nr", rxDbmHz);
        tag.putFloat("At", atmospheric);
        tag.putBoolean("Thu", thunder);
        tag.putByte("Mode", (byte) mode);
        tag.putInt("Bw", bwHz);
        tag.putBoolean("Open", open);
        tag.putBoolean("Side", sidetone);
        tag.putLong("Dial", dialHz);
        ListTag l = new ListTag();
        for (Sig g : signals) {
            CompoundTag c = new CompoundTag();
            c.putString("K", g.key());
            c.putLong("S", g.start());
            c.putInt("L", g.len());
            c.putFloat("D", g.dbm());
            c.putInt("F", g.df());
            c.putFloat("Y", g.skyShare());
            c.putFloat("T", g.delayMs());
            c.putFloat("P", g.dopplerHz());
            c.putBoolean("O", g.on());
            c.putLong("Z", g.fadeSeed());
            c.putFloat("W", g.antBwHz());
            c.putByte("V", (byte) g.modulation());
            l.add(c);
        }
        tag.put("Sig", l);
        return tag;
    }

    public void load(CompoundTag tag) {
        extDbmHz = tag.contains("Ne") ? tag.getFloat("Ne") : -170;
        rxDbmHz = tag.contains("Nr") ? tag.getFloat("Nr") : -170;
        atmospheric = tag.getFloat("At");
        thunder = tag.getBoolean("Thu");
        mode = tag.getByte("Mode");
        bwHz = tag.contains("Bw") ? tag.getInt("Bw") : 9000;
        open = !tag.contains("Open") || tag.getBoolean("Open");
        sidetone = tag.getBoolean("Side");
        dialHz = tag.getLong("Dial");
        List<Sig> l = new ArrayList<>();
        for (Tag t : tag.getList("Sig", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            l.add(new Sig(c.getString("K"), c.getLong("S"), c.getInt("L"), c.getFloat("D"), c.getInt("F"), c.getFloat("Y"),
                    c.getFloat("T"), c.getFloat("P"), !c.contains("O") || c.getBoolean("O"), c.getLong("Z"), c.getFloat("W"), c.getByte("V"), ""));
        }
        signals = List.copyOf(l);
        updates++;
    }
}
