package slatefinder;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * A made-up world written in the game's own file format (region files with NBT chunks, palettes and packed longs), so the
 * reader can be tested without the real game: hills, a sea, a lake high in the mountains, trees, a house, plants, snow.
 */
public final class ExactTestWorld {
    public interface Fn {
        String block(int x, int y, int z);
        String biome(int x, int y, int z);
    }

    // ------------------------------------------------------------------ the world

    public static final class Demo implements Fn {
        final long seed;
        public Demo(long seed) { this.seed = seed; }

        static long hash(long a, long b, long s) {
            long v = a * 374761393L + b * 668265263L + s * 2147483647L;
            v = (v ^ (v >>> 13)) * 1274126177L;
            return (v ^ (v >>> 16)) & 0x7FFFFFFFL;
        }

        boolean inHouse(int x, int z) { return x >= 28 && x <= 38 && z >= 28 && z <= 38; }
        public int height(int x, int z) {
            if (inHouse(x, z)) return 75;
            double d = Math.hypot(x + 40, z + 40);
            if (d < 25) return 48;                                           // the sea floor
            if (Math.hypot(x - 100, z - 100) < 12) return Math.hypot(x - 100, z - 100) < 5 ? 117 : 120;   // a lake in the mountains
            return 70 + (int) Math.round(7 * Math.sin((x + seed % 100) * 0.07) + 5 * Math.cos(z * 0.05));
        }
        boolean desert(int x) { return x >= 120 && x < 150; }
        boolean snowy(int x) { return x >= 150; }

        public boolean treeAt(int x, int z) {
            return x % 11 == 3 && z % 13 == 5 && height(x, z) > 64 && !inHouse(x, z) && !desert(x) && Math.hypot(x - 100, z - 100) > 14 && !(x >= 20 && x <= 46 && z >= 20 && z <= 46);
        }

        @Override public String biome(int x, int y, int z) { return desert(x) ? "minecraft:desert" : snowy(x) ? "minecraft:snowy_plains" : (z > 200 ? "minecraft:forest" : "minecraft:plains"); }

        @Override public String block(int x, int y, int z) {
            if (y < -64 || y > 319) return "minecraft:air";
            if (y == -64) return "minecraft:bedrock";
            int h = height(x, z);
            // the house: oak planks, a glass window, a door gap, a roof, and nothing inside
            if (inHouse(x, z)) {
                if (y < 75) return y >= 72 ? "minecraft:dirt" : "minecraft:stone";
                if (y == 75) return "minecraft:cobblestone";
                boolean wall = x == 28 || x == 38 || z == 28 || z == 38;
                if (y >= 76 && y <= 78 && wall) {
                    if (x == 33 && z == 38 && y <= 77) return "minecraft:air";                 // the door
                    if (x == 28 && z == 33 && y == 77) return "minecraft:glass_pane";          // a window
                    return "minecraft:oak_planks";
                }
                if (y == 79) return x == 28 || x == 38 || z == 28 || z == 38 ? "minecraft:oak_planks" : "minecraft:oak_stairs";
                if (y == 77 && x == 31 && z == 31) return "minecraft:torch";
                return "minecraft:air";
            }
            if (y <= h) {
                // inside the ground
                if (desert(x)) return y >= h - 3 ? "minecraft:sand" : "minecraft:sandstone";
                if (y == h) {
                    if (height(x, z) <= 48) return "minecraft:sand";
                    return "minecraft:grass_block";
                }
                if (y >= h - 3) return "minecraft:dirt";
                if (y < 0) return y % 7 == 0 ? "minecraft:coal_ore" : "minecraft:deepslate";
                return y % 9 == 0 ? "minecraft:iron_ore" : "minecraft:stone";
            }
            // above the ground
            if (h <= 48 && Math.hypot(x + 40, z + 40) < 25) {           // the sea
                if (y <= 62) {
                    if (y <= h + 4 && hash(x, z, 3) % 5 == 0) return "minecraft:kelp";
                    return "minecraft:water";
                }
                return "minecraft:air";
            }
            if (h == 117 && y <= 120) return "minecraft:water";         // the mountain lake
            // trees
            for (int tx = x - 2; tx <= x + 2; tx++) for (int tz = z - 2; tz <= z + 2; tz++) {
                if (!treeAt(tx, tz)) continue;
                int th = height(tx, tz);
                int dx = Math.abs(x - tx), dz = Math.abs(z - tz);
                if (dx == 0 && dz == 0 && y > th && y <= th + 5) return "minecraft:oak_log";
                if (y >= th + 4 && y <= th + 5 && dx <= 2 && dz <= 2 && !(dx == 2 && dz == 2)) return (dx == 0 && dz == 0) ? "minecraft:oak_log" : "minecraft:oak_leaves";
                if (y == th + 6 && dx <= 1 && dz <= 1 && !(dx == 1 && dz == 1)) return "minecraft:oak_leaves";
            }
            if (y == h + 1) {
                if (snowy(x)) return "minecraft:snow";
                long r = hash(x, z, 9);
                if (!desert(x)) {
                    if (r % 7 == 0) return "minecraft:short_grass";
                    if (r % 29 == 1) return "minecraft:poppy";
                    if (r % 41 == 2) return "minecraft:tall_grass";
                    if (r % 53 == 3) return "minecraft:cherry_stairs";
                    if (r % 61 == 4) return "minecraft:amethyst_cluster";
                }
            }
            if (y == h + 2 && !snowy(x) && !desert(x) && hash(x, z, 9) % 41 == 2) return "minecraft:tall_grass";
            return "minecraft:air";
        }
    }

