package com.sparkgap.pgtelegraph.media;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** SSTV pictures being sent from the ground: the line each radiating block is on right now. */
public final class SstvAir {
    private SstvAir() {}

    /** one line on the air; {@code mode} is the SstvMode ordinal its VIS header announced */
    public record Line(long frame, int line, byte[] row, String from, long tick, int mode) {}

    private static final Map<Level, Map<BlockPos, Line>> ON_AIR = new WeakHashMap<>();

    public static void put(Level level, BlockPos source, Line l) {
        ON_AIR.computeIfAbsent(level, k -> new HashMap<>()).put(source.immutable(), l);
    }

    public static Line get(Level level, BlockPos source) {
        Map<BlockPos, Line> m = ON_AIR.get(level);
        Line l = m == null ? null : m.get(source);
        return l == null || level.getGameTime() - l.tick() > 2 ? null : l;
    }
}
