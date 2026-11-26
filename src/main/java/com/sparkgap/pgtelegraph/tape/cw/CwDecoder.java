package com.sparkgap.pgtelegraph.tape.cw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Beam-search Morse decoder (ported from the cwdecoder project, decode.rs). Marks and gaps are
 * not decided one at a time: several readings ("that gap was between letters" / "between
 * elements") are carried along together, and when a word ends the lexicon scores it, so the
 * reading that makes sense survives.
 * <p>
 * Fed with on/off timing in seconds: {@link #mark} when a mark ends, {@link #space} when the next
 * mark starts, {@link #offTick} now and then while the line is quiet. Decoded symbols collect in
 * {@link #committed}; {@link #finish} settles whatever is still open.
 */
public final class CwDecoder {
    /** (shown text, code) - the international table with prosigns */
    static final String[][] TABLE = {
            {"A", ".-"}, {"B", "-..."}, {"C", "-.-."}, {"D", "-.."}, {"E", "."}, {"F", "..-."}, {"G", "--."},
            {"H", "...."}, {"I", ".."}, {"J", ".---"}, {"K", "-.-"}, {"L", ".-.."}, {"M", "--"}, {"N", "-."},
            {"O", "---"}, {"P", ".--."}, {"Q", "--.-"}, {"R", ".-."}, {"S", "..."}, {"T", "-"}, {"U", "..-"},
            {"V", "...-"}, {"W", ".--"}, {"X", "-..-"}, {"Y", "-.--"}, {"Z", "--.."},
            {"0", "-----"}, {"1", ".----"}, {"2", "..---"}, {"3", "...--"}, {"4", "....-"}, {"5", "....."},
            {"6", "-...."}, {"7", "--..."}, {"8", "---.."}, {"9", "----."},
            {".", ".-.-.-"}, {",", "--..--"}, {"?", "..--.."}, {"/", "-..-."}, {"=", "-...-"}, {"-", "-....-"},
            {"'", ".----."}, {":", "---..."}, {";", "-.-.-."}, {"\"", ".-..-."}, {"@", ".--.-."}, {"!", "-.-.--"},
            {")", "-.--.-"}, {"_", "..--.-"}, {"$", "...-..-"},
            {"<AR>", ".-.-."}, {"<SK>", "...-.-"}, {"<KN>", "-.--."}, {"<AS>", ".-..."}, {"<HH>", "........"},
    };

    static double symPrior(int i) {
        String s = TABLE[i][0];
        if (s.length() == 1 && Character.isLetterOrDigit(s.charAt(0))) return 0;
        return switch (s) {
            case "/", "?", "=", "<AR>", "<SK>", "<KN>" -> -0.7;
            case ".", "," -> -1.5;
            case "<HH>", "<AS>" -> -2.0;
            default -> -3.0;
        };
    }

    // ---------------- trie over the codes
    private static final int ROOT = 0;
    private static final int[][] NEXT;
    private static final int[] SYM;

    static {
        List<int[]> next = new ArrayList<>();
        List<Integer> sym = new ArrayList<>();
        next.add(new int[]{-1, -1});
        sym.add(-1);
        for (int i = 0; i < TABLE.length; i++) {
            int n = 0;
            for (char c : TABLE[i][1].toCharArray()) {
                int e = c == '-' ? 1 : 0;
                if (next.get(n)[e] < 0) {
                    next.add(new int[]{-1, -1});
                    sym.add(-1);
                    next.get(n)[e] = next.size() - 1;
                }
                n = next.get(n)[e];
            }
            sym.set(n, i);
        }
        NEXT = next.toArray(new int[0][]);
        SYM = sym.stream().mapToInt(Integer::intValue).toArray();
    }

    /** a decoded symbol: what to show and its code (" " = word space) */
    public record Sym(String text, String code) {
        static final Sym SPACE = new Sym(" ", " ");
    }

    private static final int BEAM = 40;
    private static final double PRUNE = 14;

    private static final class Hyp {
        int node = ROOT;
        List<double[]> elems = new ArrayList<>();
        List<Sym> out = new ArrayList<>();
        String word = "", prev = "";
        List<String> codes = new ArrayList<>();
        double score;

        Hyp copy() {
            Hyp h = new Hyp();
            h.node = node;
            h.elems = new ArrayList<>(elems);
            h.out = new ArrayList<>(out);
            h.word = word;
            h.codes = new ArrayList<>(codes);
            h.prev = prev;
            h.score = score;
            return h;
        }

        String outText() {
            StringBuilder sb = new StringBuilder();
            for (Sym s : out) sb.append(s.text());
            return sb.toString();
        }

        String code() {
            StringBuilder sb = new StringBuilder();
            for (double[] e : elems) sb.append(e[1] > e[0] ? '-' : '.');
            return sb.toString();
        }
    }

    public final CwTiming timing;
    /** which word list judges finished words */
    public enum Lexicon { NONE, ENGLISH, KOREAN }

    private final Lexicon lexicon;
    private List<Hyp> beams = new ArrayList<>(List.of(new Hyp()));
    public final List<Sym> committed = new ArrayList<>();
    private Double held, glitch;
    private double gap;
    private boolean gapOpen, gapDone;
    /** the first 24 marks only teach the speed; then everything is decoded from the start */
    private List<double[]> warm = new ArrayList<>();
    private boolean replaying;

    public CwDecoder(boolean lexicon) {
        this(lexicon ? Lexicon.ENGLISH : Lexicon.NONE, 0);
    }

    /** @param quantum measuring resolution in seconds (one game tick = 0.05) */
    public CwDecoder(Lexicon lexicon, double quantum) {
        this.lexicon = lexicon;
        this.timing = new CwTiming(quantum);
    }

    // ---------------- input
    private static final int MARK = 0, SPACE_EV = 1, OFF = 2;

    public void mark(double d) {
        event(MARK, d);
    }

    public void space(double d) {
        event(SPACE_EV, d);
    }

    public void offTick(double t) {
        event(OFF, t);
    }

    private void event(int kind, double v) {
        if (warm != null) {
            warm.add(new double[]{kind, v});
            int marks = 0;
            for (double[] e : warm) if (e[0] == MARK) marks++;
            boolean longQuiet = kind == OFF && v > 1.0;
            if (marks >= 24 || (longQuiet && marks > 0)) flushWarm();
            return;
        }
        inner(kind, v);
    }

    private void flushWarm() {
        if (warm == null) return;
        List<double[]> buf = warm;
        warm = null;
        // marks first, then gaps: learnt in arrival order the gaps would be judged against the default speed
        for (double[] e : buf) if (e[0] == MARK) timing.addMark(e[1]);
        for (double[] e : buf) if (e[0] == SPACE_EV) timing.addSpace(e[1]);
        // replay with what was just learnt, without learning the same values again (re-fitting from a
        // handful of samples during the replay is what used to garble the first word)
        replaying = true;
        for (double[] e : buf) inner((int) e[0], e[1]);
        replaying = false;
    }

    private void inner(int kind, double v) {
        double dit = timing.dit;
        if (kind == MARK) {
            double d = v;
            if (d < 0.3 * dit && held == null && glitch == null) { // a glitch: part of the gap
                if (gapOpen) gap += d;
                return;
            }
            if (glitch != null) { // the signal dropped for a moment: one mark
                double h = held == null ? 0 : held;
                held = h + glitch + d;
                glitch = null;
                return;
            }
            if (gapOpen && !gapDone) {
                if (!replaying) timing.addSpace(gap);
                onGap(timing.spaceLp(gap));
            }
            gapOpen = false;
            gapDone = false;
            gap = 0;
            held = d;
        } else if (kind == SPACE_EV) {
            double s = v;
            if (held != null) {
                if (s < 0.3 * dit) {
                    glitch = s;
                    return;
                }
                double h = held;
                held = null;
                emitMark(h);
                gapOpen = true;
                gapDone = false;
                gap = s;
            } else if (gapOpen) gap += s;
        } else {
            double t = v;
            if (glitch == null && held != null && t >= 0.3 * dit) {
                double h = held;
                held = null;
                emitMark(h);
                gapOpen = true;
                gapDone = false;
                gap = 0;
            }
            if (gapOpen && !gapDone && gap + t > 1.3 * timing.word) {
                onGap(new double[]{Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, 0});
                gapDone = true;
            }
        }
    }

    /** settle everything still open (end of a transmission) */
    public void finish() {
        flushWarm();
        if (held != null) {
            double g = glitch == null ? 0 : glitch;
            emitMark(held + g);
            held = null;
            glitch = null;
            gapOpen = true;
        }
        if (gapOpen && !gapDone) onGap(new double[]{Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, 0});
        gapOpen = false;
        gapDone = false;
        gap = 0;
        committed.addAll(beams.get(0).out);
        beams = new ArrayList<>(List.of(new Hyp()));
        warm = new ArrayList<>();
    }

    // ---------------- search
    private void emitMark(double d) {
        if (!replaying) timing.addMark(d);
        double[] lp = timing.markLp(d);
        double[] p = {Math.exp(lp[0]), Math.exp(lp[1])};
        List<Hyp> next = new ArrayList<>(beams.size() * 2);
        for (Hyp h : beams) {
            for (int e = 0; e < 2; e++) {
                int c = NEXT[h.node][e];
                if (c < 0) continue;
                Hyp n = h.copy();
                n.node = c;
                n.elems.add(p);
                n.score += lp[e];
                next.add(n);
            }
        }
        if (next.isEmpty()) { // longer than any code: a broken letter
            Hyp n = beams.get(0).copy();
            n.out.add(new Sym("*", n.code()));
            n.word += "*";
            n.node = ROOT;
            n.elems.clear();
            next.add(n);
        }
        beams = next;
        prune();
    }

    private void emitSym(Hyp h, int si) {
        h.out.add(new Sym(TABLE[si][0], TABLE[si][1]));
        h.word += TABLE[si][0];
        h.codes.add(TABLE[si][1]);
        h.score += symPrior(si);
        h.node = ROOT;
        h.elems.clear();
    }

    private void endWord(Hyp h) {
        if (!h.word.isEmpty()) {
            if (lexicon == Lexicon.ENGLISH) h.score += CwLexicon.wordScore(h.word) + CwLexicon.contextScore(h.prev, h.word);
            else if (lexicon == Lexicon.KOREAN) h.score += CwKorean.wordScore(h.codes);
            h.prev = h.word;
            h.word = "";
            h.codes = new ArrayList<>();
        }
        if (!h.out.isEmpty() && !h.out.get(h.out.size() - 1).text().equals(" ")) h.out.add(Sym.SPACE);
    }

    /** lp = {element gap, letter gap, word gap} */
    private void onGap(double[] lp) {
        List<Hyp> next = new ArrayList<>(beams.size() * 3);
        for (Hyp h : beams) {
            boolean pending = !h.elems.isEmpty();
            boolean hasChild = NEXT[h.node][0] >= 0 || NEXT[h.node][1] >= 0;
            if (Double.isFinite(lp[0]) && pending && hasChild) {
                Hyp n = h.copy();
                n.score += lp[0];
                next.add(n);
            }
            int sym = SYM[h.node];
            if (pending && sym < 0) continue;
            for (int k = 1; k <= 2; k++) {
                if (!Double.isFinite(lp[k])) continue;
                Hyp n = h.copy();
                n.score += lp[k];
                if (pending) emitSym(n, sym);
                if (k == 2) endWord(n);
                next.add(n);
            }
        }
        if (next.isEmpty()) {
            Hyp n = beams.get(0).copy();
            if (!n.elems.isEmpty()) {
                n.out.add(new Sym("*", n.code()));
                n.word += "*";
            }
            n.node = ROOT;
            n.elems.clear();
            if (Double.isFinite(lp[2])) endWord(n);
            next.add(n);
        }
        beams = next;
        prune();
        commit();
    }

    private void prune() {
        Map<String, Integer> best = new HashMap<>();
        List<Hyp> keep = new ArrayList<>();
        for (Hyp h : beams) {
            String key = h.node + "|" + h.elems.size() + "|" + h.outText();
            Integer i = best.get(key);
            if (i == null) {
                best.put(key, keep.size());
                keep.add(h);
            } else if (h.score > keep.get(i).score) keep.set(i, h);
        }
        keep.sort((a, b) -> Double.compare(b.score, a.score));
        double top = keep.get(0).score;
        keep.removeIf(h -> h.score <= top - PRUNE);
        if (keep.size() > BEAM) keep = new ArrayList<>(keep.subList(0, BEAM));
        for (Hyp h : keep) h.score -= top;
        beams = keep;
    }

    /** the beginning every reading agrees on is final; a very long tail is forced from the best */
    private void commit() {
        Hyp b0 = beams.get(0);
        int bestLen = b0.out.size(), n = bestLen;
        for (int i = 1; i < beams.size(); i++) {
            Hyp h = beams.get(i);
            int c = 0;
            while (c < h.out.size() && c < b0.out.size() && h.out.get(c).text().equals(b0.out.get(c).text())) c++;
            n = Math.min(n, c);
        }
        if (bestLen - n > 24) {
            n = bestLen - 12;
            final int cut = n;
            List<Sym> prefix = new ArrayList<>(b0.out.subList(0, cut));
            beams.removeIf(h -> {
                if (h.out.size() < cut) return true;
                for (int i = 0; i < cut; i++) if (!h.out.get(i).text().equals(prefix.get(i).text())) return true;
                return false;
            });
        }
        if (n == 0) return;
        committed.addAll(new ArrayList<>(beams.get(0).out.subList(0, n)));
        for (Hyp h : beams) h.out = new ArrayList<>(h.out.subList(n, h.out.size()));
    }

    // ---------------- batch use
    /**
     * Decode a whole recording (mark, space, mark, ... in ticks of {@code tickSeconds}) into codes,
     * with {@link #WORD} between words.
     */
    public static List<String> decodeRuns(List<Integer> runs, double tickSeconds, boolean lexicon) {
        return decodeRuns(runs, tickSeconds, lexicon ? Lexicon.ENGLISH : Lexicon.NONE);
    }

    public static List<String> decodeRuns(List<Integer> runs, double tickSeconds, Lexicon lexicon) {
        CwDecoder d = new CwDecoder(lexicon, tickSeconds);
        for (int i = 0; i < runs.size(); i++) {
            double v = Math.max(1, runs.get(i)) * tickSeconds;
            if (i % 2 == 0) d.mark(v);
            else d.space(v);
        }
        d.offTick(60);
        d.finish();
        List<String> out = new ArrayList<>();
        for (Sym s : d.committed) {
            if (s.code().equals(" ")) {
                if (!out.isEmpty() && !out.get(out.size() - 1).equals(WORD)) out.add(WORD);
            } else out.add(s.code());
        }
        while (!out.isEmpty() && out.get(out.size() - 1).equals(WORD)) out.remove(out.size() - 1);
        return out;
    }

    public static final String WORD = " ";
}
