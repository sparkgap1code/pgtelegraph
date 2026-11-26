package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/** Sends RF from a power stage down its coax: into an antenna (radiated) or into an amplifier. */
final class RfChain {
    private RfChain() {}

    /** @return power reflected back into the source (W) */
    static double deliver(Level level, BlockPos source, AntennaLink link, long freqHz, double rfW) {
        return deliver(level, source, link, freqHz, rfW, 0, null);
    }

    /**
     * @param spreadHz  broadband emission width (spark), 0 for a clean carrier
     * @param harmonics level of the 2nd and 3rd harmonic relative to the carrier (dBc, negative), or null
     * @return power reflected back into the source (W)
     */
    static double deliver(Level level, BlockPos source, AntennaLink link, long freqHz, double rfW, double spreadHz,
                          double[] harmonics) {
        double f = freqHz / 1e6;
        var lna = com.sparkgap.pgtelegraph.radio.Preamp.of(level, link, f);
        if (lna != null) { // an LNA only amplifies towards the radio: transmitted into, it burns out and passes nothing
            if (!lna.burnt() && rfW > 0.2) {
                level.setBlock(lna.pos(), level.getBlockState(lna.pos()).setValue(LnaBlock.BURNT, true), 3);
                level.playSound(null, lna.pos(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.6f);
                if (level instanceof net.minecraft.server.level.ServerLevel sl)
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, lna.pos().getX() + 0.5, lna.pos().getY() + 0.6,
                            lna.pos().getZ() + 0.5, 8, 0.1, 0.1, 0.1, 0.01);
            }
            com.sparkgap.pgtelegraph.radio.RadioWorld.silence(level, source);
            return rfW; // all of it comes back
        }
        double g2 = link.reflection(f);
        double delivered = rfW * (1 - g2) * Math.pow(10, -RadioMath.coaxLossDb(link.coaxLossDb10(), f) / 10);
        if (link.amplifier() != null) {
            RadioWorld.silence(level, source);
            if (level.getBlockEntity(link.amplifier()) instanceof LinearAmplifierBlockEntity amp) {
                amp.drive(delivered, freqHz, source);
            }
            return rfW * g2;
        }
        if (link.kind() == AntennaLink.Kind.DUMMY) {
            if (level.getBlockEntity(link.feed()) instanceof DummyLoadBlockEntity d) d.absorb(delivered, freqHz);
        }
        if (link.coil() != null && level.getBlockEntity(link.coil()) instanceof LoadingCoilBlockEntity coil) {
            coil.heat(delivered * coil.coilLossShare());
        }
        RadioWorld.setAntennaBandwidth(level, source, link.amplifier() != null || link.q() <= 0 ? 0 : link.f0MHz() * 1e6 / link.q());
        RadioWorld.emit(level, new RadioWorld.Emission(source, link.feed(), freqHz,
                RadioMath.dbm(delivered) + link.gainDb(f), link.heightM(), level.getGameTime(),
                link.aim(), link.beamDeg(f), link.floorDb(), spreadHz));
        for (int n = 2; n <= 3; n++) {
            BlockPos key = RadioWorld.harmonicKey(source, n);
            if (harmonics == null || harmonics[n - 2] < -150) {
                RadioWorld.silence1(level, key);
                continue;
            }
            double fn = f * n;
            double hw = rfW * Math.pow(10, harmonics[n - 2] / 10) * (1 - link.reflection(fn))
                    * Math.pow(10, -RadioMath.coaxLossDb(link.coaxLossDb10(), fn) / 10);
            RadioWorld.emit(level, new RadioWorld.Emission(key, link.feed(), freqHz * n,
                    RadioMath.dbm(hw) + link.gainDb(fn), link.heightM(), level.getGameTime(),
                    link.aim(), link.beamDeg(fn), link.floorDb(), 0));
        }
        return rfW * g2;
    }

    /** Over-powered coax: the lowest-rated piece on the run burns through. */
    static void burnWeakest(Level level, AntennaLink link) {
        BlockPos worst = null;
        double worstW = Double.MAX_VALUE;
        for (BlockPos p : link.coaxPath()) {
            if (level.getBlockState(p).getBlock() instanceof CoaxBlock c && c.grade.maxWatts < worstW) {
                worstW = c.grade.maxWatts;
                worst = p;
            }
        }
        if (worst == null) return;
        level.destroyBlock(worst, false);
        level.playSound(null, worst, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.2f);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, worst.getX() + 0.5, worst.getY() + 0.2, worst.getZ() + 0.5,
                    10, 0.2, 0.1, 0.2, 0.01);
        }
    }
}
