package com.sparkgap.pgtelegraph.sat;

import com.sparkgap.pgtelegraph.radio.RadioMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Circular orbit around a spherical, rotating Earth. The world is a patch of Earth: block (0, 0)
 * sits at 37.5 N 127 E and the usual block-to-km propagation scale maps blocks to ground distance.
 * Orbital time runs {@value #SPEED}x faster than real time (a 97-minute orbit takes 16 minutes,
 * Earth turns once in 4 hours), but velocities - and so Doppler shifts - are real.
 */
public final class Orbit {
    private Orbit() {}

    public static final double RE = 6371, MU = 398600.4418, SPEED = 6;
    public static final double LAT0 = 37.5, LON0 = 127;
    /** sidereal rotation per tick (rad) */
    static final double EARTH_RATE = 2 * Math.PI / 86164.1 * SPEED / 20;
    public static final double C_KM_S = 299792.458;

    /** orbital seconds that pass in one tick */
    public static double secondsPerTick() {
        return SPEED / 20;
    }

    public static double periodS(double altKm) {
        double a = RE + altKm;
        return 2 * Math.PI * Math.sqrt(a * a * a / MU);
    }

    /** Earth rotation angle at a game time */
    static double gst(long tick) {
        return (tick * EARTH_RATE) % (2 * Math.PI);
    }

    static double signedKm(double blocks) {
        return Math.signum(blocks) * RadioMath.effectiveKm(Math.abs(blocks));
    }

    /** geodetic latitude/longitude (radians) of a block */
    public static double[] latLon(BlockPos p) {
        double lat = LAT0 - signedKm(p.getZ()) / 111.32;
        double lon = LON0 + signedKm(p.getX()) / (111.32 * Math.cos(Math.toRadians(LAT0)));
        return new double[]{Math.toRadians(lat), Math.toRadians(lon)};
    }

    /** Earth-fixed position (km) of a ground point */
    public static double[] ecef(double lat, double lon, double altKm) {
        double r = RE + altKm;
        return new double[]{r * Math.cos(lat) * Math.cos(lon), r * Math.cos(lat) * Math.sin(lon), r * Math.sin(lat)};
    }

    public record Elements(double altKm, double incl, double raan, double u0, long epoch) {
        /** inertial position (km) at a game tick */
        double[] eci(double tick) {
            double a = RE + altKm;
            double n = Math.sqrt(MU / (a * a * a)) * secondsPerTick();
            double u = u0 + n * (tick - epoch);
            double cu = Math.cos(u), su = Math.sin(u), co = Math.cos(raan), so = Math.sin(raan), ci = Math.cos(incl), si = Math.sin(incl);
            return new double[]{a * (co * cu - so * su * ci), a * (so * cu + co * su * ci), a * su * si};
        }

        /** Earth-fixed position (km) at a game tick */
        public double[] ecefAt(double tick) {
            double[] r = eci(tick);
            double g = (tick * EARTH_RATE) % (2 * Math.PI);
            double c = Math.cos(g), s = Math.sin(g);
            return new double[]{c * r[0] + s * r[1], -s * r[0] + c * r[1], r[2]};
        }

        public double periodS() {
            return Orbit.periodS(altKm);
        }

        /** argument of latitude at a tick */
        public double uAt(double tick) {
            double a = RE + altKm;
            return u0 + Math.sqrt(MU / (a * a * a)) * secondsPerTick() * (tick - epoch);
        }

        /** same orbit plane and position, new altitude (after drag) */
        public Elements withAlt(double alt, long now) {
            return new Elements(alt, incl, raan, uAt(now) % (2 * Math.PI), now);
        }

        public boolean geostationary() {
            return altKm > 30000;
        }

        public double inclDeg() {
            return Math.toDegrees(incl);
        }
    }

    /**
     * Elements of a rocket launched from {@code site} at {@code tick} toward compass azimuth
     * {@code azDeg}: cos i = cos(lat) sin(az), so due east gives the lowest inclination (= latitude),
     * north or south a polar orbit, west a retrograde one.
     */
    public static Elements launch(BlockPos site, long tick, double azDeg, double altKm) {
        double[] ll = latLon(site);
        double lat = ll[0], az = Math.toRadians(azDeg);
        double cosI = Math.cos(lat) * Math.sin(az);
        double incl = Math.acos(Math.max(-1, Math.min(1, cosI)));
        if (incl < Math.abs(lat)) incl = Math.abs(lat);
        double su = Math.sin(lat) / Math.max(1e-9, Math.sin(incl));
        double u0 = Math.asin(Math.max(-1, Math.min(1, su)));
        boolean northbound = Math.cos(az) >= 0;
        if (!northbound) u0 = Math.PI - u0;
        double alpha = ll[1] + gst(tick); // right ascension of the site
        double raan = alpha - Math.atan2(Math.cos(incl) * Math.sin(u0), Math.cos(u0));
        return new Elements(altKm, incl, raan, u0, tick);
    }

