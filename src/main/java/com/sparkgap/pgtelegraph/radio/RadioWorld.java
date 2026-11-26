package com.sparkgap.pgtelegraph.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server-side "ether": every keyed transmitter re-announces its emission each tick; receivers
 * read what was announced this tick or the previous one. Nothing is saved - a transmitter that
 * stops ticking simply stops radiating.
 */
public final class RadioWorld {
    private RadioWorld() {}

    /** eirpDbm already includes coax loss, mismatch and peak antenna gain; aim/beam/floor describe a beam (aim null = omni). */
    public record Emission(BlockPos source, BlockPos antenna, long freqHz, double eirpDbm, double heightM, long tick,
                           net.minecraft.world.phys.Vec3 aim, double beamDeg, double floorDb, double spreadHz,
                           com.sparkgap.pgtelegraph.sat.Satellite sat) {
        public Emission(BlockPos source, BlockPos antenna, long freqHz, double eirpDbm, double heightM, long tick,
                        net.minecraft.world.phys.Vec3 aim, double beamDeg, double floorDb, double spreadHz) {
            this(source, antenna, freqHz, eirpDbm, heightM, tick, aim, beamDeg, floorDb, spreadHz, null);
        }

        public Emission(BlockPos source, BlockPos antenna, long freqHz, double eirpDbm, double heightM, long tick,
                        net.minecraft.world.phys.Vec3 aim, double beamDeg, double floorDb) {
            this(source, antenna, freqHz, eirpDbm, heightM, tick, aim, beamDeg, floorDb, 0, null);
        }
    }

    /** keys for a transmitter's harmonics (2f, 3f) in the ether */
    public static BlockPos harmonicKey(BlockPos source, int n) {
        return source.offset(0, 100_000 * n, 0);
    }

    private static final Map<Level, Map<BlockPos, Emission>> ETHER = new WeakHashMap<>();

    public static void emit(Level level, Emission e) {
        ETHER.computeIfAbsent(level, l -> new HashMap<>()).put(e.source(), e);
    }

    public static void silence(Level level, BlockPos source) {
        Map<BlockPos, Emission> m = ETHER.get(level);
        if (m != null) {
            m.remove(source);
            m.remove(harmonicKey(source, 2));
            m.remove(harmonicKey(source, 3));
        }
    }

    /** remove exactly one key (not its harmonics) */
    public static void silence1(Level level, BlockPos key) {
        Map<BlockPos, Emission> m = ETHER.get(level);
        if (m != null) m.remove(key);
    }

    private static final Map<Level, Map<BlockPos, Double>> ANT_BW = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Integer>> MODULATION = new WeakHashMap<>();
    /** what a radiating block puts on its carrier: nothing (CW / FSK keying), AM, FM or SSB voice */
    public static final int MOD_CARRIER = 0, MOD_AM = 1, MOD_FM = 2, MOD_SSB = 3, MOD_TV = 4,
            /** broadcast FM: +-75 kHz deviation, about 200 kHz wide */
            MOD_WFM = 5,
            /** a jammer: noise spread over its bandwidth - it raises receivers' noise floor */
            MOD_NOISE = 6,
            /** a bare carrier keyed hard (no shaping): key clicks splash a few kHz either side */
            MOD_CLICKY = 7;

    public static void setModulation(Level level, BlockPos source, int mod) {
        MODULATION.computeIfAbsent(level, l -> new HashMap<>()).put(source.immutable(), mod);
    }

    public static int modulation(Level level, BlockPos source) {
        Map<BlockPos, Integer> m = MODULATION.get(level);
        Integer v = m == null ? null : m.get(source);
        return v == null ? MOD_CARRIER : v;
    }

    /**
     * Bandwidth (Hz) of the antenna system a transmitter radiates through: f0 / Q of its loaded
     * antenna. A short, heavily loaded antenna on LF/MF is so narrow that it cuts the sidebands - the
     * programme comes out muffled. 0 = unknown / wide.
     */
    public static void setAntennaBandwidth(Level level, BlockPos source, double hz) {
        ANT_BW.computeIfAbsent(level, l -> new HashMap<>()).put(source.immutable(), hz);
    }

    public static double antennaBandwidth(Level level, BlockPos source) {
        Map<BlockPos, Double> m = ANT_BW.get(level);
        Double v = m == null ? null : m.get(source);
        return v == null ? 0 : v;
    }

    /** the emission from {@code source} on the air this tick, or null */
    public static Emission current(Level level, BlockPos source) {
        Map<BlockPos, Emission> m = ETHER.get(level);
        Emission e = m == null ? null : m.get(source);
        return e != null && level.getGameTime() - e.tick() <= 1 ? e : null;
    }

    /** is the emission from {@code source} on the air this tick (a keyed transmitter, for instance) */
    public static boolean live(Level level, BlockPos source) {
        Map<BlockPos, Emission> m = ETHER.get(level);
        Emission e = m == null ? null : m.get(source);
        return e != null && level.getGameTime() - e.tick() <= 1;
    }

    public static Collection<Emission> on(Level level) {
        Map<BlockPos, Emission> m = ETHER.get(level);
        if (m == null) return java.util.List.of();
        long now = level.getGameTime();
        m.values().removeIf(e -> now - e.tick() > 1);
        return m.values();
    }
}
