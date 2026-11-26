package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** A radio picked up as an item shows the parts still fitted inside it (or that it is empty). */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT)
public final class PartsTooltip {
    private PartsTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        if (!(s.getItem() instanceof BlockItem bi)
                || !(bi.getBlock() instanceof com.sparkgap.pgtelegraph.block.TransmitterBlock
                || bi.getBlock() instanceof com.sparkgap.pgtelegraph.block.ReceiverBlock)) return;
        var c = s.get(DataComponents.CONTAINER);
        MutableComponent names = Component.empty();
        int n = 0;
        if (c != null) {
            for (ItemStack p : c.nonEmptyItems()) {
                if (n++ > 0) names.append(", ");
                names.append(p.getHoverName());
            }
        }
        e.getToolTip().add(1, n == 0
                ? Component.translatable("pgtelegraph.radio.parts_none").withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("pgtelegraph.radio.parts_inside", names).withStyle(ChatFormatting.GRAY));
    }
}
