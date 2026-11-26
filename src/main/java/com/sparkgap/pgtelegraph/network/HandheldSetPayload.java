package com.sparkgap.pgtelegraph.network;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.radio.HandheldRadio;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: handheld front-panel settings for the radio in a hand. */
public record HandheldSetPayload(boolean offHand, long hz, int power, int squelch, boolean on, boolean paddle, int wpm)
        implements CustomPacketPayload {
    public static final Type<HandheldSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "handheld_set"));
    public static final StreamCodec<FriendlyByteBuf, HandheldSetPayload> STREAM_CODEC = StreamCodec.of(
            (buf, m) -> {
                buf.writeBoolean(m.offHand);
                buf.writeVarLong(m.hz);
                buf.writeVarInt(m.power);
                buf.writeVarInt(m.squelch);
                buf.writeBoolean(m.on);
                buf.writeBoolean(m.paddle);
                buf.writeVarInt(m.wpm);
            },
            buf -> new HandheldSetPayload(buf.readBoolean(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HandheldSetPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                HandheldRadio.applySettings(p, msg.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                        msg.hz(), msg.power(), msg.squelch(), msg.on(), msg.paddle(), msg.wpm());
            }
        });
    }
}
