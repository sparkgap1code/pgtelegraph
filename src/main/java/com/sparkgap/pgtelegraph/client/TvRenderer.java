package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sparkgap.pgtelegraph.block.DeviceBlock;
import com.sparkgap.pgtelegraph.media.TvFrames;
import com.sparkgap.pgtelegraph.media.TvReceiverBlockEntity;
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
import java.util.Map;
import java.util.SplittableRandom;

/**
 * The analog TV set's picture tube. The last frame received is shown through what an analog set
 * does with a poor signal, all worked out from the signal-to-noise ratio and the path the server
 * reported:
 * <ul>
 *   <li>snow: noise on the brightness, from about 40 dB down; below about 5 dB nothing but snow</li>
 *   <li>colour fades out first (the colour burst is lost below about 30 dB)</li>
 *   <li>ghosts: a fainter copy a little to the right, from paths bent round hills or bounced off the sky</li>
 *   <li>weak sync: lines tear sideways below about 18 dB, and below about 9 dB the picture rolls</li>
 *   <li>a second station on the channel: moving venetian-blind bars</li>
 * </ul>
 */
public class TvRenderer implements BlockEntityRenderer<TvReceiverBlockEntity> {
    private static final float X0 = 3.5f / 16, X1 = 11.5f / 16, Y0 = 3.5f / 16, Y1 = 9.5f / 16, Z = 2.38f / 16;
    private static final int FULL_BRIGHT = 15728880;
    static final int W = TvFrames.W, H = TvFrames.H;

    static final class Tube {
        final DynamicTexture tex = new DynamicTexture(W, H, false);
        final ResourceLocation id = Minecraft.getInstance().getTextureManager().register("pgtelegraph_tv", tex);
        final SplittableRandom rnd = new SplittableRandom();
        long lastDraw, seen;
        double roll, jitter;
    }

    private static final Map<BlockPos, Tube> TUBES = new HashMap<>();
    private static final float[] GAUSS = new float[1 << 16];

    static {
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < GAUSS.length; i++) GAUSS[i] = (float) r.nextGaussian();
    }

    public TvRenderer(BlockEntityRendererProvider.Context ctx) {}

    /** the set's current picture (shared with the full-screen viewer) */
    static ResourceLocation picture(TvReceiverBlockEntity be) {
        Tube t = TUBES.computeIfAbsent(be.getBlockPos().immutable(), p -> new Tube());
        t.seen = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        double dist = Minecraft.getInstance().player == null ? 0 : Math.sqrt(Minecraft.getInstance().player.distanceToSqr(be.getBlockPos().getCenter()));
        boolean goggles = be.getBlockPos().equals(com.sparkgap.pgtelegraph.media.FpvGogglesItem.GOGGLE_POS);
        long period = goggles ? 33 : Minecraft.getInstance().screen instanceof TvScreen ? 60 : dist < 16 ? 90 : 250;
        if (now - t.lastDraw >= period) {
            t.lastDraw = now;
            draw(t, be);
        }
        return t.id;
    }

    @Override
    public void render(TvReceiverBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        PowerLedRenderer.draw(be, be.lamp(), ps, buffers);
        if (!be.isOn() || !be.isPowered()) return;
        ResourceLocation id = picture(be);
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
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(id));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        vertex(vc, pose, m, X1, Y1, 0, 0);
        vertex(vc, pose, m, X1, Y0, 0, 1);
        vertex(vc, pose, m, X0, Y0, 1, 1);
        vertex(vc, pose, m, X0, Y1, 1, 0);
        ps.popPose();
        if (TUBES.size() > 16) prune();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float x, float y, float u, float v) {
        vc.addVertex(m, x, y, Z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, 0, 0, -1);
    }

    /** one field of the tube: the received picture through the analog impairments */
    private static void draw(Tube t, TvReceiverBlockEntity be) {
        NativeImage px = t.tex.getPixels();
        if (px == null) return;
        int[] pic = be.picture;
        boolean signal = be.tuned && pic != null && be.snr > 5;
        double snr = be.snr;
        // brightness noise (snow) and colour
        double sigma = signal ? Math.min(160, 255 * 1.4 * Math.pow(10, -snr / 20)) : 110;
        double sat = signal ? Math.max(0, Math.min(1, (snr - 22) / 10)) : 0;
        double ghostA = signal ? be.ghost * 0.55 : 0;
        int ghostShift = (int) (W * (0.012 + be.ghost * 0.05));
        // sync
        double tear = signal ? Math.max(0, 18 - snr) * 0.9 : 0;
        if (signal && snr < 9) t.roll = (t.roll + (9 - snr) * 3) % H;
        else t.roll *= 0.8;
        double bars = signal && be.interf > -40 ? Math.pow(10, be.interf / 20) * 140 : 0;
        double barPhase = System.currentTimeMillis() / 1000.0 * (0.3 + Math.abs(be.interfDf) % 1000 / 800.0);
        int pw = be.picW, ph = be.picH;
        for (int y = 0; y < H; y++) {
            if (tear > 0) t.jitter = t.jitter * 0.85 + t.rnd.nextGaussian() * tear * 0.6;
            else t.jitter = 0;
            int sy = (int) ((y + t.roll) % H);
            double bar = bars > 0 ? bars * Math.sin(2 * Math.PI * (y / (double) H * 7 + barPhase)) : 0;
            for (int x = 0; x < W; x++) {
                double r, g, b;
                if (signal) {
                    int sx = Math.floorMod((int) (x + t.jitter), W);
                    int c = sample(pic, pw, ph, sx, sy);
                    r = c >> 16 & 255;
                    g = c >> 8 & 255;
                    b = c & 255;
                    if (ghostA > 0) {
                        int gc = sample(pic, pw, ph, Math.floorMod(sx - ghostShift, W), sy);
                        r = r * (1 - ghostA) + (gc >> 16 & 255) * ghostA;
                        g = g * (1 - ghostA) + (gc >> 8 & 255) * ghostA;
                        b = b * (1 - ghostA) + (gc & 255) * ghostA;
                    }
                    double yl = 0.299 * r + 0.587 * g + 0.114 * b;
                    r = yl + (r - yl) * sat;
                    g = yl + (g - yl) * sat;
                    b = yl + (b - yl) * sat;
                } else r = g = b = 90;
                double n = sigma > 0.5 ? GAUSS[t.rnd.nextInt(GAUSS.length)] * sigma : 0;
                r += n + bar;
                g += n + bar;
                b += n + bar;
                int ir = clamp(r), ig = clamp(g), ib = clamp(b);
                px.setPixelRGBA(x, y, 0xFF000000 | ib << 16 | ig << 8 | ir); // ABGR
            }
        }
        t.tex.upload();
    }

    private static int sample(int[] pic, int pw, int ph, int x, int y) {
        if (pw <= 0 || ph <= 0) return 0;
        int sx = x * pw / W, sy = y * ph / H;
        return pic[2 + sy * pw + sx];
    }

    private static int clamp(double v) {
        return v < 0 ? 0 : v > 255 ? 255 : (int) v;
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        TUBES.entrySet().removeIf(e -> {
            if (now - e.getValue().seen < 10_000) return false;
            Minecraft.getInstance().getTextureManager().release(e.getValue().id);
            return true;
        });
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
