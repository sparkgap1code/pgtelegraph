package com.sparkgap.pgtelegraph.block;

/** A box with a power lamp on its top front corner (drawn by the client's PowerLedRenderer). */
public interface PowerLamp {
    int OFF = 0, ON = 1, WARN = 2, BLINK = 3, FAULT = 4;

    /** client: what the lamp shows now */
    int lamp();
}
