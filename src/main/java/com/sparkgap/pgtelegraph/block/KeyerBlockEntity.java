package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricWire;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.List;

/**
 * Iambic keyer (mode B style memory). Inputs are sensed electrically like a real keyer:
 * an internal 5 V rail feeds 1 kΩ pull-ups to DIT and DAH; a paddle contact shorting an input
 * to COM draws ~5 mA, which is read as "paddle closed" (threshold 2.5 mA).
 * Timing runs on the game tick (50 ms); see MorseClock for non-integer dit lengths.
 */
public class KeyerBlockEntity extends RatedBlockEntity {
    /** Low-voltage electronics: 30 V on any terminal, 0.5 A output contact, 85 °C. */
    public static final Spec SPEC = new Spec(85f, 2f, 1f, 30f,
            Spec.contact(0.05f, 0.5f, 1), Spec.pullup(1000f, 2), Spec.rail(5f));
    private static final float RAIL_VOLTS = 5f;
    private static final float PULLUP_OHMS = 1000f;
    private static final double SENSE_AMPS = 0.0025;
    private static final float OUTPUT_RESISTANCE = 0.05f;

    private SwitchedWire ditPullup, dahPullup;
    private SwitchedWire output;
    private ScrollValueBehaviour wpm;
    private final IambicKeyer keyer = new IambicKeyer();
    private boolean keyed;

    public KeyerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        wpm = new ScrollValueBehaviour(Component.translatable("pgtelegraph.keyer.speed"), this, new FrontBox())
                .between(5, 24)
                .withFormatter(v -> v + " WPM");
        wpm.value = 12;
        behaviours.add(wpm);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    protected void guards(Guard g) {
        g.check(output, SPEC.maxVolts(), SPEC.part("contact").maxAmps());
        g.check(ditPullup, SPEC.maxVolts(), 0.05f);
        g.check(dahPullup, SPEC.maxVolts(), 0.05f);
    }

    @Override
    protected void openAll() {
        for (SwitchedWire w : new SwitchedWire[]{output, ditPullup, dahPullup}) {
            if (w != null && w.getState()) w.setState(false);
        }
        keyed = false;
        if (keyer != null) keyer.reset();
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(5);
        FloatingNode rail = builder.addInternalNode();
        // rail(+) to COM(-), 1 Ω internal resistance, 5 V
        builder.add(new VoltageSourceCoupling(rail, builder.terminalNode(1), 1f, RAIL_VOLTS));
        ditPullup = builder.connectSwitch(PULLUP_OHMS, rail, builder.terminalNode(0), !isBurnt());
        dahPullup = builder.connectSwitch(PULLUP_OHMS, rail, builder.terminalNode(2), !isBurnt());
        output = builder.connectSwitch(OUTPUT_RESISTANCE, builder.terminalNode(3), builder.terminalNode(4), keyed);
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (output != null) applyPower(output);
    }

    private static boolean closed(ElectricWire pullup) {
        return pullup != null && Math.abs(pullup.current()) > SENSE_AMPS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isBurnt()) {
            if (level != null && !level.isClientSide && getBlockState().getValue(KeyerBlock.KEYED)) setKeyed(false);
            return;
        }
        boolean on = keyer.tick(closed(ditPullup), closed(dahPullup), wpm == null ? 12 : wpm.getValue());
        if (on != keyed) setKeyed(on);
    }

    private void setKeyed(boolean on) {
        keyed = on;
        boolean closed = on && !isBurnt();
        if (output != null && output.getState() != closed) output.setState(closed);
        BlockState s = getBlockState();
        if (s.getValue(KeyerBlock.KEYED) != on) level.setBlock(worldPosition, s.setValue(KeyerBlock.KEYED, on), 2);
    }

    /** Create value box on the front face of the case. */
    private static class FrontBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            // front face of the case sits 5 px in front of centre (z = 13 in south frame), mid-height
            return new Vec3(8 / 16.0, 3.5 / 16.0, 13.05 / 16.0);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction == state.getValue(KeyerBlock.FACING);
        }
    }
}
