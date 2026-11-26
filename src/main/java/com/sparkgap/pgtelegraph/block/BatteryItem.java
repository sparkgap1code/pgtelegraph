package com.sparkgap.pgtelegraph.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Locale;

/** Battery pack item: shows the charge carried over from the placed block. */
public class BatteryItem extends BlockItem {
    public BatteryItem(BatteryBlock block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        BatteryBlockEntity.Chem c = ((BatteryBlock) getBlock()).chem;
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag t = d == null ? new CompoundTag() : d.copyTag();
        double soc = t.contains("Soc") ? t.getDouble("Soc") : 0.5, health = t.contains("Health") ? t.getDouble("Health") : 1;
        tip.add(Component.literal(String.format(Locale.ROOT, "%.1f-%.1f V, %.0f Ah, max %.0f A",
                c.ocv(0), c.ocv(1), c.ah, c.maxA)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal(String.format(Locale.ROOT, "SOC %.0f %%", soc * 100)).withStyle(ChatFormatting.GREEN));
        if (health < 0.999) tip.add(Component.translatable("pgtelegraph.battery.health")
                .append(String.format(Locale.ROOT, ": %.0f %%", health * 100)).withStyle(ChatFormatting.YELLOW));
        tip.add(Component.translatable("pgtelegraph.battery." + c.key() + ".hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        double soc = d != null && d.copyTag().contains("Soc") ? d.copyTag().getDouble("Soc") : 0.5;
        return (int) Math.round(13 * Math.max(0, Math.min(1, soc)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x40D040;
    }
}
