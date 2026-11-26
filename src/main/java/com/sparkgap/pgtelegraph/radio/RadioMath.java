package com.sparkgap.pgtelegraph.radio;

/**
 * Radio propagation, after the ITU-R models but small enough to run every tick:
 * <ul>
 *   <li>distance scale ({@link #effectiveKm}, server config): true 1:1 nearby, growing smoothly to
 *       100 m per block further out, so local links stay realistic while a Minecraft world still spans
 *       HF skip distances. Antennas themselves are always 1 block = 1 m.</li>
 *   <li>ground wave: ITU-R P.368-style Norton attenuation over land or sea, plus earth curvature</li>
 *   <li>sky wave: round-earth hop geometry and the secant law against foF2 (skip zone and MUF
 *       follow from it), D-layer absorption that punishes low bands by day</li>
 *   <li>VHF and up: free space / two-ray plane earth, radio horizon, knife-edge terrain</li>
 *   <li>noise: atmospheric/galactic noise falling with frequency, worse at night and in storms</li>
 * </ul>
 */
public final class RadioMath {
    private RadioMath() {}

    public static final double METERS_PER_BLOCK = 100;
    /**
     * Propagation distance (km) for a separation in blocks: 1:1 up to the configured near distance
     * (default 6 chunks), then metres-per-block grows as (d / near)^P to the configured maximum
     * (default 100 m) at the far distance (default 60 chunks), constant beyond. Continuous and smooth.
     */
    public static double effectiveKm(double blocks) {
        double near = Math.max(1, com.sparkgap.pgtelegraph.TelegraphConfig.nearBlocks());
        double far = com.sparkgap.pgtelegraph.TelegraphConfig.farBlocks();
        double max = com.sparkgap.pgtelegraph.TelegraphConfig.maxMetersPerBlock();
        double d = Math.max(1, blocks);
        if (d <= near) return d / 1000;
        double p = Math.log(max) / Math.log(far / near);
        double ramp = near + near / (p + 1) * (Math.pow(Math.min(d, far) / near, p + 1) - 1);
        return (ramp + Math.max(0, d - far) * max) / 1000;
    }
    /** CW filter bandwidth for detection */
    public static final int FILTER_HZ = 500;
    /** signals further than this from the dial are not heard at all */
    public static final int AUDIBLE_HZ = 1500;
    /** loaded Q used to turn detuning into reflection */
    public static final double DEFAULT_Q = 5;
    private static final double Q = DEFAULT_Q;

    public static double dbm(double watts) {
        return 10 * Math.log10(Math.max(watts, 1e-15) * 1000);
    }

    public static double fromDb(double db) {
        return Math.pow(10, db / 10);
    }

    /** |Γ|² of an antenna resonant at f0 used at f (simple series RLC with loaded Q). */
    public static double reflection(double fMHz, double f0MHz) {
        return reflection(fMHz, f0MHz, Q);
    }

    /** Same with an explicit loaded Q (a tuned, electrically short antenna is very narrow). */
    public static double reflection(double fMHz, double f0MHz, double q) {
        double x = q * (fMHz / f0MHz - f0MHz / fMHz);
        return x * x / (4 + x * x);
    }

    public static double swr(double gamma2) {
        double g = Math.sqrt(Math.min(gamma2, 0.9999));
        return (1 + g) / (1 - g);
    }

    /** Coax loss scales with √f. lossDb10 = total dB at 10 MHz. */
    public static double coaxLossDb(double lossDb10, double fMHz) {
        return lossDb10 * Math.sqrt(fMHz / 10);
    }

    public static double muf(boolean day) {
        return day ? 22 : 9;
    }

    /** ground electrical constants: conductivity (S/m) and relative permittivity (ITU-R P.527) */
    public static final double LAND_SIGMA = 0.005, LAND_EPS = 15, SEA_SIGMA = 5, SEA_EPS = 70;
    public static final double EARTH_KM = 6371;

    /** Ground-wave path loss in dB over average land. */
    public static double groundLoss(double dKm, double fMHz, double hTx, double hRx) {
        return groundLoss(dKm, fMHz, hTx, hRx, 0);
    }

