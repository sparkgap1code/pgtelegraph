package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.Reception;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Repeater: listens on the dial frequency and re-sends what it hears, mark for mark, on the dial
 * plus the band's standard offset (HF +100 kHz, VHF +600 kHz, UHF +5 MHz, above 1 GHz +12 MHz).
 * One antenna does both through the built-in duplexer. With a callsign set it sends "DE call/R"
 * every 10 minutes while idle. KEY shorted to KEY COM switches it off.
 */
public class RepeaterBlockEntity extends TransmitterBlockEntity {
    public static final int SQUELCH_DB = 8, ID_TICKS = 12000;

    private final Reception.TerrainCache terrain = new Reception.TerrainCache();
    private String call = "";
    private int idTimer = ID_TICKS, idle;

    public RepeaterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static long offsetHz(long hz) {
        if (hz < 30_000_000L) return 100_000L;
        if (hz < 300_000_000L) return 600_000L;
        if (hz < 1_000_000_000L) return 5_000_000L;
        return 12_000_000L;
    }

    @Override
    protected int maxPanelW() {
        return 50;
    }

    @Override
    protected long emitHz() {
        return freqHz + offsetHz(freqHz) + driftHz();
    }

    @Override
    protected boolean keyInput() {
        if (keyContact()) return false;
        boolean heard = false;
        if (link != null && link.amplifier() == null) {
            var r = Reception.listen(level, link, freqHz + driftHz(), 0, worldPosition, worldPosition.asLong() * 7, terrain,
                    -137, RadioMath.FILTER_HZ);
            heard = r.hasSignal() && r.best() - r.noiseDbm() >= SQUELCH_DB
                    && com.sparkgap.pgtelegraph.radio.Ctcss.passes(level, ctcss, r.bestEmission());
        }
        if (heard) {
            idle = 0;
            return true;
        }
        if (computerText.busy()) return computerText.tick();
        idle++;
        if (!call.isEmpty() && --idTimer <= 0 && idle > 60) {
            idTimer = ID_TICKS;
            computerText.send("DE " + call + "/R", 20);
        }
        return false;
    }

    public void setCall(String c) {
        call = c == null ? "" : c;
        setChanged();
        sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        super.addToGoggleTooltip(tip, sneaking);
        tip.add(Component.literal("     RX " + formatFreq(freqHz) + "  TX " + formatFreq(freqHz + offsetHz(freqHz)))
                .withStyle(ChatFormatting.GRAY));
        if (ctcss > 0) tip.add(Component.literal("     CTCSS " + com.sparkgap.pgtelegraph.radio.Ctcss.label(ctcss)).withStyle(ChatFormatting.GRAY));
        if (!call.isEmpty()) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.license.call"))
                .append(": " + call).withStyle(ChatFormatting.GRAY));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("Call", call);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        call = tag.getString("Call");
    }
}
