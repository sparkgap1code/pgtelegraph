package com.sparkgap.pgtelegraph.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * APRS position reports (위치 보고): a tracker sends a short 1200-baud AFSK burst on 144.390 MHz FM
 * every 30 s with its name and position. A station that hears the burst (6 dB above the noise,
 * the strongest one if two collide) plots it - but only if the burst carries the station's
 * network key: two items, set the way a Create redstone link sets its frequency.
 */
public final class Aprs {
    private Aprs() {}

    public static final long FREQ = 144_390_000L;
    public static final int PERIOD = 600, BURST = 10;
    public static final int PERSON = 0, VEHICLE = 1, FIXED = 2;

    public record Packet(String name, double x, double y, double z, String key, int kind) {}

    private record Sent(Packet p, long tick) {}

    private static final Map<Level, Map<BlockPos, Sent>> ON_AIR = new WeakHashMap<>();

    /** the network key of two frequency items ("" for an empty slot) */
    public static String key(ItemStack a, ItemStack b) {
        return id(a) + "|" + id(b);
    }

    public static String id(ItemStack s) {
        return s == null || s.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    /** is a sender with this seed in its burst at tick t */
    public static boolean bursting(long t, long seed) {
        return Math.floorMod(t + seed, PERIOD) < BURST;
    }

    /** one tick of a burst from {@code source}: on the air and readable */
    public static void send(Level level, BlockPos source, BlockPos antenna, double heightM, double eirpDbm, Packet p) {
        long t = level.getGameTime();
        RadioWorld.emit(level, new RadioWorld.Emission(source, antenna, FREQ, eirpDbm, heightM, t, null, 360, 0));
        RadioWorld.setModulation(level, source, RadioWorld.MOD_FM);
        ON_AIR.computeIfAbsent(level, l -> new HashMap<>()).put(source.immutable(), new Sent(p, t));
    }

    /** the packet the emission from source carries now, or null */
    public static Packet packet(Level level, BlockPos source) {
        Map<BlockPos, Sent> m = ON_AIR.get(level);
        Sent s = m == null ? null : m.get(source);
        if (s == null) return null;
        if (s.tick < level.getGameTime() - 1) {
            if (s.tick < level.getGameTime() - 1200) m.remove(source);
            return null;
        }
        return s.p;
    }
}
