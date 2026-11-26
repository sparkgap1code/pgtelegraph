package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Vacuum-tube linear amplifier (진공관 선형 증폭기). Rear: HV+ / HV- (lower), FIL+ / FIL- (upper).
 * RF in from the operator's left side, out to the antenna on the right side. The tube glows
 * once the filament is hot.
 */
public class LinearAmplifierBlock extends DeviceBlock<LinearAmplifierBlockEntity> implements RfPort, org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public final AmpTier tier;

    static final double[][] TERMINALS = {
            {10, 2, 14, 13, 5, 15.5},  // 0 HV+
            {3, 2, 14, 6, 5, 15.5},    // 1 HV-
            {10, 6.5, 14, 13, 9, 15.5}, // 2 FIL+
            {3, 6.5, 14, 6, 9, 15.5},  // 3 FIL-
    };

    public LinearAmplifierBlock(Properties properties) {
        this(properties, AmpTier.STANDARD, PgTelegraph.LINEAR_AMPLIFIER_BE);
    }

    public LinearAmplifierBlock(Properties properties, AmpTier tier,
                                java.util.function.Supplier<? extends net.minecraft.world.level.block.entity.BlockEntityType<? extends LinearAmplifierBlockEntity>> type) {
        super(properties, TERMINALS, new Component[]{
                        Component.translatable("pgtelegraph.terminal.hv_pos"),
                        Component.translatable("pgtelegraph.terminal.hv_neg"),
                        Component.translatable("pgtelegraph.terminal.fil_pos"),
                        Component.translatable("pgtelegraph.terminal.fil_neg")},
                new double[][]{{1, 0, 2, 15, 10, 14}, {5.5, 10, 5.5, 10.5, 16, 10.5},
                        TERMINALS[0], TERMINALS[1], TERMINALS[2], TERMINALS[3]},
                LinearAmplifierBlockEntity.class, type);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }
}
