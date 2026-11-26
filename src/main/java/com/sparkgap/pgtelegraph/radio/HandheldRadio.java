package com.sparkgap.pgtelegraph.radio;

import com.sparkgap.pgtelegraph.PgTelegraph;
import com.sparkgap.pgtelegraph.network.HandheldStatePayload;
import com.sparkgap.pgtelegraph.parts.BatteryState;
import com.sparkgap.pgtelegraph.parts.Chassis;
import com.sparkgap.pgtelegraph.parts.PartDef;
import com.sparkgap.pgtelegraph.parts.PartItem;
import com.sparkgap.pgtelegraph.parts.PartSlot;
import com.sparkgap.pgtelegraph.parts.Wreckage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of handheld radios. Per player, the first switched-on radio (main hand, off hand,
 * then inventory) is the active one. Everything comes from the parts inside it:
 * battery chemistry and charge, regulator efficiency/noise, LNA noise floor, IF filter, PA power
 * and efficiency, band module. Losses heat the radio (60 J/K, 0.3 W/K to air): protected packs
 * cut out at 60 °C, a bare Li-ion pack past 80 °C goes into thermal runaway and takes the whole
 * radio with it.
 */
@EventBusSubscriber(modid = PgTelegraph.MODID)
public final class HandheldRadio {
    private HandheldRadio() {}

    private static final double RUBBER_DUCK_LOSS_DB = 7.15; // 2.15 dBi whip -> about -5 dBi
    private static final double RX_WATTS = 0.4, HEAT_CAP = 60, COOLING = 0.3, AMBIENT = 20;
    private static final int RUNAWAY_TICKS = 60;

    private static final class State {
        final Reception.TerrainCache terrain = new Reception.TerrainCache();
        double usedWh, tempC = AMBIENT;
        boolean bmsCut;
        int runaway = -1;
        int sentTone = -1;
        float sentVol = -1, sentNoise = -1;
        boolean sentOn, sentKeyed;
        int tick;
        double heightM = 1.6;
        final com.sparkgap.pgtelegraph.block.IambicKeyer keyer = new com.sparkgap.pgtelegraph.block.IambicKeyer();
        /** what the handheld hears, for the player's simulated speaker */
        final RxChannel channel = new RxChannel();
        boolean rxSide, rxOpen = true;
        /** riding a contraption with a vehicle radio: 50 W through its roof whip, vehicle power */
        boolean vehicle;
    }

    /** send the channel when it changed (keying edges at once, levels every half second) */
    private static void sendRx(ServerPlayer p, State st, boolean dirty) {
        if (dirty) PacketDistributor.sendToPlayer(p, new com.sparkgap.pgtelegraph.network.HandheldRxPayload(st.channel.save()));
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** push-to-talk from a manpack wearer: keyed until this game tick */
    private static final java.util.Map<UUID, Long> PTT = new java.util.HashMap<>();

    public static void ptt(ServerPlayer p, boolean down) {
        if (down) PTT.put(p.getUUID(), p.level().getGameTime() + 15);
        else PTT.remove(p.getUUID());
    }

    /** a DTMF key pressed on the keypad: keys the radio for the tone (game tick it ends) */
    private static final java.util.Map<UUID, Long> DTMF = new java.util.HashMap<>();

    public static void dtmf(ServerPlayer p, char c) {
        if (!Dtmf.valid(c) || active(p) == null) return;
        Level level = p.level();
        DTMF.put(p.getUUID(), level.getGameTime() + Dtmf.TONE_TICKS);
        Dtmf.send(level, sourceKey(p.getUUID()), c);
        int i = Dtmf.KEYS.indexOf(c);
        level.playSound(null, p, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), net.minecraft.sounds.SoundSource.PLAYERS,
                0.35f, 0.9f + (i / 4) * 0.12f + (i % 4) * 0.05f);
    }

    public static void setTone(ServerPlayer p, InteractionHand hand, int idx) {
        ItemStack s = p.getItemInHand(hand);
        if (s.getItem() instanceof HandheldRadioItem) s.set(PgTelegraph.CTCSS.get(), Ctcss.clamp(idx));
    }

