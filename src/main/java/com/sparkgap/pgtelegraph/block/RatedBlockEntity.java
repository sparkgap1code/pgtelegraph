package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.sim.AbstractElectricWire;

/**
 * Device with nameplate ratings ({@link Spec}). Three ways to destroy it:
 * <ul>
 *   <li>overheating - Power Grid's own thermal model (may also explode, per PG config)</li>
 *   <li>over-voltage across a guarded element for {@value #OVERVOLT_TICKS} ticks - insulation flashover</li>
 *   <li>over-current - a 1 s budget at the limit, 10x faster above 5x the limit</li>
 * </ul>
 * A burnt device stays in the world but every contact and coil is open for good.
 */
public abstract class RatedBlockEntity extends ElectricBlockEntity {
    private static final int OVERVOLT_TICKS = 5;
    private static final int OVERCURRENT_BUDGET = 20;

    private boolean burnt;
    private int overVolt, overAmp;
    private boolean tickOverV;
    private int tickOverA;

    protected RatedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public abstract Spec spec();

    /** Report every guarded element with its limits; called each server tick. */
    protected abstract void guards(Guard g);

    /** Force every contact and coil open. Called on burn-out and every tick afterwards. */
    protected abstract void openAll();

    @FunctionalInterface
    public interface Guard {
        void check(AbstractElectricWire wire, float maxVolts, float maxAmps);
    }

    @Override
    public ThermalBehaviour specifyThermalBehaviour() {
        Spec s = spec();
        return ThermalBehaviour.forMaxPower(this, s.thermalMass(), s.maxPowerW(), s.maxTempC())
                .overheatCallback(this::burn);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide) {
            if (burnt && level.random.nextInt(10) == 0) {
                level.addParticle(ParticleTypes.SMOKE,
                        worldPosition.getX() + 0.3 + level.random.nextDouble() * 0.4,
                        worldPosition.getY() + 0.4,
                        worldPosition.getZ() + 0.3 + level.random.nextDouble() * 0.4,
                        0, 0.02, 0);
            }
            return;
        }
        if (burnt) {
            openAll();
            return;
        }
        tickOverV = false;
        tickOverA = 0;
        guards(this::evaluate);
        overVolt = tickOverV ? overVolt + 1 : 0;
        overAmp = tickOverA > 0 ? overAmp + tickOverA : Math.max(0, overAmp - 1);
        if (overVolt >= OVERVOLT_TICKS || overAmp >= OVERCURRENT_BUDGET) burn();
    }

    private void evaluate(AbstractElectricWire wire, float maxVolts, float maxAmps) {
        if (wire == null) return;
        if (maxVolts > 0 && Math.abs(wire.potentialDifference()) > maxVolts) tickOverV = true;
        double a = Math.abs(wire.current());
        if (maxAmps > 0 && a > maxAmps) tickOverA = Math.max(tickOverA, a > maxAmps * 5 ? 10 : 1);
    }

    protected void burn() {
        if (burnt || level == null || level.isClientSide) return;
        if (this instanceof RadioBlockEntity r && r.creative()) return;
        if (this instanceof LineSimulatorBlockEntity) return;
        burnt = true;
        openAll();
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 1.3f);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, 12, 0.2, 0.15, 0.2, 0.01);
        }
        setChanged();
        sendData();
    }

    /** Repair kit: new windings/contacts. The circuit is rebuilt so every element is fresh. */
    public void repair() {
        if (!burnt || level == null || level.isClientSide) return;
        burnt = false;
        overVolt = overAmp = 0;
        if (thermalBehaviour != null) thermalBehaviour.resetTemperature();
        if (electricBehaviour != null) electricBehaviour.rebuildCircuit(true);
        setChanged();
        sendData();
    }

    public boolean isBurnt() {
        return burnt;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (burnt) tag.putBoolean("Burnt", true);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        burnt = tag.getBoolean("Burnt");
    }
}
