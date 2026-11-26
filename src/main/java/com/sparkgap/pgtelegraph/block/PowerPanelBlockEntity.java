package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
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
 * Power distribution panel (전원 분배 패널, like a RigRunner): one 13.8 V input feeds four outputs,
 * each through its own blade fuse. A fuse carrying more than its rating for half a second blows and
 * opens that output only. Empty hand: replace blown fuses; sneak + empty hand: next fuse size
 * (5 / 10 / 20 / 30 A, all four outputs). The bus itself is rated 40 A and 30 V.
 */
public class PowerPanelBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation, PowerLamp {
    public static final int OUTPUTS = 4;
    public static final int[] FUSES = {5, 10, 20, 30};
    public static final Spec SPEC = new Spec(60f, 40f, 3f, 30f, Spec.rail(13.8f));
    private static final float BUS_OHMS = 0.004f;

    private final SwitchedWire[] outs = new SwitchedWire[OUTPUTS];
    /** 100 kOhm across the input: the panel's voltmeter */
    private SwitchedWire sense;
    private final boolean[] blown = new boolean[OUTPUTS];
    private final int[] over = new int[OUTPUTS];
    private int fuse = 2;
    // synced
    private final float[] shownA = new float[OUTPUTS];
    private float shownV;

    public PowerPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    public int fuseA() {
        return FUSES[fuse];
    }

    /** terminals: 0 IN+, 1 GND, 2-5 OUT1-OUT4 */
    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2 + OUTPUTS);
        sense = builder.connectSwitch(100_000f, builder.terminalNode(0), builder.terminalNode(1), true);
        for (int i = 0; i < OUTPUTS; i++)
            outs[i] = builder.connectSwitch(BUS_OHMS, builder.terminalNode(0), builder.terminalNode(2 + i), !blown[i] && !isBurnt());
    }

    @Override
    protected void guards(Guard g) {
        for (SwitchedWire w : outs) g.check(w, 0, 40);
        g.check(sense, 30, 0);
    }

    @Override
    protected void openAll() {
        for (SwitchedWire w : outs) if (w != null && w.getState()) w.setState(false);
        if (sense != null && sense.getState()) sense.setState(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isBurnt()) return;
        boolean dirty = level.getGameTime() % 20 == 0;
        for (int i = 0; i < OUTPUTS; i++) {
            SwitchedWire w = outs[i];
            if (w == null) continue;
            double a = Math.abs(w.current());
            if (!blown[i] && a > FUSES[fuse]) {
                if (++over[i] >= (a > FUSES[fuse] * 3 ? 2 : 10)) {
                    blown[i] = true;
                    over[i] = 0;
                    w.setState(false);
                    level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 2f);
                    dirty = true;
                }
            } else over[i] = Math.max(0, over[i] - 1);
            if (Math.abs(a - shownA[i]) > 0.1) dirty = true;
            shownA[i] = blown[i] ? 0 : (float) a;
        }
        float v = 0;
        v = (float) busVolts();
        if (Math.abs(v - shownV) > 0.1) dirty = true;
        shownV = v;
        if (dirty) sendData();
    }

    private double busVolts() {
        return sense == null ? 0 : Math.abs(sense.potentialDifference());
    }

    /** empty hand: new fuses for the blown outputs */
    public boolean replaceFuses() {
        boolean any = false;
        for (int i = 0; i < OUTPUTS; i++) {
            if (blown[i]) {
                blown[i] = false;
                any = true;
                if (outs[i] != null && !isBurnt()) outs[i].setState(true);
            }
        }
        if (any) {
            setChanged();
            sendData();
        }
        return any;
    }

    public void nextFuse() {
        fuse = (fuse + 1) % FUSES.length;
        setChanged();
        sendData();
    }

    @Override
    public int lamp() {
        if (isBurnt()) return FAULT;
        for (boolean b : blown) if (b) return BLINK;
        return shownV > 9 ? ON : OFF;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        tag.putInt("Fuse", fuse);
        int b = 0;
        for (int i = 0; i < OUTPUTS; i++) if (blown[i]) b |= 1 << i;
        tag.putInt("Blown", b);
        if (clientPacket) {
            for (int i = 0; i < OUTPUTS; i++) tag.putFloat("A" + i, shownA[i]);
            tag.putFloat("V", shownV);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        fuse = Math.floorMod(tag.getInt("Fuse"), FUSES.length);
        if (!tag.contains("Fuse")) fuse = 2;
        int b = tag.getInt("Blown");
        for (int i = 0; i < OUTPUTS; i++) blown[i] = (b & 1 << i) != 0;
        if (clientPacket) {
            for (int i = 0; i < OUTPUTS; i++) shownA[i] = tag.getFloat("A" + i);
            shownV = tag.getFloat("V");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        if (isBurnt()) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.panel.burnt")).withStyle(ChatFormatting.RED));
            return true;
        }
        tip.add(Component.literal(String.format(Locale.ROOT, "     IN %.1f V", shownV)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.panel.fuse", FUSES[fuse])).withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < OUTPUTS; i++) {
            tip.add(Component.literal("     OUT" + (i + 1) + "  ").append(blown[i]
                    ? Component.translatable("pgtelegraph.panel.blown").withStyle(ChatFormatting.RED)
                    : Component.literal(String.format(Locale.ROOT, "%.1f A", shownA[i])).withStyle(ChatFormatting.GREEN)));
        }
        return true;
    }
}
