package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.ToneSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Looping tone or noise for any {@link ToneSource}. */
public class ToneSound extends AbstractTickableSoundInstance {
    private final ToneSource src;
    private final boolean tone;

    ToneSound(ToneSource src, boolean tone) {
        super(tone ? PgTelegraph.BUZZER_TONE.get() : PgTelegraph.RADIO_NOISE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.src = src;
        this.tone = tone;
        looping = true;
        delay = 0;
        var p = src.getBlockPos();
        x = p.getX() + 0.5;
        y = p.getY() + 0.5;
        z = p.getZ() + 0.5;
        update();
    }

    private void update() {
        if (tone) {
            volume = Math.max(0.001f, src.soundTone() * 0.7f);
            pitch = Math.max(0.5f, Math.min(2f, src.soundHz() / 700f));
        } else volume = Math.max(0.001f, src.soundNoise());
    }

    @Override
    public void tick() {
        if (src.isRemoved() || !src.soundOn()) {
            stop();
            return;
        }
        update();
    }

    public static void ensure(ToneSource src) {
        Object[] s = src.clientSounds();
        var sm = Minecraft.getInstance().getSoundManager();
        for (int i = 0; i < 2; i++) {
            if (!(s[i] instanceof ToneSound t) || t.isStopped()) {
                ToneSound n = new ToneSound(src, i == 0);
                s[i] = n;
                sm.play(n);
            }
        }
    }
}
