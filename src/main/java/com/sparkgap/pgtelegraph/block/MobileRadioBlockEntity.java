package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sparkgap.pgtelegraph.item.AprsTrackerItem;
import com.sparkgap.pgtelegraph.radio.Aprs;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Vehicle radio (차량용 무전기): a 50 W 2 m / 70 cm set with a roof antenna, for Create trains, carts
 * and airships. Riding a contraption that carries one, your handheld or manpack transmits through it
 * (50 W, the roof whip) and runs off the vehicle instead of its battery. It also sends the vehicle's
 * APRS position every 30 s with its network key (the two items on top), parked or moving. Name tag:
 * the vehicle's name.
 */
public class MobileRadioBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, AprsKeyed {
    public static final double WATTS = 50, APRS_WATTS = 25, ROOF_GAIN_DB = 2.15;

    private final ItemStack[] keys = {ItemStack.EMPTY, ItemStack.EMPTY};
    private String name = "";

    public MobileRadioBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public ItemStack[] keySlots() {
        return keys;
    }

    @Override
    public void keyChanged() {
        setChanged();
        sendData();
    }

    @Override
    public double slotTopPx() {
        return 4;
    }

    public String name() {
        return name.isEmpty() ? "MOBILE-" + Math.floorMod(worldPosition.asLong(), 1000) : name;
    }

    public void setName(String n) {
        name = n == null ? "" : n.length() > 24 ? n.substring(0, 24) : n;
        setChanged();
        sendData();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        long seed = worldPosition.asLong() * 31;
        if (Aprs.bursting(level.getGameTime(), seed))
            Aprs.send(level, worldPosition, worldPosition, 3, RadioMath.dbm(APRS_WATTS) + ROOF_GAIN_DB,
                    new Aprs.Packet(name(), worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5, aprsKey(), Aprs.VEHICLE));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.write(tag, r, clientPacket);
        AprsKeyed.save(keys, tag, r);
        tag.putString("Name", name);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
        super.read(tag, r, clientPacket);
        AprsKeyed.load(keys, tag, r);
        name = tag.getString("Name");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal("     " + name()).withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.aprs.key", AprsTrackerItem.keyText(aprsKey())))
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** on a moving contraption: the APRS reports go on from wherever the vehicle is */
    public static class Movement implements MovementBehaviour {
        private record Cfg(String name, String key) {}

        @Override
        public void tick(MovementContext ctx) {
            Level level = ctx.world;
            if (level == null || level.isClientSide || ctx.position == null) return;
            Cfg cfg = ctx.temporaryData instanceof Cfg c ? c : null;
            if (cfg == null) {
                CompoundTag d = ctx.blockEntityData == null ? new CompoundTag() : ctx.blockEntityData;
                ItemStack[] k = {ItemStack.EMPTY, ItemStack.EMPTY};
                AprsKeyed.load(k, d, level.registryAccess());
                String n = d.getString("Name");
                if (n.isEmpty() && ctx.contraption != null && ctx.contraption.entity != null)
                    n = ctx.contraption.entity.getContraptionName().getString();
                cfg = new Cfg(n.isEmpty() ? "MOBILE" : n, Aprs.key(k[0], k[1]));
                ctx.temporaryData = cfg;
            }
            int id = ctx.contraption != null && ctx.contraption.entity != null ? ctx.contraption.entity.getId() : 0;
            BlockPos key = new BlockPos(28_000_000 + id, -2_000_000 - Math.floorMod(ctx.localPos.hashCode(), 10_000), 0);
            if (!Aprs.bursting(level.getGameTime(), id * 977L + ctx.localPos.asLong())) return;
            Aprs.send(level, key, BlockPos.containing(ctx.position), 3, RadioMath.dbm(APRS_WATTS) + ROOF_GAIN_DB,
                    new Aprs.Packet(cfg.name(), ctx.position.x, ctx.position.y, ctx.position.z, cfg.key(), Aprs.VEHICLE));
        }
    }

    /** is this player riding a contraption with a vehicle radio on it */
    public static boolean riding(net.minecraft.world.entity.player.Player p, net.minecraft.world.level.block.Block radio) {
        if (!(p.getVehicle() instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity ce)) return false;
        var c = ce.getContraption();
        if (c == null) return false;
        for (var info : c.getBlocks().values()) if (info.state().is(radio)) return true;
        return false;
    }
}
