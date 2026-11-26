package com.sparkgap.pgtelegraph.block;

/**
 * A radio set that can drive an audio amplifier. While an amplifier takes its audio the set's own
 * little loudspeaker is switched off - the sound comes out of the PA speakers only.
 */
public interface AmpFed {
    /** called by the amplifier every tick it is fed from this set (server) */
    void ampFed(long tick);

    /** the set's own speaker is off (synced to clients) */
    boolean ampMuted();
}
