package com.sparkgap.pgtelegraph.item;

import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Vector network analyser (벡터 네트워크 분석기): right-click a radio, tuner or coax
 * and it sweeps the antenna system behind it, 101 points: SWR, return loss and the complex
 * reflection - plotted on a Smith chart - as the radio's port sees it, through the coax (its loss
 * shrinks the circle, its length winds the trace around the chart). Zoom and move the sweep on
 * the screen.
 */
public class VnaItem extends Item {
    public static final int POINTS = 101;
    public static final double VF = 0.66, C = 299_792_458;

    public VnaItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer p)) return InteractionResult.PASS;
        BlockPos pos = ctx.getClickedPos();
        AntennaLink l = find(level, pos);
        if (l == null || l.amplifier() != null) {
            p.displayClientMessage(Component.translatable("pgtelegraph.vna.nothing").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        if (l.kind() == AntennaLink.Kind.CREATIVE) { // flat everywhere: show the HF bands
            sweep(p, pos, 1_000_000L, 30_000_000L);
            return InteractionResult.SUCCESS;
        }
        double f0 = Math.max(0.01, l.f0MHz()) * 1e6;
        sweep(p, pos, (long) (f0 * 0.7), (long) (f0 * 1.3));
        return InteractionResult.SUCCESS;
    }

    static AntennaLink find(Level level, BlockPos pos) {
        AntennaLink l = AntennaLink.scan(level, pos);
        if (l != null) return l;
        for (Direction d : Direction.values()) {
            l = AntennaLink.scan(level, pos.relative(d));
            if (l != null) return l;
        }
        return null;
    }

    /** server: measure and send the sweep */
    public static void sweep(ServerPlayer p, BlockPos pos, long startHz, long stopHz) {
        Level level = p.level();
        if (p.distanceToSqr(pos.getCenter()) > 100) return;
        AntennaLink l = find(level, pos);
        if (l == null) return;
        startHz = Math.max(10_000L, startHz);
        stopHz = Math.max(startHz + 1000, Math.min(30_000_000_000L, stopHz));
        float[] re = new float[POINTS], im = new float[POINTS];
        double f0 = l.f0MHz();
        for (int i = 0; i < POINTS; i++) {
            double fHz = startHz + (stopHz - startHz) * (double) i / (POINTS - 1), f = fHz / 1e6;
            double g2 = Math.min(0.9999, l.reflection(f));
            // series-resonant model: z = 1 + jx, inductive above resonance
            double x = Math.sqrt(4 * g2 / (1 - g2)) * (f >= f0 ? 1 : -1);
            double gr = x * x / (4 + x * x), gi = 2 * x / (4 + x * x); // jx / (2 + jx)
            // through the coax: loss both ways, and the length turns the phase
            double a = Math.pow(10, -2 * RadioMath.coaxLossDb(l.coaxLossDb10(), f) / 20);
            double ph = -2 * 2 * Math.PI * fHz * l.coaxMeters() / (VF * C);
            double c = Math.cos(ph), s = Math.sin(ph);
            re[i] = (float) (a * (gr * c - gi * s));
            im[i] = (float) (a * (gr * s + gi * c));
        }
        String info = l.kind().name() + String.format(java.util.Locale.ROOT, " f0 %.3f MHz, coax %d m", f0, l.coaxMeters());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                new com.sparkgap.pgtelegraph.network.Batch2Payloads.VnaData(pos, startHz, stopHz, re, im, info));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("pgtelegraph.vna.hint").withStyle(ChatFormatting.GRAY));
    }
}
