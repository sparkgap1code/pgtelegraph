package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.radio.AntennaLink;
import com.sparkgap.pgtelegraph.radio.RadioMath;
import com.sparkgap.pgtelegraph.radio.RadioWorld;
import com.sparkgap.pgtelegraph.radio.Reception;
import com.sparkgap.pgtelegraph.radio.Terrain;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
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
 * FPV goggles (FPV 고글): an analog TV receiver worn on the head (helmet slot) with a small patch
 * antenna on top. Tuned to a TV transmitter or a bodycam's channel, the picture fills your view -
 * with everything an analog link does to it (snow, colour loss, tearing, rolling, ghosts).
 * Sneak + R-click: channel (50 MHz - 6 GHz). R-click: put them on.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public class FpvGogglesItem extends Item {
    public static final long LOW_HZ = 50_000_000L, HIGH_HZ = 6_000_000_000L, DEFAULT_HZ = 434_250_000L;
    /** where the goggles' picture is kept on the client (no block there) */
    public static final BlockPos GOGGLE_POS = new BlockPos(0, -30001, 0);

    public FpvGogglesItem(Properties p) {
        super(p.stacksTo(1));
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    public static long hz(ItemStack s) {
        Long f = s.get(PgTelegraph.ITEM_FREQ.get());
        return f == null ? DEFAULT_HZ : Math.max(LOW_HZ, Math.min(HIGH_HZ, f));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) com.sparkgap.pgtelegraph.client.ClientAccess.openItemTune(hand, hz(s));
            return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }
        if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return InteractionResultHolder.fail(s);
        if (!level.isClientSide) {
            player.setItemSlot(EquipmentSlot.HEAD, s.copy());
            s.setCount(0);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(RadioMath.formatMHz(hz(s) / 1e6)).withStyle(ChatFormatting.AQUA));
    }

    public static ItemStack worn(Player p) {
        ItemStack h = p.getItemBySlot(EquipmentSlot.HEAD);
        return h.getItem() instanceof FpvGogglesItem ? h : null;
    }

    private static final class State {
        final Reception.TerrainCache cache = new Reception.TerrainCache();
        BlockPos key;
        int lastSeq = -1, terrainScan;
        double obstruction;
        boolean sentTuned;
        float sentSnr = -999;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack g = worn(p);
        if (g == null) {
            STATES.remove(p.getUUID());
            return;
        }
        Level level = p.level();
        State st = STATES.computeIfAbsent(p.getUUID(), u -> new State());
        long t = level.getGameTime();
        long freq = hz(g);
        if (t % 10 == Math.floorMod(p.getUUID().hashCode(), 10)) {
            double f = freq / 1e6;
            BlockPos at = BlockPos.containing(p.getEyePosition());
            // a small patch antenna on top of the goggles: about 0 dBi, a little loss
            AntennaLink rl = new AntennaLink(at, AntennaLink.Kind.VHF_WHIP, 0.1, f, p.getEyeY() - p.getY() + 0.2, 0, 0, Double.MAX_VALUE,
                    List.of(), null, 0.5, 1.0, null, 0, null);
            Reception.Channel ch = Reception.channel(level, rl, freq, null, p.getUUID().getLeastSignificantBits(), st.cache, -134, 3_000_000, 4);
            Reception.Heard station = null, other = null;
            for (Reception.Heard h : ch.signals()) {
                boolean isTv = RadioWorld.modulation(level, h.e().source()) == RadioWorld.MOD_TV;
                if (station == null && isTv && Math.abs(h.df()) <= 500_000) station = h;
                else if (other == null && Math.abs(h.df()) <= 1_000_000) other = h;
            }
            boolean tuned = station != null;
            float snr = -99, ghost = 0, interf = -99;
            int interfDf = 0;
            BlockPos key = null;
            if (station != null) {
                double noise = ch.noiseDbmPerHz() + 10 * Math.log10(TvReceiverBlockEntity.VIDEO_BW);
                double fade = com.sparkgap.pgtelegraph.radio.Fading.carrierGain(station.fadeSeed(), t / 20.0, station.dopplerHz(), station.skyShare());
                snr = (float) (station.dbm() + 10 * Math.log10(Math.max(1e-6, fade)) - noise);
                if (--st.terrainScan <= 0) {
                    st.terrainScan = 4;
                    st.obstruction = Terrain.obstructionDb(level, station.e().antenna(), at, f);
                }
                ghost = (float) Math.max(0, Math.min(0.6, st.obstruction / 30 + 0.5 * station.skyShare()));
                interf = other == null ? -99 : (float) (other.dbm() - station.dbm());
                interfDf = other == null ? 0 : other.df() - station.df();
                key = TvTransmitterBlockEntity.TvAir.camera(level, station.e().source());
            }
            if (!java.util.Objects.equals(key, st.key)) st.lastSeq = -1; // a new station: start from its key frame
            st.key = key;
            if (tuned != st.sentTuned || Math.abs(snr - st.sentSnr) > 0.5 || t % 40 == 0) {
                st.sentTuned = tuned;
                st.sentSnr = snr;
                PacketDistributor.sendToPlayer(p, new TvNet.Goggles(tuned, snr, ghost, interf, interfDf));
            }
        }
        // every frame of the picture, in order (an H.264 stream cannot skip one)
        if (st.key != null && st.sentTuned && st.sentSnr > -6) {
            for (TvFrames.Frame fr : TvFrames.gop(level, st.key)) {
                if (fr.seq() <= st.lastSeq) continue;
                st.lastSeq = fr.seq();
                PacketDistributor.sendToPlayer(p, new TvNet.Down(GOGGLE_POS, fr.seq(), fr.jpeg()));
            }
        }
    }
}
