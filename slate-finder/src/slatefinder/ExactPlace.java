package slatefinder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A piece of the real world: every column of an area, read from the game's own world files. For each column it keeps what
 * the ground is made of, how high it is, and the exact blocks standing on it (trees, buildings, plants). It is saved in a
 * compact file so the big world files can be deleted, and it plugs into {@link Terrain} so the 3D views draw it.
 */
final class ExactPlace implements Terrain.ExactSource {
    static final int VERSION = 1;
    static final int SEA_BLOCK = 62;          // the highest block of the sea (the water surface is at 63)

    final long seed;
    final int cx0, cz0, cw, cd;               // the area, in chunks
    /** Names of every block and biome used here; index 0 of the blocks is air. */
    final List<String> blocks = new ArrayList<>();
    final List<String> biomes = new ArrayList<>();
    /** The block stacks that stand on columns: palette indexes, lowest first (0 = air). */
    final List<short[]> stacks = new ArrayList<>();
    private final Map<String, Integer> stackIndex = new HashMap<>();

    /** One column as stored. */
    static final class Col {
        short ter;
        short top, sub, under, deco = -1;
        short biome;
        byte flags;
        int stack = -1;
        short gy;
    }

    final Col[][] cols;                       // per chunk, 256 columns (null: chunk not available)
    private Terrain.Chunk[] built;

    ExactPlace(long seed, int cx0, int cz0, int cw, int cd) {
        this.seed = seed; this.cx0 = cx0; this.cz0 = cz0; this.cw = cw; this.cd = cd;
        this.cols = new Col[cw * cd][];
        blocks.add("minecraft:air");
    }

    int x0() { return cx0 * 16; }
    int z0() { return cz0 * 16; }
    int x1() { return (cx0 + cw) * 16 - 1; }
    int z1() { return (cz0 + cd) * 16 - 1; }
    boolean contains(int x, int z) { return x >= x0() && x <= x1() && z >= z0() && z <= z1(); }

    /** Whether the area around (x, z), {@code r} blocks each way, is inside this place. */
    boolean covers(int x, int z, int r) { return contains(x - r, z - r) && contains(x + r, z + r); }

    int chunksAvailable() { int n = 0; for (Col[] c : cols) if (c != null) n++; return n; }

    // ------------------------------------------------------------------ reading the game's world files

    interface Progress { void update(double fraction, String text); }

    private final Map<String, Integer> blockIdx = new HashMap<>();
    private final Map<String, Integer> biomeIdx = new HashMap<>();
    private final List<ExactBlocks.Info> infos = new ArrayList<>();

    private int blockIndex(String name) {
        Integer i = blockIdx.get(name);
        if (i != null) return i;
        int n = blocks.size();
        blocks.add(name);
        blockIdx.put(name, n);
        while (infos.size() <= n) infos.add(null);
        infos.set(n, ExactBlocks.of(name));
        return n;
    }

    private int biomeIndex(String name) {
        Integer i = biomeIdx.get(name);
        if (i != null) return i;
        int n = biomes.size();
        biomes.add(name);
        biomeIdx.put(name, n);
        return n;
    }

    private void initPalette() {
        blockIdx.put("minecraft:air", 0);
        infos.clear();
        infos.add(ExactBlocks.of("minecraft:air"));
    }

    /** Reads the chunks cx0..cx0+cw-1, cz0..cz0+cd-1 from a world folder (the finished server world). Chunks that are missing are left out. */
    static ExactPlace read(File world, long seed, int cx0, int cz0, int cw, int cd, Progress p) throws IOException {
        File dir = Anvil.regionDir(world);
        if (dir == null) throw new IOException("no region folder in " + world);
        ExactPlace pl = new ExactPlace(seed, cx0, cz0, cw, cd);
        pl.initPalette();
        Map<Long, Anvil.Region> cache = new HashMap<>();
        int done = 0, total = cw * cd;
        long lastRegion = Long.MIN_VALUE;
        for (int cz = cz0; cz < cz0 + cd; cz++) for (int cx = cx0; cx < cx0 + cw; cx++) {
            int rx = Math.floorDiv(cx, 32), rz = Math.floorDiv(cz, 32);
            long key = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
            Anvil.Region r = cache.get(key);
            if (r == null) {
                if (cache.size() >= 2) cache.clear();      // a region is tens of megabytes: keep only the ones in use
                r = new Anvil.Region(dir, rx, rz);
                cache.put(key, r);
            }
            Map<String, Object> ch = r.chunk(Math.floorMod(cx, 32), Math.floorMod(cz, 32));
            if (ch != null) {
                String st = Nbt.str(ch.get("Status"));
                if (st == null || st.endsWith("full")) pl.cols[(cz - cz0) * cw + (cx - cx0)] = pl.columns(ch);
            }
            done++;
            if (p != null && (done & 31) == 0) p.update(done / (double) total, "Reading the world…  " + done + " of " + total + " chunks");
        }
        return pl;
    }

