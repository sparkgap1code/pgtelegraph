package com.sparkgap.pgtelegraph.item;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sparkgap.pgtelegraph.Awards;
import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Contest: "/pgcontest start &lt;minutes&gt;" opens a contest for everyone on the server; every
 * different callsign a player logs in a logbook during it scores one point. At the end the
 * scores are announced and the winner gets an advancement. "/pgcontest stop" ends it early.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class Contest {
    private Contest() {}

    private static long endTick = -1;
    private static final Map<UUID, Set<String>> LOGGED = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();
    private static final Map<UUID, Set<String>> MULTS = new HashMap<>();
    private static final Map<UUID, Integer> DUPES = new HashMap<>();

    /** the multiplier of a callsign: its prefix up to and including the first digits (HL1, JA2, W6, 4X6, UA9) */
    public static String prefix(String call) {
        var m = java.util.regex.Pattern.compile("^([A-Z0-9]*?[0-9]+)").matcher(call);
        return m.find() ? m.group(1) : call;
    }

    /** a full exchange in a log line: a report (5NN / 599 / 579 ...) and a serial number */
    public static boolean hasExchange(String entry) {
        return java.util.regex.Pattern.compile("\\b(5NN|[1-5][1-9][1-9])\\s+[0-9TNO]{1,4}\\b").matcher(entry.toUpperCase(java.util.Locale.ROOT).replace('|', ' ').replaceAll("\s+", " ")).find();
    }

    public static int score(UUID id) {
        return LOGGED.getOrDefault(id, Set.of()).size() * Math.max(1, MULTS.getOrDefault(id, Set.of()).size());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("pgcontest")
                .then(Commands.literal("start").then(Commands.argument("minutes", IntegerArgumentType.integer(1, 240))
                        .executes(c -> {
                            start(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "minutes"));
                            return 1;
                        })))
                .then(Commands.literal("stop").executes(c -> {
                    finish(c.getSource().getServer());
                    return 1;
                })));
    }

    static void start(MinecraftServer server, int minutes) {
        LOGGED.clear();
        NAMES.clear();
        MULTS.clear();
        DUPES.clear();
        endTick = server.overworld().getGameTime() + minutes * 1200L;
        broadcast(server, Component.translatable("pgtelegraph.contest.start", minutes).withStyle(ChatFormatting.GOLD));
    }

    public static boolean running() {
        return endTick >= 0;
    }

    /** a logbook line was added; its first field is the other station's callsign */
    public static void logged(ServerPlayer p, String entry) {
        if (!running()) return;
        String call = entry.split("\\|")[0].trim().toUpperCase(java.util.Locale.ROOT);
        if (call.isEmpty()) return;
        NAMES.put(p.getUUID(), p.getGameProfile().getName());
        if (!hasExchange(entry)) {
            p.displayClientMessage(Component.translatable("pgtelegraph.contest.no_exchange").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (LOGGED.computeIfAbsent(p.getUUID(), u -> new HashSet<>()).add(call)) {
            boolean newMult = MULTS.computeIfAbsent(p.getUUID(), u -> new HashSet<>()).add(prefix(call));
            p.displayClientMessage(Component.translatable(newMult ? "pgtelegraph.contest.point_mult" : "pgtelegraph.contest.point",
                            LOGGED.get(p.getUUID()).size(), MULTS.get(p.getUUID()).size(), score(p.getUUID()))
                    .withStyle(newMult ? ChatFormatting.AQUA : ChatFormatting.GREEN), true);
        } else {
            DUPES.merge(p.getUUID(), 1, Integer::sum);
            p.displayClientMessage(Component.translatable("pgtelegraph.contest.dupe", call).withStyle(ChatFormatting.YELLOW), true);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (running() && e.getServer().overworld().getGameTime() >= endTick) finish(e.getServer());
    }

    static void finish(MinecraftServer server) {
        if (!running()) return;
        endTick = -1;
        List<Map.Entry<UUID, Set<String>>> rank = new ArrayList<>(LOGGED.entrySet());
        rank.sort((a, b) -> score(b.getKey()) - score(a.getKey()));
        broadcast(server, Component.translatable("pgtelegraph.contest.end").withStyle(ChatFormatting.GOLD));
        int place = 1;
        for (var en : rank) {
            if (place > 10) break;
            UUID id = en.getKey();
            broadcast(server, Component.translatable("pgtelegraph.contest.line", place++, NAMES.get(id), en.getValue().size(),
                    MULTS.getOrDefault(id, Set.of()).size(), DUPES.getOrDefault(id, 0), score(id)).withStyle(ChatFormatting.YELLOW));
        }
        if (!rank.isEmpty() && !rank.get(0).getValue().isEmpty()) {
            ServerPlayer w = server.getPlayerList().getPlayer(rank.get(0).getKey());
            if (w != null) Awards.award(w, "contest");
        }
    }

    private static void broadcast(MinecraftServer server, Component c) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.displayClientMessage(c, false);
    }
}
