package com.sparkgap.pgtelegraph.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * What one receiving antenna hears this tick: the strongest signal in the 500 Hz CW filter,
 * the loudest signal within ±1.5 kHz (for the speaker), and the noise plus interference.
 * Shared by the receiver block and the handheld radio.
 */
public final class Reception {
    public static final double RX_NOISE_DBM = -140;

    /** strongest in-filter signal (dBm, -inf if none), loudest audible + its offset, noise+interference (dBm), bare noise (dBm) */
    public record Result(double best, double loud, int loudDf, double noiseDbm, double floorDbm, RadioWorld.Emission bestEmission) {
        public boolean hasSignal() {
            return best > Double.NEGATIVE_INFINITY;
        }
    }

    /** per-transmitter terrain loss, refreshed every 2 s; keep one per listener */
    public static final class TerrainCache {
        private final Map<BlockPos, double[]> map = new HashMap<>();

        private final Map<BlockPos, double[]> buried = new HashMap<>();

        /** loss through the earth or water above the transmitting antenna, refreshed every 5 s */
        double burial(Level level, RadioWorld.Emission e, long t) {
            double[] c = buried.get(e.source());
            if (c == null || t - c[1] > RadioLoad.every(100)) {
                c = new double[]{Burial.lossDb(level, e.antenna(), e.freqHz()), t};
                buried.put(e.source(), c);
                if (buried.size() > 256) buried.clear();
            }
            return c[0];
        }

        private final Map<BlockPos, double[]> sea = new HashMap<>();

        /** how much of the ground-wave path runs over water, refreshed every 10 s */
        double sea(Level level, RadioWorld.Emission e, BlockPos rx, long t) {
            double[] c = sea.get(e.source());
            if (c == null || t - c[1] > RadioLoad.every(200)) {
                c = new double[]{Terrain.seaFraction(level, e.antenna(), rx), t};
                sea.put(e.source(), c);
                if (sea.size() > 256) sea.clear();
            }
            return c[0];
        }

        double get(Level level, RadioWorld.Emission e, BlockPos rx, double f, long t) {
            double[] c = map.get(e.source());
            if (c == null || t - c[1] > RadioLoad.every(40) || c[2] != f) {
                c = new double[]{Terrain.obstructionDb(level, e.antenna(), rx, f), t, f};
                map.put(e.source(), c);
                if (map.size() > 256) map.clear();
            }
            return c[0];
        }
    }

    /**
     * @param rl        the receiving antenna (feed = its position)
     * @param extraDb   extra loss before the receiver (amplifier bypass etc.)
     * @param ignore    an emission source not to hear (one's own transmitter), or null
     * @param seed      salt for this listener's fading
     */
    public static Result listen(Level level, AntennaLink rl, long freqHz, double extraDb, BlockPos ignore, long seed,
                                TerrainCache cache, double rxNoiseDbm, int filterHz) {
        double f = freqHz / 1e6;
        boolean day = level.isDay(), rain = level.isRaining(), thunder = level.isThundering();
        double sysLoss = rl.systemLossDb(f) + extraDb;
        Preamp lna = Preamp.of(level, rl, f);
        if (lna != null) {
            sysLoss -= lna.gainDb();
            rxNoiseDbm = lna.rxNoise(rxNoiseDbm);
        }
        double ext = RadioMath.noiseDbm(f, day, rain, thunder) - sysLoss;
        // noise scales with the filter: the -140 dBm reference is for 500 Hz
        double bwDb = 10 * Math.log10(filterHz / (double) RadioMath.FILTER_HZ);
        double noiseMw = RadioMath.fromDb(ext + bwDb) + RadioMath.fromDb(rxNoiseDbm + bwDb);

        double best = Double.NEGATIVE_INFINITY, loud = Double.NEGATIVE_INFINITY;
        RadioWorld.Emission bestE = null;
        int loudDf = 0;
        double interferenceMw = 0;
        long t = level.getGameTime();
        double rxBurial = Burial.lossDb(level, rl.feed(), freqHz);
        for (RadioWorld.Emission e : RadioWorld.on(level)) {
            if (e.source().equals(ignore)) continue;
            long df = observedHz(level, e, rl.feed(), t) - freqHz;
            double spreadDb = 0;
            if (e.spreadHz() > 0) {
                // broadband emitter (spark): its power is smeared over spreadHz, heard as a rasp
                if (Math.abs(df) > e.spreadHz() / 2 + filterHz / 2.0) continue;
                spreadDb = 10 * Math.log10(Math.max(1, e.spreadHz() / filterHz));
                df = 0;
            } else if (Math.abs(df) > Math.max(RadioMath.AUDIBLE_HZ, filterHz / 2)) continue;
            double prx = received(level, e, rl, f, seed, cache, t, day, rain, thunder) - sysLoss - spreadDb - rxBurial;
            if (e.spreadHz() > 0 && RadioWorld.modulation(level, e.source()) == RadioWorld.MOD_NOISE) {
                noiseMw += RadioMath.fromDb(prx); // a jammer: its share of noise in the filter
                continue;
            }
            if (Math.abs(df) <= filterHz / 2) {
                if (prx > best) {
                    if (best > Double.NEGATIVE_INFINITY) interferenceMw += RadioMath.fromDb(best);
                    best = prx;
                    bestE = e;
                } else interferenceMw += RadioMath.fromDb(prx);
            }
            if (prx > loud) {
                loud = prx;
                loudDf = (int) df;
            }
        }
        return new Result(best, loud, loudDf, 10 * Math.log10(noiseMw + interferenceMw), 10 * Math.log10(noiseMw), bestE);
    }

