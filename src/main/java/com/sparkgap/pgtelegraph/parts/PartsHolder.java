package com.sparkgap.pgtelegraph.parts;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/** A block entity with a parts bay. */
public interface PartsHolder {
    PartSlot.Layout layout();

    List<ItemStack> parts();

    void setParts(List<ItemStack> parts);

    static void openBlock(ServerPlayer player, BlockEntity be, PartsHolder h) {
        BlockPos pos = be.getBlockPos();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RadioPartsMenu(id, inv, h.layout(),
                        new RadioPartsMenu.Bay(h.parts(), h::setParts),
                        pl -> !be.isRemoved() && pl.distanceToSqr(pos.getCenter()) < 64),
                        be.getBlockState().getBlock().getName()),
                buf -> buf.writeVarInt(h.layout().ordinal()));
    }

    static void openHandheld(ServerPlayer player, InteractionHand hand) {
        ItemStack radio = player.getItemInHand(hand);
        if (!(radio.getItem() instanceof com.sparkgap.pgtelegraph.radio.HandheldRadioItem)) return;
        PartSlot.Layout layout = PartSlot.Layout.HANDHELD;
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new RadioPartsMenu(id, inv, layout,
                        new RadioPartsMenu.Bay(Chassis.of(radio, layout), items -> Chassis.store(radio, items)),
                        pl -> pl.getItemInHand(hand) == radio),
                        Component.translatable("item.pgtelegraph.handheld_radio")),
                buf -> buf.writeVarInt(layout.ordinal()));
    }
}
