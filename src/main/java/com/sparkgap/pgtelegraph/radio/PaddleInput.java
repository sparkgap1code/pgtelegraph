package com.sparkgap.pgtelegraph.radio;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.item.ClipKeyItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server side: paddle levers held on hand-held items, per player, with a keep-alive timeout. */
public final class PaddleInput {
    private PaddleInput() {}

    /** a lever counts as released if no keep-alive came for this long */
    private static final int TIMEOUT_TICKS = 10;
    private static final Map<UUID, long[]> UNTIL = new HashMap<>();

    public static void update(ServerPlayer p, boolean dit, boolean dah) {
        long now = p.level().getGameTime();
        long[] u = UNTIL.computeIfAbsent(p.getUUID(), k -> new long[2]);
        u[0] = dit ? now + TIMEOUT_TICKS : 0;
        u[1] = dah ? now + TIMEOUT_TICKS : 0;
    }

    public static boolean dit(ServerPlayer p) {
        long[] u = UNTIL.get(p.getUUID());
        return u != null && p.level().getGameTime() <= u[0];
    }

    public static boolean dah(ServerPlayer p) {
        long[] u = UNTIL.get(p.getUUID());
        return u != null && p.level().getGameTime() <= u[1];
    }

    public static void forget(UUID id) {
        UNTIL.remove(id);
    }

    /** Sneak + mouse wheel: keyer speed of the paddle item in the main hand. */
    public static void changeWpm(ServerPlayer p, int delta) {
        ItemStack s = p.getMainHandItem();
        int wpm;
        if (s.getItem() instanceof HandheldRadioItem) {
            HandheldData d = HandheldRadioItem.data(s).withWpm(HandheldRadioItem.data(s).wpm() + delta);
            s.set(PgTelegraph.HANDHELD.get(), d);
            wpm = d.wpm();
        } else if (s.getItem() instanceof ClipKeyItem k && k.paddle) {
            ClipKeyItem.Leads l = ClipKeyItem.leads(s);
            l = l.withWpm(l.wpm() + delta);
            s.set(PgTelegraph.CLIP_LEADS.get(), l);
            wpm = l.wpm();
        } else return;
        p.displayClientMessage(Component.translatable("pgtelegraph.keyer.wpm", wpm).withStyle(ChatFormatting.GOLD), true);
    }
}
