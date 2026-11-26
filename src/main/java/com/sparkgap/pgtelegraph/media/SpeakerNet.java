package com.sparkgap.pgtelegraph.media;

import com.sparkgap.pgtelegraph.PgTelegraph;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * A loudspeaker that is playing, told to every player within earshot - however far, even where the
 * speaker's chunk is not loaded on their side: where it is, which way it points, how hard it is driven,
 * and the radio channel its amplifier is fed from (the client simulates that sound itself).
 */
public final class SpeakerNet {
    private SpeakerNet() {}

    public static void register(PayloadRegistrar r) {
        r.playToClient(State.TYPE, State.CODEC, State::handle);
    }

    public record State(BlockPos pos, boolean horn, float ax, float ay, float az, float watts, BlockPos source, BlockPos amp, CompoundTag channel)
            implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PgTelegraph.MODID, "speaker"));
        public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of((b, m) -> {
            b.writeBlockPos(m.pos);
            b.writeBoolean(m.horn);
            b.writeFloat(m.ax);
            b.writeFloat(m.ay);
            b.writeFloat(m.az);
            b.writeFloat(m.watts);
            b.writeBlockPos(m.source);
            b.writeBlockPos(m.amp);
            b.writeNbt(m.channel);
        }, b -> new State(b.readBlockPos(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(),
                b.readBlockPos(), b.readBlockPos(), b.readNbt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(State m, IPayloadContext ctx) {
            ctx.enqueueWork(() -> com.sparkgap.pgtelegraph.client.ClientAccess.speaker(m));
        }
    }
}
