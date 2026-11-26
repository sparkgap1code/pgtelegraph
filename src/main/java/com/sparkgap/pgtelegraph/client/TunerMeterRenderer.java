package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sparkgap.pgtelegraph.block.ManualTunerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * The manual tuner's SWR meter on the lid: a scale in |Γ| (1:1 at the left, 1.5 / 2 / 3 marked, red past
 * 3:1, ∞ at the right) and a needle that swings with the SWR the rig sees at its own frequency.
 */
public class TunerMeterRenderer implements BlockEntityRenderer<ManualTunerBlockEntity> {
    private static final float PX = 8, PY = 8.56f, PZ = 5.2f, R = 2.4f, HALF = 50;

    public TunerMeterRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static float angle(double g) {
        return (float) Math.toRadians(-HALF + 2 * HALF * g);
    }

    @Override
    public void render(ManualTunerBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        // scale marks at 1, 1.5, 2, 3 and ∞
        for (double s : new double[]{1, 1.5, 2, 3, 1e9}) {
            float a = angle((s - 1) / (s + 1));
            boolean red = s > 3;
            line(vc, pose, a, R * 0.85f, R * 1.05f, red ? 0.8f : 0.1f, 0.1f, 0.1f);
        }
        // red arc past 3:1
        for (int i = 0; i < 6; i++) {
            float a0 = angle(0.5 + 0.5 * i / 6), a1 = angle(0.5 + 0.5 * (i + 1) / 6);
            seg(vc, pose, PX + R * 1.05f * Mth.sin(a0), PZ - R * 1.05f * Mth.cos(a0),
                    PX + R * 1.05f * Mth.sin(a1), PZ - R * 1.05f * Mth.cos(a1), 0.8f, 0.1f, 0.1f);
        }
        if (be.rigSwr() >= 1 || be.needle > 0.005f) {
            float a = angle(Mth.lerp(pt, be.needleO, be.needle));
            line(vc, pose, a, 0, R, 0.05f, 0.05f, 0.05f);
        }
        pose.popPose();
    }

    private static void line(VertexConsumer vc, PoseStack pose, float a, float r0, float r1, float r, float g, float b) {
        float s = Mth.sin(a), c = Mth.cos(a);
        seg(vc, pose, PX + r0 * s, PZ - r0 * c, PX + r1 * s, PZ - r1 * c, r, g, b);
    }

    private static void seg(VertexConsumer vc, PoseStack pose, float x1, float z1, float x2, float z2, float r, float g, float b) {
        float nx = x2 - x1, nz = z2 - z1, len = (float) Math.sqrt(nx * nx + nz * nz);
        if (len < 1e-4) return;
        nx /= len;
        nz /= len;
        PoseStack.Pose p = pose.last();
        vc.addVertex(p, x1, PY, z1).setColor(r, g, b, 1f).setNormal(p, nx, 0, nz);
        vc.addVertex(p, x2, PY, z2).setColor(r, g, b, 1f).setNormal(p, nx, 0, nz);
    }
}
