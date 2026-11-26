package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.media.TvFrames;
import com.sparkgap.pgtelegraph.media.TvNet;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Films TV cameras the server asked this client to film: once in a while, before the frame is
 * drawn, the world is rendered once from the camera (the view moved to the camera, no hand),
 * read back, cut to 4:3, scaled to 640 x 480 and JPEG'd in the background; then the normal frame
 * is drawn over it as if nothing happened.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT)
public final class TvCapture {
    private TvCapture() {}

    private record Job(BlockPos cam, double x, double y, double z, float yaw, float pitch, long until) {}

    private static final Map<BlockPos, Job> JOBS = new HashMap<>();
    private static long lastShot;
    private static int turn, seq;
    private static Marker dummy;

    /** our own view filmed for a bodycam (640 x 480, 30/s) or a camcorder (320 x 240, 15/s) */
    private static final class Own {
        long until, last;
        final int w, h, fps;
        /** a bodycam sees its wearer's hands and what they hold; a camcorder (held up to the eye) does not */
        final boolean hands;

        Own(long until, int w, int h, int fps, boolean hands) {
            this.until = until;
            this.w = w;
            this.h = h;
            this.fps = fps;
            this.hands = hands;
        }
    }

    private static final Map<BlockPos, Own> OWN = new HashMap<>();
    private static volatile boolean busy;
    /** encoding in order, one picture at a time (an H.264 encoder predicts from the last picture) */
    private static final java.util.concurrent.ExecutorService ENC = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pgtelegraph-tv-encode");
        t.setDaemon(true);
        return t;
    });
    private static final Map<BlockPos, com.sparkgap.pgtelegraph.media.TvCodec.Enc> ENCODERS = new HashMap<>();
    private static volatile boolean camBusy;

    /** JPEG for the small camcorder picture (it goes on tape), H.264 for live pictures */
    private static byte[] encode(BlockPos key, int[] rgb, int w, int h) {
        if (w <= 320) return TvFrames.jpeg(rgb, w, h, 0.6f);
        com.sparkgap.pgtelegraph.media.TvCodec.Enc e;
        synchronized (ENCODERS) {
            if (ENCODERS.size() > 32) ENCODERS.clear();
            e = ENCODERS.computeIfAbsent(key, k -> new com.sparkgap.pgtelegraph.media.TvCodec.Enc());
        }
        try {
            return e.encode(rgb, w, h);
        } catch (Throwable t) {
            synchronized (ENCODERS) {
                ENCODERS.remove(key);
            }
            return TvFrames.jpeg(rgb, w, h, 0.7f);
        }
    }

    static void film(TvNet.Film m) {
        var lvl = Minecraft.getInstance().level;
        if (lvl == null) return;
        if (Float.isNaN(m.yaw())) { // our own view: the frame we are drawing anyway
            boolean small = m.pitch() == 1;
            Own o = OWN.get(m.cam());
            if (o == null) OWN.put(m.cam(), new Own(lvl.getGameTime() + m.ticks(), small ? 320 : TvFrames.W, small ? 240 : TvFrames.H,
                    Math.min(TvFrames.MAX_FPS, small ? 15 : com.sparkgap.pgtelegraph.media.BodycamItem.FPS), !small));
            else o.until = lvl.getGameTime() + m.ticks();
            return;
        }
        JOBS.put(m.cam(), new Job(m.cam(), m.x(), m.y(), m.z(), m.yaw(), m.pitch(), lvl.getGameTime() + m.ticks()));
    }

    /** right after the world is drawn (before the hand and HUD): the camcorder's picture (and the bodycam's with the HUD hidden) */
    @SubscribeEvent
    public static void onLevelDrawn(net.neoforged.neoforge.client.event.RenderLevelStageEvent e) {
        if (OWN.isEmpty() || e.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        capture(false, Minecraft.getInstance().options.hideGui);
    }

    /** the hands are drawn, the HUD not yet: the bodycam's picture */
    @SubscribeEvent
    public static void onBeforeHud(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre e) {
        if (!OWN.isEmpty()) capture(true, false);
    }

    /** read the frame back once for every own-view job due at this stage */
    private static void capture(boolean handStage, boolean hudHidden) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long gt = mc.level.getGameTime();
        OWN.values().removeIf(o -> gt > o.until);
        if (busy) return;
        long now = System.currentTimeMillis();
        List<Map.Entry<BlockPos, Own>> due = new ArrayList<>();
        for (var en : OWN.entrySet()) {
            Own o = en.getValue();
            boolean here = handStage ? o.hands : (!o.hands || hudHidden);
            if (here && now - o.last >= 1000 / o.fps) due.add(en);
        }
        // keep to a steady beat (not "33 ms after the last shot", which at 50 fps would give only 25)
        if (due.isEmpty()) return;
        NativeImage shot;
        try {
            shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        } catch (Throwable ex) {
            return;
        }
        busy = true;
        List<Object[]> jobs = new ArrayList<>();
        for (var en : due) {
            Own o = en.getValue();
            long period = 1000 / o.fps;
            o.last = now - o.last > 2 * period ? now : o.last + period;
            jobs.add(new Object[]{en.getKey(), en.getValue().w, en.getValue().h, ++seq});
        }
        ENC.execute(() -> { // shrinking and encoding off the render thread
            try (shot) {
                for (Object[] j : jobs) {
                    int w = (int) j[1], h = (int) j[2];
                    byte[] jpeg = encode((BlockPos) j[0], shrink(shot, w, h), w, h);
                    if (jpeg != null) mc.execute(() -> send((BlockPos) j[0], (int) j[3], jpeg));
                }
            } catch (Throwable ignored) {
            } finally {
                busy = false;
            }
        });
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || JOBS.isEmpty() || mc.isPaused()) return;
        long now = System.currentTimeMillis();
        long t = mc.level.getGameTime();
        JOBS.values().removeIf(j -> j.until < t);
        if (JOBS.isEmpty()) return;
        // one camera per shot, taking turns; FPS shots per second per camera
        if (now - lastShot < 1000 / (TvFrames.FPS * JOBS.size()) || camBusy) return;
        lastShot = now;
        List<Job> list = new ArrayList<>(JOBS.values());
        Job job = list.get(Math.floorMod(turn++, list.size()));
        int[] rgb = shoot(mc, job, e);
        if (rgb == null) return;
        BlockPos cam = job.cam;
        int s = ++seq;
        camBusy = true;
        ENC.execute(() -> {
            try {
                byte[] z = encode(cam, rgb, TvFrames.W, TvFrames.H);
                if (z != null) mc.execute(() -> send(cam, s, z));
            } finally {
                camBusy = false;
            }
        });
    }

    private static int[] shoot(Minecraft mc, Job job, RenderFrameEvent e) {
        if (dummy == null || dummy.level() != mc.level) dummy = new Marker(EntityType.MARKER, mc.level);
        dummy.setPos(job.x, job.y, job.z);
        dummy.xo = dummy.xOld = job.x;
        dummy.yo = dummy.yOld = job.y;
        dummy.zo = dummy.zOld = job.z;
        dummy.setYRot(job.yaw);
        dummy.setXRot(job.pitch);
        dummy.yRotO = job.yaw;
        dummy.xRotO = job.pitch;
        Entity prev = mc.getCameraEntity();
        CameraType prevType = mc.options.getCameraType();
        boolean prevHide = mc.options.hideGui;
        try {
            mc.setCameraEntity(dummy);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
            mc.gameRenderer.setRenderHand(false);
            mc.gameRenderer.renderLevel(e.getPartialTick());
            try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                return shrink(shot);
            }
        } catch (Throwable ex) {
            return null;
        } finally {
            mc.gameRenderer.setRenderHand(true);
            mc.options.hideGui = prevHide;
            mc.options.setCameraType(prevType);
            mc.setCameraEntity(prev);
        }
    }

    private static void send(BlockPos cam, int s, byte[] z) {
        if (Minecraft.getInstance().getConnection() == null) return;
        int total = (z.length + TvNet.CHUNK - 1) / TvNet.CHUNK;
        if (total * TvNet.CHUNK > TvNet.MAX_BYTES) return;
        for (int i = 0; i < total; i++)
            PacketDistributor.sendToServer(new TvNet.Up(cam, s, i, total,
                    Arrays.copyOfRange(z, i * TvNet.CHUNK, Math.min(z.length, (i + 1) * TvNet.CHUNK))));
    }

    /** cut the middle to 4:3 and box-average down to W x H (0xRRGGBB) */
    private static int[] shrink(NativeImage s) {
        return shrink(s, TvFrames.W, TvFrames.H);
    }

    private static int[] shrink(NativeImage s, int W, int H) {
        int sw = s.getWidth(), sh = s.getHeight();
        int cw = sw, ch = sh;
        if (sw * H > sh * W) cw = sh * W / H;
        else ch = sw * H / W;
        int ox = (sw - cw) / 2, oy = (sh - ch) / 2;
        int[] out = new int[W * H];
        for (int y = 0; y < H; y++) {
            int y0 = oy + y * ch / H, y1 = Math.max(y0 + 1, oy + (y + 1) * ch / H);
            for (int x = 0; x < W; x++) {
                int x0 = ox + x * cw / W, x1 = Math.max(x0 + 1, ox + (x + 1) * cw / W);
                long r = 0, g = 0, b = 0;
                int n = 0;
                for (int yy = y0; yy < y1; yy++)
                    for (int xx = x0; xx < x1; xx++) {
                        int c = s.getPixelRGBA(xx, yy); // ABGR
                        r += c & 255;
                        g += (c >> 8) & 255;
                        b += (c >> 16) & 255;
                        n++;
                    }
                out[y * W + x] = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        return out;
    }
}
