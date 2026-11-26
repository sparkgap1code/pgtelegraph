package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Bodycam (바디캠): a camera worn in the chest armour slot, paired with an analog TV transmitter.
 * Worn, it films what its wearer actually sees (the frame the wearer's own game has just drawn -
 * no second rendering) and sends it over a short video link to its transmitter, which puts it on
 * the air in place of a TV camera block.
 * Sneak + R-click a TV transmitter: pair. R-click: put it on.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public class BodycamItem extends Item {
    /** how far the wearer may be from the paired transmitter (the video link) */
    public static final double LINK_RANGE = 300;
    public static final int FPS = 30; // NTSC: 30 frames a second

    public BodycamItem(Properties p) {
        super(p);
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.CHEST;
    }

    private static CompoundTag tag(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    /** the paired transmitter, or null */
    public static BlockPos paired(ItemStack s) {
        CompoundTag t = tag(s);
        return t.contains("Tx") ? BlockPos.of(t.getLong("Tx")) : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Player p = c.getPlayer();
        if (p == null || !p.isShiftKeyDown() || !(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof TvTransmitterBlockEntity))
            return InteractionResult.PASS;
        if (!c.getLevel().isClientSide) {
            CompoundTag t = tag(c.getItemInHand());
            t.putLong("Tx", c.getClickedPos().asLong());
            t.putString("Dim", c.getLevel().dimension().location().toString());
            c.getItemInHand().set(DataComponents.CUSTOM_DATA, CustomData.of(t));
            p.displayClientMessage(Component.translatable("pgtelegraph.bodycam.paired", c.getClickedPos().toShortString())
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) return InteractionResultHolder.fail(s);
        if (!level.isClientSide) {
            player.setItemSlot(EquipmentSlot.CHEST, s.copy());
            s.setCount(0);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        BlockPos tx = paired(s);
        tip.add((tx == null ? Component.translatable("pgtelegraph.bodycam.unpaired")
                : Component.translatable("pgtelegraph.bodycam.paired", tx.toShortString()))
                .withStyle(tx == null ? ChatFormatting.RED : ChatFormatting.GRAY));
    }

    /** the key a wearer's picture goes under */
    public static BlockPos key(UUID id) {
        return BlockPos.of(id.getLeastSignificantBits() ^ Long.rotateLeft(id.getMostSignificantBits(), 23) ^ 0x5B0D_CA30_0000L);
    }

    private record Wearer(BlockPos key, long tick) {}

    /** transmitter -> the bodycam paired with it that is worn within range right now */
    private static final Map<Level, Map<BlockPos, Wearer>> WORN = new WeakHashMap<>();

    /** the picture key of a bodycam feeding this transmitter, or null */
    public static BlockPos feeding(Level level, BlockPos tx) {
        Map<BlockPos, Wearer> m = WORN.get(level);
        Wearer w = m == null ? null : m.get(tx);
        return w == null || level.getGameTime() - w.tick > 5 ? null : w.key;
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack cam = p.getItemBySlot(EquipmentSlot.CHEST);
        if (!(cam.getItem() instanceof BodycamItem)) return;
        BlockPos tx = paired(cam);
        Level level = p.level();
        if (tx == null || !level.dimension().location().toString().equals(tag(cam).getString("Dim"))) return;
        if (p.distanceToSqr(tx.getCenter()) > LINK_RANGE * LINK_RANGE) return;
        long t = level.getGameTime();
        BlockPos key = key(p.getUUID());
        WORN.computeIfAbsent(level, l -> new HashMap<>()).put(tx.immutable(), new Wearer(key, t));
        TvFrames.filmedBy(level, key, p.getUUID());
        // the wearer's own game films it: its own view, no second rendering
        if (t % 20 == 0) PacketDistributor.sendToPlayer(p, new TvNet.Film(key, 0, 0, 0, Float.NaN, 0, 60));
    }
}
