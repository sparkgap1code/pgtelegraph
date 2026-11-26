package com.sparkgap.pgtelegraph.broadcast;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/** Blank record (use: import an MP3 / WAV / OGG file from your computer) and the recorded result. */
public final class RecordItems {
    private RecordItems() {}

    /** what a recorded disc holds: "id|seconds|title" */
    public record Audio(String id, int seconds, String title) {
        public static Audio of(ItemStack s) {
            String v = s.get(PgTelegraph.AUDIO.get());
            if (v == null) return null;
            String[] p = v.split("\\|", 3);
            if (p.length < 3) return null;
            try {
                return new Audio(p[0], Integer.parseInt(p[1]), p[2]);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public String encode() {
            return id + "|" + seconds + "|" + title;
        }
    }

    public static class Blank extends Item {
        public Blank(Properties p) {
            super(p.stacksTo(16));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack s = player.getItemInHand(hand);
            if (level.isClientSide) ClientAccess.importAudio(hand);
            return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
            tip.add(Component.translatable("item.pgtelegraph.blank_record.hint").withStyle(ChatFormatting.GRAY));
        }
    }

    public static class Recorded extends Item {
        public Recorded(Properties p) {
            super(p.stacksTo(1));
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
            Audio a = Audio.of(stack);
            if (a == null) return;
            tip.add(Component.literal(a.title()).withStyle(ChatFormatting.AQUA));
            tip.add(Component.literal(String.format(Locale.ROOT, "%d:%02d", a.seconds() / 60, a.seconds() % 60)).withStyle(ChatFormatting.GRAY));
        }
    }
}
