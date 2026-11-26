package com.sparkgap.pgtelegraph.network;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.parts.PartsHolder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: open the parts bay of the handheld in a hand. */
public record OpenPartsPayload(boolean offHand) implements CustomPacketPayload {
    public static final Type<OpenPartsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "open_parts"));
    public static final StreamCodec<FriendlyByteBuf, OpenPartsPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, OpenPartsPayload::offHand, OpenPartsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenPartsPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                PartsHolder.openHandheld(p, msg.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            }
        });
    }
}
