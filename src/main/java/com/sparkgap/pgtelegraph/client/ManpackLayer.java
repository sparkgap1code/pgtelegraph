package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.radio.ManpackItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.InventoryMenu;

/** Draws a worn manpack radio on the player's back: the pack, shoulder straps and the whip above the shoulder. */
public class ManpackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelResourceLocation MODEL =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "item/manpack_worn"));

    public ManpackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ManpackItem) || player.isInvisible()) return;
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        // model pixels: x 0-16 across (8 = spine), y 0 = waist up to 12 = shoulders, z 0 = the back going outward
        pose.translate(0, 0, 2 / 16f);
        pose.mulPose(Axis.ZP.rotationDegrees(180));
        pose.translate(-0.5, -0.75, 0);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS)), null, model, 1f, 1f, 1f,
                light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
