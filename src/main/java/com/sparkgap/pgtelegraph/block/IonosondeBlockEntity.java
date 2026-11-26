package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.Ionosphere;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Ground ionosonde (이오노존데): sweeps 1-20 MHz straight up through any antenna on its coax every
 * 5 s and draws the ionogram: virtual height of the echo against frequency. The E layer echoes near
 * 110 km up to foE, the F2 layer from about 250 km, climbing steeply to the critical frequency foF2
 * where the echo disappears. During a flare the low frequencies give no echo at all (absorbed).
 */
public class IonosondeBlockEntity extends com.sparkgap.pgtelegraph.block.PoweredStationBlockEntity implements IHaveGoggleInformation {
    // synced
    public float foF2, foE, fMin, muf;
    public int kp;
    public boolean flare, hasAntenna;

    public IonosondeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    /** mains: 230 V, 300 W */
    public static final com.sparkgap.pgtelegraph.block.Spec SPEC = spec(230, 300);

    @Override
    public double nominalV() {
        return 230;
    }

    @Override
    public double ratedW() {
        return 300;
    }

    @Override
    public com.sparkgap.pgtelegraph.block.Spec spec() {
        return SPEC;
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide && !powerTick()) return;
        if (level == null || level.isClientSide || level.getGameTime() % 100 != Math.floorMod(worldPosition.asLong(), 100)) return;
        AntennaLink l = AntennaLink.scan(level, worldPosition);
        hasAntenna = l != null && l.amplifier() == null;
        Ionosphere io = Ionosphere.get(level);
        double sun = Ionosphere.sun(level);
        foF2 = (float) io.foF2(level);
        muf = (float) io.muf(level);
        foE = (float) (sun > 0 ? 0.9 * Math.pow(sun * io.sfi() / 120, 0.25) * 3.3 : 0.6);
        flare = io.flare(level);
        fMin = (float) (1 + 1.5 * sun * io.sfi() / 120 + (flare ? 6 * sun : 0));
        kp = io.kp();
        sendData();
    }

    /** virtual height (km) of the echo at f MHz, or -1 for no echo */
    public static double virtualHeight(double f, float foE, float foF2, float fMin) {
        if (f < fMin || f >= foF2) return -1;
        if (f < foE * 0.98) return 105 + 25 / Math.sqrt(1 - Math.pow(f / foE, 2));
        double x = Math.min(0.995, f / foF2);
        return Math.min(800, 230 + 60 / Math.sqrt(1 - x * x));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        if (!hasAntenna) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.no_antenna")).withStyle(ChatFormatting.RED));
            return true;
        }
        tip.add(Component.literal(String.format(Locale.ROOT, "     foF2 %.1f MHz  MUF %.1f MHz", foF2, muf)).withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal(String.format(Locale.ROOT, "     foE %.1f  fmin %.1f  Kp %d%s", foE, fMin, kp, flare ? "  FLARE" : ""))
                .withStyle(flare || kp > 4 ? ChatFormatting.RED : ChatFormatting.GRAY));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putFloat("FoF2", foF2);
            tag.putFloat("FoE", foE);
            tag.putFloat("FMin", fMin);
            tag.putFloat("Muf", muf);
            tag.putInt("Kp", kp);
            tag.putBoolean("Flare", flare);
            tag.putBoolean("Ant", hasAntenna);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            foF2 = tag.getFloat("FoF2");
            foE = tag.getFloat("FoE");
            fMin = tag.getFloat("FMin");
            muf = tag.getFloat("Muf");
            kp = tag.getInt("Kp");
            flare = tag.getBoolean("Flare");
            hasAntenna = tag.getBoolean("Ant");
        }
    }
}
