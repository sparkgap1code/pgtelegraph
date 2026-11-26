package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.media.FpvGogglesItem;
import com.sparkgap.pgtelegraph.media.TvNet;
import com.sparkgap.pgtelegraph.media.TvReceiverBlockEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * FPV goggles on the client: a TV set with no block (its picture and what the signal does to it),
 * drawn over the whole view - letterboxed 4:3, through the same analog tube as a TV set.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class FpvClient {
    private FpvClient() {}

    private static TvReceiverBlockEntity set;

    /** the goggles' TV set (made on first use) */
    public static TvReceiverBlockEntity set() {
        if (set == null) set = new TvReceiverBlockEntity(PgTelegraph.TV_RECEIVER_BE.get(), FpvGogglesItem.GOGGLE_POS,
                PgTelegraph.TV_RECEIVER.get().defaultBlockState());
        set.alwaysOn = true; // the goggles' own battery
        return set;
    }

    static void state(TvNet.Goggles m) {
        TvReceiverBlockEntity s = set();
        s.tuned = m.tuned();
        s.snr = m.snr();
        s.ghost = m.ghost();
        s.interf = m.interf();
        s.interfDf = m.interfDf();
    }

    @SubscribeEvent
    public static void onLayers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "fpv_goggles"), FpvClient::draw);
    }

    private static void draw(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || FpvGogglesItem.worn(mc.player) == null || mc.options.hideGui && mc.screen != null) return;
        int sw = g.guiWidth(), sh = g.guiHeight();
        g.fill(0, 0, sw, sh, 0xFF000000);
        int h = sh, w = h * 4 / 3;
        if (w > sw) {
            w = sw;
            h = w * 3 / 4;
        }
        ResourceLocation id = TvRenderer.picture(set());
        g.blit(id, (sw - w) / 2, (sh - h) / 2, w, h, 0f, 0f, TvRenderer.W, TvRenderer.H, TvRenderer.W, TvRenderer.H);
    }
}
