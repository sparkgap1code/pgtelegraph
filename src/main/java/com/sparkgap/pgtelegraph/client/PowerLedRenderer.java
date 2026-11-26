package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sparkgap.pgtelegraph.block.DeviceBlock;
import com.sparkgap.pgtelegraph.block.PowerLamp;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/** The power lamp: a small LED on the top front-right corner of a box - green on, amber warning/battery, red burnt. */
public class PowerLedRenderer<T extends BlockEntity & PowerLamp> implements BlockEntityRenderer<T> {
    public PowerLedRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        draw(be, be.lamp(), pose, buffers);
    }

    public static void draw(BlockEntity be, int lamp, PoseStack pose, MultiBufferSource buffers) {
        if (be.getLevel() == null) return;
        BlockState st = be.getBlockState();
        Direction facing = st.hasProperty(DeviceBlock.FACING) ? st.getValue(DeviceBlock.FACING) : Direction.NORTH;
        BlockState north = st.hasProperty(DeviceBlock.FACING) ? st.setValue(DeviceBlock.FACING, Direction.NORTH) : st;
        VoxelShape shape = north.getShape(be.getLevel(), be.getBlockPos());
        if (shape.isEmpty()) return;
        // the main box: the biggest part of the shape (terminal posts stick up above it)
        AABB box = shape.toAabbs().stream().max(java.util.Comparator.comparingDouble(b -> b.getXsize() * b.getZsize())).orElse(shape.bounds());
        long t = be.getLevel().getGameTime();
        int rgb = switch (lamp) {
            case PowerLamp.ON -> 0x30FF40;
            case PowerLamp.WARN -> 0xFFB020;
            case PowerLamp.BLINK -> t % 20 < 10 ? 0xFFB020 : 0x302010;
            case PowerLamp.FAULT -> t % 40 < 30 ? 0xFF2020 : 0x301010;
            default -> 0x182018;
        };
        float s = 1.2f / 16, x1 = (float) box.maxX - 1.2f / 16, z0 = (float) box.minZ + 0.6f / 16, y0 = (float) box.maxY;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        VertexConsumer vc = buffers.getBuffer(RenderType.debugQuads());
        cube(vc, pose.last().pose(), x1 - s, y0, z0, x1, y0 + 0.5f / 16, z0 + s, rgb);
        pose.popPose();
    }

    static void cube(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        // top
        q(vc, m, r, g, b, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        // sides, a little darker
        int r2 = r * 3 / 4, g2 = g * 3 / 4, b2 = b * 3 / 4;
        q(vc, m, r2, g2, b2, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        q(vc, m, r2, g2, b2, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        q(vc, m, r2, g2, b2, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        q(vc, m, r2, g2, b2, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void q(VertexConsumer vc, Matrix4f m, int r, int g, int b, float... p) {
        for (int i = 0; i < 12; i += 3) vc.addVertex(m, p[i], p[i + 1], p[i + 2]).setColor(r, g, b, 255);
        for (int i = 9; i >= 0; i -= 3) vc.addVertex(m, p[i], p[i + 1], p[i + 2]).setColor(r, g, b, 255); // both windings: no culling surprises
    }
}
