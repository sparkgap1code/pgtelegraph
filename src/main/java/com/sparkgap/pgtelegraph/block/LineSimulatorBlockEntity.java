package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;

import java.util.List;
import java.util.Locale;

/**
 * Creative line simulator (크리에이티브 선로 거리 생성기): what goes in at A comes out at B as if the
 * two were joined by an overhead line of the set length (1 m - 250 km, value box). Electrically a
 * T section: half the wire's resistance, the leakage of all its insulators to earth (much worse in
 * rain), the other half. The line tracer counts the length too, so the line test set and the field
 * telephone see the distance. Sneak + empty hand: wire kind (iron telegraph wire / copper). Never burns.
 */
public class LineSimulatorBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final double[] OHMS_PER_KM = {8, 3};
    /** insulation to earth per km of line (Ω·km): dry, wet */
    public static final double DRY_LEAK = 5e6, WET_LEAK = 5e4;
    public static final Spec SPEC = new Spec(1000f, 1e9f, 1e6f, 1e6f);

    private ScrollValueBehaviour meters;
    private SwitchedWire a, b, leak;
    private int kind;
    private boolean wet;

    public LineSimulatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        meters = new StepScroll.Rows(Component.translatable("pgtelegraph.linesim.length"), this,
                ValueBoxes.top(8, 6, 8, 0.3f), 250, new int[]{1, 10, 100, 1000}, "m");
        meters.value = 1000;
        meters.withCallback(v -> update());
        behaviours.add(meters);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    public double meters() {
        return meters == null ? 1000 : meters.getValue();
    }

    private double km() {
        return meters() / 1000;
    }

    private float halfOhms() {
        return (float) Math.max(0.001, OHMS_PER_KM[kind] * km() / 2);
    }

    private float leakOhms() {
        return (float) Math.min(1e9, (wet ? WET_LEAK : DRY_LEAK) / Math.max(1e-3, km()));
    }

    /** 0 A, 1 B */
    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        FloatingNode mid = builder.addInternalNode();
        a = builder.connectSwitch(halfOhms(), builder.terminalNode(0), mid, true);
        b = builder.connectSwitch(halfOhms(), mid, builder.terminalNode(1), true);
        leak = builder.connectSwitch(leakOhms(), mid, null, true);
    }

    private void update() {
        if (a != null) a.setResistance(halfOhms());
        if (b != null) b.setResistance(halfOhms());
        if (leak != null) leak.setResistance(leakOhms());
        setChanged();
        sendData();
    }

    @Override
    protected void guards(Guard g) {}

    @Override
    protected void openAll() {}

    public void nextKind(Player p) {
        kind = (kind + 1) % OHMS_PER_KM.length;
        update();
        p.displayClientMessage(Component.translatable("pgtelegraph.linesim.kind." + kind), true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || level.getGameTime() % 40 != 0) return;
        boolean w = level.isRainingAt(worldPosition.above());
        if (w != wet) {
            wet = w;
            update();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        tag.putInt("Kind", kind);
        tag.putBoolean("Wet", wet);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        kind = Math.floorMod(tag.getInt("Kind"), OHMS_PER_KM.length);
        wet = tag.getBoolean("Wet");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal("     " + StepScroll.Rows.fmt((int) meters(), "m") + "  ").append(Component.translatable("pgtelegraph.linesim.kind." + kind))
                .withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal(String.format(Locale.ROOT, "     %.1f Ω", 2 * halfOhms())).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("     ").append(Component.translatable(wet ? "pgtelegraph.linesim.wet" : "pgtelegraph.linesim.dry", LineTestSetBlockEntity.ohms(leakOhms())))
                .withStyle(wet ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        if (a != null) tip.add(Component.literal(String.format(Locale.ROOT, "     A %.1f mA  B %.1f mA", Math.abs(a.current()) * 1000, Math.abs(b.current()) * 1000))
                .withStyle(ChatFormatting.GRAY));
        return true;
    }
}
