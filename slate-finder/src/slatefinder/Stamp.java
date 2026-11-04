package slatefinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A small block model: a tree, an animal, a house. Voxel y=0 sits on the ground, and the model's column (x, z)
 * is drawn as a solid column up to its highest block, which is what the heightfield renderer needs.
 */
final class Stamp {
    private static final List<Stamp> ALL = new ArrayList<>();
    private static volatile Stamp[] TABLE = new Stamp[0];

    final int id, w, d, h;
    final byte[] v;
    /** The model is centred on its anchor: voxel (ax, az) is the column the stamp is placed at. */
    final int ax, az;

    Stamp(int w, int d, int h, int ax, int az) {
        this.w = w; this.d = d; this.h = h; this.ax = ax; this.az = az;
        this.v = new byte[w * d * h];
        synchronized (ALL) { this.id = ALL.size(); ALL.add(this); TABLE = ALL.toArray(new Stamp[0]); }
    }

    static Stamp get(int id) { return TABLE[id]; }

    byte at(int x, int y, int z) {
        if (x < 0 || z < 0 || y < 0 || x >= w || z >= d || y >= h) return 0;
        return v[(x * d + z) * h + y];
    }

    void set(int x, int y, int z, byte b) {
        if (x < 0 || z < 0 || y < 0 || x >= w || z >= d || y >= h) return;
        v[(x * d + z) * h + y] = b;
    }

