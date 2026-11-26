package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.ReceiverBlockEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Looping BFO tone or band noise from a receiver's speaker; volume follows the synced levels. */
public class ReceiverSound extends AbstractTickableSoundInstance {
    private final ReceiverBlockEntity rx;
    private final boolean tone;

    public ReceiverSound(ReceiverBlockEntity rx, boolean tone) {
        super(tone ? PgTelegraph.BUZZER_TONE.get() : PgTelegraph.RADIO_NOISE.get(), SoundSource.BLOCKS,
                SoundInstance.createUnseededRandom());
        this.rx = rx;
        this.tone = tone;
        this.looping = true;
        this.delay = 0;
        var p = rx.getBlockPos();
        this.x = p.getX() + 0.5;
        this.y = p.getY() + 0.5;
        this.z = p.getZ() + 0.5;
        update();
    }

    private void update() {
        if (tone) {
            volume = Math.max(0.001f, rx.toneVol * 0.7f);
            pitch = Math.max(0.5f, Math.min(2f, rx.toneHz / 700f));
        } else {
            volume = Math.max(0.001f, rx.noiseVol);
            pitch = 1f;
        }
    }

    @Override
    public void tick() {
        if (rx.isRemoved() || !rx.isPowered() || rx.isBurnt()) {
            stop();
            return;
        }
        update();
    }
}
