package com.sparkgap.pgtelegraph.block;

import net.minecraft.nbt.CompoundTag;

/**
 * What a beacon sends. MESSAGE: the text, then {@code gapSec} of silence, repeated.
 * NDB: 8 s of carrier (for direction finders), then the ident keyed, repeated.
 * CARRIER: key down for good (tuning, fox-hunt practice).
 */
public final class BeaconProgram {
    public static final int MESSAGE = 0, NDB = 1, CARRIER = 2;
    public static final int MAX_LEN = 120;

    public String message = "VVV DE BCN";
    public int wpm = 15, mode = MESSAGE, gapSec = 10;
    /** station callsign (from a license); replaces {CALL} in the message */
    public String call = "";

    private String text() {
        return message.replace("{CALL}", call.isEmpty() ? "NOCALL" : call);
    }

    private final BeaconSequencer seq = new BeaconSequencer();
    private int wait, carrier;

    public void set(String msg, int speed, int m, int gap) {
        message = msg == null ? "" : msg.length() > MAX_LEN ? msg.substring(0, MAX_LEN) : msg;
        wpm = Math.max(5, Math.min(40, speed));
        mode = Math.max(0, Math.min(2, m));
        gapSec = Math.max(0, Math.min(600, gap));
        seq.stop();
        wait = carrier = 0;
    }

    /** advance one tick; true while the carrier should be on */
    public boolean tick() {
        switch (mode) {
            case CARRIER -> {
                return true;
            }
            case NDB -> {
                if (carrier > 0) {
                    carrier--;
                    return true;
                }
                if (seq.busy()) return seq.tick();
                if (wait > 0) {
                    wait--;
                    return false;
                }
                if (carrier == 0 && !seq.busy()) {
                    seq.send(text(), wpm);
                    carrier = -1; // ident running; carrier comes back after it
                }
                boolean k = seq.tick();
                if (!seq.busy()) {
                    carrier = 160;
                    wait = 10;
                }
                return k;
            }
            default -> {
                if (seq.busy()) return seq.tick();
                if (wait > 0) {
                    wait--;
                    return false;
                }
                seq.send(text(), wpm);
                wait = gapSec * 20;
                return seq.tick();
            }
        }
    }

    public void save(CompoundTag tag) {
        tag.putString("BcnMsg", message);
        tag.putInt("BcnWpm", wpm);
        tag.putInt("BcnMode", mode);
        tag.putInt("BcnGap", gapSec);
        tag.putString("BcnCall", call);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("BcnMsg")) return;
        message = tag.getString("BcnMsg");
        wpm = tag.getInt("BcnWpm");
        mode = tag.getInt("BcnMode");
        gapSec = tag.getInt("BcnGap");
        call = tag.getString("BcnCall");
    }

    /** a block holding a beacon program (configured through BeaconPayload) */
    public interface Holder {
        BeaconProgram program();

        void programChanged();
    }
}
