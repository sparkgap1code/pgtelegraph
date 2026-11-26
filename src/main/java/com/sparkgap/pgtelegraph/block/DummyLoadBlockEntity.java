package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/** Oil-cooled 50 Ω load: 1.5 kW continuous, heat capacity 6 kJ/K, 10 W/K to the air. */
public class DummyLoadBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public static final double HEAT_J_PER_K = 6000, COOLING_W_PER_K = 10, AMBIENT_C = 20, BURN_C = 180;

    private double tempC = AMBIENT_C;
    private double inW;
    private long inHz;
    private long lastIn = -100;
    private float shownW, shownC = (float) AMBIENT_C;

    public DummyLoadBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** called by a transmitter each tick it feeds us */
    public void absorb(double watts, long hz) {
        if (level == null || getBlockState().getValue(DummyLoadBlock.BURNT)) return;
        inW += watts;
        inHz = hz;
        lastIn = level.getGameTime();
    }

    void serverTick() {
        double p = inW;
        inW = 0;
        tempC += (p - COOLING_W_PER_K * (tempC - AMBIENT_C)) / 20 / HEAT_J_PER_K;
        if (level instanceof ServerLevel sl && tempC > 120 && sl.random.nextInt(8) == 0) {
            sl.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8,
                    worldPosition.getZ() + 0.5, 1, 0.1, 0.05, 0.1, 0.01);
        }
        if (tempC > BURN_C && !getBlockState().getValue(DummyLoadBlock.BURNT)) {
            level.setBlock(worldPosition, getBlockState().setValue(DummyLoadBlock.BURNT, true), 3);
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.8f);
        }
        float w = level.getGameTime() - lastIn > 1 ? 0 : (float) p;
        if (Math.abs(w - shownW) > Math.max(0.5f, shownW * 0.05f) || Math.abs(tempC - shownC) > 1 || (w == 0) != (shownW == 0)) {
            shownW = w;
            shownC = (float) tempC;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal(String.format(Locale.ROOT, "     %.1f W", shownW)).withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal(String.format(Locale.ROOT, "     %.0f °C", shownC))
                .withStyle(shownC > 120 ? ChatFormatting.RED : ChatFormatting.GRAY));
        if (getBlockState().getValue(DummyLoadBlock.BURNT)) {
            tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.radio.burnt").withStyle(ChatFormatting.DARK_RED)));
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.putDouble("Temp", tempC);
        tag.putFloat("ShownW", shownW);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        tempC = tag.contains("Temp") ? tag.getDouble("Temp") : AMBIENT_C;
        shownC = (float) tempC;
        shownW = tag.getFloat("ShownW");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider r) {
        return saveWithoutMetadata(r);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
