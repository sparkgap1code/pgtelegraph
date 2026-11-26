package com.sparkgap.pgtelegraph.block;

/** A transmitter an SWR/power meter can read: forward and reflected power at its output (W). */
public interface RfMeterable {
    double forwardW();

    double reflectedW();
}
