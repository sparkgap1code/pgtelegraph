package com.sparkgap.pgtelegraph.radio;

/**
 * Sky-wave fading that the server and every client agree on. Each of the two Watterson modes
 * (ITU-R F.1487) gets a complex Rayleigh-fading gain built as a sum of sinusoids (Jakes' model):
 * eight Doppler components whose frequencies, angles and phases come from the path's seed. It is a
 * deterministic function of time, so the receiver's OUT contact (server) fades exactly with what
 * its loudspeaker plays (client).
 */
public final class Fading {
    private Fading() {}

    private static final int N = 8;

    /**
     * Tap gains {Ar, Ai, Br, Bi} at time t (seconds): each complex gain has unit mean power
     * (0.5 per component).
     */
    public static double[] taps(long seed, double t, double dopplerHz, double[] out) {
        for (int tap = 0; tap < 2; tap++) {
            double re = 0, im = 0;
            long s = mix(seed + tap * 0x9E3779B97F4A7C15L);
            for (int k = 0; k < N; k++) {
                s = mix(s);
                double alpha = (s & 0xFFFF) / 65536.0 * 2 * Math.PI;
                double phiR = ((s >>> 16) & 0xFFFF) / 65536.0 * 2 * Math.PI;
                double phiI = ((s >>> 32) & 0xFFFF) / 65536.0 * 2 * Math.PI;
                double w = 2 * Math.PI * dopplerHz * Math.cos(alpha) * t;
                re += Math.cos(w + phiR);
                im += Math.cos(w + phiI);
            }
            out[2 * tap] = re / Math.sqrt(N);
            out[2 * tap + 1] = im / Math.sqrt(N);
        }
        return out;
    }

    /**
     * Power factor of the carrier itself (linear, mean 1) for a signal whose power is a share
     * {@code sky} sky wave and the rest steady ground wave.
     */
    public static double carrierGain(long seed, double t, double dopplerHz, double sky) {
        if (sky <= 0) return 1;
        double[] g = taps(seed, t, dopplerHz, new double[4]);
        double ground = Math.sqrt(Math.max(0, 1 - sky)), sk = Math.sqrt(sky / 2);
        double re = ground + sk * (g[0] + g[2]), im = sk * (g[1] + g[3]);
        return re * re + im * im;
    }

    /** Doppler spread of the ionosphere now: quiet 0.1 Hz, faster in storms and flares */
    public static double dopplerHz(Ionosphere io, net.minecraft.world.level.Level level) {
        return 0.1 + 0.15 * io.kp() + (io.flare(level) ? 0.5 : 0);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
