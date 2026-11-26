package com.sparkgap.pgtelegraph.sat;

import com.sparkgap.pgtelegraph.block.BeaconSequencer;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.Ionosphere;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import com.sparkgap.pgtelegraph.radio.Reception;
import com.sparkgap.pgtelegraph.tape.Ita2;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An amateur satellite in orbit, doing whatever its {@link SatConfig} says: CW beacon, linear
 * transponder, RTTY (ionosonde reports and space-weather alerts, mailbox, telecommand), SSTV
 * pictures of the Earth, and an inter-satellite link. It lives on solar power: in the Earth's
 * shadow it runs from its battery, and a flat battery silences it until the sun has charged it to
 * 20 %. Air drag lowers low orbits until the satellite burns up on re-entry below 350 km.
 */
public final class Satellite {
    public static final double RX_THRESHOLD_DBM = -128;
    public static final int MAX_RELAYED = 4, MAX_MAIL = 16;
    public static final long RTTY_BW = 1500;
    public static final int SSTV_TICKS_PER_LINE = 4;
    /** spacecraft bus by rocket: small, medium, heavy (geostationary) */
    public static final double[] PANEL_W = {4, 15, 200}, BATTERY_WH = {20, 80, 1000}, MAX_W = {5, 20, 100};
    public static final double HOUSEKEEPING_W = 0.3, REENTRY_KM = 350;
    static final BlockPos SKY = new BlockPos(0, 30_000, 0);
    private static final int K_CW = 0, K_RTTY = 1, K_XP = 2, K_SSTV = 6, K_ISL = 7, K_TV = 11, K_APT = 12, K_LAST = 13;
    /** TV: a ground station is heard on the uplink only above this (a picture needs much more than CW) */
    public static final double TV_UPLINK_DBM = -105;
    private long tvCamTick = -1000;

    public final int id;
    public final int bus;
    public Orbit.Elements orbit;
    public final SatConfig cfg;
    public String name;
    private final List<String> mail = new ArrayList<>();
    private final ArrayDeque<String> acks = new ArrayDeque<>();
    double batteryWh;
    boolean flat;
    // transient
    private final BeaconSequencer beacon = new BeaconSequencer();
    private final Ita2.Sender rttyOut = new Ita2.Sender();
    private final Ita2.Receiver rttyIn = new Ita2.Receiver();
    private final StringBuilder line = new StringBuilder();
    private int cwGap, rttyGap, sstvGap = 100;
    /** uplink FSK demodulator: the mark tone's frequency as tracked */
    private double afcHi;
    private boolean afcOn;
    private long sstvStart = -1;
    private boolean wasFlare;
    private int wasKp;
    /** downlink frequencies relayed this tick (for the inter-satellite link) */
    final List<Long> relayed = new ArrayList<>();
    private double txW;
    boolean dirty;

    public Satellite(int id, int bus, SatConfig cfg, Orbit.Elements orbit) {
        this.id = id;
        this.bus = Math.max(0, Math.min(2, bus));
        this.cfg = cfg;
        this.name = cfg.name();
        this.orbit = orbit;
        this.batteryWh = BATTERY_WH[this.bus];
    }

    public long beaconHz() {
        if (cfg.on("CW")) return cfg.hz("CWHZ");
        if (cfg.on("RTTY")) return cfg.hz("RTTYDOWN");
        if (cfg.on("SSTV")) return cfg.hz("SSTVHZ");
        return cfg.hz("XPDOWN");
    }

    public String summary() {
        return cfg.summary();
    }

    public List<String> mail() {
        return mail;
    }

    public double batteryPct() {
        return 100 * batteryWh / BATTERY_WH[bus];
    }

    BlockPos key(int slot) {
        return new BlockPos(29_000_000 + id, -1_000_000 - slot, 0);
    }

    private double watts() {
        return Math.min(cfg.num("POWER"), MAX_W[bus]);
    }

    /** frame start tick and current line of the SSTV picture being sent, or -1 */
    public long sstvFrame() {
        return sstvStart;
    }

    /** a weather satellite sending APT pictures */
    public boolean apt() {
        return cfg.on("APT");
    }

    public int sstvLine(long now) {
        if (sstvStart < 0) return -1;
        int l = (int) ((now - sstvStart) / SSTV_TICKS_PER_LINE);
        return l < com.sparkgap.pgtelegraph.media.Pic.H ? l : -1;
    }

