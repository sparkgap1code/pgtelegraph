package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.broadcast.AudioNet;
import com.sparkgap.pgtelegraph.broadcast.AudioStore;
import com.sparkgap.pgtelegraph.broadcast.BroadcastRadioBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Client side of recordings: importing a file (MP3 via JLayer, WAV via Java Sound, OGG via
 * stb_vorbis) into 11 kHz 8-bit mono and uploading it; downloading recordings for radios; and
 * running a switched-on radio's loudspeaker, whose sound {@link ClientRadio} builds as a real
 * receiver would hear it.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT)
public final class ClientAudio {
    private ClientAudio() {}

    // ======================================================== import + upload
    private static final ArrayDeque<AudioNet.Upload> SENDING = new ArrayDeque<>();
    private static volatile boolean busy;

    public static void importFile(InteractionHand hand) {
        if (busy) return;
        busy = true;
        Thread t = new Thread(() -> {
            try {
                String path;
                try (MemoryStack st = MemoryStack.stackPush()) {
                    PointerBuffer f = st.mallocPointer(4);
                    f.put(st.UTF8("*.mp3")).put(st.UTF8("*.wav")).put(st.UTF8("*.ogg")).put(st.UTF8("*.MP3")).flip();
                    path = TinyFileDialogs.tinyfd_openFileDialog("Import audio", "", f, "MP3 / WAV / OGG", false);
                }
                if (path == null) return;
                File file = new File(path);
                say(Component.translatable("pgtelegraph.record.decoding", file.getName()), ChatFormatting.GRAY);
                byte[] pcm = decode(file);
                if (pcm.length == 0) throw new IllegalStateException("no audio");
                String title = file.getName().replaceFirst("\\.[^.]+$", "");
                if (title.length() > 60) title = title.substring(0, 60);
                int total = (pcm.length + AudioNet.UP_CHUNK - 1) / AudioNet.UP_CHUNK;
                boolean off = hand == InteractionHand.OFF_HAND;
                synchronized (SENDING) {
                    for (int i = 0; i < total; i++) {
                        int from = i * AudioNet.UP_CHUNK;
                        SENDING.add(new AudioNet.Upload(off, i, total, title, java.util.Arrays.copyOfRange(pcm, from,
                                Math.min(pcm.length, from + AudioNet.UP_CHUNK))));
                    }
                }
                say(Component.translatable("pgtelegraph.record.uploading", title, pcm.length / AudioStore.RATE / 60,
                        pcm.length / AudioStore.RATE % 60), ChatFormatting.GRAY);
            } catch (Throwable e) {
                say(Component.translatable("pgtelegraph.record.failed", String.valueOf(e.getMessage())), ChatFormatting.RED);
            } finally {
                busy = false;
            }
        }, "pgtelegraph-audio-import");
        t.setDaemon(true);
        t.start();
    }

