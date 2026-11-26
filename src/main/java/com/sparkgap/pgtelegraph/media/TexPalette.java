package com.sparkgap.pgtelegraph.media;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * What blocks look like, for the server-side TV camera: for each block a 4 × 4 thumbnail of its
 * top and of its side texture (RGB565) and whether the texture is tinted by the biome (grass,
 * leaves, water). The server has no textures, so the first player to join builds this table from
 * their resource pack and sends it up; it is kept with the world (pgtelegraph_textures.bin), with
 * a fingerprint of the block registry beside it (.sig): when mods add blocks the table is rebuilt
 * by the next player to join. /pgtexture rebuilds it from your own client (new resource pack).
 * Format (deflated): int count, then per block: UTF id, byte flags (1 top tinted, 2 side tinted),
 * 16 shorts top, 16 shorts side.
 */
public final class TexPalette {
    private TexPalette() {}

    public static final int MAX_BYTES = 4 << 20;

    public record Entry(boolean tintTop, boolean tintSide, short[] top, short[] side) {}

    private static Map<Block, Entry> table;
    private static boolean loaded;

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("pgtelegraph_textures.bin");
    }

    public static synchronized boolean has(MinecraftServer server) {
        load(server);
        return table != null;
    }

    private static Path sigFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("pgtelegraph_textures.sig");
    }

    /** fingerprint of the block registry: changes when a mod adds or removes blocks */
    static String sig() {
        long h = 1125899906842597L;
        int n = 0;
        for (ResourceLocation k : BuiltInRegistries.BLOCK.keySet().stream().sorted().toList()) {
            h = h * 31 + k.toString().hashCode();
            n++;
        }
        return n + ":" + Long.toHexString(h);
    }

    /** a table exists and was made for the blocks this server has now */
    public static synchronized boolean fresh(MinecraftServer server) {
        if (!has(server)) return false;
        try {
            Path f = sigFile(server);
            return Files.exists(f) && Files.readString(f).trim().equals(sig());
        } catch (IOException e) {
            return false;
        }
    }

    /** /pgtexture: treat the table as stale (the old one stays in use until a new one comes) */
    public static synchronized void invalidate(MinecraftServer server) {
        try {
            Files.deleteIfExists(sigFile(server));
        } catch (IOException ignored) {
        }
    }

    public static synchronized Entry get(MinecraftServer server, Block b) {
        load(server);
        return table == null ? null : table.get(b);
    }

    private static void load(MinecraftServer server) {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file(server);
            if (Files.exists(f)) table = parse(Files.readAllBytes(f));
        } catch (IOException e) {
            table = null;
        }
    }

    /** forget the table when the server stops (another world may be opened next) */
    public static synchronized void reset() {
        table = null;
        loaded = false;
    }

    /** a table sent up by a client; returns its block count, 0 when ignored (still fresh, or does not parse) */
    public static synchronized int accept(MinecraftServer server, byte[] deflated) {
        if (fresh(server)) return 0;
        Map<Block, Entry> t = parse(deflated);
        if (t == null || t.size() < 16) return 0;
        table = t;
        try {
            Files.write(file(server), deflated);
            Files.writeString(sigFile(server), sig());
        } catch (IOException e) {
            LogUtils.getLogger().warn("pgtelegraph: could not save texture table", e);
        }
        return t.size();
    }

    private static Map<Block, Entry> parse(byte[] deflated) {
        byte[] raw = Pic.inflate(deflated, MAX_BYTES);
        if (raw == null) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
            int n = in.readInt();
            if (n < 0 || n > 200000) return null;
            Map<Block, Entry> t = new IdentityHashMap<>();
            for (int i = 0; i < n; i++) {
                String id = in.readUTF();
                int flags = in.readByte();
                short[] top = new short[16], side = new short[16];
                for (int k = 0; k < 16; k++) top[k] = in.readShort();
                for (int k = 0; k < 16; k++) side[k] = in.readShort();
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) continue;
                Block b = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
                if (b != null) t.put(b, new Entry((flags & 1) != 0, (flags & 2) != 0, top, side));
            }
            return t;
        } catch (IOException e) {
            return null;
        }
    }

    /** writer used by the client */
    public static final class Writer {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream out = new DataOutputStream(bytes);
        private int count;

        public void add(String id, boolean tintTop, boolean tintSide, short[] top, short[] side) throws IOException {
            out.writeUTF(id);
            out.writeByte((tintTop ? 1 : 0) | (tintSide ? 2 : 0));
            for (short s : top) out.writeShort(s);
            for (short s : side) out.writeShort(s);
            count++;
        }

        public byte[] finish() throws IOException {
            out.flush();
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(all);
            o.writeInt(count);
            o.write(bytes.toByteArray());
            o.flush();
            return Pic.deflate(all.toByteArray());
        }
    }
}
