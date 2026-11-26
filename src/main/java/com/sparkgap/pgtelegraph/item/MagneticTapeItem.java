package com.sparkgap.pgtelegraph.item;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.TapeRecorderBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;
import java.util.Locale;

/** Magnetic tape (자기 테이프) for the tape recorder: 5 minutes. Use on a recorder to load it. */
public class MagneticTapeItem extends Item {
    public MagneticTapeItem(Properties p) {
        super(p.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().getBlockEntity(ctx.getClickedPos()) instanceof TapeRecorderBlockEntity r && !r.hasTape()) {
            if (!ctx.getLevel().isClientSide) {
                r.insert(ctx.getItemInHand());
                ctx.getItemInHand().shrink(1);
            }
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        List<Integer> d = stack.get(PgTelegraph.MAG_TAPE.get());
        int t = 0;
        if (d != null) for (int p : d) t += p >>> 16;
        tip.add(Component.literal(String.format(Locale.ROOT, "%d:%02d / 5:00", t / 1200, t / 20 % 60)).withStyle(ChatFormatting.GRAY));
    }
}
