package com.sparkgap.pgtelegraph.radio;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Manpack radio (등짐형 무전기): a two-way NFM set carried on the back in the chest armour slot, like
 * the field radios soldiers carry. The same parts and battery as a handheld, but a bigger power
 * amplifier and supply (1 - 20 W) and a 1 m whip standing above the shoulder. Push to talk with
 * the microphone key (V): your voice goes out, and whoever is on the frequency is heard in your
 * earpiece through the simulated receiver (noise, squelch, fading and all).
 * R-click: put it on. Sneak + R-click: settings and parts.
 */
public class ManpackItem extends HandheldRadioItem {
    public static final float[] POWER_W = {1f, 5f, 10f, 20f};

    public ManpackItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.CHEST;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) return super.use(level, player, hand);
        if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) return InteractionResultHolder.fail(s);
        if (!level.isClientSide) {
            player.setItemSlot(EquipmentSlot.CHEST, s.copy());
            s.setCount(0);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    /** worn and switched on */
    public static ItemStack worn(Player p) {
        ItemStack c = p.getItemBySlot(EquipmentSlot.CHEST);
        return c.getItem() instanceof ManpackItem && data(c).on() ? c : null;
    }
}
