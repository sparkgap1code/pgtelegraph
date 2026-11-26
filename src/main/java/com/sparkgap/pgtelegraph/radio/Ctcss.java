package com.sparkgap.pgtelegraph.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * CTCSS (sub-audible tone squelch, 톤 스켈치): a transmitter adds one of the 38 standard EIA tones
 * under its signal; a receiver set to a tone only opens for signals carrying that tone. Index 0 = no tone.
 */
public final class Ctcss {
    private Ctcss() {}

    public static final double[] HZ = {0, 67.0, 71.9, 74.4, 77.0, 79.7, 82.5, 85.4, 88.5, 91.5, 94.8, 97.4, 100.0, 103.5,
            107.2, 110.9, 114.8, 118.8, 123.0, 127.3, 131.8, 136.5, 141.3, 146.2, 151.4, 156.7, 162.2, 167.9, 173.8,
            179.9, 186.2, 192.8, 203.5, 210.7, 218.1, 225.7, 233.6, 241.8, 250.3};
    public static final int COUNT = HZ.length;

    private record Mark(int tone, long tick) {}

    private static final Map<Level, Map<BlockPos, Mark>> ON_AIR = new WeakHashMap<>();

    public static int clamp(int i) {
        return Math.floorMod(i, COUNT);
    }

    public static String label(int i) {
        i = clamp(i);
        return i == 0 ? "OFF" : String.format(Locale.ROOT, "%.1f Hz", HZ[i]);
    }

    /** every tick while keyed: {@code source} carries tone i (0 clears) */
    public static void mark(Level level, BlockPos source, int i) {
        Map<BlockPos, Mark> m = ON_AIR.computeIfAbsent(level, l -> new HashMap<>());
        if (i <= 0) m.remove(source);
        else m.put(source.immutable(), new Mark(clamp(i), level.getGameTime()));
    }

    /** the tone the emission from source carries now, 0 for none */
    public static int tone(Level level, BlockPos source) {
        Map<BlockPos, Mark> m = ON_AIR.get(level);
        Mark k = m == null ? null : m.get(source);
        if (k == null) return 0;
        if (k.tick < level.getGameTime() - 2) {
            if (k.tick < level.getGameTime() - 200) m.remove(source);
            return 0;
        }
        return k.tone;
    }

    /** does a receiver set to {@code want} open for this emission */
    public static boolean passes(Level level, int want, RadioWorld.Emission e) {
        return want <= 0 || (e != null && tone(level, e.source()) == want);
    }
}
