package com.sparkgap.pgtelegraph.tape.cw;

import java.util.Set;

/**
 * Ham-radio context rules (ported from cwdecoder, lexicon.rs): no training, a word list plus
 * patterns. Every finished word is scored so that ambiguous code leans towards what makes sense.
 */
public final class CwLexicon {
    private CwLexicon() {}

    private static final Set<String> HAM = Set.of(
            "CQ", "DE", "K", "KN", "BK", "R", "RR", "TU", "TNX", "TKS", "FB", "OM", "YL", "XYL",
            "UR", "URS", "RST", "NAME", "OP", "QTH", "QRZ", "QRL", "QRM", "QRN", "QSB", "QSL",
            "QSO", "QSY", "QRP", "QRO", "QRS", "QRQ", "QRT", "QRV", "QRX", "QRU", "QTC", "QSK",
            "73", "88", "GL", "GM", "GA", "GE", "GN", "HW", "HR", "CPY", "CPI", "ES", "WX",
            "TEMP", "RIG", "ANT", "PWR", "W", "WATTS", "DIPOLE", "YAGI", "VERT", "BEAM", "LOOP",
            "AGN", "PSE", "SRI", "HI", "HIHI", "DR", "FER", "ABT", "CUL", "BCNU", "GUD", "VY",
            "NR", "TEST", "NIL", "OK", "HPE", "UP", "DN", "SUNNY", "CLOUDY", "RAIN", "SNOW",
            "FINE", "WARM", "COLD", "C", "F", "QRPP", "EU", "AS", "NA", "SA", "AF", "OC", "DX",
            "POTA", "SOTA", "IOTA", "WWFF", "CWT", "SST", "MST", "CWOPS", "FISTS", "SKCC", "QCX",
            "KX2", "KX3", "IC7300", "FT991", "FT817", "FT818", "TS590", "K3", "K4", "EFHW",
            "INV", "LW", "WIRE", "ELBUG", "BUG", "PADDLE", "KEY", "SK", "EE", "TTT",
            "MNI", "BTU", "CFM", "INFO", "SOLID", "LOUD", "WEAK", "AGE", "YRS", "RETIRED",
            "ENJOY", "NICE", "MEET", "SOON", "BEST", "WISHES", "ALL", "BACK", "TO", "YOU",
            // Minecraft words players send
            "MC", "BASE", "SPAWN", "NETHER", "END", "MINE", "DIAMOND", "IRON", "HELP", "COME");

    private static final Set<String> ENGLISH = Set.of(
            "THE", "AND", "IS", "IN", "OF", "IT", "I", "AM", "MY", "ON", "AT", "FOR", "WITH",
            "HERE", "THERE", "NOW", "SO", "BUT", "NOT", "NO", "YES", "A", "AN", "BE", "ARE",
            "WAS", "HAVE", "HAS", "VERY", "GOOD", "GREAT", "THANKS", "THANK", "TODAY",
            "TONIGHT", "MORNING", "EVENING", "DAY", "NIGHT", "WEEK", "WEEKEND", "HOME", "NEAR",
            "CITY", "TOWN", "ME", "WE", "OUR", "YOUR", "HE", "SHE", "THEY", "WILL", "CAN", "GO",
            "GET", "SEE", "HEAR", "HEARD", "COPY", "SIGNAL", "SIG", "BAND", "CONDX", "CONDITIONS",
            "JUST", "ALSO", "TIME", "WORK", "FROM", "BY", "ABOUT", "OUT", "IF", "THAT", "THIS",
            "ONE", "TWO", "THREE", "FIVE", "TEN", "OLD", "NEW", "BIG", "LITTLE", "HOPE", "AGAIN",
            "HELLO", "HI", "SOS");

