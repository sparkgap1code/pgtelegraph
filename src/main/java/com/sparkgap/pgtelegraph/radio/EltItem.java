package com.sparkgap.pgtelegraph.radio;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.util.List;

/**
 * Emergency locator transmitter (조난 비컨, ELT): 121.5 MHz, 0.1 W, keyed half a second on, half a
 * second off. Use toggles it; it switches itself on when its carrier falls hard (10+ blocks) - so
 * friends can find you with a direction finder. Works anywhere in the inventory.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public class EltItem extends Item {
    public static final long HZ = 121_500_000L;
    public static final double WATTS = 0.1;

    public EltItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean on(ItemStack s) {
        return Boolean.TRUE.equals(s.get(PgTelegraph.ELT_ON.get()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean next = !on(s);
            s.set(PgTelegraph.ELT_ON.get(), next);
            player.displayClientMessage(Component.translatable(next ? "pgtelegraph.elt.on" : "pgtelegraph.elt.off")
                    .withStyle(next ? ChatFormatting.RED : ChatFormatting.GRAY), true);
            level.playSound(null, player.blockPosition(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.5f, 1.5f);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return on(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !on(stack) || !(entity instanceof ServerPlayer p)) return;
        BlockPos key = HandheldRadio.sourceKey(p.getUUID()).offset(0, -77_777, 0);
        if ((level.getGameTime() / 10) % 2 == 0) {
            BlockPos at = BlockPos.containing(p.getEyePosition());
            RadioWorld.emit(level, new RadioWorld.Emission(key, at, HZ, RadioMath.dbm(WATTS), 1.6, level.getGameTime(),
                    null, 360, 0));
        } else RadioWorld.silence1(level, key);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getDistance() < 10 || !(e.getEntity() instanceof ServerPlayer p)) return;
        for (ItemStack s : p.getInventory().items) {
            if (s.getItem() instanceof EltItem && !on(s)) {
                s.set(PgTelegraph.ELT_ON.get(), true);
                p.displayClientMessage(Component.translatable("pgtelegraph.elt.triggered").withStyle(ChatFormatting.RED), false);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("121.500 MHz, 0.1 W").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable(on(stack) ? "pgtelegraph.elt.on" : "pgtelegraph.elt.off")
                .withStyle(on(stack) ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
    }
}
