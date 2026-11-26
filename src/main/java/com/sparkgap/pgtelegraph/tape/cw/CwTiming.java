package com.sparkgap.pgtelegraph.tape.cw;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Speed and "fist" adaptation (ported from the cwdecoder project, timing.rs). No ideal 1:3:7 is
 * assumed: recent mark lengths are split into two clusters (dit / dah) and space lengths into
 * three (element / letter / word gap) in the log domain, so this operator's real rhythm is the
 * reference. Judgements come back as log probabilities.
 */
public final class CwTiming {
    private final ArrayDeque<Double> marks = new ArrayDeque<>(), spaces = new ArrayDeque<>();
    public double dit, dah, intra, chr, word;
    /** log-domain spread of marks / spaces - the shakier the hand, the larger */
    public double sm = 0.22, ss = 0.3;

    private static final int MARK_HIST = 24, SPACE_HIST = 48;
    /** about 4-50 WPM (the in-game keyers go down to 5 WPM) */
    private static final double DIT_MIN = 0.024, DIT_MAX = 0.3;

    /** measuring resolution (s): 0 for audio, one game tick (0.05) for a coil read every tick */
    private final double quantum;

    public CwTiming() {
        this(0);
    }

    public CwTiming(double quantum) {
        this.quantum = quantum;
        dit = 0.06;
        dah = 3 * dit;
        intra = dit;
        chr = 3 * dit;
        word = 7 * dit;
    }

    public double wpm() {
        return 1.2 / dit;
    }

    public void clearHistory() {
        marks.clear();
        spaces.clear();
    }

    private static double gaussLp(double x, double mu, double sigma) {
        double z = (x - mu) / sigma;
        return -0.5 * z * z;
    }

    /** normalise log probabilities, with a floor (0.02) so the lexicon can still overturn them */
    private static double[] normalize(double[] lp) {
        double m = Double.NEGATIVE_INFINITY;
        for (double v : lp) m = Math.max(m, v);
        double s = 0;
        for (double v : lp) s += Math.exp(v - m);
        double[] out = new double[lp.length];
        for (int i = 0; i < lp.length; i++) out[i] = Math.log(Math.max(Math.exp(lp[i] - m) / s, 0.02));
        return out;
    }

    /** k-means in place on centres {@code init}; returns the within-cluster standard deviation */
    private static double kmeans(double[] logs, double[] init) {
        int k = init.length;
        double sse = 0;
        int used = 0;
        for (int it = 0; it < 10; it++) {
            double[] sum = new double[k];
            int[] cnt = new int[k];
            sse = 0;
            for (double x : logs) {
                int j = 0;
                for (int a = 1; a < k; a++) if (Math.abs(x - init[a]) < Math.abs(x - init[j])) j = a;
                sum[j] += x;
                cnt[j]++;
                sse += (x - init[j]) * (x - init[j]);
            }
            used = 0;
            for (int j = 0; j < k; j++) {
                if (cnt[j] > 0) {
                    init[j] = sum[j] / cnt[j];
                    used++;
                }
            }
        }
        return Math.sqrt(sse / Math.max(1, logs.length - used));
    }

    public void addMark(double d) {
        // more than 3x a dah is an outlier (merged marks, a tuning carrier): not learnt from
        if (marks.size() >= 6 && d > 3 * dah) return;
        marks.addLast(d);
        if (marks.size() > MARK_HIST) marks.removeFirst();
        refitMarks();
    }

    public void addSpace(double d) {
        if (d < 0.25 * dit) return;
        spaces.addLast(Math.min(d, 12 * dit));
        if (spaces.size() > SPACE_HIST) spaces.removeFirst();
        refitSpaces();
    }

    private static double[] sorted(ArrayDeque<Double> q) {
        double[] s = new double[q.size()];
        int i = 0;
        for (double v : q) s[i++] = v;
        Arrays.sort(s);
        return s;
    }

