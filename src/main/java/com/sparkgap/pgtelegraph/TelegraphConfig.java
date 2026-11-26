package com.sparkgap.pgtelegraph;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (world/serverconfig/pgtelegraph-server.toml). */
public final class TelegraphConfig {
    private TelegraphConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue NEAR_CHUNKS;
    public static final ModConfigSpec.IntValue FAR_CHUNKS;
    public static final ModConfigSpec.DoubleValue MAX_METERS_PER_BLOCK;
    public static final ModConfigSpec.BooleanValue STATION_CHUNK_LOADING;
    public static final ModConfigSpec.BooleanValue VIRTUAL_STATIONS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("radio");
        NEAR_CHUNKS = b.comment("Radio propagation distance is true 1:1 (1 block = 1 m) up to this many chunks.")
                .defineInRange("nearChunks", 6, 0, 256);
        FAR_CHUNKS = b.comment("At this many chunks the distance scale reaches maxMetersPerBlock (smooth ramp in between).")
                .defineInRange("farChunks", 60, 1, 4096);
        MAX_METERS_PER_BLOCK = b.comment("Metres of propagation distance per block beyond farChunks.")
                .defineInRange("maxMetersPerBlock", 100.0, 1.0, 100000.0);
        STATION_CHUNK_LOADING = b.comment("Radio stations (transmitters, receivers, amplifiers, alternators) keep their own chunk,",
                        "their coax run and their antenna loaded so they keep working when nobody is near.")
                .define("stationChunkLoading", true);
        b.pop();
        VIRTUAL_STATIONS = b.comment("Far-away stations on the air: the NCDXF beacon network and CW activity on the HF bands.")
                .define("virtualStations", true);
        SPEC = b.build();
    }

    public static int nearBlocks() {
        try {
            return NEAR_CHUNKS.get() * 16;
        } catch (IllegalStateException e) {
            return 96;
        }
    }

    public static int farBlocks() {
        try {
            return Math.max(nearBlocks() + 16, FAR_CHUNKS.get() * 16);
        } catch (IllegalStateException e) {
            return 960;
        }
    }

    public static double maxMetersPerBlock() {
        try {
            return MAX_METERS_PER_BLOCK.get();
        } catch (IllegalStateException e) {
            return 100;
        }
    }

    public static boolean chunkLoading() {
        try {
            return STATION_CHUNK_LOADING.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean virtualStations() {
        try {
            return VIRTUAL_STATIONS.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
