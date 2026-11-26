package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.sparkgap.pgtelegraph.broadcast.BroadcastRadioBlockEntity;
import com.sparkgap.pgtelegraph.radio.RxChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.List;
import java.util.Locale;

/**
 * Audio power amplifier (오디오 앰프). Takes the sound of a radio set touching it (broadcast radio or
 * receiver) and drives the loudspeakers wired to its SPK terminals. Runs off the grid on its PWR
 * terminals (230 V nominal, 60 % efficient, so 1 kW out draws about 1.7 kW); with less voltage it
 * gives less (power goes with the square of the voltage). The output is shared between the speakers
 * on the line; a speaker driven well past its rating burns its voice coil.
 */
public class AudioAmpBlockEntity extends RatedBlockEntity implements IHaveGoggleInformation {
    public static final double NOMINAL_V = 230, EFF = 0.6;
    public static final int MAX_W = 2000;
    public static final Spec SPEC = new Spec(85f, 1500f, 4f, 260f, new Spec.Part("load", 26f, 0, 1));

    private com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour power;
    private SwitchedWire load;
    private int scan;
    private List<BlockPos> speakers = List.of();
    // synced
    private float outW;
    private int speakerCount;
    private boolean hasSource;
    /** a microphone against it last spoke at this tick (public address) */
    private long micAt = -1000;
    private double eachW;

    public AudioAmpBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        power = new StepScroll.Plain(Component.translatable("pgtelegraph.amp.power"), this,
                ValueBoxes.front(8, 4, 3, 0.3f), StepScroll.powers(MAX_W)).stepFormat(v -> v + " W")
                .between(1, MAX_W)
                .withFormatter(v -> v + " W");
        power.value = 50;
        power.withCallback(v -> {
            if (load != null) load.setResistance(loadOhms());
        });
        behaviours.add(power);
    }

    private float loadOhms() {
        double inW = Math.max(1, (power == null ? 50 : power.getValue()) / EFF);
        return (float) (NOMINAL_V * NOMINAL_V / inW);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(4);
        load = builder.connectSwitch(loadOhms(), builder.terminalNode(0), builder.terminalNode(1), false);
        builder.connectSwitch(8f, builder.terminalNode(2), builder.terminalNode(3), false); // the speaker line (signal only)
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        if (load != null) applyPower(load);
    }

    @Override
    protected void guards(Guard g) {
        g.check(load, SPEC.maxVolts(), 0);
    }

    @Override
    protected void openAll() {
        if (load != null && load.getState()) load.setState(false);
    }

    /**
     * 50 ms of speech from a microphone against this amplifier: out to everyone in earshot of one of
     * its speakers (each speaker mixes it over the radio it plays)
     */
    public void micSpeech(byte[] data) {
        if (level == null || isBurnt()) return;
        micAt = level.getGameTime();
        if (eachW <= 0) return;
        var msg = new com.sparkgap.pgtelegraph.media.VoiceNet.Out(worldPosition, data, 60f, false);
        for (net.minecraft.server.level.ServerPlayer p : ((net.minecraft.server.level.ServerLevel) level).players()) {
            for (BlockPos sp : speakers) {
                if (!(level.getBlockEntity(sp) instanceof SpeakerBlockEntity s)) continue;
                if (p.position().distanceTo(sp.getCenter()) <= SpeakerBlockEntity.reach(s.horn(), eachW)) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, msg);
                    break;
                }
            }
        }
    }

    /** the radio set this amplifier listens to (one touching it) and its channel, or null */
    public static BlockPos sourceOf(net.minecraft.world.level.Level level, BlockPos amp) {
        for (Direction d : Direction.values()) {
            BlockPos p = amp.relative(d);
            var be = level.getBlockEntity(p);
            if (be instanceof BroadcastRadioBlockEntity r && r.isOn()) return p;
            if (be instanceof ReceiverBlockEntity r && r.isPowered()) return p;
        }
        return null;
    }

    public static RxChannel channelOf(net.minecraft.world.level.Level level, BlockPos src) {
        var be = level.getBlockEntity(src);
        if (be instanceof BroadcastRadioBlockEntity r) return r.channel;
        if (be instanceof ReceiverBlockEntity r) return r.channel;
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        BlockPos src = sourceOf(level, worldPosition);
        boolean mic = level.getGameTime() - micAt < 60; // a microphone keeps it on a little after the last word
        boolean run = (src != null || mic) && !isBurnt();
        if (src != null && level.getBlockEntity(src) instanceof AmpFed f) f.ampFed(level.getGameTime());
        if (load != null && load.getState() != run) load.setState(run);
        if (--scan <= 0) {
            scan = 20;
            speakers = WireGraph.find(level, worldPosition, new int[]{2, 3}, be -> be instanceof SpeakerBlockEntity);
            for (Direction d : Direction.values()) { // or a speaker standing right against it
                BlockPos p = worldPosition.relative(d);
                if (level.getBlockEntity(p) instanceof SpeakerBlockEntity && !speakers.contains(p)) {
                    speakers = new java.util.ArrayList<>(speakers);
                    speakers.add(p.immutable());
                }
            }
        }
        // what the supply lets it deliver: the panel setting at full voltage, less (V^2) when the supply sags
        double v = load == null ? 0 : Math.abs(load.potentialDifference());
        double avail = run && v > 20 ? Math.min(power.getValue(), v * v / Math.max(1, loadOhms()) * EFF) : 0;
        float old = outW;
        outW = (float) avail;
        boolean dirty = Math.abs(old - outW) > 1 || speakerCount != speakers.size() || hasSource != (src != null || mic);
        speakerCount = speakers.size();
        hasSource = src != null || mic;
        eachW = 0;
        if (avail > 0 && !speakers.isEmpty()) {
            double each = avail / speakers.size();
            eachW = each;
            for (BlockPos p : speakers)
                if (level.getBlockEntity(p) instanceof SpeakerBlockEntity s) s.feed(src != null ? src : worldPosition, each, worldPosition);
        }
        if (dirty && level.getGameTime() % 10 == 0) sendData();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tip, boolean sneaking) {
        tip.add(Component.literal("    ").append(com.sparkgap.pgtelegraph.block.Labels.title(this)));
        tip.add(Component.literal("     ").append(Component.translatable(hasSource ? "pgtelegraph.amp.source" : "pgtelegraph.amp.no_source"))
                .withStyle(hasSource ? ChatFormatting.GREEN : ChatFormatting.RED));
        tip.add(Component.literal(String.format(Locale.ROOT, "     %.0f W  ", outW))
                .append(Component.translatable("pgtelegraph.amp.speakers", speakerCount)).withStyle(ChatFormatting.AQUA));
        if (isBurnt()) tip.add(Component.literal("     ").append(Component.translatable("pgtelegraph.rated.burnt")).withStyle(ChatFormatting.DARK_RED));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putFloat("OutW", outW);
            tag.putInt("Spk", speakerCount);
            tag.putBoolean("Src", hasSource);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            outW = tag.getFloat("OutW");
            speakerCount = tag.getInt("Spk");
            hasSource = tag.getBoolean("Src");
        }
    }
}
