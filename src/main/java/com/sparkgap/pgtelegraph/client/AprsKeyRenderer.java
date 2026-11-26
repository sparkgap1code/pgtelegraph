package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sparkgap.pgtelegraph.block.AprsKeyed;
import com.sparkgap.pgtelegraph.block.DeviceBlock;
import com.sparkgap.pgtelegraph.block.PowerLamp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The two APRS key items lying on top of the box (like a redstone link's frequency), and the power lamp. */
public class AprsKeyRenderer<T extends BlockEntity & AprsKeyed> implements BlockEntityRenderer<T> {
    public AprsKeyRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be instanceof PowerLamp lamp) PowerLedRenderer.draw(be, lamp.lamp(), pose, buffers);
        var st = be.getBlockState();
        Direction facing = st.hasProperty(DeviceBlock.FACING) ? st.getValue(DeviceBlock.FACING) : Direction.NORTH;
        for (int i = 0; i < 2; i++) {
            ItemStack s = be.keySlots()[i];
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
            pose.translate(-0.5, 0, -0.5);
            double[] c = AprsKeyed.SLOTS[i];
            pose.translate(c[0] / 16, be.slotTopPx() / 16 + 0.01, c[1] / 16);
            if (s.isEmpty()) { // an empty slot: a dark pad
                pose.popPose();
                continue;
            }
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.25f, 0.25f, 0.25f);
            Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, light, overlay, pose, buffers,
                    be.getLevel(), 0);
            pose.popPose();
        }
    }
}
