package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntityEndpoint;
import org.patryk3211.powergrid.electricity.wire.DeferredJunctionWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Follows a line out of a terminal along Power Grid wiring and reports what is at the far ends -
 * terminal by terminal, so a switchboard is crossed only along its cords. Wire length is added up
 * on the way (from the wires' end points). Used by the field telephone (who is on the line) and the
 * line test set (where the line ends, where it leaks).
 */
public final class WireTrace {
    private WireTrace() {}

    private static final int MAX_NODES = 400;

    /** something found at the end of the line: the block, the terminal the line comes into, metres of wire to it */
    public record Hit(BlockPos pos, int terminal, double meters, Kind kind) {}

    public enum Kind {
        /** a phone / device of ours: the line stops in it */
        DEVICE,
        /** the wire ends at a tie point with nothing beyond: a break */
        BREAK,
        /** a wet, leaking insulator on the way (the line goes on) */
        LEAK
    }

    public interface Stop {
        /** true if the line ends in this block (it is recorded as a DEVICE) */
        boolean test(BlockEntity be);
    }

    public static List<Hit> trace(Level level, BlockPos from, int[] terminals, Stop stop) {
        List<Hit> out = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        Map<Object, Double> dist = new HashMap<>();
        ArrayDeque<Object> todo = new ArrayDeque<>();
        seen.add(from);
        ElectricBehaviour start = BlockEntityBehaviour.get(level, from, ElectricBehaviour.TYPE);
        if (start == null) return out;
        for (var en : start.getConnections().entrySet())
            for (int t : terminals)
                if (en.getKey().getTerminal() == t) for (BaseWireEntity w : en.getValue()) if (seen.add(w)) {
                    todo.add(w);
                    dist.put(w, 0.0);
                }
        while (!todo.isEmpty() && seen.size() < MAX_NODES) {
            Object node = todo.poll();
            double d = dist.getOrDefault(node, 0.0);
            if (node instanceof BaseWireEntity w) {
                double len = length(level, w);
                for (IWireEndpoint ep : new IWireEndpoint[]{w.getEndpoint1(), w.getEndpoint2()}) {
                    if (ep instanceof BlockWireEndpoint b) {
                        Object key = b.getPos().immutable().asLong() * 64 + b.getTerminal();
                        if (b.getPos().equals(from) || !level.isLoaded(b.getPos()) || !seen.add(key)) continue;
                        enterBlock(level, b.getPos(), b.getTerminal(), d + len, stop, out, seen, dist, todo);
                    } else {
                        Object next = ep instanceof BlockWireEntityEndpoint e ? e.getEntity(level)
                                : ep instanceof DeferredJunctionWireEndpoint j ? j.getEntity(level) : null;
                        if (next != null && seen.add(next)) {
                            dist.put(next, d + len);
                            todo.add(next);
                        }
                    }
                }
                for (BaseWireEntity o : level.getEntitiesOfClass(BaseWireEntity.class, w.getBoundingBox().inflate(1.5))) {
                    if (seen.contains(o)) continue;
                    if (w.equals(entity(level, o.getEndpoint1())) || w.equals(entity(level, o.getEndpoint2()))) {
                        seen.add(o);
                        dist.put(o, d + len);
                        todo.add(o);
                    }
                }
            }
        }
        return out;
    }

    private static void enterBlock(Level level, BlockPos p, int terminal, double d, Stop stop, List<Hit> out, Set<Object> seen,
                                   Map<Object, Double> dist, ArrayDeque<Object> todo) {
        BlockEntity be = level.getBlockEntity(p);
        if (be instanceof LineSimulatorBlockEntity sim) { // a long line squeezed into one block: out the other end, further away
            ElectricBehaviour eb = BlockEntityBehaviour.get(level, p, ElectricBehaviour.TYPE);
            if (eb == null) return;
            for (var en : eb.getConnections().entrySet()) {
                if (en.getKey().getTerminal() == terminal) continue;
                for (BaseWireEntity w : en.getValue())
                    if (seen.add(w)) {
                        dist.put(w, d + sim.meters());
                        todo.add(w);
                    }
            }
            return;
        }
        if (stop.test(be)) {
            out.add(new Hit(p.immutable(), terminal, d, Kind.DEVICE));
            return;
        }
        ElectricBehaviour eb = BlockEntityBehaviour.get(level, p, ElectricBehaviour.TYPE);
        if (eb == null) return;
        Set<Integer> onward = new HashSet<>();
        if (be instanceof SwitchboardBlockEntity sb) { // only along the cords plugged into this line's jack
            for (int[] c : sb.cords()) {
                if (c[0] == terminal) onward.add(c[1]);
                if (c[1] == terminal) onward.add(c[0]);
            }
        } else if (be instanceof RatedBlockEntity && !(be instanceof InsulatorBlockEntity)) {
            out.add(new Hit(p.immutable(), terminal, d, Kind.DEVICE)); // some other device: the line ends in it
            return;
        } else onward = null; // a tie point, junction or connector: on along every wire
        if (be instanceof InsulatorBlockEntity ins && ins.leakOhms() < 10_000)
            out.add(new Hit(p.immutable(), terminal, d, Kind.LEAK));
        int wires = 0;
        for (var en : eb.getConnections().entrySet()) {
            int t = en.getKey().getTerminal();
            if (onward != null && !onward.contains(t)) continue;
            for (BaseWireEntity w : en.getValue()) {
                if (seen.add(w)) {
                    wires++;
                    dist.put(w, d);
                    todo.add(w);
                }
            }
        }
        if (onward == null && wires == 0) out.add(new Hit(p.immutable(), terminal, d, Kind.BREAK)); // nothing beyond
    }

    private static Object entity(Level level, IWireEndpoint ep) {
        if (ep instanceof BlockWireEntityEndpoint b) return b.getEntity(level);
        if (ep instanceof DeferredJunctionWireEndpoint j) return j.getEntity(level);
        return null;
    }

    private static double length(Level level, BaseWireEntity w) {
        try {
            Vec3 a = w.getEndpoint1().getExactPosition(level), b = w.getEndpoint2().getExactPosition(level);
            return a == null || b == null ? 0 : a.distanceTo(b);
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
