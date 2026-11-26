package com.sparkgap.pgtelegraph.network;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.PerforatorBlock;
import com.sparkgap.pgtelegraph.tape.Morse;
import com.sparkgap.pgtelegraph.tape.PaperTapeItem;
import com.sparkgap.pgtelegraph.tape.TapeData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Client -> server: punch this text onto the blank tape the player is holding. */
public record PerforatePayload(BlockPos pos, String text) implements CustomPacketPayload {
    public static final int MAX_CHARS = 120;

    public static final Type<PerforatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "perforate"));

    public static final StreamCodec<FriendlyByteBuf, PerforatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PerforatePayload::pos,
            ByteBufCodecs.stringUtf8(MAX_CHARS * 4), PerforatePayload::text,
            PerforatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PerforatePayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            var level = player.level();
            if (!level.isLoaded(msg.pos())) return;
            if (!(level.getBlockState(msg.pos()).getBlock() instanceof PerforatorBlock)) return;
            if (player.getEyePosition().distanceToSqr(msg.pos().getCenter()) > 8 * 8) return;

            String text = msg.text().strip();
            if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS);
            List<Integer> runs = Morse.encodeUnits(Morse.codes(text));
            if (runs.isEmpty() || runs.size() > TapeData.MAX_RUNS) return;

            InteractionHand hand = PaperTapeItem.isBlank(player.getMainHandItem()) ? InteractionHand.MAIN_HAND
                    : PaperTapeItem.isBlank(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
            if (hand == null) return;
            ItemStack blank = player.getItemInHand(hand);
            ItemStack punched = PaperTapeItem.of(new TapeData(TapeData.PUNCHED, runs, text));
            if (blank.getCount() == 1) {
                player.setItemInHand(hand, punched);
            } else {
                blank.shrink(1);
                player.getInventory().placeItemBackInInventory(punched);
            }
            level.playSound(null, msg.pos(), SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 0.8f, 1.4f);
        });
    }
}
