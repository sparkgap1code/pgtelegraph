package com.sparkgap.pgtelegraph.radio;

import com.sparkgap.pgtelegraph.block.AntennaRodBlock;
import com.sparkgap.pgtelegraph.block.CoaxBlock;
import com.sparkgap.pgtelegraph.block.LinearAmplifierBlock;
import com.sparkgap.pgtelegraph.block.VhfAntennaBlock;
import com.sparkgap.pgtelegraph.block.VhfAntennaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a radio is connected to: the coax run (length, loss, weakest power rating) and the
 * antenna at its end (kind, electrical length, resonance, height above ground, and for beam
 * antennas the direction they point). If the run ends at a linear amplifier instead,
 * {@code amplifier} is set and the amplifier's input is a matched load.
 */
public record AntennaLink(BlockPos feed, Kind kind, double lengthM, double f0MHz, double heightM,
                          int coaxMeters, double coaxLossDb10, double coaxMaxW, List<BlockPos> coaxPath,
                          BlockPos amplifier, double q, double efficiencyDb, BlockPos coil, double spanM,
                          Vec3 aim) {

    /** Plain omni antennas: default Q, no efficiency loss, no aim. */
    public AntennaLink(BlockPos feed, Kind kind, double lengthM, double f0MHz, double heightM, int coaxMeters,
                       double coaxLossDb10, double coaxMaxW, List<BlockPos> coaxPath, BlockPos amplifier) {
        this(feed, kind, lengthM, f0MHz, heightM, coaxMeters, coaxLossDb10, coaxMaxW, coaxPath, amplifier,
                RadioMath.DEFAULT_Q, 0, null, kind == Kind.DIPOLE ? lengthM / 2 + 1 : 0, null);
    }

    public enum Kind {
        VERTICAL(0), DIPOLE(2.15), WHIP(0), SHORT_DIPOLE(2.15),
        /** short vertical tuned by a loading coil (directivity of a short monopole over ground) */
        LOADED_VERTICAL(4.77),
        /** VHF/UHF ground-plane whip */
        VHF_WHIP(2.15),
        /** VHF/UHF Yagi: 12 dBi, 40° beam, 20 dB front-to-back */
        YAGI(12),
        /** 1 m parabolic dish, gain and beam set by frequency */
        DISH(0),
        /** 1 m normal-mode helical whip: resonant but electrically short (efficiency falls on low bands) */
        HELICAL_WHIP(2.15),
        /** tuned small receiving loop: figure-eight, tiny radiation resistance */
        LOOP(1.76),
        /** axial-mode helix, 1 m long: gain and beam from the number of turns at the band */
        HELIX(0),
        /** creative antenna: matched everywhere, lossless, omni */
        CREATIVE(2.15),
        /** 50 Ω dummy load: matched, radiates almost nothing */
        DUMMY(0),
        /** phased array of N patch tiles (N kept in spanM): 5 dBi each + 10 log N, beam 100°/√N */
        ARRAY(5);
        public final double gainDbi;

        Kind(double gainDbi) {
            this.gainDbi = gainDbi;
        }
    }

    private static final int MAX_COAX = 512;
    private static final double DISH_D = 1.0, DISH_EFF = 0.55;

    /** Mismatch + coax loss + radiation efficiency (dB, positive) at f. */
    public double systemLossDb(double fMHz) {
        double g2 = reflection(fMHz);
        double loss = RadioMath.coaxLossDb(coaxLossDb10, fMHz) - 10 * Math.log10(Math.max(1 - g2, 1e-9)) + efficiencyDb;
        if (q < 0) { // behind a tuner
            Fit t = TUNERS.get(this);
            double raw = rawReflection(fMHz), work = t == null ? raw : t.fixMHz > 0 ? rawReflection(t.fixMHz) : raw;
            Tuner spec = t == null ? Tuner.DESK : t.spec;
            if (work <= spec.maxG2) {
                // the coax between tuner and antenna still carries the antenna's SWR: more loss than when matched
                double lb = RadioMath.coaxLossDb(t == null ? coaxLossDb10 : t.beyondDb10, fMHz), a = Math.pow(10, lb / 10);
                double line = 10 * Math.log10((a * a - raw) / (a * (1 - raw))) - lb;
                // and the tuner's own coils and capacitors heat up more the harder they have to work
                double tuner = Math.min(spec.capDb, spec.baseDb + spec.slopeDb * (RadioMath.swr(work) - 1));
                loss += line + tuner;
            }
        }
        return loss;
    }

    /** |Γ|² at f as the radio sees it; an amplifier input is matched, a tuner matches within its range. */
    public double reflection(double fMHz) {
        if (amplifier != null) return 0;
        if (q < 0) {
            Fit t = TUNERS.get(this);
            double raw = rawReflection(fMHz);
            if (t == null) return raw <= Tuner.DESK.maxG2 ? 0 : raw;
            if (t.fixMHz <= 0) return raw <= t.spec.maxG2 ? 0 : raw; // past its range an automatic tuner gives up
            // a manual tuner is set for one frequency: the harder the match there, the narrower it holds
            double work = rawReflection(t.fixMHz);
            if (work > t.spec.maxG2) return raw;
            double qn = 1 + 2 * Math.sqrt(RadioMath.swr(work));
            return Math.min(raw, RadioMath.reflection(fMHz, t.fixMHz, qn));
        }
        return RadioMath.reflection(fMHz, f0MHz, q);
    }

    /** |Γ|² of the antenna itself, tuner or not */
    public double rawReflection(double fMHz) {
        return amplifier != null ? 0 : RadioMath.reflection(fMHz, f0MHz, Math.abs(q));
    }

    /** Peak gain including radiation efficiency (dBi). */
    public double gainDb(double fMHz) {
        if (kind == null) return 0;
        if (kind == Kind.DISH) {
            double lambda = 299.792458 / fMHz;
            return 10 * Math.log10(DISH_EFF * Math.pow(Math.PI * DISH_D / lambda, 2)) + stackDb() - efficiencyDb;
        }
        if (kind == Kind.HELIX) return Math.min(18, 10 * Math.log10(15 * helixTurns(f0MHz) / 4)) + stackDb() - efficiencyDb;
        if (kind == Kind.YAGI) return kind.gainDbi + stackDb() - efficiencyDb;
        if (kind == Kind.ARRAY) return 5 + 10 * Math.log10(Math.max(1, spanM)) - efficiencyDb;
        return kind.gainDbi - efficiencyDb;
    }

    /** Half-power beam width (degrees); 360 for omni antennas. */
    public double beamDeg(double fMHz) {
        double b = beamDeg1(fMHz);
        return stackable() && spanM > 1.01 ? b / Math.sqrt(spanM) : b;
    }

    private double beamDeg1(double fMHz) {
        if (kind == Kind.YAGI) return 40;
        if (kind == Kind.ARRAY) return Math.min(120, 100 / Math.sqrt(Math.max(1, spanM)));
        if (kind == Kind.DISH) return Math.min(60, 70 * (299.792458 / fMHz) / DISH_D);
        if (kind == Kind.HELIX) return Math.min(60, 52 / Math.sqrt(helixTurns(f0MHz) / 4));
        if (kind == Kind.LOOP) return -1; // figure-eight
        return 360;
    }

    /** turns of an axial helix 1 m long with spacing λ/4 */
    static double helixTurns(double fMHz) {
        return Math.max(1, 1.0 / (0.25 * 299.792458 / fMHz));
    }

    /** How far the pattern falls at most (dB below peak). */
    public double floorDb() {
        return kind == Kind.ARRAY ? 25 : kind == Kind.DISH ? 30 : kind == Kind.YAGI || kind == Kind.HELIX ? 20 : kind == Kind.LOOP ? 40 : 0;
    }

    /** Pattern loss (dB) toward a direction, for a beam aimed at {@code aim}. */
    public static double patternLoss(Vec3 aim, double beamDeg, double floorDb, Vec3 toward) {
        if (aim == null || beamDeg >= 360 || toward.lengthSqr() < 1e-9) return 0;
        if (beamDeg < 0) { // small loop: response ∝ |cos| of the angle to the loop plane direction, nulls broadside
            Vec3 a = new Vec3(aim.x, 0, aim.z), t = new Vec3(toward.x, 0, toward.z);
            if (a.lengthSqr() < 1e-9 || t.lengthSqr() < 1e-9) return 0;
            double c = Math.abs(a.normalize().dot(t.normalize()));
            return Math.min(floorDb, -20 * Math.log10(Math.max(c, 1e-3)));
        }
        double cos = aim.normalize().dot(toward.normalize());
        double theta = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
        return Math.min(floorDb, 12 * Math.pow(theta / beamDeg, 2));
    }

    /** Gain (dBi, with efficiency) toward a direction. */
    public double gainToward(double fMHz, Vec3 toward) {
        return gainDb(fMHz) - patternLoss(aim, beamDeg(fMHz), floorDb(), toward);
    }

    public static AntennaLink scan(Level level, BlockPos device) {
        return scan(level, device, null);
    }

    /**
     * Finds the antenna (or linear amplifier / loading coil) reached from a device through coax,
     * or null. Shortest coax run wins; blocks merely touching the device do not count. {@code onlySide} restricts where the search starts.
     */
    public static AntennaLink scan(Level level, BlockPos device, Direction onlySide) {
        // only through coax: an antenna, amplifier or coil merely standing next to the device is not connected
        BlockState devState = level.getBlockState(device);
        if (onlySide == null && com.sparkgap.pgtelegraph.block.Rack.racked(devState)) onlySide = com.sparkgap.pgtelegraph.block.Rack.rear(devState);
        Direction[] sides = onlySide == null ? Direction.values() : new Direction[]{onlySide};
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : sides) {
            BlockPos p = device.relative(d);
            BlockState ps = level.getBlockState(p);
            // coax, or a tuner / LNA / arrester plugged straight into the device
            if (ps.getBlock() instanceof CoaxBlock || passThrough(ps)) {
                parent.put(p, null);
                queue.add(p);
            }
        }
        while (!queue.isEmpty() && parent.size() <= MAX_COAX) {
            BlockPos c = queue.poll();
            BlockState cs = level.getBlockState(c);
            Direction switchExit = null;
            if (cs.getBlock() instanceof com.sparkgap.pgtelegraph.block.CoaxSwitchBlock) {
                switchExit = com.sparkgap.pgtelegraph.block.CoaxSwitchBlock.through(cs, toward(c, parent.get(c)));
                if (switchExit == null) continue;
            }
            for (Direction d : Direction.values()) {
                if (switchExit != null && d != switchExit) continue;
                BlockPos p = c.relative(d);
                if (p.equals(device)) continue;
                BlockState ps = level.getBlockState(p);
                if (ps.getBlock() instanceof com.sparkgap.pgtelegraph.block.CoaxSwitchBlock && !parent.containsKey(p)) {
                    if (com.sparkgap.pgtelegraph.block.CoaxSwitchBlock.through(ps, d.getOpposite()) != null) {
                        parent.put(p, c);
                        queue.add(p);
                    }
                    continue;
                }
                if (passThrough(ps) && !parent.containsKey(p)) {
                    parent.put(p, c); // the tuner sits in the coax run: carry on through it
                    queue.add(p);
                    continue;
                }
                if (isAntenna(ps) || ps.getBlock() instanceof LinearAmplifierBlock
                        || ps.getBlock() instanceof com.sparkgap.pgtelegraph.block.DummyLoadBlock
                        || ps.getBlock() instanceof com.sparkgap.pgtelegraph.block.LoadingCoilBlock) {
                    List<BlockPos> path = new ArrayList<>();
                    for (BlockPos q = c; q != null; q = parent.get(q)) path.add(q);
                    AntennaLink end = endpoint(level, p, path);
                    if (end == null) return null;
                    // path[0] is next to the antenna: the coax before the tuner (antenna side) runs at the antenna's SWR
                    double beyond = 0;
                    for (BlockPos q : path) {
                        BlockState qs = level.getBlockState(q);
                        if (qs.getBlock() instanceof com.sparkgap.pgtelegraph.block.AntennaTunerBlock tb) {
                            double fix = level.getBlockEntity(q) instanceof com.sparkgap.pgtelegraph.block.ManualTunerBlockEntity m ? m.matchMHz() : 0;
                            return end.tuned(beyond, tb.spec, fix);
                        }
                        if (qs.getBlock() instanceof CoaxBlock cb) beyond += cb.grade.lossDbPerM10;
                    }
                    return end;
                }
            }
            final Direction exit = switchExit;
            Iterable<BlockPos> next = exit != null
                    ? (level.getBlockState(c.relative(exit)).getBlock() instanceof CoaxBlock ? List.of(c.relative(exit)) : List.<BlockPos>of())
                    : passThrough(level.getBlockState(c))
                    ? tunerLinks(level, c) : CoaxBlock.links(level, c);
            for (BlockPos n : next) {
                if (com.sparkgap.pgtelegraph.block.Rack.separateLines(level, c, n)) continue; // two racked units' own feed lines
                if (!parent.containsKey(n)) {
                    parent.put(n, c);
                    queue.add(n);
                }
            }
        }
        return null;
    }

    /** side of {@code from} that faces {@code to} (neighbours) */
    static Direction toward(BlockPos from, BlockPos to) {
        if (to == null) return null;
        return Direction.fromDelta(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    /** blocks that sit inside a coax run: tuner, lightning arrester */
    public static boolean passThrough(BlockState s) {
        return s.getBlock() instanceof com.sparkgap.pgtelegraph.block.AntennaTunerBlock
                || s.getBlock() instanceof com.sparkgap.pgtelegraph.block.LightningArresterBlock
                || s.getBlock() instanceof com.sparkgap.pgtelegraph.block.LnaBlock;
    }

    private static List<BlockPos> tunerLinks(Level level, BlockPos c) {
        List<BlockPos> out = new ArrayList<>(6);
        for (Direction d : Direction.values()) {
            if (level.getBlockState(c.relative(d)).getBlock() instanceof CoaxBlock) out.add(c.relative(d));
        }
        return out;
    }

    /**
     * What a tuner can do: the worst SWR it still matches, and its own loss (base + slope per SWR step, capped).
     * Desk automatic 10:1; remote (antenna-mounted) automatic 100:1; manual 64:1 but set by hand for one frequency.
     */
    public record Tuner(double maxSwr, double maxG2, double baseDb, double slopeDb, double capDb) {
        public Tuner(double maxSwr, double baseDb, double slopeDb, double capDb) {
            this(maxSwr, Math.pow((maxSwr - 1) / (maxSwr + 1), 2), baseDb, slopeDb, capDb);
        }

        public static final Tuner DESK = new Tuner(10, 0.3, 0.12, 6), REMOTE = new Tuner(100, 0.3, 0.12, 10),
                MANUAL = new Tuner(64, 0.2, 0.08, 6);
    }

    /** a tuned link: which tuner, the coax loss (dB at 10 MHz) between it and the antenna, and (manual) the frequency it is set for */
    private record Fit(Tuner spec, double beyondDb10, double fixMHz) {}

    private static final java.util.Map<AntennaLink, Fit> TUNERS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Seen through an antenna tuner (q stored negative): within its range the radio sees a match, but the tuner's
     * loss grows with the SWR it fixes and the coax between tuner and antenna still runs at that SWR - so a badly
     * matched antenna wants its tuner at the antenna. Past its range it gives up. A manual tuner matches only
     * around the frequency its knobs are set for.
     */
    public AntennaLink tuned(double beyondDb10, Tuner spec, double fixMHz) {
        if (amplifier != null || kind == Kind.DUMMY) return this;
        AntennaLink t = new AntennaLink(feed, kind, lengthM, f0MHz, heightM, coaxMeters, coaxLossDb10, coaxMaxW, coaxPath,
                null, -Math.max(Math.abs(q), 1e-6), efficiencyDb, coil, spanM, aim);
        TUNERS.put(t, new Fit(spec, beyondDb10, fixMHz));
        return t;
    }

    public AntennaLink tuned(double beyondDb10) {
        return tuned(beyondDb10, Tuner.DESK, 0);
    }

    public AntennaLink tuned() {
        return tuned(coaxLossDb10);
    }

    public boolean isTuned() {
        return q < 0 && kind != Kind.DUMMY;
    }

    private static double[] coax(Level level, List<BlockPos> path) {
        double loss = 0, maxW = Double.MAX_VALUE;
        for (BlockPos q : path) {
            if (level.getBlockState(q).getBlock() instanceof CoaxBlock c) {
                loss += c.grade.lossDbPerM10;
                maxW = Math.min(maxW, c.grade.maxWatts);
            }
        }
        return new double[]{loss, maxW};
    }

    private static AntennaLink endpoint(Level level, BlockPos p, List<BlockPos> path) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof com.sparkgap.pgtelegraph.block.DummyLoadBlock) {
            double[] c = coax(level, path);
            boolean burnt = s.getValue(com.sparkgap.pgtelegraph.block.DummyLoadBlock.BURNT);
            // burnt open: everything comes back (q huge -> |Γ|² ≈ 1)
            return new AntennaLink(p, Kind.DUMMY, 0, 1, 0.5, path.size(), c[0], c[1], path, null,
                    burnt ? 1e9 : 0, 60, null, 0, null);
        }
        if (s.getBlock() instanceof LinearAmplifierBlock) {
            double[] c = coax(level, path);
            return new AntennaLink(p, null, 0, Double.NaN, 0, path.size(), c[0], c[1], path, p);
        }
        if (s.getBlock() instanceof com.sparkgap.pgtelegraph.block.LoadingCoilBlock
                && level.getBlockEntity(p) instanceof com.sparkgap.pgtelegraph.block.LoadingCoilBlockEntity coil) {
            double[] c = coax(level, path);
            var a = coil.analyse();
            if (a == null) return null;
            return new AntennaLink(a.feed(), Kind.LOADED_VERTICAL, a.mastM(), a.f0MHz(), a.heightM(), path.size(),
                    c[0], c[1], path, null, a.q(), a.efficiencyDb(), p, a.spanM(), null);
        }
        if (level.getBlockEntity(p) instanceof com.sparkgap.pgtelegraph.block.CreativeAntennaBlockEntity ca) {
            double[] c = coax(level, path);
            // Q = 0: no reactance at any frequency (f0 only has to be a number)
            return new AntennaLink(p, Kind.CREATIVE, 1, 1, ca.heightM(), path.size(), c[0], c[1], path, null, 0, 0, null, 0, null);
        }
        if (s.getBlock() instanceof VhfAntennaBlock vb) {
            double[] c = coax(level, path);
            double ground = heightAboveGround(level, p);
            VhfAntennaBlockEntity be = level.getBlockEntity(p) instanceof VhfAntennaBlockEntity e ? e : null;
            return switch (vb.type) {
                case WHIP -> new AntennaLink(p, Kind.VHF_WHIP, 0.5, be == null ? 145 : be.bandMHz(), ground,
                        path.size(), c[0], c[1], path, null, 10, 0, null, 0, null);
                case YAGI -> new AntennaLink(p, Kind.YAGI, 1, be == null ? 145 : be.bandMHz(), ground,
                        path.size(), c[0], c[1], path, null, 15, 0, null, stack(level, p, vb, be), be == null ? new Vec3(0, 0, -1) : be.aim());
                case DISH -> new AntennaLink(p, Kind.DISH, 1, 5477, ground,
                        path.size(), c[0], c[1], path, null, 0.1, 0, null, stack(level, p, vb, be), be == null ? new Vec3(0, 0, -1) : be.aim());
                case HELICAL_WHIP -> {
                    double f0 = be == null ? 7.05 : be.bandMHz(), lambda = 299.792458 / f0;
                    double rr = 395 * Math.pow(1.0 / lambda, 2), eta = Math.min(0.9, rr / (rr + 3));
                    double q = Math.min(80, 5 + 0.5 * lambda);
                    yield new AntennaLink(p, Kind.HELICAL_WHIP, 1, f0, ground, path.size(), c[0], c[1], path, null,
                            q, -10 * Math.log10(eta), null, 0, null);
                }
                case LOOP -> {
                    double f0 = be == null ? 0.0172 : be.bandMHz(), lambda = 299.792458 / f0;
                    double rr = 31171 * Math.pow(10 * 1.0 / (lambda * lambda), 2), eta = Math.min(0.9, rr / (rr + 0.5));
                    yield new AntennaLink(p, Kind.LOOP, 1, f0, ground, path.size(), c[0], c[1], path, null,
                            50, -10 * Math.log10(Math.max(eta, 1e-15)), null, 0, be == null ? new Vec3(0, 0, -1) : be.aim());
                }
                case ARRAY -> new AntennaLink(p, Kind.ARRAY, 1, be == null ? 432.1 : be.bandMHz(), ground, path.size(), c[0], c[1], path,
                        null, 8, 0, null, arrayTiles(level, p), be == null ? new Vec3(0, 1, 0) : be.aim());
                case HELIX -> new AntennaLink(p, Kind.HELIX, 1, be == null ? 2304.1 : be.bandMHz(), ground,
                        path.size(), c[0], c[1], path, null, 2, 0, null, stack(level, p, vb, be), be == null ? new Vec3(0, 0, -1) : be.aim());
            };
        }
        return isAntenna(s) ? analyse(level, p, path) : null;
    }

    /**
     * Stacking: beam antennas of the same kind, band and aim standing within 2 blocks of each other
     * (side by side, one above another, or a grid) are fed together through a power divider. N of
     * them give up to 10 log N dB more gain and a beam √N narrower - but only when they are far enough
     * apart: closer than about 0.6 λ their apertures overlap and the gain falls off with the spacing.
     * @return the effective number of antennas (1 = on its own), kept in spanM
     */
    static double stack(Level level, BlockPos start, VhfAntennaBlock vb, VhfAntennaBlockEntity be0) {
        if (be0 == null) return 1;
        Vec3 aim = be0.aim();
        double band = be0.bandMHz();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        double minGap = Double.MAX_VALUE;
        while (!q.isEmpty() && seen.size() < 16) {
            BlockPos c = q.poll();
            for (int dx = -2; dx <= 2; dx++)
                for (int dy = -2; dy <= 2; dy++)
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = c.offset(dx, dy, dz);
                        if (seen.contains(n) || !(level.getBlockState(n).getBlock() instanceof VhfAntennaBlock nb) || nb.type != vb.type) continue;
                        if (!(level.getBlockEntity(n) instanceof VhfAntennaBlockEntity e)) continue;
                        if (Math.abs(e.bandMHz() - band) > 0.02 * band || e.aim().normalize().dot(aim.normalize()) < 0.985) continue;
                        seen.add(n);
                        q.add(n);
                        minGap = Math.min(minGap, Math.sqrt(dx * dx + dy * dy + dz * dz));
                    }
        }
        int n = seen.size();
        if (n <= 1) return 1;
        double lambda = 299.792458 / (vb.type == VhfAntennaBlock.Type.DISH ? 5477 : band);
        double k = Math.min(1, minGap / (0.6 * lambda)); // too close: the apertures overlap
        return 1 + (n - 1) * k;
    }

    /** extra gain of a stack (dB): 10 log N less the divider and phasing lines */
    private double stackDb() {
        return spanM > 1.01 ? 10 * Math.log10(spanM) - 0.4 : 0;
    }

    private boolean stackable() {
        return kind == Kind.YAGI || kind == Kind.DISH || kind == Kind.HELIX;
    }

    /** tiles of a phased array touching each other (up to 256) */
    static int arrayTiles(Level level, BlockPos start) {
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty() && seen.size() < 256) {
            BlockPos c = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (!seen.contains(n) && level.getBlockState(n).getBlock() instanceof VhfAntennaBlock v
                        && v.type == VhfAntennaBlock.Type.ARRAY) {
                    seen.add(n);
                    q.add(n);
                }
            }
        }
        return seen.size();
    }

    static boolean isAntenna(BlockState s) {
        return s.getBlock() instanceof AntennaRodBlock || s.getBlock() instanceof com.sparkgap.pgtelegraph.block.ShortAntennaBlock
                || s.getBlock() instanceof VhfAntennaBlock || s.is(com.sparkgap.pgtelegraph.PgTelegraph.CREATIVE_ANTENNA.get());
    }

    private static AntennaLink analyse(Level level, BlockPos feed, List<BlockPos> path) {
        double[] c = coax(level, path);
        double loss = c[0], maxW = c[1];
        BlockState s = level.getBlockState(feed);
        double ground = heightAboveGround(level, feed);
        if (s.getBlock() instanceof com.sparkgap.pgtelegraph.block.ShortAntennaBlock) {
            boolean dipole = s.getValue(com.sparkgap.pgtelegraph.block.ShortAntennaBlock.DIPOLE);
            return new AntennaLink(feed, dipole ? Kind.SHORT_DIPOLE : Kind.WHIP, 1, dipole ? 150 : 75, ground,
                    path.size(), loss, maxW, path, null);
        }
        Direction.Axis axis = s.getValue(AntennaRodBlock.AXIS);
        if (axis == Direction.Axis.Y) {
            int up = count(level, feed, Direction.UP, axis);
            double len = 1 + up;
            return new AntennaLink(feed, Kind.VERTICAL, len, 75 / len, ground + len / 2, path.size(), loss, maxW, path, null);
        }
        Direction pos = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        double len = 1 + count(level, feed, pos, axis) + count(level, feed, pos.getOpposite(), axis);
        return new AntennaLink(feed, Kind.DIPOLE, len, 150 / len, ground, path.size(), loss, maxW, path, null);
    }

    private static int count(Level level, BlockPos from, Direction dir, Direction.Axis axis) {
        int n = 0;
        BlockPos p = from.relative(dir);
        while (n < 128) {
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof AntennaRodBlock) || s.getValue(AntennaRodBlock.AXIS) != axis) break;
            n++;
            p = p.relative(dir);
        }
        return n;
    }

    private static double heightAboveGround(Level level, BlockPos p) {
        int h = 0;
        BlockPos q = p.below();
        while (h < 256 && q.getY() > level.getMinBuildHeight()) {
            BlockState s = level.getBlockState(q);
            if (!s.isAir() && !(s.getBlock() instanceof AntennaRodBlock) && s.isSolid()) break;
            h++;
            q = q.below();
        }
        return h + 0.5;
    }

    public String describe() {
        if (amplifier != null) return "linear amplifier";
        return switch (kind) {
            case VERTICAL -> String.format(java.util.Locale.ROOT, "%.0f m vertical", lengthM);
            case DIPOLE -> String.format(java.util.Locale.ROOT, "%.0f m dipole", lengthM);
            case WHIP -> "1 m whip";
            case SHORT_DIPOLE -> "1 m dipole";
            case LOADED_VERTICAL -> String.format(java.util.Locale.ROOT, "%.0f m loaded mast, eff %.1f%%",
                    lengthM, 100 * Math.pow(10, -efficiencyDb / 10));
            case VHF_WHIP -> String.format(java.util.Locale.ROOT, "whip for %s", RadioMath.formatMHz(f0MHz));
            case YAGI -> String.format(java.util.Locale.ROOT, "yagi for %s, az %.0f°", RadioMath.formatMHz(f0MHz), azimuth(aim));
            case DISH -> String.format(java.util.Locale.ROOT, "1 m dish, az %.0f° el %.0f°", azimuth(aim), elevation(aim));
            case HELICAL_WHIP -> String.format(java.util.Locale.ROOT, "helical whip for %s, eff %.1f%%",
                    RadioMath.formatMHz(f0MHz), 100 * Math.pow(10, -efficiencyDb / 10));
            case LOOP -> String.format(java.util.Locale.ROOT, "loop at %s, az %.0f°", RadioMath.formatMHz(f0MHz), azimuth(aim));
            case HELIX -> String.format(java.util.Locale.ROOT, "helix for %s, az %.0f°", RadioMath.formatMHz(f0MHz), azimuth(aim));
            case DUMMY -> "50 ohm dummy load";
            case CREATIVE -> String.format(java.util.Locale.ROOT, "creative antenna, %.0f m up", heightM);
            case ARRAY -> String.format(java.util.Locale.ROOT, "%d-tile array for %s, az %.0f° el %.0f°", (int) spanM,
                    RadioMath.formatMHz(f0MHz), azimuth(aim), elevation(aim));
        } + (isTuned() ? " (tuner)" : "");
    }

    /** Compass bearing of a direction: 0 = north (-Z), 90 = east (+X). */
    public static double azimuth(Vec3 v) {
        if (v == null) return 0;
        double a = Math.toDegrees(Math.atan2(v.x, -v.z));
        return a < 0 ? a + 360 : a;
    }

    public static double elevation(Vec3 v) {
        if (v == null) return 0;
        return Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
    }
}
