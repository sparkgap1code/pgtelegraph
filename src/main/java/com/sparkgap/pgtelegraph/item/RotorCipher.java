package com.sparkgap.pgtelegraph.item;

import java.util.Locale;
import java.util.Random;

/**
 * Three-rotor cipher machine with reflector and plugboard. Self-reciprocal: with the same
 * settings, enciphering the ciphertext gives back the plaintext. Only A-Z are enciphered (and
 * step the rotors); everything else passes through. The five rotor wirings and the reflector are
 * this mod's own, fixed for every machine so that any two machines can talk.
 *
 * Settings string: "r1r2r3 POS PLUGS", e.g. "152 KQX AB CD EF" (rotors left to right, 1-5,
 * starting letters, plug pairs).
 */
public final class RotorCipher {
    private RotorCipher() {}

    static final int[][] WIRING = new int[5][26], INVERSE = new int[5][26];
    static final int[] NOTCH = {16, 4, 21, 9, 25};
    static final int[] REFLECTOR = new int[26];

    static {
        Random r = new Random(0x51A7_C0DEL);
        for (int w = 0; w < 5; w++) {
            int[] p = perm(r);
            WIRING[w] = p;
            for (int i = 0; i < 26; i++) INVERSE[w][p[i]] = i;
        }
        // reflector: random fixed-point-free involution
        int[] order = perm(r);
        for (int i = 0; i < 26; i += 2) {
            REFLECTOR[order[i]] = order[i + 1];
            REFLECTOR[order[i + 1]] = order[i];
        }
    }

    private static int[] perm(Random r) {
        int[] p = new int[26];
        for (int i = 0; i < 26; i++) p[i] = i;
        for (int i = 25; i > 0; i--) {
            int j = r.nextInt(i + 1), t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        return p;
    }

    public record Settings(int[] rotors, int[] start, int[] plug) {
        public static final String DEFAULT = "123 AAA";

        public static Settings parse(String s) {
            int[] rot = {0, 1, 2}, pos = {0, 0, 0}, plug = new int[26];
            for (int i = 0; i < 26; i++) plug[i] = i;
            String[] parts = (s == null ? DEFAULT : s).trim().toUpperCase(Locale.ROOT).split("\\s+");
            if (parts.length > 0 && parts[0].length() == 3) {
                for (int i = 0; i < 3; i++) {
                    int d = parts[0].charAt(i) - '1';
                    if (d >= 0 && d < 5) rot[i] = d;
                }
            }
            if (parts.length > 1 && parts[1].length() == 3) {
                for (int i = 0; i < 3; i++) {
                    int d = parts[1].charAt(i) - 'A';
                    if (d >= 0 && d < 26) pos[i] = d;
                }
            }
            for (int k = 2; k < parts.length; k++) {
                String pr = parts[k];
                if (pr.length() != 2) continue;
                int a = pr.charAt(0) - 'A', b = pr.charAt(1) - 'A';
                if (a < 0 || a > 25 || b < 0 || b > 25 || a == b || plug[a] != a || plug[b] != b) continue;
                plug[a] = b;
                plug[b] = a;
            }
            return new Settings(rot, pos, plug);
        }
    }

    public static String run(String text, String settings) {
        Settings st = Settings.parse(settings);
        int[] pos = st.start().clone();
        StringBuilder out = new StringBuilder();
        for (char c0 : text.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c0 < 'A' || c0 > 'Z') {
                out.append(c0);
                continue;
            }
            // stepping with the double step of the middle rotor
            boolean midAtNotch = pos[1] == NOTCH[st.rotors()[1]];
            boolean rightAtNotch = pos[2] == NOTCH[st.rotors()[2]];
            if (midAtNotch) {
                pos[0] = (pos[0] + 1) % 26;
                pos[1] = (pos[1] + 1) % 26;
            } else if (rightAtNotch) pos[1] = (pos[1] + 1) % 26;
            pos[2] = (pos[2] + 1) % 26;

            int x = st.plug()[c0 - 'A'];
            for (int i = 2; i >= 0; i--) x = through(WIRING[st.rotors()[i]], x, pos[i]);
            x = REFLECTOR[x];
            for (int i = 0; i <= 2; i++) x = through(INVERSE[st.rotors()[i]], x, pos[i]);
            out.append((char) ('A' + st.plug()[x]));
        }
        return out.toString();
    }

    private static int through(int[] w, int x, int p) {
        return Math.floorMod(w[(x + p) % 26] - p, 26);
    }
}
