package com.sparkgap.pgtelegraph.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.patryk3211.powergrid.electricity.base.IElectricEntity;
import org.patryk3211.powergrid.electricity.sim.SwitchedWire;

import java.util.ArrayList;
import java.util.List;

/**
 * Cord switchboard (교환대), 8 lines. Every pair of jacks has a contact that a patch cord closes,
 * so any number of cords can be up at once (including several on one jack for a party line).
 * Each idle jack has a 500 Ω drop indicator coil from its line to the common (ground) terminal;
 * a calling station's current drops the shutter. Plugging a cord into the jack cuts the coil out.
 * Several boards are joined by wiring a line terminal of one to a line terminal of another (trunk).
 */
public class SwitchboardBlockEntity extends RatedBlockEntity {
    public static final int LINES = 8;
    public static final float DROP_OHMS = 500f;
    public static final double DROP_AMPS = 0.005;
    private static final int PENDING_TIMEOUT = 200;

    public static final Spec SPEC = new Spec(80f, 3f, 2f, 250f,
            Spec.contact(0.05f, 2f, LINES * (LINES - 1) / 2), Spec.coil(DROP_OHMS, 0.1f));

    /** pair index -> contact; pairs ordered (0,1),(0,2)...(6,7) */
    // no initializers: buildCircuit runs inside the super constructor, before field initializers
    private SwitchedWire[] contacts;
    private SwitchedWire[] drops;

    private final List<int[]> cords = new ArrayList<>();
    private int dropped;        // bitmask of fallen shutters
    private int pending = -1;   // first jack of a cord being placed
    private long pendingSince;
    private boolean statesDirty = true;

    public SwitchboardBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    static int pairIndex(int a, int b) {
        if (a > b) { int t = a; a = b; b = t; }
        return a * (2 * LINES - a - 1) / 2 + (b - a - 1);
    }

    @Override
    public Spec spec() {
        return SPEC;
    }

    @Override
    public void buildCircuit(IElectricEntity.CircuitBuilder builder) {
        builder.setTerminalCount(LINES + 1);
        contacts = new SwitchedWire[LINES * (LINES - 1) / 2];
        drops = new SwitchedWire[LINES];
        for (int a = 0; a < LINES; a++) {
            for (int b = a + 1; b < LINES; b++) {
                contacts[pairIndex(a, b)] = builder.connectSwitch(0.05f, builder.terminalNode(a), builder.terminalNode(b), false);
            }
            drops[a] = builder.connectSwitch(DROP_OHMS, builder.terminalNode(a), builder.terminalNode(LINES), false);
        }
        statesDirty = true;
    }

    @Override
    public void electricalTick() {
        super.electricalTick();
        for (SwitchedWire w : contacts) if (w != null) applyPower(w);
        for (SwitchedWire w : drops) if (w != null) applyPower(w);
    }

    @Override
    protected void guards(Guard g) {
        float ca = SPEC.part("contact").maxAmps(), da = SPEC.part("coil").maxAmps();
        for (SwitchedWire w : contacts) g.check(w, SPEC.maxVolts(), ca);
        for (SwitchedWire w : drops) g.check(w, SPEC.maxVolts(), da);
    }

    @Override
    protected void openAll() {
        for (SwitchedWire w : contacts) if (w != null && w.getState()) w.setState(false);
        for (SwitchedWire w : drops) if (w != null && w.getState()) w.setState(false);
    }

    public boolean jackBusy(int jack) {
        for (int[] c : cords) if (c[0] == jack || c[1] == jack) return true;
        return false;
    }

    public List<int[]> cords() {
        return cords;
    }

    public boolean isDropped(int jack) {
        return (dropped & (1 << jack)) != 0;
    }

    public int pending() {
        return pending;
    }

