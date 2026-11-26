package com.sparkgap.pgtelegraph.sat;

import com.sparkgap.pgtelegraph.media.Pic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Weather-satellite APT picture (기상위성 APT): one scan line across the ground track per line,
 * the way the NOAA polar orbiters send it. Two channels side by side, each with its sync stripes
 * on the left and the telemetry wedges on the right: A (left) visible light - dark at night;
 * B (right) thermal infrared - cold cloud tops bright, day and night. The swath is ±1400 km.
 * Over the station the clouds are the world's own weather: overcast in rain, towering storm cells
 * (the brightest, coldest tops) in a thunderstorm. A small cross marks the station.
 */
public final class AptImage {
    private AptImage() {}

    public static final int CH = 160, SYNC = 10, WEDGE = 6, TICKS_PER_LINE = 10;
    private static final double SWATH_KM = 1400;

    /** one received line: the satellite's position at {@code t}, seen from the station at {@code station} */
    public static byte[] row(Satellite sat, Level level, long t, int lineNo, BlockPos station) {
        byte[] out = new byte[Pic.ROW];
        double[] s = sat.orbit.ecefAt(t), s2 = sat.orbit.ecefAt(t + 20);
        double[] sub = norm(s), along = norm(sub(norm(s2), sub));
        double[] across = norm(cross(along, sub));
        double[] sun = Orbit.sunEcef(level);
        double[] st = Orbit.latLon(station);
        double[] stp = {Math.cos(st[0]) * Math.cos(st[1]), Math.cos(st[0]) * Math.sin(st[1]), Math.sin(st[0])};
        boolean rain = level.isRaining(), storm = level.isThundering();
        int w = CH - SYNC - WEDGE;
        for (int x = 0; x < w; x++) {
            double dKm = ((x + 0.5) / w * 2 - 1) * SWATH_KM, a = dKm / Orbit.RE;
            double[] p = norm(new double[]{sub[0] * Math.cos(a) + across[0] * Math.sin(a),
                    sub[1] * Math.cos(a) + across[1] * Math.sin(a), sub[2] * Math.cos(a) + across[2] * Math.sin(a)});
            double lat = Math.asin(p[2]), lon = Math.atan2(p[1], p[0]);
            double light = Math.max(0, dot(p, sun));
            // surface: albedo (visible) and warmth (IR: warm = dark)
            double alb, warm;
            if (Math.abs(lat) > Math.toRadians(68)) {
                alb = 0.8;
                warm = 0.15;
            } else if (SstvImage.noise(lat * 3, lon * 3, 7) > 0.05) {
                boolean desert = Math.abs(Math.toDegrees(lat)) < 32 && Math.abs(Math.toDegrees(lat)) > 15 && SstvImage.noise(lat * 6, lon * 6, 3) > 0;
                alb = desert ? 0.45 : 0.22;
                warm = (desert ? 0.95 : 0.75) * (0.6 + 0.4 * light);
            } else {
                alb = 0.07;
                warm = 0.7;
            }
            // clouds: the procedural sky, the world's weather near the station
            double cloud = Math.max(0, (SstvImage.noise(lat * 8 + t / 4000.0, lon * 8, 11) - 0.2) * 2.5), top = 0.5;
            double near = Math.acos(Math.max(-1, Math.min(1, dot(p, stp)))) * Orbit.RE;
            if (near < 600 && (rain || storm)) {
                double k = 1 - near / 600;
                cloud = Math.max(cloud, (rain ? 0.9 : 0) * k);
                if (storm) {
                    double cell = SstvImage.noise(lat * 60 + t / 2000.0, lon * 60, 5);
                    if (cell > 0.1) {
                        cloud = Math.max(cloud, k);
                        top = Math.max(top, 0.8 + cell);
                    }
                }
            }
            cloud = Math.min(1, cloud);
            double vis = (alb * (1 - cloud) + 0.85 * cloud) * (0.05 + 0.95 * light);
            double ir = (1 - warm) * (1 - cloud) + Math.min(1, top) * cloud;
            int va = (int) (255 * Math.min(1, vis)), vb = (int) (255 * Math.min(1, ir));
            Pic.put(out, SYNC + x, va, va, va);
            Pic.put(out, CH + SYNC + x, vb, vb, vb);
            // the station: a small yellow cross drawn by the decoder
            double sx = (dot(stp, across)) * Orbit.RE, sy = dot(stp, along) * Orbit.RE;
            if (Math.abs(near) < 20 || (Math.abs(near) < 60 && (Math.abs(sx - dKm) < 8 || Math.abs(sy) < 8))) {
                Pic.put(out, SYNC + x, 255, 220, 0);
                Pic.put(out, CH + SYNC + x, 255, 220, 0);
            }
        }
        // sync A: 7 cycles of 1040 Hz; sync B: 7 pulses of 832 Hz - drawn as stripes
        for (int x = 0; x < SYNC; x++) {
            int a = (x % 3 == 0) ? 255 : 0, b = (x % 2 == 0) ? 255 : 0;
            Pic.put(out, x, a, a, a);
            Pic.put(out, CH + x, b, b, b);
        }
        // telemetry wedges: eight grey steps, eight lines each, repeating
        int wedge = 32 * (lineNo / 8 % 8);
        for (int x = 0; x < WEDGE; x++) {
            Pic.put(out, CH - WEDGE + x, wedge, wedge, wedge);
            Pic.put(out, 2 * CH - WEDGE + x, wedge, wedge, wedge);
        }
        return out;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return new double[]{a[0] / l, a[1] / l, a[2] / l};
    }
}