    public static BlockPos sourceKey(UUID id) {
        return BlockPos.of(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        STATES.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        Level level = player.level();
        BlockPos key = sourceKey(player.getUUID());
        State st = STATES.computeIfAbsent(player.getUUID(), u -> new State());
        st.tick++;
        ItemStack radio = active(player);
        if (radio == null) {
            RadioWorld.silence(level, key);
            st.tempC += (AMBIENT - st.tempC) * 0.01;
            st.runaway = -1;
            send(player, st, false, false, 700, 0, 0);
            return;
        }
        NonNullList<ItemStack> parts = Chassis.of(radio, PartSlot.Layout.HANDHELD);
        Chassis.Stats stats = Chassis.stats(parts, PartSlot.Layout.HANDHELD);
        HandheldData d = HandheldRadioItem.data(radio);

        // ---- thermal runaway in progress: nothing can stop it now
        if (st.runaway >= 0) {
            RadioWorld.silence(level, key);
            Wreckage.smoulder(level, player.position().add(0, 1, 0), st.runaway);
            if (st.runaway-- == 0) {
                replace(player, radio, new ItemStack(PgTelegraph.EXPLODED_RADIO.get()));
                Wreckage.explode(level, player.position().add(0, 1, 0));
                com.sparkgap.pgtelegraph.Awards.award(player, "boom");
                st.runaway = -1;
                st.tempC = AMBIENT;
            }
            return;
        }

        int bi = PartSlot.Layout.HANDHELD.indexOf(PartSlot.BATTERY);
        ItemStack packStack = parts.get(bi);
        PartDef pack = stats.battery();
        BatteryState bs = PartItem.battery(packStack);
        if (pack == null || bs == null || bs.chargeWh() <= 0) {
            RadioWorld.silence(level, key);
            send(player, st, false, false, 700, 0, 0);
            return;
        }
        // protected packs: over-temperature cut-off with hysteresis
        if (pack.protectedCells()) {
            if (st.tempC > 60) st.bmsCut = true;
            else if (st.tempC < 45) st.bmsCut = false;
        } else st.bmsCut = false;
        if (st.tempC > pack.chemistry().runawayC()) {
            if (pack.chemistry() == PartDef.Chemistry.LI_ION) {
                st.runaway = RUNAWAY_TICKS;
                player.displayClientMessage(Component.translatable("pgtelegraph.handheld.runaway").withStyle(ChatFormatting.RED), true);
                return;
            }
            Wreckage.vent(level, player.position().add(0, 1, 0));
            parts.set(bi, Wreckage.burnt(packStack));
            Chassis.store(radio, parts);
            return;
        }
        if (st.bmsCut) {
            RadioWorld.silence(level, key);
            send(player, st, false, false, 700, 0, 0);
            cool(st, 0);
            return;
        }

        boolean bandOk = stats.covers(d.freqHz());
        boolean manpack = radio.getItem() instanceof ManpackItem;
        boolean operated;
        if (manpack) { // push to talk (V), or still talking
            Long until = PTT.get(player.getUUID());
            operated = (until != null && until >= level.getGameTime()) || com.sparkgap.pgtelegraph.media.VoiceAir.talking(level, key);
        } else if (d.paddle()) {
            // built-in keyer: levers only work with the radio in the main hand
            boolean main = player.getMainHandItem() == radio;
            operated = st.keyer.tick(main && PaddleInput.dit(player), main && PaddleInput.dah(player), d.wpm());
        } else {
            operated = player.isUsingItem() && player.getUseItem() == radio;
        }
        Long dt = DTMF.get(player.getUUID());
        boolean tone = dt != null && dt >= level.getGameTime();
        operated |= tone;
        boolean keyed = operated && bandOk && stats.paMaxW() > 0;
        if (st.tick % 20 == 0) {
            st.heightM = heightAboveTerrain(level, player);
            st.vehicle = com.sparkgap.pgtelegraph.block.MobileRadioBlockEntity.riding(player, PgTelegraph.MOBILE_RADIO.get());
        }
        BlockPos at = BlockPos.containing(player.getEyePosition());
        double f = d.freqHz() / 1e6;
        // a handheld's rubber duck; a manpack's 1 m whip standing above the shoulder
        AntennaLink duck = st.vehicle
                ? new AntennaLink(at, AntennaLink.Kind.VHF_WHIP, 0.5, f, st.heightM + 1.5, 0, 0, Double.MAX_VALUE,
                List.of(), null, 3, 0.5, null, 0, null)
                : manpack
                ? new AntennaLink(at, AntennaLink.Kind.VHF_WHIP, 1.0, f, st.heightM + 0.6, 0, 0, Double.MAX_VALUE,
                List.of(), null, 3, 1.0, null, 0, null)
                : new AntennaLink(at, AntennaLink.Kind.VHF_WHIP, 0.17, f, st.heightM, 0, 0, Double.MAX_VALUE,
                List.of(), null, 3, RUBBER_DUCK_LOSS_DB, null, 0, null);

        double volts = bs.volts(pack);
        double draw = RX_WATTS / stats.regEfficiency(), rf = 0;
        if (keyed) {
            rf = (manpack ? Math.min(ManpackItem.POWER_W[d.power()], stats.paMaxW() * 2.5) // a bigger heatsink and supply
                    : Math.min(HandheldData.POWER_W[d.power()], stats.paMaxW())) * Math.min(1, Math.pow(volts / 7.4, 2));
            if (st.vehicle) rf = com.sparkgap.pgtelegraph.block.MobileRadioBlockEntity.WATTS; // the vehicle set, on the vehicle's supply
            else draw += rf / stats.paEfficiency() / stats.regEfficiency();
            RadioWorld.emit(level, new RadioWorld.Emission(key, at, d.freqHz(),
                    RadioMath.dbm(rf) + duck.gainDb(f), st.heightM, level.getGameTime(), null, 360, 0));
            com.sparkgap.pgtelegraph.radio.Ctcss.mark(level, key, radio.getOrDefault(PgTelegraph.CTCSS.get(), 0));
            // a touch-tone burst goes out as NFM; plain keying is a bare carrier
            RadioWorld.setModulation(level, key, manpack || tone ? RadioWorld.MOD_FM : RadioWorld.MOD_CARRIER);
            if (manpack) { // NFM voice: the wearer's microphone goes out on it
                RadioWorld.setModulation(level, key, RadioWorld.MOD_FM);
                com.sparkgap.pgtelegraph.media.VoiceAir.onAir(level, key, key, RadioWorld.MOD_FM);
            }
            send(player, st, true, true, 700, manpack ? 0f : 0.35f, 0);
            if (!st.channel.sidetone) {
                st.channel.sidetone = true;
                sendRx(player, st, true);
            }
        } else {
            if (st.channel.sidetone) {
                st.channel.sidetone = false;
                sendRx(player, st, true);
            }
            RadioWorld.silence(level, key);
            if (bandOk) {
                Reception.Result r = Reception.listen(level, duck, d.freqHz(), 0, key, player.getUUID().getLeastSignificantBits(),
                        st.terrain, stats.rxNoiseDbm(), stats.filterHz());
                int tsq = radio.getOrDefault(PgTelegraph.CTCSS.get(), 0);
                boolean toneOk = Ctcss.passes(level, tsq, r.bestEmission());
                boolean open = toneOk && r.hasSignal() && r.best() - r.noiseDbm() >= d.squelch();
                boolean dirty = st.channel.track(level);
                if (st.tick % 10 == 0) dirty |= st.channel.refresh(level, duck, d.freqHz(), key, player.getUUID().getLeastSignificantBits(),
                        st.terrain, stats.rxNoiseDbm(), manpack ? 12_500 : Math.max(stats.filterHz(), 3000),
                        manpack ? RxChannel.FM_NARROW : RxChannel.CW, manpack ? 12_500 : stats.filterHz(),
                        open || (d.squelch() == 0 && tsq == 0));
                if (manpack && toneOk && r.hasSignal() && r.bestEmission() != null) // whose voice reaches the earpiece
                    com.sparkgap.pgtelegraph.media.VoiceAir.listenPack(level, key, player.getUUID(), r.bestEmission().source(),
                            (float) (r.best() - r.noiseDbm()));
                else if (st.channel.open != (open || d.squelch() == 0)) {
                    st.channel.open = open || d.squelch() == 0;
                    dirty = true;
                }
                sendRx(player, st, dirty);
                if (open && r.best() - r.noiseDbm() >= 6) Reception.awardHeard((net.minecraft.server.level.ServerLevel) level, r, duck, player, at);
                send(player, st, true, false, Reception.toneHz(r, 700), manpack ? 0f : Reception.toneVolume(r),
                        open || d.squelch() == 0 ? Reception.noiseVolume(r) * 0.6f : 0.02f);
                if (st.tick % 10 == 0 && holding(player, radio)) {
                    actionBar(player, d, Math.max(r.best(), r.floorDbm()), bs, pack, st);
                }
            } else {
                send(player, st, true, false, 700, 0, 0.02f);
                if (!st.channel.signals.isEmpty() || st.channel.open) { // out of band: nothing comes through
                    st.channel.clear();
                    st.channel.open = false;
                    sendRx(player, st, true);
                }
                if (st.tick % 10 == 0 && holding(player, radio)) {
                    player.displayClientMessage(Component.translatable("pgtelegraph.handheld.out_of_band").withStyle(ChatFormatting.RED), true);
                }
            }
        }
        cool(st, draw - rf);

        st.usedWh += draw / 20.0 / 3600.0;
        if (st.tick % 20 == 0 && st.usedWh > 0) {
            BatteryState next = bs.withCharge(bs.chargeWh() - st.usedWh, pack);
            st.usedWh = 0;
            if (next.chargeWh() <= 0) next = flattened(next, pack);
            ItemStack updated = packStack.copy();
            if (pack.chemistry() == PartDef.Chemistry.ALKALINE && next.chargeWh() <= 0) {
                updated = Wreckage.burnt(packStack); // flat alkalines leak
            } else {
                updated.set(PgTelegraph.BATTERY_STATE.get(), next);
            }
            parts.set(bi, updated);
            Chassis.store(radio, parts);
        }
    }

    /** What running a pack flat does to it. */
    private static BatteryState flattened(BatteryState b, PartDef pack) {
        if (pack.protectedCells()) return b;
        return switch (pack.chemistry()) {
            case LI_ION -> b.abused(0.2, true);   // copper dissolves: charging it again is a gamble
            case NIMH -> b.abused(0.1, false);    // cell reversal
            default -> b;
        };
    }

    private static void cool(State st, double heatW) {
        st.tempC += (heatW - (st.tempC - AMBIENT) * COOLING) / 20.0 / HEAT_CAP;
    }

    private static boolean holding(ServerPlayer p, ItemStack radio) {
        return p.getMainHandItem() == radio || p.getOffhandItem() == radio || radio.getItem() instanceof ManpackItem;
    }

    private static void actionBar(ServerPlayer player, HandheldData d, double dbm, BatteryState bs, PartDef pack, State st) {
        player.displayClientMessage(Component.literal(com.sparkgap.pgtelegraph.block.RadioBlockEntity.formatFreq(d.freqHz()))
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal("  " + RadioMath.sMeter(dbm)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(String.format(java.util.Locale.ROOT, "  %.1f V %d%%  %.0f°C",
                        bs.volts(pack), Math.round(100 * bs.fraction(pack)), st.tempC))
                        .withStyle(st.tempC > 55 ? ChatFormatting.RED : ChatFormatting.GRAY)), true);
    }

    private static void replace(ServerPlayer p, ItemStack old, ItemStack with) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == old) {
                inv.setItem(i, with);
                return;
            }
        }
    }

    private static ItemStack active(ServerPlayer p) {
        ItemStack back = ManpackItem.worn(p);
        if (back != null) return back;
        for (ItemStack s : new ItemStack[]{p.getMainHandItem(), p.getOffhandItem()}) {
            if (isOn(s) && !(s.getItem() instanceof ManpackItem)) return s;
        }
        for (ItemStack s : p.getInventory().items) if (isOn(s) && !(s.getItem() instanceof ManpackItem)) return s;
        return null;
    }

    private static boolean isOn(ItemStack s) {
        return s.getItem() instanceof HandheldRadioItem && HandheldRadioItem.data(s).on();
    }

    /** eye height over the mean surface around the player (loaded chunks only) */
    private static double heightAboveTerrain(Level level, ServerPlayer p) {
        int bx = p.getBlockX(), bz = p.getBlockZ();
        double sum = 0;
        int n = 0;
        for (int[] o : new int[][]{{0, 0}, {48, 0}, {-48, 0}, {0, 48}, {0, -48}}) {
            int x = bx + o[0], z = bz + o[1];
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            sum += level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            n++;
        }
        double ground = n == 0 ? p.getY() : sum / n;
        return Math.max(1.5, p.getEyeY() - ground);
    }

    private static void send(ServerPlayer p, State st, boolean on, boolean keyed, int tone, float vol, float noise) {
        if (on == st.sentOn && keyed == st.sentKeyed && tone == st.sentTone && Math.abs(vol - st.sentVol) < 0.06f
                && (vol > 0) == (st.sentVol > 0) && Math.abs(noise - st.sentNoise) < 0.04f) return;
        st.sentOn = on;
        st.sentKeyed = keyed;
        st.sentTone = tone;
        st.sentVol = vol;
        st.sentNoise = noise;
        PacketDistributor.sendToPlayer(p, new HandheldStatePayload(on, keyed, tone, vol, noise));
    }

    /** Applies panel settings from the client to the radio in that hand. */
    public static void applySettings(ServerPlayer p, InteractionHand hand, long hz, int power, int squelch, boolean on,
                                     boolean paddle, int wpm) {
        ItemStack s = p.getItemInHand(hand);
        if (!(s.getItem() instanceof HandheldRadioItem)) return;
        s.set(PgTelegraph.HANDHELD.get(), HandheldRadioItem.data(s).withSettings(hz, power, squelch, on, paddle, wpm));
    }
}
