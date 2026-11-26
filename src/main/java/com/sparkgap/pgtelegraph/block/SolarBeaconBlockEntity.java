package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Solar beacon: a self-contained low-power beacon for remote hilltops. A 5 W panel on its lid
 * charges a 20 Wh battery in full sun (less when clouded, nothing at night or under a roof); the
 * transmitter is 50 % efficient and sends 0.1-5 W. Coax to an antenna on any side.
 * At 1 W sending NDB-style (mostly key down) it runs about 30 h on a full battery.
 */
public class SolarBeaconBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, Tunable,
        BeaconProgram.Holder, RfMeterable {
    public static final double PANEL_W = 5, BATTERY_WH = 20, EFF = 0.5, IDLE_W = 0.05;

    private final BeaconProgram program = new BeaconProgram();
    private ScrollValueBehaviour power;
    private long freqHz = 475_500L;
    private double chargeWh = BATTERY_WH / 2;
    private AntennaLink link;
    private int rescan;
    private double fwd, refl;
    // synced
    private float shownSolar, shownRf;

    public SolarBeaconBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        program.message = "VVV DE {CALL}";
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        power = new ScrollValueBehaviour(Component.translatable("pgtelegraph.transmitter.power"), this,
                ValueBoxes.front(8, 3, 3, 0.3f))
                .between(1, 50)
                .withFormatter(v -> String.format(Locale.ROOT, "%.1f W", v / 10.0));
        power.value = 10;
        behaviours.add(power);
    }

    @Override
    public long freqHz() {
        return freqHz;
    }

    @Override
    public void setFreq(long hz) {
        freqHz = Math.max(RadioBlockEntity.MIN_HZ, Math.min(RadioBlockEntity.MAX_HZ, hz));
        rescan = 0;
        setChanged();
        sendData();
    }

    @Override
    public BeaconProgram program() {
        return program;
    }

    @Override
    public void programChanged() {
        setChanged();
        sendData();
    }

    @Override
    public double forwardW() {
        return fwd;
    }

    @Override
    public double reflectedW() {
        return refl;
    }

    /** panel output now (W) */
    private double solarW() {
        if (!level.isDay()) return 0;
        int sky = level.getBrightness(LightLayer.SKY, worldPosition.above());
        if (sky < 15) return 0;
        // sun height: 1 at noon, 0 at sunrise/sunset
        double a = level.getTimeOfDay(1f) * 2 * Math.PI;
        double sun = Math.max(0, Math.cos(a));
        double clouds = level.isThundering() ? 0.1 : level.isRaining() ? 0.3 : 1;
        return PANEL_W * sun * clouds;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        if (--rescan <= 0) {
            rescan = 40;
            link = AntennaLink.scan(level, worldPosition);
        }
        double solar = solarW();
        double draw = IDLE_W;
        boolean keyed = chargeWh > 0 && program.tick();
        double rf = 0;
        if (keyed && link != null) {
            rf = (power == null ? 10 : power.getValue()) / 10.0;
            draw += rf / EFF;
            refl = RfChain.deliver(level, worldPosition, link, freqHz, rf);
            fwd = rf;
        } else {
            RadioWorld.silence(level, worldPosition);
            fwd = refl = 0;
        }
        chargeWh = Math.max(0, Math.min(BATTERY_WH, chargeWh + (solar - draw) / 20 / 3600));
        if (Math.abs(solar - shownSolar) > 0.1 || Math.abs(rf - shownRf) > 0.05 || level.getGameTime() % 100 == 0) {
            shownSolar = (float) solar;
            shownRf = (float) rf;
            setChanged();
            sendData();
        }
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide) RadioWorld.silence(level, worldPosition);
        super.remove();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.freq"))
                .append(": ").append(Component.literal(RadioBlockEntity.formatFreq(freqHz)).withStyle(ChatFormatting.AQUA)));
        tip.add(Component.literal(String.format(Locale.ROOT, "     ☀ %.1f W   ⚡ %.0f %%   RF %.1f W",
                shownSolar, 100 * chargeWh / BATTERY_WH, shownRf)).withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("     ").append(Component.literal(program.message).withStyle(ChatFormatting.GRAY)));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putLong("Freq", freqHz);
        tag.putDouble("Charge", chargeWh);
        program.save(tag);
        if (clientPacket) {
            tag.putFloat("Solar", shownSolar);
            tag.putFloat("Rf", shownRf);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Freq")) freqHz = tag.getLong("Freq");
        chargeWh = tag.getDouble("Charge");
        program.load(tag);
        if (clientPacket) {
            shownSolar = tag.getFloat("Solar");
            shownRf = tag.getFloat("Rf");
        }
    }
}
