package com.sparkgap.pgtelegraph.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sparkgap.pgtelegraph.PgTelegraph;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectric;

import java.util.List;

/**
 * Clip-lead keys (집게 전건): a key you carry and clip straight onto any two terminals with test
 * leads, like a multimeter, instead of placing and wiring a block. The contact between the leads
 * is real: it joins the Power Grid circuit and burns if overloaded.
 * <ul>
 *   <li>straight (paddle = false): hold right-click to close the contact</li>
 *   <li>paddle (paddle = true): built-in iambic keyer, left click = dit, right click = dah,
 *       sneak + mouse wheel = speed</li>
 * </ul>
 * Sneak + right-click a terminal clips the next lead on; sneak + right-click the air unclips both.
 */
public class ClipKeyItem extends Item {
    /** the two lead clips (terminal -1 = not clipped) and the keyer speed */
    public record Leads(long aPos, int aTerm, long bPos, int bTerm, int wpm) {
        public static final Leads NONE = new Leads(0, -1, 0, -1, 15);
        public static final Codec<Leads> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("a").forGetter(Leads::aPos),
                Codec.INT.fieldOf("at").forGetter(Leads::aTerm),
                Codec.LONG.fieldOf("b").forGetter(Leads::bPos),
                Codec.INT.fieldOf("bt").forGetter(Leads::bTerm),
                Codec.INT.optionalFieldOf("wpm", 15).forGetter(Leads::wpm)
        ).apply(i, Leads::new));
        public static final StreamCodec<ByteBuf, Leads> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Leads::aPos,
                ByteBufCodecs.VAR_INT, Leads::aTerm,
                ByteBufCodecs.VAR_LONG, Leads::bPos,
                ByteBufCodecs.VAR_INT, Leads::bTerm,
                ByteBufCodecs.VAR_INT, Leads::wpm,
                Leads::new);

        public boolean hasA() {
            return aTerm >= 0;
        }

        public boolean hasB() {
            return bTerm >= 0;
        }

        public boolean complete() {
            return hasA() && hasB();
        }

        public Leads withWpm(int w) {
            return new Leads(aPos, aTerm, bPos, bTerm, com.sparkgap.pgtelegraph.radio.HandheldData.clampWpm(w));
        }

        public Leads cleared() {
            return new Leads(0, -1, 0, -1, wpm);
        }
    }

    public final boolean paddle;
    public final float maxVolts, maxAmps;

    public ClipKeyItem(Properties properties, boolean paddle, float maxVolts, float maxAmps) {
        super(properties.stacksTo(1));
        this.paddle = paddle;
        this.maxVolts = maxVolts;
        this.maxAmps = maxAmps;
    }

    public static Leads leads(ItemStack stack) {
        Leads l = stack.get(PgTelegraph.CLIP_LEADS.get());
        return l == null ? Leads.NONE : l;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        IElectric el = IElectric.getAt(level, pos);
        if (el == null) return InteractionResult.PASS;
        int term = el.terminalIndexAt(state, ctx.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ()));
        if (term < 0) return InteractionResult.PASS;
        if (!level.isClientSide) {
            ItemStack stack = ctx.getItemInHand();
            Leads l = leads(stack);
            long p = pos.asLong();
            boolean sameAsA = l.hasA() && l.aPos() == p && l.aTerm() == term;
            int which;
            if (!l.hasA() || l.complete()) {
                l = new Leads(p, term, 0, -1, l.wpm());
                which = 1;
            } else if (sameAsA) {
                player.displayClientMessage(Component.translatable("pgtelegraph.clip.same").withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            } else {
                l = new Leads(l.aPos(), l.aTerm(), p, term, l.wpm());
                which = 2;
            }
            stack.set(PgTelegraph.CLIP_LEADS.get(), l);
            player.displayClientMessage(Component.translatable("pgtelegraph.clip.attached", which,
                    state.getBlock().getName(), term + 1).withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHAIN_PLACE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.6f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Leads l = leads(stack);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && (l.hasA() || l.hasB())) {
                stack.set(PgTelegraph.CLIP_LEADS.get(), l.cleared());
                player.displayClientMessage(Component.translatable("pgtelegraph.clip.detached").withStyle(ChatFormatting.YELLOW), true);
                level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.CHAIN_BREAK,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.6f);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (paddle || !l.complete()) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand); // contact closed while held
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return leads(stack).complete();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        Leads l = leads(stack);
        tip.add(Component.translatable(l.complete() ? "pgtelegraph.clip.state.both" : l.hasA() ? "pgtelegraph.clip.state.one"
                : "pgtelegraph.clip.state.none").withStyle(l.complete() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (paddle) tip.add(Component.translatable("pgtelegraph.keyer.wpm", l.wpm()).withStyle(ChatFormatting.GOLD));
    }
}