    /**
     * One signal in a receiver's channel: total median power at the receiver input (dBm), offset
     * from the dial (Hz), the share of that power that came by sky wave (0 = ground wave only,
     * steady; 1 = sky wave only, Rayleigh fading; between = both, Rician), and the sky-wave channel
     * after the Watterson model (ITU-R F.1487): time spread between the modes (ms) and Doppler
     * spread of their fading (Hz).
     */
    public record Heard(RadioWorld.Emission e, double dbm, int df, float skyShare, float delayMs, float dopplerHz, long fadeSeed,
                        float antBwHz) {
        public boolean sky() {
            return skyShare > 0.5f;
        }
    }

    /**
     * Everything inside a receiver's channel, signal by signal, for receivers that rebuild the
     * actual waveform (the broadcast radio): every emission within {@code spanHz} of the dial with
     * its received median power after path loss and the receiving system's losses, strongest first
     * (at most {@code max}). Noise comes back as densities (dBm per Hz): external (atmospheric,
     * galactic, man-made; rain and thunder included) and the receiver's own.
     */
    public record Channel(java.util.List<Heard> signals, double extDbmPerHz, double rxDbmPerHz) {
        public double noiseDbmPerHz() {
            return 10 * Math.log10(RadioMath.fromDb(extDbmPerHz) + RadioMath.fromDb(rxDbmPerHz));
        }
    }

    public static Channel channel(Level level, AntennaLink rl, long freqHz, BlockPos ignore, long seed,
                                  TerrainCache cache, double rxNoiseDbm, int spanHz, int max) {
        return channel(level, rl, freqHz, 0, ignore, seed, cache, rxNoiseDbm, spanHz, max);
    }

