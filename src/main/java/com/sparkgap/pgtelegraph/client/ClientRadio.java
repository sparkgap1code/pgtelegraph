package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.broadcast.AudioStore;
import com.sparkgap.pgtelegraph.radio.RxChannel;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The loudspeaker of a radio set (broadcast radio, station receiver, handheld) as a real receiver, sample by sample.
 * <p>
 * The server tells the radio which signals are in its channel and how each arrives: median power
 * after path loss (ITU-style ground wave / sky wave / line of sight with knife-edge terrain), how
 * much of it came by sky wave and that sky wave's time and Doppler spread, and which ticks each
 * was on the air; plus the noise densities (external and the receiver's own) and how much of the
 * external noise is lightning. From that this stream rebuilds the radio-frequency signal at
 * complex baseband around the dial and demodulates it like a set would:
 * <ol>
 *   <li><b>Transmitters.</b> Every station runs its programme (joined where that station is now -
 *       the station is the clock; live microphone speech mixed over it) through a broadcast
 *       processor (AGC-compressor and peak clipper), then modulates: AM = carrier x (1 + m x audio),
 *       over-modulation pinching the carrier off; FM = 75 kHz deviation after 75 us pre-emphasis.
 *       A transmitter with no programme is a bare carrier, keyed exactly as it was on the air
 *       (CW comes through in Morse), with 5 ms keying shaping.</li>
 *   <li><b>The path.</b> Ground wave arrives steady; sky wave follows the Watterson model
 *       (ITU-R F.1487): two modes a little apart in time, each with its own Rayleigh-fading
 *       complex gain whose speed is the Doppler spread - so fading is frequency-selective, the
 *       carrier can vanish while the sidebands stay and AM distorts. Both together: Rician.</li>
 *   <li><b>Noise.</b> Gaussian receiver and galactic noise; atmospheric noise as what it is -
 *       impulses from lightning the world over (Poisson arrivals, log-normal strength) at the
 *       right average power; in a thunderstorm, crashes from strokes nearby.</li>
 *   <li><b>The receiver.</b> A front end that overloads on very strong signals (mixing products);
 *       AM: 9 kHz IF filter, envelope detector, AGC; FM: 200 kHz IF filter, limiter-discriminator,
 *       de-emphasis and soft mute. Beat whistles, capture effect, FM threshold clicks and flutter
 *       all come out of this by themselves.</li>
 * </ol>
 */
final class ClientRadio {
    private ClientRadio() {}

    static final int FA = 32000;        // audio rate = AM channel rate
    static final int OS = 8;            // FM runs at 8 x 32 kHz = 256 kHz
    static final double FS_FM = FA * OS;
    static final double DEV = 75_000;   // FM peak deviation
    static final int RING = 1024;       // delay line for the sky-wave second mode
    /** 63-tap Hamming-windowed Hilbert transformer: the quadrature half of an SSB signal */
    static final double[] HILBERT = new double[63];

    static {
        int m = HILBERT.length / 2;
        for (int n = -m; n <= m; n++) {
            double h = n % 2 == 0 ? 0 : 2 / (Math.PI * n);
            double w = 0.54 + 0.46 * Math.cos(Math.PI * n / m);
            HILBERT[n + m] = h * w;
        }
    }
    /** front-end overload: about -20 dBm at the input (as an amplitude, sqrt(mW)) */
    static final double SAT = 0.1;

    // ============================================================ live speech from the server
    /** microphone speech carried by the station a radio is hearing: 8 kHz samples */
    static final class Voice {
        final ConcurrentLinkedQueue<float[]> q = new ConcurrentLinkedQueue<>();
        volatile long lastMs;
        float[] cur;
        int idx;
        double frac;
        boolean playing;
        int queued;
    }

    private static final Map<BlockPos, Voice> VOICES = new ConcurrentHashMap<>();

    /** 50 ms of mu-law speech that a radio's station is carrying */
    static void voice(BlockPos rx, byte[] mulaw) {
        float[] f = new float[mulaw.length];
        for (int i = 0; i < f.length; i++) f[i] = com.sparkgap.pgtelegraph.media.MuLaw.decode(mulaw[i]) / 32768f;
        Voice v = VOICES.computeIfAbsent(rx.immutable(), k -> new Voice());
        v.q.add(f);
        v.lastMs = System.currentTimeMillis();
    }

    // ============================================================ programme audio
    /** a station's programme audio at FA, joined where the station is now */
    abstract static class Feed {
        /** seconds into the programme of the next sample */
        double secs;
        /** playback speed: nudged a few percent to follow the station clock instead of jumping */
        volatile double rate = 1;

        abstract float next();

        abstract boolean ready();

        void close() {}
    }

    /** imported recording: 8-bit 11 kHz, looping */
    static final class PcmFeed extends Feed {
        private final byte[] pcm;
        private double idx;
        private static final double STEP = AudioStore.RATE / (double) FA;

        PcmFeed(byte[] pcm, double startSecs) {
            this.pcm = pcm;
            this.secs = startSecs;
            this.idx = (startSecs * AudioStore.RATE) % Math.max(1, pcm.length);
        }

        @Override
        float next() {
            int n = pcm.length;
            if (n == 0) return 0;
            int i = (int) idx;
            double fr = idx - i;
            float a = pcm[i % n] / 128f, b = pcm[(i + 1) % n] / 128f;
            idx += STEP * rate;
            if (idx >= n) idx -= n;
            secs += rate / FA;
            return (float) (a + (b - a) * fr);
        }

        @Override
        boolean ready() {
            return true;
        }
    }

    /** a music disc: the game's own OGG stream, decoded ahead to the station's position in the background */
    static final class StreamFeed extends Feed {
        private volatile AudioStream src;
        private int channels = 1;
        private double step = 1, frac;
        private float prev, cur;
        private ByteBuffer buf;
        private boolean ended;
        volatile boolean failed;

        StreamFeed(ResourceLocation soundEvent, SoundBufferLibrary lib, java.util.function.DoubleSupplier stationSecs) {
            CompletableFuture.runAsync(() -> {
                try {
                    var ev = Minecraft.getInstance().getSoundManager().getSoundEvent(soundEvent);
                    if (ev == null) {
                        failed = true;
                        com.sparkgap.pgtelegraph.PgTelegraph.LOGGER.warn("[pgtelegraph] radio: no sound event {}", soundEvent);
                        return;
                    }
                    var sound = ev.getSound(RandomSource.create());
                    AudioStream s = lib.getStream(sound.getPath(), false).join();
                    AudioFormat f = s.getFormat();
                    int ch = Math.max(1, f.getChannels());
                    double rate = f.getSampleRate();
                    // catch up with the station: skip what it has played, again until we are there
                    double done = 0;
                    for (int round = 0; round < 6; round++) {
                        double target = stationSecs.getAsDouble();
                        long bytes = (long) ((target - done) * rate) * ch * 2;
                        if (bytes < rate * ch * 2 * 0.03) break;
                        while (bytes > 0) {
                            ByteBuffer b = s.read((int) Math.min(bytes, 1 << 16));
                            if (b == null || !b.hasRemaining()) break;
                            bytes -= b.remaining();
                        }
                        done = target;
                    }
                    channels = ch;
                    step = rate / FA;
                    secs = done;
                    src = s;
                } catch (Exception ex) {
                    failed = true;
                    com.sparkgap.pgtelegraph.PgTelegraph.LOGGER.warn("[pgtelegraph] radio: could not load {}: {}", soundEvent, ex.toString());
                }
            }, Util.backgroundExecutor());
        }

        private float sample() throws IOException {
            if (buf == null || buf.remaining() < 2 * channels) {
                buf = src.read(8192 * channels);
                if (buf == null || buf.remaining() < 2 * channels) {
                    ended = true;
                    return 0;
                }
                buf.order(ByteOrder.LITTLE_ENDIAN);
            }
            float s = 0;
            for (int c = 0; c < channels; c++) s += buf.getShort() / 32768f;
            return s / channels;
        }

        @Override
        float next() {
            if (src == null || ended) return 0;
            try {
                frac += step * rate;
                while (frac >= 1) {
                    frac -= 1;
                    prev = cur;
                    cur = sample();
                }
            } catch (IOException e) {
                ended = true;
            }
            secs += rate / FA;
            return (float) (prev + (cur - prev) * frac);
        }

        @Override
        boolean ready() {
            return src != null;
        }

        @Override
        void close() {
            try {
                if (src != null) src.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ============================================================ one station in the channel
    static final class Slot {
        String key = "";
        long start;
        int len;
        int df;
        double amp, target;
        // path: sky share, Watterson parameters and tap gains (A: first mode, B: delayed mode)
        double sky, delayMs, doppler;
        long fadeSeed;
        /** the transmitting antenna's bandwidth as a low-pass on the modulation envelope (single tuned circuit) */
        double bwA = 1, bwY = 1;
        /** what the station puts on its carrier (RadioWorld.MOD_*) */
        int modulation;
        /** audio history for the Hilbert transformer (SSB) */
        final double[] hist = new double[HILBERT.length];
        int histPos;
        double gAr, gAi, gBr, gBi, nAr, nAi;
        final double[] ringI = new double[RING], ringQ = new double[RING];
        int ringPos;
        // carrier
        double c = 1, s = 0, phase;
        // keying, as the transmitter is keyed right now, with 5 ms shaping
        boolean keyed = true;
        double keyEnv = 1;
        // transmitter audio processing
        double procEnv = 0.1, pre, duck = 1;
        boolean top;
        Feed feed, pending;
        long failedAt;

        boolean matches(RxChannel.Sig g) {
            return key.equals(g.key()) && Math.abs(df - g.df()) < 400;
        }
    }

    // ============================================================ the receiver
    /**
     * A set's loudspeaker. Reads its {@link RxChannel} every audio block and runs the demodulator its
     * mode asks for: CW (IF filter + BFO product detector + AGC), AM (IF filter + envelope + AGC),
     * wide FM (broadcast) or narrow FM (with squelch).
     */
    static final class RadioStream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(FA, 16, 1, true, false);
        private static final double BFO = 700;
        /** the channel to simulate; a shared group points it at whichever member read last */
        volatile RxChannel ch;
        volatile BlockPos voicePos;
        private final SoundBufferLibrary lib;
        private final SplittableRandom rnd = new SplittableRandom();
        private List<Slot> slots = new ArrayList<>();
        private long t;
        private int mode = -1, bw;
        private final Biquad[] ifI = {new Biquad(), new Biquad()}, ifQ = {new Biquad(), new Biquad()};
        private final Biquad[] fmI = {new Biquad(), new Biquad()}, fmQ = {new Biquad(), new Biquad()};
        private double agc, dcIn, dcOut, bfo, side;
        private double pI = 1e-9, pQ, deEmph, cnrPow, mute = 1, gate = 1;
        private double impAmp, impDecay, crash, crashDecay;
        private final double[] nz = new double[2];

        RadioStream(RxChannel ch, BlockPos voicePos, SoundBufferLibrary lib) {
            this.ch = ch;
            this.voicePos = voicePos;
            this.lib = lib;
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        /** (re)design the IF filters for the set's mode and bandwidth */
        private void design(int m, int b) {
            mode = m;
            bw = b;
            agc = 0;
            double half = Math.max(150, Math.min(m == RxChannel.FM_WIDE ? 100_000 : 0.45 * FA, b / 2.0));
            if (m == RxChannel.SSB) half = Math.min(half, 1200); // 300-2700 Hz around the 1.5 kHz centre
            double rate = m == RxChannel.FM_WIDE ? FS_FM : FA;
            Biquad[][] f = m == RxChannel.FM_WIDE ? new Biquad[][]{fmI, fmQ} : new Biquad[][]{ifI, ifQ};
            for (Biquad[] pair : f) {
                pair[0].lowPass(half, rate, 0.5412);
                pair[1].lowPass(half, rate, 1.3066);
            }
        }

        private static double stationSecs(long start, int len) {
            var lvl = Minecraft.getInstance().level;
            if (lvl == null || len <= 0) return 0;
            return Math.floorMod(lvl.getGameTime() - start, (long) len) / 20.0;
        }

        /** one line for the log: what this speaker is simulating right now */
        String debug() {
            StringBuilder sb = new StringBuilder();
            sb.append("mode ").append(mode).append(" bw ").append(bw).append(" open ").append(ch.open).append(" gate ")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", gate)).append(" sigs ").append(ch.signals.size())
                    .append(" slots ").append(slots.size())
                    .append(String.format(java.util.Locale.ROOT, " noise %.0f/%.0f dBm/Hz", ch.extDbmHz, ch.rxDbmHz));
            for (Slot sl : slots)
                sb.append(" [").append(sl.key.isEmpty() ? "-" : sl.key).append(" mod ").append(sl.modulation)
                        .append(String.format(java.util.Locale.ROOT, " %.0f dBm df %d", 10 * Math.log10(Math.max(1e-30, sl.target * sl.target)), sl.df))
                        .append(" feed ").append(sl.feed == null ? (sl.pending == null ? "none" : "loading") : sl.feed.ready() ? "ok" : "notready")
                        .append(sl.pending instanceof StreamFeed sf && sf.failed ? " FAILED" : "").append("]");
            return sb.toString();
        }

        private void reconcile() {
            List<RxChannel.Sig> sigs = ch.signals;
            boolean wide = ch.mode == RxChannel.FM_WIDE;
            double reach = wide ? 120_000 : 0.45 * FA;
            List<Slot> next = new ArrayList<>();
            boolean first = true;
            for (RxChannel.Sig g : sigs) {
                if (Math.abs(g.df()) > reach) continue; // outside the simulated band
                Slot sl = null;
                for (Slot o : slots) if (!next.contains(o) && o.matches(g)) sl = o;
                if (sl == null) {
                    sl = new Slot();
                    sl.key = g.key();
                    sl.phase = rnd.nextDouble() * Math.PI * 2;
                    sl.c = Math.cos(sl.phase);
                    sl.s = Math.sin(sl.phase);
                    sl.gAr = rnd.nextGaussian() * 0.7;
                    sl.gAi = rnd.nextGaussian() * 0.7;
                    sl.gBr = rnd.nextGaussian() * 0.7;
                    sl.gBi = rnd.nextGaussian() * 0.7;
                    sl.amp = Math.sqrt(Math.pow(10, g.dbm() / 10));
                    sl.keyed = g.on();
                    sl.keyEnv = g.on() ? 1 : 0;
                }
                sl.start = g.start();
                sl.len = g.len();
                sl.df = g.df();
                sl.sky = g.skyShare();
                sl.delayMs = g.delayMs();
                sl.doppler = Math.max(0.05, g.dopplerHz());
                sl.fadeSeed = g.fadeSeed();
                sl.modulation = g.modulation();
                double fc = g.antBwHz() / 2; // the envelope of a single tuned circuit: corner at half its bandwidth
                sl.bwA = fc <= 0 || fc > 12000 ? 1 : 1 - Math.exp(-2 * Math.PI * fc / FA);
                sl.target = Math.sqrt(Math.pow(10, g.dbm() / 10)); // sqrt(mW): an amplitude
                sl.keyed = g.on();
                sl.top = first && Math.abs(g.df()) <= Math.max(bw / 2, 500);
                if (sl.top) first = false;
                syncFeed(sl);
                next.add(sl);
            }
            for (Slot o : slots) if (!next.contains(o)) closeFeeds(o);
            slots = next;
        }

        private static void closeFeeds(Slot o) {
            if (o.feed != null) o.feed.close();
            if (o.pending != null) o.pending.close();
        }

        /** keep the slot's programme audio where the station is (start it, or re-join after drift / a loop) */
        private void syncFeed(Slot sl) {
            if (sl.key.isEmpty() || sl.len <= 0) return;
            if (sl.pending instanceof StreamFeed sf && sf.failed && System.currentTimeMillis() - sl.failedAt > 3000) {
                sl.failedAt = System.currentTimeMillis(); // try again in a few seconds
                sl.pending = null;
            }
            if (sl.pending != null && sl.pending.ready()) {
                if (sl.feed != null) sl.feed.close();
                sl.feed = sl.pending;
                sl.pending = null;
            }
            double want = stationSecs(sl.start, sl.len);
            if (sl.feed != null && sl.feed.ready()) {
                double lenS = sl.len / 20.0, e = want - sl.feed.secs;
                e -= lenS * Math.rint(e / lenS); // the shorter way round a looping record
                // the station clock is game time, which a lagging server runs slow (and skips when far behind):
                // follow it by playing up to 3 % faster or slower rather than jumping back - only a big gap jumps
                if (Math.abs(e) < 15) {
                    sl.feed.rate = 1 + Math.max(-0.03, Math.min(0.03, e * 0.01));
                    return;
                }
            } else if (sl.feed != null || sl.pending != null) return; // still loading
            if (sl.pending != null) return;
            final long start = sl.start;
            final int len = sl.len;
            if (sl.key.startsWith("a:")) {
                byte[] pcm = ClientAudio.ready(sl.key.substring(2));
                if (pcm != null) sl.pending = new PcmFeed(pcm, want);
            } else if (sl.key.startsWith("s:")) {
                ResourceLocation id = ResourceLocation.tryParse(sl.key.substring(2));
                if (id != null) sl.pending = new StreamFeed(id, lib, () -> stationSecs(start, len));
            }
            if (sl.pending != null && sl.pending.ready()) {
                sl.feed = sl.pending;
                sl.pending = null;
            }
        }

        @Override
        public ByteBuffer read(int size) {
            reconcile();
            var lvl = Minecraft.getInstance().level;
            if (lvl != null) {
                double world = lvl.getGameTime() / 20.0;
                if (fadeSec < 0 || Math.abs(world - fadeSec) > 0.5) fadeSec = world; // follow the world clock
            }
            if (ch.mode != mode || ch.bwHz != bw) design(ch.mode, ch.bwHz);
            int n = Math.max(64, Math.min(8192, size / 2));
            ByteBuffer out = BufferUtils.createByteBuffer(n * 2);
            double ext = Math.pow(10, ch.extDbmHz / 10), rx = Math.pow(10, ch.rxDbmHz / 10); // mW per Hz
            double atm = Math.max(0, Math.min(0.95, ch.atmospheric));
            boolean storm = ch.thunder && mode != RxChannel.FM_WIDE;
            Voice voice = voicePos == null ? null : VOICES.get(voicePos);
            boolean open = ch.open, sidetone = ch.sidetone;
            for (int i = 0; i < n; i++) {
                double y;
                if (sidetone) { // our own set is keyed: its sidetone, the receiver muted
                    side += 2 * Math.PI * BFO / FA;
                    y = 0.3 * Math.sin(side);
                } else {
                    y = switch (mode) {
                        case RxChannel.FM_WIDE -> fmWide(ext, rx, atm, voice);
                        case RxChannel.FM_NARROW -> fmNarrow(ext, rx, atm, storm, voice);
                        case RxChannel.CW -> cw(ext, rx, atm, storm);
                        case RxChannel.SSB -> ssb(ext, rx, atm, storm, voice);
                        default -> am(ext, rx, atm, storm, voice);
                    };
                }
                // squelch gate with a short fade
                gate += ((open || sidetone ? 1 : 0) - gate) * 0.002;
                y *= gate;
                short v = (short) Math.max(-32767, Math.min(32767, y * 26000));
                out.put((byte) (v & 0xFF)).put((byte) ((v >> 8) & 0xFF));
                t++;
                fadeSec += 1.0 / FA;
            }
            out.flip();
            return out;
        }

        // ------------------------------------------------------------ transmitter side
        /** the station's programme audio this sample, live speech mixed over the record */
        private double programme(Slot sl, Voice voice) {
            double x = sl.feed != null && sl.feed.ready() ? sl.feed.next() : 0;
            // the announcer talks over the record: the music dips under the voice in about a fifth of a second and,
            // a second after the last word, comes back up over two seconds (a broadcast desk ducker)
            boolean talking = sl.top && voice != null && System.currentTimeMillis() - voice.lastMs < 1000;
            sl.duck += ((talking ? 0.15 : 1) - sl.duck) * (talking ? 1.0 / (0.2 * FA) : 1.0 / (2.0 * FA));
            x *= sl.duck;
            if (sl.top && voice != null) x += voiceSample(voice);
            // broadcast processor: slow AGC-compressor to a steady loudness, then a soft peak clipper
            double a = Math.abs(x);
            sl.procEnv += (a - sl.procEnv) * (a > sl.procEnv ? 0.003 : 0.00007);
            double gain = Math.min(6, 0.35 / Math.max(0.02, sl.procEnv));
            return Math.tanh(x * gain * 1.3) / Math.tanh(1.3);
        }

        private static double voiceSample(Voice v) {
            if (!v.playing) {
                int q = 0;
                for (float[] f : v.q) q += f.length;
                if (q < 800) return 0; // fill 100 ms of jitter buffer first
                v.playing = true;
            }
            if (v.cur == null || v.idx + 1 >= v.cur.length) {
                float[] nx = v.q.poll();
                if (nx == null) {
                    v.playing = false;
                    v.cur = null;
                    return 0;
                }
                v.cur = nx;
                v.idx = 0;
            }
            double s = v.cur[v.idx] + (v.cur[v.idx + 1] - v.cur[v.idx]) * v.frac;
            v.frac += 8000.0 / FA;
            while (v.frac >= 1) {
                v.frac -= 1;
                v.idx++;
            }
            return s * 1.5;
        }

        /** on/off keying as the transmitter is keyed now, 5 ms raised-cosine rise and fall */
        private static double keying(Slot sl) {
            // 5 ms shaping; a hard-keyed transmitter switches in 0.15 ms and clicks
            double step = 1.0 / ((sl.modulation == com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_CLICKY ? 0.00015 : 0.005) * FA);
            sl.keyEnv = sl.keyed ? Math.min(1, sl.keyEnv + step) : Math.max(0, sl.keyEnv - step);
            return 0.5 - 0.5 * Math.cos(Math.PI * sl.keyEnv);
        }

        // ------------------------------------------------------------ the path
        private final double[] taps = new double[4];
        /** fading clock (seconds of game time), kept in step with the world */
        private double fadeSec = -1;

        /** the Watterson tap gains now - the same deterministic fading the server uses for the OUT contact */
        private void fade(Slot sl, double rate) {
            if (sl.sky <= 0) return;
            com.sparkgap.pgtelegraph.radio.Fading.taps(sl.fadeSeed, fadeSec, sl.doppler, taps);
            sl.gAr = taps[0];
            sl.gAi = taps[1];
            sl.gBr = taps[2];
            sl.gBi = taps[3];
        }

        /** one station's transmitted complex sample after the path (ground wave + two sky modes); into nAr / nAi */
        private static void path(Slot sl, double xI, double xQ, double rate) {
            sl.ringI[sl.ringPos] = xI;
            sl.ringQ[sl.ringPos] = xQ;
            int d = (int) Math.min(RING - 1, Math.round(sl.delayMs / 1000 * rate));
            int j = (sl.ringPos - d) & (RING - 1);
            sl.ringPos = (sl.ringPos + 1) & (RING - 1);
            double ground = Math.sqrt(Math.max(0, 1 - sl.sky)), sk = Math.sqrt(sl.sky / 2);
            double aR = ground + sk * sl.gAr, aI = sk * sl.gAi;
            double bR = sk * sl.gBr, bI = sk * sl.gBi;
            double dI = sl.ringI[j], dQ = sl.ringQ[j];
            sl.nAr = xI * aR - xQ * aI + dI * bR - dQ * bI;
            sl.nAi = xI * aI + xQ * aR + dI * bI + dQ * bR;
        }

        /** rotate a slot's carrier phasor by its offset for one sample at FA */
        private void advance(Slot sl) {
            double w = 2 * Math.PI * sl.df / FA, cw = Math.cos(w), sw = Math.sin(w);
            double c2 = sl.c * cw - sl.s * sw, s2 = sl.c * sw + sl.s * cw;
            sl.c = c2;
            sl.s = s2;
            if ((t & 1023) == 0) {
                double m = Math.hypot(sl.c, sl.s);
                sl.c /= m;
                sl.s /= m;
            }
        }

        // ------------------------------------------------------------ noise
        /**
         * External + receiver noise for one complex sample at {@code rate}. Receiver and galactic noise
         * are Gaussian; atmospheric noise is lightning the world over: many small impulses (a few
         * thousand a second, log-normal strength) at the right average power - a crackle under the hiss,
         * not a buzz; in a thunderstorm, crashes from strokes nearby.
         */
        private double[] noise(double ext, double rxN, double atm, boolean storm, double rate) {
            double gauss = Math.sqrt((rxN + ext * (1 - atm)) * rate / 2);
            double nI = rnd.nextGaussian() * gauss, nQ = rnd.nextGaussian() * gauss;
            if (atm > 0) {
                final double L = 2500, tau = 0.0001, sigma = 1.1;
                double p = L / rate, sum = 1 / (1 - Math.exp(-2 / (tau * rate)));
                if (rnd.nextDouble() < p) {
                    double mean = ext * atm * rate / (p * sum);
                    double a2 = Math.exp(Math.log(mean) - sigma * sigma / 2 + sigma * rnd.nextGaussian());
                    if (a2 > impAmp * impAmp) impAmp = Math.sqrt(a2);
                    impDecay = Math.exp(-1 / (tau * rate));
                }
                if (impAmp > 0) {
                    nI += rnd.nextGaussian() * impAmp * 0.7071;
                    nQ += rnd.nextGaussian() * impAmp * 0.7071;
                    impAmp *= impDecay;
                    if (impAmp < 1e-15) impAmp = 0;
                }
            }
            if (storm) {
                double base = Math.sqrt(ext * rate);
                if (crash < base && rnd.nextDouble() < 0.4 / rate) { // a stroke nearby: a crash
                    crash = base * (5 + rnd.nextDouble() * 30);
                    crashDecay = Math.exp(-1.0 / (rate * (0.02 + rnd.nextDouble() * 0.12)));
                }
                if (crash > 0) {
                    nI += rnd.nextGaussian() * crash;
                    nQ += rnd.nextGaussian() * crash;
                    crash *= crashDecay;
                }
            }
            nz[0] = nI;
            nz[1] = nQ;
            return nz;
        }

        /** the receiver's front end: amplifiers that overload on very strong input (mixing products) */
        /** RF AGC: the front end's average input level (slow) */
        private double rfEnv;

        private static double overload(double m) {
            return m <= SAT * 0.5 ? 1 : SAT * Math.tanh(m / SAT) / m;
        }

        /**
         * AGC on the IF envelope: 20 ms attack, 0.4 s release, and fed a clipped envelope so a lightning
         * click cannot pump the gain down; limited so noise alone stays a moderate hiss.
         */
        private double agcGain(double env, double floor) {
            int m = ch.agcMode;
            if (m == 2) return 1 / (floor * 4); // AGC off: fixed gain - strong signals blast, weak ones stay faint
            double in = agc > 0 ? Math.min(env, 3 * agc) : env;
            double release = m == 0 ? 0.1 : 0.8; // fast (CW) / slow (voice)
            agc += (in - agc) * (in > agc ? 1.0 / (0.005 * FA) : 1.0 / (release * FA));
            return 1 / Math.max(agc, floor);
        }

        private double nbAvg;
        private int nbHold;

        /**
         * Noise blanker: watches the wide signal before the IF filter and blanks it for a moment when a
         * pulse towers over the average - lightning clicks and ignition hash vanish, steady signals stay.
         */
        private void blank() {
            double m = Math.hypot(nz[0], nz[1]);
            if (nbAvg > 0 && m > 6 * nbAvg) nbHold = (int) (0.0004 * FA) + 1;
            nbAvg += (Math.min(m, 3 * Math.max(nbAvg, 1e-12)) - nbAvg) * 0.002;
            if (nbHold > 0) {
                nbHold--;
                nz[0] = 0;
                nz[1] = 0;
            }
        }

        /** sum of all stations at complex baseband (one FA sample), through the path, plus noise, through the front end and IF */
        private double ssbShift;

        /**
         * One FA sample of the channel at complex baseband: every station modulated its own way (bare
         * or keyed carrier, AM, narrow FM, or upper-sideband SSB with no carrier), through its path, plus
         * noise, through the front end and the IF filter. An SSB set's filter passes 300-2700 Hz above
         * the dial (the filter is centred 1.5 kHz up).
         */
        private double[] channelSample(double ext, double rxN, double atm, boolean storm, Voice voice) {
            double I = 0, Q = 0;
            boolean fadeNow = (t & 63) == 0;
            for (Slot sl : slots) {
                sl.amp += (sl.target - sl.amp) * 0.0004;
                if (fadeNow) fade(sl, FA / 64.0);
                double key = keying(sl);
                double xI, xQ;
                switch (sl.modulation) {
                    case com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_AM -> {
                        double m = key * Math.max(0, 1 + 0.95 * programme(sl, voice)); // over-modulation pinches off
                        sl.bwY += sl.bwA * (m - sl.bwY); // a narrow transmitting antenna rounds the envelope
                        xI = sl.bwY * sl.c;
                        xQ = sl.bwY * sl.s;
                    }
                    case com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_TV -> {
                        // the vision carrier: amplitude-modulated, its 60 Hz field sync heard as a raw buzz
                        double ph = (t % (FA / 60)) / (double) (FA / 60);
                        double video = ph < 0.08 ? 1 : 0.35 + 0.25 * Math.sin(2 * Math.PI * ph * 3);
                        double m = key * video;
                        xI = m * sl.c;
                        xQ = m * sl.s;
                    }
                    case com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_SSB -> {
                        // analytic audio (x + j H{x}) on the suppressed carrier: the upper sideband only
                        double x = programme(sl, voice);
                        sl.hist[sl.histPos] = x;
                        int n = HILBERT.length;
                        double h = 0;
                        for (int k = 0; k < n; k++) h += HILBERT[k] * sl.hist[(sl.histPos - k + n) % n];
                        double re = sl.hist[(sl.histPos - n / 2 + n) % n];
                        sl.histPos = (sl.histPos + 1) % n;
                        double a = key * 1.4;
                        xI = a * (re * sl.c - h * sl.s);
                        xQ = a * (re * sl.s + h * sl.c);
                    }
                    case com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_FM, com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_WFM -> {
                        double x = programme(sl, voice);
                        sl.phase += 2 * Math.PI * 2500 * Math.max(-1, Math.min(1, x)) / FA; // narrow FM, on top of the offset
                        double c = Math.cos(sl.phase), sn = Math.sin(sl.phase);
                        xI = key * (c * sl.c - sn * sl.s);
                        xQ = key * (c * sl.s + sn * sl.c);
                    }
                    default -> {
                        sl.bwY += sl.bwA * (key - sl.bwY);
                        xI = sl.bwY * sl.c;
                        xQ = sl.bwY * sl.s;
                    }
                }
                path(sl, sl.amp * xI, sl.amp * xQ, FA);
                I += sl.nAr;
                Q += sl.nAi;
                advance(sl);
            }
            noise(ext, rxN, atm, storm, FA);
            I += nz[0];
            Q += nz[1];
            // RF AGC: a very strong station (a transmitter next door) turns the front end's gain down before it
            // overloads - so the set gets loud and a little rough instead of the AM being flattened to nothing.
            // With the AGC switched off nothing protects the front end and it does overload.
            double inEnv = Math.hypot(I, Q);
            if (ch.agcMode != 2) {
                rfEnv += (inEnv - rfEnv) * (inEnv > rfEnv ? 1.0 / (0.05 * FA) : 1.0 / (1.0 * FA));
                double rg = Math.min(1, 0.2 * SAT / Math.max(rfEnv, 1e-30));
                I *= rg;
                Q *= rg;
                inEnv *= rg;
            }
            double g = overload(inEnv);
            I *= g;
            Q *= g;
            if (ch.blanker) {
                nz[0] = I;
                nz[1] = Q;
                blank();
                I = nz[0];
                Q = nz[1];
            }
            if (mode == RxChannel.SSB) { // centre the filter 1.5 kHz above the dial
                ssbShift += 2 * Math.PI * 1500 / FA;
                if (ssbShift > Math.PI * 64) ssbShift -= Math.PI * 64;
                double c = Math.cos(ssbShift), sn = Math.sin(ssbShift);
                double i2 = I * c + Q * sn, q2 = Q * c - I * sn;
                i2 = ifI[1].run(ifI[0].run(i2));
                q2 = ifQ[1].run(ifQ[0].run(q2));
                I = i2 * c - q2 * sn;
                Q = q2 * c + i2 * sn;
            } else {
                I = ifI[1].run(ifI[0].run(I));
                Q = ifQ[1].run(ifQ[0].run(Q));
            }
            nz[0] = I;
            nz[1] = Q;
            return nz;
        }

        // ------------------------------------------------------------ AM
        private double am(double ext, double rxN, double atm, boolean storm, Voice voice) {
            double[] z = channelSample(ext, rxN, atm, storm, voice);
            double env = Math.hypot(z[0], z[1]);
            double a = env * agcGain(env, Math.sqrt((ext + rxN) * bw) * 2.5);
            double hp = 0.988 * (dcOut + a - dcIn); // DC block: removes the carrier, leaves the audio
            dcIn = a;
            dcOut = hp;
            return hp * 0.85;
        }

        // ------------------------------------------------------------ CW / FSK: BFO product detector
        private double cw(double ext, double rxN, double atm, boolean storm) {
            double[] z = channelSample(ext, rxN, atm, storm, null);
            double env = Math.hypot(z[0], z[1]);
            double g = agcGain(env, Math.sqrt((ext + rxN) * Math.max(bw, 200)) * 3);
            bfo += 2 * Math.PI * BFO / FA;
            if (bfo > Math.PI * 64) bfo -= Math.PI * 64;
            // Re(z e^{j w t}): a carrier df off the dial comes out as a (700 + df) Hz tone
            return (z[0] * Math.cos(bfo) - z[1] * Math.sin(bfo)) * g * 0.6;
        }

        // ------------------------------------------------------------ SSB: product detector, BFO on the dial
        private double ssb(double ext, double rxN, double atm, boolean storm, Voice voice) {
            double[] z = channelSample(ext, rxN, atm, storm, voice);
            double env = Math.hypot(z[0], z[1]);
            double g = agcGain(env, Math.sqrt((ext + rxN) * Math.max(bw, 500)) * 3);
            // Re(z): a station df off the dial comes out df Hz higher (or lower) - the voice changes pitch
            return z[0] * g * 0.7;
        }

        // ------------------------------------------------------------ narrow FM (2.5 kHz deviation, squelched by the set)
        private double fmNarrow(double ext, double rxN, double atm, boolean storm, Voice voice) {
            double I = 0, Q = 0;
            boolean fadeNow = (t & 63) == 0;
            for (Slot sl : slots) {
                sl.amp += (sl.target - sl.amp) * 0.0004;
                if (fadeNow) fade(sl, FA / 64.0);
                double x = programme(sl, voice);
                // a broadcast (wide FM) station swings +-75 kHz, far past this 12.5 kHz set: it comes out torn and distorted
                double devHz = sl.modulation == com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_WFM ? DEV * 0.7 : 2500;
                sl.phase += 2 * Math.PI * (sl.df + devHz * Math.max(-1, Math.min(1, x))) / FA;
                if (Math.abs(sl.phase) > Math.PI * 64) sl.phase %= Math.PI * 2;
                double a = sl.amp * keying(sl);
                path(sl, a * Math.cos(sl.phase), a * Math.sin(sl.phase), FA);
                I += sl.nAr;
                Q += sl.nAi;
            }
            noise(ext, rxN, atm, storm, FA);
            I += nz[0];
            Q += nz[1];
            double g = overload(Math.hypot(I, Q));
            I = ifI[1].run(ifI[0].run(I * g));
            Q = ifQ[1].run(ifQ[0].run(Q * g));
            double re = I * pI + Q * pQ, im = Q * pI - I * pQ;
            pI = I;
            pQ = Q;
            double f = Math.atan2(im, re) * FA / (2 * Math.PI) / 2500;
            deEmph += (f - deEmph) * 0.45; // ~3 kHz audio low-pass
            return Math.max(-1.2, Math.min(1.2, deEmph * 0.7));
        }

        // ------------------------------------------------------------ wide FM (broadcast)
        private double fmWide(double ext, double rxN, double atm, Voice voice) {
            int k = slots.size();
            double[] dev = new double[k], ke = new double[k];
            for (int j = 0; j < k; j++) {
                Slot sl = slots.get(j);
                double x = programme(sl, voice);
                double pre = x + (x - sl.pre) * 2.4; // 75 us pre-emphasis at 32 kHz
                sl.pre = x;
                // a narrow FM station on a broadcast set: only 2.5 kHz of its 75 kHz scale - faint (as it really is)
                dev[j] = Math.max(-1, Math.min(1, pre * 0.7)) * (sl.modulation == com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_FM ? 2500 : DEV);
                ke[j] = keying(sl);
                sl.amp += (sl.target - sl.amp) * 0.0004;
                if ((t & 63) == 0) fade(sl, FA / 64.0);
            }
            double sum = 0;
            for (int o = 0; o < OS; o++) {
                double I = 0, Q = 0;
                for (int j = 0; j < k; j++) {
                    Slot sl = slots.get(j);
                    sl.phase += 2 * Math.PI * (sl.df + dev[j]) / FS_FM;
                    if (Math.abs(sl.phase) > Math.PI * 64) sl.phase %= Math.PI * 2;
                    double a = sl.amp * ke[j];
                    path(sl, a * Math.cos(sl.phase), a * Math.sin(sl.phase), FS_FM);
                    I += sl.nAr;
                    Q += sl.nAi;
                }
                noise(ext, rxN, atm, false, FS_FM);
                I += nz[0];
                Q += nz[1];
                double g = overload(Math.hypot(I, Q));
                I = fmI[1].run(fmI[0].run(I * g));
                Q = fmQ[1].run(fmQ[0].run(Q * g));
                cnrPow += (I * I + Q * Q - cnrPow) * 0.00002;
                double re = I * pI + Q * pQ, im = Q * pI - I * pQ; // limiter-discriminator
                sum += Math.atan2(im, re);
                pI = I;
                pQ = Q;
            }
            double f = sum / OS * FS_FM / (2 * Math.PI) / DEV;
            deEmph += (f - deEmph) * 0.34; // 75 us de-emphasis at 32 kHz
            // soft mute: a weak, noisy station is turned down instead of blasting hiss
            double cnrDb = 10 * Math.log10(Math.max(1e-30, cnrPow) / ((ext + rxN) * 200_000));
            double want = Math.max(0.3, Math.min(1, (cnrDb - 2) / 12));
            mute += (want - mute) * 0.0005;
            return Math.max(-1.2, Math.min(1.2, deEmph * 0.9 * mute));
        }

        @Override
        public void close() {
            for (Slot o : slots) closeFeeds(o);
        }
    }

    /** RBJ biquad (direct form I) */
    static final class Biquad {
        private double b0, b1, b2, a1, a2, x1, x2, y1, y2;

        void lowPass(double hz, double rate, double q) {
            double w = 2 * Math.PI * hz / rate, cs = Math.cos(w), al = Math.sin(w) / (2 * q), a0 = 1 + al;
            b0 = (1 - cs) / 2 / a0;
            b1 = (1 - cs) / a0;
            b2 = b0;
            a1 = -2 * cs / a0;
            a2 = (1 - al) / a0;
        }

        double run(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }

    // ============================================================ loudspeakers
    /** a set's speaker: positional for blocks, in your head for the handheld */
    static final class RxSound extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private final RxChannel ch;
        private final BlockPos voicePos;
        private final java.util.function.BooleanSupplier alive;
        private final float vol;
        private final boolean handheld;
        private final BlockPos pos;

        private final long created = System.currentTimeMillis();

        /**
         * still coming out of the speaker? The sound engine forgets every sound when it restarts (the audio
         * device changed, F3+T) without stopping our instance, so ask the engine - after a moment's grace
         * for a sound that has only just been started
         */
        boolean playing() {
            if (isStopped()) return false;
            return System.currentTimeMillis() - created < 2000 || Minecraft.getInstance().getSoundManager().isActive(this);
        }

        /** give it up (the engine dropped it; a new one takes its place) */
        void drop() {
            stop();
        }

        /** where the set is: the block, or the player holding the handheld */
        BlockPos where() {
            var p = Minecraft.getInstance().player;
            return handheld ? (p == null ? BlockPos.ZERO : p.blockPosition()) : pos;
        }

        RxSound(RxChannel ch, BlockPos pos, boolean handheld, java.util.function.BooleanSupplier alive, float vol) {
            super(com.sparkgap.pgtelegraph.PgTelegraph.BROADCAST_STREAM.get(),
                    handheld ? net.minecraft.sounds.SoundSource.PLAYERS : net.minecraft.sounds.SoundSource.RECORDS,
                    net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            this.ch = ch;
            this.voicePos = handheld ? com.sparkgap.pgtelegraph.media.VoiceAir.EARPIECE : pos;
            this.alive = alive;
            this.vol = vol;
            this.handheld = handheld;
            this.pos = pos;
            looping = false;
            volume = vol;
            if (handheld) {
                relative = true;
                attenuation = Attenuation.NONE;
                x = y = z = 0;
            } else {
                x = pos.getX() + 0.5;
                y = pos.getY() + 0.5;
                z = pos.getZ() + 0.5;
            }
        }

        @Override
        public void tick() {
            if (!alive.getAsBoolean()) stop();
            else volume = !handheld && muted(pos) ? 0 : vol;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        /** an audio amplifier takes this set's audio: only the PA speakers sound */
        private static boolean muted(BlockPos p) {
            var lvl = Minecraft.getInstance().level;
            return lvl != null && lvl.getBlockEntity(p) instanceof com.sparkgap.pgtelegraph.block.AmpFed f && f.ampMuted();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, net.minecraft.client.resources.sounds.Sound sound, boolean looping) {
            // nearby sets hearing the same thing share one simulation (SimHub)
            return CompletableFuture.completedFuture(new SimHub.SharedStream(ch, this::where, voicePos, lib, handheld));
        }
    }
}
