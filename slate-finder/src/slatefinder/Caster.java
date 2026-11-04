package slatefinder;

import java.util.stream.IntStream;

/**
 * Draws the world: a ray caster for the 3D views (every pixel follows a ray through the blocks, with textures, sun,
 * shadows, water and fog) and a top-down map renderer for the flat views.
 */
final class Caster {
    final Terrain world;

    Caster(Terrain w) { this.world = w; }

    static final class Cam {
        double x, y, z, yaw, pitch = -0.4, fov = 1.0;
        Cam copy() { Cam c = new Cam(); c.x = x; c.y = y; c.z = z; c.yaw = yaw; c.pitch = pitch; c.fov = fov; return c; }
        double fx() { return Math.cos(pitch) * Math.sin(yaw); }
        double fy() { return Math.sin(pitch); }
        double fz() { return Math.cos(pitch) * Math.cos(yaw); }
    }

    static final class Opts {
        double sunElev = 50, sunAz = 215;
        int maxDist = 520;
        boolean shadows = true, ao = true, clouds = true;
        double fog = 1.0;
        int skyTop = 0xFF5C98E0, skyHorizon = 0xFFCFE2F4;
        boolean underwaterTint = true;
        /** Flat colour per block instead of its picture: quicker to draw and easier to read from far away. */
        boolean flat;
    }

