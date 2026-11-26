package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
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

/** Photo (사진): use to look at it; put it on an SSTV transmitter to send it. */
public class PhotoItem extends Item {
    public PhotoItem(Properties p) {
        super(p.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        Photo ph = s.get(PgTelegraph.PHOTO.get());
        if (ph == null) return InteractionResultHolder.pass(s);
        if (level.isClientSide) com.sparkgap.pgtelegraph.client.ClientAccess.openPhoto(ph);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        Photo ph = stack.get(PgTelegraph.PHOTO.get());
        if (ph != null && !ph.caption().isEmpty()) tip.add(Component.literal(ph.caption()).withStyle(ChatFormatting.GRAY));
    }
}
