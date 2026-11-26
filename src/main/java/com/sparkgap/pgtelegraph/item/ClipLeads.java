package com.sparkgap.pgtelegraph.item;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.block.IambicKeyer;
import com.sparkgap.pgtelegraph.parts.Wreckage;
import com.sparkgap.pgtelegraph.radio.PaddleInput;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;
import org.patryk3211.powergrid.electricity.sim.node.IElectricNode;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the clip-lead keys: while a player holds one with both leads clipped on, a
 * switched wire joins the two terminals in Power Grid's circuit. It is closed while keyed
 * (straight: right-click held; paddle: the built-in keyer's output) and removed again when the
 * key is put away, a lead comes off, or the player walks too far away (the leads are pulled off).
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class ClipLeads {
    private ClipLeads() {}

    private static final float CONTACT_OHMS = 0.05f;
    private static final double LEAD_REACH = 12;
    /** overload tolerated this long before the contact burns */
    private static final int BURN_TICKS = 20;

    private static final class Live {
        BlockWireEndpoint a, b;
        SwitchedWire wire;
        Level level;
        final IambicKeyer keyer = new IambicKeyer();
        boolean keyed;
        int overload;
    }

    private static final Map<UUID, Live> LIVE = new HashMap<>();

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Live l = LIVE.remove(e.getEntity().getUUID());
        if (l != null) drop(l);
        PaddleInput.forget(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        Level level = player.level();
        InteractionHand hand = player.getMainHandItem().getItem() instanceof ClipKeyItem ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().getItem() instanceof ClipKeyItem ? InteractionHand.OFF_HAND : null;
        Live live = LIVE.get(player.getUUID());
        if (hand == null) {
            if (live != null) drop(live);
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        ClipKeyItem item = (ClipKeyItem) stack.getItem();
        ClipKeyItem.Leads leads = ClipKeyItem.leads(stack);
        if (!leads.complete()) {
            if (live != null) drop(live);
            return;
        }

        BlockPos pa = BlockPos.of(leads.aPos()), pb = BlockPos.of(leads.bPos());
        BlockWireEndpoint a = new BlockWireEndpoint(pa, leads.aTerm());
        BlockWireEndpoint b = new BlockWireEndpoint(pb, leads.bTerm());
        double reach = LEAD_REACH * LEAD_REACH;
        if (player.getEyePosition().distanceToSqr(pa.getCenter()) > reach || player.getEyePosition().distanceToSqr(pb.getCenter()) > reach) {
            unclip(player, stack, leads, live, "pgtelegraph.clip.pulled");
            return;
        }
        if (!level.isLoaded(pa) || !level.isLoaded(pb)) return;
        if (!a.isValid(level) || !b.isValid(level)) {
            unclip(player, stack, leads, live, "pgtelegraph.clip.lost");
            return;
        }

        if (live == null) {
            live = new Live();
            LIVE.put(player.getUUID(), live);
        }
        if (!a.equals(live.a) || !b.equals(live.b) || live.level != level || stale(live, level)) {
            drop(live);
            if (!connect(live, level, a, b)) return;
        }

        // ---- keying
        boolean keyed;
        if (item.paddle) {
            boolean main = hand == InteractionHand.MAIN_HAND;
            keyed = live.keyer.tick(main && PaddleInput.dit(player), main && PaddleInput.dah(player), leads.wpm());
        } else {
            keyed = player.isUsingItem() && player.getUseItem() == stack;
        }
        if (keyed != live.keyed) {
            live.keyed = keyed;
            live.wire.setState(keyed);
            if (!item.paddle) level.playSound(null, player.blockPosition(), SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
                    SoundSource.PLAYERS, 0.2f, keyed ? 1.9f : 1.6f);
        }

        // ---- ratings: an overloaded contact burns, too much voltage arcs over the open gap
        double amps = Math.abs(live.wire.current());
        double volts = Math.abs(live.wire.potentialDifference());
        boolean over = amps > item.maxAmps || (!keyed && volts > item.maxVolts);
        live.overload = over ? live.overload + 1 : Math.max(0, live.overload - 1);
        if (live.overload > BURN_TICKS) {
            drop(live);
            LIVE.remove(player.getUUID());
            Wreckage.vent(level, player.position().add(0, 1, 0));
            player.setItemInHand(hand, Wreckage.burnt(stack));
            player.displayClientMessage(Component.translatable("pgtelegraph.clip.burnt",
                    String.format(java.util.Locale.ROOT, "%.1f", amps), String.format(java.util.Locale.ROOT, "%.0f", volts))
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    /** the circuit was rebuilt under us (block re-wired, chunk reloaded): our wire points at old nodes */
    private static boolean stale(Live l, Level level) {
        if (l.wire == null || l.wire.getNetwork() == null) return true;
        return l.wire.getNode1() != l.a.getNode(level) || l.wire.getNode2() != l.b.getNode(level);
    }

    private static boolean connect(Live l, Level level, BlockWireEndpoint a, BlockWireEndpoint b) {
        WorldNetworks wn = GlobalElectricNetworks.getWorldNetworks(level);
        if (wn == null) return false;
        wn.putInNetwork(a);
        wn.putInNetwork(b);
        ElectricalNetwork net = wn.prepareForConnection(a, b);
        if (net == null) return false;
        IElectricNode n1 = a.getNode(level), n2 = b.getNode(level);
        if (n1 == null || n2 == null) return false;
        l.wire = new SwitchedWire(CONTACT_OHMS, n1, n2, false);
        net.addWire(l.wire);
        l.a = a;
        l.b = b;
        l.level = level;
        l.keyed = false;
        l.overload = 0;
        l.keyer.reset();
        return true;
    }

    private static void drop(Live l) {
        if (l.wire != null) {
            if (l.wire.getNetwork() != null) l.wire.remove();
            l.wire = null;
        }
        l.a = l.b = null;
        l.keyed = false;
    }

    private static void unclip(ServerPlayer p, ItemStack stack, ClipKeyItem.Leads leads, Live live, String msg) {
        if (live != null) drop(live);
        stack.set(PgTelegraph.CLIP_LEADS.get(), leads.cleared());
        p.displayClientMessage(Component.translatable(msg).withStyle(ChatFormatting.YELLOW), true);
    }
}