    String fill(Level level, String text) {
        Ionosphere io = Ionosphere.get(level);
        Orbit.Look sub = Orbit.look(orbit, BlockPos.ZERO, level.getGameTime());
        return text.replace("{NAME}", name)
                .replace("{LAT}", String.valueOf(Math.round(sub.subLat())))
                .replace("{LON}", String.valueOf(Math.round(sub.subLon())))
                .replace("{SFI}", String.valueOf(Math.round(io.sfi())))
                .replace("{KP}", String.valueOf(io.kp()))
                .replace("{MUF}", String.format(Locale.ROOT, "%.1f", io.muf(level)))
                .replace("{FOF2}", String.format(Locale.ROOT, "%.1f", io.foF2(level)))
                .replace("{FLARE}", io.flare(level) ? "FLARE" : "QUIET")
                .replace("{BAT}", String.valueOf(Math.round(batteryPct())));
    }

    /** one tick: power, beacon, RTTY, SSTV and transponder */
    void tick(Level level) {
        long t = level.getGameTime();
        relayed.clear();
        lastTick = t;
        // ---- power budget (orbital seconds per tick)
        double dt = Orbit.secondsPerTick() / 3600;
        boolean sun = !Orbit.eclipsed(orbit, level, t);
        batteryWh = Math.min(BATTERY_WH[bus], batteryWh + ((sun ? PANEL_W[bus] : 0) - HOUSEKEEPING_W - txW) * dt);
        txW = 0;
        if (batteryWh <= 0) {
            batteryWh = 0;
            flat = true;
        } else if (flat && batteryWh > 0.2 * BATTERY_WH[bus]) flat = false;
        if (flat) {
            silence(level);
            return;
        }

        List<RadioWorld.Emission> ground = new ArrayList<>();
        for (RadioWorld.Emission e : RadioWorld.on(level)) if (e.sat() == null && e.spreadHz() <= 0) ground.add(e);

        // ---- space-weather alerts (with the ionosonde on)
        if (cfg.on("IONO")) {
            Ionosphere io = Ionosphere.get(level);
            boolean fl = io.flare(level);
            if (fl && !wasFlare) acks.add(fill(level, "ALERT X-RAY FLARE HF BLACKOUT MUF {MUF}"));
            if (io.kp() >= 5 && wasKp < 5) acks.add("ALERT GEOMAGNETIC STORM KP " + io.kp());
            if (io.kp() < 5 && wasKp >= 5) acks.add("STORM OVER KP " + io.kp());
            wasFlare = fl;
            wasKp = io.kp();
        }

        // ---- CW beacon
        boolean mark = false;
        if (cfg.on("CW")) {
            if (beacon.busy()) mark = beacon.tick();
            else if (cwGap-- <= 0) {
                beacon.send(fill(level, cfg.get("CWTEXT")), (int) cfg.num("CWWPM"));
                cwGap = (int) cfg.num("CWGAP") * 20;
                mark = beacon.tick();
            }
        }
        emitOr(level, K_CW, mark, cfg.hz("CWHZ"), t);

        // ---- RTTY
        boolean rttyMark = false;
        if (cfg.on("RTTY")) {
            // the uplink receiver: FSK with AFC - the higher of the two tones is mark, 170 Hz below is space
            // (the carrier stays on through both); an on-off keyed carrier (CW mode) reads as mark / space too
            Double fUp = null;
            long upHz = cfg.hz("RTTYUP");
            double jamDbm = Double.NEGATIVE_INFINITY, sigDbm = Double.NEGATIVE_INFINITY;
            for (RadioWorld.Emission e : RadioWorld.on(level)) { // jammers covering the uplink: noise in its 1.5 kHz
                if (e.sat() != null || e.spreadHz() <= 0 || Math.abs(e.freqHz() - upHz) > e.spreadHz() / 2) continue;
                Orbit.Look lk = Orbit.look(orbit, e.antenna(), t);
                if (!lk.visible()) continue;
                double j = uplinkDbm(e, lk) - 10 * Math.log10(e.spreadHz() / RTTY_BW);
                jamDbm = 10 * Math.log10(RadioMath.fromDb(jamDbm) + RadioMath.fromDb(j));
            }
            for (RadioWorld.Emission e : ground) {
                if (Math.abs(e.freqHz() - upHz) > RTTY_BW + 20_000) continue;
                Orbit.Look lk = Orbit.look(orbit, e.antenna(), t);
                if (!lk.visible()) continue;
                double f = Reception.dopplerCorrected(level, e.antenna()) ? e.freqHz() : e.freqHz() + lk.dopplerHz(e.freqHz());
                if (Math.abs(f - upHz) > RTTY_BW) continue;
                double u = uplinkDbm(e, lk);
                if (u >= RX_THRESHOLD_DBM && u >= jamDbm + 6) { // heard, and 6 dB clear of any jamming
                    fUp = f;
                    sigDbm = u;
                    break;
                }
            }
            boolean up;
            if (fUp == null) {
                up = false;
                afcOn = false;
            } else {
                if (!afcOn) afcHi = fUp;
                afcOn = true;
                afcHi = Math.max(fUp, afcHi - 5); // follows Doppler drift (a few Hz a tick), jumps up to a new mark at once
                up = fUp >= afcHi - 85;
            }
            char c = rttyIn.tick(up);
            if (c == '\n') command(level);
            else if (c != 0 && line.length() < 120) line.append(c);
            if (!rttyOut.busy()) {
                if (!acks.isEmpty()) {
                    StringBuilder sb = new StringBuilder("\n");
                    while (!acks.isEmpty()) sb.append(acks.poll()).append('\n');
                    rttyOut.send(sb.toString());
                } else if (rttyGap-- <= 0) {
                    rttyOut.send(report(level));
                    rttyGap = (int) cfg.num("RTTYGAP") * 20;
                }
            }
            rttyMark = rttyOut.tick();
        } else acks.clear();
        emitOr(level, K_RTTY, rttyMark, cfg.hz("RTTYDOWN"), t);

        // ---- SSTV: a carrier while a frame goes down, 4 ticks a line
        boolean sstvOn = false;
        if (cfg.on("SSTV")) {
            if (sstvLine(t) >= 0) sstvOn = true;
            else {
                if (sstvStart >= 0) {
                    sstvStart = -1;
                    sstvGap = (int) cfg.num("SSTVGAP") * 20;
                }
                if (sstvGap-- <= 0) {
                    sstvStart = t;
                    sstvOn = true;
                }
            }
        } else sstvStart = -1;
        emitOr(level, K_SSTV, sstvOn, cfg.hz("SSTVHZ"), t);
        // ---- APT weather pictures: sent all the time, a line every half second
        emitOr(level, K_APT, cfg.on("APT"), cfg.hz("APTHZ"), t);

        // ---- linear transponder
        int slot = K_XP;
        if (cfg.on("XP")) {
            long lo = cfg.hz("XPUP"), width = cfg.hz("XPWIDTH"), down = cfg.hz("XPDOWN");
            boolean inv = cfg.on("XPINV");
            for (RadioWorld.Emission e : ground) {
                if (slot >= K_XP + MAX_RELAYED) break;
                if (e.freqHz() < lo - 20_000 || e.freqHz() > lo + width + 20_000) continue;
                Orbit.Look lk = Orbit.look(orbit, e.antenna(), t);
                if (!lk.visible()) continue;
                double f = Reception.dopplerCorrected(level, e.antenna()) ? e.freqHz() : e.freqHz() + lk.dopplerHz(e.freqHz());
                double off = f - lo;
                if (off < 0 || off > width) continue;
                if (uplinkDbm(e, lk) < RX_THRESHOLD_DBM) continue;
                long out = down + Math.round(inv ? width - off : off);
                RadioWorld.emit(level, emission(key(slot++), out, t));
                relayed.add(out);
            }
        }
        for (; slot < K_XP + MAX_RELAYED; slot++) RadioWorld.silence1(level, key(slot));

        // ---- TV: relay a ground TV station from the uplink, or (TVCAM) the Earth below
        boolean tvOn = false;
        BlockPos tvKey = key(K_TV);
        if (cfg.on("TV")) {
            long up = cfg.hz("TVUP");
            for (RadioWorld.Emission e : ground) {
                if (Math.abs(e.freqHz() - up) > 1_000_000 || RadioWorld.modulation(level, e.source()) != RadioWorld.MOD_TV) continue;
                Orbit.Look lk = Orbit.look(orbit, e.antenna(), t);
                if (!lk.visible() || uplinkDbm(e, lk) < TV_UPLINK_DBM) continue;
                BlockPos cam = com.sparkgap.pgtelegraph.media.TvTransmitterBlockEntity.TvAir.camera(level, e.source());
                if (cam == null) continue;
                com.sparkgap.pgtelegraph.media.TvTransmitterBlockEntity.TvAir.onAir(level, tvKey, cam);
                tvOn = true;
                break;
            }
        }
        if (!tvOn && cfg.on("TVCAM")) { // the satellite's own camera: the Earth below, a frame every two seconds
            if (t - tvCamTick >= 40) {
                tvCamTick = t;
                int w = 160, h = 128;
                int[] rgb = new int[w * h];
                for (int y = 0; y < h; y++) {
                    byte[] row = SstvImage.row(this, level, t, y * 256 / h);
                    for (int x = 0; x < w; x++) rgb[y * w + x] = com.sparkgap.pgtelegraph.media.Pic.rgb(row, x * 2);
                }
                com.sparkgap.pgtelegraph.media.TvFrames.put(level, tvKey, com.sparkgap.pgtelegraph.media.TvFrames.jpeg(rgb, w, h, 0.8f));
            }
            com.sparkgap.pgtelegraph.media.TvTransmitterBlockEntity.TvAir.onAir(level, tvKey, tvKey);
            tvOn = true;
        }
        if (tvOn) RadioWorld.setModulation(level, tvKey, RadioWorld.MOD_TV);
        emitOr(level, K_TV, tvOn, cfg.hz("TVDOWN"), t);
    }