    // ------------------------------------------------------------------ NBT out

    static final class W {
        final ByteArrayOutputStream bo = new ByteArrayOutputStream();
        final DataOutputStream o = new DataOutputStream(bo);
        void head(int type, String name) throws IOException { o.writeByte(type); o.writeUTF(name); }
        void compoundStart(String name) throws IOException { head(10, name); }
        void end() throws IOException { o.writeByte(0); }
        void str(String name, String v) throws IOException { head(8, name); o.writeUTF(v); }
        void i32(String name, int v) throws IOException { head(3, name); o.writeInt(v); }
        void i8(String name, int v) throws IOException { head(1, name); o.writeByte(v); }
        void longs(String name, long[] v) throws IOException { head(12, name); o.writeInt(v.length); for (long l : v) o.writeLong(l); }
        void bytes(String name, byte[] v) throws IOException { head(7, name); o.writeInt(v.length); o.write(v); }
        void listStart(String name, int elemType, int n) throws IOException { head(9, name); o.writeByte(elemType); o.writeInt(n); }
    }

    static long[] pack(int[] values, int bits) {
        int per = 64 / bits;
        long[] out = new long[(values.length + per - 1) / per];
        for (int i = 0; i < values.length; i++) out[i / per] |= ((long) values[i]) << ((i % per) * bits);
        return out;
    }

