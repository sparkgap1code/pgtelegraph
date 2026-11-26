package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sparkgap.pgtelegraph.block.DeviceBlock;
import com.sparkgap.pgtelegraph.media.Pic;
import com.sparkgap.pgtelegraph.media.PicNet;
import com.sparkgap.pgtelegraph.sat.SstvMonitorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The SSTV receiver shows its picture on its own screen, in the world: the picture tube glows with
 * what has been painted so far, line by line as it arrives, so you can see from across the room
 * that a picture is coming in. A monitor seen for the first time asks the server for the picture
 * it already holds.
 */
public class SstvMonitorRenderer implements BlockEntityRenderer<SstvMonitorBlockEntity> {
    /** the screen in model pixels (NORTH-authored), the 320 x 256 picture fitted 5:4 inside it */
    private static final float X0 = 3.5f / 16, X1 = 11.5f / 16, Y0 = 3.3f / 16, Y1 = 9.7f / 16, Z = 2.38f / 16;
    private static final int FULL_BRIGHT = 15728880;

    private static final class Screen {
        final int w, h;
        final DynamicTexture tex;
        final ResourceLocation id;

        Screen(int w, int h) {
            this.w = w;
            this.h = h;
            tex = new DynamicTexture(w, h, false);
            id = Minecraft.getInstance().getTextureManager().register("pgtelegraph_sstv", tex);
        }

        int version = -1;
        long seen;
    }

    private static final Map<BlockPos, Screen> SCREENS = new HashMap<>();
    private static final Set<BlockPos> ASKED = new HashSet<>();

    public SstvMonitorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(SstvMonitorBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        PowerLedRenderer.draw(be, be.lamp(), ps, buffers);
        BlockPos pos = be.getBlockPos().immutable();
        if (be.version == 0 && ASKED.add(pos)) PicNetClient.want(pos); // what was painted before we came
        if (!hasPicture(be)) return;
        Screen sc = SCREENS.get(pos);
        if (sc == null || sc.w != be.imgW || sc.h != be.imgH) {
            if (sc != null) Minecraft.getInstance().getTextureManager().release(sc.id);
            sc = new Screen(be.imgW, be.imgH);
            SCREENS.put(pos, sc);
        }
        sc.seen = System.currentTimeMillis();
        if (sc.version != be.version) {
            sc.version = be.version;
            upload(sc, be.image);
        }
        Direction facing = be.getBlockState().hasProperty(DeviceBlock.FACING) ? be.getBlockState().getValue(DeviceBlock.FACING) : Direction.NORTH;
        float angle = switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-angle));
        ps.translate(-0.5, 0, -0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(sc.id));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        // seen from the front (north), the picture's left edge is at +x
        vertex(vc, pose, m, X1, Y1, 0, 0);
        vertex(vc, pose, m, X1, Y0, 0, 1);
        vertex(vc, pose, m, X0, Y0, 1, 1);
        vertex(vc, pose, m, X0, Y1, 1, 0);
        ps.popPose();
        if (SCREENS.size() > 32) prune();
    }

    private static boolean hasPicture(SstvMonitorBlockEntity be) {
        if (be.row >= 0) return true;
        for (int i = 0; i < be.image.length; i += 97) if (be.image[i] != 0) return true;
        return false;
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float x, float y, float u, float v) {
        vc.addVertex(m, x, y, Z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, 0, 0, -1);
    }

    private static void upload(Screen sc, byte[] img) {
        NativeImage px = sc.tex.getPixels();
        if (px == null) return;
        for (int y = 0; y < sc.h; y++)
            for (int x = 0; x < sc.w; x++) {
                int c = img.length < (y * sc.w + x + 1) * 2 ? 0 : Pic.rgb(img, y * sc.w + x);
                px.setPixelRGBA(x, y, 0xFF000000 | (c & 255) << 16 | (c & 0xFF00) | (c >> 16 & 255)); // ABGR
            }
        sc.tex.upload();
    }

    /** drop textures of monitors not drawn for a while */
    private static void prune() {
        long now = System.currentTimeMillis();
        SCREENS.entrySet().removeIf(e -> {
            if (now - e.getValue().seen < 10_000) return false;
            Minecraft.getInstance().getTextureManager().release(e.getValue().id);
            ASKED.remove(e.getKey());
            return true;
        });
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    /** keeps the network call out of the renderer's imports */
    static final class PicNetClient {
        static void want(BlockPos pos) {
            if (Minecraft.getInstance().getConnection() != null)
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new PicNet.MonitorWant(pos));
        }
    }
}
