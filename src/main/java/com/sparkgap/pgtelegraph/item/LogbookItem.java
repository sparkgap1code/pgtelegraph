package com.sparkgap.pgtelegraph.item;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.client.ClientAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Station logbook (교신 일지): one line per contact - day and time are stamped automatically.
 * From an entry the screen can fill in a blank QSL card from the inventory.
 */
public class LogbookItem extends Item {
    public static final int MAX_ENTRIES = 300, MAX_LEN = 160;
    public static final int ADD = 0, DELETE = 1, QSL = 2;

    public LogbookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static List<String> entries(ItemStack s) {
        List<String> l = s.get(PgTelegraph.LOGBOOK.get());
        return l == null ? List.of() : l;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (level.isClientSide) ClientAccess.openLogbook(hand, entries(s));
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    /** server side of the logbook screen */
    public static void apply(ServerPlayer p, InteractionHand hand, int action, int index, String text) {
        ItemStack s = p.getItemInHand(hand);
        if (!(s.getItem() instanceof LogbookItem)) return;
        List<String> l = new ArrayList<>(entries(s));
        switch (action) {
            case ADD -> {
                String t = text == null ? "" : text.trim();
                if (t.isEmpty()) return;
                if (t.length() > MAX_LEN) t = t.substring(0, MAX_LEN);
                long dt = p.level().getDayTime();
                long day = dt / 24000 + 1, hm = (dt + 6000) % 24000;
                String stamp = String.format(Locale.ROOT, "D%d %02d:%02d", day, hm / 1000, (hm % 1000) * 60 / 1000);
                l.add(0, stamp + " | " + t);
                Contest.logged(p, t);
                while (l.size() > MAX_ENTRIES) l.remove(l.size() - 1);
            }
            case DELETE -> {
                if (index < 0 || index >= l.size()) return;
                l.remove(index);
            }
            case QSL -> {
                if (index < 0 || index >= l.size()) return;
                ItemStack blank = null;
                for (ItemStack c : p.getInventory().items) {
                    if (c.getItem() instanceof QslCardItem && QslCardItem.text(c) == null) {
                        blank = c;
                        break;
                    }
                }
                if (blank == null) {
                    p.displayClientMessage(Component.translatable("pgtelegraph.logbook.no_card").withStyle(ChatFormatting.RED), true);
                    return;
                }
                blank.shrink(1);
                ItemStack card = new ItemStack(PgTelegraph.QSL_CARD.get());
                card.set(PgTelegraph.QSL.get(), "DE " + LicenseItem.of(p) + " | " + l.get(index) + " | TNX QSO 73");
                if (!p.getInventory().add(card)) p.drop(card, false);
                int filled = 0;
                for (ItemStack c : p.getInventory().items) if (c.getItem() instanceof QslCardItem && QslCardItem.text(c) != null) filled += c.getCount();
                if (filled >= 10) com.sparkgap.pgtelegraph.Awards.award(p, "qsl10");
                p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.2f);
                return;
            }
            default -> {
                return;
            }
        }
        s.set(PgTelegraph.LOGBOOK.get(), List.copyOf(l));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        List<String> l = entries(stack);
        tip.add(Component.translatable("pgtelegraph.logbook.count", l.size()).withStyle(ChatFormatting.GRAY));
        if (!l.isEmpty()) tip.add(Component.literal(l.get(0)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
