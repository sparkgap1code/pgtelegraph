package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/** A device box with terminals given as data (NORTH-authored pixels), a case box, and an empty-hand action. */
public class GenericDeviceBlock<T extends BlockEntity> extends DeviceBlock<T> {
    private final StationBlock.Use<T> use;
    private final Class<T> cls;

    public GenericDeviceBlock(Properties p, double[] box, double[][] terminals, String[] names, Class<T> cls,
                              Supplier<? extends BlockEntityType<? extends T>> type, StationBlock.Use<T> use) {
        super(p, terminals, components(names), shape(box, terminals), cls, type);
        this.use = use;
        this.cls = cls;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    private static Component[] components(String[] names) {
        Component[] c = new Component[names.length];
        for (int i = 0; i < names.length; i++) c[i] = Component.translatable("pgtelegraph.terminal." + names[i]);
        return c;
    }

    private static double[][] shape(double[] box, double[][] terminals) {
        double[][] s = new double[terminals.length + 1][];
        s[0] = box;
        System.arraycopy(terminals, 0, s, 1, terminals.length);
        return s;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (use == null || !cls.isInstance(be)) return InteractionResult.PASS;
        return use.use((T) be, player, level, pos) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }
}
