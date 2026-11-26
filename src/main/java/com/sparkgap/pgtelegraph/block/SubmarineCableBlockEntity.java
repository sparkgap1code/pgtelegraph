package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.sim.special.CapacitorWire;

import java.util.List;
import java.util.Locale;

/**
 * Submarine cable (해저 케이블): a long gutta-percha-insulated copper core with the sea all round it
 * as the return. The whole length (10 to 4000 km, value box) is in this block as an RC ladder:
 * 3 Ω and 0.25 μF per km, the capacitance to the sea. A pulse put in at one end comes out of the
 * other rounded, late and weak: the longer the cable the slower you must send - the trouble of
 * the 1858 Atlantic cable. Read the far end with a mirror galvanometer, not a sounder.
 * Terminals: A and B, the two shore ends.
 */
public class SubmarineCableBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final double OHMS_PER_KM = 3, UF_PER_KM = 0.25;
    public static final int SECTIONS = 8;
    static final int[] LENGTHS = {10, 30, 100, 300, 500, 1000, 2000, 3000, 4000};
    public static final Spec SPEC = new Spec(60f, 50f, 50f, 2000f, Spec.coil(1000f, 0.5f));

    private ScrollValueBehaviour km;
    private SwitchedWire first;
    private int builtKm = -1;

    public SubmarineCableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        km = new StepScroll.Plain(Component.translatable("pgtelegraph.cable.length"), this,
                ValueBoxes.top(8, 4, 8, 0.3f), LENGTHS).stepFormat(v -> v + " km")
                .between(1, 4000)
                .withFormatter(v -> v + " km");
        km.value = 500;
        km.withCallback(v -> {
            if (electricBehaviour != null && level != null && !level.isClientSide && v != builtKm) electricBehaviour.rebuildCircuit(true);
        });
        behaviours.add(km);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    public int lengthKm() {
        return km == null ? 500 : km.getValue();
    }

    /** the time constant R·C of the whole cable (s): signalling slower than a few of these is hopeless */
    public double rcSeconds() {
        double L = lengthKm();
        return OHMS_PER_KM * L * UF_PER_KM * 1e-6 * L;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        int L = lengthKm();
        builtKm = L;
        float r = (float) (OHMS_PER_KM * L / SECTIONS);
        double c = UF_PER_KM * 1e-6 * L / SECTIONS;
        IElectricNode prev = builder.terminalNode(0);
        for (int i = 0; i < SECTIONS; i++) {
            IElectricNode next = i == SECTIONS - 1 ? builder.terminalNode(1) : builder.addInternalNode();
            SwitchedWire w = builder.connectSwitch(Math.max(0.01f, r), prev, next, !isBurnt());
            if (i == 0) first = w;
            if (i < SECTIONS - 1) builder.add(new CapacitorWire(c, next, null)); // to the sea
            prev = next;
        }
    }

    @Override
    protected void guards(Guard g) {
        g.check(first, 0, 0.5f);
    }

    @Override
    protected void openAll() {
        if (first != null && first.getState()) first.setState(false);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        double L = lengthKm();
        tip.add(Component.literal(String.format(Locale.ROOT, "     %d km  %.0f Ω  %.0f μF", (int) L, OHMS_PER_KM * L, UF_PER_KM * L))
                .withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.cable.rc", String.format(Locale.ROOT, "%.2f", rcSeconds())))
                .withStyle(ChatFormatting.GOLD));
        if (first != null) tip.add(Component.literal(String.format(Locale.ROOT, "     %.2f mA", Math.abs(first.current()) * 1000)).withStyle(ChatFormatting.AQUA));
        return true;
    }
}
