package com.sparkgap.pgtelegraph.network;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: the channel the player's handheld hears (see RxChannel), for its simulated speaker. */
public record HandheldRxPayload(CompoundTag channel) implements CustomPacketPayload {
    public static final Type<HandheldRxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "handheld_rx"));
    public static final StreamCodec<FriendlyByteBuf, HandheldRxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, HandheldRxPayload::channel, HandheldRxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HandheldRxPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientAccess.handheldRx(msg.channel()));
    }
}
