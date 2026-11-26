package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.tape.Morse;

import java.util.List;

/** Plays text as Morse, one game tick at a time (beacons, computer-sent text). */
public final class BeaconSequencer {
    private List<Integer> runs = List.of();
    private int index, left, wpm = 15;
    private boolean active;
    private final MorseClock clock = new MorseClock();
    private String text = "";

    /** start sending {@code text}; replaces whatever was being sent */
    public void send(String text, int wpm) {
        this.text = text == null ? "" : text;
        this.wpm = Math.max(5, Math.min(40, wpm));
        runs = Morse.encodeUnits(Morse.codes(this.text));
        index = 0;
        clock.reset();
        active = !runs.isEmpty();
        if (active) left = clock.ticks(runs.get(0), this.wpm);
    }

    public void stop() {
        active = false;
    }

    public boolean busy() {
        return active;
    }

    public String text() {
        return active ? text : "";
    }

    /** advance one tick; true while a mark is being sent */
    public boolean tick() {
        if (!active) return false;
        boolean keyed = index % 2 == 0;
        if (--left <= 0) {
            index++;
            if (index >= runs.size()) active = false;
            else left = clock.ticks(runs.get(index), wpm);
        }
        return keyed;
    }
}