    private void refitMarks() {
        if (marks.size() < 3) return;
        // dahs first: noise and split marks only ever land on the short side, the long quantile holds
        double[] s = sorted(marks);
        int n = s.length;
        double lo = s[n / 5], hi = s[Math.min(n * 9 / 10, n - 1)];
        boolean mixed = hi / lo >= 2;
        List<Double> top = new ArrayList<>();
        if (mixed) {
            double thr = Math.sqrt(lo * hi);
            for (double d : s) if (d > thr) top.add(d);
        } else {
            for (int i = n * 7 / 10; i < n; i++) top.add(s[i]);
        }
        double ls = 0;
        for (double d : top) ls += Math.log(d);
        double lng = Math.exp(ls / top.size());
        // even the long ones may be dits ("5", "EEE"): compare with the element gap (about a dit)
        double intraEst = dit;
        if (spaces.size() >= 3) {
            double[] sp = sorted(spaces);
            intraEst = sp[sp.length / 4];
        }
        boolean allDits = !mixed && lng < 1.8 * Math.max(intraEst, 0.5 * dit);
        double nDit, nDah;
        if (allDits) {
            nDit = lng;
            nDah = Math.max(lng * dah / dit, 2.2 * lng);
        } else {
            double sum = 0;
            int c = 0;
            for (double d : s)
                if (d > lng / 5 && d < lng / 1.8) {
                    sum += Math.log(d);
                    c++;
                }
            nDit = c >= 2 ? Math.exp(sum / c) : lng / 3;
            nDah = lng;
        }
        dit = nDit;
        dah = nDah;
        if (n >= 8) {
            double thr = Math.log(Math.sqrt(dit * dah)), ld = Math.log(dit), lh = Math.log(dah);
            List<Double> dev = new ArrayList<>();
            for (double d : s) {
                if (d <= dit / 2) continue;
                double x = Math.log(d);
                dev.add(x < thr ? Math.abs(x - ld) : Math.abs(x - lh));
            }
            if (!dev.isEmpty()) {
                dev.sort(null);
                sm = clamp(1.4826 * dev.get(dev.size() / 2), 0.12, 0.45);
            }
        }
        double r = dah / dit;
        if (r < 2 || r > 5) {
            double mid = Math.sqrt(dit * dah), h = Math.sqrt(clamp(r, 2, 5));
            dit = mid / h;
            dah = mid * h;
        }
        dit = clamp(dit, DIT_MIN, DIT_MAX);
        dah = Math.max(dah, 2 * dit);
    }

    private void refitSpaces() {
        if (spaces.size() < 3) {
            intra = dit;
            chr = Math.max(chr, 2.2 * dit);
            word = Math.max(word, 2.2 * chr);
            return;
        }
        double[] s = sorted(spaces);
        // 1) element gaps: shorter than the dit/dah boundary
        double cut = Math.sqrt(dit * dah);
        List<Double> shortL = new ArrayList<>();
        for (double d : s) if (d < cut) shortL.add(Math.log(d));
        // operators who run letters together (letter gap ~1.5-1.8 dit) straddle that boundary: look a little
        // wider (< 2.6 dit) and split off the lower group only where there is a real hole between values
        Double longCut = null;
        List<Double> wideL = new ArrayList<>();
        for (double d : s) if (d < 2.6 * dit) wideL.add(Math.log(d));
        if (wideL.size() >= 12) {
            double[] wide = wideL.stream().mapToDouble(Double::doubleValue).toArray();
            int gi = 1;
            double gap = wide[1] - wide[0];
            for (int i = 2; i < wide.length; i++)
                if (wide[i] - wide[i - 1] > gap) {
                    gap = wide[i] - wide[i - 1];
                    gi = i;
                }
            double[] c = {wide[0], wide[wide.length - 1]};
            double sd = kmeans(wide, c);
            double mid = 0.5 * (wide[gi - 1] + wide[gi]);
            // a step of one measuring quantum (1 tick vs 2 ticks) is not a hole, just the clock
            boolean real = Math.exp(wide[gi]) - Math.exp(wide[gi - 1]) > 1.5 * quantum;
            boolean hole = real && gap > Math.log(1.25) && gap > 2 * Math.max(sd, 0.04) && wide.length - gi >= 2 && gi >= 4;
            if (hole && c[1] - c[0] > Math.log(1.3) && mid < Math.log(cut) + 0.3) {
                shortL = new ArrayList<>();
                for (int i = 0; i < gi; i++) shortL.add(wide[i]);
                longCut = Math.exp(mid);
            }
        }
        if (shortL.size() >= 2) {
            double m = 0;
            for (double x : shortL) m += x;
            intra = clamp(Math.exp(m / shortL.size()), 0.5 * dit, 2 * dit);
        } else intra = dit;
        // 2) the rest (letter / word gaps) in two
        double lc = longCut != null ? longCut : 1.8 * intra;
        List<Double> longL = new ArrayList<>();
        for (double d : s) if (d >= lc) longL.add(Math.log(d));
        longL.sort(null);
        boolean split = false;
        if (!longL.isEmpty()) {
            double[] lg = longL.stream().mapToDouble(Double::doubleValue).toArray();
            double lo = lg[0], hi = lg[lg.length - 1];
            double[] c = {lo, hi};
            double sd = lg.length >= 4 ? kmeans(lg, c) : 1;
            double thr = 0.5 * (c[0] + c[1]);
            int nHi = 0;
            for (double x : lg) if (x > thr) nHi++;
            boolean bimodal = hi - lo > Math.log(1.25) && nHi >= 1 && lg.length - nHi >= 2 && c[1] - c[0] > 2.5 * Math.max(sd, 0.04);
            split = bimodal;
            if (bimodal) {
                chr = Math.exp(c[0]);
                word = Math.exp(c[1]);
            } else {
                double m = 0;
                for (double x : lg) m += x;
                m = Math.exp(m / lg.length);
                if (m < 5 * intra) chr = m;
                else word = m;
            }
        }
        double[] centres = {Math.log(intra), Math.log(chr), Math.log(word)};
        if (s.length >= 8) {
            double[] dev = new double[s.length];
            for (int i = 0; i < s.length; i++) {
                double x = Math.log(s[i]), best = Double.MAX_VALUE;
                for (double c : centres) best = Math.min(best, Math.abs(x - c));
                dev[i] = best;
            }
            Arrays.sort(dev);
            ss = clamp(1.4826 * dev[dev.length / 2], 0.12, 0.5);
        }
        chr = Math.max(chr, (longCut != null ? 1.3 : 2.2) * intra);
        word = Math.max(word, (split ? 1.25 : 2.0) * chr);
    }