    /** @param extraDb loss in front of the receiver (amplifier bypass, attenuator): signals and outside noise, not its own */
    public static Channel channel(Level level, AntennaLink rl, long freqHz, double extraDb, BlockPos ignore, long seed,
                                  TerrainCache cache, double rxNoiseDbm, int spanHz, int max) {
        double f = freqHz / 1e6;
        boolean day = level.isDay(), rain = level.isRaining(), thunder = level.isThundering();
        double sysLoss = rl.systemLossDb(f) + extraDb;
        Preamp lna = Preamp.of(level, rl, f);
        if (lna != null) {
            sysLoss -= lna.gainDb();
            rxNoiseDbm = lna.rxNoise(rxNoiseDbm);
        }
        double per500 = 10 * Math.log10(RadioMath.FILTER_HZ);
        double ext = RadioMath.noiseDbm(f, day, rain, thunder) - sysLoss - per500;
        double rxN = rxNoiseDbm - per500;
        double floor = 10 * Math.log10(RadioMath.fromDb(ext) + RadioMath.fromDb(rxN)) + 10 * Math.log10(spanHz);
        long t = level.getGameTime();
        double rxBurial = Burial.lossDb(level, rl.feed(), freqHz);
        Ionosphere io = Ionosphere.get(level);
        java.util.ArrayList<Heard> out = new java.util.ArrayList<>();
        double jamMwHz = 0;
        for (RadioWorld.Emission e : RadioWorld.on(level)) {
            if (e.source().equals(ignore)) continue;
            long df = observedHz(level, e, rl.feed(), t) - freqHz;
            double spreadDb = 0;
            if (e.spreadHz() > 0) {
                if (Math.abs(df) > e.spreadHz() / 2 + spanHz) continue;
                spreadDb = 10 * Math.log10(Math.max(1, e.spreadHz() / (2.0 * spanHz)));
                df = 0;
            } else if (Math.abs(df) > spanHz) continue;
            float skyShare = 0, delay = 0, doppler = 0;
            double prx;
            if (e.sat() != null) {
                prx = received(level, e, rl, f, seed, cache, t, day, rain, thunder);
            } else {
                double[] w = paths(level, e, rl, f, cache, t, rain, thunder);
                Vec3 toTx = e.antenna().getCenter().subtract(rl.feed().getCenter());
                double gains = e.eirpDbm() - AntennaLink.patternLoss(e.aim(), e.beamDeg(), e.floorDb(), toTx.scale(-1))
                        + rl.gainToward(f, toTx) + rl.efficiencyDb() - cache.burial(level, e, t);
                double gMw = w[0] < Double.POSITIVE_INFINITY ? RadioMath.fromDb(gains - w[0]) : 0;
                double sMw = w[1] < Double.POSITIVE_INFINITY ? RadioMath.fromDb(gains - w[1]) : 0;
                double eme = f >= EME_MIN_MHZ && !RadioLoad.simple() ? RadioMath.fromDb(moonBounce(level, e, rl, f) + rl.efficiencyDb()) : 0;
                double tot = gMw + sMw + eme;
                if (tot <= 0) continue;
                prx = 10 * Math.log10(tot);
                skyShare = (float) (sMw / tot);
                if (sMw > 0) {
                    // Watterson: two modes (e.g. 1F/2F or the o- and x-rays) a little apart in time;
                    // short paths spread more, a disturbed ionosphere fades faster
                    double dKm = RadioMath.effectiveKm(Math.sqrt(e.antenna().distSqr(rl.feed())));
                    delay = (float) Math.max(0.5, Math.min(2.5, 2.5 - dKm / 2500));
                    doppler = (float) Fading.dopplerHz(io, level);
                }
            }
            prx -= sysLoss + spreadDb + rxBurial;
            if (e.spreadHz() > 0 && RadioWorld.modulation(level, e.source()) == RadioWorld.MOD_NOISE) {
                jamMwHz += RadioMath.fromDb(prx) / (2.0 * spanHz); // a jammer: noise over the span, not a station
                continue;
            }
            if (prx < floor - 30) continue; // far below the noise: inaudible
            out.add(new Heard(e, prx, (int) df, skyShare, delay, doppler, e.source().asLong() * 31 + seed,
                    (float) RadioWorld.antennaBandwidth(level, e.source())));
        }
        out.sort((a, b) -> Double.compare(b.dbm(), a.dbm()));
        if (jamMwHz > 0) ext = 10 * Math.log10(RadioMath.fromDb(ext) + jamMwHz);
        return new Channel(out.size() > max ? java.util.List.copyOf(out.subList(0, max)) : out, ext, rxN);
    }

