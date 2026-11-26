package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.BuzzerBlockEntity;
import com.sparkgap.pgtelegraph.block.CoilDeviceBlockEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Looping tone that lives exactly as long as the buzzer block shows ENERGIZED. */
public class BuzzerSound extends AbstractTickableSoundInstance {
    private final BuzzerBlockEntity buzzer;

    public BuzzerSound(BuzzerBlockEntity buzzer) {
        super(PgTelegraph.BUZZER_TONE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.buzzer = buzzer;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.6f;
        this.pitch = buzzer.pitch();
        var p = buzzer.getBlockPos();
        this.x = p.getX() + 0.5;
        this.y = p.getY() + 0.5;
        this.z = p.getZ() + 0.5;
    }

    @Override
    public void tick() {
        if (buzzer.isRemoved() || buzzer.getLevel() == null
                || !buzzer.getBlockState().hasProperty(CoilDeviceBlockEntity.ENERGIZED)
                || !buzzer.getBlockState().getValue(CoilDeviceBlockEntity.ENERGIZED)) {
            stop();
            return;
        }
        this.pitch = buzzer.pitch();
    }
}
