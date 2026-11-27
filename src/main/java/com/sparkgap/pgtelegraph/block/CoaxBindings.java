package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which radio a piece of coax is plugged into (동축 연결 대상). Coax laid where two or more radios
 * could take it - beside a rack, between two sets - asks the player which one it goes to; from then
 * on only that set uses it, and coax plugged into two different sets never joins up. Sneak + empty
 * hand on the coax asks again. Coax touching only one radio needs no choice.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class CoaxBindings extends SavedData {
    private final Map<Long, Long> bound = new HashMap<>();

    private static final SavedData.Factory<CoaxBindings> FACTORY = new SavedData.Factory<>(CoaxBindings::new, CoaxBindings::load, null);

    public static CoaxBindings get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "pgtelegraph_coax");
    }

    /** the set this coax is plugged into, or null (server side; on the client always null) */
    public static BlockPos boundTo(BlockGetter level, BlockPos coax) {
        if (!(level instanceof ServerLevel sl)) return null;
        Long d = get(sl).bound.get(coax.asLong());
        return d == null ? null : BlockPos.of(d);
    }

    /** may {@code device} use the coax at {@code coax} */
    public static boolean allows(BlockGetter level, BlockPos coax, BlockPos device) {
        BlockPos b = boundTo(level, coax);
        return b == null || b.equals(device);
    }

    /** two coax blocks plugged into two different sets are separate feed lines */
    public static boolean separate(BlockGetter level, BlockPos a, BlockPos b) {
        BlockPos ba = boundTo(level, a), bb = boundTo(level, b);
        return ba != null && bb != null && !ba.equals(bb);
    }

    public static void bind(ServerLevel level, BlockPos coax, BlockPos device) {
        CoaxBindings d = get(level);
        if (device == null) d.bound.remove(coax.asLong());
        else d.bound.put(coax.asLong(), device.asLong());
        d.setDirty();
    }

    /** a radio that starts a coax run (not an amplifier, antenna or tuner in the middle of one) */
    public static boolean isRadio(BlockEntity be) {
        if (be == null || com.sparkgap.pgtelegraph.radio.AntennaLink.passThrough(be.getBlockState())) return false; // tuners sit in the run
        return be instanceof RadioBlockEntity || be instanceof Tunable || be instanceof AprsStationBlockEntity
                || be instanceof IonosondeBlockEntity;
    }

    /** the radios touching this coax */
    public static List<BlockPos> candidates(Level level, BlockPos coax) {
        List<BlockPos> out = new ArrayList<>();
        for (Direction d : Direction.values()) {
            BlockPos p = coax.relative(d);
            if (isRadio(level.getBlockEntity(p))) out.add(p);
        }
        return out;
    }

    /** open the choice on the player's screen */
    public static void ask(ServerPlayer p, BlockPos coax) {
        Level level = p.level();
        List<BlockPos> c = candidates(level, coax);
        List<String> names = new ArrayList<>();
        for (BlockPos q : c) {
            BlockEntity be = level.getBlockEntity(q);
            String s = Labels.title(be).getString();
            if (be instanceof Tunable t) s += "  " + RadioBlockEntity.formatFreq(t.freqHz());
            names.add(s);
        }
        BlockPos cur = boundTo(level, coax);
        PacketDistributor.sendToPlayer(p, new com.sparkgap.pgtelegraph.network.Batch2Payloads.CoaxChoice(coax, c, names, cur == null ? BlockPos.ZERO : cur, cur != null));
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getPlacedBlock().getBlock() instanceof CoaxBlock)) return;
        if (candidates(p.level(), e.getPos()).size() >= 2) ask(p, e.getPos());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getLevel() instanceof ServerLevel sl && e.getState().getBlock() instanceof CoaxBlock) bind(sl, e.getPos(), null);
    }

    @SubscribeEvent
    public static void onClick(PlayerInteractEvent.RightClickBlock e) {
        if (!e.getEntity().isShiftKeyDown() || !e.getItemStack().isEmpty()) return;
        if (!(e.getLevel().getBlockState(e.getPos()).getBlock() instanceof CoaxBlock)) return;
        if (candidates(e.getLevel(), e.getPos()).isEmpty()) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(e.getLevel().isClientSide));
        if (e.getEntity() instanceof ServerPlayer p) ask(p, e.getPos());
    }

    /** the player's answer */
    public static void answer(ServerPlayer p, BlockPos coax, BlockPos device, boolean any) {
        if (!(p.level() instanceof ServerLevel sl) || p.distanceToSqr(coax.getCenter()) > 100) return;
        if (!(sl.getBlockState(coax).getBlock() instanceof CoaxBlock)) return;
        if (any) {
            bind(sl, coax, null);
            p.displayClientMessage(Component.translatable("pgtelegraph.coax.any"), true);
            return;
        }
        if (!candidates(sl, coax).contains(device)) return;
        bind(sl, coax, device);
        p.displayClientMessage(Component.translatable("pgtelegraph.coax.bound", Labels.title(sl.getBlockEntity(device))), true);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider r) {
        ListTag l = new ListTag();
        for (var en : bound.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("C", en.getKey());
            c.putLong("D", en.getValue());
            l.add(c);
        }
        tag.put("B", l);
        return tag;
    }

    static CoaxBindings load(CompoundTag tag, HolderLookup.Provider r) {
        CoaxBindings d = new CoaxBindings();
        for (Tag t : tag.getList("B", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.bound.put(c.getLong("C"), c.getLong("D"));
        }
        return d;
    }
}
