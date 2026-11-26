package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** CW transmitter (CW 송신기). Coax or an antenna plugs into any side. Empty hand: tune. */
public class TransmitterBlock extends DeviceBlock<TransmitterBlockEntity> implements RfPort, org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord {
    public static final BooleanProperty ON = BooleanProperty.create("on");
    static final double[][] TERMINALS = RadioTerminals.BOXES;

    public TransmitterBlock(Properties properties) {
        super(properties, TERMINALS, new Component[]{
                        Component.translatable("pgtelegraph.terminal.pwr_pos"),
                        Component.translatable("pgtelegraph.terminal.pwr_neg"),
                        Component.translatable("pgtelegraph.terminal.key"),
                        Component.translatable("pgtelegraph.terminal.key_com")},
                RadioTerminals.shape(), TransmitterBlockEntity.class, PgTelegraph.TRANSMITTER_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ON, false));
    }

    /** for transmitters that share this case (beacon, spark transmitter, creative) */
    public TransmitterBlock(Properties properties, Component[] names,
                               java.util.function.Supplier<? extends net.minecraft.world.level.block.entity.BlockEntityType<? extends TransmitterBlockEntity>> type) {
        super(properties, TERMINALS, names, RadioTerminals.shape(), TransmitterBlockEntity.class, type);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ON);
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
