package com.sparkgap.pgtelegraph.client;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.BuzzerBlockEntity;
import com.sparkgap.pgtelegraph.block.KeyerBlockEntity;
import com.sparkgap.pgtelegraph.block.PaddleBlockEntity;
import com.sparkgap.pgtelegraph.block.RelayBlockEntity;
import com.sparkgap.pgtelegraph.block.SounderBlockEntity;
import com.sparkgap.pgtelegraph.block.Spec;
import com.sparkgap.pgtelegraph.block.TelegraphKeyBlockEntity;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Shift tooltip: Create's summary (from lang "block.pgtelegraph.X.tooltip.*") followed by the
 * device's nameplate ratings taken straight from its {@link Spec}.
 */
public class RatingsTooltip implements TooltipModifier {
    private final Spec spec;

    private RatingsTooltip(Spec spec) {
        this.spec = spec;
    }

    public static void registerAll() {
        register(PgTelegraph.TELEGRAPH_KEY_ITEM.get(), TelegraphKeyBlockEntity.SPEC);
        register(PgTelegraph.PADDLE_ITEM.get(), PaddleBlockEntity.SPEC);
        register(PgTelegraph.KEYER_ITEM.get(), KeyerBlockEntity.SPEC);
        register(PgTelegraph.KEYER_PADDLE_ITEM.get(), com.sparkgap.pgtelegraph.block.KeyerPaddleBlockEntity.SPEC);
        describe(PgTelegraph.CLIP_KEY.get());
        describe(PgTelegraph.CLIP_PADDLE.get());
        register(PgTelegraph.SOUNDER_ITEM.get(), SounderBlockEntity.SPEC);
        register(PgTelegraph.RELAY_ITEM.get(), RelayBlockEntity.SPEC);
        register(PgTelegraph.BUZZER_ITEM.get(), BuzzerBlockEntity.SPEC);
        register(PgTelegraph.INKER_ITEM.get(), com.sparkgap.pgtelegraph.block.InkerBlockEntity.SPEC);
        register(PgTelegraph.TAPE_TRANSMITTER_ITEM.get(), com.sparkgap.pgtelegraph.block.TapeTransmitterBlockEntity.SPEC);
        describe(PgTelegraph.PERFORATOR_ITEM.get());
        register(PgTelegraph.SWITCHBOARD_ITEM.get(), com.sparkgap.pgtelegraph.block.SwitchboardBlockEntity.SPEC);
        describe(PgTelegraph.PAPER_TAPE.get());
        register(PgTelegraph.TRANSMITTER_ITEM.get(), com.sparkgap.pgtelegraph.block.TransmitterBlockEntity.SPEC);
        register(PgTelegraph.RECEIVER_ITEM.get(), com.sparkgap.pgtelegraph.block.ReceiverBlockEntity.SPEC);
        describe(PgTelegraph.ANTENNA_ROD_ITEM.get());
        describe(PgTelegraph.SHORT_ANTENNA_ITEM.get());
        describe(PgTelegraph.COAX_THIN_ITEM.get());
        describe(PgTelegraph.COAX_THICK_ITEM.get());
        describe(PgTelegraph.COAX_HARDLINE_ITEM.get());
        describe(PgTelegraph.COAX_HEAVY_ITEM.get());
        describe(PgTelegraph.COAX_RIGID_ITEM.get());
        register(PgTelegraph.BROADCAST_AMPLIFIER_ITEM.get(), com.sparkgap.pgtelegraph.block.AmpTier.BROADCAST.spec());
        describe(PgTelegraph.LOADING_COIL_ITEM.get());
        describe(PgTelegraph.LITZ_LOADING_COIL_ITEM.get());
        describe(PgTelegraph.TOP_LOAD_WIRE_ITEM.get());
        describe(PgTelegraph.GROUND_MAT_ITEM.get());
        describe(PgTelegraph.ALTERNATOR_ITEM.get());
        describe(PgTelegraph.VHF_WHIP_ITEM.get());
        describe(PgTelegraph.YAGI_ANTENNA_ITEM.get());
        describe(PgTelegraph.DISH_ANTENNA_ITEM.get());
        describe(PgTelegraph.HELICAL_WHIP_ITEM.get());
        describe(PgTelegraph.RECEIVING_LOOP_ITEM.get());
        describe(PgTelegraph.HELIX_ANTENNA_ITEM.get());
        describe(PgTelegraph.HANDHELD_RADIO.get());
        describe(PgTelegraph.SCREWDRIVER.get());
        describe(PgTelegraph.EXPLODED_RADIO.get());
        register(PgTelegraph.HANDHELD_CHARGER_ITEM.get(), com.sparkgap.pgtelegraph.block.HandheldChargerBlockEntity.SPEC);
        register(PgTelegraph.LINEAR_AMPLIFIER_ITEM.get(), com.sparkgap.pgtelegraph.block.LinearAmplifierBlockEntity.SPEC);
        // batch 2
        describe(PgTelegraph.DUMMY_LOAD.get().asItem());
        describe(PgTelegraph.ANTENNA_TUNER.get().asItem());
        register(PgTelegraph.BEACON.get().asItem(), com.sparkgap.pgtelegraph.block.TransmitterBlockEntity.SPEC);
        describe(PgTelegraph.SOLAR_BEACON.get().asItem());
        register(PgTelegraph.SPARK_TRANSMITTER.get().asItem(), com.sparkgap.pgtelegraph.block.SparkTransmitterBlockEntity.SPARK_SPEC);
        register(PgTelegraph.REPEATER.get().asItem(), com.sparkgap.pgtelegraph.block.TransmitterBlockEntity.SPEC);
        register(PgTelegraph.OSCILLOSCOPE.get().asItem(), com.sparkgap.pgtelegraph.block.OscilloscopeBlockEntity.SPEC);
        register(PgTelegraph.BMS.get().asItem(), com.sparkgap.pgtelegraph.block.BmsBlockEntity.SPEC);
        register(PgTelegraph.DECODER.get().asItem(), com.sparkgap.pgtelegraph.block.DecoderBlockEntity.SPEC);
        register(PgTelegraph.TELEPRINTER.get().asItem(), com.sparkgap.pgtelegraph.block.TeleprinterBlockEntity.SPEC);
        describe(PgTelegraph.TELEGRAPH_POLE.get().asItem());
        describe(PgTelegraph.GLASS_INSULATOR.get().asItem());
        describe(PgTelegraph.WOODEN_PEG.get().asItem());
        describe(PgTelegraph.LIGHTNING_ARRESTER.get().asItem());
        describe(PgTelegraph.ROTATOR.get().asItem());
        describe(PgTelegraph.LAUNCH_PAD.get().asItem());
        register(PgTelegraph.IONOSONDE.get().asItem(), com.sparkgap.pgtelegraph.block.IonosondeBlockEntity.SPEC);
        register(PgTelegraph.QSO_ROBOT.get().asItem(), com.sparkgap.pgtelegraph.block.QsoRobotBlockEntity.SPEC);
        describe(PgTelegraph.TAPE_RECORDER.get().asItem());
        describe(PgTelegraph.COAX_SWITCH.get().asItem());
        describe(PgTelegraph.PHASED_ARRAY.get().asItem());
        register(PgTelegraph.SSTV_MONITOR.get().asItem(), com.sparkgap.pgtelegraph.sat.SstvMonitorBlockEntity.SPEC);
        register(PgTelegraph.BROADCAST_TX.get().asItem(), com.sparkgap.pgtelegraph.broadcast.BroadcastTransmitterBlockEntity.BC_SPEC);
        register(PgTelegraph.BROADCAST_RADIO.get().asItem(), com.sparkgap.pgtelegraph.broadcast.BroadcastRadioBlockEntity.SPEC);
        register(PgTelegraph.SSTV_TX.get().asItem(), com.sparkgap.pgtelegraph.block.TransmitterBlockEntity.SPEC);
        describe(PgTelegraph.MICROPHONE.get().asItem());
        describe(PgTelegraph.TV_CAMERA.get().asItem());
        describe(PgTelegraph.CAMERA.get());
        // newer blocks and items
        register(PgTelegraph.FM_BROADCAST_TX.get().asItem(), com.sparkgap.pgtelegraph.broadcast.BroadcastTransmitterBlockEntity.BC_SPEC);
        register(PgTelegraph.TV_TX.get().asItem(), com.sparkgap.pgtelegraph.media.TvTransmitterBlockEntity.TV_SPEC);
        register(PgTelegraph.TV_RECEIVER.get().asItem(), com.sparkgap.pgtelegraph.media.TvReceiverBlockEntity.SPEC);
        register(PgTelegraph.AUDIO_AMP.get().asItem(), com.sparkgap.pgtelegraph.block.AudioAmpBlockEntity.SPEC);
        register(PgTelegraph.POWER_SUPPLY.get().asItem(), com.sparkgap.pgtelegraph.block.PowerSupplyBlockEntity.SPEC);
        register(PgTelegraph.UPS.get().asItem(), com.sparkgap.pgtelegraph.block.PowerSupplyBlockEntity.SPEC);
        register(PgTelegraph.POWER_PANEL.get().asItem(), com.sparkgap.pgtelegraph.block.PowerPanelBlockEntity.SPEC);
        register(PgTelegraph.SELECTOR.get().asItem(), com.sparkgap.pgtelegraph.block.SelectorBlockEntity.SPEC);
        register(PgTelegraph.APRS_STATION.get().asItem(), com.sparkgap.pgtelegraph.block.AprsStationBlockEntity.SPEC);
        register(PgTelegraph.FIELD_PHONE.get().asItem(), com.sparkgap.pgtelegraph.block.FieldPhoneBlockEntity.SPEC);
        register(PgTelegraph.DUPLEX_SET.get().asItem(), com.sparkgap.pgtelegraph.block.DuplexSetBlockEntity.SPEC);
        register(PgTelegraph.LINE_TEST_SET.get().asItem(), com.sparkgap.pgtelegraph.block.LineTestSetBlockEntity.SPEC);
        register(PgTelegraph.SUBMARINE_CABLE.get().asItem(), com.sparkgap.pgtelegraph.block.SubmarineCableBlockEntity.SPEC);
        register(PgTelegraph.MIRROR_GALVANOMETER.get().asItem(), com.sparkgap.pgtelegraph.block.MirrorGalvanometerBlockEntity.SPEC);
        register(PgTelegraph.TELEGRAPH_REPEATER.get().asItem(), com.sparkgap.pgtelegraph.block.TelegraphRepeaterBlockEntity.SPEC);
        register(PgTelegraph.DTMF_DECODER.get().asItem(), com.sparkgap.pgtelegraph.block.DtmfDecoderBlockEntity.SPEC);
        register(PgTelegraph.SPEAKER.get().asItem(), com.sparkgap.pgtelegraph.block.SpeakerBlockEntity.SPEC);
        register(PgTelegraph.HORN_SPEAKER.get().asItem(), com.sparkgap.pgtelegraph.block.SpeakerBlockEntity.SPEC);
        register(PgTelegraph.JAMMER.get().asItem(), com.sparkgap.pgtelegraph.block.JammerBlockEntity.JAM_SPEC);
        describe(PgTelegraph.VCR.get().asItem());
        describe(PgTelegraph.VIDEO_TAPE.get());
        describe(PgTelegraph.CAMCORDER.get());
        describe(PgTelegraph.BODYCAM.get());
        describe(PgTelegraph.FPV_GOGGLES.get());
        describe(PgTelegraph.MANPACK.get());
        describe(PgTelegraph.LNA.get().asItem());
        describe(PgTelegraph.REMOTE_TUNER.get().asItem());
        describe(PgTelegraph.MANUAL_TUNER.get().asItem());
    }

