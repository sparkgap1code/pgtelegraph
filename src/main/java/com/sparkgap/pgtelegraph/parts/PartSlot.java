package com.sparkgap.pgtelegraph.parts;

/** Positions inside a radio. Layouts list which slots a given radio has, in order. */
public enum PartSlot {
    BATTERY, FUSE, REGULATOR, LNA, IF_FILTER, PA, BAND, LPF, EMI_FILTER, OSC;

    public String langKey() {
        return "pgtelegraph.slot." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Slot layouts (index = menu slot). New slots are only ever appended. */
    public enum Layout {
        HANDHELD(BATTERY, REGULATOR, LNA, IF_FILTER, PA, BAND),
        TRANSMITTER(FUSE, PA, BAND, LPF, OSC),
        RECEIVER(FUSE, REGULATOR, LNA, IF_FILTER, BAND, EMI_FILTER, OSC);

        public final PartSlot[] slots;

        Layout(PartSlot... slots) {
            this.slots = slots;
        }

        public int indexOf(PartSlot s) {
            for (int i = 0; i < slots.length; i++) if (slots[i] == s) return i;
            return -1;
        }
    }
}
