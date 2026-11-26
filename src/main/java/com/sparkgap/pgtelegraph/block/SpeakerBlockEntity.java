package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.sparkgap.pgtelegraph.media.SpeakerNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;
import java.util.Locale;

/**
 * A loudspeaker on an audio amplifier's speaker line. Two kinds:
 * <ul>
 *   <li>cone speaker (스피커): 92 dB at 1 m for 1 W, up to 100 W, broad - 6 dB down to the side, 12 dB behind</li>
 *   <li>horn loudspeaker (지향성 확성기), the long-range kind used across borders: 112 dB at 1 m for 1 W, up to 600 W,
 *   a 15 degree beam (6 dB down 7.5 degrees off its axis, 30 dB behind); its tilt is set with an empty hand</li>
 * </ul>
 * Loudness falls 6 dB for each doubling of the distance and air soaks up about 5 dB a kilometre; sound
 * takes its time to get there (343 m/s). Driven past 1.5 times its rating for 5 s, the voice coil burns.
 */
public class SpeakerBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final Spec SPEC = new Spec(110f, 700f, 1f, 120f, Spec.coil(8f, 10f));
    public static final int[] TILTS = {-30, -20, -10, -5, 0, 5, 10, 20, 30, 45, 60};

    private SwitchedWire coil;
    private BlockPos source, amp;
    private double watts;
    private long lastFeed = -1000;
    private int overTicks, sendTick;
    // synced
    private int tilt = 4; // index into TILTS: level
    private float shownW;

    public SpeakerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean horn() {
        return getBlockState().getBlock() instanceof SpeakerBlock b && b.horn;
    }

    public double sensitivityDb() {
        return horn() ? 112 : 92;
    }

    public double ratedW() {
        return horn() ? 600 : 100;
    }

    public int tiltDeg() {
        return TILTS[Math.floorMod(tilt, TILTS.length)];
    }

    public void cycleTilt() {
        tilt = (tilt + 1) % TILTS.length;
        setChanged();
        sendData();
    }

    /** where the speaker points (unit vector, Minecraft axes) */
    public Vec3 axis() {
        Direction f = getBlockState().hasProperty(DeviceBlock.FACING) ? getBlockState().getValue(DeviceBlock.FACING) : Direction.NORTH;
        if (horn()) f = f.getOpposite(); // the horn's mouth is at the back; its terminals face whoever placed it
        double e = Math.toRadians(horn() ? tiltDeg() : 0);
        return new Vec3(f.getStepX() * Math.cos(e), Math.sin(e), f.getStepZ() * Math.cos(e));
    }

    /** the amplifier feeds it this tick */
    public void feed(BlockPos src, double w, BlockPos amp) {
        source = src.immutable();
        this.amp = amp.immutable();
        watts = w;
        lastFeed = level.getGameTime();
    }

    /** sound pressure level (dB) at distance r (m) and angle (degrees) off the axis */
    public static double spl(boolean horn, double watts, double r, double offDeg) {
        double s = (horn ? 112 : 92) + 10 * Math.log10(Math.max(1e-6, watts)) - 20 * Math.log10(Math.max(1, r)) - 0.005 * r;
        double loss = horn ? Math.min(30, 24 * Math.pow(offDeg / 15, 2)) : 6 * (1 - Math.cos(Math.toRadians(offDeg)));
        return s - loss;
    }

    /** how far (m) along its axis it can still be heard over a quiet background (30 dB) */
    public static double reach(boolean horn, double watts) {
        double lo = 1, hi = 8000;
        if (spl(horn, watts, hi, 0) > 30) return hi;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (spl(horn, watts, mid, 0) > 30) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    /** above this a sound hurts (dB); 140 dB injures at once */
    public static final double PAIN_DB = 120, QUEASY_DB = 110;

    /**
     * Standing in front of a loud speaker: from 110 dB the noise makes you queasy, from 120 dB it hurts -
     * more the louder it is (140 dB: about 2 hearts a second). A wall between you and the speaker takes
     * off about 25 dB; a helmet about 5 dB.
     */
    private void hurtNearby(double w) {
        if (w <= 0) return;
        double reach = Math.pow(10, (sensitivityDb() + 10 * Math.log10(w) - QUEASY_DB) / 20);
        if (reach < 0.5) return;
        Vec3 c = worldPosition.getCenter(), ax = axis();
        var box = new net.minecraft.world.phys.AABB(worldPosition).inflate(Math.min(48, reach + 1));
        for (net.minecraft.world.entity.LivingEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
            Vec3 ear = e.getEyePosition(), to = ear.subtract(c);
            double r = to.length();
            double off = r < 1e-3 ? 0 : Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.dot(ax) / r))));
            double db = spl(horn(), w, r, off);
            var hit = level.clip(new net.minecraft.world.level.ClipContext(c.add(ax.scale(0.6)), ear,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, e));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && !hit.getBlockPos().equals(worldPosition)) db -= 25;
            if (!e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) db -= 5;
            if (db >= QUEASY_DB)
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 120, 0, false, false));
            if (db >= PAIN_DB) {
                float dmg = (float) ((db - PAIN_DB) / 10.0 + 0.5) * 2 / 2; // half a heart at 120 dB, 2.5 hearts per half second at 140 dB
                e.hurt(damage(), dmg);
                if (e instanceof ServerPlayer sp && db >= 130)
                    sp.displayClientMessage(Component.translatable("pgtelegraph.speaker.too_loud",
                            String.format(Locale.ROOT, "%.0f", db)).withStyle(ChatFormatting.RED), true);
            }
        }
    }

    private net.minecraft.world.damagesource.DamageSource damage() {
        var reg = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sparkgap.pgtelegraph.PgTelegraph.MODID, "loudspeaker"));
        var h = reg.getHolder(key);
        return h.isPresent() ? new net.minecraft.world.damagesource.DamageSource(h.get()) : level.damageSources().generic();
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(2);
        coil = builder.connectSwitch(8f, builder.terminalNode(0), builder.terminalNode(1), true);
    }

    @Override
    protected void guards(Guard g) {
        g.check(coil, SPEC.maxVolts(), 0);
    }

    @Override
    protected void openAll() {
        if (coil != null && coil.getState()) coil.setState(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        long t = level.getGameTime();
        boolean live = t - lastFeed < 30 && source != null && !isBurnt();
        // overdriven: the voice coil cooks
        if (live && watts > ratedW() * 1.5) {
            if (++overTicks > 100) {
                burn();
                level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.4f);
            }
        } else overTicks = Math.max(0, overTicks - 1);
        float w = live ? (float) Math.min(watts, ratedW() * 1.5) : 0;
        if (Math.abs(w - shownW) > 1) {
            shownW = w;
            sendData();
        }
        if (live && t % 10 == 0) hurtNearby(w);
        if (!live || --sendTick > 0) return;
        sendTick = 10;
        var ch = AudioAmpBlockEntity.channelOf(level, source);
        if (ch == null && source.equals(amp)) ch = new com.sparkgap.pgtelegraph.radio.RxChannel(); // a microphone only
        if (ch == null) return;
        double reach = reach(horn(), w);
        Vec3 c = worldPosition.getCenter(), ax = axis();
        SpeakerNet.State msg = new SpeakerNet.State(worldPosition, horn(), (float) ax.x, (float) ax.y, (float) ax.z, w, source, amp == null ? source : amp, ch.save());
        for (ServerPlayer p : ((ServerLevel) level).players())
            if (p.position().distanceTo(c) <= reach) PacketDistributor.sendToPlayer(p, msg);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal(String.format(Locale.ROOT, "     %.0f W / %.0f W  (%.0f dB @ 1 m, %.0f dB @ 100 m)", shownW, ratedW(),
                spl(horn(), Math.max(shownW, 1e-6), 1, 0), spl(horn(), Math.max(shownW, 1e-6), 100, 0))).withStyle(ChatFormatting.AQUA));
        if (horn()) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.speaker.tilt", String.format(Locale.ROOT, "%+d°", tiltDeg())))
                .withStyle(ChatFormatting.GRAY));
        if (shownW > 0) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.speaker.reach",
                String.format(Locale.ROOT, "%.0f", reach(horn(), shownW)))).withStyle(ChatFormatting.GRAY));
        if (isBurnt()) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.speaker.burnt")).withStyle(ChatFormatting.DARK_RED));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Tilt", tilt);
        if (clientPacket) tag.putFloat("W", shownW);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tilt = tag.contains("Tilt") ? tag.getInt("Tilt") : 4;
        if (clientPacket) shownW = tag.getFloat("W");
    }
}
