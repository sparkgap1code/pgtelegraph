package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;

/**
 * Block entity of a mains-powered station box: a resistive load of the station's rated power across
 * PWR+ / PWR-. It works while the supply is at least 70 % of its rating; more than 1.3 x burns it.
 * Subclasses ask {@link #powerTick()} at the top of their server tick.
 */
public abstract class PoweredStationBlockEntity extends RatedBlockEntity implements PowerLamp {
    @Override
    public int lamp() {
        return isBurnt() ? FAULT : isPowered() ? ON : OFF;
    }

    private SwitchedWire supply;
    /** synced: running on its supply */
    protected boolean powered;
    /** fake sets made on the client (FPV goggles) are always on */
    public boolean alwaysOn;

    protected PoweredStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** rated supply (V) */
    public abstract double nominalV();

    /** power drawn when on (W) */
    public abstract double ratedW();

    public static Spec spec(double v, double w) {
        return new Spec(90f, (float) Math.max(30, w * 3), 4f, (float) (v * 1.3),
                new Spec.Part("load", (float) (v * v / w), 0, 1));
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        supply = builder.connectSwitch((float) (nominalV() * nominalV() / ratedW()), builder.terminalNode(0), builder.terminalNode(1), true);
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (supply != null) applyPower(supply);
    }

    @Override
    protected void guards(Guard g) {
        g.check(supply, spec().maxVolts(), 0);
    }

    @Override
    protected void openAll() {
        if (supply != null && supply.getState()) supply.setState(false);
    }

    public boolean isPowered() {
        return alwaysOn || powered;
    }

    /** server: is the supply up? (call at the top of the server tick; false = do nothing this tick) */
    protected boolean powerTick() {
        boolean p = !isBurnt() && supply != null && Math.abs(supply.potentialDifference()) >= 0.7 * nominalV();
        if (p != powered) {
            powered = p;
            setChanged();
            sendData();
            if (!p) lostPower();
        }
        return p;
    }

    /** the supply just failed: stop what it was doing */
    protected void lostPower() {}

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) tag.putBoolean("Pwr", powered);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) powered = tag.getBoolean("Pwr");
    }
}
