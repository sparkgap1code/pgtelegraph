package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;

/** A block that plays a beat tone plus band noise from a speaker (receiver, tape recorder). */
public interface ToneSource {
    int soundHz();

    float soundTone();

    float soundNoise();

    boolean soundOn();

    BlockPos getBlockPos();

    boolean isRemoved();

    /** client-only sound instances, kept on the block entity */
    Object[] clientSounds();
}
