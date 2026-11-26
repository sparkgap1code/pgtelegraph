package com.sparkgap.pgtelegraph.tape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * International (English) and Korean (한글) Morse. Encoding turns text into unit runs
 * (dit 1, dah 3, element gap 1, character gap 3, word gap 7). Decoding classifies recorded
 * runs back into codes and renders them with both tables, since a tape alone cannot tell
 * which table the sender used.
 */
public final class Morse {
    private Morse() {}

    public static final String WORD = " ";

    private static final Map<Character, String> EN = new HashMap<>();
    private static final Map<String, Character> EN_REV = new HashMap<>();
    private static final Map<Character, String> KO = new HashMap<>();
    private static final Map<String, Character> KO_REV = new HashMap<>();

    private static void en(char c, String code) {
        EN.put(c, code);
        EN_REV.put(code, c);
    }

    private static void ko(char c, String code) {
        KO.put(c, code);
        KO_REV.put(code, c);
    }

    static {
        String[] letters = {".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-",
                ".-..", "--", "-.", "---", ".--.", "--.-", ".-.", "...", "-", "..-", "...-", ".--", "-..-",
                "-.--", "--.."};
        for (int i = 0; i < 26; i++) en((char) ('A' + i), letters[i]);
        String[] digits = {"-----", ".----", "..---", "...--", "....-", ".....", "-....", "--...", "---..", "----."};
        for (int i = 0; i < 10; i++) en((char) ('0' + i), digits[i]);
        en('.', ".-.-.-");
        en(',', "--..--");
        en('?', "..--..");
        en('/', "-..-.");
        en('=', "-...-");
        en('-', "-....-");
        en('\'', ".----.");
        en('(', "-.--.");
        en(')', "-.--.-");
        en(':', "---...");
        en('"', ".-..-.");
        en('@', ".--.-.");
        en('!', "-.-.--");
        en('+', ".-.-.");
        en('&', ".-...");

        ko('ㄱ', ".-..");
        ko('ㄴ', "..-.");
        ko('ㄷ', "-...");
        ko('ㄹ', "...-");
        ko('ㅁ', "--");
        ko('ㅂ', ".--");
        ko('ㅅ', "--.");
        ko('ㅇ', "-.-");
        ko('ㅈ', ".--.");
        ko('ㅊ', "-.-.");
        ko('ㅋ', "-..-");
        ko('ㅌ', "--..");
        ko('ㅍ', "---");
        ko('ㅎ', ".---");
        ko('ㅏ', ".");
        ko('ㅑ', "..");
        ko('ㅓ', "-");
        ko('ㅕ', "...");
        ko('ㅗ', ".-");
        ko('ㅛ', "-.");
        ko('ㅜ', "....");
        ko('ㅠ', ".-.");
        ko('ㅡ', "-..");
        ko('ㅣ', "..-");
        ko('ㅐ', "--.-");
        ko('ㅔ', "-.--");
    }

    // ---- Hangul tables ----
    private static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final String JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";
    private static final String JONG = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";
    /** compound jamo -> the two basic jamo it is sent as */
    private static final Map<Character, String> SPLIT = new HashMap<>();
    private static final Map<String, Character> JOIN = new HashMap<>();

    static {
        String[][] pairs = {
                {"ㄲ", "ㄱㄱ"}, {"ㄸ", "ㄷㄷ"}, {"ㅃ", "ㅂㅂ"}, {"ㅆ", "ㅅㅅ"}, {"ㅉ", "ㅈㅈ"},
                {"ㄳ", "ㄱㅅ"}, {"ㄵ", "ㄴㅈ"}, {"ㄶ", "ㄴㅎ"}, {"ㄺ", "ㄹㄱ"}, {"ㄻ", "ㄹㅁ"}, {"ㄼ", "ㄹㅂ"},
                {"ㄽ", "ㄹㅅ"}, {"ㄾ", "ㄹㅌ"}, {"ㄿ", "ㄹㅍ"}, {"ㅀ", "ㄹㅎ"}, {"ㅄ", "ㅂㅅ"},
                {"ㅒ", "ㅑㅣ"}, {"ㅖ", "ㅕㅣ"}, {"ㅘ", "ㅗㅏ"}, {"ㅙ", "ㅗㅐ"}, {"ㅚ", "ㅗㅣ"},
                {"ㅝ", "ㅜㅓ"}, {"ㅞ", "ㅜㅔ"}, {"ㅟ", "ㅜㅣ"}, {"ㅢ", "ㅡㅣ"},
        };
        for (String[] p : pairs) {
            SPLIT.put(p[0].charAt(0), p[1]);
            JOIN.put(p[1], p[0].charAt(0));
        }
    }

