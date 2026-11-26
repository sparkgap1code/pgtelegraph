package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
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
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Line test set (선로 시험기): a Wheatstone-bridge / megger box for finding faults. Wire LINE to the
 * line and EARTH to a ground rod, then press (empty hand): for two seconds it puts 10 V on the line
 * and measures the resistance to earth - an open line reads very high, a leak or an earth fault low,
 * the line looped to earth at the far end reads the conductor resistance. At the same time it
 * walks the line and reports, with the length of wire to each: where the line ends at a device,
 * where it stops at a tie point with nothing beyond (a break), and wet insulators that leak.
 */
public class LineTestSetBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final float TEST_V = 10f, SOURCE_OHMS = 10f;
    public static final Spec SPEC = new Spec(80f, 10f, 1f, 250f, Spec.rail(TEST_V));

    private SwitchedWire test;
    private int measuring;
    private double sumR;
    private int samples;
    // synced
    private float lastOhms = -1;
    private List<String> report = new ArrayList<>();

    public LineTestSetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    /** terminals: 0 LINE, 1 EARTH */
    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        FloatingNode src = builder.addInternalNode();
        builder.add(new VoltageSourceCoupling(src, builder.terminalNode(1), SOURCE_OHMS, TEST_V));
        test = builder.connectSwitch(0.01f, src, builder.terminalNode(0), false);
    }

    @Override
    protected void guards(Guard g) {
        g.check(test, SPEC.maxVolts(), 2f);
    }

    @Override
    protected void openAll() {
        if (test != null && test.getState()) test.setState(false);
    }

    public void start(Player p) {
        if (isBurnt() || test == null || level == null) return;
        measuring = 40;
        sumR = 0;
        samples = 0;
        test.setState(true);
        // walk the line now; the resistance comes when the measurement settles
        report = new ArrayList<>();
        for (WireTrace.Hit h : WireTrace.trace(level, worldPosition, new int[]{0}, be -> false)) {
            // kind | block description id | metres | x | y | z  - put into words on the client
            report.add(h.kind().name().toLowerCase(Locale.ROOT) + "|" + level.getBlockState(h.pos()).getBlock().getDescriptionId() + "|"
                    + Math.round(h.meters()) + "|" + h.pos().getX() + "|" + h.pos().getY() + "|" + h.pos().getZ());
            if (report.size() >= 8) break;
        }
        p.displayClientMessage(Component.translatable("pgtelegraph.linetest.started"), true);
        sendData();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || measuring <= 0 || test == null) return;
        measuring--;
        if (measuring < 30) { // let it settle, then average
            double a = Math.abs(test.current());
            double vLine = TEST_V - a * SOURCE_OHMS;
            if (a > 1e-7) sumR += vLine / a;
            else sumR += 1e9;
            samples++;
        }
        if (measuring == 0) {
            test.setState(false);
            lastOhms = (float) (sumR / Math.max(1, samples));
            setChanged();
            sendData();
            for (var p : level.players())
                if (p.distanceToSqr(worldPosition.getCenter()) < 64) {
                    p.displayClientMessage(Component.translatable("pgtelegraph.linetest.result", ohms(lastOhms)).withStyle(ChatFormatting.GOLD), false);
                    if (report.isEmpty()) p.displayClientMessage(Component.translatable("pgtelegraph.linetest.nothing"), false);
                    for (String s : report) p.displayClientMessage(Component.literal("  ").append(line(s)).withStyle(ChatFormatting.GRAY), false);
                }
        }
    }

    static Component line(String s) {
        String[] p = s.split("[|]");
        if (p.length < 6) return Component.literal(s);
        return Component.translatable("pgtelegraph.linetest." + p[0], Component.translatable(p[1]), p[2], p[3] + ", " + p[4] + ", " + p[5]);
    }

    static String ohms(double r) {
        if (r >= 1e8) return "∞";
        if (r >= 1e6) return String.format(Locale.ROOT, "%.1f MΩ", r / 1e6);
        if (r >= 1e3) return String.format(Locale.ROOT, "%.1f kΩ", r / 1e3);
        return String.format(Locale.ROOT, "%.0f Ω", r);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        tag.putFloat("Ohms", lastOhms);
        tag.putString("Report", String.join("\n", report));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        lastOhms = tag.contains("Ohms") ? tag.getFloat("Ohms") : -1;
        String s = tag.getString("Report");
        report = s.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(s.split("\n")));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        if (lastOhms >= 0) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.linetest.result", ohms(lastOhms)))
                .withStyle(ChatFormatting.GOLD));
        for (String s : report) tip.add(Component.literal("     ").append(line(s)).withStyle(ChatFormatting.GRAY));
        return true;
    }
}
