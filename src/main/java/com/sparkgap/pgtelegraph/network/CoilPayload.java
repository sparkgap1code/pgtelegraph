package com.sparkgap.pgtelegraph.network;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.LoadingCoilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: set a loading coil's inductance (mH). */
public record CoilPayload(BlockPos pos, double mH) implements CustomPacketPayload {
    public static final Type<CoilPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "coil"));
    public static final StreamCodec<FriendlyByteBuf, CoilPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CoilPayload::pos, ByteBufCodecs.DOUBLE, CoilPayload::mH, CoilPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CoilPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!player.level().isLoaded(msg.pos()) || !Double.isFinite(msg.mH())) return;
            if (player.getEyePosition().distanceToSqr(msg.pos().getCenter()) > 8 * 8) return;
            if (player.level().getBlockEntity(msg.pos()) instanceof LoadingCoilBlockEntity be) be.setInductance(msg.mH());
        });
    }
}
