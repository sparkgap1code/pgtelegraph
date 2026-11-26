package com.sparkgap.pgtelegraph.media;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Video tape (비디오테이프): 10 minutes of 320 x 240 pictures at 15 frames a second. Recorded by a
 * VCR (off a TV set or a TV camera) or a camcorder, or filled from a video file with /pgvideo import;
 * played back by a VCR. The same data also rides inside a camcorder that has a tape in it.
 */
public class VideoTapeItem extends Item {
    public VideoTapeItem(Properties p) {
        super(p);
    }

    static CompoundTag tag(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    static void put(ItemStack s, CompoundTag t) {
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    public static String id(ItemStack s) {
        return tag(s).getString("Video");
    }

    /** the tape's id, made up now if it is blank */
    public static String ensureId(ItemStack s) {
        CompoundTag t = tag(s);
        if (!VideoStore.validId(t.getString("Video"))) {
            t.putString("Video", VideoStore.newId());
            put(s, t);
        }
        return t.getString("Video");
    }

    public static void setInfo(ItemStack s, String title, int frames) {
        CompoundTag t = tag(s);
        if (title != null) t.putString("Title", title);
        t.putInt("Sec", frames / VideoStore.FPS);
        put(s, t);
    }

    /** copy the tape data between a tape item and a camcorder */
    public static void copy(ItemStack from, ItemStack to) {
        CompoundTag f = tag(from), t = tag(to);
        for (String k : new String[]{"Video", "Title", "Sec"}) {
            if (f.contains(k)) t.put(k, f.get(k));
            else t.remove(k);
        }
        put(to, t);
    }

    public static void clear(ItemStack s) {
        CompoundTag t = tag(s);
        t.remove("Video");
        t.remove("Title");
        t.remove("Sec");
        put(s, t);
    }

    public static String time(int sec) {
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    static void info(ItemStack s, List<Component> tip) {
        CompoundTag t = tag(s);
        if (!VideoStore.validId(t.getString("Video"))) {
            tip.add(Component.translatable("pgtelegraph.video.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!t.getString("Title").isEmpty()) tip.add(Component.literal(t.getString("Title")).withStyle(ChatFormatting.WHITE));
        tip.add(Component.literal(time(t.getInt("Sec")) + " / " + time(VideoStore.MAX_SECONDS)).withStyle(ChatFormatting.GRAY));
    }

    /** sneak + use (in the air) twice within 3 s: wipe the tape */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                                                                      net.minecraft.world.InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || !VideoStore.validId(id(s))) return net.minecraft.world.InteractionResultHolder.pass(s);
        if (!level.isClientSide) {
            CompoundTag t = tag(s);
            long now = level.getGameTime();
            if (now - t.getLong("EraseAsk") <= 60) {
                VideoStore.erase(level.getServer(), id(s));
                t.remove("EraseAsk");
                t.remove("Title");
                t.putInt("Sec", 0);
                put(s, t);
                player.displayClientMessage(Component.translatable("pgtelegraph.video.erased").withStyle(ChatFormatting.YELLOW), true);
            } else {
                t.putLong("EraseAsk", now);
                put(s, t);
                player.displayClientMessage(Component.translatable("pgtelegraph.video.erase_ask").withStyle(ChatFormatting.RED), true);
            }
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        info(s, tip);
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return false;
    }
}
