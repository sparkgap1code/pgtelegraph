package com.sparkgap.pgtelegraph.media;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * The picture format of photos and SSTV: 320 × 256 (the Martin / Scottie SSTV frame), 16-bit
 * colour (RGB565, two bytes a pixel, little end first). A row is 640 bytes, a picture 160 KiB.
 */
public final class Pic {
    private Pic() {}

    public static final int W = 320, H = 256, ROW = W * 2, BYTES = ROW * H;

    public static short pack(int r, int g, int b) {
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return (short) (((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3));
    }

    public static void put(byte[] buf, int px, short c) {
        buf[px * 2] = (byte) c;
        buf[px * 2 + 1] = (byte) (c >> 8);
    }

    public static void put(byte[] buf, int px, int r, int g, int b) {
        put(buf, px, pack(r, g, b));
    }

    public static int raw(byte[] buf, int px) {
        return (buf[px * 2] & 255) | (buf[px * 2 + 1] & 255) << 8;
    }

    /** 0xRRGGBB of pixel {@code px} */
    public static int rgb(byte[] buf, int px) {
        int v = raw(buf, px);
        int r = (v >> 11) & 31, g = (v >> 5) & 63, b = v & 31;
        return (r * 255 / 31) << 16 | (g * 255 / 63) << 8 | (b * 255 / 31);
    }

    public static int rgb565(int v) {
        int r = (v >> 11) & 31, g = (v >> 5) & 63, b = v & 31;
        return (r * 255 / 31) << 16 | (g * 255 / 63) << 8 | (b * 255 / 31);
    }

    /** a 64 × 64 RGB332 picture from before (old photos), scaled up to the frame */
    public static byte[] fromLegacy(byte[] old) {
        byte[] out = new byte[BYTES];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int sx = x * 64 / W, sy = y * 64 / H, i = sy * 64 + sx;
                int v = i < old.length ? old[i] : 0;
                int r = (v >> 5) & 7, g = (v >> 2) & 7, b = v & 3;
                put(out, y * W + x, r * 255 / 7, g * 255 / 7, b * 255 / 3);
            }
        }
        return out;
    }

    public static byte[] row(byte[] img, int y) {
        byte[] r = new byte[ROW];
        if (img != null && img.length >= (y + 1) * ROW) System.arraycopy(img, y * ROW, r, 0, ROW);
        return r;
    }

    public static byte[] deflate(byte[] in) {
        Deflater d = new Deflater(6);
        d.setInput(in);
        d.finish();
        ByteArrayOutputStream o = new ByteArrayOutputStream(in.length / 3 + 64);
        byte[] b = new byte[16384];
        while (!d.finished()) o.write(b, 0, d.deflate(b));
        d.end();
        return o.toByteArray();
    }

    /** inflate, refusing anything bigger than {@code max}; null when broken or too big */
    public static byte[] inflate(byte[] in, int max) {
        Inflater f = new Inflater();
        f.setInput(in);
        ByteArrayOutputStream o = new ByteArrayOutputStream(Math.min(max, in.length * 4 + 64));
        byte[] b = new byte[16384];
        try {
            while (!f.finished()) {
                int n = f.inflate(b);
                if (n == 0 && (f.needsInput() || f.needsDictionary())) return null;
                o.write(b, 0, n);
                if (o.size() > max) return null;
            }
            return o.toByteArray();
        } catch (DataFormatException e) {
            return null;
        } finally {
            f.end();
        }
    }
}
