package com.sparkgap.pgtelegraph.network;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.BeaconProgram;
import com.sparkgap.pgtelegraph.block.TeleprinterBlockEntity;
import com.sparkgap.pgtelegraph.item.CipherMachineItem;
import com.sparkgap.pgtelegraph.item.LogbookItem;
import com.sparkgap.pgtelegraph.item.MeterItem;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import com.sparkgap.pgtelegraph.radio.Reception;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Network messages of the second batch (beacons, instruments, records, teleprinter, cipher). */
public final class Batch2Payloads {
    private Batch2Payloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mk(String id) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, id));
    }

    static boolean near(Player p, BlockPos pos) {
        return p.level().isLoaded(pos) && p.getEyePosition().distanceToSqr(pos.getCenter()) <= 8 * 8;
    }

    static InteractionHand hand(boolean off) {
        return off ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    public static void register(PayloadRegistrar r) {
        r.playToServer(Beacon.TYPE, Beacon.CODEC, Beacon::handle);
        r.playToServer(ItemTune.TYPE, ItemTune.CODEC, ItemTune::handle);
        r.playToServer(SpectrumRequest.TYPE, SpectrumRequest.CODEC, SpectrumRequest::handle);
        r.playToClient(SpectrumData.TYPE, SpectrumData.CODEC, SpectrumData::handle);
        r.playToServer(Teleprinter.TYPE, Teleprinter.CODEC, Teleprinter::handle);
        r.playToServer(Logbook.TYPE, Logbook.CODEC, Logbook::handle);
        r.playToServer(Cipher.TYPE, Cipher.CODEC, Cipher::handle);
        r.playToServer(Track.TYPE, Track.CODEC, Track::handle);
        r.playToServer(CubeSat.TYPE, CubeSat.CODEC, CubeSat::handle);
        r.playToServer(RadioMode.TYPE, RadioMode.CODEC, RadioMode::handle);
        r.playToServer(DtmfSet.TYPE, DtmfSet.CODEC, DtmfSet::handle);
        r.playToServer(DtmfKey.TYPE, DtmfKey.CODEC, DtmfKey::handle);
        r.playToServer(HandheldTone.TYPE, HandheldTone.CODEC, HandheldTone::handle);
        r.playToServer(VnaRequest.TYPE, VnaRequest.CODEC, VnaRequest::handle);
        r.playToClient(VnaData.TYPE, VnaData.CODEC, VnaData::handle);
    }

    // ---------------- DTMF: decoder codes, keypad presses ----------------
    public record DtmfSet(BlockPos pos, String on, String off, int mode, long hz) implements CustomPacketPayload {
        public static final Type<DtmfSet> TYPE = mk("dtmf_set");
        public static final StreamCodec<FriendlyByteBuf, DtmfSet> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.pos);
            b.writeUtf(m.on, 16);
            b.writeUtf(m.off, 16);
            b.writeVarInt(m.mode);
            b.writeVarLong(m.hz);
        }, b -> new DtmfSet(b.readBlockPos(), b.readUtf(16), b.readUtf(16), b.readVarInt(), b.readVarLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(DtmfSet m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                Player p = ctx.player();
                if (near(p, m.pos) && p.level().getBlockEntity(m.pos) instanceof com.sparkgap.pgtelegraph.block.DtmfDecoderBlockEntity be)
                    be.configure(m.on, m.off, m.mode, m.hz);
            });
        }
    }

    // ---------------- vector network analyser ----------------
    public record VnaRequest(BlockPos pos, long startHz, long stopHz) implements CustomPacketPayload {
        public static final Type<VnaRequest> TYPE = mk("vna_request");
        public static final StreamCodec<FriendlyByteBuf, VnaRequest> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.pos);
            b.writeVarLong(m.startHz);
            b.writeVarLong(m.stopHz);
        }, b -> new VnaRequest(b.readBlockPos(), b.readVarLong(), b.readVarLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(VnaRequest m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) com.sparkgap.pgtelegraph.item.VnaItem.sweep(sp, m.pos, m.startHz, m.stopHz);
            });
        }
    }

    public record VnaData(BlockPos pos, long startHz, long stopHz, float[] re, float[] im, String info) implements CustomPacketPayload {
        public static final Type<VnaData> TYPE = mk("vna_data");
        public static final StreamCodec<FriendlyByteBuf, VnaData> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.pos);
            b.writeVarLong(m.startHz);
            b.writeVarLong(m.stopHz);
            b.writeVarInt(m.re.length);
            for (int i = 0; i < m.re.length; i++) {
                b.writeFloat(m.re[i]);
                b.writeFloat(m.im[i]);
            }
            b.writeUtf(m.info, 200);
        }, b -> {
            BlockPos pos = b.readBlockPos();
            long s = b.readVarLong(), e = b.readVarLong();
            int n = Math.min(1001, b.readVarInt());
            float[] re = new float[n], im = new float[n];
            for (int i = 0; i < n; i++) {
                re[i] = b.readFloat();
                im[i] = b.readFloat();
            }
            return new VnaData(pos, s, e, re, im, b.readUtf(200));
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(VnaData m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.sparkgap.pgtelegraph.client.ClientAccess.vna(m));
        }
    }

    public record HandheldTone(boolean offHand, int tone) implements CustomPacketPayload {
        public static final Type<HandheldTone> TYPE = mk("handheld_tone");
        public static final StreamCodec<FriendlyByteBuf, HandheldTone> CODEC = StreamCodec.of((b, m) -> {
            b.writeBoolean(m.offHand);
            b.writeVarInt(m.tone);
        }, b -> new HandheldTone(b.readBoolean(), b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(HandheldTone m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) com.sparkgap.pgtelegraph.radio.HandheldRadio.setTone(sp, hand(m.offHand), m.tone);
            });
        }
    }

    public record DtmfKey(char c) implements CustomPacketPayload {
        public static final Type<DtmfKey> TYPE = mk("dtmf_key");
        public static final StreamCodec<FriendlyByteBuf, DtmfKey> CODEC = StreamCodec.of((b, m) -> b.writeChar(m.c), b -> new DtmfKey(b.readChar()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(DtmfKey m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) com.sparkgap.pgtelegraph.radio.HandheldRadio.dtmf(sp, m.c);
            });
        }
    }

    // ---------------- beacon program ----------------
    public record Beacon(BlockPos pos, String msg, int wpm, int mode, int gap) implements CustomPacketPayload {
        public static final Type<Beacon> TYPE = mk("beacon");
        public static final StreamCodec<FriendlyByteBuf, Beacon> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.pos);
            b.writeUtf(m.msg, 256);
            b.writeVarInt(m.wpm);
            b.writeVarInt(m.mode);
            b.writeVarInt(m.gap);
        }, b -> new Beacon(b.readBlockPos(), b.readUtf(256), b.readVarInt(), b.readVarInt(), b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Beacon m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                Player p = ctx.player();
                if (!near(p, m.pos) || !(p.level().getBlockEntity(m.pos) instanceof BeaconProgram.Holder h)) return;
                h.program().set(m.msg, m.wpm, m.mode, m.gap);
                h.programChanged();
            });
        }
    }

    // ---------------- dial of a hand-held item (direction finder) ----------------
    public record ItemTune(boolean offHand, long hz) implements CustomPacketPayload {
        public static final Type<ItemTune> TYPE = mk("item_tune");
        public static final StreamCodec<FriendlyByteBuf, ItemTune> CODEC = StreamCodec.of((b, m) -> {
            b.writeBoolean(m.offHand);
            b.writeVarLong(m.hz);
        }, b -> new ItemTune(b.readBoolean(), b.readVarLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(ItemTune m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                ItemStack s = ctx.player().getItemInHand(hand(m.offHand));
                if (s.getItem() instanceof MeterItem || s.getItem() instanceof com.sparkgap.pgtelegraph.media.BodycamItem
                        || s.getItem() instanceof com.sparkgap.pgtelegraph.media.FpvGogglesItem) {
                    s.set(PgTelegraph.ITEM_FREQ.get(), Math.max(10_000L, Math.min(30_000_000_000L, m.hz)));
                }
            });
        }
    }

    // ---------------- spectrum analyzer ----------------
    public static final int BINS = 160;

    public record SpectrumRequest(long centerHz, long spanHz) implements CustomPacketPayload {
        public static final Type<SpectrumRequest> TYPE = mk("spectrum_request");
        public static final StreamCodec<FriendlyByteBuf, SpectrumRequest> CODEC = StreamCodec.of((b, m) -> {
            b.writeVarLong(m.centerHz);
            b.writeVarLong(m.spanHz);
        }, b -> new SpectrumRequest(b.readVarLong(), b.readVarLong()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(SpectrumRequest m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p)) return;
                boolean holding = p.getMainHandItem().getItem() instanceof MeterItem a && a.kind == MeterItem.Kind.SPECTRUM
                        || p.getOffhandItem().getItem() instanceof MeterItem b && b.kind == MeterItem.Kind.SPECTRUM;
                if (!holding) return;
                long span = Math.max(1000, Math.min(10_000_000_000L, m.spanHz));
                long center = Math.max(span / 2, Math.min(30_000_000_000L, m.centerHz));
                PacketDistributor.sendToPlayer(p, new SpectrumData(center, span, sweep(p, center, span)));
            });
        }
    }

    private static final Reception.TerrainCache SA_CACHE = new Reception.TerrainCache();

    /** one sweep: dBm per bin, resolution bandwidth = bin width (at least 500 Hz), on a short whip */
    static float[] sweep(ServerPlayer p, long center, long span) {
        Level level = p.level();
        double binHz = span / (double) BINS, rbw = Math.max(RadioMath.FILTER_HZ, binHz);
        double lo = center - span / 2.0;
        boolean day = level.isDay(), rain = level.isRaining(), thunder = level.isThundering();
        double[] mw = new double[BINS];
        double rbwDb = 10 * Math.log10(rbw / RadioMath.FILTER_HZ);
        for (int i = 0; i < BINS; i++) {
            double f = (lo + (i + 0.5) * binHz) / 1e6;
            mw[i] = RadioMath.fromDb(RadioMath.noiseDbm(Math.max(0.01, f), day, rain, thunder) - 10 + rbwDb)
                    + RadioMath.fromDb(-130 + rbwDb);
        }
        long t = level.getGameTime();
        for (RadioWorld.Emission e : RadioWorld.on(level)) {
            double half = e.spreadHz() / 2;
            long obs = Reception.observedHz(level, e, p.blockPosition(), t);
            double f0 = obs - half, f1 = obs + half;
            if (f1 < lo || f0 > lo + span) continue;
            double fm = e.freqHz() / 1e6;
            AntennaLink rl = MeterItem.probe(p, null, fm);
            double w = RadioMath.fromDb(Reception.received(level, e, rl, fm, p.getId(), SA_CACHE, t, day, rain, thunder));
            if (e.spreadHz() <= 0) {
                int i = (int) Math.floor((obs - lo) / binHz);
                if (i >= 0 && i < BINS) mw[i] += w;
            } else {
                int i0 = Math.max(0, (int) Math.floor((f0 - lo) / binHz)), i1 = Math.min(BINS - 1, (int) Math.floor((f1 - lo) / binHz));
                double perHz = w / e.spreadHz();
                for (int i = i0; i <= i1; i++) mw[i] += perHz * Math.min(binHz, e.spreadHz()) * Math.min(1, rbw / binHz);
            }
        }
        float[] out = new float[BINS];
        for (int i = 0; i < BINS; i++) out[i] = (float) (10 * Math.log10(mw[i]));
        return out;
    }

    public record SpectrumData(long centerHz, long spanHz, float[] dbm) implements CustomPacketPayload {
        public static final Type<SpectrumData> TYPE = mk("spectrum_data");
        public static final StreamCodec<FriendlyByteBuf, SpectrumData> CODEC = StreamCodec.of((b, m) -> {
            b.writeVarLong(m.centerHz);
            b.writeVarLong(m.spanHz);
            b.writeVarInt(m.dbm.length);
            for (float v : m.dbm) b.writeFloat(v);
        }, b -> {
            long c = b.readVarLong(), s = b.readVarLong();
            int n = Math.min(1024, b.readVarInt());
            float[] d = new float[n];
            for (int i = 0; i < n; i++) d[i] = b.readFloat();
            return new SpectrumData(c, s, d);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(SpectrumData m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.sparkgap.pgtelegraph.client.ClientAccess.spectrum(m.centerHz, m.spanHz, m.dbm));
        }
    }

    // ---------------- teleprinter keyboard ----------------
    public record Teleprinter(BlockPos pos, int action, String text) implements CustomPacketPayload {
        public static final Type<Teleprinter> TYPE = mk("teleprinter");
        public static final StreamCodec<FriendlyByteBuf, Teleprinter> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.pos);
            b.writeVarInt(m.action);
            b.writeUtf(m.text, 256);
        }, b -> new Teleprinter(b.readBlockPos(), b.readVarInt(), b.readUtf(256)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Teleprinter m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                Player p = ctx.player();
                if (!near(p, m.pos) || !(p.level().getBlockEntity(m.pos) instanceof TeleprinterBlockEntity t)) return;
                if (m.action == 1) t.clearPaper();
                else t.type(m.text);
            });
        }
    }

    // ---------------- logbook ----------------
    public record Logbook(boolean offHand, int action, int index, String text) implements CustomPacketPayload {
        public static final Type<Logbook> TYPE = mk("logbook");
        public static final StreamCodec<FriendlyByteBuf, Logbook> CODEC = StreamCodec.of((b, m) -> {
            b.writeBoolean(m.offHand);
            b.writeVarInt(m.action);
            b.writeVarInt(m.index);
            b.writeUtf(m.text, 256);
        }, b -> new Logbook(b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readUtf(256)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Logbook m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer p) LogbookItem.apply(p, hand(m.offHand), m.action, m.index, m.text);
            });
        }
    }

    // ---------------- antenna tracking ----------------
    public record Track(BlockPos pos, int mode, String name) implements CustomPacketPayload {
        public static final Type<Track> TYPE = mk("track");
        public static final StreamCodec<FriendlyByteBuf, Track> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.pos);
            b.writeVarInt(m.mode);
            b.writeUtf(m.name, 32);
        }, b -> new Track(b.readBlockPos(), b.readVarInt(), b.readUtf(32)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Track m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                Player p = ctx.player();
                if (near(p, m.pos) && p.level().getBlockEntity(m.pos) instanceof com.sparkgap.pgtelegraph.block.VhfAntennaBlockEntity be) {
                    be.setTrack(m.mode, m.name);
                }
            });
        }
    }

    // ---------------- CW / FSK switch ----------------
    public record RadioMode(BlockPos pos, int mode) implements CustomPacketPayload {
        public static final Type<RadioMode> TYPE = mk("radio_mode");
        public static final StreamCodec<FriendlyByteBuf, RadioMode> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.pos);
            b.writeVarInt(m.mode);
        }, b -> new RadioMode(b.readBlockPos(), b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(RadioMode m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                Player p = ctx.player();
                if (!near(p, m.pos)) return;
                var be = p.level().getBlockEntity(m.pos);
                if (m.mode >= 600 && m.mode < 600 + com.sparkgap.pgtelegraph.block.QsoRobotBlockEntity.MODES) { // QSO robot operating mode
                    if (be instanceof com.sparkgap.pgtelegraph.block.QsoRobotBlockEntity q) q.setOpMode(m.mode - 600);
                } else if (m.mode >= 500 && m.mode < 500 + com.sparkgap.pgtelegraph.radio.Ctcss.COUNT) { // CTCSS tone
                    if (be instanceof com.sparkgap.pgtelegraph.block.RadioBlockEntity r) r.setCtcss(m.mode - 500);
                } else if (m.mode >= 300 && m.mode < 300 + com.sparkgap.pgtelegraph.media.SstvMode.values().length) { // SSTV mode
                    if (be instanceof com.sparkgap.pgtelegraph.media.SstvTransmitterBlockEntity sx) sx.setSstvMode(m.mode - 300);
                } else if (m.mode == 199) { // TV set power
                    if (be instanceof com.sparkgap.pgtelegraph.media.TvReceiverBlockEntity tv) tv.toggle();
                } else if (m.mode >= 100) { // a receiver knob: 100 + knob * 10 + value
                    if (be instanceof com.sparkgap.pgtelegraph.block.ReceiverBlockEntity rx) rx.setKnob((m.mode - 100) / 10, (m.mode - 100) % 10);
                } else if (be instanceof com.sparkgap.pgtelegraph.block.RadioBlockEntity r) r.setMode(m.mode);
            });
        }
    }

    // ---------------- CubeSat set-up ----------------
    public record CubeSat(boolean offHand, String cfg) implements CustomPacketPayload {
        public static final Type<CubeSat> TYPE = mk("cubesat");
        public static final StreamCodec<FriendlyByteBuf, CubeSat> CODEC = StreamCodec.of((b, m) -> {
            b.writeBoolean(m.offHand);
            b.writeUtf(m.cfg, 2048);
        }, b -> new CubeSat(b.readBoolean(), b.readUtf(2048)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(CubeSat m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                ItemStack s = ctx.player().getItemInHand(hand(m.offHand));
                if (s.getItem() instanceof com.sparkgap.pgtelegraph.sat.CubeSatItem) {
                    s.set(PgTelegraph.SAT_CONFIG.get(), com.sparkgap.pgtelegraph.sat.SatConfig.parse(m.cfg).serialize());
                }
            });
        }
    }

    // ---------------- cipher settings ----------------
    public record Cipher(boolean offHand, String settings) implements CustomPacketPayload {
        public static final Type<Cipher> TYPE = mk("cipher");
        public static final StreamCodec<FriendlyByteBuf, Cipher> CODEC = StreamCodec.of((b, m) -> {
            b.writeBoolean(m.offHand);
            b.writeUtf(m.settings, 128);
        }, b -> new Cipher(b.readBoolean(), b.readUtf(128)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Cipher m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                ItemStack s = ctx.player().getItemInHand(hand(m.offHand));
                if (s.getItem() instanceof CipherMachineItem) s.set(PgTelegraph.CIPHER_KEY.get(), m.settings);
            });
        }
    }
}
