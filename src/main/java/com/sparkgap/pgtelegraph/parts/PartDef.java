package com.sparkgap.pgtelegraph.parts;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One kind of radio part and the numbers it brings. Unused fields stay at their neutral value.
 *
 * @param handheld  fits a handheld; {@code base} fits installed radios
 */
public record PartDef(String id, PartSlot slot, boolean handheld, boolean base,
                      // battery
                      Chemistry chemistry, double nominalV, double capacityWh, boolean protectedCells,
                      // fuse
                      double fuseA,
                      // regulator: extra receiver noise (dB) and supply efficiency
                      double regNoiseDb, double regEfficiency,
                      // receiver
                      double rxNoiseDbm, int filterHz,
                      // power amplifier
                      double paMaxW, double paEfficiency,
                      // band module (Hz)
                      long bandMinHz, long bandMaxHz, double bandNfPenaltyDb) {

    public enum Chemistry {
        /** Li-ion: 8.4 V full (2S), runs away if overcharged, deep-discharged then charged, or overheated */
        LI_ION(8.4, 6.0, 80),
        /** LiFePO4: 7.3 V full, vents instead of burning */
        LIFEPO4(7.3, 5.0, 150),
        /** NiMH 6 cells: 8.7 V full, swells */
        NIMH(8.7, 6.0, 70),
        /** alkaline AA x6: not rechargeable, leaks */
        ALKALINE(9.6, 5.4, 70);

        public final double fullV, cutoffV, runawayC;

        Chemistry(double fullV, double cutoffV, double runawayC) {
            this.fullV = fullV;
            this.cutoffV = cutoffV;
            this.runawayC = runawayC;
        }

        public String langKey() {
            return "pgtelegraph.chem." + name().toLowerCase(java.util.Locale.ROOT);
        }

        public double fullV() {
            return fullV;
        }

        public double cutoffV() {
            return cutoffV;
        }

        public double runawayC() {
            return runawayC;
        }
    }

    /** creative radios: every frequency */
    public static final PartDef CREATIVE_BAND = band("creative_band", 10_000L, 30_000_000_000L, false, true, 0);

    private static PartDef base(String id, PartSlot slot, boolean hh, boolean bs) {
        return new PartDef(id, slot, hh, bs, null, 0, 0, false, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0);
    }

    static PartDef battery(String id, Chemistry c, double v, double wh, boolean prot) {
        return new PartDef(id, PartSlot.BATTERY, true, false, c, v, wh, prot, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0);
    }

    static PartDef fuse(String id, double a) {
        PartDef b = base(id, PartSlot.FUSE, false, true);
        return new PartDef(id, b.slot, false, true, null, 0, 0, false, a, 0, 1, 0, 0, 0, 0, 0, 0, 0);
    }

    static PartDef regulator(String id, double noiseDb, double eff) {
        return new PartDef(id, PartSlot.REGULATOR, true, true, null, 0, 0, false, 0, noiseDb, eff, 0, 0, 0, 0, 0, 0, 0);
    }

    static PartDef lna(String id, double rxNoiseDbm) {
        return new PartDef(id, PartSlot.LNA, true, true, null, 0, 0, false, 0, 0, 1, rxNoiseDbm, 0, 0, 0, 0, 0, 0);
    }

    static PartDef filter(String id, int hz) {
        return new PartDef(id, PartSlot.IF_FILTER, true, true, null, 0, 0, false, 0, 0, 1, 0, hz, 0, 0, 0, 0, 0);
    }

    static PartDef pa(String id, double w, double eff, boolean hh) {
        return new PartDef(id, PartSlot.PA, hh, !hh, null, 0, 0, false, 0, 0, 1, 0, 0, w, eff, 0, 0, 0);
    }

    static PartDef band(String id, long min, long max, boolean hh, boolean bs, double nfPenalty) {
        return new PartDef(id, PartSlot.BAND, hh, bs, null, 0, 0, false, 0, 0, 1, 0, 0, 0, 0, min, max, nfPenalty);
    }

    /** low-pass filter: cutoff in bandMaxHz, stop-band attenuation (dB) in regNoiseDb */
    static PartDef lpf(String id, long cutoffHz, double attenDb) {
        return new PartDef(id, PartSlot.LPF, false, true, null, 0, 0, false, 0, attenDb, 1, 0, 0, 0, 0, 0, cutoffHz, 0);
    }

    /** mains/EMI filter: rejection (dB) of nearby machine noise, kept in regNoiseDb */
    static PartDef emiFilter(String id, double rejectDb) {
        return new PartDef(id, PartSlot.EMI_FILTER, false, true, null, 0, 0, false, 0, rejectDb, 1, 0, 0, 0, 0, 0, 0, 0);
    }

    /** reference oscillator: worst-case drift over temperature (ppm), kept in rxNoiseDbm */
    static PartDef osc(String id, double ppm) {
        return new PartDef(id, PartSlot.OSC, false, true, null, 0, 0, false, 0, 0, 1, ppm, 0, 0, 0, 0, 0, 0);
    }

    public boolean fits(PartSlot.Layout layout) {
        return layout == PartSlot.Layout.HANDHELD ? handheld : base;
    }

    // ---------------- catalogue ----------------
    public static final Map<String, PartDef> ALL = new LinkedHashMap<>();

    private static void add(PartDef d) {
        ALL.put(d.id(), d);
    }

    static {
        add(battery("battery_li_ion", Chemistry.LI_ION, 7.4, 14.8, true));
        add(battery("battery_li_ion_bare", Chemistry.LI_ION, 7.4, 14.8, false));
        add(battery("battery_li_ion_large", Chemistry.LI_ION, 7.4, 29.6, true));
        add(battery("battery_lifepo4", Chemistry.LIFEPO4, 6.4, 19.2, true));
        add(battery("battery_nimh", Chemistry.NIMH, 7.2, 14.4, false));
        add(battery("battery_alkaline", Chemistry.ALKALINE, 9.0, 22.5, false));
        add(fuse("fuse_2a", 2));
        add(fuse("fuse_5a", 5));
        add(fuse("fuse_10a", 10));
        add(fuse("fuse_20a", 20));
        add(regulator("regulator_linear", 0, 0.75));
        add(regulator("regulator_switching", 3, 0.92));
        add(lna("lna_standard", -140));
        add(lna("lna_low_noise", -143));
        add(filter("filter_ceramic_2k4", 2400));
        add(filter("filter_crystal_500", 500));
        add(filter("filter_crystal_250", 250));
        add(pa("pa_handheld_0w5", 0.5, 0.5, true));
        add(pa("pa_handheld_5w", 5, 0.5, true));
        add(pa("pa_handheld_8w", 8, 0.4, true));
        add(pa("pa_10w", 10, 0.6, false));
        add(pa("pa_50w", 50, 0.6, false));
        add(pa("pa_100w", 100, 0.6, false));
        add(band("band_2m", 144_000_000L, 148_000_000L, true, false, 0));
        add(band("band_70cm", 430_000_000L, 440_000_000L, true, false, 0));
        add(band("band_dual", 144_000_000L, 440_000_000L, true, false, 0));
        add(band("band_lf_mf", 10_000L, 3_000_000L, false, true, 0));
        add(band("band_hf", 1_800_000L, 54_000_000L, false, true, 0));
        add(band("band_vhf_uhf", 50_000_000L, 1_300_000_000L, false, true, 0));
        add(band("band_microwave", 1_000_000_000L, 30_000_000_000L, false, true, 0));
        add(band("band_general", 10_000L, 30_000_000_000L, false, true, 3));
        add(lpf("lpf_lf", 3_000_000L, 50));
        add(lpf("lpf_hf", 55_000_000L, 50));
        add(lpf("lpf_vhf", 160_000_000L, 50));
        add(lpf("lpf_uhf", 480_000_000L, 50));
        add(emiFilter("emi_filter", 20));
        add(emiFilter("emi_filter_ferrite", 30));
        add(osc("osc_tcxo", 2));
        add(osc("osc_ocxo", 0.1));
        add(osc("osc_gpsdo", 0.001));
    }

    /** Dual band covers only 2 m and 70 cm, not everything between. */
    public boolean covers(long hz) {
        if (id.equals("band_dual")) {
            return (hz >= 144_000_000L && hz <= 148_000_000L) || (hz >= 430_000_000L && hz <= 440_000_000L);
        }
        return hz >= bandMinHz && hz <= bandMaxHz;
    }
}
