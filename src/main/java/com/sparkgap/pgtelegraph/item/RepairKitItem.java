package com.sparkgap.pgtelegraph.item;

import com.sparkgap.pgtelegraph.block.DummyLoadBlock;
import com.sparkgap.pgtelegraph.block.RatedBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Repair kit (수리 키트): use on a burnt device to rewind its coils and replace its contacts. One use. */
public class RepairKitItem extends Item {
    public RepairKitItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockState s = level.getBlockState(ctx.getClickedPos());
        boolean done = false;
        if (level.getBlockEntity(ctx.getClickedPos()) instanceof RatedBlockEntity r && r.isBurnt()) {
            if (!level.isClientSide) r.repair();
            done = true;
        } else if (s.getBlock() instanceof DummyLoadBlock && s.getValue(DummyLoadBlock.BURNT)) {
            if (!level.isClientSide) level.setBlockAndUpdate(ctx.getClickedPos(), s.setValue(DummyLoadBlock.BURNT, false));
            done = true;
        }
        if (!done) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.playSound(null, ctx.getClickedPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
            if (ctx.getPlayer() != null && !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.pgtelegraph.repair_kit.hint").withStyle(ChatFormatting.GRAY));
    }
}
