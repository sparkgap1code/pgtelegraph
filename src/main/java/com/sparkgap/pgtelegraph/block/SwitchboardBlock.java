package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Switchboard (교환대). Front panel: 8 jacks (top row 1-4, bottom row 5-8).
 * Empty hand on a jack: pick up a cord end, then click a second jack to connect (same pair again
 * removes it). Sneak-click a jack: pull its cords and reset its drop shutter.
 * Rear: 8 line terminals in the same layout plus a common/ground terminal at the bottom.
 */
public class SwitchboardBlock extends DeviceBlock<SwitchboardBlockEntity> {
    /** jack centres on the front face (z = FRONT_Z), NORTH-authored pixels; the operator faces
     *  south, so their left is +x and jack 1 is at the highest x */
    public static final double[] JACK_X = {13.5, 10, 6.5, 3};
    public static final double[] JACK_Y = {12.25, 7.75};
    public static final double FRONT_Z = 6;

    static final double[][] TERMINALS = new double[SwitchboardBlockEntity.LINES + 1][];
    static final Component[] NAMES = new Component[SwitchboardBlockEntity.LINES + 1];

    static {
        for (int i = 0; i < SwitchboardBlockEntity.LINES; i++) {
            double x = JACK_X[i % 4], y = JACK_Y[i / 4];
            // each line terminal sits directly behind its jack
            TERMINALS[i] = new double[]{x - 1.5, y - 1.25, 10, x + 1.5, y + 1.25, 12};
            NAMES[i] = Component.translatable("pgtelegraph.terminal.line", i + 1);
        }
        TERMINALS[SwitchboardBlockEntity.LINES] = new double[]{6.5, 2.5, 10, 9.5, 5, 12};
        NAMES[SwitchboardBlockEntity.LINES] = Component.translatable("pgtelegraph.terminal.ground");
    }

    private static double[][] shape() {
        double[][] s = new double[2 + TERMINALS.length][];
        s[0] = new double[]{0, 0, 0, 16, 2, 12};
        s[1] = new double[]{1, 2, 6, 15, 15, 10};
        System.arraycopy(TERMINALS, 0, s, 2, TERMINALS.length);
        return s;
    }

    public SwitchboardBlock(Properties properties) {
        super(properties, TERMINALS, NAMES, shape(), SwitchboardBlockEntity.class, PgTelegraph.SWITCHBOARD_BE);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    /** Which jack (0-7) a hit on the front panel lands on, or -1. */
    public static int jackAt(BlockState state, BlockPos pos, Vec3 hit) {
        double lx = (hit.x - pos.getX()) * 16, ly = (hit.y - pos.getY()) * 16, lz = (hit.z - pos.getZ()) * 16;
        double[] m = Geometry.toModel(lx, lz, state.getValue(FACING));
        if (Math.abs(m[1] - FRONT_Z) > 0.6) return -1;
        int best = -1;
        double bestD = 2.2 * 2.2;
        for (int i = 0; i < SwitchboardBlockEntity.LINES; i++) {
            double dx = m[0] - JACK_X[i % 4], dy = ly - JACK_Y[i / 4];
            double d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int jack = jackAt(state, pos, hit.getLocation());
        if (jack < 0 || !(level.getBlockEntity(pos) instanceof SwitchboardBlockEntity be)) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!level.isClientSide) be.clickJack(jack, player.isShiftKeyDown(), player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** with insulated copper wire in hand, a jack takes a cord; elsewhere the wire goes on the terminals as usual */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack held, BlockState state, Level level,
                                                                  BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        int jack = jackAt(state, pos, hit.getLocation());
        if (jack < 0 || !SwitchboardBlockEntity.isCordWire(held) || hand != net.minecraft.world.InteractionHand.MAIN_HAND
                || !(level.getBlockEntity(pos) instanceof SwitchboardBlockEntity be))
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) be.clickJack(jack, false, player);
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
