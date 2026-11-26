package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.network.HandheldStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** The handheld's speaker: a simulated CW receiver (BFO, IF filter, AGC), sidetone while keying, squelch. */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT)
public final class HandheldClient {
    private HandheldClient() {}

    static volatile HandheldStatePayload state = new HandheldStatePayload(false, false, 700, 0, 0);
    /** what the handheld hears (sent by the server), played through the simulated receiver */
    static final com.sparkgap.pgtelegraph.radio.RxChannel channel = new com.sparkgap.pgtelegraph.radio.RxChannel();
    private static ClientRadio.RxSound speaker;

    static void update(HandheldStatePayload s) {
        state = s;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !state.on()) return;
        if (speaker == null || !speaker.playing()) {
            if (speaker != null) speaker.drop();
            speaker = new ClientRadio.RxSound(channel, BlockPos.ZERO, true,
                    () -> state.on() && Minecraft.getInstance().player != null, 0.7f);
            mc.getSoundManager().play(speaker);
        }
    }
}
