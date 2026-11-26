package com.sparkgap.pgtelegraph.block;

/**
 * Nameplate ratings of a device. The same numbers drive the simulation (overheat temperature,
 * continuous power, voltage/current limits) and the Shift tooltip, so they can never disagree.
 *
 * @param maxTempC    temperature at which Power Grid's thermal model overheats the part
 * @param maxPowerW   continuous dissipation that settles just below maxTempC
 * @param thermalMass heat capacity (how long a brief overload is tolerated)
 * @param maxVolts    insulation limit: more than this across any guarded element flashes it over
 */
public record Spec(float maxTempC, float maxPowerW, float thermalMass, float maxVolts, Part... parts) {

    /** kind is a lang suffix: coil, contact, pullup, rail. */
    public record Part(String kind, float value, float maxAmps, int count) {}

    public static Part coil(float ohms, float maxAmps) {
        return new Part("coil", ohms, maxAmps, 1);
    }

    public static Part contact(float ohms, float maxAmps, int count) {
        return new Part("contact", ohms, maxAmps, count);
    }

    public static Part pullup(float ohms, int count) {
        return new Part("pullup", ohms, 0, count);
    }

    /** Internal supply; value is in volts. */
    public static Part rail(float volts) {
        return new Part("rail", volts, 0, 1);
    }

    public Part part(String kind) {
        for (Part p : parts) if (p.kind.equals(kind)) return p;
        throw new IllegalArgumentException(kind);
    }
}
