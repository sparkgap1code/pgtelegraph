package com.sparkgap.pgtelegraph.media;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;

/**
 * The TV camera (server side, no textures here): one ray per pixel of the 320 × 256 frame into the
 * world. The block hit is coloured from {@link TexPalette} - a 4 × 4 thumbnail of its real
 * texture, sampled where the ray landed and tinted by the biome for grass, leaves and water - or,
 * before a client has sent that table, by its map colour. Shaded by face and light, faded into
 * the sky with distance. Mobs and players in their spawn-egg colours. Sky with sun, moon and stars.
 * 70° across, 64 blocks deep.
 */
public final class CameraRender {
    private CameraRender() {}

    static final double FOV = Math.toRadians(70), RANGE = 64;

    /** a camera pose: where it is and which way it looks */
    public static final class View {
        final Vec3 eye, fwd, right, up;
        final List<Entity> mobs;

        public View(Level level, Vec3 eye, float yaw, float pitch, Entity except) {
            this.eye = eye;
            pitch = Math.max(-89, Math.min(89, pitch));
            fwd = Vec3.directionFromRotation(pitch, yaw);
            right = fwd.cross(new Vec3(0, 1, 0)).normalize();
            up = right.cross(fwd).normalize();
            AABB box = new AABB(eye, eye.add(fwd.scale(RANGE))).inflate(RANGE * 0.75);
            List<Entity> all = level.getEntities(except, box, e -> !(e instanceof ItemEntity) && !e.isSpectator() && !e.isInvisible());
            mobs = all.size() > 64 ? all.subList(0, 64) : all;
        }
    }

    /** row {@code y} of the frame, {@link Pic#ROW} bytes */
    public static byte[] row(Level level, View v, int y) {
        byte[] out = new byte[Pic.ROW];
        double t = Math.tan(FOV / 2), tv = t * Pic.H / Pic.W;
        double sky = 1 - level.getSkyDarken() / 11.0; // 1 day .. 0 night
        float sa = level.getSunAngle(1f);
        Vec3 sun = new Vec3(-Math.sin(sa), Math.cos(sa), 0);
        boolean rain = level.isRaining();
        for (int x = 0; x < Pic.W; x++) {
            double u = ((x + 0.5) / Pic.W * 2 - 1) * t, w = (1 - (y + 0.5) / Pic.H * 2) * tv;
            Vec3 dir = v.fwd.add(v.right.scale(u)).add(v.up.scale(w)).normalize();
            Pic.put(out, x, pixel(level, v, dir, sky, sun, rain));
        }
        return out;
    }

    /** line y of a w x h picture (0xRRGGBB) */
    public static int[] line(Level level, View v, int y, int w, int h) {
        int[] out = new int[w];
        double t = Math.tan(FOV / 2), tv = t * h / w;
        double sky = 1 - level.getSkyDarken() / 11.0;
        float sa = level.getSunAngle(1f);
        Vec3 sun = new Vec3(-Math.sin(sa), Math.cos(sa), 0);
        boolean rain = level.isRaining();
        double ww = (1 - (y + 0.5) / h * 2) * tv;
        for (int x = 0; x < w; x++) {
            double u = ((x + 0.5) / w * 2 - 1) * t;
            Vec3 dir = v.fwd.add(v.right.scale(u)).add(v.up.scale(ww)).normalize();
            out[x] = Pic.rgb565(pixel(level, v, dir, sky, sun, rain) & 0xFFFF);
        }
        return out;
    }

