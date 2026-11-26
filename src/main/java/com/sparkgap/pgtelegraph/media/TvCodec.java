package com.sparkgap.pgtelegraph.media;

import org.jcodec.codecs.h264.H264Decoder;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.encode.RateControl;
import org.jcodec.codecs.h264.io.model.SliceType;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Size;

import java.nio.ByteBuffer;

/**
 * H.264 for live TV pictures (JCodec, pure Java: nothing native, runs on every machine). A picture
 * is sent as a JPEG (starts FF D8) or as an H.264 access unit (starts 00 00 00 01); a key frame
 * (sequence header + IDR) comes every second so a set that tunes in late can start.
 * About a quarter of the bits of JPEG at the same quality, 640 x 480 at 30 frames a second
 * coming to about 4 Mbit/s.
 */
public final class TvCodec {
    private TvCodec() {}

    public static final int QP = 26, KEY_INTERVAL = 30;

    public static boolean isJpeg(byte[] b) {
        return b.length > 2 && (b[0] & 255) == 0xFF && (b[1] & 255) == 0xD8;
    }

    /** an H.264 key frame: its first NAL unit is a sequence parameter set (7) or an IDR slice (5) */
    public static boolean isKey(byte[] b) {
        if (isJpeg(b)) return true;
        for (int i = 0; i + 3 < b.length && i < 64; i++)
            if (b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 1) {
                int t = b[i + 3] & 0x1F;
                return t == 7 || t == 5;
            }
        return false;
    }

    private static final class FixedQp implements RateControl {
        public int startPicture(Size s, int maxSize, SliceType t) {
            return QP;
        }

        public int initialQpDelta() {
            return 0;
        }

        public int accept(int bits) {
            return 0;
        }

        public void reset() {}
    }

    /** one camera's encoder (keeps the previous picture to predict from) */
    public static final class Enc {
        /** made new for every key frame (its first picture is always a key frame; JCodec's own
         *  forced-IDR / P-frame calls write broken streams) */
        private H264Encoder enc;
        private Picture pic;
        private ByteBuffer buf;
        private long lastKey, lastFrame;

        public Enc() {}

        public synchronized byte[] encode(int[] rgb, int w, int h) {
            if (pic == null || pic.getWidth() != w || pic.getHeight() != h) {
                pic = Picture.create(w, h, ColorSpace.YUV420J);
                buf = ByteBuffer.allocate(w * h * 3);
            }
            byte[][] d = pic.getData();
            byte[] Y = d[0], U = d[1], V = d[2];
            int cw = w / 2;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int c = rgb[y * w + x];
                    Y[y * w + x] = (byte) (((77 * (c >> 16 & 255) + 150 * (c >> 8 & 255) + 29 * (c & 255)) >> 8) - 128);
                }
            for (int y = 0; y + 1 < h; y += 2) // chroma: the mean of each 2 x 2 block (picking one pixel aliases colour edges)
                for (int x = 0; x + 1 < w; x += 2) {
                    int c0 = rgb[y * w + x], c1 = rgb[y * w + x + 1], c2 = rgb[(y + 1) * w + x], c3 = rgb[(y + 1) * w + x + 1];
                    int r = ((c0 >> 16 & 255) + (c1 >> 16 & 255) + (c2 >> 16 & 255) + (c3 >> 16 & 255)) >> 2;
                    int g = ((c0 >> 8 & 255) + (c1 >> 8 & 255) + (c2 >> 8 & 255) + (c3 >> 8 & 255)) >> 2;
                    int b = ((c0 & 255) + (c1 & 255) + (c2 & 255) + (c3 & 255)) >> 2;
                    int i = (y >> 1) * cw + (x >> 1);
                    U[i] = (byte) clampS((-43 * r - 85 * g + 128 * b) >> 8);
                    V[i] = (byte) clampS((128 * r - 107 * g - 21 * b) >> 8);
                }
            buf.clear();
            long now = System.currentTimeMillis();
            boolean key = lastKey == 0 || now - lastKey >= 1000 || now - lastFrame > 500;
            lastFrame = now;
            if (key) lastKey = now;
            if (key || enc == null) {
                enc = new H264Encoder(new FixedQp());
                enc.setKeyInterval(1 << 20); // keys are ours to decide (time based, above)
            }
            ByteBuffer out = enc.encodeFrame(pic, buf).getData();
            byte[] b = new byte[out.remaining()];
            out.get(b);
            return b;
        }
    }

    /** one picture stream's decoder: feed every frame in order; a gap waits for the next key frame */
    public static final class Dec {
        private H264Decoder dec;
        private byte[][] fb;
        int lastSeq = Integer.MIN_VALUE;
        private int[] last;

        /** @return {w, h, rgb...} or null (JPEGs are decoded too) */
        public synchronized int[] decode(byte[] data, int seq) {
            if (isJpeg(data)) {
                lastSeq = Integer.MIN_VALUE; // the H.264 stream (if any) has to start again
                return last = TvFrames.decode(data);
            }
            boolean key = isKey(data);
            if (!key && seq != lastSeq + 1) return null; // missed one: wait for a key frame
            if (key || dec == null) dec = new H264Decoder();
            lastSeq = seq;
            try {
                if (fb == null) fb = Picture.create(800, 608, ColorSpace.YUV420J).getData();
                Picture p = dec.decodeFrame(ByteBuffer.wrap(data), fb);
                if (p == null) return null;
                return last = toRgb(p);
            } catch (Throwable t) {
                lastSeq = Integer.MIN_VALUE;
                return null;
            }
        }

        public synchronized int[] last() {
            return last;
        }

        public synchronized void reset() {
            dec = null;
            lastSeq = Integer.MIN_VALUE;
        }
    }

    private static int[] toRgb(Picture p) {
        int w = p.getCroppedWidth() > 0 ? p.getCroppedWidth() : p.getWidth(), h = p.getCroppedHeight() > 0 ? p.getCroppedHeight() : p.getHeight();
        int stride = p.getPlaneWidth(0), cs = p.getPlaneWidth(1);
        byte[] Y = p.getPlaneData(0), U = p.getPlaneData(1), V = p.getPlaneData(2);
        int[] out = new int[2 + w * h];
        out[0] = w;
        out[1] = h;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int yy = Y[y * stride + x] + 128;
                int ci = (y >> 1) * cs + (x >> 1);
                int u = U[ci], v = V[ci];
                int r = yy + ((359 * v) >> 8), g = yy - ((88 * u + 183 * v) >> 8), b = yy + ((454 * u) >> 8);
                out[2 + y * w + x] = clamp(r) << 16 | clamp(g) << 8 | clamp(b);
            }
        return out;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }

    private static int clampS(int v) {
        return v < -128 ? -128 : Math.min(127, v);
    }
}
