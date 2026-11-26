package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.item.ClipKeyItem;
import com.sparkgap.pgtelegraph.network.PaddleInputPayload;
import com.sparkgap.pgtelegraph.radio.HandheldData;
import com.sparkgap.pgtelegraph.radio.HandheldRadioItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Paddle keying on hand-held items (handheld radio in paddle mode, clip-lead paddle):
 * left mouse = dit lever, right mouse = dah lever, sneak + wheel = keyer speed.
 * While such an item is in the main hand the clicks are swallowed (no punching, no block
 * breaking, no item use); sneaking gives the buttons back (panel, clipping leads).
 */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT)
public final class PaddleClient {
    private static final int KEEPALIVE_TICKS = 4;

    private static boolean sentDit, sentDah;
    private static int sinceKeepAlive;

    private PaddleClient() {}

    static boolean isPaddle(ItemStack s) {
        if (s.getItem() instanceof HandheldRadioItem) {
            HandheldData d = HandheldRadioItem.data(s);
            return d.on() && d.paddle();
        }
        return s.getItem() instanceof ClipKeyItem k && k.paddle;
    }

    private static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.screen == null && !mc.player.isShiftKeyDown() && isPaddle(mc.player.getMainHandItem());
    }

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered e) {
        if (!active() || !(e.isAttack() || e.isUseItem())) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.player.isShiftKeyDown() || !isPaddle(mc.player.getMainHandItem())) return;
        if (e.getScrollDeltaY() == 0) return;
        e.setCanceled(true);
        PacketDistributor.sendToServer(new PaddleInputPayload(false, false, e.getScrollDeltaY() > 0 ? 1 : -1));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            sentDit = sentDah = false;
            return;
        }
        boolean dit = false, dah = false;
        if (active()) {
            dit = mc.options.keyAttack.isDown();
            dah = mc.options.keyUse.isDown();
            if (mc.gameMode != null && mc.gameMode.isDestroying()) mc.gameMode.stopDestroyBlock();
        }
        boolean changed = dit != sentDit || dah != sentDah;
        boolean keepAlive = (dit || dah) && ++sinceKeepAlive >= KEEPALIVE_TICKS;
        if (changed || keepAlive) {
            PacketDistributor.sendToServer(new PaddleInputPayload(dit, dah, 0));
            sentDit = dit;
            sentDah = dah;
            sinceKeepAlive = 0;
        }
    }
}
