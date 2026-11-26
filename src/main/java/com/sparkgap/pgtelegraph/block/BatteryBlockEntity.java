package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.FloatingNode;
import org.patryk3211.powergrid.electricity.sim.node.VoltageSourceCoupling;

import java.util.List;
import java.util.Locale;

/**
 * Battery pack: a voltage source following the open-circuit voltage of its state of charge, behind
 * the pack's internal resistance. No protection of its own - put a BMS in front of lithium packs.
 * <ul>
 *   <li>Li-ion overcharged past 105 %: thermal runaway, fire and explosion after 3 s</li>
 *   <li>LiFePO4 overcharged: vents and dies, no fire</li>
 *   <li>lead-acid overcharged: gasses, loses water (capacity); left flat it sulphates</li>
 *   <li>lithium run below 0 %: copper dissolves, 20 % of the capacity is gone for good</li>
 *   <li>over-current (above the rated amps for 1 s) cooks the pack; Li-ion then explodes</li>
 * </ul>
 */
public class BatteryBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public enum Chem {
        /** 4S Li-ion, 20 Ah: 12.0-16.8 V */
        LI_ION(4, 20, 0.04, 40, 0.99, new double[]{0, 3.0, 0.1, 3.5, 0.2, 3.6, 0.8, 3.95, 0.95, 4.1, 1, 4.2}),
        /** 4S LiFePO4, 50 Ah: 10.0-14.6 V, flat plateau at 13.3 V */
        LIFEPO4(4, 50, 0.02, 100, 0.99, new double[]{0, 2.5, 0.05, 3.0, 0.1, 3.2, 0.9, 3.33, 0.99, 3.4, 1, 3.6}),
        /** 6-cell lead-acid, 100 Ah: 11.7-12.7 V at rest */
        LEAD_ACID(6, 100, 0.03, 200, 0.85, new double[]{0, 1.95, 0.5, 2.03, 1, 2.12});

        public final int cells;
        public final double ah, ohms, maxA, chargeEff;
        final double[] curve;

        Chem(int cells, double ah, double ohms, double maxA, double chargeEff, double[] curve) {
            this.cells = cells;
            this.ah = ah;
            this.ohms = ohms;
            this.maxA = maxA;
            this.chargeEff = chargeEff;
            this.curve = curve;
        }