    static byte[] chunk(int cx, int cz, Fn fn, String status) throws IOException {
        W w = new W();
        w.compoundStart("");
        w.i32("DataVersion", 4671);
        w.i32("xPos", cx); w.i32("yPos", -4); w.i32("zPos", cz);
        w.str("Status", status);
        w.head(4, "LastUpdate"); w.o.writeLong(1234L);
        w.listStart("sections", 10, 26);
        for (int sy = -5; sy <= 20; sy++) {
            w.i8("Y", sy);
            if (sy < -4 || sy > 19) {                                    // the game also writes the sections just outside the world, with light only
                w.bytes("SkyLight", new byte[2048]);
                w.end();
                continue;
            }
            // blocks
            LinkedHashMap<String, Integer> pal = new LinkedHashMap<>();
            int[] idx = new int[4096];
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                String b = fn.block(cx * 16 + x, sy * 16 + y, cz * 16 + z);
                Integer k = pal.get(b);
                if (k == null) { k = pal.size(); pal.put(b, k); }
                idx[(y << 8) | (z << 4) | x] = k;
            }
            w.compoundStart("block_states");
            w.listStart("palette", 10, pal.size());
            for (String name : pal.keySet()) {
                w.str("Name", name);
                if (name.endsWith("stairs")) { w.compoundStart("Properties"); w.str("facing", "north"); w.str("half", "bottom"); w.end(); }
                w.end();
            }
            if (pal.size() > 1) {
                int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(pal.size() - 1));
                w.longs("data", pack(idx, bits));
            }
            w.end();
            // biomes
            LinkedHashMap<String, Integer> bp = new LinkedHashMap<>();
            int[] bi = new int[64];
            for (int y = 0; y < 4; y++) for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++) {
                String b = fn.biome(cx * 16 + x * 4, sy * 16 + y * 4, cz * 16 + z * 4);
                Integer k = bp.get(b);
                if (k == null) { k = bp.size(); bp.put(b, k); }
                bi[(y << 4) | (z << 2) | x] = k;
            }
            w.compoundStart("biomes");
            w.listStart("palette", 8, bp.size());
            for (String name : bp.keySet()) w.o.writeUTF(name);
            if (bp.size() > 1) w.longs("data", pack(bi, Math.max(1, 32 - Integer.numberOfLeadingZeros(bp.size() - 1))));
            w.end();
            w.bytes("SkyLight", new byte[2048]);
            w.end();
        }
        w.compoundStart("Heightmaps");
        w.longs("WORLD_SURFACE", new long[37]);
        w.end();
        w.end();
        return w.bo.toByteArray();
    }

    /** Writes the region files for these chunks into world/region (rewriting any that are there). */
    public static void writeRegions(File world, Map<Long, byte[]> chunks) throws IOException {
        File dir = new File(world, "region");
        dir.mkdirs();
        Map<Long, List<long[]>> byRegion = new HashMap<>();
        for (Long key : chunks.keySet()) {
            int cx = (int) (key >> 32), cz = (int) (long) key;
            long rk = ((long) Math.floorDiv(cx, 32) << 32) ^ (Math.floorDiv(cz, 32) & 0xFFFFFFFFL);
            byRegion.computeIfAbsent(rk, k -> new ArrayList<>()).add(new long[]{cx, cz});
        }
        for (Map.Entry<Long, List<long[]>> e : byRegion.entrySet()) {
            int rx = (int) (e.getKey() >> 32), rz = (int) (long) e.getKey();
            File f = new File(dir, "r." + rx + "." + rz + ".mca");
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            int[] loc = new int[1024];
            int sector = 2;
            for (long[] c : e.getValue()) {
                int cx = (int) c[0], cz = (int) c[1];
                byte[] raw = chunks.get(((long) cx << 32) ^ (cz & 0xFFFFFFFFL));
                ByteArrayOutputStream z = new ByteArrayOutputStream();
                try (DeflaterOutputStream d = new DeflaterOutputStream(z, new Deflater(6))) { d.write(raw); }
                byte[] zb = z.toByteArray();
                int len = zb.length + 1;
                int total = len + 4;
                int sectors = (total + 4095) / 4096;
                DataOutputStream o = new DataOutputStream(body);
                o.writeInt(len);
                o.writeByte(2);
                o.write(zb);
                for (int i = total; i < sectors * 4096; i++) o.writeByte(0);
                loc[Math.floorMod(cx, 32) + Math.floorMod(cz, 32) * 32] = (sector << 8) | sectors;
                sector += sectors;
            }
            try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
                raf.setLength(0);
                ByteArrayOutputStream head = new ByteArrayOutputStream();
                DataOutputStream ho = new DataOutputStream(head);
                for (int v : loc) ho.writeInt(v);
                for (int i = 0; i < 1024; i++) ho.writeInt(0);
                raf.write(head.toByteArray());
                raf.write(body.toByteArray());
            }
        }
    }

    public static void write(File world, Fn fn, int cx0, int cz0, int cx1, int cz1) throws IOException {
        Map<Long, byte[]> chunks = new HashMap<>();
        for (int cz = cz0; cz <= cz1; cz++) for (int cx = cx0; cx <= cx1; cx++) chunks.put(((long) cx << 32) ^ (cz & 0xFFFFFFFFL), chunk(cx, cz, fn, "minecraft:full"));
        writeRegions(world, chunks);
    }
}
