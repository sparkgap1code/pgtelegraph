package com.sparkgap.pgtelegraph.client;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.BlockPos;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public address: a microphone against an audio amplifier. Its speech (8 kHz) is kept per amplifier
 * in a ring every speaker on that amplifier reads at its own pace, mixed over the radio the amplifier
 * also carries - the music dips under the voice and comes back up after it (a ducker).
 */
public final class PaClient {
    private PaClient() {}

    private static final int RING = 8000 * 8, JITTER = 800;

    static final class Buf {
        final float[] ring = new float[RING];
        volatile long w;
        volatile long lastMs;
    }

    private static final Map<BlockPos, Buf> BUFS = new ConcurrentHashMap<>();

    /** 50 ms of mu-law speech into the amplifier at {@code amp} */
    static void add(BlockPos amp, byte[] mulaw) {
        Buf b = BUFS.computeIfAbsent(amp.immutable(), k -> new Buf());
        synchronized (b) {
            for (byte m : mulaw) {
                b.ring[(int) (b.w % RING)] = com.sparkgap.pgtelegraph.media.MuLaw.decode(m) / 32768f;
                b.w++;
            }
        }
        b.lastMs = System.currentTimeMillis();
    }

    /** one speaker's own read position in an amplifier's speech */
    static final class Reader {
        private final BlockPos amp;
        private double r = -1;
        private long waitFrom = -1;

        Reader(BlockPos amp) {
            this.amp = amp;
        }

        boolean talking() {
            Buf b = BUFS.get(amp);
            return b != null && System.currentTimeMillis() - b.lastMs < 1000;
        }

        /** the next sample at the audio rate */
        double next() {
            Buf b = BUFS.get(amp);
            if (b == null) return 0;
            long w = b.w;
            if (r < 0) { // waiting for 100 ms of speech before starting (jitter buffer)
                if (waitFrom < 0) waitFrom = w;
                if (w - waitFrom < JITTER) return 0;
                r = w - JITTER;
                waitFrom = -1;
            }
            if (w - r > RING / 2) r = w - JITTER; // fell far behind: catch up
            if (r + 1 >= w) { // ran dry: wait for the next words
                r = -1;
                waitFrom = w;
                return 0;
            }
            int i = (int) ((long) r % RING), j = (i + 1) % RING;
            double f = r - Math.floor(r);
            double s = b.ring[i] + (b.ring[j] - b.ring[i]) * f;
            r += 8000.0 / ClientRadio.FA;
            return s * 1.5;
        }
    }

    /** a speaker's sound: the radio (if any) with the microphone mixed over it */
    static final class Mix implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(ClientRadio.FA, 16, 1, true, false);
        private final AudioStream radio;
        private final Reader voice;
        private double duck = 1;

        Mix(AudioStream radio, BlockPos amp) {
            this.radio = radio;
            this.voice = new Reader(amp);
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer read(int size) throws java.io.IOException {
            ByteBuffer in = radio == null ? null : radio.read(size);
            int n = in == null ? ClientRadio.FA / 20 : in.remaining() / 2;
            ByteBuffer out = BufferUtils.createByteBuffer(n * 2);
            boolean talking = voice.talking();
            for (int k = 0; k < n; k++) {
                double x = 0;
                if (in != null) {
                    int lo = in.get() & 0xFF, hi = in.get();
                    x = (short) ((hi << 8) | lo) / 32768.0;
                }
                duck += ((talking ? 0.15 : 1) - duck) * (talking ? 1.0 / (0.2 * ClientRadio.FA) : 1.0 / (2.0 * ClientRadio.FA));
                double y = x * duck + voice.next() * 0.9;
                short v = (short) Math.max(-32767, Math.min(32767, y * 32767));
                out.put((byte) (v & 0xFF)).put((byte) ((v >> 8) & 0xFF));
            }
            out.flip();
            return out;
        }

        @Override
        public void close() throws java.io.IOException {
            if (radio != null) radio.close();
        }
    }
}
