package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.media.SstvMode;
import com.sparkgap.pgtelegraph.sat.SstvMonitorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * What an SSTV receiver's loudspeaker plays: the picture as tones, rebuilt from each line as it is
 * painted - the VIS header at the start of a frame (1900 Hz leader, 1200 Hz break, 30 ms bits at
 * 1100/1300 Hz), then per line a 1200 Hz sync pulse, the 1500 Hz porch and the pixels as pitch
 * (1500 Hz black - 2300 Hz white): Y then R-Y and B-Y for Robot/PD, G B R for Martin/Scottie.
 * Noise rises as the SNR falls, with FM clicks below about 11 dB.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT)
public final class SstvAudio {
    private SstvAudio() {}

    static final int RATE = 24000;
    /** play no more than this much behind (samples): a lag must not leave the tones running late */
    private static final int MAX_QUEUE = RATE * 3;

    private static final class Chan {
        final BlockPos pos;
        /** frequency per sample (Hz); NaN = silence */
        float[] buf = new float[RATE * 4];
        int head, tail; // ring: write at tail, read at head (guarded by this)
        volatile float snr = 30;
        volatile long seen = System.currentTimeMillis();
        int lastRow = -1;
        Sound sound;

        Chan(BlockPos pos) {
            this.pos = pos;
        }

        synchronized int queued() {
            return Math.floorMod(tail - head, buf.length);
        }

        synchronized void tone(double hz, double ms) {
            int n = (int) Math.round(ms * RATE / 1000);
            if (queued() + n >= buf.length - 1) return;
            for (int i = 0; i < n; i++) {
                buf[tail] = (float) hz;
                tail = (tail + 1) % buf.length;
            }
        }

        synchronized float next() {
            if (head == tail) return Float.NaN;
            float f = buf[head];
            head = (head + 1) % buf.length;
            return f;
        }

        synchronized void trim(int keep) {
            int q = queued();
            if (q > keep) head = (head + q - keep) % buf.length;
        }
    }

    private static final Map<BlockPos, Chan> CHANS = new HashMap<>();

    /** a line painted on a monitor here: queue its sound */
    public static void row(SstvMonitorBlockEntity be, int row, int h, byte[] data) {
        int w = data.length / 2;
        SstvMode m = SstvMode.of(be.mode);
        if (m.w != w || m.h != h) { // the monitor's mode may not have reached us yet: go by the line's size
            for (SstvMode c : SstvMode.values()) if (c.w == w && c.h == h) { m = c; break; }
        }
        Chan c = CHANS.computeIfAbsent(be.getBlockPos().immutable(), Chan::new);
        c.seen = System.currentTimeMillis();
        c.snr = be.snr == 0 ? c.snr : be.snr;
        double lineMs = m.seconds * 1000 / m.h;
        if (row == 0 || row < c.lastRow) vis(c, m);
        // lines the server skipped (more than one a tick) still take their time on the air: repeat this one
        int lines = c.lastRow < 0 || row <= c.lastRow ? 1 : Math.min(4, row - c.lastRow);
        c.lastRow = row;
        for (int k = 0; k < lines; k++) line(c, m, data, w, lineMs);
        c.trim(MAX_QUEUE);
        if (c.sound == null || !c.sound.playing()) {
            if (c.sound != null) c.sound.stop2();
            c.sound = new Sound(c);
            Minecraft.getInstance().getSoundManager().play(c.sound);
        }
    }

    private static void vis(Chan c, SstvMode m) {
        c.tone(1900, 300);
        c.tone(1200, 10);
        c.tone(1900, 300);
        c.tone(1200, 30); // start bit
        int code = m.vis, parity = 0;
        for (int b = 0; b < 7; b++) {
            int bit = (code >> b) & 1;
            parity ^= bit;
            c.tone(bit == 1 ? 1100 : 1300, 30);
        }
        c.tone(parity == 1 ? 1100 : 1300, 30);
        c.tone(1200, 30); // stop bit
    }