    /** inter-satellite link: re-send what another satellite relayed this tick */
    void islRelay(Level level, List<Long> freqs, long t) {
        int k = 0;
        for (long f : freqs) {
            if (k >= MAX_RELAYED) break;
            RadioWorld.emit(level, emission(key(K_ISL + k++), f, t));
        }
        for (; k < MAX_RELAYED; k++) RadioWorld.silence1(level, key(K_ISL + k));
    }

    private double uplinkDbm(RadioWorld.Emission e, Orbit.Look lk) {
        double pattern = AntennaLink.patternLoss(e.aim(), e.beamDeg(), e.floorDb(), lk.dir());
        return e.eirpDbm() - pattern - fspl(lk.rangeKm(), e.freqHz() / 1e6) + antennaGainDb(e.antenna(), lastTick);
    }

    /** 3 dB beam width of the spot antenna (degrees) */
    public static final double SPOT_BEAM_DEG = 3;
    private long lastTick;

    /**
     * The satellite's own antenna gain (dBi) toward a point on the ground - the same both ways (transmit and receive):
     * OMNI 0 dBi everywhere (a CubeSat's whips); EARTH a horn that just fills the Earth's disk as seen from the orbit
     * (about 20 dBi from GEO, about 3 dBi from a low orbit, 3 dB down at the Earth's edge); SPOT a 3 degree dish beam
     * (about 35 dBi) on SPOTLAT / SPOTLON (the launch site unless set) - loud there, little a few hundred km away.
     */
    public double antennaGainDb(net.minecraft.core.BlockPos ground, long t) {
        String ant = cfg.get("ANT");
        if (ant.equals("OMNI")) return 0;
        double[] s = orbit.ecefAt(t);
        double r = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
        double[] ll = Orbit.latLon(ground);
        double[] g = Orbit.ecef(ll[0], ll[1], 0);
        double[] toG = {g[0] - s[0], g[1] - s[1], g[2] - s[2]};
        if (ant.equals("EARTH")) {
            double half = Math.asin(Math.min(1, Orbit.RE / r));
            double g0 = 10 * Math.log10(0.55 * 2 / (1 - Math.cos(half))); // 4 pi / the cone's solid angle, 55 % efficient
            double off = Math.toDegrees(angle(new double[]{-s[0], -s[1], -s[2]}, toG)), bw = 2 * Math.toDegrees(half);
            return g0 - Math.min(20, 12 * Math.pow(off / bw, 2));
        }
        double lat, lon;
        try {
            lat = Math.toRadians(Double.parseDouble(cfg.get("SPOTLAT")));
            lon = Math.toRadians(Double.parseDouble(cfg.get("SPOTLON")));
        } catch (NumberFormatException ex) {
            lat = Math.toRadians(Orbit.LAT0);
            lon = Math.toRadians(Orbit.LON0);
        }
        double[] tg = Orbit.ecef(lat, lon, 0);
        double half = Math.toRadians(SPOT_BEAM_DEG / 2);
        double g0 = 10 * Math.log10(0.55 * 2 / (1 - Math.cos(half)));
        double off = Math.toDegrees(angle(new double[]{tg[0] - s[0], tg[1] - s[1], tg[2] - s[2]}, toG));
        return g0 - Math.min(35, 12 * Math.pow(off / SPOT_BEAM_DEG, 2));
    }

