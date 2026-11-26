package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.List;
import java.util.Locale;

/**
 * Station power supply (직류 전원장치): a switch-mode supply that turns any grid voltage from 90 to
 * 300 V into a steady 13.8 V for the radios. It draws from IN what OUT delivers divided by its
 * efficiency (88 %) plus a few watts idling. Rated 30 A; past 33 A it limits the current by
 * folding the voltage back (so a short across OUT does not burn it). Under 90 V in it shuts off;
 * more than 300 V in burns it.
 */
public class PowerSupplyBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation, PowerLamp {
    @Override
    public int lamp() {
        if (isBurnt()) return FAULT;
        if (onBattery) return BLINK;
        if (shownOut < 1) return OFF;
        return limiting ? WARN : ON;
    }

    public static final double OUT_V = 13.8, MAX_A = 30, LIMIT_A = 33, MIN_IN = 90, MAX_IN = 300, EFF = 0.88, IDLE_W = 6;
    public static final Spec SPEC = new Spec(85f, 150f, 6f, (float) MAX_IN, Spec.rail((float) OUT_V));
    private static final float SOURCE_OHMS = 0.004f;

    private SwitchedWire input, output;
    private VoltageSourceCoupling source;
    private double setV = OUT_V, inW = IDLE_W;
    // synced
    private float shownIn, shownOut, shownA;
    private boolean limiting;
    /** UPS: stored energy (Wh); synced as a fraction */
    protected double chargeWh;
    private float shownCharge;
    private boolean onBattery;

    public PowerSupplyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    /** built-in battery (Wh); 0 = a plain supply */
    public double capacityWh() {
        return 0;
    }

    /** charger power drawn from the mains while the battery is not full (W) */
    public double chargerW() {
        return 0;
    }

    public double chargeFraction() {
        return capacityWh() <= 0 ? 0 : (level != null && level.isClientSide ? shownCharge : chargeWh / capacityWh());
    }

    public double inputVolts() {
        return shownIn;
    }

    public double outputVolts() {
        return shownOut;
    }

    public double outputAmps() {
        return shownA;
    }

    public boolean isLimiting() {
        return limiting;
    }

    public boolean onBattery() {
        return onBattery;
    }

    public boolean outputOn() {
        return output != null && output.getState() && !isBurnt();
    }

    /** server: the battery just took over (or gave back to the mains) */
    protected void batteryEdge(boolean nowOnBattery) {}

