package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.parts.BatteryState;
import com.sparkgap.pgtelegraph.parts.Chassis;
import com.sparkgap.pgtelegraph.parts.PartDef;
import com.sparkgap.pgtelegraph.parts.PartItem;
import com.sparkgap.pgtelegraph.parts.PartSlot;
import com.sparkgap.pgtelegraph.parts.Wreckage;
import com.sparkgap.pgtelegraph.radio.HandheldRadioItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;
import java.util.Locale;

/**
 * Desk charger (무전기 충전기) for a handheld or a loose battery pack. Draws about 10 W from a 12 V
 * Power Grid supply (14.4 Ω while charging), 85 % into the pack, needs 9 V or more.
 * The front panel sets the end-of-charge voltage. The charger trusts it:
 * <ul>
 *   <li>above the pack's full voltage, a pack without protection keeps being pushed after it is
 *       full - Li-ion goes into thermal runaway (the radio explodes), LiFePO4 / NiMH vent</li>
 *   <li>below it, the pack only reaches part of its charge</li>
 *   <li>a bare Li-ion pack that was run flat goes into runaway after a few seconds of charging</li>
 *   <li>alkaline cells cannot be charged: they leak</li>
 * </ul>
 */
public class HandheldChargerBlockEntity extends RatedBlockEntity {
    public static final float LOAD_OHMS = 14.4f;
    public static final Spec SPEC = new Spec(70f, 15f, 1f, 20f, new Spec.Part("load", LOAD_OHMS, 0, 1));
    private static final int RUNAWAY_TICKS = 60;

    public enum Mode implements INamedIconOptions {
        LI_ION(8.4), LIFEPO4(7.3), NIMH(8.7);
        public final double volts;

        Mode(double v) {
            this.volts = v;
        }

        @Override
        public AllIcons getIcon() {
            return AllIcons.I_TARGET;
        }

        @Override
        public String getTranslationKey() {
            return "pgtelegraph.charger.mode." + name().toLowerCase(Locale.ROOT);
        }
    }

    private SwitchedWire load;
    private ScrollOptionBehaviour<Mode> mode;
    private ItemStack docked = ItemStack.EMPTY;
    private double gainedWh;
    private int overTicks, deepTicks, alkTicks;
    private int runaway = -1;

