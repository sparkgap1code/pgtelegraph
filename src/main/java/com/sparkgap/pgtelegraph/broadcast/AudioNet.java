package com.sparkgap.pgtelegraph.broadcast;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Moving recordings around: a client uploads an imported file in 30 kB pieces (it becomes a
 * recorded disc), and clients that tune a radio to it download it in 256 kB pieces.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class AudioNet {
    private AudioNet() {}

    public static final int UP_CHUNK = 30_000, DOWN_CHUNK = 256 * 1024;

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mk(String id) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, id));
    }

    public static void register(PayloadRegistrar r) {
        r.playToServer(Upload.TYPE, Upload.CODEC, Upload::handle);
        r.playToServer(Request.TYPE, Request.CODEC, Request::handle);
        r.playToClient(Chunk.TYPE, Chunk.CODEC, Chunk::handle);
    }

    // ---------------- upload ----------------
    private static final class Pending {
        final ByteArrayOutputStream data = new ByteArrayOutputStream();
        int next, total;
        String title;
        boolean offHand;
    }

    private static final Map<UUID, Pending> UPLOADS = new HashMap<>();

    public record Upload(boolean offHand, int index, int total, String title, byte[] data) implements CustomPacketPayload {
        public static final Type<Upload> TYPE = mk("audio_upload");
        public static final StreamCodec<FriendlyByteBuf, Upload> CODEC = StreamCodec.of((b, m) -> {
            b.writeBoolean(m.offHand);
            b.writeVarInt(m.index);
            b.writeVarInt(m.total);
            b.writeUtf(m.title, 64);
            b.writeByteArray(m.data);
        }, b -> new Upload(b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readUtf(64), b.readByteArray(UP_CHUNK)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Upload m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p)) return;
                int max = (AudioStore.MAX_BYTES + UP_CHUNK - 1) / UP_CHUNK;
                if (m.total <= 0 || m.total > max) return;
                Pending u = UPLOADS.get(p.getUUID());
                if (m.index == 0) {
                    u = new Pending();
                    u.total = m.total;
                    u.title = m.title.isBlank() ? "Recording" : m.title;
                    u.offHand = m.offHand;
                    UPLOADS.put(p.getUUID(), u);
                }
                if (u == null || m.index != u.next || m.total != u.total) {
                    UPLOADS.remove(p.getUUID());
                    return;
                }
                u.data.writeBytes(m.data);
                u.next++;
                if (u.next < u.total) return;
                UPLOADS.remove(p.getUUID());
                InteractionHand hand = u.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                ItemStack held = p.getItemInHand(hand);
                if (!held.is(PgTelegraph.BLANK_RECORD.get())) {
                    p.displayClientMessage(Component.translatable("pgtelegraph.record.need_blank").withStyle(ChatFormatting.RED), false);
                    return;
                }
                byte[] pcm = u.data.toByteArray();
                try {
                    String id = AudioStore.save(p.server, pcm);
                    held.shrink(1);
                    ItemStack disc = new ItemStack(PgTelegraph.RECORDED_DISC.get());
                    disc.set(PgTelegraph.AUDIO.get(), new RecordItems.Audio(id, pcm.length / AudioStore.RATE, u.title).encode());
                    if (!p.getInventory().add(disc)) p.drop(disc, false);
                    p.displayClientMessage(Component.translatable("pgtelegraph.record.done", u.title).withStyle(ChatFormatting.GREEN), false);
                } catch (Exception e) {
                    p.displayClientMessage(Component.literal(e.toString()).withStyle(ChatFormatting.RED), false);
                }
            });
        }
    }

    // ---------------- download ----------------
    private record Outgoing(ServerPlayer player, String id, byte[] data, int[] next) {}

    private static final ArrayDeque<Outgoing> OUT = new ArrayDeque<>();

    public record Request(String id) implements CustomPacketPayload {
        public static final Type<Request> TYPE = mk("audio_request");
        public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.of((b, m) -> b.writeUtf(m.id, 64),
                b -> new Request(b.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Request m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p)) return;
                byte[] data = AudioStore.load(p.server, m.id);
                if (data == null) {
                    com.sparkgap.pgtelegraph.PgTelegraph.LOGGER.warn("[pgtelegraph] recording {} asked for by {} but not found on the server", m.id,
                            p.getGameProfile().getName());
                    return;
                }
                for (Outgoing o : OUT) if (o.player == p && o.id.equals(m.id)) return;
                OUT.add(new Outgoing(p, m.id, data, new int[]{0}));
            });
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        // two pieces a tick (~10 MB/s at most)
        for (int n = 0; n < 2 && !OUT.isEmpty(); n++) {
            Outgoing o = OUT.peek();
            if (o.player.hasDisconnected()) {
                OUT.poll();
                continue;
            }
            int total = Math.max(1, (o.data.length + DOWN_CHUNK - 1) / DOWN_CHUNK);
            int i = o.next[0]++;
            int from = i * DOWN_CHUNK, to = Math.min(o.data.length, from + DOWN_CHUNK);
            byte[] part = java.util.Arrays.copyOfRange(o.data, from, Math.max(from, to));
            PacketDistributor.sendToPlayer(o.player, new Chunk(o.id, i, total, o.data.length, part));
            if (o.next[0] >= total) OUT.poll();
        }
    }

    public record Chunk(String id, int index, int total, int size, byte[] data) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = mk("audio_chunk");
        public static final StreamCodec<FriendlyByteBuf, Chunk> CODEC = StreamCodec.of((b, m) -> {
            b.writeUtf(m.id, 64);
            b.writeVarInt(m.index);
            b.writeVarInt(m.total);
            b.writeVarInt(m.size);
            b.writeByteArray(m.data);
        }, b -> new Chunk(b.readUtf(64), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByteArray(DOWN_CHUNK)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Chunk m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.sparkgap.pgtelegraph.client.ClientAudio.chunk(m.id, m.index, m.total, m.size, m.data));
        }
    }
}
