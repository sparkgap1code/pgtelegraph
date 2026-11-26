package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** CW receiver (수신기). Coax or an antenna plugs into any side. Empty hand: tune. */
public class ReceiverBlock extends DeviceBlock<ReceiverBlockEntity> implements RfPort, org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord {
    public ReceiverBlock(Properties properties) {
        super(properties, RadioTerminals.BOXES, new Component[]{
                        Component.translatable("pgtelegraph.terminal.pwr_pos"),
                        Component.translatable("pgtelegraph.terminal.pwr_neg"),
                        Component.translatable("pgtelegraph.terminal.out"),
                        Component.translatable("pgtelegraph.terminal.out")},
                RadioTerminals.shape(), ReceiverBlockEntity.class, PgTelegraph.RECEIVER_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    public ReceiverBlock(Properties properties, java.util.function.Supplier<? extends net.minecraft.world.level.block.entity.BlockEntityType<? extends ReceiverBlockEntity>> type) {
        super(properties, RadioTerminals.BOXES, new Component[]{
                        Component.translatable("pgtelegraph.terminal.pwr_pos"),
                        Component.translatable("pgtelegraph.terminal.pwr_neg"),
                        Component.translatable("pgtelegraph.terminal.out"),
                        Component.translatable("pgtelegraph.terminal.out")},
                RadioTerminals.shape(), ReceiverBlockEntity.class, type);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof RadioBlockEntity be) {
            if (level.isClientSide) ClientAccess.openTune(pos, be.freqHz());
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