    public static final double GEO_ALT = 35786;

    /** a geostationary satellite parked over the launch site's longitude (equatorial, inclination 0) */
    public static Elements geo(BlockPos site, long tick) {
        double lon = latLon(site)[1];
        return new Elements(GEO_ALT, 0, 0, lon + gst(tick), tick);
    }

    /** unit vector toward the sun, Earth-fixed: noon is overhead at the world origin's longitude */
    public static double[] sunEcef(net.minecraft.world.level.Level level) {
        double lon = Math.toRadians(LON0) - level.getTimeOfDay(1f) * 2 * Math.PI;
        return new double[]{Math.cos(lon), Math.sin(lon), 0};
    }

    /** is the satellite in the Earth's shadow? */
    public static boolean eclipsed(Elements el, net.minecraft.world.level.Level level, long tick) {
        double[] s = el.ecefAt(tick), sun = sunEcef(level);
        double d = s[0] * sun[0] + s[1] * sun[1] + s[2] * sun[2];
        if (d >= 0) return false;
        double px = s[0] - d * sun[0], py = s[1] - d * sun[1], pz = s[2] - d * sun[2];
        return px * px + py * py + pz * pz < RE * RE;
    }

    /** can two satellites see each other past the Earth (100 km of atmosphere kept clear)? */
    public static boolean lineOfSight(Elements a, Elements b, long tick) {
        double[] p = a.ecefAt(tick), q = b.ecefAt(tick);
        double dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
        double len2 = dx * dx + dy * dy + dz * dz;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(p[0] * dx + p[1] * dy + p[2] * dz) / len2));
        double cx = p[0] + t * dx, cy = p[1] + t * dy, cz = p[2] + t * dz;
        return cx * cx + cy * cy + cz * cz > (RE + 100) * (RE + 100);
    }

    /** where a satellite is seen from a station */
    public record Look(double azDeg, double elDeg, double rangeKm, double rangeRateKmS, double subLat, double subLon) {
        public boolean visible() {
            return elDeg > 0;
        }

        /** unit vector in Minecraft axes (x east, y up, z south) */
        public Vec3 dir() {
            double a = Math.toRadians(azDeg), e = Math.toRadians(elDeg);
            return new Vec3(Math.sin(a) * Math.cos(e), Math.sin(e), -Math.cos(a) * Math.cos(e));
        }

        /** Doppler shift (Hz) of a carrier at hz: positive while approaching */
        public double dopplerHz(double hz) {
            return -hz * rangeRateKmS / C_KM_S;
        }
    }

    public static Look look(Elements el, BlockPos station, double tick) {
        double[] ll = latLon(station);
        double lat = ll[0], lon = ll[1];
        double[] st = ecef(lat, lon, 0);
        double[] s0 = el.ecefAt(tick), s1 = el.ecefAt(tick + 1);
        double dx = s0[0] - st[0], dy = s0[1] - st[1], dz = s0[2] - st[2];
        double range = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double r1 = Math.sqrt(sq(s1[0] - st[0]) + sq(s1[1] - st[1]) + sq(s1[2] - st[2]));
        double sl = Math.sin(lat), cl = Math.cos(lat), so = Math.sin(lon), co = Math.cos(lon);
        double east = -so * dx + co * dy;
        double north = -sl * co * dx - sl * so * dy + cl * dz;
        double up = cl * co * dx + cl * so * dy + sl * dz;
        double az = Math.toDegrees(Math.atan2(east, north));
        if (az < 0) az += 360;
        double elev = Math.toDegrees(Math.asin(up / range));
        double sr = Math.sqrt(sq(s0[0]) + sq(s0[1]) + sq(s0[2]));
        return new Look(az, elev, range, (r1 - range) / secondsPerTick(),
                Math.toDegrees(Math.asin(s0[2] / sr)), Math.toDegrees(Math.atan2(s0[1], s0[0])));
    }

    /** ticks until the satellite next rises above the horizon (0 if up now), or -1 if not within two orbits */
    public static long nextRise(Elements el, BlockPos station, long now) {
        if (look(el, station, now).visible()) return 0;
        long horizon = (long) (2 * el.periodS() / secondsPerTick());
        for (long t = 20; t < horizon; t += 20) {
            if (look(el, station, now + t).visible()) return t;
        }
        return -1;
    }

    private static double sq(double v) {
        return v * v;
    }
}
