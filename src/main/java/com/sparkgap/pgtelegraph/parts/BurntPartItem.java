package com.sparkgap.pgtelegraph.parts;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** "Burnt X": what a failed part turns into. Useless, but shows what went. */
public class BurntPartItem extends Item {
    public BurntPartItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        String id = stack.get(PgTelegraph.BURNT_OF.get());
        if (id == null) return super.getName(stack);
        return Component.translatable("item.pgtelegraph.burnt_part.of", Component.translatable("item.pgtelegraph." + id));
    }
}