    public HandheldChargerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        mode = new ScrollOptionBehaviour<>(Mode.class, Component.translatable("pgtelegraph.charger.mode"), this,
                ValueBoxes.front(8, 2, 4, 0.3f));
        behaviours.add(mode);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        load = builder.connectSwitch(LOAD_OHMS, builder.terminalNode(0), builder.terminalNode(1), false);
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (load != null) applyPower(load);
    }

    @Override
    protected void guards(Guard g) {
        g.check(load, SPEC.maxVolts(), 0);
    }

    @Override
    protected void openAll() {
        if (load != null && load.getState()) load.setState(false);
    }

    public boolean hasRadio() {
        return !docked.isEmpty();
    }

    public boolean accepts(ItemStack s) {
        return s.getItem() instanceof HandheldRadioItem
                || (PartItem.def(s) != null && PartItem.def(s).slot() == PartSlot.BATTERY);
    }

    public boolean insert(ItemStack s) {
        if (hasRadio() || !accepts(s)) return false;
        docked = s.copyWithCount(1);
        gainedWh = 0;
        overTicks = deepTicks = alkTicks = 0;
        changed();
        return true;
    }

    public ItemStack take() {
        ItemStack out = takeRaw();
        changed();
        return out;
    }

    /** Remove without touching the block state (block being broken). */
    public ItemStack takeRaw() {
        flush();
        ItemStack out = docked;
        docked = ItemStack.EMPTY;
        runaway = -1;
        setChanged();
        return out;
    }

    private void changed() {
        if (level != null) {
            BlockState s = getBlockState();
            if (s.hasProperty(HandheldChargerBlock.LOADED) && s.getValue(HandheldChargerBlock.LOADED) != hasRadio()) {
                level.setBlock(worldPosition, s.setValue(HandheldChargerBlock.LOADED, hasRadio()), 3);
            }
        }
        setChanged();
    }

    // ---- the pack being charged: inside a docked radio, or docked on its own
    private ItemStack pack() {
        if (docked.getItem() instanceof HandheldRadioItem) {
            return Chassis.of(docked, PartSlot.Layout.HANDHELD).get(PartSlot.Layout.HANDHELD.indexOf(PartSlot.BATTERY));
        }
        return docked;
    }

    private void setPack(ItemStack p) {
        if (docked.getItem() instanceof HandheldRadioItem) {
            NonNullList<ItemStack> parts = Chassis.of(docked, PartSlot.Layout.HANDHELD);
            parts.set(PartSlot.Layout.HANDHELD.indexOf(PartSlot.BATTERY), p);
            Chassis.store(docked, parts);
        } else docked = p;
        setChanged();
    }

    private void flush() {
        if (docked.isEmpty() || gainedWh <= 0) return;
        ItemStack p = pack();
        PartDef d = PartItem.def(p);
        BatteryState b = PartItem.battery(p);
        if (d != null && b != null) {
            ItemStack n = p.copy();
            n.set(PgTelegraph.BATTERY_STATE.get(), b.withCharge(b.chargeWh() + gainedWh, d));
            setPack(n);
        }
        gainedWh = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || load == null) return;
        Vec3 at = worldPosition.getCenter().add(0, 0.2, 0);
        if (runaway >= 0) {
            if (load.getState()) load.setState(false);
            Wreckage.smoulder(level, at, runaway);
            if (runaway-- == 0) {
                docked = docked.getItem() instanceof HandheldRadioItem
                        ? new ItemStack(PgTelegraph.EXPLODED_RADIO.get()) : Wreckage.burnt(docked);
                gainedWh = 0;
                Wreckage.explode(level, at);
                changed();
            }
            return;
        }
        ItemStack p = pack();
        PartDef d = PartItem.def(p);
        BatteryState b = PartItem.battery(p);
        double v = Math.abs(load.potentialDifference());
        boolean supply = v >= 9 && !isBurnt();
        if (d == null || b == null || !supply) {
            if (load.getState()) load.setState(false);
            return;
        }
        Mode m = mode == null ? Mode.LI_ION : mode.get();
        double charge = b.chargeWh() + gainedWh, cap = b.capacity(d);
        // a lower end voltage stops the pack early; a higher one never stops on its own
        double reach = Math.min(1, Math.max(0, (m.volts - d.chemistry().cutoffV()) / (d.chemistry().fullV() - d.chemistry().cutoffV())));
        boolean over = m.volts > d.chemistry().fullV() + 0.15;
        boolean full = charge >= cap * Math.min(reach, 1) - 1e-6;

        boolean charging;
        if (d.chemistry() == PartDef.Chemistry.ALKALINE) {
            charging = true;
            if (++alkTicks > 100) {
                Wreckage.vent(level, at);
                setPack(Wreckage.burnt(p));
                return;
            }
        } else if (full && !(over && !d.protectedCells())) {
            charging = false; // done (or the pack's protection stops it)
        } else {
            charging = true;
            if (full) { // still being pushed past full
                if (++overTicks > 200) {
                    if (d.chemistry() == PartDef.Chemistry.LI_ION) runaway = RUNAWAY_TICKS;
                    else {
                        Wreckage.vent(level, at);
                        setPack(Wreckage.burnt(p));
                    }
                    return;
                }
            }
            if (b.deep() && d.chemistry() == PartDef.Chemistry.LI_ION && !d.protectedCells() && ++deepTicks > 100) {
                runaway = RUNAWAY_TICKS;
                return;
            }
        }
        if (load.getState() != charging) load.setState(charging);
        if (charging && !full) gainedWh += Math.abs(v * load.current()) * 0.85 / 20.0 / 3600.0;
        if (level.getGameTime() % 20 == 0) flush();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!docked.isEmpty() && !clientPacket) tag.put("Radio", docked.save(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket) docked = tag.contains("Radio") ? ItemStack.parseOptional(registries, tag.getCompound("Radio")) : ItemStack.EMPTY;
    }
}
