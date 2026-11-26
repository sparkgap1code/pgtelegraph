package com.sparkgap.pgtelegraph.network;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.radio.PaddleInput;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: paddle levers of the item in the main hand (handheld in paddle mode, clip-lead
 * paddle). Sent on every change and repeated as a keep-alive while a lever is held.
 * wpmDelta != 0 instead changes the speed of that item's keyer (sneak + mouse wheel).
 */
public record PaddleInputPayload(boolean dit, boolean dah, int wpmDelta) implements CustomPacketPayload {
    public static final Type<PaddleInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "paddle_input"));
    public static final StreamCodec<FriendlyByteBuf, PaddleInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PaddleInputPayload::dit,
            ByteBufCodecs.BOOL, PaddleInputPayload::dah,
            ByteBufCodecs.VAR_INT, PaddleInputPayload::wpmDelta,
            PaddleInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PaddleInputPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer p)) return;
            if (msg.wpmDelta() != 0) PaddleInput.changeWpm(p, Integer.signum(msg.wpmDelta()));
            else PaddleInput.update(p, msg.dit(), msg.dah());
        });
    }
}