    // ------------------------------------------------------------------ colours

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }
    private static int rgb(int r, int g, int b) { return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
    static int mix(int a, int b, double t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return rgb((int) (ar + (br - ar) * t), (int) (ag + (bg - ag) * t), (int) (ab + (bb - ab) * t));
    }
    private static int scale(int c, double f) { return rgb((int) (((c >> 16) & 255) * f), (int) (((c >> 8) & 255) * f), (int) ((c & 255) * f)); }
    private static double lum(int c) { return 0.3 * ((c >> 16) & 255) + 0.59 * ((c >> 8) & 255) + 0.11 * (c & 255); }

    private boolean tinted(byte b) {
        if (world.exactTextures && (b == Blocks3D.BIRCH_LEAVES || b == Blocks3D.SPRUCE_LEAVES)) return true;
        return b == Blocks3D.GRASS || b == Blocks3D.GRASS_TALL || b == Blocks3D.OAK_LEAVES || b == Blocks3D.JUNGLE_LEAVES || b == Blocks3D.ACACIA_LEAVES || b == Blocks3D.DARK_LEAVES || b == Blocks3D.MANGROVE_LEAVES || b == Blocks3D.MOSS;
    }

    private static int applyTint(int tex, int tint, byte b) {
        int bi = b & 255;
        int r = (tex >> 16) & 255, g = (tex >> 8) & 255, bl = tex & 255;
        if (g < r * 1.05 || g < bl * 1.25) return tex;                       // not a green pixel: leave it
        double f = lum(tex) / Math.max(40, lum(Blocks3D.AVG[bi]));
        return scale(tint, f);
    }

    /** The colour of a block's texel. */
    private int texel(byte b, int face, double fu, double fv, int tint) {
        int bi = b & 255;
        int[] t = world.exactTextures ? RealTextures.pick(bi, face == 0) : null;
        boolean real = t != null;
        if (t == null) t = face == 0 ? Blocks3D.TOP[bi] : Blocks3D.SIDE[bi];
        if (t == null) t = Blocks3D.TOP[Blocks3D.STONE];
        int c = t[((int) (fv * 16) & 15) * 16 + ((int) (fu * 16) & 15)];
        if (tint != 0 && tinted(b)) c = real ? multiply(c, tint) : applyTint(c, tint, b);
        return c;
    }

    /** The game's own leaf and grass pictures are grey and get their colour from the biome: multiply. */
    private static int multiply(int c, int tint) {
        return rgb((((c >> 16) & 255) * ((tint >> 16) & 255)) / 255, (((c >> 8) & 255) * ((tint >> 8) & 255)) / 255, ((c & 255) * (tint & 255)) / 255);
    }

    // ------------------------------------------------------------------ the sky

    private int sky(double dx, double dy, double dz, Opts o, double sx, double sy, double sz) {
        double t = Math.max(0, dy);
        t = Math.pow(t, 0.6);
        int base = mix(o.skyHorizon, o.skyTop, Math.min(1, t * 1.2));
        double warm = Math.max(0, (28 - o.sunElev) / 28.0);
        if (warm > 0) base = mix(base, 0xFFF2B880, warm * Math.pow(Math.max(0, 1 - t * 2.5), 2) * 0.7);
        double sd = dx * sx + dy * sy + dz * sz;
        if (sd > 0.9992) return 0xFFFFFFF0;
        if (sd > 0.97) base = mix(base, 0xFFFFF4D0, (sd - 0.97) / 0.03 * 0.5);
        return base;
    }

    // ------------------------------------------------------------------ the ray

    /** Marches a ray across the columns. Fills hit with t, face, block, x, z, y, tWater, step sign, chunk index; returns true on a hit. */
    private boolean march(double ox, double oy, double oz, double dx, double dy, double dz, double maxD, double[] hit, double topLimit) {
        int cx = (int) Math.floor(ox), cz = (int) Math.floor(oz);
        int sx = dx > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? 1e30 : Math.abs(1 / dx), tdz = dz == 0 ? 1e30 : Math.abs(1 / dz);
        double tmx = dx == 0 ? 1e30 : ((dx > 0 ? cx + 1 - ox : ox - cx) * tdx);
        double tmz = dz == 0 ? 1e30 : ((dz > 0 ? cz + 1 - oz : oz - cz) * tdz);
        double t = 0;
        int axis = -1;
        boolean inWater = false;
        double tWater = -1;
        Terrain.Chunk ch = null;
        int lcx = Integer.MIN_VALUE, lcz = Integer.MIN_VALUE;
        int ceilY = 0;
        while (true) {
            int ccx = cx >> 4, ccz = cz >> 4;
            if (ch == null || ccx != lcx || ccz != lcz) { ch = world.chunk(ccx, ccz); lcx = ccx; lcz = ccz; ceilY = Math.max(ch.maxH(), Terrain.SEA) + 1; }
            // above everything in this chunk, and still above it where the ray leaves it: cross the chunk without looking at a column
            if (oy + dy * t > ceilY) {
                double bx = dx > 0 ? ((ccx + 1) * 16 - ox) / dx : dx < 0 ? (ccx * 16 - ox) / dx : 1e30;
                double bz = dz > 0 ? ((ccz + 1) * 16 - oz) / dz : dz < 0 ? (ccz * 16 - oz) / dz : 1e30;
                double tc = Math.min(Math.min(bx, bz), maxD + 1);
                if (oy + dy * tc > ceilY) {
                    do {
                        if (tmx < tmz) { t = tmx; tmx += tdx; cx += sx; axis = 0; } else { t = tmz; tmz += tdz; cz += sz; axis = 1; }
                    } while ((cx >> 4) == ccx && (cz >> 4) == ccz);
                    if (t > maxD) { hit[6] = tWater; hit[9] = inWater ? hit[9] : 0; return false; }
                    if (dy > 0 && oy + dy * t > topLimit) { hit[6] = tWater; return false; }
                    continue;
                }
            }
            int idx = ((cz & 15) << 4) | (cx & 15);
            double tExit = Math.min(tmx, tmz);
            double yEntry = oy + dy * t, yExit = oy + dy * tExit;
            int h = ch.h[idx];
            if ((ch.flags[idx] & Terrain.F_WATER) != 0 && !inWater && yEntry > Terrain.SEA && yExit <= Terrain.SEA && dy < 0) {
                inWater = true;
                tWater = (Terrain.SEA - oy) / dy;
                hit[9] = world.waterTint(ch, idx);
            }
            int sid = ch.stamp[idx];
            if (sid >= 0) {
                // a tree or a building: look at its blocks one by one, so a canopy can hang over a bare trunk
                Stamp sp = Stamp.get(sid);
                int ter = ch.ter[idx], gy = ch.gy[idx], sxl = ch.sx[idx], szl = ch.sz[idx];
                int yHi = (int) Math.floor(Math.max(yEntry, yExit)), yLo = (int) Math.floor(Math.min(yEntry, yExit));
                int ev = (int) Math.floor(yEntry);
                int ceil = Math.max(ter, gy + sp.h);
                if (yHi > ceil) yHi = ceil;
                boolean desc = dy <= 0;
                for (int k = 0; k <= yHi - yLo; k++) {
                    int y = desc ? yHi - k : yLo + k;
                    if (y < 0) continue;
                    byte b;
                    if (y < ter) b = (y == ter - 1) ? ch.top[idx] : world.groundBlock(ch, idx, y);
                    else if (y >= gy) { b = sp.at(sxl, y - gy, szl); if (b == 0) continue; }
                    else continue;
                    boolean fromSide = (y == ev) && axis >= 0;
                    if (y == ev && axis < 0) continue;
                    double th; int face;
                    if (fromSide) { th = t; face = axis == 0 ? 1 : 2; }
                    else if (desc) { th = (y + 1 - oy) / dy; face = 0; }
                    else { th = (y - oy) / dy; face = 3; }
                    if (th < t - 1e-9) th = t;
                    hit[0] = th; hit[1] = face; hit[2] = b; hit[3] = cx; hit[4] = cz; hit[5] = face == 0 ? y + 1 : y; hit[6] = tWater; hit[7] = axis == 0 ? sx : sz; hit[8] = idx;
                    hit[10] = world.grassTint(ch, idx); hit[11] = world.foliageTint(ch, idx);
                    hit[12] = ccx; hit[13] = ccz;
                    return true;
                }
            } else {
                if (axis >= 0 && yEntry < h && yEntry >= 0) {
                    hit[0] = t; hit[1] = axis == 0 ? 1 : 2; hit[3] = cx; hit[4] = cz; hit[5] = Math.floor(yEntry); hit[6] = tWater; hit[7] = axis == 0 ? sx : sz; hit[8] = idx;
                    hit[2] = world.sideBlock(ch, idx, (int) Math.floor(yEntry));
                    hit[10] = world.grassTint(ch, idx); hit[11] = world.foliageTint(ch, idx);
                    hit[12] = ccx; hit[13] = ccz;
                    return true;
                }
                if (yEntry >= h && yExit < h && dy < 0) {
                    double tt = (h - oy) / dy;
                    hit[0] = tt; hit[1] = 0; hit[3] = cx; hit[4] = cz; hit[5] = h; hit[6] = tWater; hit[7] = 0; hit[8] = idx;
                    hit[2] = ch.faceTop[idx];
                    hit[10] = world.grassTint(ch, idx); hit[11] = world.foliageTint(ch, idx);
                    hit[12] = ccx; hit[13] = ccz;
                    return true;
                }
            }
            if (tmx < tmz) { t = tmx; tmx += tdx; cx += sx; axis = 0; } else { t = tmz; tmz += tdz; cz += sz; axis = 1; }
            if (t > maxD) { hit[6] = tWater; hit[9] = inWater ? hit[9] : 0; return false; }
            if (dy > 0 && oy + dy * t > topLimit) { hit[6] = tWater; return false; }
        }
    }

    /** Whether the sun can see a point: a short march toward it. */
    private boolean lit(double px, double py, double pz, double sx, double sy, double sz) {
        Terrain.Chunk ch = null;
        int lcx = Integer.MIN_VALUE, lcz = Integer.MIN_VALUE;
        for (int k = 1; k <= 56; k++) {
            double s = k * (k > 24 ? 2.0 : 1.0);
            int x = (int) Math.floor(px + sx * s), z = (int) Math.floor(pz + sz * s);
            double y = py + sy * s;
            if (y > 260) return true;
            int ccx = x >> 4, ccz = z >> 4;
            if (ch == null || ccx != lcx || ccz != lcz) { ch = world.chunk(ccx, ccz); lcx = ccx; lcz = ccz; }
            if (ch.h[((z & 15) << 4) | (x & 15)] > y) return false;
        }
        return true;
    }

    private int shade(double[] hit, double ox, double oy, double oz, double dx, double dy, double dz, Opts o, double sx, double sy, double sz, boolean detail) {
        double t = hit[0];
        int face = (int) hit[1];
        byte b = (byte) hit[2];
        double px = ox + dx * t, py = oy + dy * t, pz = oz + dz * t;
        double fu, fv;
        double nx = 0, ny = 0, nz = 0;
        if (face == 0) { fu = px - Math.floor(px); fv = pz - Math.floor(pz); ny = 1; }
        else if (face == 3) { fu = px - Math.floor(px); fv = pz - Math.floor(pz); ny = -1; }
        else if (face == 1) { fu = pz - Math.floor(pz); fv = 1 - (py - Math.floor(py - 1e-6)); nx = -hit[7]; }
        else { fu = px - Math.floor(px); fv = 1 - (py - Math.floor(py - 1e-6)); nz = -hit[7]; }
        if (fv >= 1) fv = 0.999;
        int tint = (b == Blocks3D.GRASS || b == Blocks3D.GRASS_TALL || (b == Blocks3D.MOSS && !world.exactTextures)) ? (int) hit[10] : (Blocks3D.leaf(b) && b != Blocks3D.BIRCH_LEAVES && b != Blocks3D.SPRUCE_LEAVES && b != Blocks3D.CHERRY_LEAVES && b != Blocks3D.PALE_LEAVES && b != Blocks3D.LEAF_FLOWER) ? (int) hit[11] : 0;
        if (world.exactTextures) { if (b == Blocks3D.BIRCH_LEAVES) tint = 0xFF80A755; else if (b == Blocks3D.SPRUCE_LEAVES) tint = 0xFF619961; }
        int col = o.flat ? avgColour(b, tint) : texel(b, face == 3 ? 0 : face, fu, fv, tint);
        double dot = Math.max(0, nx * sx + ny * sy + nz * sz);
        double light = 0.40 + 0.62 * dot;
        if (face == 1) light *= 0.92;
        if (detail && dot > 0 && o.shadows && !lit(px + nx * 0.02, py + ny * 0.02 + 0.02, pz + nz * 0.02, sx, sy, sz)) light = 0.40 + 0.0 * dot;
        if (detail && o.ao && face == 0) {
            int cx = (int) hit[3], cz = (int) hit[4], h = (int) hit[5];
            int n = 0;
            if (world.topAt(cx + 1, cz) > h + 1) n++;
            if (world.topAt(cx - 1, cz) > h + 1) n++;
            if (world.topAt(cx, cz + 1) > h + 1) n++;
            if (world.topAt(cx, cz - 1) > h + 1) n++;
            light *= 1 - 0.07 * n;
        }
        if (b == Blocks3D.SEA_LANTERN || b == Blocks3D.LANTERN) light = Math.max(light, 1.05);
        col = scale(col, Math.max(0.5, light * Math.min(1.1, 0.6 + o.sunElev / 90.0)));
        double tw = hit[6];
        if (tw >= 0 && tw < t) {
            int wcol = (int) hit[9];
            if (wcol == 0) wcol = 0xFF3F76E4;
            double depth = t - tw;
            double a = Math.min(0.95, 0.2 + 0.7 * (1 - Math.exp(-depth * 0.075)));
            int wl = scale(wcol, 0.55 + 0.45 * Math.min(1, o.sunElev / 50.0));
            col = mix(scale(col, 0.85), wl, a);
            // a hint of the sky on the surface, stronger when looking along it
            double fr = 0.05 + 0.4 * Math.pow(1 - Math.abs(dy), 4);
            col = mix(col, sky(dx, -dy, dz, o, sx, sy, sz), fr);
        }
        double f = Math.min(1, t / (o.maxDist * Math.max(0.3, o.fog)));
        double haze = 0.10 * f + 0.9 * Math.pow(f, 3.2);
        return mix(col, o.skyHorizon, Math.min(1, haze));
    }

    // ------------------------------------------------------------------ 3D

    /** Renders a view. {@code step} is the size of the blocks of pixels the picture is drawn in (4 for a quick look, 1 for the real thing). */
    void render3D(int[] out, int w, int h, int step, Cam cam, Opts o, boolean detail) { render3D(out, w, h, step, cam, o, detail, null); }

    /** As above, but gives up as soon as {@code stale} says the picture is no longer wanted (the person moved again). */
    void render3D(int[] out, int w, int h, int step, Cam cam, Opts o, boolean detail, java.util.function.BooleanSupplier stale) {
        double el = Math.toRadians(o.sunElev), az = Math.toRadians(o.sunAz);
        final double sx = Math.cos(el) * Math.sin(az), sy = Math.sin(el), sz = Math.cos(el) * Math.cos(az);
        final double fx = cam.fx(), fy = cam.fy(), fz = cam.fz();
        final double rx = -Math.cos(cam.yaw), rz = Math.sin(cam.yaw);
        // up = right x forward
        final double upx = (0 * fz - rz * fy), upy = (rz * fx - rx * fz), upz = (rx * fy - 0 * fx);
        final double th = Math.tan(cam.fov / 2), aspect = w / (double) h;
        final int rows = (h + step - 1) / step, cols = (w + step - 1) / step;
        final double topLimit = 330;
        IntStream.range(0, rows).parallel().forEach(r -> {
            if (stale != null && stale.getAsBoolean()) return;
            double[] hit = new double[16];
            int py = r * step;
            double v = (1 - (py + step * 0.5) / h * 2) * th;
            for (int cI = 0; cI < cols; cI++) {
                int px = cI * step;
                double u = ((px + step * 0.5) / w * 2 - 1) * th * aspect;
                double dx = fx + rx * u + upx * v, dy = fy + 0 * u + upy * v, dz = fz + rz * u + upz * v;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= len; dy /= len; dz /= len;
                java.util.Arrays.fill(hit, 0);
                int c;
                if (march(cam.x, cam.y, cam.z, dx, dy, dz, o.maxDist, hit, topLimit)) c = shade(hit, cam.x, cam.y, cam.z, dx, dy, dz, o, sx, sy, sz, detail);
                else {
                    c = sky(dx, dy, dz, o, sx, sy, sz);
                    if (hit[6] >= 0 && dy < 0) c = mix(c, hit[9] == 0 ? 0xFF3F76E4 : (int) hit[9], 0.75);
                    else if (hit[6] >= 0) c = mix(c, hit[9] == 0 ? 0xFF3F76E4 : (int) hit[9], 0.6);
                }
                int xe = Math.min(w, px + step), ye = Math.min(h, py + step);
                for (int yy = py; yy < ye; yy++) { int base = yy * w; for (int xx = px; xx < xe; xx++) out[base + xx] = c; }
            }
        });
    }

    /** What is under a pixel: {x, y, z} of the point hit, or null for the sky. */
    double[] pick(Cam cam, int w, int h, int px, int py, Opts o) {
        double fx = cam.fx(), fy = cam.fy(), fz = cam.fz();
        double rx = -Math.cos(cam.yaw), rz = Math.sin(cam.yaw);
        double upx = (0 * fz - rz * fy), upy = (rz * fx - rx * fz), upz = (rx * fy - 0 * fx);
        double th = Math.tan(cam.fov / 2), aspect = w / (double) h;
        double u = ((px + 0.5) / w * 2 - 1) * th * aspect, v = (1 - (py + 0.5) / h * 2) * th;
        double dx = fx + rx * u + upx * v, dy = fy + upy * v, dz = fz + rz * u + upz * v;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len; dy /= len; dz /= len;
        double[] hit = new double[16];
        if (!march(cam.x, cam.y, cam.z, dx, dy, dz, Math.max(o.maxDist, 3000), hit, 330)) return null;
        return new double[]{cam.x + dx * hit[0], cam.y + dy * hit[0], cam.z + dz * hit[0]};
    }

    /** Projects a world point to the screen: {x, y, depth}, or null if it is behind the camera. */
    static double[] project(Cam cam, int w, int h, double wx, double wy, double wz) {
        double fx = cam.fx(), fy = cam.fy(), fz = cam.fz();
        double rx = -Math.cos(cam.yaw), rz = Math.sin(cam.yaw);
        double upx = (0 * fz - rz * fy), upy = (rz * fx - rx * fz), upz = (rx * fy - 0 * fx);
        double dx = wx - cam.x, dy = wy - cam.y, dz = wz - cam.z;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.3) return null;
        double th = Math.tan(cam.fov / 2), aspect = w / (double) h;
        double u = (dx * rx + dz * rz) / depth, v = (dx * upx + dy * upy + dz * upz) / depth;
        return new double[]{(u / (th * aspect) + 1) * 0.5 * w, (1 - v / th) * 0.5 * h, depth};
    }

    // ------------------------------------------------------------------ the flat map

    /** Draws the map from above. {@code bpp} is blocks per pixel; below about 1.6 the real columns are drawn, beyond that a quick biome relief. */
    void renderMap(int[] out, int w, int h, double cx, double cz, double bpp, Opts o, boolean contours) { renderMap(out, w, h, cx, cz, bpp, o, contours, null); }

    void renderMap(int[] out, int w, int h, double cx, double cz, double bpp, Opts o, boolean contours, java.util.function.BooleanSupplier stale) {
        final boolean close = bpp <= 1.6;
        double el = Math.toRadians(o.sunElev), az = Math.toRadians(o.sunAz);
        IntStream.range(0, h).parallel().forEach(py -> {
            if (stale != null && stale.getAsBoolean()) return;
            for (int px = 0; px < w; px++) {
                double wx = cx + (px - w / 2.0) * bpp, wz = cz + (py - h / 2.0) * bpp;
                out[py * w + px] = close ? closeColour(wx, wz, bpp, o) : farColour(wx, wz, bpp, o);
            }
        });
    }

    private int avgColour(byte b, int tint) {
        int bi = b & 255;
        if (world.exactTextures) {
            int a = RealTextures.avg(bi);
            if (a != 0) return tint != 0 && tinted(b) ? multiply(a, tint) : a;
        }
        return tint != 0 && tinted(b) ? applyTint(Blocks3D.AVG[bi], tint, b) : Blocks3D.AVG[bi];
    }

    private int closeColour(double wx, double wz, double bpp, Opts o) {
        int x = (int) Math.floor(wx), z = (int) Math.floor(wz);
        Terrain.Chunk ch = world.chunk(x >> 4, z >> 4);
        int idx = ((z & 15) << 4) | (x & 15);
        byte b = ch.faceTop[idx];
        double fu = wx - x, fv = wz - z;
        int tint = (b == Blocks3D.GRASS || b == Blocks3D.GRASS_TALL || b == Blocks3D.MOSS) ? world.grassTint(ch, idx) : Blocks3D.leaf(b) ? world.foliageTint(ch, idx) : 0;
        int col = bpp < 0.6 ? texel(b, 0, fu, fv, tint) : avgColour(b, tint);
        int h = ch.h[idx];
        int hE = world.topAt(x + 1, z), hS = world.topAt(x, z + 1), hW = world.topAt(x - 1, z), hN = world.topAt(x, z - 1);
        double slope = ((hW - hE) * 0.5 + (hN - hS) * 0.5) * 0.045;
        double light = 0.82 + slope + (h > 150 ? 0.05 : 0);
        if ((ch.flags[idx] & Terrain.F_WATER) != 0) {
            int ter = ch.ter[idx];
            double depth = Terrain.SEA - ter;
            int wc = world.waterTint(ch, idx);
            col = mix(scale(col, 0.7), wc, Math.min(0.95, 0.45 + depth * 0.05));
            col = scale(col, Math.max(0.35, 1 - depth * 0.012));
            light = 0.95 + slope * 0.3;
        }
        return scale(col, Math.max(0.5, Math.min(1.25, light)));
    }

    private int farColour(double wx, double wz, double bpp, Opts o) {
        int x = (int) Math.floor(wx), z = (int) Math.floor(wz);
        int bi = world.biomeIndexAt(x, z);
        Terrain.Style st = world.styles[bi];
        double hgt = world.rawHeight(x, z);
        double d = Math.max(2, bpp * 0.6);
        double hE = world.rawHeight((int) (x + d), z), hW = world.rawHeight((int) (x - d), z), hS = world.rawHeight(x, (int) (z + d)), hN = world.rawHeight(x, (int) (z - d));
        double slope = ((hW - hE) + (hN - hS)) * 0.5 / d * 6;
        int col;
        if (hgt < Terrain.SEA) {
            double depth = Terrain.SEA - hgt;
            col = mix(scale(st.water, 0.95), scale(st.water, 0.45), Math.min(1, depth / 38.0));
            if (st.ice) col = mix(col, 0xFFDCEEFF, 0.55);
            return scale(col, 1 + slope * 0.05);
        }
        byte top = st.top;
        if (st.snowy && st.top != Blocks3D.SNOW && hgt > Terrain.SEA + 3) top = Blocks3D.SNOW;
        if (hgt > 125 && (st.snowy || hgt > 140)) top = Blocks3D.SNOW;
        col = Blocks3D.AVG[top];
        if (tinted(top)) col = applyTint(Blocks3D.AVG[top], st.grass, top);
        if (st.trees.length > 0 && st.density > 0.08) col = mix(col, applyTint(Blocks3D.AVG[Blocks3D.OAK_LEAVES], st.foliage, Blocks3D.OAK_LEAVES), Math.min(0.6, st.density * 1.1));
        if (st.id.equals("snowy_taiga") || st.id.equals("taiga") || st.id.contains("spruce") || st.id.contains("pine")) col = mix(col, Blocks3D.AVG[Blocks3D.SPRUCE_LEAVES], Math.min(0.5, st.density * 1.1));
        if (st.mesa) col = Blocks3D.AVG[Blocks3D.TERRA + ((int) hgt * 5 / 3 & 7)];
        double light = 0.85 + slope * 0.09 + Math.min(0.12, (hgt - 70) * 0.0012);
        return scale(col, Math.max(0.55, Math.min(1.3, light)));
    }
}
