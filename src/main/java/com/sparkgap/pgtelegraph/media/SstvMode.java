package com.sparkgap.pgtelegraph.media;

/**
 * SSTV modes, with the real resolutions and frame times. Each starts with its VIS code, so a
 * receiver switches to the right mode by itself. What differs on the air:
 * <ul>
 *   <li>frame time and resolution (lines arrive at height / seconds)</li>
 *   <li>pixel time: the longer each pixel is sent, the narrower the noise bandwidth per pixel - a
 *       slow mode survives a weaker signal (dB relative to PD50)</li>
 *   <li>colour: Martin / Scottie send R, G and B one after another (noise makes colour grain);
 *       Robot and PD send brightness and colour difference (noise is mostly grey grain)</li>
 * </ul>
 */
public enum SstvMode {
    MARTIN_M1("Martin M1", Family.MARTIN, 320, 256, 114.3, 0.4576, false, 44),
    MARTIN_M2("Martin M2", Family.MARTIN, 160, 256, 58.1, 0.2288, false, 40),
    SCOTTIE_S1("Scottie S1", Family.SCOTTIE, 320, 256, 109.6, 0.4320, false, 60),
    SCOTTIE_S2("Scottie S2", Family.SCOTTIE, 160, 256, 71.1, 0.2752, false, 56),
    ROBOT_36("Robot 36", Family.ROBOT, 320, 240, 36.0, 0.275, true, 8),
    PD50("PD50", Family.PD, 320, 256, 49.7, 0.286, true, 93),
    PD90("PD90", Family.PD, 320, 256, 89.99, 0.532, true, 99),
    PD120("PD120", Family.PD, 640, 496, 126.1, 0.190, true, 95),
    PD160("PD160", Family.PD, 512, 400, 160.9, 0.382, true, 98),
    PD180("PD180", Family.PD, 640, 496, 187.1, 0.286, true, 96),
    PD240("PD240", Family.PD, 640, 496, 248.0, 0.382, true, 97),
    PD290("PD290", Family.PD, 800, 616, 288.7, 0.286, true, 94);

    public enum Family { MARTIN, SCOTTIE, ROBOT, PD }

    public static final int MAX_W = 800, MAX_H = 616;

    public final String label;
    public final Family family;
    public final int w, h;
    public final double seconds, pixelMs;
    /** colour as brightness + colour difference (true) or R, G, B one after another */
    public final boolean yc;
    public final int vis;

    SstvMode(String label, Family family, int w, int h, double seconds, double pixelMs, boolean yc, int vis) {
        this.label = label;
        this.family = family;
        this.w = w;
        this.h = h;
        this.seconds = seconds;
        this.pixelMs = pixelMs;
        this.yc = yc;
        this.vis = vis;
    }

    /** the line being sent {@code ticks} after the start (-1 once the frame is done) */
    public int lineAt(long ticks) {
        long l = (long) Math.floor(ticks * h / (seconds * 20));
        return l >= h ? -1 : (int) l;
    }

    /** SNR gain per pixel against PD50 from the pixel time (narrower noise bandwidth) */
    public double snrGainDb() {
        return 10 * Math.log10(pixelMs / PD50.pixelMs);
    }

    /** displayed aspect (width / height): the 160-pixel modes have wide pixels */
    public double aspect() {
        return (w < 200 ? w * 2.0 : w) / h;
    }

    public static SstvMode of(int ordinal) {
        SstvMode[] v = values();
        return v[Math.floorMod(ordinal, v.length)];
    }

    /** the modes of one family, in order */
    public static java.util.List<SstvMode> family(Family f) {
        java.util.List<SstvMode> out = new java.util.ArrayList<>();
        for (SstvMode m : values()) if (m.family == f) out.add(m);
        return out;
    }

    /** PD by resolution: the speeds there are at that resolution */
    public static java.util.List<SstvMode> pdAt(int w) {
        java.util.List<SstvMode> out = new java.util.ArrayList<>();
        for (SstvMode m : values()) if (m.family == Family.PD && m.w == w) out.add(m);
        return out;
    }

    public static final int[] PD_WIDTHS = {320, 512, 640, 800};
}
