package com.sparkgap.pgtelegraph.block;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.radio.Aprs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A box with two APRS network-key slots on its top front, set like a Create redstone link's
 * frequency: right-click a slot with any item (it is not used up), sneak + empty hand clears it.
 * Right-clicking the box with an APRS tracker copies the key into the tracker.
 */
public interface AprsKeyed {
    /** the two key items */
    ItemStack[] keySlots();

    /** server: a slot changed (save and sync) */
    void keyChanged();

    /** top of the box (pixels) where the slots sit */
    double slotTopPx();

    default String aprsKey() {
        return Aprs.key(keySlots()[0], keySlots()[1]);
    }

    /** NORTH-authored slot centres (pixels): x, z */
    double[][] SLOTS = {{5.5, 6}, {10.5, 6}};

    /** which slot the click lands on: 0, 1 or -1 */
    static int slotAt(BlockState st, BlockPos pos, Vec3 hit, double topPx) {
        if (Math.abs(hit.y - (pos.getY() + topPx / 16)) > 0.03) return -1;
        double xw = hit.x - pos.getX(), zw = hit.z - pos.getZ(), xn, zn;
        Direction f = st.hasProperty(DeviceBlock.FACING) ? st.getValue(DeviceBlock.FACING) : Direction.NORTH;
        switch (f) {
            case EAST -> {
                xn = zw;
                zn = 1 - xw;
            }
            case SOUTH -> {
                xn = 1 - xw;
                zn = 1 - zw;
            }
            case WEST -> {
                xn = 1 - zw;
                zn = xw;
            }
            default -> {
                xn = xw;
                zn = zw;
            }
        }
        if (zn < 3 / 16.0 || zn > 9 / 16.0 || xn < 3 / 16.0 || xn > 13 / 16.0) return -1;
        return xn < 0.5 ? 0 : 1;
    }

    /** the block's useItemOn (an empty hand too, with sneak) */
    static ItemInteractionResult use(AprsKeyed be, BlockState st, Level level, BlockPos pos, Player player, ItemStack held, Vec3 hit) {
        if (held.getItem() instanceof com.sparkgap.pgtelegraph.item.AprsTrackerItem) {
            if (!level.isClientSide) {
                held.set(PgTelegraph.APRS_KEY.get(), be.aprsKey());
                player.displayClientMessage(Component.translatable("pgtelegraph.aprs.copied"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        int slot = slotAt(st, pos, hit, be.slotTopPx());
        if (slot < 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (held.isEmpty() && !player.isShiftKeyDown()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            be.keySlots()[slot] = held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1);
            be.keyChanged();
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.5f, 1.2f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    static void save(ItemStack[] slots, net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider r) {
        for (int i = 0; i < 2; i++) {
            if (slots[i].isEmpty()) tag.remove("Key" + i);
            else tag.put("Key" + i, slots[i].save(r));
        }
    }

    static void load(ItemStack[] slots, net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider r) {
        for (int i = 0; i < 2; i++)
            slots[i] = tag.contains("Key" + i) ? ItemStack.parseOptional(r, tag.getCompound("Key" + i)) : ItemStack.EMPTY;
    }
}
