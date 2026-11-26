package com.sparkgap.pgtelegraph.sat;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Everything a satellite does, as key=value settings (set on the ground in the CubeSat screen,
 * or in orbit with "SET KEY VALUE" sent over the RTTY uplink).
 * <pre>
 * NAME     satellite name                         POWER  transmitter power (W, 0.1-5)
 * CW       CW beacon on/off (1/0)                  CWHZ   beacon frequency (Hz)
 * CWTEXT   beacon text; {NAME} {LAT} {LON} {SFI} {KP} {MUF} {FOF2} {FLARE} are filled in
 * CWWPM    beacon speed 5-40                       CWGAP  pause between beacons (s)
 * XP       linear transponder on/off               XPUP   uplink passband bottom (Hz)
 * XPDOWN   downlink passband bottom (Hz)           XPWIDTH passband width (Hz, 1k-200k)
 * XPINV    inverting transponder (1) or not (0)
 * RTTY     RTTY (ITA2, 10 baud) on/off             RTTYUP / RTTYDOWN  frequencies (Hz)
 * IONO     ionosphere sounder report over RTTY     BBS    store-and-forward mailbox over RTTY
 * RTTYGAP  pause between RTTY broadcasts (s)
 * SSTV     slow-scan TV pictures of the Earth below on/off   SSTVHZ  frequency   SSTVGAP pause (s)
 * ISL      inter-satellite link: relay what other ISL satellites hear (and they relay ours)
 * </pre>
 */
public final class SatConfig {
    public static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("NAME", "CUBESAT");
        DEFAULTS.put("POWER", "0.5");
        DEFAULTS.put("CW", "1");
        DEFAULTS.put("CWHZ", "435795000");
        DEFAULTS.put("CWTEXT", "VVV DE {NAME} {LAT} {LON}");
        DEFAULTS.put("CWWPM", "18");
        DEFAULTS.put("CWGAP", "5");
        DEFAULTS.put("XP", "1");
        DEFAULTS.put("XPUP", "145900000");
        DEFAULTS.put("XPDOWN", "435800000");
        DEFAULTS.put("XPWIDTH", "30000");
        DEFAULTS.put("XPINV", "0");
        DEFAULTS.put("RTTY", "0");
        DEFAULTS.put("RTTYUP", "145850000");
        DEFAULTS.put("RTTYDOWN", "435900000");
        DEFAULTS.put("IONO", "0");
        DEFAULTS.put("BBS", "0");
        DEFAULTS.put("RTTYGAP", "10");
        DEFAULTS.put("SSTV", "0");
        DEFAULTS.put("SSTVHZ", "145800000");
        DEFAULTS.put("SSTVGAP", "60");
        DEFAULTS.put("APT", "0");
        DEFAULTS.put("APTHZ", "137620000");
        DEFAULTS.put("ISL", "0");
        DEFAULTS.put("TV", "0");
        DEFAULTS.put("TVUP", "860000000");
        DEFAULTS.put("TVDOWN", "740000000");
        DEFAULTS.put("TVCAM", "0");
        DEFAULTS.put("ANT", "OMNI");
        DEFAULTS.put("SPOTLAT", "");
        DEFAULTS.put("SPOTLON", "");
    }

    private final Map<String, String> v = new LinkedHashMap<>(DEFAULTS);

    public static SatConfig parse(String s) {
        SatConfig c = new SatConfig();
        if (s == null || s.isEmpty()) return c;
        for (String part : s.split("\u001f")) {
            int i = part.indexOf('=');
            if (i > 0) c.set(part.substring(0, i), part.substring(i + 1));
        }
        return c;
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (var e : v.entrySet()) {
            if (sb.length() > 0) sb.append('\u001f');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /** set a value after checking it; returns the stored value, or null if the key or value is not valid */
    public String set(String key, String value) {
        String k = key.trim().toUpperCase(Locale.ROOT), val = value.trim();
        if (!DEFAULTS.containsKey(k)) return null;
        try {
            switch (k) {
                case "NAME" -> {
                    val = val.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9/-]", "");
                    if (val.isEmpty()) return null;
                    if (val.length() > 12) val = val.substring(0, 12);
                }
                case "CWTEXT" -> {
                    if (val.length() > 80) val = val.substring(0, 80);
                }
                case "POWER" -> val = fmt(clamp(Double.parseDouble(val), 0.1, 100));
                case "ANT" -> {
                    val = val.toUpperCase(Locale.ROOT);
                    if (!val.equals("OMNI") && !val.equals("EARTH") && !val.equals("SPOT")) return null;
                }
                case "SPOTLAT" -> val = String.format(Locale.ROOT, "%.3f", clamp(Double.parseDouble(val), -90, 90));
                case "SPOTLON" -> val = String.format(Locale.ROOT, "%.3f", clamp(Double.parseDouble(val), -180, 180));
                case "CW", "XP", "XPINV", "RTTY", "IONO", "BBS", "SSTV", "ISL", "TV", "TVCAM" -> val = flag(val) ? "1" : "0";
                case "CWWPM" -> val = String.valueOf((long) clamp(Double.parseDouble(val), 5, 40));
                case "CWGAP", "RTTYGAP", "SSTVGAP" -> val = String.valueOf((long) clamp(Double.parseDouble(val), 0, 600));
                case "XPWIDTH" -> val = String.valueOf((long) clamp(Double.parseDouble(val), 1000, 200_000));
                default -> val = String.valueOf((long) clamp(Double.parseDouble(val), 1_000_000, 30_000_000_000.0));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        v.put(k, val);
        return val;
    }

    private static boolean flag(String s) {
        String u = s.toUpperCase(Locale.ROOT);
        return u.equals("1") || u.equals("ON") || u.equals("TRUE") || u.equals("YES");
    }

    private static double clamp(double x, double lo, double hi) {
        if (!Double.isFinite(x)) throw new NumberFormatException();
        return Math.max(lo, Math.min(hi, x));
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.1f", d);
    }

    public String get(String k) {
        return v.getOrDefault(k, DEFAULTS.get(k));
    }

    public boolean on(String k) {
        return "1".equals(get(k));
    }

    public long hz(String k) {
        return Long.parseLong(get(k));
    }

    public double num(String k) {
        return Double.parseDouble(get(k));
    }

    public String name() {
        return get("NAME");
    }

    /** one-line summary for chat */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (on("CW")) sb.append("CW ").append(mhz(hz("CWHZ"))).append("  ");
        if (on("XP")) sb.append("XP ").append(mhz(hz("XPUP"))).append(on("XPINV") ? " ->(inv) " : " -> ").append(mhz(hz("XPDOWN"))).append("  ");
        if (on("RTTY")) sb.append("RTTY ").append(mhz(hz("RTTYUP"))).append(" -> ").append(mhz(hz("RTTYDOWN")))
                .append(on("IONO") ? " IONO" : "").append(on("BBS") ? " BBS" : "").append("  ");
        if (on("SSTV")) sb.append("SSTV ").append(mhz(hz("SSTVHZ"))).append("  ");
        if (!get("ANT").equals("OMNI")) sb.append("ANT ").append(get("ANT")).append("  ");
        if (on("TV") || on("TVCAM")) sb.append("TV ").append(on("TV") ? mhz(hz("TVUP")) + " -> " : "CAM -> ").append(mhz(hz("TVDOWN"))).append("  ");
        if (on("ISL")) sb.append("ISL");
        return sb.toString().trim();
    }

    static String mhz(long hz) {
        return String.format(Locale.ROOT, "%.3f", hz / 1e6);
    }
}
