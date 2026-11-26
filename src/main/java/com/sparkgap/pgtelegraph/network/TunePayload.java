package com.sparkgap.pgtelegraph.network;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.RadioBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: set a radio's dial. */
public record TunePayload(BlockPos pos, long hz) implements CustomPacketPayload {
    public static final Type<TunePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "tune"));
    public static final StreamCodec<FriendlyByteBuf, TunePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TunePayload::pos, ByteBufCodecs.VAR_LONG, TunePayload::hz, TunePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TunePayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!player.level().isLoaded(msg.pos())) return;
            if (player.getEyePosition().distanceToSqr(msg.pos().getCenter()) > 16 * 16) return;
            var be = player.level().getBlockEntity(msg.pos());
            if (be instanceof com.sparkgap.pgtelegraph.block.Tunable r) r.setFreq(msg.hz());
            else if (be instanceof com.sparkgap.pgtelegraph.block.VhfAntennaBlockEntity v) v.setLoopFreq(msg.hz());
        });
    }
}