    /**
     * Ground-wave path loss (dB), after ITU-R P.368: the field of a vertical antenna over flat
     * ground is twice the free-space field times Norton's attenuation factor A(p, b) - numerical
     * distance p grows with distance and frequency and falls with ground conductivity, so sea water
     * carries MF a long way and dry land soaks up HF within a few km. Past about 80 / f^(1/3) km the
     * wave also has to diffract round the curve of the earth. A mixed land / sea path takes the
     * average in dB, weighted by the sea fraction (a simple Millington).
     */
    public static double groundLoss(double dKm, double fMHz, double hTx, double hRx, double seaFraction) {
        double sea = Math.max(0, Math.min(1, seaFraction));
        double att = (1 - sea) * nortonDb(dKm, fMHz, LAND_SIGMA, LAND_EPS) + sea * nortonDb(dKm, fMHz, SEA_SIGMA, SEA_EPS);
        double fspl = 20 * Math.log10(Math.max(dKm, 0.01)) + 20 * Math.log10(fMHz) + 32.44;
        double dc = 80 / Math.cbrt(fMHz) * (sea > 0.5 ? 1.6 : 1);
        double curve = dKm > dc ? 6 * (dKm / dc - 1) : 0;
        return fspl - 6 + att + curve - heightGain(hTx) - heightGain(hRx);
    }

    /** -20 log10 of Norton's surface-wave attenuation factor (vertical polarisation) */
    static double nortonDb(double dKm, double fMHz, double sigma, double eps) {
        double lambda = 299.79 / fMHz;
        double x = 1.8e4 * sigma / fMHz;
        double b = Math.atan((eps + 1) / x);
        double p = Math.PI * dKm * 1000 / (lambda * x) * Math.cos(b);
        double a = (2 + 0.3 * p) / (2 + p + 0.6 * p * p) - Math.sqrt(p / 2) * Math.exp(-5 * p / 8) * Math.sin(b);
        return -20 * Math.log10(Math.max(1e-7, Math.min(1, a)));
    }

    /** Sky-wave path loss in dB, or +inf when the ionosphere does not support it (rough day/night ionosphere). */
    public static double skyLoss(double dKm, double fMHz, boolean day) {
        return skyLoss(dKm, fMHz, day ? 7.3 : 3, (day ? 400 : 25) / (fMHz * fMHz));
    }

    /**
     * Sky wave off the F2 layer (dB), or +inf when it does not come down here. Real geometry on a
     * round earth: the path takes as many hops as it needs (at most ~3800 km each, reflecting at
     * 300 km), and on each hop the layer only turns the wave back if f cos(i) <= foF2 (secant law,
     * i = angle of incidence) - which makes the skip zone and the MUF fall out by themselves.
     * Loss = free space along the slant path + D-layer absorption (per hop, longer through the
     * layer at low angles) + 2 dB per ground reflection between hops + a steep penalty for
     * frequencies that only just get through (above-the-MUF region).
     *
     * @param foF2       critical frequency of the F2 layer (MHz)
     * @param absorption D-layer absorption of one hop at average incidence (dB)
     */
    public static double skyLoss(double dKm, double fMHz, double foF2, double absorption) {
        // LF and below come back off the bottom of the D region (~85 km); MF and HF off the F2 layer
        boolean low = fMHz < 0.5;
        final double h = low ? 85 : 300, hD = low ? 70 : 90;
        int hops = Math.max(1, (int) Math.ceil(dKm / (low ? 2000 : 3800)));
        double g = Math.max(dKm, 1) / (2 * hops);
        double phi = g / EARTH_KM;
        double cosI = cosIncidence(phi, h);
        double fMax = low ? Double.MAX_VALUE : foF2 / Math.max(0.05, cosI);
        if (fMHz > fMax * 1.15) return Double.POSITIVE_INFINITY;
        double r = EARTH_KM, rh = r + h;
        double slant = Math.sqrt(r * r + rh * rh - 2 * r * rh * Math.cos(phi)) * 2 * hops;
        double fspl = 20 * Math.log10(slant) + 20 * Math.log10(fMHz) + 32.44;
        double secD = 1 / Math.max(0.15, cosIncidence(phi, hD));
        double absorb = absorption * hops * secD / 3;
        double reflections = (low ? 3 : 2) * (hops - 1);
        double nearMuf = fMHz > fMax ? (fMHz / fMax - 1) * 120 : 0;
        return fspl + absorb + reflections + nearMuf + 3;
    }

    /**
     * VLF (below 100 kHz): the earth and the bottom of the ionosphere form a waveguide. Past a few
     * hundred km the wave spreads in two dimensions only (10 log d instead of 20 log d) and loses
     * just 2-3 dB per 1000 km - which is why a VLF station is heard half a world away.
     *
     * @param sun 1 at noon, 0 at night (the guide is a little lossier by day)
     */
    public static double waveguideLoss(double dKm, double fMHz, double sun) {
        if (fMHz >= 0.1) return Double.POSITIVE_INFINITY;
        double d0 = 300, d = Math.max(dKm, 1);
        double near = 20 * Math.log10(Math.min(d, d0)) + 20 * Math.log10(fMHz) + 32.44;
        double spread = d > d0 ? 10 * Math.log10(d / d0) : 0;
        double alpha = 2 + 1.5 * sun; // dB per 1000 km
        return near + spread + alpha * d / 1000 + 3;
    }

