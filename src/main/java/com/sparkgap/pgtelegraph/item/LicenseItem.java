package com.sparkgap.pgtelegraph.item;

import com.sparkgap.pgtelegraph.block.BeaconProgram;
import com.sparkgap.pgtelegraph.block.RepeaterBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * Station license (무선국 면허): the callsign is the name given to it in an anvil (or the holder's
 * name). Use on a beacon or repeater to sign it; logbooks and QSL cards use it too.
 */
public class LicenseItem extends Item {
    public LicenseItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static String callsign(ItemStack s, Player holder) {
        Component n = s.get(DataComponents.CUSTOM_NAME);
        String c = n != null ? n.getString() : holder != null ? holder.getGameProfile().getName() : "NOCALL";
        c = c.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9/]", "");
        return c.isEmpty() ? "NOCALL" : c.length() > 12 ? c.substring(0, 12) : c;
    }

    /** callsign of the first license in a player's inventory, or their name */
    public static String of(Player p) {
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof LicenseItem) return callsign(s, p);
        for (ItemStack s : p.getInventory().offhand) if (s.getItem() instanceof LicenseItem) return callsign(s, p);
        return p.getGameProfile().getName().toUpperCase(Locale.ROOT);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        var be = level.getBlockEntity(ctx.getClickedPos());
        String call = callsign(ctx.getItemInHand(), ctx.getPlayer());
        if (be instanceof BeaconProgram.Holder h) {
            if (!level.isClientSide) {
                h.program().call = call;
                h.programChanged();
                if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("pgtelegraph.license.signed", call), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (be instanceof RepeaterBlockEntity r) {
            if (!level.isClientSide) {
                r.setCall(call);
                if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("pgtelegraph.license.signed", call), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("pgtelegraph.license.call").append(": " + callsign(stack, null)).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("item.pgtelegraph.license.hint").withStyle(ChatFormatting.GRAY));
    }
}
