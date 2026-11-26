package com.sparkgap.pgtelegraph.item;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.patryk3211.powergrid.electricity.wire.BaseWireEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.electricity.wire.IWireEndpoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Patch cord (패치 코드): an insulated copper lead with plugs on both ends. It is laid exactly like
 * Power Grid's insulated copper wire (it is one of its wire types), but no wire cutter is needed to
 * take it off: right-click a terminal one of its plugs sits in - with an empty hand or anything
 * else but another patch cord - and the whole cord comes out and back into your inventory.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class PatchCord {
    private PatchCord() {}

    /** how close to a plug the click must land (blocks) */
    static final double REACH = 0.3;

    public static boolean is(ItemStack s) {
        return s.is(PgTelegraph.PATCH_CORD.get());
    }

    /** the patch cords plugged into the terminal at {@code hit} on the block at {@code pos} */
    static List<BaseWireEntity> pluggedAt(Level level, BlockPos pos, Vec3 hit) {
        List<BaseWireEntity> out = new ArrayList<>();
        for (BaseWireEntity w : level.getEntitiesOfClass(BaseWireEntity.class, new AABB(pos).inflate(1))) {
            if (w.getItem() != PgTelegraph.PATCH_CORD.get()) continue;
            for (IWireEndpoint ep : new IWireEndpoint[]{w.getEndpoint1(), w.getEndpoint2()}) {
                if (!(ep instanceof BlockWireEndpoint b) || !b.getPos().equals(pos)) continue;
                Vec3 at = b.getExactPosition(level);
                if (at != null && at.distanceTo(hit) <= REACH) {
                    out.add(w);
                    break;
                }
            }
        }
        return out;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClick(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (is(p.getItemInHand(e.getHand()))) return; // plugging in another one
        Level level = e.getLevel();
        List<BaseWireEntity> cords = pluggedAt(level, e.getPos(), e.getHitVec().getLocation());
        if (cords.isEmpty()) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) return;
        int back = 0;
        for (BaseWireEntity w : cords) {
            back += w.getWireCount();
            w.incrementWireCount(-w.getWireCount()); // the items go to the player, not onto the ground
            w.discard();
        }
        if (back > 0 && !p.getAbilities().instabuild) {
            ItemStack st = new ItemStack(PgTelegraph.PATCH_CORD.get(), back);
            if (!p.getInventory().add(st)) p.drop(st, false);
        }
        level.playSound(null, e.getPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.4f);
    }

    @SubscribeEvent
    public static void onTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) {
        if (is(e.getItemStack())) e.getToolTip().add(net.minecraft.network.chat.Component.translatable("pgtelegraph.patchcord.hint")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