    /** cos of the angle of incidence on a layer at height h (km) for a half-hop of central angle phi */
    private static double cosIncidence(double phi, double h) {
        double r = EARTH_KM, rh = r + h;
        double tanB = (Math.cos(phi) - r / rh) / Math.sin(Math.max(phi, 1e-6));
        double beta = Math.atan(tanB);
        double sinI = r * Math.cos(beta) / rh;
        return Math.sqrt(Math.max(0, 1 - sinI * sinI));
    }

    private static double heightGain(double h) {
        return h <= 4 ? 0 : Math.min(6, 2 * (Math.log(1 + h / 10) / Math.log(2)));
    }

    /**
     * Line-of-sight path for 30 MHz and up: free-space loss, or - once the ground reflection starts
     * to cancel the direct ray - plane-earth two-ray loss, 40 log d - 20 log (h1 h2); a steep extra
     * loss beyond the radio horizon (4.12 (sqrt h1 + sqrt h2) km, 4/3 earth); and rain fade on SHF.
     * Hills and buildings are added separately (Terrain, knife-edge diffraction).
     */
    public static double losLoss(double dKm, double fMHz, double hTx, double hRx, boolean rain, boolean thunder) {
        double fspl = 20 * Math.log10(Math.max(dKm, 0.001)) + 20 * Math.log10(fMHz) + 32.44;
        double h1 = Math.max(hTx, 1.5), h2 = Math.max(hRx, 1.5), dm = Math.max(dKm, 0.001) * 1000;
        double planeEarth = 40 * Math.log10(dm) - 20 * Math.log10(h1 * h2);
        double horizon = 4.12 * (Math.sqrt(h1) + Math.sqrt(h2));
        double beyond = dKm > horizon ? 6 + 40 * Math.log10(dKm / horizon) : 0;
        double rainDb = rain && fMHz > 5000 ? 0.02 * Math.pow(fMHz / 1000, 1.5) * dKm * (thunder ? 3 : 1) : 0;
        return Math.max(fspl, planeEarth) + beyond + rainDb;
    }

    /** Above this, propagation is line of sight (no ground wave or sky wave model). */
    public static final double LOS_MHZ = 30;

    public static String formatMHz(double mhz) {
        if (mhz >= 1000) return String.format(java.util.Locale.ROOT, "%.3f GHz", mhz / 1000);
        if (mhz >= 1) return String.format(java.util.Locale.ROOT, "%.3f MHz", mhz);
        return String.format(java.util.Locale.ROOT, "%.2f kHz", mhz * 1000);
    }

    /** Noise in the CW filter (dBm). */
    public static double noiseDbm(double fMHz, boolean day, boolean rain, boolean thunder) {
        double fa = Math.max(0, 60 - 30 * Math.log10(fMHz)); // dB above kTB (galactic noise fades out above VHF)
        if (!day) fa += 6;
        if (rain) fa += 3;
        if (thunder && fMHz < 12) fa += 15;
        return -174 + 10 * Math.log10(FILTER_HZ) + fa;
    }

    /**
     * Share of the external noise that is impulsive atmospheric noise (lightning the world over):
     * nearly all of it on LF/MF, fading out towards 30 MHz where galactic and man-made noise rule.
     */
    public static double atmosphericShare(double fMHz) {
        return Math.max(0, Math.min(0.6, 0.6 * (1 - Math.log10(Math.max(fMHz, 0.1) / 2) / Math.log10(15))));
    }

    /** Slow sky-wave fading (QSB), ±6 dB, different for every transmitter-receiver pair. */
    public static double qsb(long gameTime, long pairSeed) {
        double a = (pairSeed & 0xFFFF) / 65535.0 * 6.28, b = ((pairSeed >> 16) & 0xFFFF) / 65535.0 * 6.28;
        return 6 * Math.sin(gameTime / 37.0 + a) * Math.sin(gameTime / 113.0 + b);
    }

    /** S-units: S9 = -73 dBm, 6 dB per unit. */
    public static String sMeter(double dbm) {
        if (dbm >= -73) return "S9+" + Math.round(dbm + 73) + "dB";
        int s = (int) Math.floor((dbm + 127) / 6);
        return "S" + Math.max(0, s);
    }
}