    private static boolean alnum(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    /** callsign shape: [prefix 1-3, with a letter][digit][suffix 1-4 letters], '/' parts allowed */
    public static boolean isCallsign(String w) {
        String[] parts = w.split("/", -1);
        if (parts.length > 3) return false;
        int mainI = 0;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) return false;
            if (parts[i].length() > parts[mainI].length()) mainI = i;
        }
        for (int i = 0; i < parts.length; i++) {
            if (i == mainI) continue;
            if (parts[i].length() > 4) return false;
            for (char c : parts[i].toCharArray()) if (!alnum(c)) return false;
        }
        String b = parts[mainI];
        if (b.length() < 3 || b.length() > 8) return false;
        for (char c : b.toCharArray()) if (!alnum(c)) return false;
        int d = -1;
        for (int i = b.length() - 1; i >= 0; i--)
            if (digit(b.charAt(i))) {
                d = i;
                break;
            }
        if (d < 0) return false;
        String pre = b.substring(0, d), suf = b.substring(d + 1);
        if (pre.length() < 1 || pre.length() > 3 || suf.length() < 1 || suf.length() > 4) return false;
        boolean letter = false;
        for (char c : pre.toCharArray()) if (!digit(c)) letter = true;
        if (!letter) return false;
        for (char c : suf.toCharArray()) if (digit(c)) return false;
        return true;
    }

    private static boolean isRst(String w) {
        if (w.length() != 3) return false;
        char a = w.charAt(0);
        if (a < '1' || a > '5') return false;
        for (int i = 1; i < 3; i++) {
            char c = w.charAt(i);
            if (!(c == 'N' || (c >= '1' && c <= '9'))) return false;
        }
        return true;
    }

    private static boolean isCutNumber(String w) {
        if (w.length() < 2 || w.length() > 4) return false;
        boolean hasDigit = false;
        for (char c : w.toCharArray()) {
            if (digit(c)) hasDigit = true;
            else if ("TONAE".indexOf(c) < 0) return false;
        }
        return hasDigit;
    }

    private static boolean isNumberLike(String w) {
        if (w.isEmpty()) return false;
        boolean all = true;
        for (char c : w.toCharArray()) if (!digit(c)) all = false;
        if (all || isRst(w)) return true;
        if (w.endsWith("W") && w.length() > 1) {
            boolean d = true;
            for (char c : w.substring(0, w.length() - 1).toCharArray()) if (!digit(c)) d = false;
            if (d) return true;
        }
        return isCutNumber(w);
    }

    private static boolean isValid(String w) {
        return HAM.contains(w) || ENGLISH.contains(w) || isCallsign(w) || isNumberLike(w);
    }

    /** fewest known pieces w splits into (each 2+ letters, at least one a callsign/number), or -1 */
    private static int segment(String w) {
        int n = w.length();
        if (n < 4 || n > 40) return -1;
        int[] cnt = new int[n + 1];
        boolean[] strong = new boolean[n + 1], ok = new boolean[n + 1];
        ok[0] = true;
        for (int i = 0; i < n; i++) {
            if (!ok[i]) continue;
            for (int j = i + 2; j <= n; j++) {
                String p = w.substring(i, j);
                if (!isValid(p)) continue;
                boolean st = strong[i] || isCallsign(p) || isNumberLike(p);
                int k = cnt[i] + 1;
                if (!ok[j] || k < cnt[j] || (k == cnt[j] && st && !strong[j])) {
                    ok[j] = true;
                    cnt[j] = k;
                    strong[j] = st;
                }
            }
        }
        return ok[n] && strong[n] ? cnt[n] : -1;
    }

    /** two words run together because a word gap was missed ("5NNBT", "TESTON4XX"): penalty */
    private static double gluedPenalty(String w) {
        int k = segment(w);
        double seg = k >= 2 ? -4.5 * (k - 1) : 0;
        if (w.length() > 3 && (w.contains("5NN") || w.contains("599"))) return Math.min(seg, -7);
        if (seg < 0) return seg;
        if (w.length() > 3 && (isRst(w.substring(0, 3)) || isRst(w.substring(w.length() - 3)))) return -7;
        for (String p : new String[]{"CQ", "TEST", "TU"})
            if (w.length() > p.length() + 1 && (w.startsWith(p) || w.endsWith(p))) return -5;
        for (int i = 2; i < w.length() - 1; i++) {
            String l = w.substring(0, i), r = w.substring(i);
            if (isValid(l) && isValid(r) && (isCallsign(l) || isCallsign(r) || isNumberLike(l) || isNumberLike(r))) return -5;
        }
        return 0;
    }

    /** log score of one word (0 = makes sense, negative = odd) */
    public static double wordScore(String w) {
        if (w.isEmpty() || w.startsWith("<") || w.equals("?") || w.equals("=") || w.equals("/") || w.equals(".") || w.equals(","))
            return 0;
        String t = w.replaceAll("[?.,]+$", "");
        if (isValid(t)) return 0;
        for (char c : t.toCharArray()) if (!alnum(c) && c != '/') return -2.5;
        double g = gluedPenalty(t);
        if (g < 0) return g;
        if (t.length() == 1) return -2.5;
        return -1.8;
    }

    /** context: after DE comes a callsign, after CQ comes CQ / DE / an event tag / a callsign */
    public static double contextScore(String prev, String w) {
        String t = w.replaceAll("\\?+$", "");
        if (prev.equals("DE") && !isCallsign(t)) return -1.5;
        if (prev.equals("CQ") && !Set.of("CQ", "DE", "TEST", "POTA", "SOTA", "DX", "NA", "EU", "AS", "SA", "AF", "OC", "WWFF", "IOTA").contains(w)
                && !isCallsign(w)) return -1.5;
        return 0;
    }
}