        /** open-circuit voltage of the pack; above 100 % it climbs steeply (overcharge) */
        public double ocv(double soc) {
            double cell;
            if (soc >= 1) cell = curve[curve.length - 1] + (soc - 1) * (this == LEAD_ACID ? 6 : 10);
            else if (soc <= 0) cell = curve[1] - 0.3;
            else {
                cell = curve[1];
                for (int i = 2; i < curve.length; i += 2) {
                    if (soc <= curve[i]) {
                        double t = (soc - curve[i - 2]) / (curve[i] - curve[i - 2]);
                        cell = curve[i - 1] + t * (curve[i + 1] - curve[i - 1]);
                        break;
                    }
                }
            }
            return cell * cells;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private SwitchedWire out;
    private VoltageSourceCoupling cell;
    private double soc = 0.5, health = 1;
    private int runaway = -1, overcharged;
    // synced
    private float shownV, shownA;

    public BatteryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Chem chem() {
        return getBlockState().getBlock() instanceof BatteryBlock b ? b.chem : Chem.LEAD_ACID;
    }

    @Override
    public Spec spec() {
        Chem c = chem();
        return new Spec(c == Chem.LEAD_ACID ? 60f : 70f, (float) (c.maxA * c.maxA * c.ohms), 30f, 60f,
                new Spec.Part("cell", (float) c.ohms, (float) c.maxA, c.cells));
    }

    public void setState(double soc, double health) {
        this.soc = soc;
        this.health = health;
        if (cell != null) cell.setVoltage(chem().ocv(soc));
        setChanged();
    }

    public double soc() {
        return soc;
    }

    public double health() {
        return health;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        Chem c = chem();
        FloatingNode mid = builder.addInternalNode();
        cell = new VoltageSourceCoupling(mid, builder.terminalNode(1), (float) c.ohms, (float) c.ocv(soc));
        builder.add(cell);
        out = builder.connectSwitch(0.001f, mid, builder.terminalNode(0), !isBurnt());
    }

    @Override
    protected void guards(Guard g) {
        g.check(out, 0, (float) chem().maxA);
    }

    @Override
    protected void openAll() {
        if (out != null && out.getState()) out.setState(false);
    }

    @Override
    protected void burn() {
        boolean was = isBurnt();
        super.burn();
        if (!was && chem() == Chem.LI_ION && runaway < 0) runaway = 60;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        Chem c = chem();
        if (runaway >= 0) {
            thermalRunaway();
            return;
        }
        if (isBurnt() || out == null || cell == null) return;
        double amps = out.current(); // + discharging, - charging
        double cap = c.ah * health;
        double dAh = amps / 20 / 3600;
        if (dAh < 0) dAh *= c.chargeEff;
        double before = soc;
        soc -= dAh / cap;
        if (thermalBehaviour != null) thermalBehaviour.applyTickPower(amps * amps * c.ohms);

        // ---- abuse
        if (soc > 1 && amps < -0.05) {
            switch (c) {
                case LEAD_ACID -> {
                    soc = Math.min(soc, 1.02); // the excess splits water instead
                    health = Math.max(0.2, health + dAh / (c.ah * 50));
                    bubbles();
                }
                case LIFEPO4 -> {
                    if (soc > 1.05 && ++overcharged > 40) {
                        vent();
                        burn();
                    }
                }
                case LI_ION -> {
                    if (soc > 1.05 && ++overcharged > 60) {
                        level.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1f, 1f);
                        runaway = 60;
                    }
                }
            }
        } else overcharged = Math.max(0, overcharged - 1);
        if (soc < 0) {
            if (before >= 0 && c != Chem.LEAD_ACID) health = Math.max(0.2, health - 0.2);
            soc = 0;
        }
        if (c == Chem.LEAD_ACID && soc < 0.2 && level.getGameTime() % 200 == 0) health = Math.max(0.2, health - 0.001);
        cell.setVoltage(c.ocv(soc));

        float v = (float) Math.max(0, c.ocv(soc) - amps * c.ohms);
        if (Math.abs(v - shownV) > 0.05f || Math.abs(amps - shownA) > 0.1f || level.getGameTime() % 40 == 0) {
            shownV = v;
            shownA = (float) amps;
            setChanged();
            sendData();
        }
    }

    private void bubbles() {
        if (level instanceof ServerLevel sl && sl.random.nextInt(4) == 0) {
            sl.sendParticles(ParticleTypes.BUBBLE_POP, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9,
                    worldPosition.getZ() + 0.5, 2, 0.2, 0.02, 0.2, 0.01);
        }
    }

    private void vent() {
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.6f);
        if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, 20, 0.2, 0.3, 0.2, 0.03);
    }

    private void thermalRunaway() {
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9,
                    worldPosition.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.05);
            if (runaway % 10 == 0) sl.sendParticles(ParticleTypes.FLAME, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9,
                    worldPosition.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0.02);
        }
        if (runaway-- > 0) return;
        Level l = level;
        BlockPos p = worldPosition;
        if (l instanceof ServerLevel sl) com.sparkgap.pgtelegraph.Awards.near(sl, p, 24, "boom");
        l.removeBlock(p, false);
        l.explode(null, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2.5f, true, Level.ExplosionInteraction.BLOCK);
        if (l.getBlockState(p).isAir()) l.setBlockAndUpdate(p, Blocks.FIRE.defaultBlockState());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal(String.format(Locale.ROOT, "     %.2f V  %+.1f A", shownV, shownA)).withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal(String.format(Locale.ROOT, "     SOC %.0f %%  (%.0f Ah)", soc * 100, chem().ah * health))
                .withStyle(soc < 0.1 || soc > 1 ? ChatFormatting.RED : ChatFormatting.GREEN));
        if (health < 0.999) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.battery.health"))
                .append(String.format(Locale.ROOT, ": %.0f %%", health * 100)).withStyle(ChatFormatting.YELLOW));
        if (isBurnt()) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.burnt")
                .withStyle(ChatFormatting.DARK_RED)));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("Soc", soc);
        tag.putDouble("Health", health);
        if (clientPacket) {
            tag.putFloat("V", shownV);
            tag.putFloat("A", shownA);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Soc")) soc = tag.getDouble("Soc");
        if (tag.contains("Health")) health = tag.getDouble("Health");
        if (clientPacket) {
            shownV = tag.getFloat("V");
            shownA = tag.getFloat("A");
        }
    }
}