    /** server, every tick while running from the battery */
    protected void batteryTick() {}

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(4);
        input = builder.connectSwitch((float) (230 * 230 / IDLE_W), builder.terminalNode(0), builder.terminalNode(1), !isBurnt());
        FloatingNode rail = builder.addInternalNode();
        source = new VoltageSourceCoupling(rail, builder.terminalNode(3), SOURCE_OHMS, (float) OUT_V);
        builder.add(source);
        output = builder.connectSwitch(0.001f, rail, builder.terminalNode(2), false);
    }

    @Override
    protected void guards(Guard g) {
        g.check(input, (float) MAX_IN, 0);
    }

    @Override
    protected void openAll() {
        if (input != null && input.getState()) input.setState(false);
        if (output != null && output.getState()) output.setState(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isBurnt() || input == null || output == null || source == null) return;
        double vin = Math.abs(input.potentialDifference());
        boolean mains = vin >= MIN_IN;
        double cap = capacityWh();
        boolean battery = !mains && cap > 0 && chargeWh > 0;
        boolean on = mains || battery;
        if (battery != onBattery) {
            onBattery = battery;
            batteryEdge(battery);
            sendData();
        }
        if (battery) batteryTick();
        if (output.getState() != on) output.setState(on);
        double a = on ? Math.abs(output.current()) : 0;
        // constant voltage up to the limit, then constant current (the voltage folds back)
        if (a > LIMIT_A) {
            setV = Math.max(0.05, setV * LIMIT_A / a);
            limiting = true;
        } else {
            setV = Math.min(OUT_V, setV * 1.25 + 0.05);
            limiting = setV < OUT_V - 0.01;
        }
        source.setVoltage(setV);
        double outW = on ? Math.max(0, setV * a) : 0;
        double charging = mains && cap > 0 && chargeWh < cap ? chargerW() * Math.min(1, (cap - chargeWh) / (cap * 0.1) + 0.1) : 0;
        double want = mains ? outW / EFF + IDLE_W + charging : 0.5;
        if (mains) chargeWh = Math.min(cap, chargeWh + charging * 0.85 / 72000.0);
        else if (battery) chargeWh = Math.max(0, chargeWh - (outW / 0.92 + IDLE_W * 0.5) / 72000.0);
        inW += (want - inW) * 0.5; // smoothed: the grid solver dislikes a load that jumps every tick
        double vRef = Math.max(vin, MIN_IN);
        input.setResistance(Math.max(1, vRef * vRef / inW));
        if (thermalBehaviour != null) thermalBehaviour.applyTickPower(mains ? Math.max(0, inW - outW - charging * 0.85) : outW * 0.08);
        float vo = on ? (float) Math.max(0, setV - a * SOURCE_OHMS) : 0;
        if (Math.abs(vin - shownIn) > 1 || Math.abs(vo - shownOut) > 0.05 || Math.abs(a - shownA) > 0.1 || level.getGameTime() % 40 == 0) {
            shownCharge = cap > 0 ? (float) (chargeWh / cap) : 0;
            shownIn = (float) vin;
            shownOut = vo;
            shownA = (float) a;
            sendData();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        if (clientPacket) {
            tag.putFloat("In", shownIn);
            tag.putFloat("Out", shownOut);
            tag.putFloat("A", shownA);
            tag.putBoolean("Cc", limiting);
            tag.putFloat("Chg", shownCharge);
            tag.putBoolean("Bat", onBattery);
        }
        if (capacityWh() > 0) tag.putDouble("ChargeWh", chargeWh);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        if (clientPacket) {
            shownIn = tag.getFloat("In");
            shownOut = tag.getFloat("Out");
            shownA = tag.getFloat("A");
            limiting = tag.getBoolean("Cc");
            shownCharge = tag.getFloat("Chg");
            onBattery = tag.getBoolean("Bat");
        }
        if (tag.contains("ChargeWh")) chargeWh = tag.getDouble("ChargeWh");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        if (isBurnt()) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.psu.burnt")).withStyle(ChatFormatting.RED));
            return true;
        }
        boolean live = shownIn >= MIN_IN || onBattery;
        tip.add(Component.literal(String.format(Locale.ROOT, "     IN %.0f V", shownIn))
                .withStyle(shownIn >= MIN_IN ? ChatFormatting.GRAY : ChatFormatting.RED));
        tip.add(Component.literal(String.format(Locale.ROOT, "     OUT %.1f V  %.1f A", shownOut, shownA))
                .withStyle(!live ? ChatFormatting.DARK_GRAY : limiting ? ChatFormatting.RED : ChatFormatting.GREEN));
        if (capacityWh() > 0) {
            double hours = shownA > 0.05 ? capacityWh() * shownCharge / (shownOut * shownA / 0.92 + IDLE_W * 0.5) : -1;
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.ups.charge", Math.round(shownCharge * 100),
                            hours < 0 ? "-" : hours >= 1 ? String.format(Locale.ROOT, "%.1f h", hours) : String.format(Locale.ROOT, "%.0f min", hours * 60)))
                    .withStyle(shownCharge < 0.2 ? ChatFormatting.RED : ChatFormatting.YELLOW));
        }
        tip.add(Component.literal("     ").append(Component.translatable(onBattery ? "pgtelegraph.ups.on_battery"
                : shownIn < MIN_IN ? (capacityWh() > 0 ? "pgtelegraph.ups.empty" : "pgtelegraph.psu.no_input")
                : limiting ? "pgtelegraph.psu.limit" : "pgtelegraph.psu.ok")).withStyle(onBattery ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        return true;
    }
}