    private static final int MIN_Y = -64, MAX_Y = 319, SECTIONS = 24;

    private short[] decodeBlocks(Map<String, Object> sec) throws IOException {
        Map<String, Object> bs = Nbt.map(sec.get("block_states"));
        if (bs == null) return null;
        List<Object> pal = Nbt.list(bs.get("palette"));
        if (pal == null || pal.isEmpty()) return null;
        int n = pal.size();
        int[] local = new int[n];
        for (int i = 0; i < n; i++) {
            Map<String, Object> e = Nbt.map(pal.get(i));
            String name = e == null ? null : Nbt.str(e.get("Name"));
            local[i] = blockIndex(name == null ? "minecraft:air" : name);
        }
        short[] out = new short[4096];
        Object d = bs.get("data");
        if (n == 1 || !(d instanceof long[])) {
            if (local[0] != 0) Arrays.fill(out, (short) local[0]);
            return out;
        }
        long[] data = (long[]) d;
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(n - 1));
        int per = 64 / bits;
        if (data.length < (4096 + per - 1) / per) throw new IOException("a block section is shorter than its palette needs");
        long mask = (1L << bits) - 1;
        for (int i = 0; i < 4096; i++) {
            int v = (int) ((data[i / per] >>> ((i % per) * bits)) & mask);
            out[i] = (short) local[v < n ? v : 0];
        }
        return out;
    }

    private int[] decodeBiomes(Map<String, Object> sec) {
        Map<String, Object> bm = Nbt.map(sec.get("biomes"));
        if (bm == null) return null;
        List<Object> pal = Nbt.list(bm.get("palette"));
        if (pal == null || pal.isEmpty()) return null;
        int n = pal.size();
        int[] local = new int[n];
        for (int i = 0; i < n; i++) { String s = Nbt.str(pal.get(i)); local[i] = biomeIndex(s == null ? "minecraft:plains" : s); }
        int[] out = new int[64];
        Object d = bm.get("data");
        if (n == 1 || !(d instanceof long[])) { Arrays.fill(out, local[0]); return out; }
        long[] data = (long[]) d;
        int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(n - 1));
        int per = 64 / bits;
        if (data.length < (64 + per - 1) / per) { Arrays.fill(out, local[0]); return out; }
        long mask = (1L << bits) - 1;
        for (int i = 0; i < 64; i++) {
            int v = (int) ((data[i / per] >>> ((i % per) * bits)) & mask);
            out[i] = local[v < n ? v : 0];
        }
        return out;
    }

    /** The 256 columns of one chunk. */
    private Col[] columns(Map<String, Object> chunk) throws IOException {
        short[][] secs = new short[SECTIONS][];
        int[][] bios = new int[SECTIONS][];
        List<Object> list = Nbt.list(chunk.get("sections"));
        if (list != null) for (Object o : list) {
            Map<String, Object> sec = Nbt.map(o);
            if (sec == null) continue;
            int y = Nbt.num(sec.get("Y"), Integer.MIN_VALUE);
            if (y < -4 || y > 19) continue;
            secs[y + 4] = decodeBlocks(sec);
            bios[y + 4] = decodeBiomes(sec);
        }
        int defaultBiome = biomeIndex("minecraft:plains");
        Col[] out = new Col[256];
        byte[] stackBuf = new byte[SECTIONS * 16];
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            Col c = new Col();
            out[lz * 16 + lx] = c;
            // the highest block of natural ground
            int yg = MIN_Y - 1;
            find:
            for (int s = SECTIONS - 1; s >= 0; s--) {
                short[] sc = secs[s];
                if (sc == null) continue;
                for (int yy = 15; yy >= 0; yy--) {
                    int idx = sc[(yy << 8) | (lz << 4) | lx];
                    if (idx != 0 && infos.get(idx).kind == ExactBlocks.GROUND) { yg = (s - 4) * 16 + yy; break find; }
                }
            }
            boolean none = yg < MIN_Y;
            if (none) yg = MIN_Y;
            c.ter = (short) yg;                                           // terrain height: the top face of the ground, one below the game's y
            c.top = (short) blockAt(secs, lx, yg, lz);
            int sub = blockAt(secs, lx, yg - 2, lz), und = blockAt(secs, lx, yg - 5, lz);
            c.sub = (short) (ground(sub) ? sub : c.top);
            c.under = (short) (ground(und) ? und : c.sub);
            int bs = yg >> 4;
            int[] bp = bs + 4 >= 0 && bs + 4 < SECTIONS ? bios[bs + 4] : null;
            c.biome = (short) (bp == null ? defaultBiome : bp[(((yg & 15) >> 2) << 4) | ((lz >> 2) << 2) | (lx >> 2)]);
            // the sea: water at the highest sea block over a floor below it
            if (yg < SEA_BLOCK) {
                int w = blockAt(secs, lx, SEA_BLOCK, lz);
                if (w != 0 && infos.get(w).kind == ExactBlocks.WATER) c.flags |= Terrain.F_WATER;
            }
            // what stands on it: blocks above the ground, with the sea left out (it is drawn as water)
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int y = yg + 1; y <= MAX_Y; y++) {
                int idx = blockAt(secs, lx, y, lz);
                byte id = 0;
                if (idx != 0) {
                    ExactBlocks.Info in = infos.get(idx);
                    if (in.kind == ExactBlocks.WATER) id = y <= SEA_BLOCK ? 0 : Blocks3D.WATER;
                    else if (in.kind != ExactBlocks.AIR) id = in.id;
                }
                stackBuf[y - yg - 1 < stackBuf.length ? y - yg - 1 : stackBuf.length - 1] = id;
                if (id != 0) { if (y < lo) lo = y; if (y > hi) hi = y; }
            }
            if (hi >= lo && lo != Integer.MAX_VALUE) {
                // a plant or two on the ground is colour on the ground, like grass in the game's own map
                boolean allPlants = hi - lo <= 1;
                for (int y = lo; y <= hi && allPlants; y++) { byte id = stackBuf[y - yg - 1]; if (id != 0 && !Blocks3D.plant(id)) allPlants = false; }
                if (allPlants && lo == yg + 1) {
                    c.deco = (short) blockAt(secs, lx, lo, lz);
                } else {
                    short[] st = new short[hi - lo + 1];
                    for (int y = lo; y <= hi; y++) st[y - lo] = (short) blockAtStack(secs, lx, y, lz);
                    c.stack = stackIndex(st);
                    c.gy = (short) (lo - 1);
                }
            }
            if (none) { c.ter = (short) MIN_Y; }
        }
        return out;
    }

    private boolean ground(int idx) { return idx != 0 && infos.get(idx).kind == ExactBlocks.GROUND; }

    private static int blockAt(short[][] secs, int lx, int y, int lz) {
        if (y < MIN_Y || y > MAX_Y) return 0;
        short[] sc = secs[(y >> 4) + 4];
        return sc == null ? 0 : sc[((y & 15) << 8) | (lz << 4) | lx];
    }

    /** Like blockAt, but sea water at or under the sea is air (it is drawn as the sea), and water above it stays. */
    private int blockAtStack(short[][] secs, int lx, int y, int lz) {
        int idx = blockAt(secs, lx, y, lz);
        if (idx != 0 && infos.get(idx).kind == ExactBlocks.WATER && y <= SEA_BLOCK) return 0;
        if (idx != 0 && infos.get(idx).kind == ExactBlocks.AIR) return 0;
        return idx;
    }

    private int stackIndex(short[] st) {
        String key = Arrays.toString(st);
        Integer i = stackIndex.get(key);
        if (i != null) return i;
        int n = stacks.size();
        stacks.add(st);
        stackIndex.put(key, n);
        return n;
    }

    // ------------------------------------------------------------------ saving and loading

    private static final int MAGIC = 0x534C5831;      // "SLX1"

    void save(File f) throws IOException {
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".part");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp), 1 << 16) {{ def.setLevel(Deflater.BEST_COMPRESSION); }}, 1 << 16))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeLong(seed);
            out.writeInt(cx0); out.writeInt(cz0); out.writeInt(cw); out.writeInt(cd);
            out.writeInt(blocks.size());
            for (String s : blocks) out.writeUTF(s);
            out.writeInt(biomes.size());
            for (String s : biomes) out.writeUTF(s);
            out.writeInt(stacks.size());
            for (short[] st : stacks) { out.writeShort(st.length); for (short v : st) out.writeShort(v); }
            for (Col[] ch : cols) {
                out.writeBoolean(ch != null);
                if (ch == null) continue;
                for (Col c : ch) {
                    out.writeShort(c.ter); out.writeShort(c.top); out.writeShort(c.sub); out.writeShort(c.under); out.writeShort(c.deco);
                    out.writeShort(c.biome); out.writeByte(c.flags); out.writeInt(c.stack); out.writeShort(c.gy);
                }
            }
        }
        java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    static ExactPlace load(File f) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f), 1 << 16), 1 << 16))) {
            if (in.readInt() != MAGIC) throw new IOException("not a Slate Finder place file");
            int ver = in.readInt();
            if (ver != VERSION) throw new IOException("this place file is from another version");
            long seed = in.readLong();
            int cx0 = in.readInt(), cz0 = in.readInt(), cw = in.readInt(), cd = in.readInt();
            if (cw <= 0 || cd <= 0 || (long) cw * cd > 200_000) throw new IOException("bad place size");
            ExactPlace pl = new ExactPlace(seed, cx0, cz0, cw, cd);
            pl.blocks.clear();
            int nb = in.readInt();
            for (int i = 0; i < nb; i++) pl.blocks.add(in.readUTF());
            int nbi = in.readInt();
            for (int i = 0; i < nbi; i++) pl.biomes.add(in.readUTF());
            int ns = in.readInt();
            for (int i = 0; i < ns; i++) {
                int len = in.readUnsignedShort();
                short[] st = new short[len];
                for (int k = 0; k < len; k++) st[k] = in.readShort();
                pl.stacks.add(st);
                pl.stackIndex.put(Arrays.toString(st), i);
            }
            for (int ci = 0; ci < pl.cols.length; ci++) {
                if (!in.readBoolean()) continue;
                Col[] ch = new Col[256];
                for (int k = 0; k < 256; k++) {
                    Col c = new Col();
                    c.ter = in.readShort(); c.top = in.readShort(); c.sub = in.readShort(); c.under = in.readShort(); c.deco = in.readShort();
                    c.biome = in.readShort(); c.flags = in.readByte(); c.stack = in.readInt(); c.gy = in.readShort();
                    ch[k] = c;
                }
                pl.cols[ci] = ch;
            }
            return pl;
        }
    }

    // ------------------------------------------------------------------ plugging into the 3D world

    private static final Map<String, Integer> STAMPS = new ConcurrentHashMap<>();

    /** Turns the stored columns into the terrain's own chunks, with this session's block numbers and biome numbers. */
    void bind(Terrain world) {
        byte[] id = new byte[blocks.size()];
        for (int i = 0; i < id.length; i++) id[i] = i == 0 ? 0 : ExactBlocks.of(blocks.get(i)).id;
        int[] biomeMap = new int[biomes.size()];
        for (int i = 0; i < biomeMap.length; i++) {
            String b = biomes.get(i);
            biomeMap[i] = world.styleIndex(b.startsWith("minecraft:") ? b.substring(10) : b);
        }
        int[] stampOf = new int[stacks.size()];
        for (int i = 0; i < stampOf.length; i++) {
            short[] st = stacks.get(i);
            byte[] v = new byte[st.length];
            for (int k = 0; k < st.length; k++) v[k] = id[st[k]];
            String key = Arrays.toString(v);
            Integer have = STAMPS.get(key);
            if (have == null) {
                Stamp s = new Stamp(1, 1, v.length, 0, 0);
                System.arraycopy(v, 0, s.v, 0, v.length);
                have = s.id;
                STAMPS.put(key, have);
            }
            stampOf[i] = have;
        }
        built = new Terrain.Chunk[cols.length];
        for (int ci = 0; ci < cols.length; ci++) {
            Col[] ch = cols[ci];
            if (ch == null) continue;
            Terrain.Chunk t = new Terrain.Chunk();
            t.cx = cx0 + ci % cw; t.cz = cz0 + ci / cw;
            for (int k = 0; k < 256; k++) {
                Col c = ch[k];
                t.ter[k] = c.ter;
                t.top[k] = id[c.top]; t.sub[k] = id[c.sub]; t.under[k] = id[c.under];
                t.biome[k] = (byte) biomeMap[c.biome];
                t.flags[k] = c.flags;
                t.stamp[k] = -1;
                t.faceTop[k] = t.top[k];
                int h = c.ter;
                if (c.stack >= 0) {
                    int sid = stampOf[c.stack];
                    Stamp s = Stamp.get(sid);
                    t.stamp[k] = sid;
                    t.gy[k] = c.gy;
                    int top = s.top(0, 0);
                    h = c.gy + top + 1;
                    t.faceTop[k] = s.at(0, top, 0);
                } else if (c.deco >= 0) {
                    t.faceTop[k] = id[c.deco];
                }
                t.h[k] = (short) h;
            }
            built[ci] = t;
        }
    }

    @Override public Terrain.Chunk chunk(int cx, int cz) {
        if (built == null) return null;
        int x = cx - cx0, z = cz - cz0;
        if (x < 0 || z < 0 || x >= cw || z >= cd) return null;
        return built[z * cw + x];
    }
}
