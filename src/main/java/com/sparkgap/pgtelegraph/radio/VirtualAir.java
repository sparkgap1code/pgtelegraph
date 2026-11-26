package com.sparkgap.pgtelegraph.radio;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.tape.Morse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Stations that are not in the world but are on the air: placed where they really are on the globe
 * (the world's origin is 37.5 N 127 E) so their signals come in by the same ground-wave / sky-wave
 * model as anyone's - they fade, skip and close with the ionosphere.
 * <ul>
 *   <li>the NCDXF/IARU beacon network: 18 beacons, each 10 s on 14.100, 18.110, 21.150, 24.930 and
 *   28.200 MHz in turn (a 3-minute cycle): callsign at 22 wpm, then four 1 s dashes at 100 W,
 *   10 W, 1 W and 0.1 W</li>
 *   <li>band activity: a few CW stations calling CQ or working each other in each HF CW segment
 *   (some keyed hard enough to click)</li>
 *   <li>callers for the QSO robot's pileup and DX modes</li>
 * </ul>
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class VirtualAir {
    private VirtualAir() {}

    /** one far transmitter sending one over */
    public static final class Tx {
        public final BlockPos key, antenna;
        public final long hz;
        public final String text;
        final boolean[] marks;
        final double[] eirp;
        final long start;
        final boolean clicky;
        public final String owner;

        Tx(BlockPos key, BlockPos antenna, long hz, boolean[] marks, double[] eirp, long start, boolean clicky, String text, String owner) {
            this.key = key;
            this.antenna = antenna;
            this.hz = hz;
            this.marks = marks;
            this.eirp = eirp;
            this.start = start;
            this.clicky = clicky;
            this.text = text;
            this.owner = owner;
        }

        public boolean done(long t) {
            return t - start >= marks.length;
        }
    }

    private static final Map<Level, List<Tx>> AIR = new WeakHashMap<>();
    private static int nextKey;

    // ------------------------------------------------------------------ geometry
    /** a block position at great-circle distance and bearing from the world's origin, for a real place */
    public static BlockPos at(double latDeg, double lonDeg) {
        double la0 = Math.toRadians(com.sparkgap.pgtelegraph.sat.Orbit.LAT0), lo0 = Math.toRadians(com.sparkgap.pgtelegraph.sat.Orbit.LON0);
        double la = Math.toRadians(latDeg), lo = Math.toRadians(lonDeg);
        double c = Math.sin(la0) * Math.sin(la) + Math.cos(la0) * Math.cos(la) * Math.cos(lo - lo0);
        double km = 6371 * Math.acos(Math.max(-1, Math.min(1, c)));
        double brg = Math.atan2(Math.sin(lo - lo0) * Math.cos(la), Math.cos(la0) * Math.sin(la) - Math.sin(la0) * Math.cos(la) * Math.cos(lo - lo0));
        double b = blocksFor(km);
        return BlockPos.containing(Math.sin(brg) * b, 64, -Math.cos(brg) * b);
    }

    /** a position at a bearing (degrees) and distance (km) */
    public static BlockPos at(double brgDeg, double km, boolean polar) {
        double b = blocksFor(km), r = Math.toRadians(brgDeg);
        return BlockPos.containing(Math.sin(r) * b, 64, -Math.cos(r) * b);
    }

    /** blocks from the origin that the propagation model reads as {@code km} */
    static double blocksFor(double km) {
        double lo = 1, hi = 1e8;
        for (int i = 0; i < 80; i++) {
            double mid = Math.sqrt(lo * hi);
            if (RadioMath.effectiveKm(mid) < km) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    // ------------------------------------------------------------------ keying
    /** tick-by-tick key-down pattern of {@code text} at {@code wpm} */
    public static boolean[] pattern(String text, int wpm) {
        List<Integer> runs = Morse.encodeUnits(Morse.codes(text));
        double unitTicks = 24.0 / wpm, t = 0;
        List<double[]> spans = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            double len = runs.get(i) * unitTicks;
            if (i % 2 == 0) spans.add(new double[]{t, t + len});
            t += len;
        }
        boolean[] out = new boolean[(int) Math.ceil(t) + 1];
        for (double[] s : spans) for (int k = (int) Math.round(s[0]); k < Math.max(Math.round(s[0]) + 1, Math.round(s[1])); k++) if (k < out.length) out[k] = true;
        return out;
    }

    public static BlockPos newKey() {
        return new BlockPos(27_000_000 + (nextKey++ & 0xFFFFF), -3_000_000, 1);
    }

    /** put an over on the air: {@code text} at {@code wpm}, from {@code where}, starting now */
    public static Tx send(Level level, BlockPos key, BlockPos where, long hz, String text, int wpm, double eirpDbm, boolean clicky, String owner) {
        boolean[] m = pattern(text, wpm);
        double[] e = new double[m.length];
        java.util.Arrays.fill(e, eirpDbm);
        Tx tx = new Tx(key, where, hz, m, e, level.getGameTime(), clicky, text, owner);
        List<Tx> l = AIR.computeIfAbsent(level, x -> new ArrayList<>());
        l.removeIf(o -> o.key.equals(key));
        l.add(tx);
        return tx;
    }

    public static List<Tx> owned(Level level, String owner) {
        List<Tx> out = new ArrayList<>();
        for (Tx t : AIR.getOrDefault(level, List.of())) if (owner.equals(t.owner) && !t.done(level.getGameTime())) out.add(t);
        return out;
    }

    public static void cancel(Level level, String owner) {
        List<Tx> l = AIR.get(level);
        if (l != null) l.removeIf(t -> owner.equals(t.owner));
    }

    // ------------------------------------------------------------------ the beacon network
    static final String[] NCDXF = {"4U1UN", "VE8AT", "W6WX", "KH6RS", "ZL6B", "VK6RBP", "JA2IGY", "RR9O", "VR2B", "4S7B",
            "ZS6DN", "5Z4B", "4X6TU", "OH2B", "CS3B", "LU4AA", "OA4B", "YV5B"};
    static final double[][] NCDXF_AT = {{40.75, -73.97}, {79.99, -85.9}, {37.15, -121.9}, {20.75, -156.4}, {-41.06, 175.6},
            {-32.1, 116.0}, {34.45, 136.78}, {54.98, 82.9}, {22.27, 114.15}, {6.9, 79.9}, {-25.9, 28.27}, {-1.25, 36.75},
            {32.1, 34.8}, {60.3, 24.4}, {32.8, -17.0}, {-34.6, -58.4}, {-12.1, -77.0}, {9.1, -67.8}};
    public static final long[] NCDXF_HZ = {14_100_000L, 18_110_000L, 21_150_000L, 24_930_000L, 28_200_000L};
    public static final int SLOT_TICKS = 200;

    /** which beacon is on band b in the slot running at tick t */
    public static String ncdxfOn(long t, int band) {
        int slot = (int) Math.floorMod(t / SLOT_TICKS, NCDXF.length);
        return NCDXF[Math.floorMod(slot - band, NCDXF.length)];
    }

    private static void ncdxfSlot(Level level, long t) {
        int slot = (int) Math.floorMod(t / SLOT_TICKS, NCDXF.length);
        for (int b = 0; b < NCDXF_HZ.length; b++) {
            int i = Math.floorMod(slot - b, NCDXF.length);
            boolean[] call = pattern(NCDXF[i], 22);
            int dash = 20, gap = 4, n = call.length + 6 + 4 * (dash + gap);
            boolean[] m = new boolean[Math.min(SLOT_TICKS - 4, n)];
            double[] e = new double[m.length];
            for (int k = 0; k < m.length; k++) {
                if (k < call.length) {
                    m[k] = call[k];
                    e[k] = 52; // 100 W into a vertical
                } else if (k >= call.length + 6) {
                    int d = (k - call.length - 6) / (dash + gap), w = (k - call.length - 6) % (dash + gap);
                    m[k] = d < 4 && w < dash;
                    e[k] = 52 - 10 * d; // 100 W, 10 W, 1 W, 0.1 W
                }
            }
            BlockPos key = new BlockPos(27_500_000 + b, -3_000_000, 2);
            List<Tx> l = AIR.computeIfAbsent(level, x -> new ArrayList<>());
            l.removeIf(o -> o.key.equals(key));
            l.add(new Tx(key, at(NCDXF_AT[i][0], NCDXF_AT[i][1]), NCDXF_HZ[b], m, e, t, false, NCDXF[i], "ncdxf"));
        }
    }

    // ------------------------------------------------------------------ band activity
    static final long[][] CW_SEGMENTS = {{3_500_000L, 3_560_000L}, {7_000_000L, 7_040_000L}, {10_100_000L, 10_130_000L},
            {14_000_000L, 14_060_000L}, {18_068_000L, 18_095_000L}, {21_000_000L, 21_070_000L}, {24_890_000L, 24_915_000L},
            {28_000_000L, 28_070_000L}};
    static final String[] PREFIX = {"W", "K", "N", "VE", "DL", "G", "F", "I", "EA", "OH", "SM", "UA", "JA", "BV", "VK", "ZL",
            "PY", "LU", "ZS", "BY", "DU", "YB", "HS", "VU", "OK", "SP", "HA", "YO", "LZ", "UR", "4X", "JH", "7K", "UA9"};
    static final double[][] PREFIX_AT = {{40, -90}, {38, -100}, {35, -85}, {50, -100}, {51, 10}, {52, -1}, {47, 2}, {42, 12},
            {40, -4}, {61, 25}, {60, 15}, {56, 38}, {35, 139}, {24, 121}, {-33, 151}, {-41, 174}, {-23, -46}, {-34, -58},
            {-26, 28}, {39, 116}, {14, 121}, {-6, 107}, {14, 100}, {28, 77}, {50, 14}, {52, 21}, {47, 19}, {45, 26},
            {42, 25}, {50, 30}, {32, 35}, {35, 135}, {34, 132}, {55, 83}};
    static final int PER_SEGMENT = 3;

    /** a made-up callsign and where it is: {call, lat, lon} */
    public static Object[] randomCall(RandomSource r) {
        int p = r.nextInt(PREFIX.length);
        StringBuilder c = new StringBuilder(PREFIX[p]);
        if (!Character.isDigit(c.charAt(c.length() - 1))) c.append(r.nextInt(10));
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) c.append((char) ('A' + r.nextInt(26)));
        return new Object[]{c.toString(), PREFIX_AT[p][0] + r.nextGaussian() * 3, PREFIX_AT[p][1] + r.nextGaussian() * 4};
    }

    private static void activity(Level level, long t) {
        RandomSource r = level.random;
        List<Tx> l = AIR.computeIfAbsent(level, x -> new ArrayList<>());
        for (int s = 0; s < CW_SEGMENTS.length; s++) {
            String owner = "band" + s;
            int n = 0;
            for (Tx x : l) if (owner.equals(x.owner)) n++;
            if (n >= PER_SEGMENT || r.nextInt(60) != 0) continue;
            Object[] a = randomCall(r);
            String me = (String) a[0];
            String text;
            if (r.nextInt(3) == 0) text = "CQ CQ CQ DE " + me + " " + me + " K";
            else {
                String you = (String) randomCall(r)[0];
                String[] names = {"BOB", "HANS", "YURI", "KEN", "JOSE", "PETE", "ANNA", "TOM", "MIKE", "SAM"};
                text = switch (r.nextInt(4)) {
                    case 0 -> you + " DE " + me + " R TNX FER CALL UR RST 5" + (5 + r.nextInt(5)) + "9 NAME " + names[r.nextInt(names.length)] + " HW? " + you + " DE " + me + " KN";
                    case 1 -> you + " DE " + me + " FB OM RIG HR 100W ANT DIPOLE WX " + (r.nextBoolean() ? "SUNNY" : "RAIN") + " BK";
                    case 2 -> "TU 73 " + you + " DE " + me + " SK";
                    default -> "QRL? " + me;
                };
            }
            long[] seg = CW_SEGMENTS[s];
            long hz = seg[0] + 500 + (long) (r.nextDouble() * (seg[1] - seg[0] - 1000)) / 100 * 100;
            double eirp = 40 + r.nextDouble() * 22; // 10 W to 1.5 kW
            Tx tx = send(level, newKey(), at((Double) a[1], (Double) a[2]), hz, text, 14 + r.nextInt(16), eirp, r.nextInt(5) == 0, owner);
            if (tx.marks.length > 2400) l.remove(tx);
        }
    }

    // ------------------------------------------------------------------ the tick
    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD || sl.players().isEmpty()) return;
        long t = sl.getGameTime();
        if (com.sparkgap.pgtelegraph.TelegraphConfig.virtualStations()) {
            if (t % SLOT_TICKS == 0) ncdxfSlot(sl, t);
            if (t % 5 == 0) activity(sl, t);
        }
        List<Tx> l = AIR.get(sl);
        if (l == null) return;
        for (Iterator<Tx> it = l.iterator(); it.hasNext(); ) {
            Tx x = it.next();
            int k = (int) (t - x.start);
            if (k >= x.marks.length) {
                RadioWorld.silence1(sl, x.key);
                it.remove();
                continue;
            }
            if (k >= 0 && x.marks[k]) {
                RadioWorld.emit(sl, new RadioWorld.Emission(x.key, x.antenna, x.hz, x.eirp[k], 15, t, null, 360, 0));
                RadioWorld.setModulation(sl, x.key, x.clicky ? RadioWorld.MOD_CLICKY : RadioWorld.MOD_CARRIER);
            } else RadioWorld.silence1(sl, x.key);
        }
    }

    public static String describe(Tx t) {
        return String.format(Locale.ROOT, "%s %.3f MHz", t.text, t.hz / 1e6);
    }
}
