package com.sparkgap.pgtelegraph.broadcast;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Imported recordings, kept in the world folder (pgtelegraph_audio/*.pcm): mono, 8-bit signed,
 * {@value #RATE} samples a second - AM broadcast quality, 11 kB a second, 8 minutes at most.
 */
public final class AudioStore {
    private AudioStore() {}

    public static final int RATE = 11025, MAX_SECONDS = 480, MAX_BYTES = RATE * MAX_SECONDS;
    private static final Map<String, byte[]> CACHE = new HashMap<>();

    static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("pgtelegraph_audio");
    }

    public static boolean validId(String id) {
        try {
            UUID.fromString(id);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String save(MinecraftServer server, byte[] pcm) throws IOException {
        String id = UUID.randomUUID().toString();
        Path d = dir(server);
        Files.createDirectories(d);
        Files.write(d.resolve(id + ".pcm"), pcm);
        CACHE.put(id, pcm);
        return id;
    }

    public static byte[] load(MinecraftServer server, String id) {
        if (!validId(id)) return null;
        byte[] b = CACHE.get(id);
        if (b != null) return b;
        try {
            b = Files.readAllBytes(dir(server).resolve(id + ".pcm"));
            if (CACHE.size() > 16) CACHE.clear();
            CACHE.put(id, b);
            return b;
        } catch (IOException e) {
            PgTelegraph.LOGGER.warn("Missing recording {}", id);
            return null;
        }
    }
}
