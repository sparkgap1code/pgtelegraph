package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.event.BlockEntityBehaviourEvent;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Labels (이름표): right-click any of this mod's devices with a renamed name tag and it carries that
 * name - "TX1 40 m", "2 m FM" - shown after the block's name in the goggles, on the dial screen and
 * on the operating-position selector. The tag is not used up; an unnamed tag takes the label off.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class Labels {
    private Labels() {}

    public static final int MAX = 24;

    public static class Behaviour extends BlockEntityBehaviour {
        public static final BehaviourType<Behaviour> TYPE = new BehaviourType<>("pgtelegraph_label");
        public String label = "";

        public Behaviour(SmartBlockEntity be) {
            super(be);
        }

        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

        @Override
        public void write(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
            if (!label.isEmpty()) tag.putString("PgLabel", label);
        }

        @Override
        public void read(CompoundTag tag, HolderLookup.Provider r, boolean clientPacket) {
            label = tag.getString("PgLabel");
        }
    }

    /** every block entity of this mod gets a label */
    @SubscribeEvent
    public static void onBehaviours(BlockEntityBehaviourEvent e) {
        for (var holder : PgTelegraph.BLOCK_ENTITIES.getEntries()) {
            @SuppressWarnings("unchecked")
            BlockEntityType<SmartBlockEntity> type = (BlockEntityType<SmartBlockEntity>) holder.get();
            e.forType(type, be -> e.attach(new Behaviour(be)));
        }
    }

    public static String get(BlockEntity be) {
        if (!(be instanceof SmartBlockEntity sbe)) return "";
        Behaviour b = BlockEntityBehaviour.get(sbe, Behaviour.TYPE);
        return b == null ? "" : b.label;
    }

    /** the block's name, and its label if it has one: the first line of a goggle tooltip */
    public static MutableComponent title(BlockEntity be) {
        MutableComponent c = be.getBlockState().getBlock().getName().copy();
        String l = get(be);
        if (!l.isEmpty()) c.append(Component.literal("  「" + l + "」").withStyle(ChatFormatting.AQUA));
        return c;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClick(PlayerInteractEvent.RightClickBlock e) {
        ItemStack held = e.getItemStack();
        if (!held.is(Items.NAME_TAG)) return;
        BlockEntity be = e.getLevel().getBlockEntity(e.getPos());
        if (!(be instanceof SmartBlockEntity sbe) || BlockEntityBehaviour.get(sbe, Behaviour.TYPE) == null) return;
        // the vehicle radio uses its name tag for the vehicle's APRS name
        if (BuiltInRegistries.BLOCK.getKey(e.getLevel().getBlockState(e.getPos()).getBlock()).getPath().equals("mobile_radio")) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.sidedSuccess(e.getLevel().isClientSide));
        if (e.getLevel().isClientSide) return;
        Behaviour b = BlockEntityBehaviour.get(sbe, Behaviour.TYPE);
        String name = held.has(DataComponents.CUSTOM_NAME) ? held.getHoverName().getString().trim() : "";
        if (name.length() > MAX) name = name.substring(0, MAX);
        b.label = name;
        sbe.setChanged();
        sbe.sendData();
        e.getEntity().displayClientMessage(name.isEmpty() ? Component.translatable("pgtelegraph.label.cleared")
                : Component.translatable("pgtelegraph.label.set", name), true);
        e.getLevel().playSound(null, e.getPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6f, 1.3f);
    }
}