    /** Summary only (no electrical ratings). */
    private static void describe(Item item) {
        TooltipModifier.REGISTRY.register(item, new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                .andThen(RatingsTooltip::supply));
    }

    /**
     * "Supply" lines (lang pgtelegraph.supply.<id>.0, .1, ...), always shown: what to wire to the
     * terminals, so nobody has to guess the voltage.
     */
    private static void supply(ItemTooltipEvent event) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        String base = "pgtelegraph.supply." + id.getPath() + ".";
        if (!net.minecraft.client.resources.language.I18n.exists(base + "0")) return;
        List<Component> tip = event.getToolTip();
        tip.add(Component.translatable("pgtelegraph.supply").withStyle(ChatFormatting.AQUA));
        for (int i = 0; i < 6 && net.minecraft.client.resources.language.I18n.exists(base + i); i++) {
            tip.add(Component.literal(" ").append(Component.translatable(base + i)).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void register(Item item, Spec spec) {
        TooltipModifier.REGISTRY.register(item,
                new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                        .andThen(RatingsTooltip::supply)
                        .andThen(new RatingsTooltip(spec)));
    }

    @Override
    public void modify(ItemTooltipEvent event) {
        if (!Screen.hasShiftDown()) return;
        List<Component> tip = event.getToolTip();
        tip.add(Component.empty());
        tip.add(Component.translatable("pgtelegraph.tooltip.ratings").withStyle(ChatFormatting.GRAY));
        tip.add(line("max_temp", Component.literal(num(spec.maxTempC()) + " °C")));
        tip.add(line("max_power", Component.literal(si(spec.maxPowerW(), "W"))));
        tip.add(line("max_volts", Component.literal(si(spec.maxVolts(), "V"))));
        for (Spec.Part p : spec.parts()) {
            MutableComponent v;
            if (p.kind().equals("plate")) {
                v = Component.literal(si(p.value(), "V"));
            } else if (p.kind().equals("rf_out")) {
                v = Component.literal(si(p.value(), "W"));
            } else if (p.kind().equals("rail")) {
                v = Component.literal(si(p.value(), "V"));
            } else {
                v = Component.literal(si(p.value(), "Ω"));
                if (p.maxAmps() > 0) {
                    v.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.translatable("pgtelegraph.tooltip.max", si(p.maxAmps(), "A")));
                }
            }
            if (p.count() > 1) v.append(Component.literal(" ×" + p.count()).withStyle(ChatFormatting.DARK_GRAY));
            tip.add(line("part." + p.kind(), v));
        }
        tip.add(Component.translatable("pgtelegraph.tooltip.failure").withStyle(ChatFormatting.DARK_RED));
    }

    private static Component line(String key, MutableComponent value) {
        return Component.literal(" ").append(Component.translatable("pgtelegraph.tooltip." + key)
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(value.withStyle(ChatFormatting.GOLD));
    }

    private static String si(double v, String unit) {
        double a = Math.abs(v);
        if (a >= 1e6) return num(v / 1e6) + " M" + unit;
        if (a >= 1e3) return num(v / 1e3) + " k" + unit;
        if (a > 0 && a < 1) return num(v * 1e3) + " m" + unit;
        return num(v) + " " + unit;
    }

    private static String num(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
