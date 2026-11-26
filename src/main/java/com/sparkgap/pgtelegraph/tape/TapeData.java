package com.sparkgap.pgtelegraph.tape;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Contents of a paper tape. {@code runs} alternate mark, space, mark, ... starting with a mark.
 * INKED tape (from the inker) counts runs in game ticks as received; PUNCHED tape (from the
 * perforator) counts them in Morse units, so the transmitter can play it at any speed.
 */
public record TapeData(int kind, List<Integer> runs, String label) {
    public static final int INKED = 0;
    public static final int PUNCHED = 1;
    public static final int MAX_RUNS = 4000;

    public TapeData {
        runs = List.copyOf(runs);
    }

    public static final Codec<TapeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("kind").forGetter(TapeData::kind),
            Codec.INT.listOf().fieldOf("runs").forGetter(TapeData::runs),
            Codec.STRING.optionalFieldOf("label", "").forGetter(TapeData::label)
    ).apply(i, TapeData::new));

    public static final StreamCodec<ByteBuf, TapeData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TapeData::kind,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), TapeData::runs,
            ByteBufCodecs.STRING_UTF8, TapeData::label,
            TapeData::new);

    public int total() {
        int t = 0;
        for (int r : runs) t += r;
        return t;
    }

    public static int[] toArray(List<Integer> runs) {
        int[] a = new int[runs.size()];
        for (int i = 0; i < a.length; i++) a[i] = runs.get(i);
        return a;
    }
}
