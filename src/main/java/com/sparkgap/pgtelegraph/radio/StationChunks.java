package com.sparkgap.pgtelegraph.radio;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.TelegraphConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.ArrayList;

/**
 * Keeps a radio station awake like a real unattended station: its own chunk, every chunk its coax
 * runs through and the chunks the antenna stands in stay loaded (ticking) while the block exists.
 * Tickets are not kept over a restart: a station wakes again once its chunk is loaded by a player
 * and then re-claims everything on its first link scan.
 */
public final class StationChunks {
    private StationChunks() {}

    public static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "station"),
            (level, helper) -> {
                // Drop every saved ticket. Re-forcing them at startup makes Power Grid load its
                // networks inside the chunk-ticket update, and a wire whose end lies in a chunk not
                // yet loaded then crashes the server. Stations re-claim their chunks when they tick.
                for (var e : new ArrayList<>(helper.getBlockTickets().entrySet())) {
                    for (long c : new LongOpenHashSet(e.getValue().ticking())) helper.removeTicket(e.getKey(), c, true);
                    for (long c : new LongOpenHashSet(e.getValue().nonTicking())) helper.removeTicket(e.getKey(), c, false);
                }
            });

    /** Chunks a station needs: itself, the coax run, the antenna feed and its extent. */
    public static LongSet wanted(BlockPos owner, AntennaLink link) {
        LongSet out = new LongOpenHashSet();
        out.add(ChunkPos.asLong(owner));
        if (link == null) return out;
        for (BlockPos p : link.coaxPath()) out.add(ChunkPos.asLong(p));
        out.add(ChunkPos.asLong(link.feed()));
        if (link.coil() != null) out.add(ChunkPos.asLong(link.coil()));
        if (link.amplifier() != null) out.add(ChunkPos.asLong(link.amplifier()));
        int r = (int) Math.ceil(link.spanM());
        if (r > 0) {
            BlockPos f = link.feed();
            for (int x = f.getX() - r; x <= f.getX() + r; x += 16)
                for (int z = f.getZ() - r; z <= f.getZ() + r; z += 16) out.add(ChunkPos.asLong(x >> 4, z >> 4));
            out.add(ChunkPos.asLong((f.getX() + r) >> 4, (f.getZ() + r) >> 4));
            out.add(ChunkPos.asLong((f.getX() - r) >> 4, (f.getZ() - r) >> 4));
        }
        return out;
    }

    /** Brings the forced set of {@code owner} to {@code want}; returns the new held set. */
    public static LongSet update(Level level, BlockPos owner, LongSet held, LongSet want) {
        if (!(level instanceof ServerLevel sl)) return held;
        if (!TelegraphConfig.chunkLoading()) want = new LongOpenHashSet();
        // make sure Power Grid has read its saved networks now, outside any chunk-ticket update
        if (!want.isEmpty()) org.patryk3211.powergrid.electricity.GlobalElectricNetworks.getWorldNetworks(level);
        for (long c : held) {
            if (!want.contains(c)) CONTROLLER.forceChunk(sl, owner, ChunkPos.getX(c), ChunkPos.getZ(c), false, true);
        }
        for (long c : want) {
            if (!held.contains(c)) CONTROLLER.forceChunk(sl, owner, ChunkPos.getX(c), ChunkPos.getZ(c), true, true);
        }
        return new LongOpenHashSet(want);
    }

    public static void release(Level level, BlockPos owner, LongSet held) {
        update(level, owner, held, new LongOpenHashSet());
    }
}
