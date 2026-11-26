package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;

/**
 * Operating-position selector (운용석 선택기): one key, one microphone, up to four transmitters. The
 * key goes on KEY IN / COM, each transmitter's KEY on OUT1-OUT4 and every transmitter's KEY COM on
 * COM. A real contact joins KEY IN to the selected output only, so the key works the chosen set.
 * A microphone touching the selector talks into the chosen transmitter (found by following the
 * wire from that output). Empty hand: next position, sneak: previous, 0 = off; a redstone signal of
 * 1-4 picks that position and overrides the knob.
 */
public class SelectorBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final int OUTS = 4, KEY_IN = 0, COM = 1, OUT0 = 2;
    public static final Spec SPEC = new Spec(80f, 5f, 1f, 250f, Spec.contact(0.05f, 2f, OUTS));

    private final SwitchedWire[] contacts = new SwitchedWire[OUTS];
    private int applied = -1;

    public SelectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    public int channel() {
        BlockState s = getBlockState();
        return s.hasProperty(SelectorBlock.CHANNEL) ? s.getValue(SelectorBlock.CHANNEL) : 0;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(OUT0 + OUTS);
        for (int i = 0; i < OUTS; i++)
            contacts[i] = builder.connectSwitch(0.05f, builder.terminalNode(KEY_IN), builder.terminalNode(OUT0 + i), false);
        applied = -1;
    }

    @Override
    protected void guards(Guard g) {
        for (SwitchedWire w : contacts) g.check(w, SPEC.maxVolts(), 2f);
    }

    @Override
    protected void openAll() {
        for (SwitchedWire w : contacts) if (w != null && w.getState()) w.setState(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isBurnt() || contacts[0] == null) return;
        int ch = channel();
        if (ch != applied) {
            for (int i = 0; i < OUTS; i++) contacts[i].setState(ch == i + 1);
            if (applied >= 0) level.playSound(null, worldPosition, PgTelegraph.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.5f, 0.8f + 0.1f * ch);
            applied = ch;
        }
    }

    /** the device at the far end of output n (1-4), or null */
    public BlockPos target(int n) {
        if (level == null || n < 1 || n > OUTS) return null;
        for (WireTrace.Hit h : WireTrace.trace(level, worldPosition, new int[]{OUT0 + n - 1}, be -> be instanceof RadioBlockEntity))
            if (h.kind() == WireTrace.Kind.DEVICE) return h.pos();
        return null;
    }

    /** the transmitter the microphone should talk into, or null */
    public TransmitterBlockEntity selectedTransmitter() {
        BlockPos p = target(channel());
        return p != null && level.getBlockEntity(p) instanceof TransmitterBlockEntity t ? t : null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(Labels.title(this)));
        int ch = channel();
        tip.add(Component.literal("     ").append(ch == 0 ? Component.translatable("pgtelegraph.selector.off")
                : Component.translatable("pgtelegraph.selector.on", ch)).withStyle(ChatFormatting.GOLD));
        for (int n = 1; n <= OUTS; n++) {
            BlockPos p = target(n);
            Component what;
            if (p == null) what = Component.translatable("pgtelegraph.selector.none").withStyle(ChatFormatting.DARK_GRAY);
            else {
                var be = level.getBlockEntity(p);
                what = be == null ? Component.literal("?") : Labels.title(be).copy();
                if (be instanceof RadioBlockEntity r) what = what.copy().append(Component.literal("  " + RadioBlockEntity.formatFreq(r.freqHz())));
            }
            tip.add(Component.literal((n == ch ? "   ▶ " : "     ") + n + "  ").append(what)
                    .withStyle(n == ch ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        return true;
    }
}
