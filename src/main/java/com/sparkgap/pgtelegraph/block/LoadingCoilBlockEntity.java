package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loading coil / variometer (부하 코일) at the foot of an electrically short mast. It resonates the
 * mast and its umbrella against the coil:
 * <pre>
 *   H     = 2 m per mast block (a VLF mast cannot be built full height under the build limit)
 *   C_ant = 6 pF per metre of mast + 7 pF per metre of top-load wire
 *   f0    = 1 / (2π √(L C_ant))
 *   h_eff = H (½ + ½ min(1, wire / 3H))           top loading lifts the current maximum
 *   R_rad = 1579 (h_eff / λ)²                     short monopole over ground
 *   R_loss= X/Q (coil Q 800, Litz 3000) + ground (25 Ω, falling with ground mats within 24 blocks of the mast foot) + 0.1 Ω
 *   η     = R_rad / (R_rad + R_loss),   loaded Q = X / (2 R_total)
 * </pre>
 * The match to 50 Ω is assumed done by the coil's taps; what is left is the resonance and the loss.
 */
public class LoadingCoilBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final double MIN_MH = 0.001, MAX_MH = 1000;
    /** Q of this coil's winding (800 solid copper, 3000 Litz) */
    private double coilQ() {
        return getBlockState().getBlock() instanceof LoadingCoilBlock b ? b.coilQ : 800;
    }
    private static final int MAX_WIRE = 8192;
    /** electrical metres per mast block when loaded by a coil */
    public static final double MAST_SCALE = 2;

    public record Analysis(BlockPos feed, double mastM, double wireM, double capF, double f0MHz, double q,
                           double efficiencyDb, double heightM, int groundMats, double rRad, double rLoss, double spanM) {}

    private double inductanceMH = 10;
    private ThermalBehaviour thermal;
    private Analysis last;
    private int rescan;
    // synced for goggles
    private double shownF0, shownEff, shownQ, shownMast, shownWire;
    private int shownMats;

    public LoadingCoilBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        thermal = ThermalBehaviour.forMaxPower(this, 3000f, 150000f, 200f);
        behaviours.add(thermal);
    }

    public double inductanceMH() {
        return inductanceMH;
    }

    public void setInductance(double mH) {
        inductanceMH = Math.max(MIN_MH, Math.min(MAX_MH, mH));
        last = null;
        rescan = 0;
        setChanged();
        sendData();
    }

    /** Called by the RF chain: the coil's share of the losses heats it. */
    public void heat(double watts) {
        if (thermal != null) thermal.applyTickPower(watts);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (--rescan <= 0) {
            rescan = 40;
            last = compute();
            Analysis a = last;
            double f0 = a == null ? 0 : a.f0MHz(), eff = a == null ? 0 : 100 * Math.pow(10, -a.efficiencyDb() / 10);
            if (Math.abs(f0 - shownF0) > 1e-6 || Math.abs(eff - shownEff) > 0.01 || (a != null && a.groundMats() != shownMats)) {
                shownF0 = f0;
                shownEff = eff;
                shownQ = a == null ? 0 : a.q();
                shownMast = a == null ? 0 : a.mastM();
                shownWire = a == null ? 0 : a.wireM();
                shownMats = a == null ? 0 : a.groundMats();
                sendData();
            }
        }
    }

    /** Latest analysis (recomputed every 2 s or after retuning). */
    public Analysis analyse() {
        if (last == null && level != null) last = compute();
        return last;
    }

    private Analysis compute() {
        BlockPos base = mastByCoax();
        if (base == null) return null;
        // the coax may touch the mast anywhere: count from its foot
        while (isMast(level.getBlockState(base.below()))) base = base.below();
        BlockPos top = base;
        int mast = 1;
        while (mast < 512) {
            BlockState s = level.getBlockState(top.above());
            if (!(s.getBlock() instanceof AntennaRodBlock) || s.getValue(AntennaRodBlock.AXIS) != Direction.Axis.Y) break;
            top = top.above();
            mast++;
        }
        // umbrella: top-load wires connected to the mast top (diagonals allowed)
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(top);
        while (!queue.isEmpty() && seen.size() < MAX_WIRE) {
            BlockPos c = queue.poll();
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = c.offset(dx, dy, dz);
                        if (!seen.contains(n) && level.getBlockState(n).getBlock() instanceof TopLoadWireBlock) {
                            seen.add(n);
                            queue.add(n);
                        }
                    }
        }
        int wire = seen.size();
        double span = 0;
        for (BlockPos w : seen) span = Math.max(span, Math.max(Math.abs(w.getX() - top.getX()), Math.abs(w.getZ() - top.getZ())));
        int mats = 0;
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-24, -4, -24), base.offset(24, 4, 24))) {
            if (level.getBlockState(p).getBlock() instanceof GroundMatBlock) mats++;
        }
        double mastM = mast * MAST_SCALE;
        double c = 6e-12 * mastM + 7e-12 * wire;
        double l = inductanceMH / 1000;
        double f0 = 1 / (2 * Math.PI * Math.sqrt(l * c)) / 1e6;
        double lambda = 300 / f0;
        double hEff = mastM * (0.5 + 0.5 * Math.min(1, wire / (3.0 * mastM)));
        double rRad = 1579 * Math.pow(hEff / lambda, 2);
        double x = 1 / (2 * Math.PI * f0 * 1e6 * c);
        double rGround = 25 / (1 + mats / 10.0) + 0.2;
        double rLoss = x / coilQ() + rGround + 0.1;
        double eta = rRad / (rRad + rLoss);
        double q = Math.max(2, x / (2 * (rRad + rLoss)));
        return new Analysis(base, mastM, wire, c, f0, q, -10 * Math.log10(Math.max(eta, 1e-9)), mastM * 0.6,
                mats, rRad, rLoss, span);
    }

    private static boolean isMast(BlockState s) {
        return s.getBlock() instanceof AntennaRodBlock && s.getValue(AntennaRodBlock.AXIS) == Direction.Axis.Y;
    }

    /**
     * The coil feeds the mast only through coax: follow every coax run leaving the coil and return
     * the first vertical antenna rod one touches (nearest first). A mast standing right next to the
     * coil without coax is not connected.
     */
    private BlockPos mastByCoax() {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            BlockPos p = worldPosition.relative(d);
            if (level.getBlockState(p).getBlock() instanceof CoaxBlock && seen.add(p)) queue.add(p);
        }
        while (!queue.isEmpty() && seen.size() <= 4096) {
            BlockPos c = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos p = c.relative(d);
                if (!p.equals(worldPosition) && isMast(level.getBlockState(p))) return p;
            }
            for (BlockPos n : CoaxBlock.links(level, c)) if (seen.add(n)) queue.add(n);
        }
        return null;
    }

    /** Fraction of the power accepted by the antenna that ends up heating the coil. */
    public double coilLossShare() {
        Analysis a = analyse();
        if (a == null) return 0;
        double x = 1 / (2 * Math.PI * a.f0MHz() * 1e6 * a.capF());
        return (x / coilQ()) / (a.rRad() + a.rLoss());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        tip.add(line("pgtelegraph.coil.inductance", String.format(Locale.ROOT, "%.4g mH", inductanceMH), ChatFormatting.AQUA));
        if (shownF0 <= 0) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.coil.no_mast").withStyle(ChatFormatting.RED)));
            return true;
        }
        tip.add(line("pgtelegraph.coil.resonance", String.format(Locale.ROOT, "%.3f kHz", shownF0 * 1000), ChatFormatting.GOLD));
        tip.add(line("pgtelegraph.coil.mast", String.format(Locale.ROOT, "%.0f m / %.0f m", shownMast, shownWire), ChatFormatting.GRAY));
        tip.add(line("pgtelegraph.coil.bandwidth", String.format(Locale.ROOT, "%.0f Hz (Q %.0f)", shownF0 * 1e6 / shownQ, shownQ), ChatFormatting.GRAY));
        tip.add(line("pgtelegraph.coil.ground", String.valueOf(shownMats), ChatFormatting.GRAY));
        tip.add(line("pgtelegraph.coil.efficiency", String.format(Locale.ROOT, "%.2f %%", shownEff),
                shownEff > 10 ? ChatFormatting.GREEN : shownEff > 1 ? ChatFormatting.YELLOW : ChatFormatting.RED));
        return true;
    }

    private static Component line(String key, String v, ChatFormatting c) {
        return Component.literal("     ").append(Component.translatable(key)).append(": ")
                .append(Component.literal(v).withStyle(c));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("L", inductanceMH);
        if (clientPacket) {
            tag.putDouble("F0", shownF0);
            tag.putDouble("Eff", shownEff);
            tag.putDouble("Q", shownQ);
            tag.putDouble("Mast", shownMast);
            tag.putDouble("Wire", shownWire);
            tag.putInt("Mats", shownMats);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("L")) inductanceMH = tag.getDouble("L");
        if (clientPacket) {
            shownF0 = tag.getDouble("F0");
            shownEff = tag.getDouble("Eff");
            shownQ = tag.getDouble("Q");
            shownMast = tag.getDouble("Mast");
            shownWire = tag.getDouble("Wire");
            shownMats = tag.getInt("Mats");
        }
    }
}
