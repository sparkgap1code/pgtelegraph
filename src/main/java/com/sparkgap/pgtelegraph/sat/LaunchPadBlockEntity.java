package com.sparkgap.pgtelegraph.sat;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Launch pad: holds a rocket and a CubeSat. Started (empty hand, or a redstone pulse) it counts
 * down 10 s, then the rocket climbs; {@value #FAIL_PERCENT} % of launches fail on the pad. The
 * pad's facing is the launch azimuth, which sets the orbit's inclination (east = lowest, north or
 * south = polar, west = retrograde). Needs open sky.
 */
public class LaunchPadBlockEntity extends BlockEntity {
    public static final int COUNTDOWN = 200, ASCENT = 120, FAIL_PERCENT = 5;

    /** 0 none, 1 small (LEO 500-800 km), 2 medium (1200-2000 km), 3 heavy (geostationary) */
    private int rocket;
    private ItemStack payload = ItemStack.EMPTY;
    private int launched;
    /** -1 idle; counts up through countdown and ascent */
    private int phase = -1;
    private boolean powered;

    public LaunchPadBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean hasRocket() {
        return rocket > 0;
    }

    public ItemStack payload() {
        return payload;
    }

    public boolean busy() {
        return phase >= 0;
    }

    public void loadRocket(int kind) {
        rocket = kind;
        changed();
    }

    public void loadPayload(ItemStack s) {
        payload = s.copyWithCount(1);
        changed();
    }

    /** take everything back off the pad */
    public ItemStack[] unload() {
        ItemStack[] out = contents();
        rocket = 0;
        payload = ItemStack.EMPTY;
        changed();
        return out;
    }

    /** what is on the pad (for drops when it is broken) */
    public ItemStack[] contents() {
        ItemStack r = switch (rocket) {
            case 1 -> new ItemStack(PgTelegraph.ROCKET.get());
            case 2 -> new ItemStack(PgTelegraph.ROCKET_MEDIUM.get());
            case 3 -> new ItemStack(PgTelegraph.ROCKET_HEAVY.get());
            default -> ItemStack.EMPTY;
        };
        return new ItemStack[]{r, payload};
    }

    public boolean start() {
        if (rocket == 0 || payload.isEmpty() || phase >= 0) return false;
        phase = 0;
        announce(Component.translatable("pgtelegraph.launch.armed", CubeSatItem.config(payload).name()).withStyle(ChatFormatting.GOLD), 48);
        changed();
        return true;
    }

    public void redstone(boolean on) {
        if (on && !powered) start();
        powered = on;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            BlockState s = getBlockState();
            boolean loaded = rocket > 0 && phase < COUNTDOWN;
            if (s.getValue(LaunchPadBlock.LOADED) != loaded) level.setBlock(worldPosition, s.setValue(LaunchPadBlock.LOADED, loaded), 3);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private void announce(Component c, int radius) {
        if (!(level instanceof ServerLevel sl)) return;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(worldPosition.getCenter()) < radius * radius) p.displayClientMessage(c, false);
        }
    }

    void serverTick() {
        if (phase < 0 || !(level instanceof ServerLevel sl)) return;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1, z = worldPosition.getZ() + 0.5;
        if (phase < COUNTDOWN) {
            int left = (COUNTDOWN - phase) / 20;
            if (phase % 20 == 0) {
                announce(Component.literal("T-" + left).withStyle(ChatFormatting.YELLOW), 48);
                sl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 1f, 1.5f);
            }
            if (phase > COUNTDOWN - 60) sl.sendParticles(ParticleTypes.CLOUD, x, y, z, 4, 0.4, 0.1, 0.4, 0.05);
        } else if (phase == COUNTDOWN) {
            if (!sl.canSeeSky(worldPosition.above(2)) || sl.random.nextInt(100) < FAIL_PERCENT) {
                fail(sl);
                return;
            }
            launched = rocket;
            rocket = 0;
            changed();
            sl.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 4f, 0.5f);
            sl.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5f, 0.5f);
            ItemStack fw = new ItemStack(Items.FIREWORK_ROCKET);
            fw.set(net.minecraft.core.component.DataComponents.FIREWORKS,
                    new net.minecraft.world.item.component.Fireworks(3, java.util.List.of()));
            sl.addFreshEntity(new FireworkRocketEntity(sl, x, y + 0.5, z, fw));
            announce(Component.translatable("pgtelegraph.launch.liftoff").withStyle(ChatFormatting.GOLD), 96);
        } else {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 6, 0.6, 0.2, 0.6, 0.04);
            if (phase < COUNTDOWN + 40) sl.sendParticles(ParticleTypes.FLAME, x, y, z, 6, 0.3, 0.1, 0.3, 0.05);
            if (phase >= COUNTDOWN + ASCENT) orbit(sl);
        }
        if (phase >= 0) phase++;
    }

    private void fail(ServerLevel sl) {
        announce(Component.translatable("pgtelegraph.launch.failed").withStyle(ChatFormatting.RED), 96);
        rocket = 0;
        payload = ItemStack.EMPTY;
        phase = -1;
        changed();
        sl.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5, 3f,
                Level.ExplosionInteraction.NONE);
    }

    private void orbit(ServerLevel sl) {
        double az = getBlockState().getValue(LaunchPadBlock.FACING).toYRot() + 180; // MC yaw -> compass
        double alt = launched == 2 ? 1200 + sl.random.nextInt(801) : 500 + sl.random.nextInt(301);
        Orbit.Elements el = launched == 3 ? Orbit.geo(worldPosition, sl.getGameTime())
                : Orbit.launch(worldPosition, sl.getGameTime(), az, alt);
        if (launched == 3) alt = Orbit.GEO_ALT;
        Satellite s = SatelliteData.get(sl).add(CubeSatItem.config(payload), Math.max(0, launched - 1), el);
        if (s.cfg.get("SPOTLAT").isEmpty()) { // the spot beam looks at home unless told otherwise
            double[] ll = Orbit.latLon(worldPosition);
            s.cfg.set("SPOTLAT", String.valueOf(Math.toDegrees(ll[0])));
            s.cfg.set("SPOTLON", String.valueOf(Math.toDegrees(ll[1])));
        }
        com.sparkgap.pgtelegraph.Awards.near(sl, worldPosition, 96, launched == 3 ? "geostationary" : "orbit");
        double pReal = el.periodS() / 60, pGame = pReal / Orbit.SPEED;
        Component msg = Component.translatable("pgtelegraph.launch.orbit", s.name, s.summary(),
                        String.format(Locale.ROOT, "%.0f", alt), String.format(Locale.ROOT, "%.1f", el.inclDeg()),
                        String.format(Locale.ROOT, "%.0f", pReal), String.format(Locale.ROOT, "%.1f", pGame),
                        "")
                .withStyle(ChatFormatting.GREEN);
        for (ServerPlayer p : sl.players()) p.displayClientMessage(msg, false);
        payload = ItemStack.EMPTY;
        phase = -1;
        changed();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.putInt("Rocket", rocket);
        tag.putInt("Launched", launched);
        if (!payload.isEmpty()) tag.put("Payload", payload.save(r));
        tag.putInt("Phase", phase);
        tag.putBoolean("Powered", powered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        rocket = tag.getInt("Rocket");
        launched = tag.getInt("Launched");
        payload = tag.contains("Payload") ? ItemStack.parseOptional(r, tag.getCompound("Payload")) : ItemStack.EMPTY;
        phase = tag.contains("Phase") ? tag.getInt("Phase") : -1;
        powered = tag.getBoolean("Powered");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider r) {
        return saveWithoutMetadata(r);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
