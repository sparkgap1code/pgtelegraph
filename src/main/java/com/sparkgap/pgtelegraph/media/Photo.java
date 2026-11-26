package com.sparkgap.pgtelegraph.media;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A photograph. The picture itself (320 × 256, {@link Pic}) is kept by the server in
 * {@link PhotoStore} under {@code id}; the item only carries the id and the caption, so a chest
 * full of photos stays light. {@code legacy} holds the 64 × 64 picture of photos taken before.
 */
public record Photo(String id, String caption, byte[] legacy) {
    private static final Codec<byte[]> BYTES = Codec.BYTE_BUFFER.xmap(b -> {
        byte[] a = new byte[b.remaining()];
        b.duplicate().get(a);
        return a;
    }, ByteBuffer::wrap);
    public static final Codec<Photo> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(Photo::id),
            Codec.STRING.optionalFieldOf("caption", "").forGetter(Photo::caption),
            BYTES.optionalFieldOf("image", new byte[0]).forGetter(Photo::legacy)).apply(i, Photo::new));
    public static final StreamCodec<ByteBuf, Photo> STREAM = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Photo::id, ByteBufCodecs.STRING_UTF8, Photo::caption,
            ByteBufCodecs.byteArray(8192), Photo::legacy, Photo::new);

    public Photo(String id, String caption) {
        this(id, caption, new byte[0]);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Photo p && id.equals(p.id) && caption.equals(p.caption) && Arrays.equals(legacy, p.legacy);
    }

    @Override
    public int hashCode() {
        return (id.hashCode() * 31 + caption.hashCode()) * 31 + Arrays.hashCode(legacy);
    }
}
