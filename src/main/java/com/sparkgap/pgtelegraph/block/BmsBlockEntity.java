package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;
import java.util.Locale;

/**
 * Battery management system (BMS): sits between a pack (BAT+/BAT-) and the load or charger
 * (LOAD+/LOAD-). A MOSFET in the + line opens when the pack voltage leaves the window set on the
 * panel (under-voltage / over-voltage, with hysteresis) or the current passes the limit (then it
 * retries after 2 s). The MOSFET is good for 100 A; above that it fails - SHORT, as real ones do,
 * leaving the pack unprotected.
 */
public class BmsBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final float FET_OHMS = 0.005f, FET_MAX_A = 100f;
    public static final Spec SPEC = new Spec(110f, 30f, 2f, 80f, new Spec.Part("mosfet", FET_OHMS, FET_MAX_A, 1));
    static final BehaviourType<NamedScroll> LOW = new BehaviourType<>("bms_low"), HIGH = new BehaviourType<>("bms_high"),
            LIMIT = new BehaviourType<>("bms_limit");

    private NamedScroll low, high, limit;
    private SwitchedWire fet, sense, ground;
    private boolean on = true;
    private int retry;
    /** why the FET is open: 0 none, 1 under-voltage, 2 over-voltage, 3 over-current */
    private int trip;
    private float shownV, shownA;

    public BmsBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        low = new StepScroll.Named(LOW, "BmsLow", Component.translatable("pgtelegraph.bms.low"), this, ValueBoxes.front(12, 4.5, 3, 0.25f),
                new int[]{50, 60, 80, 100, 105, 110, 115, 117, 120, 125, 130, 135, 140, 144, 146, 150, 160, 168, 180, 200, 240, 280, 360, 480, 600}).stepFormat(v -> String.format(Locale.ROOT, "%.1f V", v / 10.0));
        low.between(50, 600).withFormatter(v -> String.format(Locale.ROOT, "%.1f V", v / 10.0));
        low.value = 110;
        high = new StepScroll.Named(HIGH, "BmsHigh", Component.translatable("pgtelegraph.bms.high"), this, ValueBoxes.front(8, 4.5, 3, 0.25f),
                new int[]{50, 60, 80, 100, 105, 110, 115, 117, 120, 125, 130, 135, 140, 144, 146, 150, 160, 168, 180, 200, 240, 280, 360, 480, 600}).stepFormat(v -> String.format(Locale.ROOT, "%.1f V", v / 10.0));
        high.between(50, 600).withFormatter(v -> String.format(Locale.ROOT, "%.1f V", v / 10.0));
        high.value = 146;
        limit = new StepScroll.Named(LIMIT, "BmsLimit", Component.translatable("pgtelegraph.bms.limit"), this, ValueBoxes.front(4, 4.5, 3, 0.25f),
                new int[]{1, 2, 3, 5, 7, 10, 15, 20, 25, 30, 40, 50, 60, 70, 80, 100}).stepFormat(v -> v + " A");
        limit.between(1, 100).withFormatter(v -> v + " A");
        limit.value = 40;
        behaviours.add(low);
        behaviours.add(high);
        behaviours.add(limit);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(4); // 0 BAT+, 1 BAT-, 2 LOAD+, 3 LOAD-
        // a failed FET is a short: closed for good
        fet = builder.connectSwitch(FET_OHMS, builder.terminalNode(0), builder.terminalNode(2), true);
        ground = builder.connectSwitch(0.002f, builder.terminalNode(1), builder.terminalNode(3), true);
        sense = builder.connectSwitch(1_000_000f, builder.terminalNode(0), builder.terminalNode(1), true);
    }

    @Override
    protected void guards(Guard g) {
        g.check(fet, 0, FET_MAX_A);
        g.check(sense, SPEC.maxVolts(), 0);
    }

    @Override
    protected void openAll() {
        if (fet != null && !fet.getState()) fet.setState(true); // shorted
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || fet == null || sense == null) return;
        double v = Math.abs(sense.potentialDifference());
        double a = Math.abs(fet.current());
        if (!isBurnt()) {
            double lo = low.getValue() / 10.0, hi = high.getValue() / 10.0;
            int t = 0;
            if (on) {
                if (v < lo && v > 0.5) t = 1;
                else if (v > hi) t = 2;
                else if (a > limit.getValue()) t = 3;
                if (t != 0) {
                    on = false;
                    trip = t;
                    retry = 40;
                    level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.4f, 1.8f);
                }
            } else {
                boolean ok = switch (trip) {
                    case 1 -> v > lo + 0.5 || v < 0.5;
                    case 2 -> v < hi - 0.3;
                    default -> --retry <= 0;
                };
                if (ok) {
                    on = true;
                    trip = 0;
                    level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 1.8f);
                }
            }
            if (fet.getState() != on) fet.setState(on);
        }
        if (Math.abs(v - shownV) > 0.05 || Math.abs(a - shownA) > 0.1 || level.getGameTime() % 40 == 0) {
            shownV = (float) v;
            shownA = (float) a;
            sendData();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        tip.add(Component.literal(String.format(Locale.ROOT, "     %.2f V  %.1f A", shownV, shownA)).withStyle(ChatFormatting.GOLD));
        String state = isBurnt() ? "pgtelegraph.bms.shorted" : trip == 0 ? "pgtelegraph.bms.on"
                : trip == 1 ? "pgtelegraph.bms.uv" : trip == 2 ? "pgtelegraph.bms.ov" : "pgtelegraph.bms.oc";
        tip.add(Component.literal("     ").append(Component.translatable(state))
                .withStyle(isBurnt() ? ChatFormatting.DARK_RED : trip == 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("On", on);
        tag.putInt("Trip", trip);
        if (clientPacket) {
            tag.putFloat("V", shownV);
            tag.putFloat("A", shownA);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        on = !tag.contains("On") || tag.getBoolean("On");
        trip = tag.getInt("Trip");
        if (clientPacket) {
            shownV = tag.getFloat("V");
            shownA = tag.getFloat("A");
        }
    }
}
