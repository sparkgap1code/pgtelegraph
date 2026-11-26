package com.sparkgap.pgtelegraph.network;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.TelegraphInput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: operator input on a key/paddle. channel: see {@link TelegraphInput}. */
public record KeyPressPayload(BlockPos pos, int channel, boolean down) implements CustomPacketPayload {
    public static final Type<KeyPressPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "key_input"));

    public static final StreamCodec<FriendlyByteBuf, KeyPressPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, KeyPressPayload::pos,
            ByteBufCodecs.VAR_INT, KeyPressPayload::channel,
            ByteBufCodecs.BOOL, KeyPressPayload::down,
            KeyPressPayload::new);

    /** Max distance² from the player's eyes to the block centre. */
    private static final double MAX_DIST_SQ = 8 * 8;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KeyPressPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            var level = player.level();
            if (!level.isLoaded(msg.pos())) return;
            if (msg.down() && player.getEyePosition().distanceToSqr(msg.pos().getCenter()) > MAX_DIST_SQ) return;
            if (level.getBlockEntity(msg.pos()) instanceof TelegraphInput in) {
                in.input(msg.channel(), msg.down());
            }
        });
    }
}
