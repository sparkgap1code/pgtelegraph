package com.sparkgap.pgtelegraph.parts;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Screwdriver (드라이버): opens the parts bay of an installed radio. */
public class ScrewdriverItem extends Item {
    public ScrewdriverItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        var be = ctx.getLevel().getBlockEntity(ctx.getClickedPos());
        if (!(be instanceof PartsHolder h)) return InteractionResult.PASS;
        if (ctx.getPlayer() instanceof ServerPlayer sp) {
            ctx.getLevel().playSound(null, ctx.getClickedPos(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5f, 1.6f);
            PartsHolder.openBlock(sp, be, h);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }
}
