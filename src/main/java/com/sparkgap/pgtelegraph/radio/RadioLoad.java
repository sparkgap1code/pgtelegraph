package com.sparkgap.pgtelegraph.radio;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server load guard. When the server's average tick time goes over {@link #ENTER_MS} the radios
 * switch to a simpler reception model - the channel, terrain and ground-path checks are redone four
 * times less often and moon bounce is skipped - and switch back once it is under {@link #LEAVE_MS}.
 * Sound on the clients is not affected (it runs there).
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class RadioLoad {
    private RadioLoad() {}

    public static final double ENTER_MS = 40, LEAVE_MS = 30;
    private static volatile boolean simple;
    private static double lastMs;

    /** true while the server is overloaded: use the simple model */
    public static boolean simple() {
        return simple;
    }

    /** a refresh interval, four times longer while overloaded */
    public static int every(int ticks) {
        return simple ? ticks * 4 : ticks;
    }

    public static double mspt() {
        return lastMs;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 20 != 0) return;
        lastMs = e.getServer().getAverageTickTimeNanos() / 1e6;
        if (!simple && lastMs > ENTER_MS) {
            simple = true;
            PgTelegraph.LOGGER.info("[pgtelegraph] server busy ({} ms/tick): simple reception model", String.format("%.1f", lastMs));
        } else if (simple && lastMs < LEAVE_MS) {
            simple = false;
            PgTelegraph.LOGGER.info("[pgtelegraph] server load back to {} ms/tick: full reception model", String.format("%.1f", lastMs));
        }
    }
}
