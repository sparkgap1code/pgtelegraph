package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.sparkgap.pgtelegraph.block.DeviceBlock;
import com.sparkgap.pgtelegraph.block.TapeTransmitterBlockEntity;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create half-shafts on the tape transmitter: one turning with each shaft that drives it, or a
 * still stub on the back when nothing is connected, so you can see where the drive goes in.
 */
public class TapeTransmitterRenderer implements BlockEntityRenderer<TapeTransmitterBlockEntity> {
    public TapeTransmitterRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(TapeTransmitterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        BlockState state = be.getBlockState();
        VertexConsumer vc = buffers.getBuffer(RenderType.solid());
        boolean any = false;
        for (Direction d : Direction.values()) {
            KineticBlockEntity k = be.driver(d);
            if (k == null) continue;
            any = true;
            SuperByteBuffer half = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, d);
            float angle = KineticBlockEntityRenderer.getAngleForBe(k, be.getBlockPos(), d.getAxis());
            KineticBlockEntityRenderer.kineticRotationTransform(half, k, d.getAxis(), angle, light).renderInto(pose, vc);
        }
        if (!any && state.hasProperty(DeviceBlock.FACING)) {
            Direction back = state.getValue(DeviceBlock.FACING).getOpposite();
            CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, back).light(light).renderInto(pose, vc);
        }
    }
}
