package com.sparkgap.pgtelegraph.media;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Camera (카메라): takes a 320 × 256 photo of exactly what you see (the game's own picture, without
 * the hand and the HUD), on one sheet of paper from your inventory. The client reads its frame
 * and sends it up ({@link PicNet.Upload}); the server takes the paper and hands out the photo.
 */
public class CameraItem extends Item {
    public CameraItem(Properties p) {
        super(p.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (level.isClientSide) com.sparkgap.pgtelegraph.client.ClientAccess.takePhoto();
        player.getCooldowns().addCooldown(this, 30);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
}