    private void applyStates() {
        statesDirty = false;
        if (isBurnt()) {
            openAll();
            return;
        }
        boolean[] want = new boolean[contacts.length];
        for (int[] c : cords) want[pairIndex(c[0], c[1])] = true;
        for (int i = 0; i < contacts.length; i++) {
            if (contacts[i] != null && contacts[i].getState() != want[i]) contacts[i].setState(want[i]);
        }
        for (int j = 0; j < LINES; j++) {
            boolean idle = !jackBusy(j);
            if (drops[j] != null && drops[j].getState() != idle) drops[j].setState(idle);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || isBurnt()) return;
        if (statesDirty) applyStates();
        if (pending >= 0 && level.getGameTime() - pendingSince > PENDING_TIMEOUT) {
            pending = -1;
            sync();
        }
        for (int j = 0; j < LINES; j++) {
            if (isDropped(j) || drops[j] == null || !drops[j].getState()) continue;
            if (Math.abs(drops[j].current()) > DROP_AMPS) {
                dropped |= 1 << j;
                level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.35f, 1.8f);
                sync();
            }
        }
    }

    /** Operator clicked a jack. sneak = pull every cord out of it (and reset its shutter). */
    public void clickJack(int jack, boolean sneak) {
        clickJack(jack, sneak, null);
    }

    /** a patch cord is a length of insulated copper wire: plugging one in takes a wire from the hand, pulling it gives it back */
    public static boolean isCordWire(net.minecraft.world.item.ItemStack s) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
        return (id.getNamespace().equals("powergrid") && id.getPath().equals("insulated_copper_wire"))
                || s.is(com.sparkgap.pgtelegraph.PgTelegraph.PATCH_CORD.get());
    }

    private void giveWire(net.minecraft.world.entity.player.Player p, int n) {
        if (n <= 0 || p == null || p.getAbilities().instabuild) return;
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("powergrid", "insulated_copper_wire"));
        var st = new net.minecraft.world.item.ItemStack(item, n);
        if (!p.getInventory().add(st)) p.drop(st, false);
    }

    public void clickJack(int jack, boolean sneak, net.minecraft.world.entity.player.Player player) {
        if (level == null || isBurnt()) return;
        if (sneak) {
            int before = cords.size();
            boolean removed = cords.removeIf(c -> c[0] == jack || c[1] == jack);
            giveWire(player, before - cords.size());
            dropped &= ~(1 << jack);
            pending = -1;
            if (removed) level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.3f);
        } else if (pending < 0 || pending == jack) {
            pending = pending == jack ? -1 : jack;
            pendingSince = level.getGameTime();
            level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.4f, 1.6f);
        } else {
            int a = pending, b = jack;
            pending = -1;
            boolean existed = cords.removeIf(c -> (c[0] == a && c[1] == b) || (c[0] == b && c[1] == a));
            if (existed) giveWire(player, 1);
            if (!existed && player != null && !player.getAbilities().instabuild) {
                var hand = player.getMainHandItem();
                if (!isCordWire(hand)) { // no cord without a wire
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("pgtelegraph.switchboard.need_wire")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                    applyStates();
                    sync();
                    return;
                }
                hand.shrink(1);
            }
            if (!existed) {
                cords.add(new int[]{a, b});
                dropped &= ~((1 << a) | (1 << b)); // answering the call restores the shutter
            }
            level.playSound(null, worldPosition, existed ? SoundEvents.ITEM_FRAME_REMOVE_ITEM : SoundEvents.ITEM_FRAME_ADD_ITEM,
                    SoundSource.BLOCKS, 0.6f, 1.3f);
        }
        applyStates();
        sync();
    }

    private void sync() {
        setChanged();
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        int[] flat = new int[cords.size() * 2];
        for (int i = 0; i < cords.size(); i++) {
            flat[2 * i] = cords.get(i)[0];
            flat[2 * i + 1] = cords.get(i)[1];
        }
        tag.putIntArray("Cords", flat);
        tag.putInt("Dropped", dropped);
        tag.putInt("Pending", pending);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        cords.clear();
        int[] flat = tag.getIntArray("Cords");
        for (int i = 0; i + 1 < flat.length; i += 2) {
            if (flat[i] >= 0 && flat[i] < LINES && flat[i + 1] >= 0 && flat[i + 1] < LINES && flat[i] != flat[i + 1]) {
                cords.add(new int[]{flat[i], flat[i + 1]});
            }
        }
        dropped = tag.getInt("Dropped");
        pending = tag.contains("Pending") ? tag.getInt("Pending") : -1;
        statesDirty = true;
    }
}