    void box(int x0, int y0, int z0, int x1, int y1, int z1, byte b) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, b);
    }

    /** Highest block of a column, or -1. */
    int top(int x, int z) {
        if (x < 0 || z < 0 || x >= w || z >= d) return -1;
        int base = (x * d + z) * h;
        for (int y = h - 1; y >= 0; y--) if (v[base + y] != 0) return y;
        return -1;
    }

    /** The block seen from the side at height y: the one there, or the nearest below (the model is solid down to the ground). */
    byte side(int x, int y, int z) {
        if (x < 0 || z < 0 || x >= w || z >= d) return 0;
        int base = (x * d + z) * h;
        for (int k = Math.min(y, h - 1); k >= 0; k--) if (v[base + k] != 0) return v[base + k];
        for (int k = 0; k < h; k++) if (v[base + k] != 0) return v[base + k];
        return 0;
    }

    /** The same model turned a quarter turn. */
    Stamp rotated() {
        Stamp r = new Stamp(d, w, h, az, w - 1 - ax);
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) for (int y = 0; y < h; y++) r.set(z, y, w - 1 - x, at(x, y, z));
        return r;
    }

    // ------------------------------------------------------------------ trees and plants

    enum Tree { OAK, BIRCH, SPRUCE, DARK_OAK, ACACIA, JUNGLE, CHERRY, MANGROVE, PALE, CACTUS, BAMBOO, ICE_SPIKE, RED_MUSHROOM, BROWN_MUSHROOM, BUSH, BERRY_BUSH, FLOWER_R, FLOWER_Y, FLOWER_P, TALL_GRASS, DEAD_BUSH, BOULDER, FLOWER_TREE }

    private static final java.util.Map<Tree, Stamp[]> TREES = new java.util.EnumMap<>(Tree.class);

    static synchronized Stamp[] trees(Tree t) {
        Stamp[] s = TREES.get(t);
        if (s == null) {
            s = new Stamp[4];
            for (int i = 0; i < s.length; i++) s[i] = makeTree(t, new Random(t.ordinal() * 7919L + i * 104729L));
            TREES.put(t, s);
        }
        return s;
    }

    private static void blob(Stamp s, int cx, int cy, int cz, int r, int rh, byte leaf, Random rnd, double drop) {
        for (int y = cy - rh; y <= cy + rh; y++)
            for (int x = cx - r; x <= cx + r; x++)
                for (int z = cz - r; z <= cz + r; z++) {
                    double dd = (double) (x - cx) * (x - cx) / (r * r + 0.3) + (double) (y - cy) * (y - cy) / (rh * rh + 0.3) + (double) (z - cz) * (z - cz) / (r * r + 0.3);
                    if (dd <= 1.0 && (rnd == null || rnd.nextDouble() > drop) && s.at(x, y, z) == 0) s.set(x, y, z, leaf);
                }
    }

    private static Stamp makeTree(Tree t, Random rnd) {
        switch (t) {
            case OAK: case BIRCH: case FLOWER_TREE: {
                int th = (t == Tree.BIRCH ? 6 : 4) + rnd.nextInt(3);
                Stamp s = new Stamp(7, 7, th + 4, 3, 3);
                byte log = t == Tree.BIRCH ? Blocks3D.BIRCH_LOG : Blocks3D.OAK_LOG, leaf = t == Tree.BIRCH ? Blocks3D.BIRCH_LEAVES : t == Tree.FLOWER_TREE ? Blocks3D.LEAF_FLOWER : Blocks3D.OAK_LEAVES;
                for (int y = th - 2; y <= th + 1; y++) { int r = y >= th ? 1 : 2; for (int x = 3 - r; x <= 3 + r; x++) for (int z = 3 - r; z <= 3 + r; z++) if (!(r == 2 && Math.abs(x - 3) == 2 && Math.abs(z - 3) == 2 && rnd.nextBoolean())) s.set(x, y, z, leaf); }
                s.set(3, th + 2, 3, leaf);
                for (int y = 0; y < th; y++) s.set(3, y, 3, log);
                return s;
            }
            case DARK_OAK: {
                int th = 5 + rnd.nextInt(3);
                Stamp s = new Stamp(11, 11, th + 5, 5, 5);
                blob(s, 5, th, 5, 4, 2, Blocks3D.DARK_LEAVES, rnd, 0.06);
                blob(s, 5, th + 2, 5, 3, 1, Blocks3D.DARK_LEAVES, null, 0);
                for (int y = 0; y < th; y++) for (int x = 5; x <= 6; x++) for (int z = 5; z <= 6; z++) s.set(x, y, z, Blocks3D.DARK_LOG);
                return s;
            }
            case SPRUCE: {
                int th = 7 + rnd.nextInt(5);
                Stamp s = new Stamp(9, 9, th + 3, 4, 4);
                for (int y = 2; y <= th + 1; y++) {
                    int r = Math.max(0, Math.min(3, (th + 1 - y) * 3 / Math.max(4, th)));
                    if ((y & 1) == 0 && r > 0) r = Math.max(1, r - 1);
                    for (int x = 4 - r; x <= 4 + r; x++) for (int z = 4 - r; z <= 4 + r; z++) if (Math.abs(x - 4) + Math.abs(z - 4) <= r + (r > 1 ? 1 : 0)) s.set(x, y, z, Blocks3D.SPRUCE_LEAVES);
                }
                s.set(4, th + 2, 4, Blocks3D.SPRUCE_LEAVES);
                for (int y = 0; y <= th; y++) s.set(4, y, 4, Blocks3D.SPRUCE_LOG);
                return s;
            }
            case ACACIA: {
                int th = 4 + rnd.nextInt(3);
                Stamp s = new Stamp(11, 11, th + 5, 5, 5);
                int cx = 5, cz = 5, dx = rnd.nextBoolean() ? 1 : -1;
                for (int y = 0; y < th; y++) { s.set(cx, y, cz, Blocks3D.ACACIA_LOG); if (y == th / 2) cx += dx; }
                for (int k = 1; k <= 2; k++) s.set(cx + dx * k, th, cz, Blocks3D.ACACIA_LOG);
                blob(s, cx + dx, th + 1, cz, 4, 1, Blocks3D.ACACIA_LEAVES, rnd, 0.05);
                blob(s, cx - dx * 3, th, cz, 2, 1, Blocks3D.ACACIA_LEAVES, rnd, 0.05);
                return s;
            }
            case JUNGLE: {
                int th = 10 + rnd.nextInt(7);
                Stamp s = new Stamp(11, 11, th + 6, 5, 5);
                blob(s, 5, th + 1, 5, 4, 2, Blocks3D.JUNGLE_LEAVES, rnd, 0.1);
                blob(s, 5, th - 3, 5, 3, 1, Blocks3D.JUNGLE_LEAVES, rnd, 0.2);
                for (int y = 0; y <= th; y++) s.set(5, y, 5, Blocks3D.JUNGLE_LOG);
                for (int k = 0; k < 6; k++) { int vx = 1 + rnd.nextInt(9), vz = 1 + rnd.nextInt(9); for (int y = th - 1; y > th - 5; y--) if (s.at(vx, y + 1, vz) != 0 && s.at(vx, y, vz) == 0) s.set(vx, y, vz, Blocks3D.JUNGLE_LEAVES); }
                return s;
            }
            case CHERRY: {
                int th = 4 + rnd.nextInt(3);
                Stamp s = new Stamp(11, 11, th + 5, 5, 5);
                blob(s, 5, th + 1, 5, 4, 2, Blocks3D.CHERRY_LEAVES, rnd, 0.08);
                blob(s, 3, th, 4, 2, 1, Blocks3D.CHERRY_LEAVES, rnd, 0.1);
                for (int y = 0; y <= th; y++) s.set(5, y, 5, Blocks3D.CHERRY_LOG);
                s.set(4, th - 1, 5, Blocks3D.CHERRY_LOG); s.set(6, th, 5, Blocks3D.CHERRY_LOG);
                return s;
            }
            case MANGROVE: {
                int th = 5 + rnd.nextInt(3);
                Stamp s = new Stamp(9, 9, th + 5, 4, 4);
                blob(s, 4, th + 1, 4, 3, 2, Blocks3D.MANGROVE_LEAVES, rnd, 0.1);
                for (int y = 0; y <= th; y++) s.set(4, y, 4, Blocks3D.MANGROVE_LOG);
                int[][] roots = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 1}, {-2, -1}};
                for (int[] r : roots) { s.set(4 + r[0], 0, 4 + r[1], Blocks3D.MANGROVE_LOG); s.set(4 + r[0] * 2, 0, 4 + r[1] * 2, Blocks3D.MANGROVE_LOG); s.set(4 + r[0], 1, 4 + r[1], Blocks3D.MANGROVE_LOG); }
                return s;
            }
            case PALE: {
                int th = 7 + rnd.nextInt(3);
                Stamp s = new Stamp(11, 11, th + 5, 5, 5);
                blob(s, 5, th + 1, 5, 4, 2, Blocks3D.PALE_LEAVES, rnd, 0.12);
                for (int y = 0; y <= th; y++) { s.set(5, y, 5, Blocks3D.PALE_LOG); s.set(6, y, 5, Blocks3D.PALE_LOG); }
                for (int k = 0; k < 5; k++) { int vx = 2 + rnd.nextInt(7), vz = 2 + rnd.nextInt(7); for (int y = th - 1; y > th - 3; y--) if (s.at(vx, y, vz) == 0 && s.at(vx, y + 1, vz) != 0) s.set(vx, y, vz, Blocks3D.MOSS); }
                return s;
            }
            case CACTUS: { int th = 2 + rnd.nextInt(3); Stamp s = new Stamp(1, 1, th, 0, 0); s.box(0, 0, 0, 0, th - 1, 0, Blocks3D.CACTUS); return s; }
            case BAMBOO: { int th = 8 + rnd.nextInt(8); Stamp s = new Stamp(1, 1, th, 0, 0); s.box(0, 0, 0, 0, th - 1, 0, Blocks3D.BAMBOO); return s; }
            case ICE_SPIKE: {
                int th = 8 + rnd.nextInt(14);
                Stamp s = new Stamp(7, 7, th + 1, 3, 3);
                for (int y = 0; y < th; y++) { int r = Math.max(0, (th - y) * 3 / th); for (int x = 3 - r; x <= 3 + r; x++) for (int z = 3 - r; z <= 3 + r; z++) if (Math.abs(x - 3) + Math.abs(z - 3) <= r + 1) s.set(x, y, z, Blocks3D.PACKED_ICE); }
                return s;
            }
            case RED_MUSHROOM: case BROWN_MUSHROOM: {
                int th = 4 + rnd.nextInt(4);
                Stamp s = new Stamp(9, 9, th + 4, 4, 4);
                byte cap = t == Tree.RED_MUSHROOM ? Blocks3D.RED_MUSH : Blocks3D.BROWN_MUSH;
                for (int y = 0; y < th; y++) s.set(4, y, 4, Blocks3D.MUSH_STEM);
                blob(s, 4, th, 4, 4, 2, cap, null, 0);
                for (int y = th - 1; y <= th; y++) s.set(4, y, 4, Blocks3D.MUSH_STEM);
                return s;
            }
            case BUSH: { Stamp s = new Stamp(3, 3, 2, 1, 1); s.box(0, 0, 0, 2, 0, 2, Blocks3D.OAK_LEAVES); s.set(1, 1, 1, Blocks3D.OAK_LEAVES); return s; }
            case BERRY_BUSH: { Stamp s = new Stamp(2, 2, 2, 0, 0); s.box(0, 0, 0, 1, 1, 1, Blocks3D.SWEET_BERRY); return s; }
            case FLOWER_R: { Stamp s = new Stamp(1, 1, 1, 0, 0); s.set(0, 0, 0, Blocks3D.FLOWER_RED); return s; }
            case FLOWER_Y: { Stamp s = new Stamp(1, 1, 1, 0, 0); s.set(0, 0, 0, Blocks3D.FLOWER_YELLOW); return s; }
            case FLOWER_P: { Stamp s = new Stamp(1, 1, 1, 0, 0); s.set(0, 0, 0, Blocks3D.FLOWER_PINK); return s; }
            case TALL_GRASS: { Stamp s = new Stamp(1, 1, 1, 0, 0); s.set(0, 0, 0, Blocks3D.GRASS_TALL); return s; }
            case DEAD_BUSH: { Stamp s = new Stamp(1, 1, 1, 0, 0); s.set(0, 0, 0, Blocks3D.DEAD_BUSH); return s; }
            default: { Stamp s = new Stamp(3, 3, 2, 1, 1); s.box(0, 0, 0, 2, 0, 2, Blocks3D.STONE); s.set(1, 1, 1, Blocks3D.STONE); return s; }
        }
    }
}
