package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.sparkgap.pgtelegraph.media.Pic;
import com.sparkgap.pgtelegraph.media.PicNet;
import com.sparkgap.pgtelegraph.media.TexPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;

/**
 * Builds the {@link TexPalette} for the server's TV camera from this client's block models: the
 * top and the side texture of every block, each boiled down to 4 × 4 averaged texels.
 */
public final class ClientTex {
    private ClientTex() {}

    public static void buildAndSend() {
        Minecraft mc = Minecraft.getInstance();
        TexPalette.Writer w = new TexPalette.Writer();
        RandomSource rnd = RandomSource.create(42);
        try {
            for (Block b : BuiltInRegistries.BLOCK) {
                BlockState st = b.defaultBlockState();
                if (st.isAir()) continue;
                BakedModel model = mc.getBlockRenderer().getBlockModel(st);
                boolean fluid = !st.getFluidState().isEmpty();
                Face top = face(model, st, Direction.UP, rnd), side = face(model, st, Direction.NORTH, rnd);
                TextureAtlasSprite particle = mc.getBlockRenderer().getBlockModelShaper().getParticleIcon(st);
                if (top == null) top = new Face(particle, fluid && st.getFluidState().is(FluidTags.WATER));
                if (side == null) side = new Face(particle, fluid && st.getFluidState().is(FluidTags.WATER));
                short[] t = thumb(top.sprite), s = thumb(side.sprite);
                if (t == null || s == null) continue;
                w.add(BuiltInRegistries.BLOCK.getKey(b).toString(), top.tinted, side.tinted, t, s);
            }
            byte[] z = w.finish();
            int total = (z.length + PicNet.CHUNK - 1) / PicNet.CHUNK;
            if (total > PicNet.MAX_TEX_CHUNKS) return;
            for (int i = 0; i < total; i++) {
                PacketDistributor.sendToServer(new PicNet.TexUpload(i, total,
                        Arrays.copyOfRange(z, i * PicNet.CHUNK, Math.min(z.length, (i + 1) * PicNet.CHUNK))));
            }
        } catch (Exception e) {
            LogUtils.getLogger().warn("pgtelegraph: could not build the texture table", e);
        }
    }

    private record Face(TextureAtlasSprite sprite, boolean tinted) {}

    private static Face face(BakedModel model, BlockState st, Direction d, RandomSource rnd) {
        try {
            List<BakedQuad> q = model.getQuads(st, d, rnd);
            if (q.isEmpty()) {
                for (BakedQuad x : model.getQuads(st, null, rnd)) {
                    if (x.getDirection() == d) return new Face(x.getSprite(), x.isTinted());
                }
                return null;
            }
            return new Face(q.get(0).getSprite(), q.get(0).isTinted());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 4 × 4 averaged texels of the sprite's first frame, RGB565 */
    private static short[] thumb(TextureAtlasSprite sprite) {
        try {
            return thumb0(sprite);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static short[] thumb0(TextureAtlasSprite sprite) {
        if (sprite == null) return null;
        NativeImage im = sprite.contents().getOriginalImage();
        if (im == null) return null;
        int w = sprite.contents().width(), h = Math.min(sprite.contents().height(), w);
        if (w < 1 || h < 1) return null;
        short[] out = new short[16];
        for (int ty = 0; ty < 4; ty++) {
            for (int tx = 0; tx < 4; tx++) {
                long r = 0, g = 0, b = 0;
                int n = 0;
                long ar = 0, ag = 0, ab = 0;
                int an = 0;
                for (int y = ty * h / 4; y < Math.max(ty * h / 4 + 1, (ty + 1) * h / 4); y++) {
                    for (int x = tx * w / 4; x < Math.max(tx * w / 4 + 1, (tx + 1) * w / 4); x++) {
                        int c = im.getPixelRGBA(x, y); // ABGR
                        ar += c & 255;
                        ag += (c >> 8) & 255;
                        ab += (c >> 16) & 255;
                        an++;
                        if (((c >>> 24) & 255) < 32) continue;
                        r += c & 255;
                        g += (c >> 8) & 255;
                        b += (c >> 16) & 255;
                        n++;
                    }
                }
                out[ty * 4 + tx] = n > 0 ? Pic.pack((int) (r / n), (int) (g / n), (int) (b / n))
                        : Pic.pack((int) (ar / Math.max(1, an)), (int) (ag / Math.max(1, an)), (int) (ab / Math.max(1, an)));
            }
        }
        return out;
    }
}
