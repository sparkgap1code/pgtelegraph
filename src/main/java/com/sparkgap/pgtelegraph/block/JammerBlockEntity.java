package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Jammer (전파 방해기): a transmitter that sends noise - its power spread evenly over a chosen
 * bandwidth (10 kHz - 50 MHz) round the dial. Receivers in that band do not hear it as a station:
 * their noise floor rises by the jammer's power per hertz, so weak signals drown first. The same
 * power over a narrow band jams harder (+10 dB for a tenth of the width). Runs off 250 V, 40 %
 * efficient, up to 1 kW (more with a linear amplifier); INHIBIT shorted to COM switches it off.
 * Anything with ears - radios, TV sets, SSTV monitors, satellites listening for commands - is hit.
 */
public class JammerBlockEntity extends TransmitterBlockEntity {
    public static final int[] WIDTHS_KHZ = {10, 25, 50, 100, 250, 500, 1000, 2000, 5000, 10000, 20000, 50000};
    private ScrollValueBehaviour width;

    public JammerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        freqHz = 145_000_000L;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        width = new StepScroll.Plain(Component.translatable("pgtelegraph.jammer.width"), this,
                ValueBoxes.front(8, 6.5, 3, 0.3f), WIDTHS_KHZ).stepFormat(JammerBlockEntity::fmtKhz)
                .between(WIDTHS_KHZ[0], WIDTHS_KHZ[WIDTHS_KHZ.length - 1])
                .withFormatter(JammerBlockEntity::fmtKhz);
        width.value = 1000;
        behaviours.add(width);
    }

    static String fmtKhz(int k) {
        return k >= 1000 ? (k % 1000 == 0 ? (k / 1000) + " MHz" : String.format(Locale.ROOT, "%.1f MHz", k / 1000.0)) : k + " kHz";
    }

    public static final Spec JAM_SPEC = com.sparkgap.pgtelegraph.broadcast.BroadcastTransmitterBlockEntity.BC_SPEC;

    @Override
    public Spec spec() {
        return JAM_SPEC;
    }

    @Override
    protected double spreadHz() {
        return (width == null ? 1000 : width.getValue()) * 1000.0;
    }

    @Override
    protected int maxPanelW() {
        return 1000;
    }

    @Override
    protected double paMaxW() {
        return 1000;
    }

    @Override
    protected double paEff() {
        return 0.4;
    }

    @Override
    protected double nominalV() {
        return 250;
    }

    @Override
    protected double minV() {
        return 120;
    }

    @Override
    protected double[] harmonics() {
        return null;
    }

    @Override
    protected boolean bandOk() {
        return freqHz >= RadioBlockEntity.MIN_HZ && freqHz <= RadioBlockEntity.MAX_HZ;
    }

    @Override
    public boolean acceptsVoice() {
        return false;
    }

    @Override
    public int airModulation() {
        return RadioWorld.MOD_NOISE;
    }

    /** always on, unless INHIBIT is shorted to COM */
    @Override
    protected boolean keyInput() {
        return !keyContact();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        super.addToGoggleTooltip(tip, sneaking);
        double lo = (freqHz - spreadHz() / 2) / 1e6, hi = (freqHz + spreadHz() / 2) / 1e6;
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.jammer.band",
                String.format(Locale.ROOT, "%.3f", lo), String.format(Locale.ROOT, "%.3f", hi))).withStyle(ChatFormatting.RED));
        return true;
    }
}
