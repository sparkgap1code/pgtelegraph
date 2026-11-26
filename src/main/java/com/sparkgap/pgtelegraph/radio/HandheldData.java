package com.sparkgap.pgtelegraph.radio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * State of a handheld radio item: dial, power step, squelch, on/off, battery charge (Wh), and the
 * keying method: straight key (hold right-click) or paddle with the built-in keyer at {@code wpm}.
 */
public record HandheldData(long freqHz, int power, int squelch, boolean on, float batteryWh, boolean paddle, int wpm) {
    public static final float CAPACITY_WH = 14.8f; // 7.4 V 2 Ah Li-ion pack
    public static final float[] POWER_W = {0.5f, 1f, 5f, 8f};
    public static final int MIN_WPM = 5, MAX_WPM = 40;
    public static final HandheldData DEFAULT = new HandheldData(145_000_000L, 2, 6, false, CAPACITY_WH, false, 15);

    public static final Codec<HandheldData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("freq").forGetter(HandheldData::freqHz),
            Codec.INT.fieldOf("power").forGetter(HandheldData::power),
            Codec.INT.fieldOf("squelch").forGetter(HandheldData::squelch),
            Codec.BOOL.fieldOf("on").forGetter(HandheldData::on),
            Codec.FLOAT.fieldOf("battery").forGetter(HandheldData::batteryWh),
            Codec.BOOL.optionalFieldOf("paddle", false).forGetter(HandheldData::paddle),
            Codec.INT.optionalFieldOf("wpm", 15).forGetter(HandheldData::wpm)
    ).apply(i, HandheldData::new));

    public static final StreamCodec<ByteBuf, HandheldData> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                ByteBufCodecs.VAR_LONG.encode(buf, d.freqHz);
                ByteBufCodecs.VAR_INT.encode(buf, d.power);
                ByteBufCodecs.VAR_INT.encode(buf, d.squelch);
                ByteBufCodecs.BOOL.encode(buf, d.on);
                ByteBufCodecs.FLOAT.encode(buf, d.batteryWh);
                ByteBufCodecs.BOOL.encode(buf, d.paddle);
                ByteBufCodecs.VAR_INT.encode(buf, d.wpm);
            },
            buf -> new HandheldData(ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    /** dual band: 2 m and 70 cm */
    public static long clampFreq(long hz) {
        if (hz < 290_000_000L) return Math.max(144_000_000L, Math.min(148_000_000L, hz));
        return Math.max(430_000_000L, Math.min(440_000_000L, hz));
    }

    public static int clampWpm(int wpm) {
        return Math.max(MIN_WPM, Math.min(MAX_WPM, wpm));
    }

    public HandheldData withBattery(float wh) {
        return new HandheldData(freqHz, power, squelch, on, Math.max(0, Math.min(CAPACITY_WH, wh)), paddle, wpm);
    }

    public HandheldData withSettings(long hz, int pw, int sq, boolean onOff, boolean paddleMode, int speed) {
        return new HandheldData(clampFreq(hz), Math.max(0, Math.min(POWER_W.length - 1, pw)), Math.max(0, Math.min(30, sq)),
                onOff, batteryWh, paddleMode, clampWpm(speed));
    }

    public HandheldData withWpm(int speed) {
        return new HandheldData(freqHz, power, squelch, on, batteryWh, paddle, clampWpm(speed));
    }

    public int batteryPercent() {
        return Math.round(100 * batteryWh / CAPACITY_WH);
    }
}