    /**
     * Power (dBm) one emission delivers at the antenna terminals of {@code rl}, before the
     * receiving system's own losses: path loss, both antenna patterns, fading, and the ground or
     * water the transmitting antenna is buried under.
     */
    public static double received(Level level, RadioWorld.Emission e, AntennaLink rl, double f, long seed,
                                  TerrainCache cache, long t, boolean day, boolean rain, boolean thunder) {
        if (e.sat() != null) {
            var look = com.sparkgap.pgtelegraph.sat.Orbit.look(e.sat().orbit, rl.feed(), t);
            if (!look.visible()) return Double.NEGATIVE_INFINITY;
            // a little extra loss low on the horizon (atmosphere, ground clutter)
            double low = look.elDeg() < 5 ? (5 - look.elDeg()) * 2 : 0;
            return e.eirpDbm() + e.sat().antennaGainDb(rl.feed(), t) - com.sparkgap.pgtelegraph.sat.Satellite.fspl(look.rangeKm(), f) - low
                    + rl.gainToward(f, look.dir()) + rl.efficiencyDb();
        }
        double[] w = paths(level, e, rl, f, cache, t, rain, thunder);
        // ground and sky wave add up; the sky share fades exactly as the simulated loudspeakers play it
        double gMw = w[0] < Double.POSITIVE_INFINITY ? RadioMath.fromDb(-w[0]) : 0;
        double sMw = w[1] < Double.POSITIVE_INFINITY ? RadioMath.fromDb(-w[1]) : 0;
        double pl;
        if (gMw + sMw <= 0) pl = Double.POSITIVE_INFINITY;
        else {
            double fade = Fading.carrierGain(e.source().asLong() * 31 + seed, t / 20.0,
                    Fading.dopplerHz(Ionosphere.get(level), level), sMw / (gMw + sMw));
            pl = -10 * Math.log10((gMw + sMw) * Math.max(1e-6, fade));
        }
        Vec3 toTx = e.antenna().getCenter().subtract(rl.feed().getCenter());
        double txPattern = AntennaLink.patternLoss(e.aim(), e.beamDeg(), e.floorDb(), toTx.scale(-1));
        double direct = e.eirpDbm() - txPattern - pl + rl.gainToward(f, toTx);
        double eme = RadioLoad.simple() ? Double.NEGATIVE_INFINITY : moonBounce(level, e, rl, f);
        return Math.max(direct, eme) + rl.efficiencyDb() - cache.burial(level, e, t);
    }

    /**
     * Path loss (dB) of the two ways a signal can come: {ground wave or line of sight, sky wave}
     * (+inf where that way is closed). Long-term medians: no fading.
     */
    static double[] paths(Level level, RadioWorld.Emission e, AntennaLink rl, double f, TerrainCache cache, long t,
                          boolean rain, boolean thunder) {
        double dKm = RadioMath.effectiveKm(Math.sqrt(e.antenna().distSqr(rl.feed())));
        if (f >= RadioMath.LOS_MHZ)
            return new double[]{RadioMath.losLoss(dKm, f, e.heightM(), rl.heightM(), rain, thunder) + cache.get(level, e, rl.feed(), f, t),
                    Double.POSITIVE_INFINITY};
        double ground = RadioMath.groundLoss(dKm, f, e.heightM(), rl.heightM(), cache.sea(level, e, rl.feed(), t));
        ground = Math.min(ground, RadioMath.waveguideLoss(dKm, f, Ionosphere.sun(level))); // VLF earth-ionosphere guide
        Ionosphere io = Ionosphere.get(level);
        double sky = RadioMath.skyLoss(dKm, f, io.foF2(level), io.absorptionDb(level, f));
        return new double[]{ground, sky};
    }

    /** true when this signal reaches {@code rl} off the ionosphere (sky wave) rather than along the ground */
    public static boolean skywave(Level level, RadioWorld.Emission e, AntennaLink rl, double f) {
        if (e.sat() != null || f >= RadioMath.LOS_MHZ) return false;
        double[] w = paths(level, e, rl, f, SHARED, level.getGameTime(), level.isRaining(), level.isThundering());
        return w[1] < w[0];
    }

    private static final TerrainCache SHARED = new TerrainCache();

