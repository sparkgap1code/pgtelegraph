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

import java.util.List;
import java.util.Locale;

/**
 * Two-channel oscilloscope: CH1+/CH1- and CH2+/CH2- each through a 1 MΩ input, one sample per
 * game tick, the last {@value #SAMPLES} samples (10 s) kept. The screen shows both traces; inputs
 * are rated 1 kV.
 */
public class OscilloscopeBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final int SAMPLES = 200;
    public static final float INPUT_OHMS = 1_000_000f;
    public static final Spec SPEC = new Spec(70f, 2f, 1f, 1000f, new Spec.Part("input", INPUT_OHMS, 0, 2));

    private SwitchedWire ch1, ch2;
    /** ring buffers; on the client, filled from the last sync */
    public final float[] trace1 = new float[SAMPLES], trace2 = new float[SAMPLES];
    public int head;
    private int sync;

    public OscilloscopeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(4);
        ch1 = builder.connectSwitch(INPUT_OHMS, builder.terminalNode(0), builder.terminalNode(1), !isBurnt());
        ch2 = builder.connectSwitch(INPUT_OHMS, builder.terminalNode(2), builder.terminalNode(3), !isBurnt());
    }

    @Override
    protected void guards(Guard g) {
        g.check(ch1, SPEC.maxVolts(), 0);
        g.check(ch2, SPEC.maxVolts(), 0);
    }

    @Override
    protected void openAll() {
        if (ch1 != null && ch1.getState()) ch1.setState(false);
        if (ch2 != null && ch2.getState()) ch2.setState(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        trace1[head] = isBurnt() || ch1 == null ? 0 : (float) ch1.potentialDifference();
        trace2[head] = isBurnt() || ch2 == null ? 0 : (float) ch2.potentialDifference();
        head = (head + 1) % SAMPLES;
        if (++sync >= 4) {
            sync = 0;
            for (Player p : level.players()) {
                if (p.distanceToSqr(worldPosition.getCenter()) < 24 * 24) {
                    sendData();
                    break;
                }
            }
        }
    }

    /** sample i ticks ago (0 = newest) */
    public float sample(float[] t, int ago) {
        return t[Math.floorMod(head - 1 - ago, SAMPLES)];
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        tip.add(Component.literal(String.format(Locale.ROOT, "     CH1 %.2f V", sample(trace1, 0))).withStyle(ChatFormatting.YELLOW));
        tip.add(Component.literal(String.format(Locale.ROOT, "     CH2 %.2f V", sample(trace2, 0))).withStyle(ChatFormatting.AQUA));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putInt("Head", head);
            tag.putByteArray("T1", pack(trace1));
            tag.putByteArray("T2", pack(trace2));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket && tag.contains("T1")) {
            head = tag.getInt("Head");
            unpack(tag.getByteArray("T1"), trace1);
            unpack(tag.getByteArray("T2"), trace2);
        }
    }

    private static byte[] pack(float[] f) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(f.length * 4);
        for (float v : f) b.putFloat(v);
        return b.array();
    }

    private static void unpack(byte[] a, float[] f) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(a);
        for (int i = 0; i < f.length && b.remaining() >= 4; i++) f[i] = b.getFloat();
    }
}
