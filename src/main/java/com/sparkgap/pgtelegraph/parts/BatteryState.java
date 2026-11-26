package com.sparkgap.pgtelegraph.parts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Charge carried by a battery pack item (so packs can be swapped and charged on their own).
 *
 * @param health    remaining fraction of the rated capacity (abuse wears it down)
 * @param deep      a bare Li-ion pack that was run flat: charging it again is dangerous
 */
public record BatteryState(double chargeWh, double health, boolean deep) {
    public static final Codec<BatteryState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("charge").forGetter(BatteryState::chargeWh),
            Codec.DOUBLE.fieldOf("health").forGetter(BatteryState::health),
            Codec.BOOL.fieldOf("deep").forGetter(BatteryState::deep)
    ).apply(i, BatteryState::new));

    public static final StreamCodec<ByteBuf, BatteryState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, BatteryState::chargeWh,
            ByteBufCodecs.DOUBLE, BatteryState::health,
            ByteBufCodecs.BOOL, BatteryState::deep,
            BatteryState::new);

    public static BatteryState full(PartDef d) {
        return new BatteryState(d.capacityWh(), 1, false);
    }

    public double capacity(PartDef d) {
        return d.capacityWh() * health;
    }

    public double fraction(PartDef d) {
        return capacity(d) <= 0 ? 0 : chargeWh / capacity(d);
    }

    /** Terminal voltage: falls from full to cutoff as the pack empties (simple linear curve). */
    public double volts(PartDef d) {
        PartDef.Chemistry c = d.chemistry();
        return c.cutoffV() + (c.fullV() - c.cutoffV()) * Math.max(0, Math.min(1, fraction(d)));
    }

    public BatteryState withCharge(double wh, PartDef d) {
        return new BatteryState(Math.max(0, Math.min(capacity(d), wh)), health, deep);
    }

    public BatteryState abused(double healthLoss, boolean deepNow) {
        return new BatteryState(chargeWh, Math.max(0.05, health - healthLoss), deep || deepNow);
    }
}
