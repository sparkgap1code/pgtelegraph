package com.sparkgap.pgtelegraph.network;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: what the player's handheld speaker should play. */
public record HandheldStatePayload(boolean on, boolean keyed, int toneHz, float toneVol, float noiseVol) implements CustomPacketPayload {
    public static final Type<HandheldStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "handheld_state"));
    public static final StreamCodec<FriendlyByteBuf, HandheldStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, HandheldStatePayload::on,
            ByteBufCodecs.BOOL, HandheldStatePayload::keyed,
            ByteBufCodecs.VAR_INT, HandheldStatePayload::toneHz,
            ByteBufCodecs.FLOAT, HandheldStatePayload::toneVol,
            ByteBufCodecs.FLOAT, HandheldStatePayload::noiseVol,
            HandheldStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HandheldStatePayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientAccess.handheldState(msg));
    }
}
