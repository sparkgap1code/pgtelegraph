package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.media.Pic;
import com.sparkgap.pgtelegraph.media.PicNet;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;

/**
 * The hand camera takes the picture the player actually sees: right after the world is drawn
 * (before the hand and the HUD go on top) the frame is read back, the middle cut to 5:4 and
 * averaged down to 320 × 256, then deflated and sent up in pieces.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT)
public final class ClientCamera {
    private ClientCamera() {}

    private static boolean pending;

    public static void shoot() {
        Player p = Minecraft.getInstance().player;
        if (p == null || pending) return;
        if (!p.getAbilities().instabuild && !p.getInventory().contains(new net.minecraft.world.item.ItemStack(Items.PAPER))) {
            p.displayClientMessage(Component.translatable("pgtelegraph.camera.no_paper").withStyle(ChatFormatting.RED), true);
            return;
        }
        pending = true;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (!pending || e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        pending = false;
        Minecraft mc = Minecraft.getInstance();
        byte[] img;
        try (NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            img = shrink(shot);
        } catch (RuntimeException ex) {
            return;
        }
        Util.backgroundExecutor().execute(() -> {
            byte[] z = Pic.deflate(img);
            mc.execute(() -> send(z));
        });
    }

    private static void send(byte[] z) {
        int total = (z.length + PicNet.CHUNK - 1) / PicNet.CHUNK;
        if (total > PicNet.MAX_CHUNKS) return;
        for (int i = 0; i < total; i++) {
            PacketDistributor.sendToServer(new PicNet.Upload(i, total,
                    Arrays.copyOfRange(z, i * PicNet.CHUNK, Math.min(z.length, (i + 1) * PicNet.CHUNK))));
        }
    }

    /** cut the middle to 5:4 and box-average down to the frame */
    private static byte[] shrink(NativeImage s) {
        int sw = s.getWidth(), sh = s.getHeight();
        int cw = sw, ch = sh;
        if (sw * Pic.H > sh * Pic.W) cw = sh * Pic.W / Pic.H;
        else ch = sw * Pic.H / Pic.W;
        int ox = (sw - cw) / 2, oy = (sh - ch) / 2;
        byte[] out = new byte[Pic.BYTES];
        for (int y = 0; y < Pic.H; y++) {
            int y0 = oy + y * ch / Pic.H, y1 = Math.max(y0 + 1, oy + (y + 1) * ch / Pic.H);
            for (int x = 0; x < Pic.W; x++) {
                int x0 = ox + x * cw / Pic.W, x1 = Math.max(x0 + 1, ox + (x + 1) * cw / Pic.W);
                long r = 0, g = 0, b = 0;
                int n = 0;
                for (int yy = y0; yy < y1; yy++) {
                    for (int xx = x0; xx < x1; xx++) {
                        int c = s.getPixelRGBA(xx, yy); // ABGR
                        r += c & 255;
                        g += (c >> 8) & 255;
                        b += (c >> 16) & 255;
                        n++;
                    }
                }
                Pic.put(out, y * Pic.W + x, (int) (r / n), (int) (g / n), (int) (b / n));
            }
        }
        return out;
    }
}
