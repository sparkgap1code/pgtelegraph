package com.sparkgap.pgtelegraph.item;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** QSL card (QSL 카드): confirmation of a contact, filled in from a logbook entry. Blank ones stack. */
public class QslCardItem extends Item {
    public QslCardItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    public static String text(ItemStack s) {
        return s.get(PgTelegraph.QSL.get());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return text(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        String t = text(stack);
        if (t == null) {
            tip.add(Component.translatable("item.pgtelegraph.qsl_card.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (String part : t.split(" \\| ")) tip.add(Component.literal(part).withStyle(ChatFormatting.AQUA));
    }
}
