package com.sparkgap.pgtelegraph.parts;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Burnt parts and battery thermal runaway. */
public final class Wreckage {
    private Wreckage() {}

    /** A part that failed becomes "burnt X". */
    public static ItemStack burnt(ItemStack part) {
        ItemStack s = new ItemStack(PgTelegraph.BURNT_PART.get());
        PartDef d = PartItem.def(part);
        if (d != null) s.set(PgTelegraph.BURNT_OF.get(), d.id());
        return s;
    }

    /** Smoke and hissing while a pack is running away (call every tick of the countdown). */
    public static void smoulder(Level level, Vec3 at, int ticksLeft) {
        if (!(level instanceof ServerLevel sl)) return;
        sl.sendParticles(ticksLeft < 20 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, at.x, at.y, at.z, 3, 0.15, 0.1, 0.15, 0.02);
        if (ticksLeft % 10 == 0) level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 0.7f + ticksLeft / 100f);
    }

    /** Li-ion thermal runaway at the end: fire and a small blast that hurts but does not crater. */
    public static void explode(Level level, Vec3 at) {
        level.explode(null, at.x, at.y, at.z, 1.8f, true, Level.ExplosionInteraction.NONE);
    }

    /** Venting (LiFePO4, NiMH, alkaline): smoke and a pop, the pack is ruined. */
    public static void vent(Level level, Vec3 at) {
        if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 15, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.6f);
    }
}
