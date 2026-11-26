package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

/**
 * A device driven by an electromagnet coil between two terminals. The armature pulls in when the
 * coil current reaches {@link #pickupAmps()} and drops out below {@link #DROPOUT_RATIO} of that
 * (real relays/sounders have this hysteresis). Direction of current does not matter.
 */
public abstract class CoilDeviceBlockEntity extends RatedBlockEntity {
    public static final BooleanProperty ENERGIZED = BooleanProperty.create("energized");
    protected static final double DROPOUT_RATIO = 0.7;

    /** Built as a closed switch so a burnt coil can be opened (wire breaks). */
    protected SwitchedWire coil;
    private boolean energized;

    protected CoilDeviceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract double pickupAmps();

    /** Called on the server when the armature pulls in (true) or drops out (false). */
    protected abstract void onArmature(boolean energized);

    @Override
    protected void guards(Guard g) {
        g.check(coil, spec().maxVolts(), spec().part("coil").maxAmps());
    }

    @Override
    protected void openAll() {
        if (coil != null && coil.getState()) coil.setState(false);
    }

    public double coilCurrent() {
        return coil == null ? 0 : Math.abs(coil.current());
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (coil != null) applyPower(coil);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        double i = coilCurrent();
        double pickup = pickupAmps();
        boolean next = energized ? i >= pickup * DROPOUT_RATIO : i >= pickup;
        if (next != energized) {
            energized = next;
            BlockState s = getBlockState();
            if (s.hasProperty(ENERGIZED) && s.getValue(ENERGIZED) != next) {
                level.setBlock(worldPosition, s.setValue(ENERGIZED, next), 3);
            }
            onArmature(next);
        }
    }

    public boolean isEnergized() {
        return energized;
    }
}
