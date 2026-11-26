package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.media.MuLaw;
import com.sparkgap.pgtelegraph.media.VoiceNet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real microphone on the air, and speech out of receivers.
 * <p>Push-to-talk (default V) near a microphone block (4 blocks) opens the computer's microphone
 * only while held: 8 kHz, automatic gain, mu-law, 50 ms a packet. Received speech plays from the
 * receiver block through a small jitter buffer (about 0.3 s behind), with AM hiss or FM
 * "popcorn" by signal-to-noise ratio.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT)
public final class ClientVoice {
    private ClientVoice() {}

    public static final int RATE = 8000, CHUNK = RATE / 20;
    public static final KeyMapping PTT = new KeyMapping("key.pgtelegraph.ptt", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V,
            "key.categories.pgtelegraph");

    // ======================================================== capture
    private static volatile boolean capturing;
    private static Thread thread;
    private static final ConcurrentLinkedQueue<byte[]> OUT = new ConcurrentLinkedQueue<>();
    private static BlockPos mic;
    private static boolean wasDown, tried;

    private static BlockPos findMic(Minecraft mc) {
        BlockPos c = mc.player.blockPosition();
        BlockPos best = null;
        double bd = 17;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-4, -3, -4), c.offset(4, 3, 4))) {
            var st = mc.level.getBlockState(p);
            boolean phone = st.is(PgTelegraph.FIELD_PHONE.get()) && st.getValue(com.sparkgap.pgtelegraph.block.FieldPhoneBlockEntity.OFF_HOOK);
            if (!st.is(PgTelegraph.MICROPHONE.get()) && !phone) continue;
            double d = mc.player.getEyePosition().distanceToSqr(p.getCenter());
            if (d < bd) {
                bd = d;
                best = p.immutable();
            }
        }
        return best;
    }

    private static void start() {
        if (thread != null && thread.isAlive()) {
            try {
                thread.join(300);
            } catch (InterruptedException ignored) {
            }
        }
        capturing = true;
        OUT.clear();
        thread = new Thread(ClientVoice::captureLoop, "pgtelegraph-mic");
        thread.setDaemon(true);
        thread.start();
    }

    private static void captureLoop() {
        TargetDataLine line = null;
        try {
            AudioFormat fmt = null;
            for (float r : new float[]{8000f, 16000f, 44100f, 48000f}) {
                AudioFormat f = new AudioFormat(r, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, f);
                if (!AudioSystem.isLineSupported(info)) continue;
                try {
                    line = (TargetDataLine) AudioSystem.getLine(info);
                    line.open(f, (int) (r / 10) * 2);
                    fmt = f;
                    break;
                } catch (Exception e) {
                    line = null;
                }
            }
            if (line == null) {
                say(Component.translatable("pgtelegraph.mic.none"));
                capturing = false;
                return;
            }
            line.start();
            int rate = (int) fmt.getSampleRate(), per = rate / 20;
            byte[] buf = new byte[per * 2];
            float agc = 3f;
            while (capturing) {
                int got = 0;
                while (got < buf.length && capturing) {
                    int n = line.read(buf, got, buf.length - got);
                    if (n <= 0) break;
                    got += n;
                }
                if (!capturing) break;
                float[] s = new float[CHUNK];
                float peak = 0, rms = 0;
                for (int i = 0; i < CHUNK; i++) {
                    int a = i * per / CHUNK, b = Math.max(a + 1, (i + 1) * per / CHUNK);
                    float sum = 0;
                    for (int k = a; k < b; k++) sum += (short) ((buf[2 * k] & 0xFF) | (buf[2 * k + 1] << 8));
                    s[i] = sum / (b - a) / 32768f;
                    peak = Math.max(peak, Math.abs(s[i]));
                    rms += s[i] * s[i];
                }
                rms = (float) Math.sqrt(rms / CHUNK);
                float want = Math.min(8f, 0.6f / Math.max(1e-4f, peak));
                agc += (want - agc) * (want < agc ? 0.6f : 0.04f); // fast attack, slow release
                boolean gate = rms * agc < 0.01f; // background hum between words
                byte[] out = new byte[CHUNK];
                for (int i = 0; i < CHUNK; i++) {
                    float v = gate ? 0 : Math.max(-1, Math.min(1, s[i] * agc));
                    out[i] = MuLaw.encode((int) (v * 32000));
                }
                OUT.add(out);
                while (OUT.size() > 20) OUT.poll();
            }
        } catch (Throwable t) {
            PgTelegraph.LOGGER.warn("microphone capture failed", t);
            say(Component.translatable("pgtelegraph.mic.failed", String.valueOf(t.getMessage())));
            capturing = false;
        } finally {
            if (line != null) {
                line.stop();
                line.close();
            }
        }
    }

    private static void say(Component c) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.displayClientMessage(c.copy().withStyle(ChatFormatting.RED), false);
        });
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            capturing = false;
            for (Chan c : CHANNELS.values()) if (c.sound != null) mc.getSoundManager().stop(c.sound);
            CHANNELS.clear();
            return;
        }
        boolean down = PTT.isDown() && mc.screen == null;
        boolean pack = com.sparkgap.pgtelegraph.radio.ManpackItem.worn(mc.player) != null;
        if (pack && (down != wasDown || (down && mc.level.getGameTime() % 10 == 0)))
            PacketDistributor.sendToServer(new VoiceNet.Ptt(down));
        BlockPos m = down ? (pack ? com.sparkgap.pgtelegraph.media.VoiceAir.EARPIECE : findMic(mc)) : null;
        if (m != null && !capturing && !tried) {
            tried = true; // once a press: a missing microphone is not retried every tick
            start();
        }
        if (m == null && capturing) capturing = false;
        if (!down) {
            capturing = false;
            tried = false;
        }
        if (down && !wasDown && m == null) {
            mc.player.displayClientMessage(Component.translatable("pgtelegraph.mic.far").withStyle(ChatFormatting.GRAY), true);
        }
        wasDown = down;
        mic = m;
        byte[] d;
        while ((d = OUT.poll()) != null) {
            if (mic != null) PacketDistributor.sendToServer(mic.equals(com.sparkgap.pgtelegraph.media.VoiceAir.EARPIECE)
                    ? new VoiceNet.Pack(d) : new VoiceNet.In(mic, d));
        }
        if (capturing) mc.player.displayClientMessage(Component.translatable("pgtelegraph.mic.on").withStyle(ChatFormatting.RED), true);

        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<BlockPos, Chan>> it = CHANNELS.entrySet().iterator(); it.hasNext(); ) {
            Chan c = it.next().getValue();
            if (now - c.lastMs > 3000) {
                if (c.sound != null) mc.getSoundManager().stop(c.sound);
                it.remove();
            }
        }
    }

    // ======================================================== playback
    static final class Chan {
        final ConcurrentLinkedQueue<byte[]> q = new ConcurrentLinkedQueue<>();
        final AtomicInteger queued = new AtomicInteger();
        volatile float snr;
        volatile boolean fm;
        volatile long lastMs;
        VoiceSound sound;
        long soundAt;
    }

    private static final Map<BlockPos, Chan> CHANNELS = new HashMap<>();

    public static void heard(BlockPos rx, byte[] data, float snr, boolean fm) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (rx.equals(com.sparkgap.pgtelegraph.media.VoiceAir.EARPIECE)) { // the manpack's earpiece: through its simulated receiver
            ClientRadio.voice(rx, data);
            return;
        }
        if (mc.level.getBlockEntity(rx) instanceof com.sparkgap.pgtelegraph.block.AudioAmpBlockEntity) { // public address
            PaClient.add(rx, data);
            return;
        }
        if (mc.level.getBlockEntity(rx) instanceof com.sparkgap.pgtelegraph.broadcast.BroadcastRadioBlockEntity
                || mc.level.getBlockEntity(rx) instanceof com.sparkgap.pgtelegraph.block.ReceiverBlockEntity) {
            ClientRadio.voice(rx, data); // the radio modulates it onto its station and receives it like the rest
            return;
        }
        Chan c = CHANNELS.computeIfAbsent(rx.immutable(), k -> new Chan());
        c.snr = snr;
        c.fm = fm;
        c.lastMs = System.currentTimeMillis();
        c.q.add(data);
        c.queued.addAndGet(data.length);
        long t = mc.level.getGameTime();
        if (c.sound == null || (t - c.soundAt > 10 && !mc.getSoundManager().isActive(c.sound))) {
            if (c.sound != null) mc.getSoundManager().stop(c.sound);
            c.sound = new VoiceSound(rx, c);
            c.soundAt = t;
            mc.getSoundManager().play(c.sound);
        }
    }

    /** speech came out of this block in the last half second */
    public static boolean recent(BlockPos p) {
        Chan c = CHANNELS.get(p);
        return c != null && System.currentTimeMillis() - c.lastMs < 500;
    }

    static final class VoiceSound extends AbstractTickableSoundInstance {
        private final Chan chan;

        VoiceSound(BlockPos p, Chan chan) {
            super(PgTelegraph.BROADCAST_STREAM.get(), SoundSource.VOICE, SoundInstance.createUnseededRandom());
            this.chan = chan;
            x = p.getX() + 0.5;
            y = p.getY() + 0.5;
            z = p.getZ() + 0.5;
            looping = false;
            volume = 1f;
        }

        @Override
        public void tick() {
            if (System.currentTimeMillis() - chan.lastMs > 3000) stop();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, net.minecraft.client.resources.sounds.Sound sound, boolean looping) {
            return CompletableFuture.completedFuture(new VoiceStream(chan));
        }
    }

    /** jitter buffer -> 16-bit OpenAL stream, 80 ms a buffer */
    static final class VoiceStream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);
        private final Chan chan;
        private final Random rnd = new Random();
        private byte[] cur;
        private int idx, popLeft;

        VoiceStream(Chan chan) {
            this.chan = chan;
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer read(int size) {
            // keep the delay down: drop speech that piled up during a lag
            if (chan.queued.get() > RATE * 6 / 10) {
                while (chan.queued.get() > RATE / 4) {
                    byte[] d = chan.q.poll();
                    if (d == null) break;
                    chan.queued.addAndGet(-d.length);
                }
            }
            int n = RATE * 8 / 100;
            ByteBuffer out = BufferUtils.createByteBuffer(n * 2);
            float snr = chan.snr;
            boolean fm = chan.fm;
            float am = (float) Math.max(0.02, Math.min(0.9, (24 - snr) / 30));
            float pop = (float) Math.max(0, Math.min(0.6, (14 - snr) / 12));
            for (int i = 0; i < n; i++) {
                if (cur == null || idx >= cur.length) {
                    cur = chan.q.poll();
                    idx = 0;
                    if (cur != null) chan.queued.addAndGet(-cur.length);
                }
                float s = 0;
                if (cur != null) {
                    s = MuLaw.decode(cur[idx++]) / 32768f;
                    if (fm) {
                        if (popLeft > 0) {
                            popLeft--;
                            s = (rnd.nextFloat() * 2 - 1) * 0.35f;
                        } else if (i % 80 == 0 && rnd.nextFloat() < pop * 0.25f) popLeft = 20 + rnd.nextInt(160);
                    } else {
                        s = s * (1 - am * 0.7f) + (rnd.nextFloat() * 2 - 1) * am * 0.45f;
                    }
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