    private static double angle(double[] a, double[] b) {
        double d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        double la = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]), lb = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
        return Math.acos(Math.max(-1, Math.min(1, d / (la * lb))));
    }

    private void emitOr(Level level, int k, boolean on, long hz, long t) {
        if (on) RadioWorld.emit(level, emission(key(k), hz, t));
        else RadioWorld.silence1(level, key(k));
    }

    private String report(Level level) {
        StringBuilder sb = new StringBuilder("\nQST DE ").append(name).append(" BAT ").append(Math.round(batteryPct())).append('\n');
        if (cfg.on("IONO")) sb.append(fill(level, "IONO SFI {SFI} KP {KP} FOF2 {FOF2} MUF {MUF} MHZ {FLARE}")).append('\n');
        if (cfg.on("BBS")) {
            sb.append("BBS ").append(mail.size()).append(" MSG\n");
            for (String m : mail) sb.append(m).append('\n');
        }
        return sb.toString();
    }

    private void command(Level level) {
        String l = line.toString().trim();
        line.setLength(0);
        if (l.isEmpty()) return;
        com.sparkgap.pgtelegraph.PgTelegraph.LOGGER.info("[pgtelegraph] satellite {} uplink: {}", name, l);
        String[] p = l.split("\\s+", 3);
        if (p[0].equals("SET") && p.length == 3) {
            String v = cfg.set(p[1], p[2]);
            acks.add(v == null ? "NAK " + p[1] : "ACK " + p[1].toUpperCase(Locale.ROOT) + " " + v);
            if (v != null && p[1].equalsIgnoreCase("NAME")) name = cfg.name();
            dirty = true;
        } else if (p[0].equals("CLEAR") && p.length == 1) {
            mail.clear();
            acks.add("ACK CLEAR");
            dirty = true;
        } else if (cfg.on("BBS")) {
            mail.add(l.length() > 100 ? l.substring(0, 100) : l);
            while (mail.size() > MAX_MAIL) mail.remove(0);
            acks.add("ACK MSG " + mail.size());
            dirty = true;
        }
    }

    void silence(Level level) {
        for (int s = 0; s < K_LAST; s++) RadioWorld.silence1(level, key(s));
    }

    private RadioWorld.Emission emission(BlockPos key, long hz, long t) {
        double w = watts();
        txW += w;
        return new RadioWorld.Emission(key, SKY, hz, RadioMath.dbm(w), Orbit.RE, t, null, 360, 0, 0, this);
    }

    public static double fspl(double km, double fMHz) {
        return 20 * Math.log10(Math.max(km, 0.001)) + 20 * Math.log10(fMHz) + 32.44;
    }

    /** air drag: km lost per tick at an altitude (500 km lasts ~20 h of play, 800 km practically forever) */
    static double decayKmPerTick(double alt) {
        if (alt > 2000) return 0;
        return 6.6e-4 * Math.exp(-(alt - REENTRY_KM) / 50);
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", id);
        t.putInt("Bus", bus);
        t.putString("Config", cfg.serialize());
        t.putDouble("Alt", orbit.altKm());
        t.putDouble("Incl", orbit.incl());
        t.putDouble("Raan", orbit.raan());
        t.putDouble("U0", orbit.u0());
        t.putLong("Epoch", orbit.epoch());
        t.putDouble("Battery", batteryWh);
        t.putBoolean("Flat", flat);
        ListTag m = new ListTag();
        for (String s : mail) m.add(StringTag.valueOf(s));
        t.put("Mail", m);
        return t;
    }

    static Satellite load(CompoundTag t) {
        SatConfig cfg = SatConfig.parse(t.getString("Config"));
        if (!t.contains("Config") && t.contains("Name")) cfg.set("NAME", t.getString("Name"));
        Satellite s = new Satellite(t.getInt("Id"), t.getInt("Bus"), cfg,
                new Orbit.Elements(t.getDouble("Alt"), t.getDouble("Incl"), t.getDouble("Raan"), t.getDouble("U0"), t.getLong("Epoch")));
        if (t.contains("Battery")) s.batteryWh = t.getDouble("Battery");
        s.flat = t.getBoolean("Flat");
        for (Tag x : t.getList("Mail", Tag.TAG_STRING)) s.mail.add(x.getAsString());
        return s;
    }
}
