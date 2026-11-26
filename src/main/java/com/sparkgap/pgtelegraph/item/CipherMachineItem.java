package com.sparkgap.pgtelegraph.item;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Rotor cipher machine (회전자 암호기): use to open; the settings are kept on the item. */
public class CipherMachineItem extends Item {
    public CipherMachineItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static String settings(ItemStack s) {
        String v = s.get(PgTelegraph.CIPHER_KEY.get());
        return v == null ? RotorCipher.Settings.DEFAULT : v;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (level.isClientSide) ClientAccess.openCipher(hand, settings(s));
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(settings(stack)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
