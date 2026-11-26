package com.sparkgap.pgtelegraph.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * DTMF (touch-tone) digits riding on a voice carrier. A sender marks "this emission carries digit
 * c" for a few ticks; a DTMF decoder behind a receiver that is hearing that emission picks it up.
 * Digits: 0-9, *, #, A-D.
 */
public final class Dtmf {
    private Dtmf() {}

    public static final String KEYS = "123A456B789C*0#D";
    /** how long one key press stays on the air (ticks) */
    public static final int TONE_TICKS = 6;

    private record Tone(char c, long until, long seq) {}

    private static final Map<Level, Map<BlockPos, Tone>> ON_AIR = new WeakHashMap<>();
    private static long seq;

    public static boolean valid(char c) {
        return KEYS.indexOf(c) >= 0;
    }

    /** {@code source} (an emission key) sends digit c now */
    public static void send(Level level, BlockPos source, char c) {
        if (!valid(c)) return;
        ON_AIR.computeIfAbsent(level, l -> new HashMap<>()).put(source.immutable(), new Tone(c, level.getGameTime() + TONE_TICKS, ++seq));
    }

    /** is source sending a digit right now */
    public static boolean sending(Level level, BlockPos source) {
        Map<BlockPos, Tone> m = ON_AIR.get(level);
        Tone t = m == null ? null : m.get(source);
        return t != null && t.until >= level.getGameTime();
    }

    /** the press source is sending now: its digit and a number that changes with every new press; null if silent */
    public static long[] heard(Level level, BlockPos source) {
        Map<BlockPos, Tone> m = ON_AIR.get(level);
        if (m == null) return null;
        Tone t = m.get(source);
        if (t == null) return null;
        if (t.until < level.getGameTime()) {
            if (t.until < level.getGameTime() - 200) m.remove(source);
            return null;
        }
        return new long[]{t.c, t.seq};
    }
}
