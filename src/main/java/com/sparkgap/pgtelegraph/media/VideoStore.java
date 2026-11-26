package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Video tapes on disk: {@code <world>/pgtelegraph_videos/<id>.vid}, a run of [length][JPEG] frames,
 * 320 x 240 at 15 frames a second (about VHS: 5-7 MB a minute), at most 10 minutes a tape.
 * Writing goes on one background thread; importing and exporting other video files go through
 * ffmpeg on the server's machine.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class VideoStore {
    private VideoStore() {}

    public static final int W = 320, H = 240, FPS = 15, MAX_SECONDS = 600, MAX_FRAMES = FPS * MAX_SECONDS;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pgtelegraph-video");
        t.setDaemon(true);
        return t;
    });

    /** where each tape's frames start in its file */
    private static final class Index {
        final List<long[]> frames = new ArrayList<>(); // {offset, length}
        long end;
    }

    private static final Map<String, Index> INDEX = new ConcurrentHashMap<>();
    private static final Random RND = new Random();

    public static String newId() {
        return String.format("%016x", RND.nextLong());
    }

    static boolean validId(String id) {
        return id != null && id.matches("[0-9a-f]{16}");
    }

    private static Path dir(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("pgtelegraph_videos");
    }

    private static Path file(MinecraftServer s, String id) {
        return dir(s).resolve(id + ".vid");
    }

    private static Index index(MinecraftServer s, String id) {
        return INDEX.computeIfAbsent(id, k -> {
            Index ix = new Index();
            Path f = file(s, id);
            if (!Files.exists(f)) return ix;
            try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "r")) {
                long p = 0, n = raf.length();
                while (p + 4 <= n) {
                    raf.seek(p);
                    int len = raf.readInt();
                    if (len <= 0 || p + 4 + len > n) break;
                    ix.frames.add(new long[]{p + 4, len});
                    p += 4 + len;
                }
                ix.end = p;
            } catch (IOException ignored) {
            }
            return ix;
        });
    }

    public static int frames(MinecraftServer s, String id) {
        if (!validId(id)) return 0;
        Index ix = index(s, id);
        synchronized (ix) {
            return ix.frames.size();
        }
    }

    public static byte[] read(MinecraftServer s, String id, int i) {
        if (!validId(id)) return null;
        Index ix = index(s, id);
        long[] at;
        synchronized (ix) {
            if (i < 0 || i >= ix.frames.size()) return null;
            at = ix.frames.get(i);
        }
        try (RandomAccessFile raf = new RandomAccessFile(file(s, id).toFile(), "r")) {
            byte[] b = new byte[(int) at[1]];
            raf.seek(at[0]);
            raf.readFully(b);
            return b;
        } catch (IOException e) {
            return null;
        }
    }

    /** add a 320 x 240 JPEG frame at the end (background) */
    public static void append(MinecraftServer s, String id, byte[] jpeg) {
        if (!validId(id) || jpeg == null) return;
        IO.execute(() -> write(s, id, jpeg));
    }

    /**
     * record a received picture: scaled to 320 x 240 with what the signal did to it burnt in -
     * snow, colour washing out, a ghost (as a VCR records an off-air picture)
     */
    public static void appendReceived(MinecraftServer s, String id, java.util.function.Supplier<int[]> picture, double snrDb, double ghost) {
        if (!validId(id)) return;
        IO.execute(() -> {
            int[] src = picture.get();
            if (src == null) return;
            int sw = src[0], sh = src[1];
            int[] out = new int[W * H];
            Random r = new Random();
            double sigma = snrDb >= 45 ? 0 : 255 * Math.pow(10, -snrDb / 20) * 0.6;
            double sat = Math.max(0, Math.min(1, (snrDb - 12) / 18)); // colour goes before the picture does
            int gShift = 6;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int c = src[2 + (y * sh / H) * sw + x * sw / W];
                    double rr = c >> 16 & 255, gg = c >> 8 & 255, bb = c & 255;
                    if (ghost > 0) {
                        int gc = src[2 + (y * sh / H) * sw + Math.max(0, x - gShift) * sw / W];
                        rr = rr * (1 - ghost) + (gc >> 16 & 255) * ghost;
                        gg = gg * (1 - ghost) + (gc >> 8 & 255) * ghost;
                        bb = bb * (1 - ghost) + (gc & 255) * ghost;
                    }
                    double yy = 0.299 * rr + 0.587 * gg + 0.114 * bb;
                    rr = yy + (rr - yy) * sat;
                    gg = yy + (gg - yy) * sat;
                    bb = yy + (bb - yy) * sat;
                    if (sigma > 0) {
                        double n = r.nextGaussian() * sigma;
                        rr += n;
                        gg += n;
                        bb += n;
                    }
                    out[y * W + x] = clamp(rr) << 16 | clamp(gg) << 8 | clamp(bb);
                }
            write(s, id, TvFrames.jpeg(out, W, H, 0.6f));
        });
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    private static void write(MinecraftServer s, String id, byte[] jpeg) {
        if (jpeg == null) return;
        Index ix = index(s, id);
        synchronized (ix) {
            if (ix.frames.size() >= MAX_FRAMES) return;
            try {
                Files.createDirectories(dir(s));
                try (RandomAccessFile raf = new RandomAccessFile(file(s, id).toFile(), "rw")) {
                    raf.seek(ix.end);
                    raf.writeInt(jpeg.length);
                    raf.write(jpeg);
                    ix.frames.add(new long[]{ix.end + 4, jpeg.length});
                    ix.end += 4 + jpeg.length;
                    raf.setLength(ix.end);
                }
            } catch (IOException ignored) {
            }
        }
    }

    /** wipe a tape (background) */
    public static void erase(MinecraftServer s, String id) {
        if (!validId(id)) return;
        Index now = index(s, id);
        synchronized (now) {
            now.frames.clear();
            now.end = 0;
        }
        IO.execute(() -> {
            Index ix = index(s, id);
            synchronized (ix) {
                ix.frames.clear();
                ix.end = 0;
                try {
                    Files.deleteIfExists(file(s, id));
                } catch (IOException ignored) {
                }
            }
        });
    }

    // ---------------------------------------------------------------- ffmpeg
    public static Path importDir() {
        return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("pgtelegraph_videos").resolve("import");
    }

    public static Path exportDir() {
        return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("pgtelegraph_videos").resolve("export");
    }

    /** convert any video file onto a tape with ffmpeg (no sound), 320 x 240 15 fps, up to 10 minutes */
    public static void importFile(MinecraftServer s, String id, Path src, Consumer<String> done) {
        Thread t = new Thread(() -> {
            String err = null;
            try {
                Process p = new ProcessBuilder("ffmpeg", "-nostdin", "-v", "error", "-i", src.toString(), "-t", String.valueOf(MAX_SECONDS), "-an",
                        "-vf", "fps=" + FPS + ",scale=" + W + ":" + H + ":force_original_aspect_ratio=decrease,pad=" + W + ":" + H
                        + ":(ow-iw)/2:(oh-ih)/2,setsar=1", "-q:v", "6", "-f", "image2pipe", "-c:v", "mjpeg", "-")
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                IO.submit(() -> {
                }).get(); // let earlier writes to this tape finish
                Index ix = index(s, id);
                synchronized (ix) {
                    ix.frames.clear();
                    ix.end = 0;
                }
                try (InputStream in = new BufferedInputStream(p.getInputStream())) {
                    ByteArrayOutputStream cur = new ByteArrayOutputStream();
                    int prev = -1, b;
                    boolean inFrame = false;
                    while ((b = in.read()) >= 0) {
                        if (!inFrame) {
                            if (prev == 0xFF && b == 0xD8) {
                                inFrame = true;
                                cur.reset();
                                cur.write(0xFF);
                                cur.write(0xD8);
                            }
                        } else {
                            cur.write(b);
                            if (prev == 0xFF && b == 0xD9) { // end of image
                                write(s, id, cur.toByteArray());
                                inFrame = false;
                                b = -1;
                            }
                        }
                        prev = b;
                    }
                }
                if (p.waitFor() != 0 && frames(s, id) == 0) err = "ffmpeg failed";
            } catch (IOException e) {
                err = "ffmpeg not found";
            } catch (Exception e) {
                err = e.getMessage();
            }
            String e2 = err;
            s.execute(() -> done.accept(e2));
        }, "pgtelegraph-video-import");
        t.setDaemon(true);
        t.start();
    }

    /** write a tape out as an MP4 file with ffmpeg */
    public static void exportFile(MinecraftServer s, String id, Path out, Consumer<String> done) {
        Thread t = new Thread(() -> {
            String err = null;
            try {
                Files.createDirectories(out.getParent());
                Process p = new ProcessBuilder("ffmpeg", "-y", "-v", "error", "-f", "mjpeg", "-framerate", String.valueOf(FPS), "-i", "-",
                        "-c:v", "libx264", "-pix_fmt", "yuv420p", out.toString())
                        .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                try (OutputStream o = new DataOutputStream(p.getOutputStream())) {
                    int n = frames(s, id);
                    for (int i = 0; i < n; i++) {
                        byte[] f = read(s, id, i);
                        if (f != null) o.write(f);
                    }
                }
                if (p.waitFor() != 0) err = "ffmpeg failed";
            } catch (IOException e) {
                err = "ffmpeg not found";
            } catch (Exception e) {
                err = e.getMessage();
            }
            String e2 = err;
            s.execute(() -> done.accept(e2));
        }, "pgtelegraph-video-export");
        t.setDaemon(true);
        t.start();
    }

    /** a dropped recorded tape burnt, despawned or fell into the void: its file goes too */
    @SubscribeEvent
    public static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity ie)) return;
        var why = ie.getRemovalReason();
        if (why != net.minecraft.world.entity.Entity.RemovalReason.DISCARDED && why != net.minecraft.world.entity.Entity.RemovalReason.KILLED) return;
        var st = ie.getItem();
        if (st.isEmpty() || e.getLevel().getServer() == null) return;
        String id = st.getItem() instanceof VideoTapeItem ? VideoTapeItem.id(st) : st.getItem() instanceof CamcorderItem ? VideoTapeItem.id(st) : "";
        if (validId(id)) erase(e.getLevel().getServer(), id);
    }

    @SubscribeEvent
    public static void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) {
        INDEX.clear();
    }
}