    /** Gauss-Hermite-like grid over the operator's spread (log domain), with normal weights */
    private static final double[] Z = new double[25], W = new double[25];

    static {
        double sum = 0;
        for (int i = 0; i < Z.length; i++) {
            Z[i] = -3 + 6.0 * i / (Z.length - 1);
            W[i] = Math.exp(-0.5 * Z[i] * Z[i]);
            sum += W[i];
        }
        for (int i = 0; i < W.length; i++) W[i] /= sum;
    }

    /**
     * Probability of reading k ticks for a run whose true length is log-normal around {@code centre}
     * (seconds) with spread s: a run of D ticks laid at a random phase on the tick clock is read as
     * k ticks with probability max(0, 1 - |D - k|) - averaged over the operator's spread.
     */
    private double pTicks(int k, double centre, double s) {
        double p = 0;
        for (int i = 0; i < Z.length; i++) {
            double dTicks = centre * Math.exp(s * Z[i]) / quantum;
            p += W[i] * Math.max(0, 1 - Math.abs(dTicks - k));
        }
        return p;
    }

    /** the operator's own spread: what was measured, less what the tick clock adds */
    private double handSpread(double s, double centre) {
        double q = quantum / Math.max(centre, 1e-6);
        return Math.sqrt(Math.max(0.0036, s * s - q * q / 6));
    }

    private double[] quantised(double d, double[] centres, double s) {
        int k = (int) Math.round(d / quantum);
        // beyond the outer classes the reading counts as the outer class itself (as in the log-domain version)
        int lo = Math.max(1, (int) Math.floor(centres[0] / quantum)), hi = (int) Math.ceil(centres[centres.length - 1] / quantum);
        k = Math.max(lo, Math.min(hi, k));
        double[] lp = new double[centres.length];
        for (int i = 0; i < centres.length; i++)
            lp[i] = Math.log(Math.max(1e-9, pTicks(k, centres[i], handSpread(s, centres[i]))));
        return normalize(lp);
    }

    /** mark length -> {ln P(dit), ln P(dah)} */
    public double[] markLp(double d) {
        if (quantum > 0) return quantised(d, new double[]{dit, dah}, sm);
        double a = Math.log(dit), b = Math.log(dah);
        double x = clamp(Math.log(d), a, b);
        return normalize(new double[]{gaussLp(x, a, sm), gaussLp(x, b, sm)});
    }

    /** space length -> {ln P(element gap), ln P(letter gap), ln P(word gap)} */
    public double[] spaceLp(double d) {
        if (quantum > 0) return quantised(d, new double[]{intra, chr, word}, ss);
        double a = Math.log(intra), b = Math.log(chr), c = Math.log(word);
        double x = clamp(Math.log(d), a, c);
        return normalize(new double[]{gaussLp(x, a, ss), gaussLp(x, b, ss), gaussLp(x, c, ss)});
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