    /** a whole w x h picture (0xRRGGBB) - the server's stand-in for a camera nobody is filming */
    public static int[] frame(Level level, View v, int w, int h) {
        int[] out = new int[w * h];
        double t = Math.tan(FOV / 2), tv = t * h / w;
        double sky = 1 - level.getSkyDarken() / 11.0;
        float sa = level.getSunAngle(1f);
        Vec3 sun = new Vec3(-Math.sin(sa), Math.cos(sa), 0);
        boolean rain = level.isRaining();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double u = ((x + 0.5) / w * 2 - 1) * t, ww = (1 - (y + 0.5) / h * 2) * tv;
                Vec3 dir = v.fwd.add(v.right.scale(u)).add(v.up.scale(ww)).normalize();
                out[y * w + x] = Pic.rgb565(pixel(level, v, dir, sky, sun, rain) & 0xFFFF);
            }
        return out;
    }

    private static short pixel(Level level, View v, Vec3 dir, double sky, Vec3 sun, boolean rain) {
        // stop the ray at the top or bottom of the world
        double range = RANGE;
        if (dir.y > 1e-6) range = Math.min(range, (level.getMaxBuildHeight() - v.eye.y) / dir.y);
        else if (dir.y < -1e-6) range = Math.min(range, (level.getMinBuildHeight() - v.eye.y) / dir.y);
        range = Math.max(0, range);
        Vec3 from = v.eye;
        double travelled = 0;
        int[] c = null;
        double dist = range;
        for (int pass = 0; pass < 4 && travelled < range; pass++) {
            Vec3 to = v.eye.add(dir.scale(range));
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.BLOCK) break;
            BlockPos bp = hit.getBlockPos();
            BlockState st = level.getBlockState(bp);
            MapColor mc = st.getMapColor(level, bp);
            double d = hit.getLocation().distanceTo(v.eye);
            if (mc == MapColor.NONE || mc.col == 0) { // glass and the like: look through
                from = hit.getLocation().add(dir.scale(0.05));
                travelled = d + 0.05;
                continue;
            }
            Direction face = hit.getDirection();
            double shade = switch (face) {
                case UP -> 1.0;
                case DOWN -> 0.5;
                case NORTH, SOUTH -> 0.82;
                default -> 0.68;
            };
            int light = level.getMaxLocalRawBrightness(bp.relative(face));
            shade *= 0.1 + 0.9 * light / 15.0;
            int col = texColor(level, bp, st, face, hit.getLocation(), mc.col);
            c = new int[]{(int) (((col >> 16) & 255) * shade), (int) (((col >> 8) & 255) * shade), (int) ((col & 255) * shade)};
            dist = d;
            break;
        }
        // mobs and players in front of the block hit
        for (Entity e : v.mobs) {
            var hit = e.getBoundingBox().clip(v.eye, v.eye.add(dir.scale(dist)));
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceTo(v.eye);
            if (d >= dist) continue;
            int col = entityColor(e);
            int light = level.getMaxLocalRawBrightness(e.blockPosition());
            double shade = 0.15 + 0.85 * light / 15.0;
            c = new int[]{(int) (((col >> 16) & 255) * shade), (int) (((col >> 8) & 255) * shade), (int) ((col & 255) * shade)};
            dist = d;
        }
        int[] s = skyColor(dir, sky, sun, rain);
        if (c == null) return rgb(s);
        double fog = Math.max(0, Math.min(1, (dist - RANGE * 0.6) / (RANGE * 0.4)));
        if (rain) fog = Math.max(fog, Math.min(0.6, dist / RANGE));
        return rgb(new int[]{mix(c[0], s[0], fog), mix(c[1], s[1], fog), mix(c[2], s[2], fog)});
    }

    /** the texel of the block's real texture where the ray landed, or its map colour */
    private static int texColor(Level level, BlockPos bp, BlockState st, Direction face, Vec3 at, int mapCol) {
        if (level.getServer() == null) return mapCol;
        TexPalette.Entry e = TexPalette.get(level.getServer(), st.getBlock());
        if (e == null) return mapCol;
        double fx = at.x - bp.getX(), fy = at.y - bp.getY(), fz = at.z - bp.getZ();
        double u, w;
        switch (face) {
            case UP, DOWN -> {
                u = fx;
                w = fz;
            }
            case NORTH, SOUTH -> {
                u = fx;
                w = 1 - fy;
            }
            default -> {
                u = fz;
                w = 1 - fy;
            }
        }
        int iu = Math.max(0, Math.min(3, (int) (u * 4))), iw = Math.max(0, Math.min(3, (int) (w * 4)));
        boolean top = face.getAxis() == Direction.Axis.Y;
        int col = Pic.rgb565((top ? e.top() : e.side())[iw * 4 + iu] & 0xFFFF);
        if (top ? e.tintTop() : e.tintSide()) {
            var biome = level.getBiome(bp).value();
            int tint = st.getFluidState().is(FluidTags.WATER) ? biome.getWaterColor()
                    : st.is(BlockTags.LEAVES) ? biome.getFoliageColor()
                    : biome.getGrassColor(bp.getX(), bp.getZ());
            col = ((col >> 16 & 255) * (tint >> 16 & 255) / 255) << 16 | ((col >> 8 & 255) * (tint >> 8 & 255) / 255) << 8
                    | (col & 255) * (tint & 255) / 255;
        }
        return col;
    }

    private static int entityColor(Entity e) {
        if (e instanceof Player) return 0x3C5AB4;
        SpawnEggItem egg = SpawnEggItem.byId(e.getType());
        if (egg != null) return egg.getColor(0);
        return 0x9A8A7A;
    }

    private static int[] skyColor(Vec3 dir, double day, Vec3 sun, boolean rain) {
        double el = Math.max(0, dir.y);
        int r = (int) (mix(170, 90, el) * day), g = (int) (mix(200, 140, el) * day), b = (int) (mix(255, 230, el) * day);
        if (rain) {
            int gr = (r + g + b) / 3;
            r = g = b = (int) (gr * 0.85);
        }
        if (dir.dot(sun) > 0.998) return new int[]{255, 250, 200};
        if (day < 0.5 && dir.dot(sun.scale(-1)) > 0.999) return new int[]{220, 220, 230};
        if (day < 0.3 && dir.y > 0.05 && !rain) {
            long h = Double.doubleToLongBits(Math.floor(dir.x * 600)) * 31 + Double.doubleToLongBits(Math.floor(dir.y * 600)) * 17
                    + Double.doubleToLongBits(Math.floor(dir.z * 600));
            if ((h ^ (h >>> 29)) % 401 == 0) return new int[]{230, 230, 230};
        }
        return new int[]{Math.max(r, 4), Math.max(g, 6), Math.max(b, 18)};
    }

    private static int mix(double a, double b, double f) {
        return (int) (a + (b - a) * f);
    }

    private static short rgb(int[] c) {
        return Pic.pack(c[0], c[1], c[2]);
    }
}
