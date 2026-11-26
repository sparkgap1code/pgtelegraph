package com.sparkgap.pgtelegraph.radio;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Space weather and the F2 layer. One sky for the whole server, kept with the overworld.
 * <ul>
 *   <li>Solar flux (SFI 65-250) drifts day to day around a solar cycle of 120 game days.</li>
 *   <li>foF2: day 2.5 + 0.035 SFI MHz, night 1.5 + 0.015 SFI, blended by the sun's height;
 *       MUF (3000 km) = 3 foF2. SFI 120 gives MUF ≈ 20 MHz by day, 10 MHz at night.</li>
 *   <li>Geomagnetic storms (8 % a day, Kp 5-8 for 0.5-2 days) lower the MUF 6 % per Kp above 3.</li>
 *   <li>Solar flares (12 % per game day, daylight only) black out the low HF for 2-8 minutes
 *       (sudden ionospheric disturbance: +30 dB absorption at 3 MHz, falling with f²).</li>
 * </ul>
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class Ionosphere extends SavedData {
    double sfi = 120;
    int kp = 2;
    long stormEnd, flareEnd;
    long lastDay = -1;

    private static final Factory<Ionosphere> FACTORY = new Factory<>(Ionosphere::new, Ionosphere::load, null);
    /** used when no server level is at hand (client side, tests) */
    private static final Ionosphere QUIET = new Ionosphere();

    public static Ionosphere get(Level level) {
        if (level instanceof ServerLevel sl) {
            return sl.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "pgtelegraph_ionosphere");
        }
        return QUIET;
    }

    public double sfi() {
        return sfi;
    }

    public int kp() {
        return kp;
    }

    public boolean flare(Level level) {
        return level.getGameTime() < flareEnd;
    }

    /** 1 at noon, 0 at night, smooth through dawn and dusk */
    public static double sun(Level level) {
        double a = level.getTimeOfDay(1f) * 2 * Math.PI;
        return Math.max(0, Math.min(1, 0.5 + 1.5 * Math.cos(a)));
    }

    public double foF2(Level level) {
        double d = sun(level);
        double f = d * (2.5 + 0.035 * sfi) + (1 - d) * (1.5 + 0.015 * sfi);
        if (kp > 3) f *= Math.max(0.5, 1 - 0.06 * (kp - 3));
        return f;
    }

    public double muf(Level level) {
        return 3 * foF2(level);
    }

    /** electron gyrofrequency (MHz): non-deviative absorption goes as 1 / (f + fH)^2, not 1 / f^2 */
    public static final double GYRO_MHZ = 1.4;

    /**
     * D-layer absorption for one hop (dB), after the George-Bradley form used in ITU-R P.533:
     * proportional to 1 / (f + fH)^2, so it is fierce on MF by day, gone by night, and does not
     * blow up on LF / VLF (which are reflected off the bottom of the D region instead).
     */
    public double absorptionDb(Level level, double fMHz) {
        double d = sun(level);
        double k = (fMHz + GYRO_MHZ) * (fMHz + GYRO_MHZ);
        double a = (36 + 540 * d * (sfi / 120)) / k;
        if (flare(level) && d > 0) a += 30 * 9 * 1.44 / k * d;
        return a;
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return;
        Ionosphere io = get(sl);
        RandomSource r = sl.random;
        long t = sl.getGameTime();
        long day = sl.getDayTime() / 24000;
        if (day != io.lastDay) {
            io.lastDay = day;
            double cycle = 140 + 70 * Math.sin(day * 2 * Math.PI / 120);
            io.sfi = Math.max(65, Math.min(250, io.sfi + (cycle - io.sfi) * 0.2 + r.nextGaussian() * 10));
            if (t >= io.stormEnd && r.nextInt(100) < 8) {
                io.stormEnd = t + 12000 + r.nextInt(36000);
                io.kp = 5 + r.nextInt(4);
            }
            io.setDirty();
        }
        if (t >= io.stormEnd && io.kp > 3) {
            io.kp = 1 + r.nextInt(3);
            io.setDirty();
        }
        // flares: checked every minute in daylight; 12 % per 10-minute day ≈ 1.2 % a minute
        if (t % 1200 == 0 && sun(sl) > 0.3 && t >= io.flareEnd && r.nextInt(1000) < 12 * io.sfi / 120) {
            io.flareEnd = t + 2400 + r.nextInt(7200);
            io.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putDouble("Sfi", sfi);
        tag.putInt("Kp", kp);
        tag.putLong("StormEnd", stormEnd);
        tag.putLong("FlareEnd", flareEnd);
        tag.putLong("LastDay", lastDay);
        return tag;
    }

    static Ionosphere load(CompoundTag tag, HolderLookup.Provider registries) {
        Ionosphere io = new Ionosphere();
        io.sfi = tag.getDouble("Sfi");
        io.kp = tag.getInt("Kp");
        io.stormEnd = tag.getLong("StormEnd");
        io.flareEnd = tag.getLong("FlareEnd");
        io.lastDay = tag.getLong("LastDay");
        return io;
    }
}
