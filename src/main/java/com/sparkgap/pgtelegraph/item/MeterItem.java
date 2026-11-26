package com.sparkgap.pgtelegraph.item;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.RfMeterable;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import com.sparkgap.pgtelegraph.radio.Reception;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Hand-held test gear. Held in the main hand it shows its reading on the action bar twice a second.
 * <ul>
 *   <li>SWR: look at a transmitter - forward / reflected power and SWR</li>
 *   <li>FIELD: total RF power arriving at you, all frequencies, on a short whip</li>
 *   <li>COUNTER: frequency of the strongest signal around (above -90 dBm)</li>
 *   <li>DF: small yagi on a handle, 40° beam - sweep it to find the bearing of a signal on its
 *       frequency (sneak + use to tune)</li>
 *   <li>SPECTRUM: use to open the spectrum display</li>
 * </ul>
 */
public class MeterItem extends Item {
    public enum Kind { SWR, FIELD, COUNTER, DF, SPECTRUM, TRACKER }

    public final Kind kind;
    private static final Reception.TerrainCache CACHE = new Reception.TerrainCache();

    public MeterItem(Properties properties, Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public static long dfHz(ItemStack s) {
        Long v = s.get(PgTelegraph.ITEM_FREQ.get());
        return v == null ? 121_500_000L : v;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (kind == Kind.DF && player.isShiftKeyDown()) {
            if (level.isClientSide) ClientAccess.openItemTune(hand, dfHz(s));
            return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }
        if (kind == Kind.SPECTRUM) {
            if (level.isClientSide) ClientAccess.openSpectrum();
            return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }
        return InteractionResultHolder.pass(s);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !selected || !(entity instanceof ServerPlayer p) || level.getGameTime() % 10 != 0) return;
        if (p.getMainHandItem() != stack || kind == Kind.SPECTRUM) return;
        p.displayClientMessage(reading(level, p, stack), true);
    }

    /** a short receiving whip at the player's head */
    public static AntennaLink probe(Player p, Vec3 aim, double fMHz) {
        BlockPos at = BlockPos.containing(p.getEyePosition());
        if (aim == null) return new AntennaLink(at, AntennaLink.Kind.WHIP, 0.3, fMHz, 1.6, 0, 0, Double.MAX_VALUE, List.of(), null);
        return new AntennaLink(at, AntennaLink.Kind.YAGI, 1, fMHz, 1.6, 0, 0, Double.MAX_VALUE, List.of(), null,
                RadioMath.DEFAULT_Q, 2, null, 0, aim);
    }

    /** strongest emission at the player: {dBm, Hz}; dBm = -inf when nothing */
    public static double[] strongest(Level level, Player p) {
        double best = Double.NEGATIVE_INFINITY, total = 0, hz = 0;
        long t = level.getGameTime();
        boolean day = level.isDay(), rain = level.isRaining(), thunder = level.isThundering();
        for (RadioWorld.Emission e : RadioWorld.on(level)) {
            double f = e.freqHz() / 1e6;
            AntennaLink rl = probe(p, null, f);
            double dbm = Reception.received(level, e, rl, f, p.getId(), CACHE, t, day, rain, thunder);
            total += RadioMath.fromDb(dbm);
            if (dbm > best) {
                best = dbm;
                hz = Reception.observedHz(level, e, BlockPos.containing(p.getEyePosition()), t);
            }
        }
        return new double[]{best, hz, total <= 0 ? Double.NEGATIVE_INFINITY : 10 * Math.log10(total)};
    }

