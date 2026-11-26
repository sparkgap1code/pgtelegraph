package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sparkgap.pgtelegraph.block.DeviceBlock;
import com.sparkgap.pgtelegraph.block.Geometry;
import com.sparkgap.pgtelegraph.block.SwitchboardBlock;
import com.sparkgap.pgtelegraph.block.SwitchboardBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Draws patch cords (sagging lines between jacks), fallen drop shutters and the cord in hand. */
public class SwitchboardRenderer implements BlockEntityRenderer<SwitchboardBlockEntity> {
    private static final float[][] CORD_COLORS = {{0.75f, 0.1f, 0.1f}, {0.08f, 0.08f, 0.08f}, {0.1f, 0.25f, 0.7f}};
    private static final double Z = SwitchboardBlock.FRONT_Z - 0.15;

    public SwitchboardRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static double jx(int j) {
        return SwitchboardBlock.JACK_X[j % 4];
    }

    private static double jy(int j) {
        return SwitchboardBlock.JACK_Y[j / 4];
    }

    @Override
    public void render(SwitchboardBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-Geometry.yRotation(be.getBlockState().getValue(DeviceBlock.FACING))));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        int n = 0;
        for (int[] c : be.cords()) {
            float[] col = CORD_COLORS[n++ % CORD_COLORS.length];
            double x1 = jx(c[0]), y1 = jy(c[0]), x2 = jx(c[1]), y2 = jy(c[1]);
            double sag = 2.5 + 0.35 * Math.hypot(x2 - x1, y2 - y1);
            double px = x1, py = y1, pz = Z;
            for (int s = 1; s <= 8; s++) {
                double t = s / 8.0;
                double x = x1 + (x2 - x1) * t;
                double y = y1 + (y2 - y1) * t - sag * 4 * t * (1 - t);
                double z = Z - 2.5 * 4 * t * (1 - t); // hangs out in front of the panel
                segment(lines, pose, px, py, pz, x, y, z, col);
                px = x; py = y; pz = z;
            }
            plug(lines, pose, x1, y1, col);
            plug(lines, pose, x2, y2, col);
        }
        for (int j = 0; j < SwitchboardBlockEntity.LINES; j++) {
            if (be.isDropped(j)) {
                // fallen shutter: an orange flap just above the jack
                LevelRenderer.renderLineBox(pose, lines, new AABB(jx(j) - 1.3, jy(j) + 1.1, Z - 0.6, jx(j) + 1.3, jy(j) + 1.9, Z),
                        1f, 0.55f, 0.05f, 1f);
            }
        }
        if (be.pending() >= 0) {
            int j = be.pending();
            LevelRenderer.renderLineBox(pose, lines, new AABB(jx(j) - 1.4, jy(j) - 1.4, Z - 0.8, jx(j) + 1.4, jy(j) + 1.4, Z),
                    1f, 0.9f, 0.2f, 1f);
        }
        pose.popPose();
    }

    private static void plug(VertexConsumer vc, PoseStack pose, double x, double y, float[] c) {
        LevelRenderer.renderLineBox(pose, vc, new AABB(x - 0.6, y - 0.6, Z - 1.2, x + 0.6, y + 0.6, Z), c[0], c[1], c[2], 1f);
    }

    private static void segment(VertexConsumer vc, PoseStack pose, double x1, double y1, double z1,
                                double x2, double y2, double z2, float[] c) {
        float nx = (float) (x2 - x1), ny = (float) (y2 - y1), nz = (float) (z2 - z1);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-4) return;
        nx /= len; ny /= len; nz /= len;
        PoseStack.Pose p = pose.last();
        vc.addVertex(p, (float) x1, (float) y1, (float) z1).setColor(c[0], c[1], c[2], 1f).setNormal(p, nx, ny, nz);
        vc.addVertex(p, (float) x2, (float) y2, (float) z2).setColor(c[0], c[1], c[2], 1f).setNormal(p, nx, ny, nz);
    }
}