    private static boolean isVowel(char c) {
        return JUNG.indexOf(c) >= 0;
    }

    private static void addJamo(List<String> out, char j) {
        String s = SPLIT.get(j);
        if (s != null) {
            for (char c : s.toCharArray()) out.add(KO.get(c));
        } else if (KO.containsKey(j)) {
            out.add(KO.get(j));
        }
    }

    /** Text -> list of codes (".-") with WORD separators. Hangul uses the Korean table. */
    public static List<String> codes(String text) {
        List<String> out = new ArrayList<>();
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                if (!out.isEmpty() && !out.get(out.size() - 1).equals(WORD)) out.add(WORD);
            } else if (c >= 0xAC00 && c <= 0xD7A3) {
                int s = c - 0xAC00;
                addJamo(out, CHO.charAt(s / (21 * 28)));
                addJamo(out, JUNG.charAt((s / 28) % 21));
                int jong = s % 28;
                if (jong != 0) addJamo(out, JONG.charAt(jong));
            } else if (c >= 0x3131 && c <= 0x318E) {
                addJamo(out, c);
            } else {
                String code = EN.get(Character.toUpperCase(c));
                if (code != null) out.add(code);
            }
        }
        while (!out.isEmpty() && out.get(out.size() - 1).equals(WORD)) out.remove(out.size() - 1);
        return out;
    }

    /** Codes -> runs in Morse units (mark, space, mark, ...), no leading or trailing space. */
    public static List<Integer> encodeUnits(List<String> codes) {
        List<Integer> runs = new ArrayList<>();
        int gap = 3;
        for (String code : codes) {
            if (code.equals(WORD)) {
                gap = 7;
                continue;
            }
            for (int i = 0; i < code.length(); i++) {
                if (!runs.isEmpty()) runs.add(i == 0 ? gap : 1);
                runs.add(code.charAt(i) == '.' ? 1 : 3);
            }
            gap = 3;
        }
        return runs;
    }

    /** Human preview of codes, e.g. "... --- ... / .-". */
    public static String preview(List<String> codes) {
        StringBuilder sb = new StringBuilder();
        for (String c : codes) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(c.equals(WORD) ? "/" : c);
        }
        return sb.toString();
    }

    // ---------------- decoding ----------------

    /**
     * Runs -> codes. For PUNCHED tape the unit is 1; for INKED tape the unit is estimated from
     * the recording (operators are never exact, so this clusters short vs long marks).
     */
    public static List<String> decodeCodes(TapeData tape) {
        return decodeCodes(tape, false);
    }

    /**
     * @param lexicon lean ambiguous code towards English / ham words (off when the language is unknown
     *                or Korean)
     */
    public static List<String> decodeCodes(TapeData tape, boolean lexicon) {
        List<Integer> runs = tape.runs();
        List<String> out = new ArrayList<>();
        if (runs.isEmpty()) return out;
        if (tape.kind() != TapeData.PUNCHED)
            return com.sparkgap.pgtelegraph.tape.cw.CwDecoder.decodeRuns(runs, 0.05, lexicon); // one tick = 50 ms
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < runs.size(); i++) {
            int r = runs.get(i);
            if (i % 2 == 0) {
                cur.append(r >= 2 ? '-' : '.');
            } else {
                if (r < 2) continue;
                if (cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
                if (r >= 5) out.add(WORD);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    static double estimateUnit(List<Integer> runs) {
        List<Integer> marks = new ArrayList<>();
        int minSpace = Integer.MAX_VALUE;
        for (int i = 0; i < runs.size(); i++) {
            if (i % 2 == 0) marks.add(runs.get(i));
            else minSpace = Math.min(minSpace, runs.get(i));
        }
        marks.sort(null);
        // largest ratio jump between consecutive sorted mark lengths separates dits from dahs
        double bestRatio = 1;
        int split = -1;
        for (int i = 1; i < marks.size(); i++) {
            double ratio = (double) marks.get(i) / Math.max(1, marks.get(i - 1));
            if (ratio > bestRatio) {
                bestRatio = ratio;
                split = i;
            }
        }
        if (bestRatio >= 1.8) {
            double shortMean = 0, longMean = 0;
            for (int i = 0; i < split; i++) shortMean += marks.get(i);
            for (int i = split; i < marks.size(); i++) longMean += marks.get(i);
            shortMean /= split;
            longMean /= marks.size() - split;
            return (shortMean + longMean / 3) / 2;
        }
        // one cluster only: compare with the shortest gap (≈ 1 unit)
        double mean = 0;
        for (int m : marks) mean += m;
        mean /= marks.size();
        if (minSpace != Integer.MAX_VALUE && mean >= 2 * minSpace) return mean / 3;
        return mean;
    }

    public static String toEnglish(List<String> codes) {
        StringBuilder sb = new StringBuilder();
        for (String c : codes) {
            if (c.equals(WORD)) sb.append(' ');
            else sb.append(EN_REV.getOrDefault(c, '*'));
        }
        return sb.toString();
    }

    /** Korean table, jamo composed into syllables. Codes not in the Korean table fall back to digits/punctuation. */
    public static String toKorean(List<String> codes) {
        Composer k = new Composer();
        for (String c : codes) {
            if (c.equals(WORD)) {
                k.other(' ');
                continue;
            }
            Character j = KO_REV.get(c);
            if (j != null) k.jamo(j);
            else k.other(EN_REV.getOrDefault(c, '*'));
        }
        return k.finish();
    }

    /** Two-set style automaton: builds syllables from a stream of basic jamo. */
    private static final class Composer {
        private final StringBuilder out = new StringBuilder();
        private char cho, jung, jong;

        void other(char c) {
            flush();
            out.append(c);
        }

        void jamo(char c) {
            if (isVowel(c)) vowel(c);
            else consonant(c);
        }

        private void consonant(char c) {
            if (jung == 0) {
                if (cho != 0) {
                    Character d = JOIN.get("" + cho + c);
                    if (d != null && CHO.indexOf(d) >= 0) {
                        cho = d;
                        return;
                    }
                    flush();
                }
                cho = c;
                return;
            }
            if (cho == 0) {
                flush();
                cho = c;
                return;
            }
            if (jong == 0) {
                if (JONG.indexOf(c) > 0) {
                    jong = c;
                    return;
                }
                flush();
                cho = c;
                return;
            }
            Character d = JOIN.get("" + jong + c);
            if (d != null && JONG.indexOf(d) > 0) {
                jong = d;
                return;
            }
            flush();
            cho = c;
        }

        private void vowel(char c) {
            if (jong != 0) {
                // the final consonant (or the last half of a compound one) starts the next syllable
                char moved;
                String s = SPLIT.get(jong);
                if (s != null) {
                    jong = s.charAt(0);
                    moved = s.charAt(1);
                } else {
                    moved = jong;
                    jong = 0;
                }
                flush();
                cho = moved;
                jung = c;
                return;
            }
            if (jung != 0) {
                Character d = JOIN.get("" + jung + c);
                if (d != null) {
                    jung = d;
                    return;
                }
                flush();
            }
            jung = c;
        }

        private void flush() {
            if (cho != 0 && jung != 0) {
                int ci = CHO.indexOf(cho), ji = JUNG.indexOf(jung), ti = jong == 0 ? 0 : JONG.indexOf(jong);
                out.append((char) (0xAC00 + (ci * 21 + ji) * 28 + ti));
            } else {
                if (cho != 0) out.append(cho);
                if (jung != 0) out.append(jung);
            }
            cho = jung = jong = 0;
        }

        String finish() {
            flush();
            return out.toString();
        }
    }
}
