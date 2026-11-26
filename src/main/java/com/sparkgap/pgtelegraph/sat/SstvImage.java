package com.sparkgap.pgtelegraph.sat;

import net.minecraft.world.level.Level;

/**
 * The picture a satellite's camera takes: the Earth seen from orbit, straight down, framing the
 * whole visible disk - procedural continents, ice caps, clouds, the night side dark. 320 × 256
 * pixels in the {@link com.sparkgap.pgtelegraph.media.Pic} format.
 */
public final class SstvImage {
    private SstvImage() {}

    /** one row of the frame taken at {@code tick} */
    public static byte[] row(Satellite sat, Level level, long tick, int y) {
        final int W = com.sparkgap.pgtelegraph.media.Pic.W, H = com.sparkgap.pgtelegraph.media.Pic.H;
        byte[] out = new byte[com.sparkgap.pgtelegraph.media.Pic.ROW];
        double[] s = sat.orbit.ecefAt(tick);
        double r = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
        double[] fwd = {-s[0] / r, -s[1] / r, -s[2] / r};
        // camera "up" = toward the north pole, made perpendicular to the view
        double[] up = {0, 0, 1};
        double d = dot(up, fwd);
        up = norm(new double[]{up[0] - d * fwd[0], up[1] - d * fwd[1], up[2] - d * fwd[2]});
        double[] right = cross(fwd, up);
        double half = Math.asin(Math.min(1, Orbit.RE / r)) * 1.08;
        double[] sun = Orbit.sunEcef(level);
        for (int x = 0; x < W; x++) {
            double u = ((x + 0.5) / W * 2 - 1) * Math.tan(half) * W / H, v = (1 - (y + 0.5) / H * 2) * Math.tan(half);
            double[] dir = norm(new double[]{fwd[0] + u * right[0] + v * up[0], fwd[1] + u * right[1] + v * up[1], fwd[2] + u * right[2] + v * up[2]});
            // ray-sphere
            double b = dot(s, dir), c = dot(s, s) - Orbit.RE * Orbit.RE, disc = b * b - c;
            if (disc < 0) {
                int star = (x * 7 + y * 13) % 211 == 0 ? 200 : 0;
                com.sparkgap.pgtelegraph.media.Pic.put(out, x, star, star, star); // space (and the odd star)
                continue;
            }
            double t = -b - Math.sqrt(disc);
            double[] p = {s[0] + t * dir[0], s[1] + t * dir[1], s[2] + t * dir[2]};
            double lat = Math.asin(p[2] / Orbit.RE), lon = Math.atan2(p[1], p[0]);
            double light = Math.max(0, dot(norm(p), sun));
            int cr, cg, cb;
            if (Math.abs(lat) > Math.toRadians(68)) {
                cr = cg = cb = 230;
            } else if (noise(lat * 3, lon * 3, 7) > 0.05) {
                boolean desert = Math.abs(Math.toDegrees(lat)) < 32 && Math.abs(Math.toDegrees(lat)) > 15 && noise(lat * 6, lon * 6, 3) > 0;
                cr = desert ? 200 : 60;
                cg = desert ? 170 : 140;
                cb = desert ? 100 : 50;
            } else {
                cr = 20;
                cg = 60;
                cb = 160;
            }
            if (noise(lat * 8 + tick / 4000.0, lon * 8, 11) > 0.35) cr = cg = cb = 240; // clouds
            double k = 0.08 + 0.92 * light;
            com.sparkgap.pgtelegraph.media.Pic.put(out, x, (int) (cr * k), (int) (cg * k), (int) (cb * k));
        }
        return out;
    }

    /** smooth value noise in -1..1 */
    static double noise(double x, double y, int seed) {
        double sum = 0, amp = 1, norm = 0;
        for (int o = 0; o < 4; o++) {
            sum += amp * value(x, y, seed + o);
            norm += amp;
            x *= 2.03;
            y *= 2.03;
            amp *= 0.5;
        }
        return sum / norm;
    }

    private static double value(double x, double y, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi, seed), b = hash(xi + 1, yi, seed), c = hash(xi, yi + 1, seed), d = hash(xi + 1, yi + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static double hash(int x, int y, int seed) {
        long h = x * 374761393L + y * 668265263L + seed * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 32767.5 - 1;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return new double[]{a[0] / l, a[1] / l, a[2] / l};
    }
}
