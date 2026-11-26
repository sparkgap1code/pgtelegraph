package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.TransmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Microphone speech up to the server, and received speech down to the players near a receiver. */
public final class VoiceNet {
    private VoiceNet() {}

    public static final int MAX = 1200;

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mk(String id) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, id));
    }

    public static void register(PayloadRegistrar r) {
        r.playToServer(In.TYPE, In.CODEC, In::handle);
        r.playToServer(Pack.TYPE, Pack.CODEC, Pack::handle);
        r.playToServer(Ptt.TYPE, Ptt.CODEC, Ptt::handle);
        r.playToClient(Out.TYPE, Out.CODEC, Out::handle);
    }

    /** 50 ms of mu-law speech into the manpack the sender wears */
    public record Pack(byte[] data) implements CustomPacketPayload {
        public static final Type<Pack> TYPE = mk("voice_pack");
        public static final StreamCodec<FriendlyByteBuf, Pack> CODEC = StreamCodec.of((b, m) -> b.writeByteArray(m.data),
                b -> new Pack(b.readByteArray(MAX)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Pack m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) return;
                if (com.sparkgap.pgtelegraph.radio.ManpackItem.worn(p) == null) return;
                VoiceAir.speak(level, com.sparkgap.pgtelegraph.radio.HandheldRadio.sourceKey(p.getUUID()), m.data);
            });
        }
    }

    /** the manpack wearer's push-to-talk key, pressed or let go (resent while held) */
    public record Ptt(boolean down) implements CustomPacketPayload {
        public static final Type<Ptt> TYPE = mk("ptt");
        public static final StreamCodec<FriendlyByteBuf, Ptt> CODEC = StreamCodec.of((b, m) -> b.writeBoolean(m.down), b -> new Ptt(b.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Ptt m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer p) com.sparkgap.pgtelegraph.radio.HandheldRadio.ptt(p, m.down);
            });
        }
    }

    /** 50 ms of mu-law speech from the microphone at {@code mic} */
    public record In(BlockPos mic, byte[] data) implements CustomPacketPayload {
        public static final Type<In> TYPE = mk("voice_in");
        public static final StreamCodec<FriendlyByteBuf, In> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.mic);
            b.writeByteArray(m.data);
        }, b -> new In(b.readBlockPos(), b.readByteArray(MAX)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(In m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) return;
                if (p.distanceToSqr(m.mic.getCenter()) > 36 || !level.isLoaded(m.mic)) return;
                if (level.getBlockEntity(m.mic) instanceof com.sparkgap.pgtelegraph.block.FieldPhoneBlockEntity phone) { // a telephone handset
                    phone.speak(m.data);
                    return;
                }
                if (!level.getBlockState(m.mic).is(PgTelegraph.MICROPHONE.get())) return;
                for (Direction d : Direction.values()) {
                    if (level.getBlockEntity(m.mic.relative(d)) instanceof com.sparkgap.pgtelegraph.block.SelectorBlockEntity sel) { // the chosen set
                        TransmitterBlockEntity t = sel.selectedTransmitter();
                        if (t != null && t.acceptsVoice()) VoiceAir.speak(level, t.getBlockPos(), m.data);
                    }
                    if (level.getBlockEntity(m.mic.relative(d)) instanceof TransmitterBlockEntity t && t.acceptsVoice()) {
                        VoiceAir.speak(level, t.getBlockPos(), m.data);
                    }
                    if (level.getBlockEntity(m.mic.relative(d)) instanceof com.sparkgap.pgtelegraph.block.AudioAmpBlockEntity a) a.micSpeech(m.data);
                }
            });
        }
    }

    /** speech heard by the receiver at {@code rx} with this signal-to-noise ratio */
    public record Out(BlockPos rx, byte[] data, float snr, boolean fm) implements CustomPacketPayload {
        public static final Type<Out> TYPE = mk("voice_out");
        public static final StreamCodec<FriendlyByteBuf, Out> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.rx);
            b.writeByteArray(m.data);
            b.writeFloat(m.snr);
            b.writeBoolean(m.fm);
        }, b -> new Out(b.readBlockPos(), b.readByteArray(MAX), b.readFloat(), b.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Out m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.sparkgap.pgtelegraph.client.ClientVoice.heard(m.rx, m.data, m.snr, m.fm));
        }
    }
}
