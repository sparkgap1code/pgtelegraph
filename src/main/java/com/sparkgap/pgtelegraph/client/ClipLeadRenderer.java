package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.item.ClipKeyItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.HangingWireRenderer;
import org.patryk3211.powergrid.electricity.wire.registry.WireItemEntry;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;

/**
 * Draws the clip leads of a clip-lead key: two sagging leads of Power Grid insulated copper wire
 * (red and black, drawn with Power Grid's own wire renderer) from the hand of every player holding
 * one to the terminals they are clipped on. Falls back to plain lines if the wire type is missing.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID, value = Dist.CLIENT)
public final class ClipLeadRenderer {
    private ClipLeadRenderer() {}

    private static final int SEGMENTS = 12;

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        WireItemEntry wire = null;
        try {
            wire = WireRegistry.forItem(mc.level, ModdedItems.INSULATED_COPPER_WIRE.get());
        } catch (RuntimeException ignored) {
        }
        RenderType type = wire != null ? RenderType.entityCutout(wire.texture()) : RenderType.lines();
        VertexConsumer vc = null;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        for (Player p : mc.level.players()) {
            ItemStack s = p.getMainHandItem().getItem() instanceof ClipKeyItem ? p.getMainHandItem()
                    : p.getOffhandItem().getItem() instanceof ClipKeyItem ? p.getOffhandItem() : null;
            if (s == null) continue;
            ClipKeyItem.Leads l = ClipKeyItem.leads(s);
            if (!l.hasA() && !l.hasB()) continue;
            if (vc == null) vc = buffers.getBuffer(type);
            Vec3 hand = handPos(p, pt, s == p.getMainHandItem(), mc);
            if (l.hasA()) lead(vc, ps, wire, mc, hand, clip(mc, l.aPos(), l.aTerm()), 0xFFC02020);
            if (l.hasB()) lead(vc, ps, wire, mc, hand, clip(mc, l.bPos(), l.bTerm()), 0xFF282828);
        }
        ps.popPose();
        if (vc != null) buffers.endBatch(type);
    }

    /** where a lead's clip bites: the terminal itself when Power Grid can tell us, else the block */
    private static Vec3 clip(Minecraft mc, long packed, int term) {
        BlockPos pos = BlockPos.of(packed);
        try {
            Vec3 t = IElectric.getTerminalPos(mc.level, pos, term);
            if (t != null) return t;
        } catch (RuntimeException ignored) {
        }
        return pos.getCenter().add(0, 0.1, 0);
    }

    private static void lead(VertexConsumer vc, PoseStack ps, WireItemEntry wire, Minecraft mc, Vec3 a, Vec3 b, int argb) {
        if (wire == null) {
            line(vc, ps.last().pose(), a, b, argb);
            return;
        }
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(a.lerp(b, 0.5)));
        ps.pushPose();
        ps.translate(a.x, a.y, a.z);
        HangingWireRenderer.renderFromPositions(ps, vc, Vec3.ZERO, b.subtract(a), wire.horizontalCoefficient(),
                wire.verticalCoefficient(), wire.wireThickness(), light, argb);
        ps.popPose();
    }

    private static Vec3 handPos(Player p, float pt, boolean main, Minecraft mc) {
        Vec3 base = p.getPosition(pt).add(0, p.getBbHeight() * 0.55, 0);
        boolean right = (p.getMainArm() == HumanoidArm.RIGHT) == main;
        float yaw = (float) Math.toRadians(p.getViewYRot(pt));
        Vec3 side = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(right ? 0.35 : -0.35);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).scale(0.3);
        if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return p.getEyePosition(pt).add(p.getViewVector(pt).scale(0.5)).add(side.scale(0.6)).add(0, -0.35, 0);
        }
        return base.add(side).add(fwd);
    }

    private static void line(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, int argb) {
        double len = a.distanceTo(b), sag = Math.min(0.8, len * 0.08);
        Vec3 prev = a;
        for (int i = 1; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            Vec3 q = a.lerp(b, t).add(0, -sag * 4 * t * (1 - t), 0);
            Vec3 n = q.subtract(prev).normalize();
            vc.addVertex(m, (float) prev.x, (float) prev.y, (float) prev.z).setColor(argb).setNormal((float) n.x, (float) n.y, (float) n.z);
            vc.addVertex(m, (float) q.x, (float) q.y, (float) q.z).setColor(argb).setNormal((float) n.x, (float) n.y, (float) n.z);
            prev = q;
        }
    }
}
