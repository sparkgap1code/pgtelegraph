package com.sparkgap.pgtelegraph.block;

/** Ratings of a tube amplifier class. */
public record AmpTier(int[] levels, int defaultLevel, double gain, double efficiency, double plateV, double minPlateV,
                      float filamentOhms, double filamentMinV, float filamentMaxV, int warmupTicks, float plateMaxA,
                      Spec spec) {

    /** Amateur-style kilowatt amplifier: 20 dB, up to 10 kW, 2.4 kV plate, 12.6 V filament. */
    public static final AmpTier STANDARD = new AmpTier(
            new int[]{100, 200, 300, 500, 700, 1000, 1500, 2000, 3000, 5000, 7000, 10000}, 5,
            100, 0.65, 2400, 1000, 2.9f, 10.5, 18f, 600, 12f,
            new Spec(250f, 4000f, 200f, 3500f,
                    new Spec.Part("rf_out", 10000, 0, 1), new Spec.Part("plate", 2400, 0, 1),
                    new Spec.Part("filament", 2.9f, 8f, 1)));

    /**
     * Broadcast transmitter tube stage: 20 dB, up to 200 kW, 10 kV plate, 48 V / 2.4 kW filament
     * (large tubes take a full minute to warm), needs serious cooling.
     */
    public static final AmpTier BROADCAST = new AmpTier(
            new int[]{1000, 2000, 5000, 10000, 20000, 30000, 50000, 70000, 100000, 150000, 200000}, 8,
            100, 0.70, 10000, 4000, 0.96f, 43, 60f, 1200, 40f,
            new Spec(300f, 60000f, 3000f, 14000f,
                    new Spec.Part("rf_out", 200000, 0, 1), new Spec.Part("plate", 10000, 0, 1),
                    new Spec.Part("filament", 0.96f, 60f, 1)));
}
