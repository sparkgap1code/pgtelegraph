package com.sparkgap.pgtelegraph.block;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * A scroll value that can share a block with others: Create keys behaviours by type and every
 * ScrollValueBehaviour saves under "ScrollValue", so each one here gets its own type and NBT key.
 */
public class NamedScroll extends ScrollValueBehaviour {
    private final BehaviourType<?> type;
    private final String key;

    public NamedScroll(BehaviourType<?> type, String key, Component label, SmartBlockEntity be, ValueBoxTransform slot) {
        super(label, be, slot);
        this.type = type;
        this.key = key;
    }

    @Override
    public BehaviourType<?> getType() {
        return type;
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        CompoundTag t = new CompoundTag();
        super.write(t, registries, clientPacket);
        if (t.contains("ScrollValue")) nbt.putInt(key, t.getInt("ScrollValue"));
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        CompoundTag t = new CompoundTag();
        if (nbt.contains(key)) t.putInt("ScrollValue", nbt.getInt(key));
        else t.putInt("ScrollValue", value);
        super.read(t, registries, clientPacket);
    }
}
