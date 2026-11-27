package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Set;

/**
 * Rack mounting (랙 장착): right-click an equipment rack with a box-type device and the device goes
 * into that rack cell - drawn shallow inside the rack frame, its terminals moved to a strip on the
 * back. Coax touching two or more sets asks which one it goes to ({@link CoaxBindings}); coax right
 * behind one unit never joins coax behind another. A mounted unit is part of the rack's steel: it is grounded
 * with the rack group. Breaking it gives back the device and the rack.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class Rack {
    private Rack() {}

    public static final BooleanProperty RACKED = BooleanProperty.create("racked");

    /** box-type devices that fit a rack */
    public static final Set<String> RACKABLE = Set.of("transmitter", "receiver", "creative_transmitter", "creative_receiver",
            "beacon", "repeater", "linear_amplifier", "broadcast_amplifier", "power_supply", "ups", "power_panel", "dtmf_decoder",
            "aprs_station", "qso_robot", "ionosonde", "broadcast_radio", "audio_amplifier", "jammer", "decoder", "oscilloscope",
            "mobile_radio", "line_test_set", "duplex_set", "telegraph_repeater", "bms", "tape_recorder", "vcr", "sstv_transmitter",
            "tv_transmitter", "handheld_charger", "fm_broadcast_transmitter", "broadcast_transmitter", "line_simulator",
            "sstv_monitor", "tv_receiver", "operating_selector");

    /** the rack frame drawn and collided around a mounted unit */
    public static final VoxelShape POSTS = Shapes.or(
            Block.box(0, 0, 0, 2, 16, 2), Block.box(14, 0, 0, 16, 16, 2),
            Block.box(0, 0, 14, 2, 16, 16), Block.box(14, 0, 14, 16, 16, 16),
            Block.box(0, 0, 2, 1, 16, 14), Block.box(15, 0, 2, 16, 16, 14));

    public static boolean rackable(Block b) {
        var id = BuiltInRegistries.BLOCK.getKey(b);
        return id.getNamespace().equals(PgTelegraph.MODID) && RACKABLE.contains(id.getPath()) && b.defaultBlockState().hasProperty(RACKED);
    }

    public static boolean racked(BlockState s) {
        return s.hasProperty(RACKED) && s.getValue(RACKED);
    }

    /** rack steel: a rack block or a unit mounted in one */
    public static boolean isRack(BlockState s) {
        return s.getBlock() instanceof EquipmentRackBlock || racked(s);
    }

    /** where a mounted unit's coax port and terminal strip face */
    public static Direction rear(BlockState s) {
        return s.hasProperty(DeviceBlock.FACING) ? s.getValue(DeviceBlock.FACING).getOpposite() : Direction.SOUTH;
    }

    /**
     * The terminal strip on the back of a mounted unit: the same terminals, in the same order, in a
     * row across the back (NORTH-authored pixels). The racked models are generated with the same rule.
     */
    public static double[][] rearTerminals(double[][] terms) {
        int n = terms.length;
        double[][] out = new double[n][];
        double pitch = 13.0 / Math.max(1, n), w = Math.min(3, pitch - 0.5);
        for (int i = 0; i < n; i++) {
            double x = 1.5 + i * pitch + (pitch - w) / 2;
            out[i] = new double[]{x, 4, 13.5, x + w, 6, 15.5};
        }
        return out;
    }

    /** the mounted unit a coax block sits right behind (its port), or null */
    public static BlockPos unitBehind(BlockGetter level, BlockPos coax) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos u = coax.relative(d);
            BlockState s = level.getBlockState(u);
            if (racked(s) && rear(s) == d.getOpposite()) return u;
        }
        return null;
    }

    /** two coax blocks behind two different units are separate feed lines */
    public static boolean separateLines(BlockGetter level, BlockPos a, BlockPos b) {
        BlockPos ua = unitBehind(level, a), ub = unitBehind(level, b);
        return ua != null && ub != null && !ua.equals(ub);
    }

    /** mount: the device replaces the rack cell, facing the way the rack faces */
    public static boolean mount(Level level, BlockPos pos, BlockState rack, Block device) {
        BlockState st = device.defaultBlockState().setValue(RACKED, true);
        if (st.hasProperty(DeviceBlock.FACING) && rack.hasProperty(DeviceBlock.FACING))
            st = st.setValue(DeviceBlock.FACING, rack.getValue(DeviceBlock.FACING));
        boolean ok = level.setBlock(pos, st, 3);
        if (ok) RackBus.schedule(level, pos); // 13.8 V from the rack's distribution panel, if it has one
        return ok;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!racked(e.getState()) || e.getPlayer().getAbilities().instabuild || !(e.getLevel() instanceof Level level)) return;
        Block.popResource(level, e.getPos(), new ItemStack(PgTelegraph.EQUIPMENT_RACK.get()));
    }
}
