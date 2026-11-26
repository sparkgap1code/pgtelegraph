package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Camcorder (캠코더): records what its holder sees onto the tape inside it (320 x 240, 15 frames
 * a second), while held in the main hand. Sneak + R-click with a tape in the off hand: load it
 * (a loaded one comes out first); sneak + R-click with an empty off hand: take the tape out;
 * R-click: record / stop.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public class CamcorderItem extends Item {
    public CamcorderItem(Properties p) {
        super(p.stacksTo(1));
    }

    public static boolean loaded(ItemStack s) {
        return VideoTapeItem.tag(s).getBoolean("Tape");
    }

    public static boolean recording(ItemStack s) {
        return VideoTapeItem.tag(s).getBoolean("Rec");
    }

    private static void setRec(ItemStack s, boolean on) {
        CompoundTag t = VideoTapeItem.tag(s);
        t.putBoolean("Rec", on);
        VideoTapeItem.put(s, t);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack cam = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(cam);
        if (level.isClientSide) return InteractionResultHolder.success(cam);
        ItemStack off = player.getOffhandItem();
        if (player.isShiftKeyDown()) {
            setRec(cam, false);
            if (loaded(cam)) { // take the tape out
                ItemStack tape = new ItemStack(PgTelegraph.VIDEO_TAPE.get());
                VideoTapeItem.copy(cam, tape);
                VideoTapeItem.clear(cam);
                CompoundTag t = VideoTapeItem.tag(cam);
                t.putBoolean("Tape", false);
                VideoTapeItem.put(cam, t);
                if (!player.getInventory().add(tape)) player.drop(tape, false);
                if (!(off.getItem() instanceof VideoTapeItem)) return InteractionResultHolder.success(cam);
                off = player.getOffhandItem();
            }
            if (off.getItem() instanceof VideoTapeItem) { // load the one in the off hand
                VideoTapeItem.copy(off, cam);
                CompoundTag t = VideoTapeItem.tag(cam);
                t.putBoolean("Tape", true);
                VideoTapeItem.put(cam, t);
                off.shrink(1);
                player.displayClientMessage(Component.translatable("pgtelegraph.camcorder.loaded"), true);
            }
            return InteractionResultHolder.success(cam);
        }
        if (!loaded(cam)) {
            player.displayClientMessage(Component.translatable("pgtelegraph.camcorder.no_tape").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(cam);
        }
        boolean on = !recording(cam);
        if (on) VideoTapeItem.ensureId(cam);
        setRec(cam, on);
        player.displayClientMessage(Component.translatable(on ? "pgtelegraph.camcorder.rec" : "pgtelegraph.camcorder.stop")
                .withStyle(on ? ChatFormatting.RED : ChatFormatting.GRAY), true);
        return InteractionResultHolder.success(cam);
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return recording(s);
    }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        if (!loaded(s)) {
            tip.add(Component.translatable("pgtelegraph.camcorder.no_tape").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (recording(s)) tip.add(Component.literal("● REC").withStyle(ChatFormatting.RED));
        VideoTapeItem.info(s, tip);
    }

    public static BlockPos key(UUID id) {
        return BlockPos.of(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17) ^ 0x3C4C_0D00_0000L);
    }

    /** camcorder picture key -> the tape it records onto */
    private static final Map<BlockPos, String> RECORDING = new HashMap<>();

    /** a frame the holder's game filmed for its camcorder */
    static void frame(net.minecraft.server.MinecraftServer s, BlockPos key, byte[] jpeg) {
        String id;
        synchronized (RECORDING) {
            id = RECORDING.get(key);
        }
        if (id != null) VideoStore.append(s, id, jpeg);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        BlockPos key = key(p.getUUID());
        ItemStack cam = p.getMainHandItem();
        boolean rec = cam.getItem() instanceof CamcorderItem && loaded(cam) && recording(cam);
        if (!rec) {
            synchronized (RECORDING) {
                RECORDING.remove(key);
            }
            return;
        }
        String id = VideoTapeItem.ensureId(cam);
        long t = p.level().getGameTime();
        int n = VideoStore.frames(p.getServer(), id);
        if (n >= VideoStore.MAX_FRAMES) { // end of the tape
            setRec(cam, false);
            p.displayClientMessage(Component.translatable("pgtelegraph.video.tape_end").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        synchronized (RECORDING) {
            RECORDING.put(key, id);
        }
        TvFrames.filmedBy(p.level(), key, p.getUUID());
        if (t % 20 == 0) {
            VideoTapeItem.setInfo(cam, null, n);
            // the holder's own game films it: small (320 x 240, 15 frames a second)
            PacketDistributor.sendToPlayer(p, new TvNet.Film(key, 0, 0, 0, Float.NaN, 1, 60));
        }
    }
}
