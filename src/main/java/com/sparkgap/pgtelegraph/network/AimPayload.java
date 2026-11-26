package com.sparkgap.pgtelegraph.network;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.VhfAntennaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: point a beam antenna (compass azimuth, elevation in degrees). */
public record AimPayload(BlockPos pos, double az, double el) implements CustomPacketPayload {
    public static final Type<AimPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "aim"));
    public static final StreamCodec<FriendlyByteBuf, AimPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AimPayload::pos, ByteBufCodecs.DOUBLE, AimPayload::az, ByteBufCodecs.DOUBLE, AimPayload::el,
            AimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AimPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!player.level().isLoaded(msg.pos()) || !Double.isFinite(msg.az()) || !Double.isFinite(msg.el())) return;
            if (player.getEyePosition().distanceToSqr(msg.pos().getCenter()) > 8 * 8) return;
            if (player.level().getBlockEntity(msg.pos()) instanceof VhfAntennaBlockEntity be) be.manualAim(msg.az(), msg.el());
        });
    }
}
