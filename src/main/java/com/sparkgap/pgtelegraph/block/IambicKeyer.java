package com.sparkgap.pgtelegraph.block;

/**
 * Iambic keyer logic (mode B style memory), shared by the keyer block, the keyer paddle, the
 * handheld's built-in keyer and the clip-lead paddle. Call {@link #tick} once per game tick with
 * the paddle state; it returns whether the output is keyed.
 */
public final class IambicKeyer {
    private static final int IDLE = 0, ELEMENT = 1, GAP = 2;

    private final MorseClock clock = new MorseClock();
    private int phase = IDLE;
    private int remaining;
    private boolean lastDah;
    private boolean ditMemory, dahMemory;
    private boolean keyed;

    public boolean keyed() {
        return keyed;
    }

    public void reset() {
        phase = IDLE;
        remaining = 0;
        ditMemory = dahMemory = false;
        keyed = false;
        clock.reset();
    }

    public boolean tick(boolean ditHeld, boolean dahHeld, int wpm) {
        if (phase != IDLE) {
            // iambic memory: opposite paddle touched during an element or its gap
            if (lastDah && ditHeld) ditMemory = true;
            if (!lastDah && dahHeld) dahMemory = true;
            if (--remaining > 0) return keyed;
            if (phase == ELEMENT) {
                phase = GAP;
                remaining = clock.ticks(1, wpm);
                keyed = false;
                return keyed;
            }
            phase = IDLE;
        }

        boolean wantDit = ditHeld || ditMemory;
        boolean wantDah = dahHeld || dahMemory;
        boolean dah;
        if (wantDit && wantDah) dah = !lastDah;   // squeeze: alternate
        else if (wantDit) dah = false;
        else if (wantDah) dah = true;
        else return keyed;

        if (dah) dahMemory = false; else ditMemory = false;
        lastDah = dah;
        phase = ELEMENT;
        remaining = clock.ticks(dah ? 3 : 1, wpm);
        keyed = true;
        return keyed;
    }
}
