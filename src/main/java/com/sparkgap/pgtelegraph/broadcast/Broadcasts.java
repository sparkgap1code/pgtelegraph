package com.sparkgap.pgtelegraph.broadcast;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What every broadcast transmitter on the air is playing, keyed like its emission (by the block
 * that radiates: the transmitter, or the amplifier it drives). Refreshed every tick while on the air.
 */
public final class Broadcasts {
    private Broadcasts() {}

    /**
     * @param audioId imported recording (null for a music disc)
     * @param sound   music disc sound event id (null for a recording)
     */
    public record Program(String audioId, String sound, long startTick, int lengthTicks, String title, long tick) {
        public String key() {
            return audioId != null ? "a:" + audioId : "s:" + sound;
        }
    }

    /** "m:ss / m:ss": where the station's record is now (it has been turning since startTick) */
    public static String position(long now, long startTick, int lengthTicks) {
        if (lengthTicks <= 0) return "";
        long t = Math.floorMod(now - startTick, (long) lengthTicks) / 20, l = lengthTicks / 20;
        return String.format(java.util.Locale.ROOT, "%d:%02d / %d:%02d", t / 60, t % 60, l / 60, l % 60);
    }

    private static final Map<Level, Map<BlockPos, Program>> ON_AIR = new WeakHashMap<>();

    public static void put(Level level, BlockPos source, Program p) {
        ON_AIR.computeIfAbsent(level, l -> new HashMap<>()).put(source, p);
    }

    public static Program get(Level level, BlockPos source) {
        Map<BlockPos, Program> m = ON_AIR.get(level);
        if (m == null) return null;
        Program p = m.get(source);
        if (p == null || level.getGameTime() - p.tick() > 2) return null;
        return p;
    }
}