    private static void line(Chan c, SstvMode m, byte[] data, int w, double lineMs) {
        double sync = Math.min(20, lineMs * 0.05), porch = Math.min(3, lineMs * 0.01);
        c.tone(1200, sync);
        c.tone(1500, porch);
        double left = lineMs - sync - porch;
        if (m.yc) {
            scan(c, data, w, left * 0.5, 0);
            scan(c, data, w, left * 0.25, 1);
            scan(c, data, w, left * 0.25, 2);
        } else {
            scan(c, data, w, left / 3, 3);
            scan(c, data, w, left / 3, 4);
            scan(c, data, w, left / 3, 5);
        }
    }

    /** one component across the line: 0 Y, 1 R-Y, 2 B-Y, 3 G, 4 B, 5 R */
    private static void scan(Chan c, byte[] data, int w, double ms, int comp) {
        double per = ms / w;
        // group pixels so each piece is at least one sample long
        int step = Math.max(1, (int) Math.ceil(1000.0 / RATE / per));
        for (int x = 0; x < w; x += step) {
            int v = ((data[x * 2] & 0xFF) << 8) | (data[x * 2 + 1] & 0xFF);
            double r = ((v >> 11) & 31) / 31.0, g = ((v >> 5) & 63) / 63.0, b = (v & 31) / 31.0;
            double y = 0.299 * r + 0.587 * g + 0.114 * b;
            double val = switch (comp) {
                case 0 -> y;
                case 1 -> 0.5 + 0.5 * (r - y);
                case 2 -> 0.5 + 0.5 * (b - y);
                case 3 -> g;
                case 4 -> b;
                default -> r;
            };
            c.tone(1500 + 800 * Math.max(0, Math.min(1, val)), per * Math.min(step, w - x));
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        if (CHANS.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean gone = Minecraft.getInstance().level == null;
        CHANS.values().removeIf(c -> {
            if (!gone && (now - c.seen < 4000 || c.queued() > 0)) return false;
            if (c.sound != null) c.sound.stop2();
            return true;
        });
    }

    static final class Sound extends AbstractTickableSoundInstance {
        private final Chan c;
        private final long created = System.currentTimeMillis();

        Sound(Chan c) {
            super(PgTelegraph.BROADCAST_STREAM.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.c = c;
            x = c.pos.getX() + 0.5;
            y = c.pos.getY() + 0.5;
            z = c.pos.getZ() + 0.5;
            looping = false;
            volume = 0.8f;
            attenuation = Attenuation.LINEAR;
        }

        boolean playing() {
            if (isStopped()) return false;
            return System.currentTimeMillis() - created < 2000 || Minecraft.getInstance().getSoundManager().isActive(this);
        }

        void stop2() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (System.currentTimeMillis() - c.seen > 4000 && c.queued() == 0) stop();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, net.minecraft.client.resources.sounds.Sound sound, boolean looping) {
            return CompletableFuture.completedFuture(new Stream(c));
        }
    }

    /** FM-demodulated tones: a phase-continuous oscillator plus the receiver's noise */
    static final class Stream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);
        private final Chan c;
        private final Random rnd = new Random();
        private double phase, lp;
        private int click;

        Stream(Chan c) {
            this.c = c;
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer read(int size) {
            int n = RATE / 20; // 50 ms a buffer
            ByteBuffer out = BufferUtils.createByteBuffer(n * 2);
            double snr = c.snr;
            double noise = Math.min(0.7, 2.5 * Math.pow(10, -snr / 20));
            double clicks = Math.max(0, Math.min(0.3, (11 - snr) / 30));
            for (int i = 0; i < n; i++) {
                float f = c.next();
                double s;
                if (Float.isNaN(f)) {
                    s = 0; // squelched between pictures
                } else {
                    phase += 2 * Math.PI * f / RATE;
                    if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
                    double wn = rnd.nextGaussian();
                    lp += 0.5 * (wn - lp);
                    s = 0.45 * Math.sin(phase) + noise * 0.4 * lp;
                    if (click > 0) {
                        click--;
                        s += (rnd.nextDouble() * 2 - 1) * 0.5;
                    } else if (rnd.nextDouble() < clicks / 200) click = 10 + rnd.nextInt(40);
                }
                short v = (short) Math.max(-32767, Math.min(32767, s * 30000));
                out.put((byte) (v & 0xFF)).put((byte) ((v >> 8) & 0xFF));
            }
            out.flip();
            return out;
        }

        @Override
        public void close() {}
    }
}
