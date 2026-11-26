package com.sparkgap.pgtelegraph.media;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * /pgvideo list | import <file> | export - video files on the server's machine
 * (game folder/pgtelegraph_videos/import and /export), through ffmpeg. Hold a video tape (or a
 * camcorder with a tape in it). Only for the world's owner or an operator.
 */
final class VideoCommand {
    private VideoCommand() {}

    private static boolean allowed(CommandSourceStack s) {
        return s.hasPermission(2) || s.getPlayer() != null && s.getServer().isSingleplayerOwner(s.getPlayer().getGameProfile());
    }

    private static ItemStack held(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        if (s.getItem() instanceof VideoTapeItem && s.getCount() == 1) return s;
        if (s.getItem() instanceof CamcorderItem && CamcorderItem.loaded(s) && !CamcorderItem.recording(s)) return s;
        return null;
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pgvideo").requires(VideoCommand::allowed)
                .then(Commands.literal("list").executes(c -> {
                    Path dir = VideoStore.importDir();
                    try {
                        Files.createDirectories(dir);
                        try (Stream<Path> st = Files.list(dir)) {
                            StringBuilder sb = new StringBuilder(dir.toString());
                            st.filter(Files::isRegularFile).forEach(f -> sb.append("\n  ").append(f.getFileName()));
                            c.getSource().sendSystemMessage(Component.literal(sb.toString()));
                        }
                    } catch (Exception ex) {
                        c.getSource().sendFailure(Component.literal(ex.getMessage()));
                    }
                    return 1;
                }))
                .then(Commands.literal("import").then(Commands.argument("file", StringArgumentType.greedyString()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack tape = held(p);
                    if (tape == null) {
                        c.getSource().sendFailure(Component.translatable("pgtelegraph.video.hold_tape"));
                        return 0;
                    }
                    String name = StringArgumentType.getString(c, "file").trim();
                    Path dir = VideoStore.importDir().toAbsolutePath().normalize();
                    Path src = dir.resolve(name).normalize();
                    if (!src.startsWith(dir) || !Files.isRegularFile(src)) {
                        c.getSource().sendFailure(Component.translatable("pgtelegraph.video.no_file", dir.toString()));
                        return 0;
                    }
                    String old = VideoTapeItem.id(tape);
                    if (VideoStore.validId(old)) VideoStore.erase(p.getServer(), old);
                    VideoTapeItem.clear(tape);
                    String id = VideoTapeItem.ensureId(tape);
                    String title = name.replaceFirst("\\.[^.]+$", "");
                    VideoTapeItem.setInfo(tape, title, 0);
                    p.displayClientMessage(Component.translatable("pgtelegraph.video.importing", name), false);
                    VideoStore.importFile(p.getServer(), id, src, err -> {
                        int n = VideoStore.frames(p.getServer(), id);
                        for (ItemStack s : p.getInventory().items)
                            if (id.equals(VideoTapeItem.id(s))) VideoTapeItem.setInfo(s, title, n);
                        p.displayClientMessage(err == null ? Component.translatable("pgtelegraph.video.imported", VideoTapeItem.time(n / VideoStore.FPS))
                                : Component.literal("ffmpeg: " + err).withStyle(ChatFormatting.RED), false);
                    });
                    return 1;
                })))
                .then(Commands.literal("export").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ItemStack tape = held(p);
                    String id = tape == null ? "" : VideoTapeItem.id(tape);
                    if (!VideoStore.validId(id) || VideoStore.frames(p.getServer(), id) == 0) {
                        c.getSource().sendFailure(Component.translatable("pgtelegraph.video.hold_tape"));
                        return 0;
                    }
                    String title = VideoTapeItem.tag(tape).getString("Title").replaceAll("[^\\w\\-가-힣 ]", "_").trim();
                    Path out = VideoStore.exportDir().resolve((title.isEmpty() ? "tape" : title) + "_" + id.substring(0, 6) + ".mp4");
                    VideoStore.exportFile(p.getServer(), id, out, err -> p.displayClientMessage(err == null
                            ? Component.translatable("pgtelegraph.video.exported", out.toString())
                            : Component.literal("ffmpeg: " + err).withStyle(ChatFormatting.RED), false));
                    return 1;
                })));
    }
}
