package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/** Vehicle radio box: APRS key slots on top, a name tag names the vehicle. */
public class MobileRadioBlock extends StationBlock<MobileRadioBlockEntity> {
    public MobileRadioBlock(Properties p, Supplier<? extends BlockEntityType<? extends MobileRadioBlockEntity>> type) {
        super(p, new double[]{3, 0, 3, 13, 4, 13}, MobileRadioBlockEntity.class, type, (be, pl, l, pos) -> {
            if (!l.isClientSide) pl.displayClientMessage(Component.translatable("pgtelegraph.mobile.hint"), true);
            return true;
        });
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState st, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MobileRadioBlockEntity be))
            return super.useItemOn(held, st, level, pos, player, hand, hit);
        if (held.is(Items.NAME_TAG) && held.has(DataComponents.CUSTOM_NAME)) {
            if (!level.isClientSide) {
                be.setName(held.getHoverName().getString());
                player.displayClientMessage(Component.translatable("pgtelegraph.mobile.named", be.name()), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        ItemInteractionResult r = AprsKeyed.use(be, st, level, pos, player, held, hit.getLocation());
        if (r != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return r;
        return super.useItemOn(held, st, level, pos, player, hand, hit);
    }
}
