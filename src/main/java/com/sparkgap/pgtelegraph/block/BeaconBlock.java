package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Automatic beacon. Empty hand: tune; sneak + empty hand: program. */
public class BeaconBlock extends TransmitterBlock {
    public BeaconBlock(Properties properties) {
        super(properties, new Component[]{
                Component.translatable("pgtelegraph.terminal.pwr_pos"),
                Component.translatable("pgtelegraph.terminal.pwr_neg"),
                Component.translatable("pgtelegraph.terminal.inhibit"),
                Component.translatable("pgtelegraph.terminal.key_com")}, PgTelegraph.BEACON_BE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof BeaconProgram.Holder h) {
            if (level.isClientSide) ClientAccess.openBeacon(pos, h.program());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
