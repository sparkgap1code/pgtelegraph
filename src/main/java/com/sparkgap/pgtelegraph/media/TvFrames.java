package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Live pictures from TV cameras. A server cannot draw the world, so a camera that is in use (by a
 * TV transmitter or an SSTV transmitter beside it) is filmed by a nearby player's client: the
 * server asks the nearest player for it, that client renders the real game view from the camera's
 * position, JPEG-compresses it and sends it up (TvNet). With nobody near, the server falls back
 * to its own ray-cast picture at a lower resolution.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class TvFrames {
    private TvFrames() {}

    /** filmed picture: 4:3, as high as the link can carry */
    public static final int W = 640, H = 480, FPS = 10;
    /** no picture source goes faster than this (NTSC); the server drops frames beyond it */
    public static final int MAX_FPS = 30;
    private static final Map<BlockPos, long[]> RATE = new HashMap<>(); // key -> {window start ms, frames}
    /** server stand-in when nobody is near */
    public static final int FALLBACK_W = 160, FALLBACK_H = 120;
    /** how far a player may be from a camera to film it */
    public static final double FILM_RANGE = 96;

    public record Frame(byte[] jpeg, int seq, long tick, boolean real) {}

    private static final Map<Level, Map<BlockPos, Long>> WANTED = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Frame>> FRAMES = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, UUID>> FILMER = new WeakHashMap<>();

    /** a transmitter is using the camera at {@code cam} (call every tick while it is) */
    public static void want(Level level, BlockPos cam) {
        WANTED.computeIfAbsent(level, l -> new HashMap<>()).put(cam.immutable(), level.getGameTime());
    }

    /** frames since the last key frame, per picture key (an H.264 viewer joining late starts here) */
    private static final Map<Level, Map<BlockPos, java.util.List<Frame>>> GOP = new WeakHashMap<>();
    /** server-side decoders, made only when something needs the pixels (SSTV, a VCR recording) */
    private static final Map<BlockPos, TvCodec.Dec> DEC = new java.util.concurrent.ConcurrentHashMap<>();

    private static Frame store(Level level, BlockPos key, byte[] data, long tick, boolean real) {
        Frame old = frame(level, key);
        Frame fr = new Frame(data, old == null ? 1 : old.seq() + 1, tick, real);
        FRAMES.computeIfAbsent(level, l -> new HashMap<>()).put(key.immutable(), fr);
        java.util.List<Frame> g = GOP.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(key.immutable(), k -> new java.util.ArrayList<>());
        synchronized (g) {
            if (TvCodec.isKey(data)) g.clear();
            if (g.size() < 120) g.add(fr);
        }
        return fr;
    }

    /** the frames a viewer joining now needs, oldest first (up to and including the newest) */
    public static java.util.List<Frame> gop(Level level, BlockPos key) {
        var m = GOP.get(level);
        java.util.List<Frame> g = m == null ? null : m.get(key);
        if (g == null) return java.util.List.of();
        synchronized (g) {
            return new java.util.ArrayList<>(g);
        }
    }

    /** the newest picture of a key as pixels {w, h, rgb...} (decodes H.264 as needed; any thread) */
    public static int[] rgb(Level level, BlockPos key) {
        return rgb(key, gop(level, key));
    }

    /** the same from a copy of the frames taken on the server thread (safe on any thread) */
    public static int[] rgb(BlockPos key, java.util.List<Frame> g) {
        if (g.isEmpty()) return null;
        Frame last = g.get(g.size() - 1);
        if (TvCodec.isJpeg(last.jpeg())) return decode(last.jpeg());
        TvCodec.Dec d = DEC.computeIfAbsent(key.immutable(), k -> new TvCodec.Dec());
        synchronized (d) {
            int[] px = null;
            for (Frame f : g) {
                if (f.seq() <= d.lastSeq && f != last) continue;
                if (f.seq() <= d.lastSeq) return d.last();
                px = d.decode(f.jpeg(), f.seq());
                if (px == null) { // out of step: start again from the key frame
                    d.reset();
                    for (Frame f2 : g) px = d.decode(f2.jpeg(), f2.seq());
                    break;
                }
            }
            return px != null ? px : d.last();
        }
    }

    public static Frame frame(Level level, BlockPos cam) {
        Map<BlockPos, Frame> m = FRAMES.get(level);
        return m == null ? null : m.get(cam);
    }

    /** a frame made on the server (a satellite's camera) */
    public static void put(Level level, BlockPos key, byte[] jpeg) {
        if (jpeg == null) return;
        Frame fr = store(level, key, jpeg, level.getGameTime(), false);
        TvReceiverBlockEntity.push(level, key, fr);
    }

    /** a player films this key from now on (a bodycam: the wearer's own view) */
    public static void filmedBy(Level level, BlockPos key, java.util.UUID player) {
        FILMER.computeIfAbsent(level, l -> new HashMap<>()).put(key.immutable(), player);
    }

    /** a frame filmed by a client (only the player asked for it is believed) */
    public static void uploaded(ServerPlayer p, BlockPos cam, byte[] jpeg) {
        Level level = p.level();
        Map<BlockPos, UUID> f = FILMER.get(level);
        if (f == null || !p.getUUID().equals(f.get(cam)) || jpeg.length < 20) return;
        long now = System.currentTimeMillis();
        long[] r = RATE.computeIfAbsent(cam.immutable(), k -> new long[]{now, 0});
        if (now - r[0] >= 1000) {
            r[0] = now;
            r[1] = 0;
        }
        if (++r[1] > MAX_FPS + 2) return; // faster than 30 a second: dropped (a little slack for timing jitter)
        if (RATE.size() > 256) RATE.clear();
        CamcorderItem.frame(p.getServer(), cam, jpeg); // a camcorder recording this view
        Frame fr = store(level, cam, jpeg, level.getGameTime(), true);
        TvReceiverBlockEntity.push(level, cam, fr); // straight on to the sets showing it, not once a tick
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 200 == 0) for (ServerLevel level : e.getServer().getAllLevels()) {
            long now = level.getGameTime();
            Map<BlockPos, Frame> fr = FRAMES.get(level);
            if (fr == null) continue;
            fr.entrySet().removeIf(en -> {
                if (now - en.getValue().tick() < 600) return false;
                var g = GOP.get(level);
                if (g != null) g.remove(en.getKey());
                DEC.remove(en.getKey());
                return true;
            });
        }
        for (ServerLevel level : e.getServer().getAllLevels()) {
            Map<BlockPos, Long> w = WANTED.get(level);
            if (w == null || w.isEmpty()) continue;
            long now = level.getGameTime();
            w.values().removeIf(t -> now - t > 60);
            for (BlockPos cam : w.keySet()) {
                if (!level.isLoaded(cam)) continue;
                BlockState s = level.getBlockState(cam);
                if (!(s.getBlock() instanceof TvCameraBlock)) continue;
                if (now % 20 == Math.floorMod(cam.asLong(), 20)) assign(level, cam, s, now);
                Frame fr = frame(level, cam);
                boolean stale = fr == null || now - fr.tick() > 40;
                if (stale && now % (20 / FPS) == 0) fallback(level, cam, s, fr, now);
            }
        }
    }

    /** ask the nearest player to film the camera for the next three seconds */
    private static void assign(ServerLevel level, BlockPos cam, BlockState s, long now) {
        ServerPlayer best = null;
        double bd = FILM_RANGE * FILM_RANGE;
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(cam.getCenter());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        Map<BlockPos, UUID> f = FILMER.computeIfAbsent(level, l -> new HashMap<>());
        if (best == null) {
            f.remove(cam);
            return;
        }
        f.put(cam.immutable(), best.getUUID());
        CameraRender.View v = TvCameraBlock.view(level, cam, s);
        float yaw = s.getValue(com.sparkgap.pgtelegraph.block.DeviceBlock.FACING).toYRot();
        PacketDistributor.sendToPlayer(best, new TvNet.Film(cam, v.eye.x, v.eye.y, v.eye.z, yaw,
                (float) -TvCameraBlock.elevation(s), 60));
    }

    /** nobody near: the server's own ray-cast picture, small */
    private static void fallback(ServerLevel level, BlockPos cam, BlockState s, Frame old, long now) {
        CameraRender.View v = TvCameraBlock.view(level, cam, s);
        int[] rgb = CameraRender.frame(level, v, FALLBACK_W, FALLBACK_H);
        byte[] jpeg = jpeg(rgb, FALLBACK_W, FALLBACK_H, 0.8f);
        if (jpeg == null) return;
        store(level, cam, jpeg, now, false);
    }

    // ---------------------------------------------------------------- JPEG
    public static byte[] jpeg(int[] rgb, int w, int h, float quality) {
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, w, h, rgb, 0, w);
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(img, null, null), param);
            }
            writer.dispose();
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** decode to {w, h, pixels...} (0xRRGGBB), or null */
    public static int[] decode(byte[] jpeg) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
            if (img == null) return null;
            int w = img.getWidth(), h = img.getHeight();
            int[] out = new int[2 + w * h];
            out[0] = w;
            out[1] = h;
            img.getRGB(0, 0, w, h, out, 2, w);
            for (int i = 2; i < out.length; i++) out[i] &= 0xFFFFFF;
            return out;
        } catch (Throwable t) {
            return null;
        }
    }
}
