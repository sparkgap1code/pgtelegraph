package com.sparkgap.pgtelegraph;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Grants this mod's advancements (all use the "impossible" trigger and are awarded from code). */
public final class Awards {
    private Awards() {}

    public static void award(ServerPlayer p, String id) {
        if (p == null || p.server == null) return;
        AdvancementHolder h = p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, id));
        if (h == null) return;
        AdvancementProgress prog = p.getAdvancements().getOrStartProgress(h);
        if (prog.isDone()) return;
        for (String c : prog.getRemainingCriteria()) p.getAdvancements().award(h, c);
    }

    /** to every player within {@code radius} blocks */
    public static void near(ServerLevel level, BlockPos pos, int radius, String id) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(pos.getCenter()) <= (double) radius * radius) award(p, id);
        }
    }
}