    private static void say(Component c, ChatFormatting f) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.displayClientMessage(c.copy().withStyle(f), false);
        });
    }

    /** any supported file -> 8-bit signed mono at AudioStore.RATE, normalised, at most MAX_SECONDS */
    static byte[] decode(File file) throws Exception {
        String n = file.getName().toLowerCase(java.util.Locale.ROOT);
        float[] mono;
        int rate;
        if (n.endsWith(".mp3")) {
            float[][] r = new float[1][];
            rate = decodeMp3(file, r);
            mono = r[0];
        } else if (n.endsWith(".ogg")) {
            try (MemoryStack st = MemoryStack.stackPush()) {
                IntBuffer ch = st.mallocInt(1), sr = st.mallocInt(1);
                ShortBuffer s = org.lwjgl.stb.STBVorbis.stb_vorbis_decode_filename(file.getAbsolutePath(), ch, sr);
                if (s == null) throw new IllegalStateException("bad ogg");
                int c = ch.get(0);
                rate = sr.get(0);
                mono = new float[s.remaining() / c];
                for (int i = 0; i < mono.length; i++) {
                    float sum = 0;
                    for (int k = 0; k < c; k++) sum += s.get(i * c + k);
                    mono[i] = sum / c / 32768f;
                }
                org.lwjgl.system.libc.LibCStdlib.free(s);
            }
        } else {
            AudioInputStream in = AudioSystem.getAudioInputStream(file);
            AudioFormat src = in.getFormat();
            AudioFormat pcm16 = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16, src.getChannels(),
                    src.getChannels() * 2, src.getSampleRate(), false);
            AudioInputStream conv = AudioSystem.getAudioInputStream(pcm16, in);
            byte[] all = conv.readAllBytes();
            int c = src.getChannels();
            rate = (int) src.getSampleRate();
            mono = new float[all.length / 2 / c];
            for (int i = 0; i < mono.length; i++) {
                float sum = 0;
                for (int k = 0; k < c; k++) {
                    int o = (i * c + k) * 2;
                    sum += (short) ((all[o] & 0xFF) | (all[o + 1] << 8));
                }
                mono[i] = sum / c / 32768f;
            }
        }
        return toPcm8(mono, rate);
    }

    private static int decodeMp3(File file, float[][] out) throws Exception {
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            javazoom.jl.decoder.Bitstream bs = new javazoom.jl.decoder.Bitstream(is);
            javazoom.jl.decoder.Decoder dec = new javazoom.jl.decoder.Decoder();
            ByteArrayOutputStream acc = new ByteArrayOutputStream();
            int rate = 44100;
            long samples = 0, limit = (long) AudioStore.MAX_SECONDS * 48000;
            javazoom.jl.decoder.Header h;
            while ((h = bs.readFrame()) != null && samples < limit) {
                javazoom.jl.decoder.SampleBuffer sb = (javazoom.jl.decoder.SampleBuffer) dec.decodeFrame(h, bs);
                rate = sb.getSampleFrequency();
                int c = sb.getChannelCount();
                short[] b = sb.getBuffer();
                int len = sb.getBufferLength();
                for (int i = 0; i + c <= len; i += c) {
                    int sum = 0;
                    for (int k = 0; k < c; k++) sum += b[i + k];
                    short v = (short) (sum / c);
                    acc.write(v & 0xFF);
                    acc.write((v >> 8) & 0xFF);
                    samples++;
                }
                bs.closeFrame();
            }
            bs.close();
            byte[] raw = acc.toByteArray();
            float[] mono = new float[raw.length / 2];
            for (int i = 0; i < mono.length; i++) mono[i] = (short) ((raw[2 * i] & 0xFF) | (raw[2 * i + 1] << 8)) / 32768f;
            out[0] = mono;
            return rate;
        }
    }

    private static byte[] toPcm8(float[] in, int rate) {
        double step = rate / (double) AudioStore.RATE;
        int n = (int) Math.min((long) AudioStore.MAX_BYTES, (long) (in.length / step));
        float peak = 1e-6f;
        for (float v : in) peak = Math.max(peak, Math.abs(v));
        float gain = 0.9f / peak;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            // box filter over the decimation step (simple anti-alias)
            int a = (int) (i * step), b = Math.max(a + 1, (int) ((i + 1) * step));
            float s = 0;
            for (int k = a; k < b && k < in.length; k++) s += in[k];
            s /= (b - a);
            out[i] = (byte) Math.max(-127, Math.min(127, Math.round(s * gain * 127)));
        }
        return out;
    }

    // ======================================================== download cache
    private static final Map<String, byte[]> READY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, byte[]> PARTIAL = new HashMap<>();
    /** when each recording was last asked for: asked again every 10 s until it arrives (it used to be asked once, ever) */
    private static final Map<String, Long> ASKED = new HashMap<>();

    public static void chunk(String id, int index, int total, int size, byte[] data) {
        byte[] buf = PARTIAL.computeIfAbsent(id, k -> new byte[Math.max(0, Math.min(size, AudioStore.MAX_BYTES))]);
        int from = index * AudioNet.DOWN_CHUNK;
        System.arraycopy(data, 0, buf, from, Math.max(0, Math.min(data.length, buf.length - from)));
        if (index == total - 1) {
            READY.put(id, PARTIAL.remove(id));
            com.sparkgap.pgtelegraph.PgTelegraph.LOGGER.info("[pgtelegraph] recording {} downloaded ({} bytes)", id, READY.get(id).length);
            if (READY.size() > 8) READY.keySet().stream().filter(k -> !k.equals(id)).findFirst().ifPresent(READY::remove);
        }
    }

    /** a downloaded recording, or null (never asks for it: safe off the main thread) */
    static byte[] ready(String id) {
        return READY.get(id);
    }

    static byte[] get(String id) {
        byte[] b = READY.get(id);
        long now = System.currentTimeMillis();
        if (b == null && now - ASKED.getOrDefault(id, 0L) > 10_000) {
            ASKED.put(id, now);
            PacketDistributor.sendToServer(new AudioNet.Request(id));
        }
        return b;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        if (Minecraft.getInstance().getConnection() == null) {
            synchronized (SENDING) {
                SENDING.clear();
            }
            ASKED.clear();
            return;
        }
        synchronized (SENDING) {
            for (int i = 0; i < 4 && !SENDING.isEmpty(); i++) PacketDistributor.sendToServer(SENDING.poll());
        }
    }

    // ======================================================== playback
    /**
     * Keeps a switched-on radio's loudspeaker going. What it plays is worked out sample by sample by
     * {@link ClientRadio.RadioStream} - the stations in the channel, modulated, through the air, with
     * noise and interference, then demodulated - so it plays (hiss, if nothing else) whenever it is on.
     */
    public static void radioTick(BroadcastRadioBlockEntity r) {
        for (var g : r.channel.signals) if (g.key().startsWith("a:")) get(g.key().substring(2)); // fetch recordings
        if (r.clientProgram instanceof ClientRadio.RxSound ps && ps.playing()) return;
        if (r.clientProgram instanceof ClientRadio.RxSound ps) ps.drop();
        var ps = new ClientRadio.RxSound(r.channel, r.getBlockPos(), false, () -> !r.isRemoved() && r.isOn() && r.isPowered(), 1f);
        r.clientProgram = ps;
        Minecraft.getInstance().getSoundManager().play(ps);
    }
}
