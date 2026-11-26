package com.sparkgap.pgtelegraph.sat;

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

/** CubeSat (큐브샛): the payload for a rocket. Use to set up everything it will do in orbit. */
public class CubeSatItem extends Item {
    public CubeSatItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static SatConfig config(ItemStack s) {
        return SatConfig.parse(s.get(PgTelegraph.SAT_CONFIG.get()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (level.isClientSide) ClientAccess.openCubeSat(hand, config(s).serialize());
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        SatConfig c = config(stack);
        tip.add(Component.literal(c.name()).withStyle(ChatFormatting.GOLD));
        String sum = c.summary();
        tip.add(Component.literal(sum.isEmpty() ? "-" : sum).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("item.pgtelegraph.cubesat.hint").withStyle(ChatFormatting.GRAY));
    }
}
