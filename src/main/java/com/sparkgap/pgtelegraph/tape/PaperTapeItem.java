package com.sparkgap.pgtelegraph.tape;

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

/** Paper tape: blank, inked (received by the inker) or punched (made on the perforator). */
public class PaperTapeItem extends Item {
    public PaperTapeItem(Properties properties) {
        super(properties);
    }

    public static TapeData data(ItemStack stack) {
        return stack.get(PgTelegraph.TAPE.get());
    }

    public static boolean isBlank(ItemStack stack) {
        return stack.getItem() instanceof PaperTapeItem && data(stack) == null;
    }

    public static ItemStack of(TapeData data) {
        ItemStack s = new ItemStack(PgTelegraph.PAPER_TAPE.get());
        if (data != null && !data.runs().isEmpty()) s.set(PgTelegraph.TAPE.get(), data);
        return s;
    }

    @Override
    public Component getName(ItemStack stack) {
        TapeData d = data(stack);
        if (d == null) return super.getName(stack);
        return Component.translatable(d.kind() == TapeData.PUNCHED
                ? "item.pgtelegraph.paper_tape.punched" : "item.pgtelegraph.paper_tape.inked");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        TapeData d = data(stack);
        if (d == null) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) ClientAccess.openTape(d);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        TapeData d = data(stack);
        if (d == null) return;
        if (!d.label().isEmpty()) tip.add(Component.literal("\"" + d.label() + "\"").withStyle(ChatFormatting.GRAY));
        if (d.kind() == TapeData.INKED) {
            tip.add(Component.translatable("pgtelegraph.tape.length_seconds",
                    String.format(java.util.Locale.ROOT, "%.1f", d.total() / 20.0)).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tip.add(Component.translatable("pgtelegraph.tape.length_units", d.total()).withStyle(ChatFormatting.DARK_GRAY));
        }
        String en = Morse.toEnglish(Morse.decodeCodes(d));
        if (en.length() > 28) en = en.substring(0, 28) + "…";
        tip.add(Component.literal(en).withStyle(ChatFormatting.DARK_AQUA));
        tip.add(Component.translatable("pgtelegraph.tape.read_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
