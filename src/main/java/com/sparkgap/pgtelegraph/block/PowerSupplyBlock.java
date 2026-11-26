package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** 13.8 V DC power supply: IN+ / IN- at the back (from the grid), OUT+ / OUT- at the front (to the station). */
public class PowerSupplyBlock extends DeviceBlock<PowerSupplyBlockEntity> implements org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord {
    public PowerSupplyBlock(Properties properties) {
        this(properties, PgTelegraph.POWER_SUPPLY_BE);
    }

    public PowerSupplyBlock(Properties properties, java.util.function.Supplier<? extends net.minecraft.world.level.block.entity.BlockEntityType<? extends PowerSupplyBlockEntity>> type) {
        super(properties, RadioTerminals.BOXES, new Component[]{
                        Component.translatable("pgtelegraph.terminal.in_pos"),
                        Component.translatable("pgtelegraph.terminal.in_neg"),
                        Component.translatable("pgtelegraph.terminal.out_pos"),
                        Component.translatable("pgtelegraph.terminal.out_neg")},
                RadioTerminals.shape(), PowerSupplyBlockEntity.class, type);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }
}
