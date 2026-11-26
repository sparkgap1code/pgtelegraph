package com.sparkgap.pgtelegraph.block;

/** Anything with a dial the tune screen can set. */
public interface Tunable {
    long freqHz();

    void setFreq(long hz);
}
