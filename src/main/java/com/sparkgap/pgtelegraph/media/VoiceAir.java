package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.radio.RadioWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Live voice on the air. A microphone next to a transmitter in AM or FM keys it while someone
 * talks into it; the transmitter (and the amplifier it drives) registers as an on-air voice source,
 * every AM/FM receiver registers which source it is hearing and how well, and each 50 ms of speech
 * is passed straight to the players near the receivers that hear it - only when the modes match.
 */
public final class VoiceAir {
    private VoiceAir() {}

    /** a radiating block carrying voice from transmitter {@code tx} */
    private record Src(BlockPos tx, int mod, long tick) {}

    /** a receiver hearing {@code source} */
    private static final class Ear {
        BlockPos source;
        int mod;
        float snr;
        long tick, awarded = -10_000;
    }

    private static final Map<Level, Map<BlockPos, Src>> SOURCES = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Ear>> EARS = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Long>> TALK = new WeakHashMap<>();

    /** while keyed, every tick: {@code radiating} (the transmitter or its amplifier) carries tx's voice */
    public static void onAir(Level level, BlockPos radiating, BlockPos tx, int mod) {
        SOURCES.computeIfAbsent(level, l -> new HashMap<>()).put(radiating.immutable(), new Src(tx.immutable(), mod, level.getGameTime()));
    }

    public static void onAir(Level level, BlockPos radiating, BlockPos tx, boolean fm) {
        onAir(level, radiating, tx, fm ? com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_FM : com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_AM);
    }

    /** a receiver's latest look at the band: the strongest signal comes from {@code source} */
    public static void listen(Level level, BlockPos rx, BlockPos source, boolean fm, float snr) {
        listen(level, rx, source, fm ? com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_FM : com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_AM, snr);
    }

    /**
     * A receiver hears {@code source}. The voice reaches it whatever its mode: the receiver's own
     * loudspeaker simulation demodulates it as it can (SSB on an AM set is unintelligible, as it should be).
     */
    public static void listen(Level level, BlockPos rx, BlockPos source, int mod, float snr) {
        Ear e = EARS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(rx.immutable(), k -> new Ear());
        e.source = source;
        e.mod = mod;
        e.snr = snr;
        e.tick = level.getGameTime();
    }

    /** manpack earpieces: ear key -> the wearer */
    private static final Map<Level, Map<BlockPos, java.util.UUID>> PACK_EARS = new WeakHashMap<>();
    /** where a manpack's voice is played on the wearer's client (no block there) */
    public static final BlockPos EARPIECE = new BlockPos(0, -30000, 0);

    /** a manpack worn by {@code wearer} hears {@code source} */
    public static void listenPack(Level level, BlockPos earKey, java.util.UUID wearer, BlockPos source, float snr) {
        PACK_EARS.computeIfAbsent(level, l -> new HashMap<>()).put(earKey.immutable(), wearer);
        listen(level, earKey, source, RadioWorld.MOD_FM, snr);
    }

    /** someone is talking into a microphone next to {@code tx} */
    public static boolean talking(Level level, BlockPos tx) {
        Map<BlockPos, Long> m = TALK.get(level);
        Long t = m == null ? null : m.get(tx);
        return t != null && level.getGameTime() - t <= 6;
    }

    /** 50 ms of mu-law speech into the transmitter at {@code tx} */
    public static void speak(ServerLevel level, BlockPos tx, byte[] data) {
        long now = level.getGameTime();
        TALK.computeIfAbsent(level, l -> new HashMap<>()).put(tx.immutable(), now);
        Map<BlockPos, Src> srcs = SOURCES.get(level);
        Map<BlockPos, Ear> ears = EARS.get(level);
        if (srcs == null || ears == null) return;
        for (var s : srcs.entrySet()) {
            Src src = s.getValue();
            if (!src.tx.equals(tx) || now - src.tick > 3) continue;
            for (var en : ears.entrySet()) {
                Ear e = en.getValue();
                if (now - e.tick > 15 || !s.getKey().equals(e.source)) continue;
                boolean fm = src.mod == com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_FM || src.mod == com.sparkgap.pgtelegraph.radio.RadioWorld.MOD_WFM;
                if (fm ? e.snr < 4 : e.snr < -3) continue; // FM below threshold: squelched; AM / SSB: lost in the noise
                BlockPos rx = en.getKey();
                var packs = PACK_EARS.get(level);
                java.util.UUID wearer = packs == null ? null : packs.get(rx);
                if (wearer != null) { // a manpack: straight into its wearer's earpiece
                    var p = level.getServer().getPlayerList().getPlayer(wearer);
                    if (p != null) PacketDistributor.sendToPlayer(p, new VoiceNet.Out(EARPIECE, data, e.snr, fm));
                    continue;
                }
                PacketDistributor.sendToPlayersNear(level, null, rx.getX() + 0.5, rx.getY() + 0.5, rx.getZ() + 0.5, 40,
                        new VoiceNet.Out(rx, data, e.snr, fm));
                if (now - e.awarded > 200) {
                    e.awarded = now;
                    com.sparkgap.pgtelegraph.Awards.near(level, rx, 16, "voice");
                }
            }
        }
        if (now % 200 == 0) prune(level, now);
    }

    private static void prune(Level level, long now) {
        Map<BlockPos, Src> s = SOURCES.get(level);
        if (s != null) s.values().removeIf(v -> now - v.tick > 100);
        Map<BlockPos, Ear> e = EARS.get(level);
        if (e != null) e.values().removeIf(v -> now - v.tick > 100);
        Map<BlockPos, Long> t = TALK.get(level);
        if (t != null) t.values().removeIf(v -> now - v > 100);
    }
}
