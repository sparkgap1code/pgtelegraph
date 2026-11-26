package com.sparkgap.pgtelegraph.sat;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/** Satellites in orbit, saved with the overworld. Ticks them every server tick. */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class SatelliteData extends SavedData {
    public static final int MAX = 64;
    private final List<Satellite> sats = new ArrayList<>();
    private int nextId = 1;
    private boolean weather;
    /** the polar-orbiting weather satellites every world has: name, APT frequency */
    private static final String[][] WEATHER = {{"NOAA-15", "137620000"}, {"NOAA-18", "137912500"}, {"NOAA-19", "137100000"}};

    private void addWeather(long t) {
        for (int i = 0; i < WEATHER.length; i++) {
            SatConfig c = SatConfig.parse("");
            c.set("NAME", WEATHER[i][0]);
            c.set("POWER", "5");
            c.set("CW", "0");
            c.set("XP", "0");
            c.set("APT", "1");
            c.set("APTHZ", WEATHER[i][1]);
            Satellite s = new Satellite(nextId++, 1, c, new Orbit.Elements(850, Math.toRadians(98.7), i * 2.1, i * 1.3, t));
            sats.add(s);
        }
        weather = true;
        setDirty();
    }

    private static final SavedData.Factory<SatelliteData> FACTORY =
            new SavedData.Factory<>(SatelliteData::new, SatelliteData::load, null);

    public static SatelliteData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "pgtelegraph_satellites");
    }

    /** satellites seen from a level (only the overworld has a sky with an orbit) */
    public static List<Satellite> of(Level level) {
        if (!(level instanceof ServerLevel sl) || level.dimension() != Level.OVERWORLD) return List.of();
        return get(sl).sats;
    }

    public Satellite add(SatConfig cfg, int bus, Orbit.Elements orbit) {
        if (sats.size() >= MAX) {
            for (int i = 0; i < sats.size(); i++)
                if (!sats.get(i).apt()) {
                    sats.remove(i);
                    break;
                }
        }
        for (Satellite s : sats) if (s.name.equalsIgnoreCase(cfg.name())) cfg.set("NAME", cfg.name() + nextId);
        Satellite s = new Satellite(nextId++, bus, cfg, orbit);
        sats.add(s);
        setDirty();
        return s;
    }

    public List<Satellite> all() {
        return sats;
    }

    /** the satellite highest above this station's horizon, or null */
    public static Satellite highest(Level level, BlockPos station) {
        Satellite best = null;
        double bestEl = 0;
        for (Satellite s : of(level)) {
            double el = Orbit.look(s.orbit, station, level.getGameTime()).elDeg();
            if (el > bestEl) {
                bestEl = el;
                best = s;
            }
        }
        return best;
    }

    public static Satellite named(Level level, String name) {
        for (Satellite s : of(level)) if (s.name.equalsIgnoreCase(name)) return s;
        return null;
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || sl.dimension() != Level.OVERWORLD) return;
        SatelliteData d = get(sl);
        long t = sl.getGameTime();
        if (!d.weather) d.addWeather(t);
        for (java.util.Iterator<Satellite> it = d.sats.iterator(); it.hasNext(); ) {
            Satellite s = it.next();
            // air drag, applied every 10 s
            if (t % 200 == 0 && !s.orbit.geostationary()) {
                double alt = s.orbit.altKm() - Satellite.decayKmPerTick(s.orbit.altKm()) * 200;
                if (alt < Satellite.REENTRY_KM) {
                    s.silence(sl);
                    it.remove();
                    d.setDirty();
                    net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component
                            .translatable("pgtelegraph.sat.reentry", s.name).withStyle(net.minecraft.ChatFormatting.GOLD);
                    for (var p : sl.players()) p.displayClientMessage(msg, false);
                    continue;
                }
                s.orbit = s.orbit.withAlt(alt, t);
                if (t % 6000 == 0) d.setDirty();
            }
            s.tick(sl);
            if (s.dirty) {
                s.dirty = false;
                d.setDirty();
            }
        }
        // inter-satellite links
        for (Satellite a : d.sats) {
            if (!a.cfg.on("ISL")) continue;
            java.util.List<Long> heard = new java.util.ArrayList<>();
            for (Satellite b : d.sats) {
                if (b == a || !b.cfg.on("ISL") || b.relayed.isEmpty()) continue;
                if (Orbit.lineOfSight(a.orbit, b.orbit, t)) heard.addAll(b.relayed);
            }
            a.islRelay(sl, heard, t);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (Satellite s : sats) l.add(s.save());
        tag.put("Sats", l);
        tag.putInt("NextId", nextId);
        tag.putBoolean("Weather", weather);
        return tag;
    }

    static SatelliteData load(CompoundTag tag, HolderLookup.Provider registries) {
        SatelliteData d = new SatelliteData();
        for (Tag t : tag.getList("Sats", Tag.TAG_COMPOUND)) d.sats.add(Satellite.load((CompoundTag) t));
        d.nextId = Math.max(1, tag.getInt("NextId"));
        d.weather = tag.getBoolean("Weather");
        return d;
    }
}