    private Component reading(Level level, ServerPlayer p, ItemStack stack) {
        switch (kind) {
            case SWR -> {
                HitResult hr = p.pick(6, 0, false);
                if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK
                        && level.getBlockEntity(bh.getBlockPos()) instanceof RfMeterable m) {
                    double f = m.forwardW(), r = m.reflectedW();
                    double g = f > 0 ? Math.sqrt(Math.min(0.999, r / f)) : 0;
                    double swr = (1 + g) / (1 - g);
                    ChatFormatting c = swr < 1.5 ? ChatFormatting.GREEN : swr < 3 ? ChatFormatting.YELLOW : ChatFormatting.RED;
                    return Component.literal(String.format(Locale.ROOT, "FWD %.1f W   REF %.1f W   ", f, r))
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(f > 0 ? String.format(Locale.ROOT, "SWR %.2f", swr) : "SWR -").withStyle(c));
                }
                return Component.translatable("pgtelegraph.meter.swr.aim").withStyle(ChatFormatting.GRAY);
            }
            case FIELD -> {
                double[] s = strongest(level, p);
                return bar(s[2], -120, -20).append(Component.literal(Double.isInfinite(s[2]) ? "  < -150 dBm"
                        : String.format(Locale.ROOT, "  %.0f dBm  %s", s[2], RadioMath.sMeter(s[2]))).withStyle(ChatFormatting.GOLD));
            }
            case COUNTER -> {
                double[] s = strongest(level, p);
                if (s[0] < -90) return Component.literal("--------.--- kHz").withStyle(ChatFormatting.DARK_GRAY);
                return Component.literal(String.format(Locale.ROOT, "%,.3f kHz", s[1] / 1000)).withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(String.format(Locale.ROOT, "   %.0f dBm", s[0])).withStyle(ChatFormatting.GRAY));
            }
            case DF -> {
                long hz = dfHz(stack);
                Vec3 look = p.getLookAngle();
                AntennaLink yagi = probe(p, look, hz / 1e6);
                Reception.Result r = Reception.listen(level, yagi, hz, 0, null, p.getId(), CACHE, -137, RadioMath.FILTER_HZ);
                double snr = r.hasSignal() ? r.best() - r.noiseDbm() : -10;
                return Component.literal(RadioBlockEntityFormat.freq(hz) + "  ").withStyle(ChatFormatting.AQUA)
                        .append(bar(snr, 0, 40))
                        .append(Component.literal(String.format(Locale.ROOT, "  %s  %.0f°",
                                r.hasSignal() ? String.format(Locale.ROOT, "%.0f dB", snr) : "--", AntennaLink.azimuth(look)))
                                .withStyle(ChatFormatting.GOLD));
            }
            case TRACKER -> {
                BlockPos at = p.blockPosition();
                long now = level.getGameTime();
                var up = com.sparkgap.pgtelegraph.sat.SatelliteData.highest(level, at);
                if (up != null) {
                    var lk = com.sparkgap.pgtelegraph.sat.Orbit.look(up.orbit, at, now);
                    return Component.literal(up.name + "  ").withStyle(ChatFormatting.GREEN)
                            .append(Component.literal(String.format(Locale.ROOT, "az %.0f° el %.0f°  %.0f km  ", lk.azDeg(), lk.elDeg(), lk.rangeKm()))
                                    .withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(String.format(Locale.ROOT, "%s %+.1f kHz", RadioBlockEntityFormat.freq(up.beaconHz()),
                                    lk.dopplerHz(up.beaconHz()) / 1000)).withStyle(ChatFormatting.AQUA));
                }
                String best = null;
                long soon = Long.MAX_VALUE;
                for (var s : com.sparkgap.pgtelegraph.sat.SatelliteData.of(level)) {
                    long r = com.sparkgap.pgtelegraph.sat.Orbit.nextRise(s.orbit, at, now);
                    if (r > 0 && r < soon) {
                        soon = r;
                        best = s.name;
                    }
                }
                if (best == null) return Component.translatable("pgtelegraph.meter.tracker.none").withStyle(ChatFormatting.DARK_GRAY);
                return Component.literal(best + "  AOS " + String.format(Locale.ROOT, "%d:%02d", soon / 1200, soon / 20 % 60))
                        .withStyle(ChatFormatting.YELLOW);
            }
            default -> {
                return Component.empty();
            }
        }
    }

    static MutableComponent bar(double v, double lo, double hi) {
        int n = (int) Math.round(Math.max(0, Math.min(1, (v - lo) / (hi - lo))) * 16);
        return Component.literal("█".repeat(n)).withStyle(ChatFormatting.GREEN)
                .append(Component.literal("░".repeat(16 - n)).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.pgtelegraph.meter." + kind.name().toLowerCase(Locale.ROOT) + ".hint")
                .withStyle(ChatFormatting.GRAY));
        if (kind == Kind.DF) tip.add(Component.literal(RadioBlockEntityFormat.freq(dfHz(stack))).withStyle(ChatFormatting.AQUA));
    }

    /** formatting shared with the radios */
    static final class RadioBlockEntityFormat {
        static String freq(long hz) {
            return com.sparkgap.pgtelegraph.block.RadioBlockEntity.formatFreq(hz);
        }
    }
}
