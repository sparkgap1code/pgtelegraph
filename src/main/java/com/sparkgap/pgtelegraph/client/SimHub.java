package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.radio.RxChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shares the receiver simulation between sets that would hear the same thing: sets within the
 * same 8-block cell, tuned to the same frequency in the same mode and bandwidth, are fed from one
 * simulation (a few blocks make no audible difference to what an antenna brings in). Each group
 * computes into a two-second ring that every member reads at its own position.
 * <p>
 * At most {@link #MAX_ACTIVE} groups - the nearest to the player - are simulated; farther ones
 * fall back to a faint hiss, so a room full of radios cannot eat the CPU.
 */
final class SimHub {
    private SimHub() {}

    static final int MAX_ACTIVE = 6;
    private static final int RING = 1 << 18; // ~8 s at 32 kHz (2.8 km of sound travel)

    private static final class Group {
        String key;
        /** members reading it (stream -> last read ms): a group with one member follows it to a new key */
        final java.util.IdentityHashMap<Object, Long> members = new java.util.IdentityHashMap<>();
        RadioStreamHolder engine;
        final short[] ring = new short[RING];
        long produced;
        long lastRead;
        BlockPos anchor;
        RxChannel ch;
        BlockPos voicePos;
        boolean priority;

        Group(String key) {
            this.key = key;
        }
    }

    /** the simulation engine of a group (created lazily on the sound thread) */
    private static final class RadioStreamHolder {
        final ClientRadio.RadioStream stream;

        RadioStreamHolder(RxChannel ch, BlockPos voicePos, SoundBufferLibrary lib) {
            stream = new ClientRadio.RadioStream(ch, voicePos, lib);
        }
    }

    private static final Map<String, Group> GROUPS = new HashMap<>();

    /** sets share a simulation when they are a few blocks apart, in the same mode and filter, on (nearly) the same
     *  dial: to the nearest 500 Hz, so a receiver's oscillator drifting a few Hz does not start a new one */
    static String key(RxChannel ch, BlockPos pos) {
        return ch.mode + "/" + ch.bwHz + "/" + Math.round(ch.dialHz / 500.0) + "/" + (pos.getX() >> 3) + "," + (pos.getY() >> 3) + "," + (pos.getZ() >> 3)
                + (ch.sidetone ? "/side" : "");
    }

    /** a member's view: reads the group it currently belongs to */
    static final class SharedStream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(ClientRadio.FA, 16, 1, true, false);
        private final RxChannel ch;
        private final java.util.function.Supplier<BlockPos> pos;
        private final BlockPos voicePos;
        private final SoundBufferLibrary lib;
        private final boolean priority;
        private final java.util.SplittableRandom rnd = new java.util.SplittableRandom();
        private String group;
        private long readPos = -1;
        /** how late this member hears the group (seconds; sound travel from a far speaker), or null */
        private final java.util.function.DoubleSupplier delaySec;
        private long delay;
        /** where the group is keyed: the set itself (pos), or for loudspeakers the radio they all play - one engine for all */
        private java.util.function.Supplier<BlockPos> keyPos;

        /** loudspeakers on one radio share its simulation: in step with each other, only sound travel apart */
        SharedStream keyedAt(BlockPos radio) {
            BlockPos k = radio.immutable();
            keyPos = () -> k;
            return this;
        }

        SharedStream(RxChannel ch, java.util.function.Supplier<BlockPos> pos, BlockPos voicePos, SoundBufferLibrary lib,
                     boolean priority) {
            this(ch, pos, voicePos, lib, priority, null);
        }

        SharedStream(RxChannel ch, java.util.function.Supplier<BlockPos> pos, BlockPos voicePos, SoundBufferLibrary lib,
                     boolean priority, java.util.function.DoubleSupplier delaySec) {
            this.delaySec = delaySec;
            this.priority = priority;
            this.ch = ch;
            this.pos = pos;
            this.voicePos = voicePos;
            this.lib = lib;
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer read(int size) {
            int n = Math.max(64, Math.min(8192, size / 2));
            ByteBuffer out = BufferUtils.createByteBuffer(n * 2);
            synchronized (GROUPS) {
                BlockPos p = pos.get();
                String k = key(ch, keyPos != null ? keyPos.get() : p);
                Group g = GROUPS.get(k);
                long nowMs = System.currentTimeMillis();
                if (g == null && group != null && !k.equals(group)) {
                    // moved to a new key (dial, mode, a few blocks): if nobody else is in the old group, take it along -
                    // the same engine keeps running (no restart click, the programme audio need not be fetched again)
                    Group old = GROUPS.get(group);
                    if (old != null) {
                        old.members.values().removeIf(t -> nowMs - t > 1000);
                        old.members.remove(this);
                        if (old.members.isEmpty()) {
                            GROUPS.remove(group);
                            old.key = k;
                            old.ch = ch;
                            GROUPS.put(k, old);
                            group = k;
                            g = old;
                        }
                    }
                }
                if (g == null) {
                    g = new Group(k);
                    g.ch = ch;
                    g.voicePos = voicePos;
                    g.anchor = p;
                    GROUPS.put(k, g);
                }
                if (!k.equals(group)) {
                    group = k;
                    delay = delaySec == null ? 0 : Math.min(RING - 2L * n - 16384, (long) (delaySec.getAsDouble() * ClientRadio.FA));
                    readPos = g.produced - Math.max(0, delay); // joined (or moved to) a group: start at its present (or as late as the sound)
                }
                g.lastRead = nowMs;
                g.members.put(this, nowMs);
                g.priority |= priority;
                g.anchor = p;
                if (!active(g)) {
                    g.engine = null; // too far down the list: no simulation, a faint hiss
                    for (int i = 0; i < n; i++) {
                        short v = (short) (rnd.nextGaussian() * 300);
                        out.put((byte) (v & 0xFF)).put((byte) ((v >> 8) & 0xFF));
                    }
                    out.flip();
                    return out;
                }
                if (g.engine == null) g.engine = new RadioStreamHolder(g.ch, g.voicePos, lib);
                g.engine.stream.ch = ch; // any member's channel will do (they are a few blocks apart); a removed one stops reading
                if (voicePos != null) g.engine.stream.voicePos = voicePos;
                if (readPos < g.produced - RING + n || readPos > g.produced) readPos = g.produced - Math.max(0, delay);
                long ahead = Math.max(0, delay); // a late listener keeps the simulation running that far ahead of it
                while (g.produced < readPos + n + ahead) {
                    ByteBuffer b = g.engine.stream.read(Math.min(8192, (int) (readPos + n + ahead - g.produced)) * 2);
                    b.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    while (b.remaining() >= 2) g.ring[(int) (g.produced++ & (RING - 1))] = b.getShort();
                }
                for (int i = 0; i < n; i++) {
                    short v = g.ring[(int) ((readPos + i) & (RING - 1))];
                    out.put((byte) (v & 0xFF)).put((byte) ((v >> 8) & 0xFF));
                }
                readPos += n;
                prune();
            }
            out.flip();
            return out;
        }

        @Override
        public void close() {}
    }

    /** is this group among the nearest MAX_ACTIVE to the player? */
    private static boolean active(Group g) {
        var player = Minecraft.getInstance().player;
        if (player == null) return true;
        if (g.priority) return true; // the handheld in your hand always plays
        // only groups that are being listened to right now count (one left behind a moment ago does not)
        long now = System.currentTimeMillis();
        double mine = player.distanceToSqr(g.anchor.getCenter());
        int closer = 0;
        for (Group o : GROUPS.values())
            if (o != g && now - o.lastRead < 500 && player.distanceToSqr(o.anchor.getCenter()) < mine) closer++;
        return closer < MAX_ACTIVE;
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        List<String> dead = new ArrayList<>();
        for (Group g : GROUPS.values()) if (now - g.lastRead > 3000) dead.add(g.key);
        for (String k : dead) {
            Group g = GROUPS.remove(k);
            if (g.engine != null) g.engine.stream.close();
        }
    }
}
