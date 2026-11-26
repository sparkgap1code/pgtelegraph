package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/** APRS station box: key slots on top, empty hand opens the map. */
public class AprsStationBlock extends PoweredStationBlock<AprsStationBlockEntity> {
    public AprsStationBlock(Properties p, Supplier<? extends BlockEntityType<? extends AprsStationBlockEntity>> type) {
        super(p, new double[]{2, 0, 3, 14, 6, 14}, AprsStationBlockEntity.class, type, (be, pl, l, pos) -> {
            if (l.isClientSide) com.sparkgap.pgtelegraph.client.ClientAccess.openAprs(pos);
            return true;
        });
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState st, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof AprsStationBlockEntity be) {
            ItemInteractionResult r = AprsKeyed.use(be, st, level, pos, player, held, hit.getLocation());
            if (r != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) return r;
        }
        return super.useItemOn(held, st, level, pos, player, hand, hit);
    }
}
