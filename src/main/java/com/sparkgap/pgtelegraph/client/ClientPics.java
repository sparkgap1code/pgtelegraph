package com.sparkgap.pgtelegraph.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.sparkgap.pgtelegraph.media.Photo;
import com.sparkgap.pgtelegraph.media.Pic;
import com.sparkgap.pgtelegraph.media.PicNet;
import com.sparkgap.pgtelegraph.sat.SstvMonitorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Pictures on the client: photos fetched from the server, SSTV screens, and a texture to draw them. */
public final class ClientPics {
    private ClientPics() {}

    private static final Map<String, byte[]> PHOTOS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
            return size() > 12;
        }
    };
    private static final Set<String> ASKED = new HashSet<>();

    /** the picture of a photo, or null while it is on its way */
    public static byte[] photo(Photo p) {
        if (p.id().isEmpty()) return Pic.fromLegacy(p.legacy());
        byte[] img = PHOTOS.get(p.id());
        if (img == null && ASKED.add(p.id())) PacketDistributor.sendToServer(new PicNet.Want(p.id()));
        return img;
    }

    public static void photoArrived(String id, byte[] deflated) {
        ASKED.remove(id);
        byte[] img = Pic.inflate(deflated, Pic.BYTES);
        if (img != null && img.length == Pic.BYTES) PHOTOS.put(id, img);
    }

    public static void monitorFull(BlockPos pos, int w, int h, byte[] deflated) {
        if (w <= 0 || h <= 0 || w > com.sparkgap.pgtelegraph.media.SstvMode.MAX_W || h > com.sparkgap.pgtelegraph.media.SstvMode.MAX_H) return;
        byte[] img = Pic.inflate(deflated, w * h * 2);
        var level = Minecraft.getInstance().level;
        if (img == null || img.length != w * h * 2 || level == null) return;
        if (level.getBlockEntity(pos) instanceof SstvMonitorBlockEntity be) {
            be.resize(w, h);
            System.arraycopy(img, 0, be.image, 0, img.length);
            be.version++;
        }
    }

    public static void monitorRow(BlockPos pos, int row, int h, byte[] data) {
        var level = Minecraft.getInstance().level;
        int w = data.length / 2;
        if (level == null || row < 0 || row >= h || w <= 0 || h > com.sparkgap.pgtelegraph.media.SstvMode.MAX_H) return;
        if (level.getBlockEntity(pos) instanceof SstvMonitorBlockEntity be) {
            if (be.imgW != w || be.imgH != h) be.resize(w, h);
            if (row == 0) java.util.Arrays.fill(be.image, (byte) 0);
            System.arraycopy(data, 0, be.image, row * w * 2, w * 2);
            be.row = row;
            be.version++;
            SstvAudio.row(be, row, h, data);
        }
    }

    /** a texture to show a picture on screen (320 x 256 unless told otherwise) */
    public static final class Tex implements AutoCloseable {
        private final DynamicTexture tex;
        private final ResourceLocation id;
        public final int w, h;

        public Tex() {
            this(Pic.W, Pic.H);
        }

        public Tex(int w, int h) {
            this.w = w;
            this.h = h;
            tex = new DynamicTexture(w, h, false);
            id = Minecraft.getInstance().getTextureManager().register("pgtelegraph_pic", tex);
        }

        public void update(byte[] img) {
            NativeImage px = tex.getPixels();
            if (px == null) return;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = img == null || img.length < (y * w + x + 1) * 2 ? 0 : Pic.rgb(img, y * w + x);
                    // NativeImage wants ABGR
                    px.setPixelRGBA(x, y, 0xFF000000 | (c & 255) << 16 | (c & 0xFF00) | (c >> 16 & 255));
                }
            }
            tex.upload();
        }

        public void draw(GuiGraphics g, int x, int y, int w, int h) {
            g.blit(id, x, y, w, h, 0f, 0f, this.w, this.h, this.w, this.h);
        }

        @Override
        public void close() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }
}
