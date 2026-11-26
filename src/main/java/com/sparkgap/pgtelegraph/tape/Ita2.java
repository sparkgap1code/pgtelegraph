package com.sparkgap.pgtelegraph.tape;

import java.util.ArrayList;
import java.util.List;

/**
 * ITA2 (Baudot-Murray) 5-bit teleprinter code with LTRS/FIGS shifts. Codes are given LSB first
 * (bit 0 = first data bit on the line).
 */
public final class Ita2 {
    private Ita2() {}

    public static final int LTRS = 0x1F, FIGS = 0x1B, SPACE = 0x04, CR = 0x08, LF = 0x02;
    private static final char[] LETTERS = new char[32], FIGURES = new char[32];

    static {
        String l = "\0E\nA SIU\rDRJNFCKTZLWHYPQOBG\0MXV\0";
        // index = code; \u0005 = WRU (who are you), \u0007 = bell
        String f = "\u00003\n- '87\r\u00054\u0007,!:(5+)2#6019?&\u0000./=\u0000";
        for (int i = 0; i < 32; i++) {
            LETTERS[i] = l.charAt(i);
            FIGURES[i] = f.charAt(i);
        }
    }

    /** text -> 5-bit codes, inserting shifts; unknown characters become spaces */
    public static List<Integer> encode(String text) {
        List<Integer> out = new ArrayList<>();
        boolean figs = false;
        out.add(LTRS);
        for (char c0 : text.toUpperCase().toCharArray()) {
            char c = c0 == '\n' ? '\r' : c0;
            if (c == ' ') {
                out.add(SPACE);
                continue;
            }
            if (c == '\r') {
                out.add(CR);
                out.add(LF);
                continue;
            }
            int li = indexOf(LETTERS, c), fi = indexOf(FIGURES, c);
            if (li > 0 && li != LTRS && li != FIGS) {
                if (figs) {
                    out.add(LTRS);
                    figs = false;
                }
                out.add(li);
            } else if (fi > 0 && fi != LTRS && fi != FIGS) {
                if (!figs) {
                    out.add(FIGS);
                    figs = true;
                }
                out.add(fi);
            } else out.add(SPACE);
        }
        return out;
    }

    private static int indexOf(char[] t, char c) {
        for (int i = 1; i < t.length; i++) if (t[i] == c) return i;
        return -1;
    }

    /** Start-stop sender, {@value #BIT_TICKS} ticks per bit, line idle = space. */
    public static final class Sender {
        public static final int BIT_TICKS = 2, CHAR_BITS = 8;
        private final java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        private int bit = -1, code, sub;

        public void send(String text) {
            if (queue.size() < 4000) queue.addAll(encode(text));
        }

        public boolean busy() {
            return bit >= 0 || !queue.isEmpty();
        }

        /** one tick; true = mark */
        public boolean tick() {
            if (bit < 0) {
                if (queue.isEmpty()) return false;
                code = queue.poll();
                bit = 0;
                sub = 0;
            }
            boolean mark = bit == 0 || (bit <= 5 && ((code >> (bit - 1)) & 1) != 0);
            if (++sub >= BIT_TICKS) {
                sub = 0;
                if (++bit >= CHAR_BITS) bit = -1;
            }
            return mark;
        }
    }

    /** Start-stop receiver sampling mid-bit; returns printed characters. */
    public static final class Receiver {
        private boolean was;
        private int t = -1, code;
        private final Printer printer = new Printer();

        /** one tick of line state; the printed character, or 0 */
        public char tick(boolean mark) {
            char out = 0;
            if (t < 0) {
                if (mark && !was) {
                    t = 0;
                    code = 0;
                }
            } else {
                t++;
                int b = t / Sender.BIT_TICKS;
                if (t % Sender.BIT_TICKS == Sender.BIT_TICKS / 2 && b >= 1 && b <= 5 && mark) code |= 1 << (b - 1);
                if (t >= 6 * Sender.BIT_TICKS) {
                    t = -1;
                    out = printer.print(code);
                }
            }
            was = mark;
            return out;
        }
    }

    /** Receiving printer state: tracks the shift. */
    public static final class Printer {
        private boolean figs;

        /** the character printed for a code, or 0 for none (shift, NUL, LF) */
        public char print(int code) {
            code &= 31;
            if (code == LTRS) {
                figs = false;
                return 0;
            }
            if (code == FIGS) {
                figs = true;
                return 0;
            }
            if (code == SPACE) {
                figs = false; // unshift on space, as most machines do
                return ' ';
            }
            if (code == CR) return '\n';
            if (code == LF || code == 0) return 0;
            char c = figs ? FIGURES[code] : LETTERS[code];
            return c == '\u0005' || c == '\u0007' ? 0 : c;
        }
    }
}
