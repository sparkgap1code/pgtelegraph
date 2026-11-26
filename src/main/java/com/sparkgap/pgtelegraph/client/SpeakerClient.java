package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.SpeakerBlockEntity;
import com.sparkgap.pgtelegraph.media.SpeakerNet;
import com.sparkgap.pgtelegraph.radio.RxChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Loudspeakers as heard here: each playing speaker the server told us about is a sound at its block,
 * as loud as the physics says at our distance and angle (no Minecraft fall-off), arriving late by the
 * time sound takes to get here (343 m/s), so a far horn echoes the radio next to you.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT)
public final class SpeakerClient {
    private SpeakerClient() {}

    private static final class Entry {
        final RxChannel ch = new RxChannel();
        volatile SpeakerNet.State state;
        volatile long seen;
        SpeakerSound sound;
    }

    private static final Map<BlockPos, Entry> SPEAKERS = new HashMap<>();

    static void update(SpeakerNet.State m) {
        Entry e = SPEAKERS.computeIfAbsent(m.pos().immutable(), k -> new Entry());
        e.ch.load(m.channel());
        e.state = m;
        e.seen = System.currentTimeMillis();
        if (e.sound == null || !e.sound.playing()) {
            if (e.sound != null) e.sound.drop();
            e.sound = new SpeakerSound(e);
            Minecraft.getInstance().getSoundManager().play(e.sound);
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post ev) {
        if (SPEAKERS.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean gone = Minecraft.getInstance().level == null;
        SPEAKERS.values().removeIf(e -> {
            if (!gone && now - e.seen < 3000) return false;
            if (e.sound != null) e.sound.drop();
            return true;
        });
    }

    /** SPL (dB) at the listener, and distance (m) */
    private static double[] at(SpeakerNet.State s) {
        var p = Minecraft.getInstance().player;
        if (p == null) return new double[]{0, 0};
        Vec3 c = s.pos().getCenter(), to = p.getEyePosition().subtract(c);
        double r = to.length();
        double off = r < 1e-3 ? 0 : Math.toDegrees(Math.acos(Math.max(-1, Math.min(1,
                (to.x * s.ax() + to.y * s.ay() + to.z * s.az()) / r))));
        return new double[]{SpeakerBlockEntity.spl(s.horn(), s.watts(), r, off), r};
    }

    static final class SpeakerSound extends AbstractTickableSoundInstance {
        private final Entry e;
        private final long created = System.currentTimeMillis();

        SpeakerSound(Entry e) {
            super(PgTelegraph.BROADCAST_STREAM.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.e = e;
            BlockPos p = e.state.pos();
            x = p.getX() + 0.5;
            y = p.getY() + 0.5;
            z = p.getZ() + 0.5;
            looping = false;
            attenuation = Attenuation.NONE; // loudness worked out below, from the speaker's physics
            volume = volumeNow();
        }

        private float volumeNow() {
            double spl = at(e.state)[0];
            if (spl < 25) return 0;
            return (float) Math.min(1, Math.pow(10, (spl - 100) / 20)); // 100 dB = full volume, -20 dB = a tenth
        }

        boolean playing() {
            if (isStopped()) return false;
            return System.currentTimeMillis() - created < 2000 || Minecraft.getInstance().getSoundManager().isActive(this);
        }

        void drop() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (System.currentTimeMillis() - e.seen > 3000) stop();
            else volume = volumeNow();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary lib, net.minecraft.client.resources.sounds.Sound sound, boolean looping) {
            BlockPos p = e.state.pos();
            BlockPos amp = e.state.amp();
            AudioStream radio = e.state.source().equals(amp) ? null // a microphone only: no radio under it
                    : new SimHub.SharedStream(e.ch, () -> p, e.state.source(), lib, false, () -> at(e.state)[1] / 343.0)
                    .keyedAt(e.state.source());
            return CompletableFuture.completedFuture(new PaClient.Mix(radio, amp));
        }
    }
}
