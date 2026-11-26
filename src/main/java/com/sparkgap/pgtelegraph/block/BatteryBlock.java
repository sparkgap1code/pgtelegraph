package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Battery pack (배터리 팩): + and - posts on top. Keeps its charge when picked up. */
public class BatteryBlock extends DeviceBlock<BatteryBlockEntity> implements org.patryk3211.powergrid.electricity.wire.powercord.IAcceptCord {
    static final double[][] TERMINALS = {
            {10, 10, 6.5, 13, 12, 9.5},
            {3, 10, 6.5, 6, 12, 9.5},
    };

    public final BatteryBlockEntity.Chem chem;

    public BatteryBlock(Properties properties, BatteryBlockEntity.Chem chem) {
        super(properties, TERMINALS, new Component[]{
                        Component.translatable("pgtelegraph.terminal.bat_pos"),
                        Component.translatable("pgtelegraph.terminal.bat_neg")},
                new double[][]{{2, 0, 3, 14, 10, 13}, TERMINALS[0], TERMINALS[1]},
                BatteryBlockEntity.class, PgTelegraph.BATTERY_BE);
        this.chem = chem;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d != null && level.getBlockEntity(pos) instanceof BatteryBlockEntity be) {
            CompoundTag t = d.copyTag();
            if (t.contains("Soc")) be.setState(t.getDouble("Soc"), t.contains("Health") ? t.getDouble("Health") : 1);
        }
    }
}
