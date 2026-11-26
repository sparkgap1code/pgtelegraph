package com.sparkgap.pgtelegraph.parts;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * The parts inside a radio and what they add up to. A freshly built radio is an empty case; the
 * factory kit is only given to blocks saved before radios had a parts bay.
 */
public final class Chassis {
    private Chassis() {}

    /** Effective numbers of a set of parts (missing part = the stage is absent). */
    public record Stats(PartDef battery, double rxNoiseDbm, int filterHz, double paMaxW, double paEfficiency,
                        double regEfficiency, PartDef band, double fuseA, long lpfCutoffHz, double lpfAttenDb, double paWear,
                        double emiRejectDb, double oscPpm) {
        /**
         * Level of harmonic n (dBc) leaving the transmitter at carrier f. Without a low-pass
         * filter: 2nd -25 dBc, 3rd -35 dBc (a plain class-C stage).
         */
        public double harmonicDbc(int n, long hz) {
            double raw = n == 2 ? -25 : -35;
            if (lpfCutoffHz > 0 && (long) n * hz > lpfCutoffHz) raw -= lpfAttenDb;
            return raw;
        }

        /** a filter cut below the carrier eats the carrier too (dB) */
        public double carrierLossDb(long hz) {
            return lpfCutoffHz > 0 && hz > lpfCutoffHz ? lpfAttenDb : 0;
        }

        /** plain crystal oscillator when no TCXO/OCXO is fitted */
        public static final double XO_PPM = 20;

        /**
         * Carrier offset from the dial (Hz) at the given temperature: the oscillator drifts by up to
         * oscPpm, reached 30 degrees away from 25 C.
         */
        public long driftHz(long hz, double tempC) {
            double x = Math.max(-1, Math.min(1, (tempC - 25) / 30));
            return Math.round(hz * oscPpm * 1e-6 * x);
        }

        public boolean covers(long hz) {
            return band != null && band.covers(hz);
        }
    }

    /** creative radios: an ideal set of parts (quiet front end, wide filter, 1 kW clean PA, every band) */
    public static final Stats CREATIVE = new Stats(null, -150, 2700, 1000, 1.0, 1.0, PartDef.CREATIVE_BAND, 1e6, 0, 0, 0, 99, 0);

    public static String[] factoryKit(PartSlot.Layout layout) {
        return switch (layout) {
            case HANDHELD -> new String[]{"battery_li_ion", "regulator_switching", "lna_standard", "filter_crystal_500",
                    "pa_handheld_5w", "band_dual"};
            case TRANSMITTER -> new String[]{"fuse_20a", "pa_100w", "band_general"};
            case RECEIVER -> new String[]{"fuse_2a", "regulator_linear", "lna_standard", "filter_crystal_500", "band_general"};
        };
    }

    public static NonNullList<ItemStack> factory(PartSlot.Layout layout) {
        String[] ids = factoryKit(layout);
        NonNullList<ItemStack> out = NonNullList.withSize(layout.slots.length, ItemStack.EMPTY);
        for (int i = 0; i < ids.length && i < out.size(); i++) {
            var item = PgTelegraph.PART_ITEMS.get(ids[i]);
            if (item != null) out.set(i, new ItemStack(item.get()));
        }
        return out;
    }

    /** Parts of a handheld stack (a freshly crafted radio is empty). */
    public static NonNullList<ItemStack> of(ItemStack radio, PartSlot.Layout layout) {
        ItemContainerContents c = radio.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (c == null) return NonNullList.withSize(layout.slots.length, ItemStack.EMPTY);
        NonNullList<ItemStack> out = NonNullList.withSize(layout.slots.length, ItemStack.EMPTY);
        c.copyInto(out);
        return out;
    }

    public static void store(ItemStack radio, List<ItemStack> parts) {
        radio.set(net.minecraft.core.component.DataComponents.CONTAINER, ItemContainerContents.fromItems(new ArrayList<>(parts)));
    }

    public static Stats stats(List<ItemStack> parts, PartSlot.Layout layout) {
        PartDef battery = null, band = null;
        double rx = -134, regNoise = 0, regEff = 1, paW = 0, paEff = 0.5, fuse = Double.MAX_VALUE, lpfAtt = 0, wear = 0;
        double emi = 0, ppm = Stats.XO_PPM;
        long lpfCut = 0;
        int filter = 6000; // no IF filter: only the IF strip's own width
        for (int i = 0; i < parts.size() && i < layout.slots.length; i++) {
            PartDef d = PartItem.def(parts.get(i));
            if (d == null || d.slot() != layout.slots[i]) continue;
            switch (d.slot()) {
                case BATTERY -> battery = d;
                case FUSE -> fuse = d.fuseA();
                case REGULATOR -> {
                    regNoise = d.regNoiseDb();
                    regEff = d.regEfficiency();
                }
                case LNA -> rx = d.rxNoiseDbm();
                case IF_FILTER -> filter = d.filterHz();
                case PA -> {
                    paW = d.paMaxW();
                    wear = PartItem.wear(parts.get(i));
                    // a worn PA (tired tubes / transistors) loses efficiency
                    paEff = d.paEfficiency() * (1 - 0.4 * wear);
                }
                case LPF -> {
                    lpfCut = d.bandMaxHz();
                    lpfAtt = d.regNoiseDb();
                }
                case BAND -> band = d;
                case EMI_FILTER -> emi = d.regNoiseDb();
                case OSC -> ppm = d.rxNoiseDbm();
            }
        }
        // a stage that is not fitted (no regulator) means raw supply: no penalty, but no efficiency gain
        if (layout.indexOf(PartSlot.REGULATOR) >= 0 && PartItem.def(parts.get(layout.indexOf(PartSlot.REGULATOR))) == null) regEff = 1;
        double nf = rx + regNoise + (band == null ? 0 : band.bandNfPenaltyDb());
        return new Stats(battery, nf, filter, paW, paEff, regEff, band, fuse, lpfCut, lpfAtt, wear, emi, ppm);
    }
}
