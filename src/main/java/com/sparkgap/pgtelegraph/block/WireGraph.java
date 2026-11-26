package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntityEndpoint;
import org.patryk3211.powergrid.electricity.wire.DeferredJunctionWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Follows Power Grid wiring (hanging wires and block wires, junctions included) out of some
 * terminals of a block and collects the blocks found along it that match - e.g. the loudspeakers
 * on an amplifier's speaker line.
 */
public final class WireGraph {
    private WireGraph() {}

    private static final int MAX_NODES = 160;

    public static List<BlockPos> find(Level level, BlockPos from, int[] terminals, Predicate<BlockEntity> want) {
        List<BlockPos> found = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        ArrayDeque<Object> todo = new ArrayDeque<>();
        seen.add(from);
        ElectricBehaviour start = BlockEntityBehaviour.get(level, from, ElectricBehaviour.TYPE);
        if (start == null) return found;
        for (var en : start.getConnections().entrySet())
            for (int t : terminals)
                if (en.getKey().getTerminal() == t) for (BaseWireEntity w : en.getValue()) if (seen.add(w)) todo.add(w);
        while (!todo.isEmpty() && seen.size() < MAX_NODES) {
            Object node = todo.poll();
            if (node instanceof BlockPos p) {
                if (want.test(level.getBlockEntity(p))) {
                    found.add(p);
                    continue; // a speaker ends the line here (its other terminal goes back to the amplifier)
                }
                ElectricBehaviour eb = BlockEntityBehaviour.get(level, p, ElectricBehaviour.TYPE);
                if (eb == null) continue;
                for (var set : eb.getConnections().values())
                    for (BaseWireEntity w : set) if (seen.add(w)) todo.add(w);
            } else if (node instanceof BaseWireEntity w) {
                for (IWireEndpoint ep : new IWireEndpoint[]{w.getEndpoint1(), w.getEndpoint2()}) {
                    Object next = resolve(level, ep);
                    if (next instanceof BlockPos q && !level.isLoaded(q)) continue;
                    if (next != null && seen.add(next)) todo.add(next);
                }
                for (BaseWireEntity o : level.getEntitiesOfClass(BaseWireEntity.class, w.getBoundingBox().inflate(1.5))) {
                    if (seen.contains(o)) continue;
                    if (w.equals(resolve(level, o.getEndpoint1())) || w.equals(resolve(level, o.getEndpoint2()))) {
                        seen.add(o);
                        todo.add(o);
                    }
                }
            }
        }
        return found;
    }

    private static Object resolve(Level level, IWireEndpoint ep) {
        if (ep instanceof BlockWireEndpoint b) return b.getPos();
        if (ep instanceof BlockWireEntityEndpoint b) return b.getEntity(level);
        if (ep instanceof DeferredJunctionWireEndpoint j) return j.getEntity(level);
        return null;
    }
}
