package com.sparkgap.pgtelegraph.media;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Photos kept with the world: one deflated {@link Pic} file per photo in world/pgtelegraph_photos. */
public final class PhotoStore {
    private PhotoStore() {}

    private static final Map<String, byte[]> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
            return size() > 24;
        }
    };

    static boolean validId(String id) {
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Path file(MinecraftServer server, String id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("pgtelegraph_photos").resolve(id + ".bin");
    }

    /** store a picture; returns its id */
    public static synchronized String save(MinecraftServer server, byte[] img) {
        String id = UUID.randomUUID().toString();
        try {
            Path f = file(server, id);
            Files.createDirectories(f.getParent());
            Files.write(f, Pic.deflate(img));
        } catch (IOException e) {
            LogUtils.getLogger().warn("pgtelegraph: could not save photo", e);
        }
        CACHE.put(id, img);
        return id;
    }

    private static Path trash(MinecraftServer server, String id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("pgtelegraph_photos").resolve("trash").resolve(id + ".bin");
    }

    /**
     * a photo item was destroyed (burnt, despawned, fell out of the world): its picture goes to the
     * trash, from where it comes back if some copy still asks for it, and is purged after a week
     */
    public static synchronized void delete(MinecraftServer server, String id) {
        if (!validId(id)) return;
        CACHE.remove(id);
        try {
            Path f = file(server, id), t = trash(server, id);
            if (!Files.exists(f)) return;
            Files.createDirectories(t.getParent());
            Files.move(f, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(t, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
        } catch (IOException e) {
            LogUtils.getLogger().warn("pgtelegraph: could not delete photo", e);
        }
    }

    /** server start: empty trash older than 7 days */
    public static synchronized void purge(MinecraftServer server) {
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("pgtelegraph_photos").resolve("trash");
        if (!Files.isDirectory(dir)) return;
        long cut = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        try (var s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.getLastModifiedTime(p).toMillis() < cut) Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            LogUtils.getLogger().warn("pgtelegraph: could not purge photo trash", e);
        }
    }

    /** the deflated file, or null */
    public static synchronized byte[] loadDeflated(MinecraftServer server, String id) {
        if (!validId(id)) return null;
        byte[] img = CACHE.get(id);
        if (img != null) return Pic.deflate(img);
        try {
            Path f = file(server, id), t = trash(server, id);
            if (!Files.exists(f) && Files.exists(t)) Files.move(t, f); // a copy survived: restore
            return Files.exists(f) ? Files.readAllBytes(f) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** the picture of a photo (old 64 × 64 ones scaled up), or a black frame */
    public static synchronized byte[] image(MinecraftServer server, Photo p) {
        if (p == null) return new byte[Pic.BYTES];
        if (p.id().isEmpty()) return Pic.fromLegacy(p.legacy());
        byte[] img = CACHE.get(p.id());
        if (img != null) return img;
        byte[] z = loadDeflated(server, p.id());
        img = z == null ? null : Pic.inflate(z, Pic.BYTES);
        if (img == null || img.length != Pic.BYTES) img = new byte[Pic.BYTES];
        CACHE.put(p.id(), img);
        return img;
    }
}
