package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Lightning striking within {@value #RADIUS} blocks of an antenna sends a surge down its coax:
 * every radio and amplifier at the far end burns, unless the surge meets a grounded lightning
 * arrester first. Loading coils, tuners and coax pass it on.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class Lightning {
    private Lightning() {}

    static final int RADIUS = 6, MAX_VISIT = 1024;

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!(e.getEntity() instanceof LightningBolt bolt) || !(e.getLevel() instanceof ServerLevel level) || e.loadedFromDisk()) return;
        BlockPos hit = bolt.blockPosition();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(hit.offset(-RADIUS, -RADIUS, -RADIUS), hit.offset(RADIUS, RADIUS, RADIUS))) {
            if (!level.isLoaded(p)) continue;
            if (isAntenna(level.getBlockState(p))) {
                BlockPos q = p.immutable();
                seen.add(q);
                queue.add(q);
            }
        }
        while (!queue.isEmpty() && seen.size() < MAX_VISIT) {
            BlockPos c = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = c.relative(d);
                if (!seen.add(n) || !level.isLoaded(n)) continue;
                BlockState s = level.getBlockState(n);
                if (s.getBlock() instanceof LightningArresterBlock) {
                    if (LightningArresterBlock.grounded(level, n)) {
                        level.playSound(null, n, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.6f, 1.8f);
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, n.getX() + 0.5, n.getY() + 0.6, n.getZ() + 0.5,
                                20, 0.2, 0.2, 0.2, 0.2);
                        continue; // surge goes to ground here
                    }
                    queue.add(n);
                    continue;
                }
                if (s.getBlock() instanceof CoaxBlock || s.getBlock() instanceof AntennaTunerBlock
                        || s.getBlock() instanceof LoadingCoilBlock || isAntenna(s)) {
                    queue.add(n);
                    continue;
                }
                if (level.getBlockEntity(n) instanceof RatedBlockEntity r && (r instanceof RadioBlockEntity
                        || r instanceof LinearAmplifierBlockEntity)) {
                    if (!r.isBurnt()) {
                        r.burn();
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, n.getX() + 0.5, n.getY() + 0.6, n.getZ() + 0.5,
                                30, 0.3, 0.3, 0.3, 0.3);
                    }
                }
            }
        }
    }

    static boolean isAntenna(BlockState s) {
        return s.getBlock() instanceof AntennaRodBlock || s.getBlock() instanceof ShortAntennaBlock
                || s.getBlock() instanceof VhfAntennaBlock || s.getBlock() instanceof TopLoadWireBlock;
    }
}
