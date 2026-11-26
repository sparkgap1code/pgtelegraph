package com.sparkgap.pgtelegraph.radio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Air temperature for oscillator drift: biome base temperature, colder at night and high up. */
public final class Climate {
    private Climate() {}

    /** plains ≈ 15 °C, desert ≈ 45 °C, snowy ≈ -5 °C; -6 °C at night, -6.5 °C per 100 blocks above sea level */
    public static double tempC(Level level, BlockPos pos) {
        double t = level.getBiome(pos).value().getBaseTemperature() * 25 - 5;
        if (!level.isDay()) t -= 6;
        t -= Math.max(0, pos.getY() - level.getSeaLevel()) * 0.065;
        return t;
    }
}
