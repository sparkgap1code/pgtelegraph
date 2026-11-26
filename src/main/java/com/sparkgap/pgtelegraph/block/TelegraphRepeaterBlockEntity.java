package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.List;
import java.util.Locale;

/**
 * Telegraph repeater station (전신 중계기): on a long line the current gets too weak for a sounder.
 * Put this in the middle: a line relay on each side (150 Ω, WEST / EAST to EARTH) and a fresh 120 V
 * battery. Current arriving from the west pulls the west relay, which puts the station battery on
 * the east line - and the other way round. While one side is repeating, the other side's relay is
 * locked out so the repeater does not latch on its own output (the automatic repeater's lock
 * relays). Pick-up current: value box.
 */
public class TelegraphRepeaterBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final float COIL_OHMS = 150f, BATTERY_V = 120f, SOURCE_OHMS = 100f;
    public static final Spec SPEC = new Spec(90f, 30f, 2f, 250f, Spec.coil(COIL_OHMS, 0.3f), Spec.rail(BATTERY_V));
    static final int[] PICKUP_MA = {2, 3, 5, 7, 10, 15, 20, 30, 50};
    private static final int NONE = 0, W2E = 1, E2W = 2, HOLD = 3;

    private SwitchedWire coilW, coilE, driveW, driveE;
    private ScrollValueBehaviour pickup;
    private int dir, hold;
    private float shownW, shownE;

    public TelegraphRepeaterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        pickup = new StepScroll.Plain(Component.translatable("pgtelegraph.relay.pickup"), this,
                ValueBoxes.top(8, 5, 8, 0.3f), PICKUP_MA).stepFormat(v -> v + " mA")
                .between(1, 50)
                .withFormatter(v -> v + " mA");
        pickup.value = 5;
        behaviours.add(pickup);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    /** 0 WEST, 1 EAST, 2 EARTH */
    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(3);
        var earth = builder.terminalNode(2);
        boolean ok = !isBurnt();
        coilW = builder.connectSwitch(COIL_OHMS, builder.terminalNode(0), earth, ok);
        coilE = builder.connectSwitch(COIL_OHMS, builder.terminalNode(1), earth, ok);
        FloatingNode bat = builder.addInternalNode();
        builder.add(new VoltageSourceCoupling(bat, earth, 2f, BATTERY_V));
        driveW = builder.connectSwitch(SOURCE_OHMS, bat, builder.terminalNode(0), false);
        driveE = builder.connectSwitch(SOURCE_OHMS, bat, builder.terminalNode(1), false);
    }

    @Override
    protected void guards(Guard g) {
        g.check(coilW, 0, 0.3f);
        g.check(coilE, 0, 0.3f);
    }

    @Override
    protected void openAll() {
        for (SwitchedWire w : new SwitchedWire[]{coilW, coilE, driveW, driveE}) if (w != null && w.getState()) w.setState(false);
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (coilW != null) applyPower(coilW);
        if (coilE != null) applyPower(coilE);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isBurnt() || coilW == null) return;
        double p = (pickup == null ? 5 : pickup.getValue()) / 1000.0;
        double w = Math.abs(coilW.current()), e = Math.abs(coilE.current());
        int was = dir;
        switch (dir) {
            case W2E -> { // repeating west -> east: the east relay is locked out
                if (w < p * 0.7) {
                    dir = HOLD;
                    hold = 2;
                }
            }
            case E2W -> {
                if (e < p * 0.7) {
                    dir = HOLD;
                    hold = 2;
                }
            }
            case HOLD -> { // let the line we were driving settle before listening to it again
                if (--hold <= 0) dir = NONE;
            }
            default -> {
                if (w > p) dir = W2E;
                else if (e > p) dir = E2W;
            }
        }
        driveE.setState(dir == W2E);
        driveW.setState(dir == E2W);
        if (dir != was && (dir == W2E || dir == E2W || was == W2E || was == E2W))
            level.playSound(null, worldPosition, PgTelegraph.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.4f, dir == NONE || dir == HOLD ? 0.9f : 1.1f);
        float sw = (float) (w * 1000), se = (float) (e * 1000);
        if (Math.abs(sw - shownW) > 0.3f || Math.abs(se - shownE) > 0.3f || dir != was) {
            shownW = sw;
            shownE = se;
            sendData();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        if (clientPacket) {
            tag.putFloat("W", shownW);
            tag.putFloat("E", shownE);
            tag.putInt("Dir", dir);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        if (clientPacket) {
            shownW = tag.getFloat("W");
            shownE = tag.getFloat("E");
            dir = tag.getInt("Dir");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        tip.add(Component.literal(String.format(Locale.ROOT, "     W %.1f mA   E %.1f mA", shownW, shownE)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.trepeater.dir." + (dir == HOLD ? NONE : dir)))
                .withStyle(dir == W2E || dir == E2W ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        return true;
    }
}