    /**
     * Advancements for hearing a signal: any signal, one from 500 km or more, one off the moon,
     * one from a satellite.
     */
    public static void awardHeard(net.minecraft.server.level.ServerLevel level, Result r, AntennaLink rl,
                                  net.minecraft.server.level.ServerPlayer player, BlockPos at) {
        RadioWorld.Emission e = r.bestEmission();
        if (e == null || level.getGameTime() % 20 != 0) return;
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.add("heard");
        if (e.sat() != null) ids.add("satellite");
        else {
            if (RadioMath.effectiveKm(Math.sqrt(e.antenna().distSqr(rl.feed()))) >= 500) ids.add("dx500");
            double f = e.freqHz() / 1e6;
            Vec3 toTx = e.antenna().getCenter().subtract(rl.feed().getCenter());
            if (f >= EME_MIN_MHZ) {
                double eme = moonBounce(level, e, rl, f);
                double direct = e.eirpDbm() - AntennaLink.patternLoss(e.aim(), e.beamDeg(), e.floorDb(), toTx.scale(-1))
                        - RadioMath.losLoss(RadioMath.effectiveKm(toTx.length()), f, e.heightM(), rl.heightM(), false, false)
                        + rl.gainToward(f, toTx);
                if (eme > direct) ids.add("moonbounce");
            }
        }
        for (String id : ids) {
            if (player != null) com.sparkgap.pgtelegraph.Awards.award(player, id);
            else com.sparkgap.pgtelegraph.Awards.near(level, at, 16, id);
        }
    }

    /** carrier frequency as heard at a station: a satellite's is Doppler-shifted */
    public static long observedHz(Level level, RadioWorld.Emission e, BlockPos at, long t) {
        if (e.sat() == null || dopplerCorrected(level, at)) return e.freqHz();
        var look = com.sparkgap.pgtelegraph.sat.Orbit.look(e.sat().orbit, at, t);
        return e.freqHz() + Math.round(look.dopplerHz(e.freqHz()));
    }

    /** an antenna in satellite-tracking mode also steers the radio's frequency against Doppler */
    public static boolean dopplerCorrected(Level level, BlockPos antenna) {
        return level.isLoaded(antenna) && level.getBlockEntity(antenna) instanceof com.sparkgap.pgtelegraph.block.VhfAntennaBlockEntity v
                && v.track() == com.sparkgap.pgtelegraph.block.VhfAntennaBlockEntity.SATELLITE;
    }

    /** EME (moon bounce) needs at least this frequency: lower bands are reflected by the ionosphere first */
    public static final double EME_MIN_MHZ = 50;

    /**
     * Direction of the moon in the sky (unit vector), or null below 5 degrees of elevation. The sun
     * and moon travel east-west through the zenith; at time-of-day 0.5 (midnight) the moon is overhead.
     */
    public static Vec3 moonDir(Level level) {
        double a = level.getTimeOfDay(1f) * 2 * Math.PI;
        Vec3 moon = new Vec3(Math.sin(a), -Math.cos(a), 0);
        return moon.y < Math.sin(Math.toRadians(5)) ? null : moon;
    }

    /**
     * Earth-moon-earth path: the moon reflects about 7 % of what hits it. Path loss is scaled for the
     * game (real EME is 20 dB worse): 232 dB at 144 MHz, +20 dB per decade of frequency, the same
     * from anywhere in the world. Both antennas must point at the moon.
     */
    static double moonBounce(Level level, RadioWorld.Emission e, AntennaLink rl, double f) {
        if (f < EME_MIN_MHZ || e.spreadHz() > 0 || e.sat() != null) return Double.NEGATIVE_INFINITY;
        Vec3 moon = moonDir(level);
        if (moon == null) return Double.NEGATIVE_INFINITY;
        double loss = 232 + 20 * Math.log10(f / 144);
        double tx = AntennaLink.patternLoss(e.aim(), e.beamDeg(), e.floorDb(), moon);
        return e.eirpDbm() - tx - loss + rl.gainToward(f, moon);
    }

    /** speaker tone volume 0..1 from the loudest signal over noise */
    public static float toneVolume(Result r) {
        if (r.loud() == Double.NEGATIVE_INFINITY) return 0;
        return (float) Math.max(0, Math.min(1, (r.loud() - r.noiseDbm() + 3) / 35));
    }

    public static int toneHz(Result r, int previous) {
        return r.loud() == Double.NEGATIVE_INFINITY ? previous : Math.max(200, Math.min(2500, 700 + r.loudDf()));
    }

    public static float noiseVolume(Result r) {
        return (float) Math.max(0.04, Math.min(0.35, (r.floorDbm() + 140) / 60 * 0.35 + 0.04));
    }
}
