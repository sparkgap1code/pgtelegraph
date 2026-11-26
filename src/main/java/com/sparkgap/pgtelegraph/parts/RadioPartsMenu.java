package com.sparkgap.pgtelegraph.parts;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Parts bay of a radio: one slot per stage of its layout, plus the player's inventory. */
public class RadioPartsMenu extends AbstractContainerMenu {
    public final PartSlot.Layout layout;
    private final Container parts;
    private final Predicate<Player> valid;

    /** Container that reports every change back to its owner (block entity or handheld stack). */
    public static class Bay extends SimpleContainer {
        private final Consumer<List<ItemStack>> sink;

        public Bay(List<ItemStack> initial, Consumer<List<ItemStack>> sink) {
            super(initial.size());
            for (int i = 0; i < initial.size(); i++) setItem(i, initial.get(i).copy());
            this.sink = sink;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (sink != null) sink.accept(getItems());
        }
    }

    public RadioPartsMenu(int id, Inventory inv, PartSlot.Layout layout, Container parts, Predicate<Player> valid) {
        super(PgTelegraph.PARTS_MENU.get(), id);
        this.layout = layout;
        this.parts = parts;
        this.valid = valid;
        int n = layout.slots.length;
        int x0 = 88 - n * 11;
        for (int i = 0; i < n; i++) {
            PartSlot want = layout.slots[i];
            addSlot(new Slot(parts, i, x0 + i * 22, 30) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    PartDef d = PartItem.def(s);
                    return d != null && d.slot() == want && d.fits(layout);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
    }

    /** client side */
    public RadioPartsMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, PartSlot.Layout.values()[buf.readVarInt()]);
    }

    private RadioPartsMenu(int id, Inventory inv, PartSlot.Layout layout) {
        this(id, inv, layout, new SimpleContainer(layout.slots.length), p -> true);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        int n = layout.slots.length;
        if (index < n) {
            if (!moveItemStackTo(s, n, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            PartDef d = PartItem.def(s);
            int target = d == null || !d.fits(layout) ? -1 : layout.indexOf(d.slot());
            if (target < 0 || slots.get(target).hasItem()) return ItemStack.EMPTY;
            ItemStack one = s.split(1);
            slots.get(target).set(one);
        }
        if (s.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return valid.test(player);
    }
}
