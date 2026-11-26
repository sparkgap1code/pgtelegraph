package com.sparkgap.pgtelegraph.parts;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/** A radio part. Its numbers come from {@link PartDef}; the tooltip prints them. */
public class PartItem extends Item {
    public final PartDef def;

    public PartItem(Properties properties, PartDef def) {
        super(properties.stacksTo(def.slot() == PartSlot.BATTERY ? 1 : 16));
        this.def = def;
    }

    public static PartDef def(ItemStack s) {
        return s.getItem() instanceof PartItem p ? p.def : null;
    }

    /** PA wear 0..1 (keyed time over its life) */
    public static double wear(ItemStack s) {
        Integer t = s.get(PgTelegraph.PART_WEAR.get());
        return t == null ? 0 : Math.min(1, t / (double) PA_LIFE_TICKS);
    }

    /** keyed time a PA lasts: 20 hours of game time on the air */
    public static final int PA_LIFE_TICKS = 20 * 3600 * 20;

    public static BatteryState battery(ItemStack s) {
        PartDef d = def(s);
        if (d == null || d.slot() != PartSlot.BATTERY) return null;
        BatteryState b = s.get(PgTelegraph.BATTERY_STATE.get());
        return b == null ? BatteryState.full(d) : b;
    }

    private static Component kv(String key, String value) {
        return Component.literal(" ").append(Component.translatable(key).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": " + value).withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(def.slot().langKey()).withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("  ").append(Component.translatable(def.handheld() && def.base()
                        ? "pgtelegraph.part.fits_both" : def.handheld() ? "pgtelegraph.part.fits_handheld" : "pgtelegraph.part.fits_base"))
                        .withStyle(ChatFormatting.DARK_GRAY)));
        switch (def.slot()) {
            case BATTERY -> {
                BatteryState b = battery(stack);
                tip.add(kv("pgtelegraph.part.chemistry", Component.translatable(def.chemistry().langKey()).getString()));
                tip.add(kv("pgtelegraph.part.voltage", String.format(Locale.ROOT, "%.1f V (%.1f-%.1f V)",
                        def.nominalV(), def.chemistry().cutoffV(), def.chemistry().fullV())));
                tip.add(kv("pgtelegraph.part.capacity", String.format(Locale.ROOT, "%.1f / %.1f Wh",
                        b.chargeWh(), b.capacity(def))));
                tip.add(Component.literal(" ").append(Component.translatable(def.protectedCells()
                        ? "pgtelegraph.part.protected" : "pgtelegraph.part.unprotected"))
                        .withStyle(def.protectedCells() ? ChatFormatting.GREEN : ChatFormatting.RED));
                if (b.deep()) tip.add(Component.literal(" ").append(Component.translatable("pgtelegraph.part.deep"))
                        .withStyle(ChatFormatting.DARK_RED));
            }
            case FUSE -> tip.add(kv("pgtelegraph.part.fuse", String.format(Locale.ROOT, "%.0f A", def.fuseA())));
            case REGULATOR -> {
                tip.add(kv("pgtelegraph.part.efficiency", String.format(Locale.ROOT, "%.0f %%", def.regEfficiency() * 100)));
                tip.add(kv("pgtelegraph.part.rx_noise", String.format(Locale.ROOT, "+%.0f dB", def.regNoiseDb())));
            }
            case LNA -> tip.add(kv("pgtelegraph.part.noise_floor", String.format(Locale.ROOT, "%.0f dBm", def.rxNoiseDbm())));
            case IF_FILTER -> tip.add(kv("pgtelegraph.part.bandwidth", def.filterHz() + " Hz"));
            case PA -> {
                tip.add(kv("pgtelegraph.part.max_power", String.format(Locale.ROOT, "%.1f W", def.paMaxW())));
                tip.add(kv("pgtelegraph.part.efficiency", String.format(Locale.ROOT, "%.0f %%", def.paEfficiency() * 100)));
                double w = wear(stack);
                if (w > 0) tip.add(kv("pgtelegraph.part.wear", String.format(Locale.ROOT, "%.1f %%", w * 100)));
            }
            case LPF -> {
                tip.add(kv("pgtelegraph.part.cutoff", RadioMath.formatMHz(def.bandMaxHz() / 1e6)));
                tip.add(kv("pgtelegraph.part.stopband", String.format(Locale.ROOT, "-%.0f dB", def.regNoiseDb())));
            }
            case EMI_FILTER -> tip.add(kv("pgtelegraph.part.emi_reject", String.format(Locale.ROOT, "-%.0f dB", def.regNoiseDb())));
            case OSC -> tip.add(kv("pgtelegraph.part.stability", "\u00b1" + (def.rxNoiseDbm() < 1
                    ? String.format(Locale.ROOT, "%.1f", def.rxNoiseDbm()) : String.format(Locale.ROOT, "%.0f", def.rxNoiseDbm())) + " ppm"));
            case BAND -> {
                tip.add(kv("pgtelegraph.part.range", def.id().equals("band_dual") ? "144-148 / 430-440 MHz"
                        : RadioMath.formatMHz(def.bandMinHz() / 1e6) + " - " + RadioMath.formatMHz(def.bandMaxHz() / 1e6)));
                if (def.bandNfPenaltyDb() > 0) tip.add(kv("pgtelegraph.part.rx_noise", String.format(Locale.ROOT, "+%.0f dB", def.bandNfPenaltyDb())));
            }
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return def.slot() == PartSlot.BATTERY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        BatteryState b = battery(stack);
        return b == null ? 0 : Math.round(13f * (float) b.fraction(def));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        BatteryState b = battery(stack);
        return net.minecraft.util.Mth.hsvToRgb((float) (b == null ? 0 : b.fraction(def)) / 3f, 1f, 1f);
    }
}
