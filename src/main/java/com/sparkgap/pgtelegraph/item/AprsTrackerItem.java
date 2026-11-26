package com.sparkgap.pgtelegraph.item;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.radio.Aprs;
import com.sparkgap.pgtelegraph.radio.HandheldRadio;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * APRS tracker (APRS 트래커): a pocket 5 W 2 m transmitter with a GPS. Switched on, it sends the
 * carrier's name and position every 30 s from anywhere in the inventory. Its network key is copied
 * from an APRS station or vehicle radio (right-click one with it).
 */
public class AprsTrackerItem extends Item {
    public static final double WATTS = 5;

    public AprsTrackerItem(Properties p) {
        super(p);
    }

    public static boolean on(ItemStack s) {
        return s.getOrDefault(PgTelegraph.APRS_ON.get(), false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean on = !on(s);
            s.set(PgTelegraph.APRS_ON.get(), on);
            player.displayClientMessage(Component.translatable(on ? "pgtelegraph.aprs.tracker_on" : "pgtelegraph.aprs.tracker_off"), true);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return on(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p) || !on(stack)) return;
        long seed = p.getUUID().getLeastSignificantBits();
        if (!Aprs.bursting(level.getGameTime(), seed)) return;
        BlockPos key = HandheldRadio.sourceKey(p.getUUID()).offset(0, 0, 3);
        Aprs.send(level, key, BlockPos.containing(p.getEyePosition()), 2.0, RadioMath.dbm(WATTS) - 5,
                new Aprs.Packet(p.getGameProfile().getName(), p.getX(), p.getY(), p.getZ(),
                        stack.getOrDefault(PgTelegraph.APRS_KEY.get(), "|"), p.getVehicle() != null ? Aprs.VEHICLE : Aprs.PERSON));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(on(stack) ? "pgtelegraph.aprs.tracker_on" : "pgtelegraph.aprs.tracker_off")
                .withStyle(on(stack) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        tip.add(Component.translatable("pgtelegraph.aprs.key", keyText(stack.getOrDefault(PgTelegraph.APRS_KEY.get(), "|")))
                .withStyle(ChatFormatting.GRAY));
    }

    /** "item A + item B" for a key string */
    public static Component keyText(String key) {
        String[] p = (key + " ").split("\\|", 2);
        return Component.empty().append(itemName(p[0].trim())).append(" + ").append(itemName(p.length > 1 ? p[1].trim() : ""));
    }

    private static Component itemName(String id) {
        if (id.isEmpty()) return Component.translatable("pgtelegraph.aprs.empty");
        var rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null) return Component.literal(id);
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl).getDescription();
    }
}
