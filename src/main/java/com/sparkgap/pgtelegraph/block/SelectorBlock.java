package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Operating-position selector: KEY IN, COM, OUT1-OUT4 in a row on top; four lamps on the front. */
public class SelectorBlock extends DeviceBlock<SelectorBlockEntity> {
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, SelectorBlockEntity.OUTS);
    static final double[][] TERMINALS = new double[2 + SelectorBlockEntity.OUTS][];

    static {
        for (int i = 0; i < TERMINALS.length; i++) {
            double x = 2.5 + i * 1.9;
            TERMINALS[i] = new double[]{x, 5, 9.5, x + 1.6, 6.5, 12};
        }
    }

    public SelectorBlock(Properties p) {
        super(p, TERMINALS, names(), shape(), SelectorBlockEntity.class, PgTelegraph.SELECTOR_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(CHANNEL, 1));
    }

    private static Component[] names() {
        Component[] c = new Component[TERMINALS.length];
        c[0] = Component.translatable("pgtelegraph.terminal.key_in");
        c[1] = Component.translatable("pgtelegraph.terminal.key_com");
        for (int i = 2; i < c.length; i++) c[i] = Component.translatable("pgtelegraph.terminal.out_n", i - 1);
        return c;
    }

    private static double[][] shape() {
        double[][] s = new double[TERMINALS.length + 1][];
        s[0] = new double[]{2, 0, 4, 14, 5, 13};
        System.arraycopy(TERMINALS, 0, s, 1, TERMINALS.length);
        return s;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHANNEL);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int n = SelectorBlockEntity.OUTS + 1;
            int ch = Math.floorMod(state.getValue(CHANNEL) + (player.isShiftKeyDown() ? -1 : 1), n);
            level.setBlock(pos, state.setValue(CHANNEL, ch), 3);
            player.displayClientMessage(ch == 0 ? Component.translatable("pgtelegraph.selector.off")
                    : Component.translatable("pgtelegraph.selector.on", ch), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** a redstone signal of 1-4 picks that position */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (level.isClientSide) return;
        int s = level.getBestNeighborSignal(pos);
        if (s >= 1 && s <= SelectorBlockEntity.OUTS && state.getValue(CHANNEL) != s) level.setBlock(pos, state.setValue(CHANNEL, s), 3);
    }
}
