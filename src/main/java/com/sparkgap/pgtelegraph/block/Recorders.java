package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.patryk3211.powergrid.electricity.base.ElectricBehaviour;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.BlockWireEntityEndpoint;
import org.patryk3211.powergrid.electricity.wire.DeferredJunctionWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Is anything that keeps a record hanging off a receiver? A receiver keeps its station awake
 * (chunk loading) only while it is copying traffic onto something that writes it down: an inker,
 * a Morse decoder or a teleprinter somewhere along the wires from its OUT contact (through
 * batteries, relays and the like), or a tape recorder standing against it. A receiver that is only
 * listened to by ear does not need to run while nobody is near.
 * <p>
 * Both kinds of Power Grid wire are followed: hanging wires (terminal to terminal) and block wires
 * laid along blocks, including where one block wire ends on another or branches off it at a junction.
 */
public final class Recorders {
    private Recorders() {}

    private static final int MAX_NODES = 96;

    public static boolean attached(Level level, BlockPos rx, int... terminals) {
        for (Direction d : Direction.values())
            if (level.getBlockEntity(rx.relative(d)) instanceof TapeRecorderBlockEntity) return true;
        Set<Object> seen = new HashSet<>();
        ArrayDeque<Object> todo = new ArrayDeque<>();
        seen.add(rx);
        // from the receiver itself follow only its OUT terminals
        ElectricBehaviour start = BlockEntityBehaviour.get(level, rx, ElectricBehaviour.TYPE);
        if (start == null) return false;
        for (var en : start.getConnections().entrySet())
            if (contains(terminals, en.getKey().getTerminal()))
                for (BaseWireEntity w : en.getValue()) if (seen.add(w)) todo.add(w);
        while (!todo.isEmpty() && seen.size() < MAX_NODES) {
            Object node = todo.poll();
            if (node instanceof BlockPos p) {
                if (isRecorder(level.getBlockEntity(p))) return true;
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
                // block wires that end on this one or branch off it
                for (BaseWireEntity o : level.getEntitiesOfClass(BaseWireEntity.class, w.getBoundingBox().inflate(1.5))) {
                    if (seen.contains(o)) continue;
                    if (w.equals(resolve(level, o.getEndpoint1())) || w.equals(resolve(level, o.getEndpoint2()))) {
                        seen.add(o);
                        todo.add(o);
                    }
                }
            }
        }
        return false;
    }

    /** what a wire end is attached to: a block (its position) or another wire entity, or null */
    private static Object resolve(Level level, IWireEndpoint ep) {
        if (ep instanceof BlockWireEndpoint b) return b.getPos();
        if (ep instanceof BlockWireEntityEndpoint b) return b.getEntity(level);
        if (ep instanceof DeferredJunctionWireEndpoint j) return j.getEntity(level);
        return null;
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    private static boolean isRecorder(BlockEntity be) {
        return be instanceof InkerBlockEntity || be instanceof DecoderBlockEntity || be instanceof TeleprinterBlockEntity;
    }
}
