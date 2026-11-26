package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only mod-bus wiring; only referenced when running on the client. */
public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new TelegraphPonder());
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(RatingsTooltip::registerAll));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) -> e.register(ClientVoice.PTT));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) ->
                e.register(PgTelegraph.PARTS_MENU.get(), RadioPartsScreen::new));
        modBus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional e) -> e.register(ManpackLayer.MODEL));
        modBus.addListener((EntityRenderersEvent.AddLayers e) -> {
            for (net.minecraft.client.resources.PlayerSkin.Model skin : e.getSkins()) {
                if (e.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer r) r.addLayer(new ManpackLayer(r));
            }
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(PgTelegraph.SWITCHBOARD_BE.get(), SwitchboardRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.TAPE_TRANSMITTER_BE.get(), TapeTransmitterRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.SSTV_MONITOR_BE.get(), SstvMonitorRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.TV_RECEIVER_BE.get(), TvRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.MANUAL_TUNER_BE.get(), TunerMeterRenderer::new);
            // power lamps
            e.registerBlockEntityRenderer(PgTelegraph.RECEIVER_BE.get(), PowerLedRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.CREATIVE_RECEIVER_BE.get(), PowerLedRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.POWER_SUPPLY_BE.get(), PowerLedRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.UPS_BE.get(), PowerLedRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.POWER_PANEL_BE.get(), PowerLedRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.APRS_STATION_BE.get(), AprsKeyRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.MIRROR_GALVANOMETER_BE.get(), GalvoRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.MOBILE_RADIO_BE.get(), AprsKeyRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.DTMF_DECODER_BE.get(), PowerLedRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.IONOSONDE_BE.get(), PowerLedRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.QSO_ROBOT_BE.get(), PowerLedRenderer::new);
            e.registerBlockEntityRenderer(PgTelegraph.BROADCAST_RADIO_BE.get(), PowerLedRenderer::new);
        });
    }
}
