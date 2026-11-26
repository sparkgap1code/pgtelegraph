package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.sparkgap.pgtelegraph.radio.Dtmf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * DTMF decoder (DTMF 디코더): listens to the audio of a receiver right next to it (AM / FM / SSB)
 * and turns touch-tone codes into a redstone signal. Runs off 13.8 V (2 W).
 * <ul>
 *   <li>PULSE: the ON code gives a 1 s pulse</li>
 *   <li>LATCH: the ON code switches on, the OFF code off (no OFF code: the ON code toggles)</li>
 *   <li>DIGIT: the output is the last digit (1-9, 0 = 10, * = 11, # = 12, A-D = 13-15), held</li>
 * </ul>
 * Digits more than 5 s apart start a new code.
 */
public class DtmfDecoderBlockEntity extends PoweredStationBlockEntity implements IHaveGoggleInformation, Tunable {
    /** its own FM receiver: dial, antenna on its coax */
    private long freqHz = 145_500_000L;
    private com.sparkgap.pgtelegraph.radio.AntennaLink link;
    private int rescan;
    private final com.sparkgap.pgtelegraph.radio.Reception.TerrainCache cache = new com.sparkgap.pgtelegraph.radio.Reception.TerrainCache();

    @Override
    public long freqHz() {
        return freqHz;
    }

    @Override
    public void setFreq(long hz) {
        freqHz = Math.max(RadioBlockEntity.MIN_HZ, Math.min(RadioBlockEntity.MAX_HZ, hz));
        setChanged();
        sendData();
    }

    public boolean hasAntenna() {
        return link != null && link.amplifier() == null;
    }

    public static final int PULSE = 0, LATCH = 1, DIGIT = 2, MODES = 3;
    public static final int MAX_CODE = 8, MIN_SNR = 10, PULSE_TICKS = 20, GAP_TICKS = 100;
    public static final Spec SPEC = spec(13.8, 2);

    private String onCode = "123", offCode = "";
    private int mode = PULSE;
    /** digits heard since the last gap */
    private String buffer = "";
    /** digits not yet read by a computer */
    private final StringBuilder unread = new StringBuilder();
    private long lastSeq = -1, lastDigitTick;
    private int output, pulse;
    private boolean latch;

    public DtmfDecoderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public double nominalV() {
        return 13.8;
    }

    @Override
    public double ratedW() {
        return 2;
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    public int output() {
        return isPowered() ? output : 0;
    }

    public String onCode() {
        return onCode;
    }

    public String offCode() {
        return offCode;
    }

    public int mode() {
        return mode;
    }

    public static String clean(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toUpperCase(java.util.Locale.ROOT).toCharArray()) if (Dtmf.valid(c) && b.length() < MAX_CODE) b.append(c);
        return b.toString();
    }

    public void configure(String on, String off, int mode, long hz) {
        if (hz > 0) freqHz = Math.max(RadioBlockEntity.MIN_HZ, Math.min(RadioBlockEntity.MAX_HZ, hz));
        configure(on, off, mode);
    }

    public void configure(String on, String off, int mode) {
        onCode = clean(on);
        offCode = clean(off);
        this.mode = Math.floorMod(mode, MODES);
        latch = false;
        pulse = 0;
        setOutput(0);
        setChanged();
        sendData();
    }

    /** computer: digits heard since the last read */
    public String takeUnread() {
        String s = unread.toString();
        unread.setLength(0);
        return s;
    }

    public String buffer() {
        return buffer;
    }

    @Override
    protected void lostPower() {
        pulse = 0;
        setOutput(0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (!powerTick()) return;
        long now = level.getGameTime();
        if (pulse > 0 && --pulse == 0 && mode == PULSE) setOutput(0);
        if (!buffer.isEmpty() && now - lastDigitTick > GAP_TICKS) {
            buffer = "";
            sendData();
        }
        // a receiver right next to it feeds its audio; otherwise its own FM receiver on its coax
        boolean fed = false;
        for (Direction d : Direction.values()) {
            if (!(level.getBlockEntity(worldPosition.relative(d)) instanceof ReceiverBlockEntity rx)) continue;
            fed = true;
            if (rx.heardSource == null || rx.heardTick < now - 1 || rx.heardSnr < MIN_SNR) continue;
            hear(rx.heardSource, now);
            break;
        }
        if (fed) return;
        if (--rescan <= 0) {
            rescan = 40;
            link = com.sparkgap.pgtelegraph.radio.AntennaLink.scan(level, worldPosition);
        }
        if (!hasAntenna()) return;
        var r = com.sparkgap.pgtelegraph.radio.Reception.listen(level, link, freqHz, 0, null, worldPosition.asLong(), cache, -130, 12_500);
        if (r.hasSignal() && r.bestEmission() != null && r.best() - r.noiseDbm() >= MIN_SNR) hear(r.bestEmission().source(), now);
    }

    private void hear(net.minecraft.core.BlockPos source, long now) {
        long[] t = Dtmf.heard(level, source);
        if (t != null && t[1] != lastSeq) {
            lastSeq = t[1];
            digit((char) t[0], now);
        }
    }

    private void digit(char c, long now) {
        lastDigitTick = now;
        buffer = (buffer + c);
        if (buffer.length() > 16) buffer = buffer.substring(buffer.length() - 16);
        if (unread.length() < 256) unread.append(c);
        switch (mode) {
            case PULSE -> {
                if (matches(onCode)) {
                    setOutput(15);
                    pulse = PULSE_TICKS;
                    buffer = "";
                }
            }
            case LATCH -> {
                if (matches(onCode)) {
                    latch = offCode.isEmpty() ? !latch : true;
                    buffer = "";
                } else if (matches(offCode)) {
                    latch = false;
                    buffer = "";
                }
                setOutput(latch ? 15 : 0);
            }
            default -> setOutput(digitLevel(c));
        }
        setChanged();
        sendData();
    }

    private boolean matches(String code) {
        return !code.isEmpty() && buffer.endsWith(code);
    }

    public static int digitLevel(char c) {
        return switch (c) {
            case '0' -> 10;
            case '*' -> 11;
            case '#' -> 12;
            case 'A' -> 13;
            case 'B' -> 14;
            case 'C', 'D' -> 15;
            default -> c - '0';
        };
    }

    private void setOutput(int v) {
        if (v == output) return;
        output = v;
        if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        setChanged();
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        tag.putString("OnCode", onCode);
        tag.putLong("Freq", freqHz);
        tag.putString("OffCode", offCode);
        tag.putInt("Mode", mode);
        tag.putInt("Out", output);
        tag.putBoolean("Latch", latch);
        if (clientPacket) tag.putString("Buf", buffer);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        if (tag.contains("OnCode")) onCode = clean(tag.getString("OnCode"));
        if (tag.contains("Freq")) freqHz = tag.getLong("Freq");
        offCode = clean(tag.getString("OffCode"));
        mode = Math.floorMod(tag.getInt("Mode"), MODES);
        output = tag.getInt("Out");
        latch = tag.getBoolean("Latch");
        if (clientPacket) buffer = tag.getString("Buf");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        if (isBurnt()) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.dtmf.burnt")).withStyle(ChatFormatting.RED));
            return true;
        }
        if (!isPowered()) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.dtmf.no_power")).withStyle(ChatFormatting.RED));
            return true;
        }
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.dtmf.mode." + mode)).withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("     " + RadioBlockEntity.formatFreq(freqHz)).withStyle(ChatFormatting.AQUA));
        if (mode != DIGIT) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.dtmf.on_code", onCode.isEmpty() ? "-" : onCode))
                    .withStyle(ChatFormatting.GRAY));
            if (mode == LATCH)
                tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.dtmf.off_code", offCode.isEmpty() ? "-" : offCode))
                        .withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.dtmf.heard", buffer.isEmpty() ? "-" : buffer))
                .withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.dtmf.output", output))
                .withStyle(output > 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        return true;
    }
}
