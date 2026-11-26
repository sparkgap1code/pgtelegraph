package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * The manual tuner's two knobs (inductor L in µH, capacitor C in pF, both in geometric steps) and
 * what they make: the frequency it matches, the antenna's SWR there and whether that is in range.
 */
public class ManualTunerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    static final BehaviourType<NamedScroll> L = new BehaviourType<>("tuner_l"), C = new BehaviourType<>("tuner_c");
    /** knob positions: L = 0.1 µH x 1.33^i, C = 10 pF x 1.2^j  (covers about 0.4 - 160 MHz) */
    static final int L_STEPS = 25, C_STEPS = 30;

    private NamedScroll l, c;
    private int rescan;
    private double shownSwr = -1;
    /** synced: SWR the rig on the other side sees at its own frequency (-1 = no rig), and that frequency */
    private double rigSwr = -1, rigMHz;
    /** client: the meter needle's swing (0 = 1:1, 1 = full scale), damped like a real movement */
    public float needle, needleO;

    public ManualTunerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static double uH(int i) {
        return 0.1 * Math.pow(1.33, i);
    }

    public static double pF(int j) {
        return 10 * Math.pow(1.2, j);
    }

    private static int[] range(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = i;
        return r;
    }

    /** a knob lying on the lid at x pixels (the block has no facing) */
    private static ValueBoxTransform knob(double x) {
        return new ValueBoxTransform.Sided() {
            {
                this.scale = 0.4f;
            }

            @Override
            public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
                return new Vec3(x / 16, 8.05 / 16, 8.0 / 16);
            }

            @Override
            protected Vec3 getSouthLocation() {
                return new Vec3(x / 16, 8.0 / 16, 0.5);
            }

            @Override
            protected boolean isSideActive(BlockState state, Direction direction) {
                return direction == Direction.UP;
            }
        };
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        l = new StepScroll.Named(L, "TunerL", Component.translatable("pgtelegraph.tuner.l"), this, knob(5), range(L_STEPS))
                .stepFormat(i -> String.format(Locale.ROOT, "%.2f µH", uH(i)));
        l.between(0, L_STEPS - 1).withFormatter(i -> String.format(Locale.ROOT, "%.1f", uH(i)));
        l.value = 12;
        c = new StepScroll.Named(C, "TunerC", Component.translatable("pgtelegraph.tuner.c"), this, knob(11), range(C_STEPS))
                .stepFormat(j -> String.format(Locale.ROOT, "%.0f pF", pF(j)));
        c.between(0, C_STEPS - 1).withFormatter(j -> String.format(Locale.ROOT, "%.0f", pF(j)));
        c.value = 12;
        behaviours.add(l);
        behaviours.add(c);
    }

    /** the frequency the knobs resonate at (MHz) */
    public double matchMHz() {
        double lh = uH(l == null ? 12 : l.getValue()) * 1e-6, cf = pF(c == null ? 12 : c.getValue()) * 1e-12;
        return 1 / (2 * Math.PI * Math.sqrt(lh * cf)) / 1e6;
    }

    private void serverTick() {
        if (level == null || level.isClientSide || --rescan > 0) return;
        rescan = 20;
        // the antenna's own SWR at the tuned frequency (the tuner is the "device": the scan looks past it)
        AntennaLink a = AntennaLink.scan(level, worldPosition);
        double swr = a == null ? -1 : RadioMath.swr(a.rawReflection(matchMHz()));
        // the meter: what the rig behind the tuner sees on its own dial, through the tuner as set
        BlockPos rig = findRig();
        double rs = -1, rf = 0;
        if (rig != null && level.getBlockEntity(rig) instanceof Tunable t) {
            rf = t.freqHz() / 1e6;
            AntennaLink seen = AntennaLink.scan(level, rig);
            if (seen != null && rf > 0) rs = RadioMath.swr(seen.reflection(rf));
        }
        if (Math.abs(swr - shownSwr) > 0.05 || Math.abs(rs - rigSwr) > 0.02 || Math.abs(rf - rigMHz) > 1e-4) {
            shownSwr = swr;
            rigSwr = rs;
            rigMHz = rf;
            sendData();
        }
    }

    /** the radio on the rig side: a tunable box at the end of the coax that leaves this tuner (not through the antenna) */
    private BlockPos findRig() {
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        seen.add(worldPosition);
        q.add(worldPosition);
        while (!q.isEmpty() && seen.size() < 256) {
            BlockPos c = q.poll();
            boolean cIsCoax = level.getBlockState(c).getBlock() instanceof CoaxBlock;
            java.util.List<BlockPos> links = cIsCoax ? CoaxBlock.links(level, c) : java.util.List.of();
            for (Direction d : Direction.values()) {
                BlockPos p = c.relative(d);
                if (seen.contains(p)) continue;
                BlockState ps = level.getBlockState(p);
                if (level.getBlockEntity(p) instanceof Tunable && !(level.getBlockEntity(p) instanceof SolarBeaconBlockEntity)) return p;
                boolean go = ps.getBlock() instanceof CoaxBlock ? (!cIsCoax || links.contains(p)) : AntennaLink.passThrough(ps);
                if (go) {
                    seen.add(p);
                    q.add(p);
                }
            }
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide) {
            needleO = needle;
            float target = rigSwr < 1 ? 0 : (float) ((rigSwr - 1) / (rigSwr + 1)); // |Γ|: the scale of a real SWR meter
            needle += (target - needle) * 0.25f;
        }
        serverTick();
    }

    public double rigSwr() {
        return rigSwr;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        if (clientPacket) {
            tag.putDouble("Swr", shownSwr);
            tag.putDouble("RigSwr", rigSwr);
            tag.putDouble("RigMHz", rigMHz);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        if (clientPacket) {
            shownSwr = tag.getDouble("Swr");
            rigSwr = tag.contains("RigSwr") ? tag.getDouble("RigSwr") : -1;
            rigMHz = tag.getDouble("RigMHz");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.tuner.match", RadioMath.formatMHz(matchMHz())))
                .withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal(String.format(Locale.ROOT, "     L %.2f µH  C %.0f pF", uH(l.getValue()), pF(c.getValue())))
                .withStyle(ChatFormatting.GRAY));
        if (shownSwr < 0) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.tuner.no_antenna")).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            boolean ok = shownSwr <= AntennaLink.Tuner.MANUAL.maxSwr();
            tip.add(Component.literal("     ").append(Component.translatable(ok ? "pgtelegraph.tuner.ok" : "pgtelegraph.tuner.out",
                    String.format(Locale.ROOT, "%.1f", shownSwr))).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (rigSwr >= 1) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.tuner.rig",
                    RadioMath.formatMHz(rigMHz), String.format(Locale.ROOT, "%.1f", rigSwr)))
                    .withStyle(rigSwr <= 1.5 ? ChatFormatting.GREEN : rigSwr <= 3 ? ChatFormatting.YELLOW : ChatFormatting.RED));
        }
        return true;
    }
}
