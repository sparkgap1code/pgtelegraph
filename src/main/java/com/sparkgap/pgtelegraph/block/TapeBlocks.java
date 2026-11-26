package com.sparkgap.pgtelegraph.block;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class TapeBlocks {
    private TapeBlocks() {}

    /** Into the empty main hand if possible, otherwise the inventory (or dropped). */
    static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (player.getMainHandItem().isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        else player.getInventory().placeItemBackInInventory(stack);
    }
}
