package com.sparkgap.pgtelegraph.block;

/** A block entity that receives operator input from the client (hold / focus mode). */
public interface TelegraphInput {
    int STRAIGHT = 0;
    int DIT = 1;
    int DAH = 2;

    /** Server side. down=true is repeated every few ticks as a keep-alive while held. */
    void input(int channel, boolean down);
}
