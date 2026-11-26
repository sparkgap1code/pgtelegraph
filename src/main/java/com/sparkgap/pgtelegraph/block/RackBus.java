package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.HangingWireEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The rack's 13.8 V wiring: when a 13.8 V unit is mounted in a rack group that has a power
 * distribution panel mounted in it (or the panel comes later), real insulated copper wire is run
 * from a free panel output to the unit's PWR+ and from the panel's GND to its PWR-. The panel's
 * fuses, the wire's resistance and everything else then work as with hand-laid wire.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class RackBus {
    private RackBus() {}

    /** units that run on 13.8 V and have PWR+ / PWR- as terminals 0 and 1 */
    public static final Set<String> BUS_UNITS = Set.of("transmitter", "receiver", "beacon", "repeater", "qso_robot",
            "dtmf_decoder", "aprs_station", "sstv_monitor");
    private static final int PANEL_GND = 1, PANEL_OUT0 = 2, PANEL_OUTS = 4, MAX_GROUP = 128;

    private record Job(BlockPos pos, long at) {}

    private static final Map<Level, List<Job>> JOBS = new WeakHashMap<>();

    /** after a mount: wire up in a few ticks, once the unit's circuit exists */
    public static void schedule(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        JOBS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Job(pos.immutable(), level.getGameTime() + 5));
    }

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        List<Job> l = JOBS.get(sl);
        if (l == null || l.isEmpty()) return;
        for (Iterator<Job> it = l.iterator(); it.hasNext(); ) {
            Job j = it.next();
            if (sl.getGameTime() < j.at) continue;
            it.remove();
            try {
                wire(sl, j.pos);
            } catch (RuntimeException ex) {
                PgTelegraph.LOGGER.warn("[pgtelegraph] rack wiring at {} failed: {}", j.pos, ex.toString());
            }
            return; // one a tick
        }
    }

    private static String id(Level level, BlockPos p) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
    }

    static boolean busUnit(Level level, BlockPos p) {
        return Rack.racked(level.getBlockState(p)) && BUS_UNITS.contains(id(level, p));
    }

    static boolean panel(Level level, BlockPos p) {
        return Rack.racked(level.getBlockState(p)) && id(level, p).equals("power_panel");
    }

    /** the rack group around p */
    static List<BlockPos> group(Level level, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && seen.size() < MAX_GROUP) {
            BlockPos c = q.poll();
            out.add(c);
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (level.isLoaded(n) && Rack.isRack(level.getBlockState(n)) && seen.add(n)) q.add(n);
            }
        }
        return out;
    }

    private static int wires(Level level, BlockPos p, int terminal) {
        ElectricBehaviour eb = BlockEntityBehaviour.get(level, p, ElectricBehaviour.TYPE);
        if (eb == null) return -1;
        int n = 0;
        for (var en : eb.getConnections().entrySet()) if (en.getKey().getTerminal() == terminal) n += en.getValue().size();
        return n;
    }

    private static void wire(ServerLevel level, BlockPos pos) {
        if (!Rack.racked(level.getBlockState(pos))) return;
        List<BlockPos> g = group(level, pos);
        BlockPos panel = null;
        for (BlockPos p : g) if (panel(level, p)) {
            panel = p;
            break;
        }
        if (panel == null) return;
        for (BlockPos u : g) {
            if (!busUnit(level, u) || wires(level, u, 0) != 0) continue; // only units with nothing on PWR+ yet
            int best = -1, bestN = Integer.MAX_VALUE;
            for (int k = 0; k < PANEL_OUTS; k++) {
                int n = wires(level, panel, PANEL_OUT0 + k);
                if (n >= 0 && n < bestN) {
                    bestN = n;
                    best = PANEL_OUT0 + k;
                }
            }
            if (best < 0) return;
            connect(level, panel, best, u, 0, DyeColor.RED);
            connect(level, panel, PANEL_GND, u, 1, DyeColor.BLACK);
        }
    }

    private static void connect(ServerLevel level, BlockPos a, int ta, BlockPos b, int tb, DyeColor color) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("powergrid", "insulated_copper_wire"));
        BlockWireEndpoint ea = new BlockWireEndpoint(a, ta), eb = new BlockWireEndpoint(b, tb);
        Vec3 pa = ea.getExactPosition(level), pb = eb.getExactPosition(level);
        double len = pa == null || pb == null ? 2 : pa.distanceTo(pb);
        HangingWireEntity w = HangingWireEntity.create(level, ea, eb, new ItemStack(item, (int) Math.max(1, Math.ceil(len * 0.5))), null);
        if (w == null) return;
        w.setColor(color);
        level.tryAddFreshEntityWithPassengers(w);
    }
}
