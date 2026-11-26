package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.sat.SstvMonitorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pictures over the network.
 * <ul>
 *   <li>{@link Upload}: a photo the client's camera took (its own screen), deflated, in pieces
 *   (a packet to the server holds at most 32 KiB)</li>
 *   <li>{@link Want} / {@link Data}: the picture of a photo, to look at it</li>
 *   <li>{@link MonitorWant} / {@link MonitorFull} / {@link MonitorRow}: an SSTV screen, whole when
 *   it is opened, then a line at a time as they come in</li>
 *   <li>{@link TexWanted} / {@link TexUpload}: the block texture table for the TV camera</li>
 * </ul>
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class PicNet {
    private PicNet() {}

    public static final int CHUNK = 30000, MAX_CHUNKS = 24, MAX_TEX_CHUNKS = 140;

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mk(String id) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, id));
    }

    public static void register(PayloadRegistrar r) {
        r.playToServer(Upload.TYPE, Upload.CODEC, Upload::handle);
        r.playToServer(Want.TYPE, Want.CODEC, Want::handle);
        r.playToClient(Data.TYPE, Data.CODEC, Data::handle);
        r.playToServer(MonitorWant.TYPE, MonitorWant.CODEC, MonitorWant::handle);
        r.playToClient(MonitorFull.TYPE, MonitorFull.CODEC, MonitorFull::handle);
        r.playToClient(MonitorRow.TYPE, MonitorRow.CODEC, MonitorRow::handle);
        r.playToClient(TexWanted.TYPE, TexWanted.CODEC, TexWanted::handle);
        r.playToServer(TexUpload.TYPE, TexUpload.CODEC, TexUpload::handle);
    }

    // ---- assembling pieces sent up by a player
    private static final class Parts {
        final int total;
        int next;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Parts(int total) {
            this.total = total;
        }
    }

    private static final Map<UUID, Parts> PHOTOS = new HashMap<>(), TEX = new HashMap<>();
    private static final Map<UUID, Long> LAST_PHOTO = new HashMap<>();
    private static UUID texFrom;

    /** adds a piece; the whole thing once the last one is in, else null */
    private static byte[] collect(Map<UUID, Parts> map, UUID who, int seq, int total, byte[] data, int maxChunks) {
        if (total < 1 || total > maxChunks || seq < 0 || seq >= total) {
            map.remove(who);
            return null;
        }
        Parts p = map.get(who);
        if (seq == 0 || p == null || p.total != total || p.next != seq) {
            if (seq != 0) {
                map.remove(who);
                return null;
            }
            p = new Parts(total);
            map.put(who, p);
        }
        p.bytes.write(data, 0, data.length);
        p.next++;
        if (p.next < total) return null;
        map.remove(who);
        return p.bytes.toByteArray();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.getServer() != null && !TexPalette.fresh(p.getServer()) && texFrom == null) {
            texFrom = p.getUUID();
            PacketDistributor.sendToPlayer(p, new TexWanted());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        PHOTOS.remove(id);
        TEX.remove(id);
        if (id.equals(texFrom)) texFrom = null;
    }

    /** a dropped photo burnt, despawned or fell into the void: its picture file goes too */
    @SubscribeEvent
    public static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity ie)) return;
        var why = ie.getRemovalReason();
        if (why != net.minecraft.world.entity.Entity.RemovalReason.DISCARDED && why != net.minecraft.world.entity.Entity.RemovalReason.KILLED) return;
        var st = ie.getItem(); // picked up or merged: the stack is empty by now
        if (st.isEmpty() || !st.is(com.sparkgap.pgtelegraph.PgTelegraph.PHOTO_ITEM.get())) return;
        Photo p = st.get(com.sparkgap.pgtelegraph.PgTelegraph.PHOTO.get());
        if (p != null && !p.id().isEmpty() && e.getLevel().getServer() != null) PhotoStore.delete(e.getLevel().getServer(), p.id());
    }

    /** /pgtexture: rebuild the TV camera's texture table from your client (after changing the resource pack) */
    @SubscribeEvent
    public static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
        VideoCommand.register(e.getDispatcher());
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("pgtexture").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            if (texFrom != null) return 0;
            TexPalette.invalidate(p.getServer());
            texFrom = p.getUUID();
            PacketDistributor.sendToPlayer(p, new TexWanted());
            p.displayClientMessage(Component.translatable("pgtelegraph.tex.building"), false);
            return 1;
        }));
    }

    @SubscribeEvent
    public static void onStarted(net.neoforged.neoforge.event.server.ServerStartedEvent e) {
        PhotoStore.purge(e.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        PHOTOS.clear();
        TEX.clear();
        LAST_PHOTO.clear();
        texFrom = null;
        TexPalette.reset();
    }

    /** a piece of a photo the client's camera took */
    public record Upload(int seq, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<Upload> TYPE = mk("photo_up");
        public static final StreamCodec<FriendlyByteBuf, Upload> CODEC = StreamCodec.of((b, m) -> {
            b.writeVarInt(m.seq);
            b.writeVarInt(m.total);
            b.writeByteArray(m.data);
        }, b -> new Upload(b.readVarInt(), b.readVarInt(), b.readByteArray(CHUNK)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Upload m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p) || p.getServer() == null) return;
                byte[] all = collect(PHOTOS, p.getUUID(), m.seq, m.total, m.data, MAX_CHUNKS);
                if (all == null) return;
                long now = p.serverLevel().getGameTime();
                Long last = LAST_PHOTO.get(p.getUUID());
                if (last != null && now - last < 20) return;
                if (!p.getInventory().contains(new ItemStack(PgTelegraph.CAMERA.get()))) return;
                byte[] img = Pic.inflate(all, Pic.BYTES);
                if (img == null || img.length != Pic.BYTES) return;
                if (!p.getAbilities().instabuild) {
                    int slot = -1;
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                        if (p.getInventory().getItem(i).is(Items.PAPER)) {
                            slot = i;
                            break;
                        }
                    }
                    if (slot < 0) {
                        p.displayClientMessage(Component.translatable("pgtelegraph.camera.no_paper").withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    p.getInventory().getItem(slot).shrink(1);
                }
                LAST_PHOTO.put(p.getUUID(), now);
                String id = PhotoStore.save(p.getServer(), img);
                long day = p.level().getDayTime() / 24000 + 1;
                ItemStack photo = new ItemStack(PgTelegraph.PHOTO_ITEM.get());
                photo.set(PgTelegraph.PHOTO.get(), new Photo(id, p.getName().getString() + " · Day " + day));
                if (!p.getInventory().add(photo)) p.drop(photo, false);
                p.level().playSound(null, p.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1.6f);
            });
        }
    }

    /** the client wants the picture of photo {@code id} */
    public record Want(String id) implements CustomPacketPayload {
        public static final Type<Want> TYPE = mk("photo_want");
        public static final StreamCodec<FriendlyByteBuf, Want> CODEC = StreamCodec.of((b, m) -> b.writeUtf(m.id, 64),
                b -> new Want(b.readUtf(64)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Want m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p) || p.getServer() == null) return;
                byte[] z = PhotoStore.loadDeflated(p.getServer(), m.id);
                if (z != null && z.length < 900_000) PacketDistributor.sendToPlayer(p, new Data(m.id, z));
            });
        }
    }

    public record Data(String id, byte[] deflated) implements CustomPacketPayload {
        public static final Type<Data> TYPE = mk("photo_data");
        public static final StreamCodec<FriendlyByteBuf, Data> CODEC = StreamCodec.of((b, m) -> {
            b.writeUtf(m.id, 64);
            b.writeByteArray(m.deflated);
        }, b -> new Data(b.readUtf(64), b.readByteArray(1_000_000)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Data m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.sparkgap.pgtelegraph.client.ClientPics.photoArrived(m.id, m.deflated));
        }
    }

    /** an SSTV screen was opened: send the whole picture */
    public record MonitorWant(BlockPos pos) implements CustomPacketPayload {
        public static final Type<MonitorWant> TYPE = mk("sstv_want");
        public static final StreamCodec<FriendlyByteBuf, MonitorWant> CODEC = StreamCodec.of((b, m) -> b.writeBlockPos(m.pos),
                b -> new MonitorWant(b.readBlockPos()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(MonitorWant m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p) || p.distanceToSqr(m.pos.getCenter()) > 64 * 64
                        || !p.level().isLoaded(m.pos)) return;
                if (p.level().getBlockEntity(m.pos) instanceof SstvMonitorBlockEntity be) {
                    PacketDistributor.sendToPlayer(p, new MonitorFull(m.pos, be.imgW, be.imgH, Pic.deflate(be.image)));
                }
            });
        }
    }

    public record MonitorFull(BlockPos pos, int w, int h, byte[] deflated) implements CustomPacketPayload {
        public static final Type<MonitorFull> TYPE = mk("sstv_full");
        public static final StreamCodec<FriendlyByteBuf, MonitorFull> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.pos);
            b.writeVarInt(m.w);
            b.writeVarInt(m.h);
            b.writeByteArray(m.deflated);
        }, b -> new MonitorFull(b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readByteArray(2_000_000)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(MonitorFull m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.sparkgap.pgtelegraph.client.ClientPics.monitorFull(m.pos, m.w, m.h, m.deflated));
        }
    }

    public record MonitorRow(BlockPos pos, int row, int h, byte[] data) implements CustomPacketPayload {
        public static final Type<MonitorRow> TYPE = mk("sstv_row");
        public static final StreamCodec<FriendlyByteBuf, MonitorRow> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.pos);
            b.writeVarInt(m.row);
            b.writeVarInt(m.h);
            b.writeByteArray(m.data);
        }, b -> new MonitorRow(b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readByteArray(SstvMode.MAX_W * 2)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(MonitorRow m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.sparkgap.pgtelegraph.client.ClientPics.monitorRow(m.pos, m.row, m.h, m.data));
        }
    }

    /** send one painted line to everyone who has the monitor's chunk loaded */
    public static void sendRow(ServerLevel level, BlockPos pos, int row, int h, byte[] data) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), new MonitorRow(pos, row, h, data));
    }

    /** the server has no texture table yet: please build one */
    public record TexWanted() implements CustomPacketPayload {
        public static final Type<TexWanted> TYPE = mk("tex_wanted");
        public static final StreamCodec<FriendlyByteBuf, TexWanted> CODEC = StreamCodec.unit(new TexWanted());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(TexWanted m, IPayloadContext ctx) {
            ctx.enqueueWork(com.sparkgap.pgtelegraph.client.ClientTex::buildAndSend);
        }
    }

    public record TexUpload(int seq, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<TexUpload> TYPE = mk("tex_up");
        public static final StreamCodec<FriendlyByteBuf, TexUpload> CODEC = StreamCodec.of((b, m) -> {
            b.writeVarInt(m.seq);
            b.writeVarInt(m.total);
            b.writeByteArray(m.data);
        }, b -> new TexUpload(b.readVarInt(), b.readVarInt(), b.readByteArray(CHUNK)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(TexUpload m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p) || p.getServer() == null || !p.getUUID().equals(texFrom)) return;
                byte[] all = collect(TEX, p.getUUID(), m.seq, m.total, m.data, MAX_TEX_CHUNKS);
                if (all == null) return;
                int n = TexPalette.accept(p.getServer(), all);
                texFrom = null;
                if (n > 0) p.displayClientMessage(Component.translatable("pgtelegraph.tex.updated", n).withStyle(ChatFormatting.GREEN), false);
            });
        }
    }
}
