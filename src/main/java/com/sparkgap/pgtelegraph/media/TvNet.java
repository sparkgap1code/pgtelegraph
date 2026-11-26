package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Live TV pictures on the wire: the server asks a client to film a camera ({@link Film}), the
 * client sends JPEG frames up in pieces ({@link Up}), and receivers get them down whole
 * ({@link Down}).
 */
public final class TvNet {
    private TvNet() {}

    public static final int CHUNK = 30000, MAX_BYTES = 600_000;

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mk(String id) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, id));
    }

    public static void register(PayloadRegistrar r) {
        r.playToClient(Film.TYPE, Film.CODEC, Film::handle);
        r.playToServer(Up.TYPE, Up.CODEC, Up::handle);
        r.playToClient(Down.TYPE, Down.CODEC, Down::handle);
        r.playToClient(Goggles.TYPE, Goggles.CODEC, Goggles::handle);
    }

    /** server -> client: film this camera (eye, yaw, pitch) for the next {@code ticks} */
    public record Film(BlockPos cam, double x, double y, double z, float yaw, float pitch, int ticks) implements CustomPacketPayload {
        public static final Type<Film> TYPE = mk("tv_film");
        public static final StreamCodec<FriendlyByteBuf, Film> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.cam);
            b.writeDouble(m.x);
            b.writeDouble(m.y);
            b.writeDouble(m.z);
            b.writeFloat(m.yaw);
            b.writeFloat(m.pitch);
            b.writeVarInt(m.ticks);
        }, b -> new Film(b.readBlockPos(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Film m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.sparkgap.pgtelegraph.client.ClientAccess.tvFilm(m));
        }
    }

    /** client -> server: a piece of a filmed frame */
    public record Up(BlockPos cam, int seq, int idx, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<Up> TYPE = mk("tv_up");
        public static final StreamCodec<FriendlyByteBuf, Up> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.cam);
            b.writeVarInt(m.seq);
            b.writeVarInt(m.idx);
            b.writeVarInt(m.total);
            b.writeByteArray(m.data);
        }, b -> new Up(b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByteArray(CHUNK)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        private static final Map<UUID, Map<BlockPos, Parts>> PARTS = new HashMap<>();

        private static final class Parts {
            int seq, next;
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        }

        static void handle(Up m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p) || m.total <= 0 || m.total * CHUNK > MAX_BYTES) return;
                Map<BlockPos, Parts> mine = PARTS.computeIfAbsent(p.getUUID(), u -> new HashMap<>());
                Parts parts = mine.get(m.cam);
                if (m.idx == 0) {
                    parts = new Parts();
                    parts.seq = m.seq;
                    mine.put(m.cam, parts);
                }
                if (parts == null || parts.seq != m.seq || parts.next != m.idx) {
                    mine.remove(m.cam);
                    return;
                }
                parts.bytes.writeBytes(m.data);
                parts.next++;
                if (parts.next < m.total) return;
                mine.remove(m.cam);
                TvFrames.uploaded(p, m.cam, parts.bytes.toByteArray());
            });
        }
    }

    /** server -> the wearer: what reaches the FPV goggles */
    public record Goggles(boolean tuned, float snr, float ghost, float interf, int interfDf) implements CustomPacketPayload {
        public static final Type<Goggles> TYPE = mk("fpv");
        public static final StreamCodec<FriendlyByteBuf, Goggles> CODEC = StreamCodec.of((b, m) -> {
            b.writeBoolean(m.tuned);
            b.writeFloat(m.snr);
            b.writeFloat(m.ghost);
            b.writeFloat(m.interf);
            b.writeVarInt(m.interfDf);
        }, b -> new Goggles(b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Goggles m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.sparkgap.pgtelegraph.client.ClientAccess.fpv(m));
        }
    }

    /** server -> client: the picture a TV receiver is showing */
    public record Down(BlockPos rx, int seq, byte[] jpeg) implements CustomPacketPayload {
        public static final Type<Down> TYPE = mk("tv_down");
        public static final StreamCodec<FriendlyByteBuf, Down> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.rx);
            b.writeVarInt(m.seq);
            b.writeByteArray(m.jpeg);
        }, b -> new Down(b.readBlockPos(), b.readVarInt(), b.readByteArray(MAX_BYTES)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Down m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.sparkgap.pgtelegraph.client.ClientAccess.tvDown(m));
        }
    }
}
