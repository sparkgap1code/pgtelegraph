package com.sparkgap.pgtelegraph.client;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.TelegraphInput;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Straight key in the world: holding right-click (empty hand, not sneaking) on it keys it.
 * Sends press, release, and a keep-alive every few ticks while held.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT)
public final class KeyHoldHandler {
    private static final int KEEPALIVE_TICKS = 4;

    private static BlockPos held;
    private static int sinceKeepAlive;

    private KeyHoldHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos target = null;

        if (mc.player != null && mc.level != null && mc.screen == null
                && mc.options.keyUse.isDown()
                && !mc.player.isShiftKeyDown()
                && mc.player.getMainHandItem().isEmpty()
                && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
            if (mc.level.getBlockState(pos).is(PgTelegraph.TELEGRAPH_KEY.get())) {
                target = pos;
            }
        }

        if (held != null && !held.equals(target)) {
            ClientAccess.send(held, TelegraphInput.STRAIGHT, false);
            held = null;
        }
        if (target != null) {
            if (held == null) {
                held = target;
                ClientAccess.send(target, TelegraphInput.STRAIGHT, true);
                sinceKeepAlive = 0;
            } else if (++sinceKeepAlive >= KEEPALIVE_TICKS) {
                ClientAccess.send(target, TelegraphInput.STRAIGHT, true);
                sinceKeepAlive = 0;
            }
        }
    }
}
