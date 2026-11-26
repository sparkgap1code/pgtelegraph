package com.sparkgap.pgtelegraph.block;

/**
 * Turns Morse units into whole game ticks at any WPM. One unit is 1.2 s / WPM = 24 / WPM ticks;
 * the rounding error of each element is carried into the next one, so the average speed is
 * exact (20 WPM really is 20) while single elements may be up to half a tick long or short.
 */
final class MorseClock {
    private double carry;

    int ticks(int units, int wpm) {
        double exact = units * 24.0 / Math.max(1, wpm) + carry;
        int t = Math.max(1, (int) Math.round(exact));
        carry = exact - t;
        return t;
    }

    void reset() {
        carry = 0;
    }
}
