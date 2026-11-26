package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sparkgap.pgtelegraph.block.DeviceBlock;
import com.sparkgap.pgtelegraph.block.MirrorGalvanometerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/** The mirror galvanometer's spot of light on the scale across its front. */
public class GalvoRenderer implements BlockEntityRenderer<MirrorGalvanometerBlockEntity> {
    public GalvoRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(MirrorGalvanometerBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var st = be.getBlockState();
        Direction facing = st.hasProperty(DeviceBlock.FACING) ? st.getValue(DeviceBlock.FACING) : Direction.NORTH;
        float s = Mth.lerp(pt, be.spotO, be.spot);
        float x = (8 + s * 4.2f) / 16, y = 5.6f / 16, z = 2.97f / 16, w = 0.5f / 16, h = 0.7f / 16;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        PowerLedRenderer.cube(buffers.getBuffer(RenderType.debugQuads()), pose.last().pose(), x - w, y - h, z - 0.002f, x + w, y + h, z, 0xFFF4C0);
        pose.popPose();
    }
}
