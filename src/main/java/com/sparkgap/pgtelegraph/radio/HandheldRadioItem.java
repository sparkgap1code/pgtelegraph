package com.sparkgap.pgtelegraph.radio;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Handheld VHF/UHF CW transceiver (휴대용 무전기). Hold right-click to key (CW), sneak + right-click
 * for the front panel. Receives whenever it is switched on and anywhere in the inventory.
 */
public class HandheldRadioItem extends Item {
    public HandheldRadioItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static HandheldData data(ItemStack stack) {
        HandheldData d = stack.get(PgTelegraph.HANDHELD.get());
        return d == null ? HandheldData.DEFAULT : d;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) ClientAccess.openHandheld(hand, data(stack), batteryPercent(stack), this instanceof ManpackItem);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        HandheldData d = data(stack);
        if (d.paddle()) return InteractionResultHolder.pass(stack); // levers are read by the client handler
        if (!d.on() || batteryPercent(stack) <= 0) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand); // keyed while held
        return InteractionResultHolder.consume(stack);
    }

    /** charge of the pack inside, 0-100 (-1 = no pack) */
    public static int batteryPercent(ItemStack radio) {
        var parts = com.sparkgap.pgtelegraph.parts.Chassis.of(radio, com.sparkgap.pgtelegraph.parts.PartSlot.Layout.HANDHELD);
        ItemStack pack = parts.get(com.sparkgap.pgtelegraph.parts.PartSlot.Layout.HANDHELD.indexOf(com.sparkgap.pgtelegraph.parts.PartSlot.BATTERY));
        var def = com.sparkgap.pgtelegraph.parts.PartItem.def(pack);
        var b = com.sparkgap.pgtelegraph.parts.PartItem.battery(pack);
        return def == null || b == null ? -1 : (int) Math.round(100 * b.fraction(def));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        HandheldData d = data(stack);
        tip.add(Component.literal(com.sparkgap.pgtelegraph.block.RadioBlockEntity.formatFreq(d.freqHz()))
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("  " + (this instanceof ManpackItem ? ManpackItem.POWER_W : HandheldData.POWER_W)[d.power()] + " W").withStyle(ChatFormatting.GRAY)));
        tip.add(Component.translatable(d.on() ? "pgtelegraph.handheld.on" : "pgtelegraph.handheld.off")
                .withStyle(d.on() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                .append(Component.literal("  ").append(batteryPercent(stack) < 0 ? Component.translatable("pgtelegraph.handheld.no_battery")
                                : Component.translatable("pgtelegraph.handheld.battery", batteryPercent(stack)))
                        .withStyle(batteryPercent(stack) > 20 ? ChatFormatting.GRAY : ChatFormatting.RED)));
        tip.add(d.paddle() ? Component.translatable("pgtelegraph.handheld.mode.paddle_wpm", d.wpm()).withStyle(ChatFormatting.GOLD)
                : Component.translatable("pgtelegraph.handheld.mode.straight").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("pgtelegraph.handheld.parts_hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return batteryPercent(stack) < 100;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * Math.max(0, batteryPercent(stack)) / 100f);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return net.minecraft.util.Mth.hsvToRgb(Math.max(0, batteryPercent(stack)) / 300f, 1f, 1f);
    }
}
