package com.sparkgap.pgtelegraph.block;

/** Shared case and terminal layout of the transmitter and receiver (NORTH-authored pixels). */
final class RadioTerminals {
    private RadioTerminals() {}

    /** 0 PWR+, 1 PWR-, 2/3 front pair (KEY/COM on the transmitter, OUT1/OUT2 on the receiver) */
    static final double[][] BOXES = {
            {10.5, 8, 10.5, 13.5, 10, 13.5},
            {2.5, 8, 10.5, 5.5, 10, 13.5},
            {9, 8, 4, 12, 10, 7},
            {4, 8, 4, 7, 10, 7},
    };

    static double[][] shape() {
        return new double[][]{{2, 0, 3, 14, 8, 14}, BOXES[0], BOXES[1], BOXES[2], BOXES[3]};
    }
}
