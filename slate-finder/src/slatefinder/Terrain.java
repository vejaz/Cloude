package slatefinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import slatefinder.Stamp.Tree;

/**
 * The world the 3D views draw. The biome of every place comes from the real biome map; the villages come from the real
 * position list. Everything finer than the map's resolution (the exact hills, where each tree stands, the shape of a
 * house) is generated to look right, so it is an illustration of the place, not a copy of the blocks in the game.
 */
final class Terrain {
    static final int SEA = 62;

    /** Where the biomes come from. */
    interface Biomes {
        int at(int x, int z);
        String[] names();
        int cell();
    }

    /** A structure site: a village at its listed position, or something placed by a scene. */
    static final class Site {
        final int x, z;
        final String kind;
        Site(int x, int z, String kind) { this.x = x; this.z = z; this.kind = kind; }
    }

    /** The real biome map, as a source of biomes. */
    static Biomes ofGrid(BiomeGrid g) {
        final String[] ids = PlaceSearch.idsOf(g);
        return new Biomes() {
            public int at(int x, int z) { int b = g.at(g.col(x), g.col(z)); return b < 0 ? 0 : b; }
            public String[] names() { return ids; }
            public int cell() { return g.step; }
        };
    }

    /** One biome everywhere (for the pictures in the book), or a function of position. */
    static Biomes scene(String[] names, java.util.function.BiFunction<Integer, Integer, Integer> f) { return scene(names, f, 64); }

    static Biomes scene(String[] names, java.util.function.BiFunction<Integer, Integer, Integer> f, int cellSize) {
        return new Biomes() {
            public int at(int x, int z) { return f.apply(x, z); }
            public String[] names() { return names; }
            public int cell() { return cellSize; }
        };
    }

    // ------------------------------------------------------------------ biome styles

    static final class Style {
        final String id;
        float base = 66, amp = 3;
        boolean ridged, mesa, snowy, ice, ocean;
        byte top = Blocks3D.GRASS, sub = Blocks3D.DIRT, under = Blocks3D.STONE, floor = Blocks3D.SAND, patch = 0;
        float patchP;
        Tree[] trees = {};
        float density;
        Tree[] plants = {};
        float plantP;
        int grass = 0xFF91BD59, foliage = 0xFF77AB2F, water = 0xFF3F76E4;
        Models.Style village = Models.Style.PLAINS;

        Style(String id) { this.id = id; }
    }

    private static final Map<String, Style> STYLES = new HashMap<>();

    private static Style st(String id, double base, double amp) {
        Style s = new Style(id);
        s.base = (float) base; s.amp = (float) amp;
        STYLES.put(id, s);
        return s;
    }

    private static Style tint(Style s, int grass, int foliage) { s.grass = 0xFF000000 | grass; s.foliage = 0xFF000000 | foliage; return s; }
    private static Style water(Style s, int w) { s.water = 0xFF000000 | w; return s; }
    private static Style trees(Style s, double d, Tree... t) { s.trees = t; s.density = (float) d; return s; }
    private static Style plants(Style s, double p, Tree... t) { s.plants = t; s.plantP = (float) p; return s; }
    private static Style surf(Style s, byte top, byte sub, byte under) { s.top = top; s.sub = sub; s.under = under; return s; }

    static {
        plants(trees(tint(st("plains", 66, 3), 0x91BD59, 0x77AB2F), 0.012, Tree.OAK), 0.2, Tree.TALL_GRASS, Tree.TALL_GRASS, Tree.TALL_GRASS, Tree.FLOWER_R, Tree.FLOWER_Y);
        plants(trees(tint(st("sunflower_plains", 66, 3), 0x91BD59, 0x77AB2F), 0.01, Tree.OAK), 0.22, Tree.TALL_GRASS, Tree.FLOWER_Y, Tree.FLOWER_Y);
        { Style s = tint(st("snowy_plains", 66, 3), 0x80B497, 0x60A17B); surf(s, Blocks3D.SNOW, Blocks3D.DIRT, Blocks3D.STONE); s.snowy = true; s.floor = Blocks3D.GRAVEL; trees(s, 0.012, Tree.SPRUCE); s.village = Models.Style.SNOWY; }
        { Style s = tint(st("ice_spikes", 66, 2), 0x80B497, 0x60A17B); surf(s, Blocks3D.SNOW_BLOCK, Blocks3D.SNOW_BLOCK, Blocks3D.STONE); s.snowy = true; trees(s, 0.07, Tree.ICE_SPIKE); }
        { Style s = tint(st("desert", 66, 6), 0xBFB755, 0xAEA42A); surf(s, Blocks3D.SAND, Blocks3D.SAND, Blocks3D.SANDSTONE); trees(s, 0.012, Tree.CACTUS); plants(s, 0.012, Tree.DEAD_BUSH); s.village = Models.Style.DESERT; }
        { Style s = tint(st("swamp", 62.6, 1.3), 0x6A7039, 0x6A7039); water(s, 0x617B64); trees(s, 0.1, Tree.OAK); plants(s, 0.25, Tree.TALL_GRASS); }
        { Style s = tint(st("mangrove_swamp", 62.2, 1.1), 0x8DB127, 0x8DB127); water(s, 0x3A7A6A); surf(s, Blocks3D.MUD, Blocks3D.MUD, Blocks3D.STONE); trees(s, 0.22, Tree.MANGROVE); }
        plants(trees(tint(st("forest", 68, 4), 0x79C05A, 0x59AE30), 0.34, Tree.OAK, Tree.OAK, Tree.OAK, Tree.BIRCH), 0.12, Tree.TALL_GRASS, Tree.FLOWER_R);
        plants(trees(tint(st("flower_forest", 68, 3), 0x79C05A, 0x59AE30), 0.2, Tree.OAK, Tree.FLOWER_TREE, Tree.BIRCH), 0.35, Tree.FLOWER_R, Tree.FLOWER_Y, Tree.FLOWER_P, Tree.TALL_GRASS);
        plants(trees(tint(st("birch_forest", 68, 4), 0x88BB67, 0x6BA941), 0.34, Tree.BIRCH), 0.12, Tree.TALL_GRASS);
        plants(trees(tint(st("old_growth_birch_forest", 68, 4), 0x88BB67, 0x6BA941), 0.4, Tree.BIRCH, Tree.JUNGLE), 0.12, Tree.TALL_GRASS);
        { Style s = tint(st("dark_forest", 68, 3), 0x507A32, 0x3D6B1F); trees(s, 0.55, Tree.DARK_OAK, Tree.DARK_OAK, Tree.RED_MUSHROOM, Tree.BROWN_MUSHROOM); plants(s, 0.06, Tree.TALL_GRASS); }
        { Style s = tint(st("pale_garden", 68, 3), 0x778272, 0x878D76); trees(s, 0.3, Tree.PALE); plants(s, 0.1, Tree.TALL_GRASS); }
        { Style s = tint(st("taiga", 70, 5), 0x86B783, 0x68A464); trees(s, 0.3, Tree.SPRUCE); plants(s, 0.16, Tree.TALL_GRASS, Tree.BERRY_BUSH); s.village = Models.Style.TAIGA; }
        { Style s = tint(st("snowy_taiga", 70, 5), 0x80B497, 0x60A17B); surf(s, Blocks3D.SNOW, Blocks3D.DIRT, Blocks3D.STONE); s.snowy = true; trees(s, 0.3, Tree.SPRUCE); s.village = Models.Style.TAIGA; }
        { Style s = tint(st("old_growth_pine_taiga", 72, 5), 0x86B783, 0x68A464); surf(s, Blocks3D.PODZOL, Blocks3D.DIRT, Blocks3D.STONE); trees(s, 0.4, Tree.SPRUCE, Tree.SPRUCE, Tree.BOULDER); plants(s, 0.1, Tree.TALL_GRASS); }
        { Style s = tint(st("old_growth_spruce_taiga", 72, 5), 0x86B783, 0x68A464); surf(s, Blocks3D.PODZOL, Blocks3D.DIRT, Blocks3D.STONE); trees(s, 0.4, Tree.SPRUCE); plants(s, 0.1, Tree.TALL_GRASS); }
        { Style s = tint(st("grove", 100, 12), 0x80B497, 0x60A17B); surf(s, Blocks3D.SNOW, Blocks3D.DIRT, Blocks3D.STONE); s.snowy = true; s.ridged = true; trees(s, 0.2, Tree.SPRUCE); }
        { Style s = tint(st("jungle", 71, 6), 0x59C93C, 0x30BB0B); trees(s, 0.5, Tree.JUNGLE, Tree.JUNGLE, Tree.BUSH); plants(s, 0.3, Tree.TALL_GRASS, Tree.TALL_GRASS, Tree.FLOWER_R); }
        { Style s = tint(st("sparse_jungle", 70, 5), 0x64C73F, 0x3EB80F); trees(s, 0.16, Tree.JUNGLE, Tree.BUSH); plants(s, 0.25, Tree.TALL_GRASS); }
        { Style s = tint(st("bamboo_jungle", 71, 6), 0x59C93C, 0x30BB0B); surf(s, Blocks3D.PODZOL, Blocks3D.DIRT, Blocks3D.STONE); trees(s, 0.55, Tree.BAMBOO, Tree.BAMBOO, Tree.BAMBOO, Tree.JUNGLE); plants(s, 0.2, Tree.TALL_GRASS); }
        { Style s = tint(st("savanna", 68, 3), 0xBFB755, 0xAEA42A); trees(s, 0.05, Tree.ACACIA); plants(s, 0.3, Tree.TALL_GRASS); s.village = Models.Style.SAVANNA; }
        { Style s = tint(st("savanna_plateau", 92, 2), 0xBFB755, 0xAEA42A); trees(s, 0.05, Tree.ACACIA); plants(s, 0.3, Tree.TALL_GRASS); }
        { Style s = tint(st("windswept_savanna", 82, 18), 0xBFB755, 0xAEA42A); s.ridged = true; trees(s, 0.04, Tree.ACACIA); plants(s, 0.2, Tree.TALL_GRASS); }
        { Style s = tint(st("badlands", 78, 9), 0x90814D, 0x9E814D); surf(s, Blocks3D.RED_SAND, Blocks3D.TERRA, Blocks3D.TERRA); s.mesa = true; plants(s, 0.02, Tree.DEAD_BUSH); trees(s, 0.006, Tree.CACTUS); }
        { Style s = tint(st("wooded_badlands", 80, 9), 0x90814D, 0x9E814D); surf(s, Blocks3D.GRASS, Blocks3D.DIRT, Blocks3D.TERRA); s.mesa = true; trees(s, 0.14, Tree.OAK); }
        { Style s = tint(st("eroded_badlands", 88, 26), 0x90814D, 0x9E814D); surf(s, Blocks3D.RED_SAND, Blocks3D.TERRA, Blocks3D.TERRA); s.mesa = true; s.ridged = true; plants(s, 0.01, Tree.DEAD_BUSH); }
        { Style s = tint(st("windswept_hills", 95, 22), 0x8AB689, 0x6DA36B); s.ridged = true; s.patch = Blocks3D.STONE; s.patchP = 0.45f; trees(s, 0.04, Tree.OAK, Tree.SPRUCE); plants(s, 0.06, Tree.TALL_GRASS); }
        { Style s = tint(st("windswept_gravelly_hills", 98, 22), 0x8AB689, 0x6DA36B); s.ridged = true; surf(s, Blocks3D.GRAVEL, Blocks3D.GRAVEL, Blocks3D.STONE); s.patch = Blocks3D.STONE; s.patchP = 0.35f; }
        { Style s = tint(st("windswept_forest", 95, 22), 0x8AB689, 0x6DA36B); s.ridged = true; s.patch = Blocks3D.STONE; s.patchP = 0.4f; trees(s, 0.16, Tree.SPRUCE, Tree.SPRUCE, Tree.OAK); }
        { Style s = tint(st("snowy_slopes", 110, 18), 0x80B497, 0x60A17B); surf(s, Blocks3D.SNOW_BLOCK, Blocks3D.SNOW_BLOCK, Blocks3D.STONE); s.snowy = true; s.ridged = true; s.patch = Blocks3D.STONE; s.patchP = 0.25f; }
        { Style s = tint(st("jagged_peaks", 135, 38), 0x80B497, 0x60A17B); surf(s, Blocks3D.SNOW_BLOCK, Blocks3D.SNOW_BLOCK, Blocks3D.STONE); s.snowy = true; s.ridged = true; s.patch = Blocks3D.STONE; s.patchP = 0.3f; }
        { Style s = tint(st("frozen_peaks", 130, 34), 0x80B497, 0x60A17B); surf(s, Blocks3D.SNOW_BLOCK, Blocks3D.PACKED_ICE, Blocks3D.PACKED_ICE); s.snowy = true; s.ridged = true; s.patch = Blocks3D.PACKED_ICE; s.patchP = 0.5f; }
        { Style s = tint(st("stony_peaks", 125, 30), 0x8AB689, 0x6DA36B); surf(s, Blocks3D.STONE, Blocks3D.STONE, Blocks3D.STONE); s.ridged = true; s.patch = Blocks3D.GRAVEL; s.patchP = 0.2f; }
        { Style s = tint(st("meadow", 98, 7), 0x83BB6D, 0x63A948); water(s, 0x0E4ECF); trees(s, 0.004, Tree.OAK); plants(s, 0.35, Tree.FLOWER_R, Tree.FLOWER_Y, Tree.FLOWER_P, Tree.TALL_GRASS, Tree.TALL_GRASS); }
        { Style s = tint(st("cherry_grove", 96, 7), 0xB6DB61, 0xB6DB61); water(s, 0x5DB7EF); trees(s, 0.16, Tree.CHERRY); plants(s, 0.25, Tree.FLOWER_P, Tree.TALL_GRASS); }
        { Style s = tint(st("river", 56, 1.3), 0x8EB971, 0x71A74D); s.floor = Blocks3D.SAND; surf(s, Blocks3D.SAND, Blocks3D.SAND, Blocks3D.STONE); }
        { Style s = tint(st("frozen_river", 56, 1.3), 0x80B497, 0x60A17B); water(s, 0x3938C9); s.ice = true; s.snowy = true; }
        { Style s = tint(st("beach", 63.4, 1.1), 0x91BD59, 0x77AB2F); surf(s, Blocks3D.SAND, Blocks3D.SAND, Blocks3D.SANDSTONE); }
        { Style s = tint(st("snowy_beach", 63.4, 1.1), 0x80B497, 0x60A17B); surf(s, Blocks3D.SAND, Blocks3D.SAND, Blocks3D.SANDSTONE); s.snowy = true; }
        { Style s = tint(st("stony_shore", 67, 6), 0x8AB689, 0x6DA36B); surf(s, Blocks3D.STONE, Blocks3D.STONE, Blocks3D.STONE); s.patch = Blocks3D.GRAVEL; s.patchP = 0.3f; }
        { Style s = st("ocean", 45, 3); s.ocean = true; s.floor = Blocks3D.GRAVEL; }
        { Style s = st("deep_ocean", 26, 4); s.ocean = true; s.floor = Blocks3D.GRAVEL; }
        { Style s = water(st("warm_ocean", 45, 2.5), 0x43D5EE); s.ocean = true; s.floor = Blocks3D.SAND; plants(s, 0.12, Tree.TALL_GRASS); }
        { Style s = water(st("lukewarm_ocean", 45, 2.5), 0x45ADF2); s.ocean = true; s.floor = Blocks3D.SAND; }
        { Style s = water(st("deep_lukewarm_ocean", 28, 3), 0x45ADF2); s.ocean = true; s.floor = Blocks3D.SAND; }
        { Style s = water(st("cold_ocean", 45, 3), 0x3D57D6); s.ocean = true; s.floor = Blocks3D.GRAVEL; }
        { Style s = water(st("deep_cold_ocean", 28, 3), 0x3D57D6); s.ocean = true; s.floor = Blocks3D.GRAVEL; }
        { Style s = water(st("frozen_ocean", 45, 3), 0x3938C9); s.ocean = true; s.ice = true; s.snowy = true; s.floor = Blocks3D.GRAVEL; }
        { Style s = water(st("deep_frozen_ocean", 28, 3), 0x3938C9); s.ocean = true; s.ice = true; s.snowy = true; s.floor = Blocks3D.GRAVEL; }
        { Style s = tint(st("mushroom_fields", 68, 4), 0x55FF55, 0x2BBB0F); surf(s, Blocks3D.MYCELIUM, Blocks3D.DIRT, Blocks3D.STONE); trees(s, 0.07, Tree.RED_MUSHROOM, Tree.BROWN_MUSHROOM); }
        { Style s = tint(st("dripstone_caves", 67, 4), 0x91BD59, 0x77AB2F); surf(s, Blocks3D.STONE, Blocks3D.STONE, Blocks3D.STONE); }
        { Style s = tint(st("lush_caves", 67, 4), 0x91BD59, 0x77AB2F); surf(s, Blocks3D.MOSS, Blocks3D.DIRT, Blocks3D.STONE); }
        { Style s = tint(st("deep_dark", 67, 3), 0x91BD59, 0x77AB2F); surf(s, Blocks3D.DEEPSLATE, Blocks3D.DEEPSLATE, Blocks3D.DEEPSLATE); }
    }

    /** A copy of a style, so one scene can bend a biome (a shallower ocean) without changing it for every other scene. */
    static Style copy(Style o) {
        Style s = new Style(o.id);
        s.base = o.base; s.amp = o.amp; s.ridged = o.ridged; s.mesa = o.mesa; s.snowy = o.snowy; s.ice = o.ice; s.ocean = o.ocean;
        s.top = o.top; s.sub = o.sub; s.under = o.under; s.floor = o.floor; s.patch = o.patch; s.patchP = o.patchP;
        s.trees = o.trees; s.density = o.density; s.plants = o.plants; s.plantP = o.plantP;
        s.grass = o.grass; s.foliage = o.foliage; s.water = o.water; s.village = o.village;
        return s;
    }

    static Style style(String id) {
        Style s = STYLES.get(id);
        return s != null ? s : STYLES.get("plains");
    }

    // ------------------------------------------------------------------ noise

    private static double hash(long x, long z, long s) {
        long v = x * 374761393L + z * 668265263L + s * 2147483647L;
        v = (v ^ (v >>> 13)) * 1274126177L;
        v ^= v >>> 16;
        return (v & 0xFFFFFF) / (double) 0x1000000;
    }

    private static double vn(double x, double z, int s) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = hash(xi, zi, s), b = hash(xi + 1, zi, s), c = hash(xi, zi + 1, s), d = hash(xi + 1, zi + 1, s);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    private static double fbm(double x, double z, int oct, int s) {
        double sum = 0, amp = 0.5, tot = 0;
        for (int i = 0; i < oct; i++) { sum += amp * vn(x, z, s + i * 31); tot += amp; x *= 2.03; z *= 2.03; amp *= 0.5; }
        return sum / tot;
    }

    // ------------------------------------------------------------------ the world

    final Biomes src;
    final String[] names;
    final Style[] styles;
    final int seed;
    private final ConcurrentHashMap<Long, Chunk> chunks = new ConcurrentHashMap<>();
    /** Things a scene places by hand: animals, a single building. {stamp, x, z} */
    final List<int[]> extras = new ArrayList<>();
    private final Map<Long, List<Site>> siteBuckets = new HashMap<>();
    private final Map<Site, Layout> layouts = new ConcurrentHashMap<>();
    int cell;

    Terrain(Biomes src, int seed) {
        this.src = src;
        this.seed = seed;
        this.names = src.names();
        this.styles = new Style[names.length];
        for (int i = 0; i < names.length; i++) styles[i] = style(names[i]);
        this.cell = Math.max(16, src.cell());
    }

    void addSite(Site s) { siteBuckets.computeIfAbsent(bucket(s.x >> 6, s.z >> 6), k -> new ArrayList<>()).add(s); }

    void addExtra(Stamp st, int x, int z) { extras.add(new int[]{st.id, x, z, Math.max(SEA, (int) Math.round(rawHeight(x, z)))}); }

    private static long bucket(int bx, int bz) { return ((long) bx << 32) ^ (bz & 0xFFFFFFFFL); }

    private List<Site> sitesNear(int x, int z, int r) {
        List<Site> out = null;
        for (int bx = (x - r) >> 6; bx <= (x + r) >> 6; bx++)
            for (int bz = (z - r) >> 6; bz <= (z + r) >> 6; bz++) {
                List<Site> l = siteBuckets.get(bucket(bx, bz));
                if (l == null) continue;
                if (out == null) out = new ArrayList<>();
                out.addAll(l);
            }
        return out;
    }

    // ------------------------------------------------------------------ columns

    static final class Chunk {
        final short[] ter = new short[256], h = new short[256], gy = new short[256], stamp = new short[256];
        final byte[] top = new byte[256], sub = new byte[256], under = new byte[256], faceTop = new byte[256], biome = new byte[256], sx = new byte[256], sz = new byte[256], flags = new byte[256];
    }

    static final int F_WATER = 1, F_ICE = 2;

    Chunk chunk(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        Chunk c = chunks.get(key);
        if (c == null) {
            c = generate(cx, cz);
            if (chunks.size() > 30000) chunks.clear();
            chunks.put(key, c);
        }
        return c;
    }

    void clear() { chunks.clear(); layouts.clear(); }

    int biomeIndexAt(int x, int z) {
        int b = src.at(warpX(x, z), warpZ(x, z));
        return b < 0 || b >= styles.length ? 0 : b;
    }

    private int warpX(int x, int z) { return x + (int) ((vn(x * 0.021, z * 0.021, 901) - 0.5) * cell * 0.7); }
    private int warpZ(int x, int z) { return z + (int) ((vn(x * 0.021 + 50, z * 0.021 + 50, 902) - 0.5) * cell * 0.7); }

    private Style styleAtCell(int i, int j) {
        int b = src.at((int) ((i + 0.5) * cell), (int) ((j + 0.5) * cell));
        return b < 0 || b >= styles.length ? styles[0] : styles[b];
    }

    /** The terrain height before villages are levelled: the biome's own height, blended smoothly across the map cells. */
    double rawHeight(int x, int z) {
        double u = x / (double) cell - 0.5, v = z / (double) cell - 0.5;
        int i0 = (int) Math.floor(u), j0 = (int) Math.floor(v);
        double fu = u - i0, fv = v - j0;
        fu = fu * fu * (3 - 2 * fu); fv = fv * fv * (3 - 2 * fv);
        double base = 0, amp = 0, ridge = 0, mesa = 0;
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
            double w = (a == 0 ? 1 - fu : fu) * (b == 0 ? 1 - fv : fv);
            if (w < 1e-4) continue;
            Style s = styleAtCell(i0 + a, j0 + b);
            base += w * s.base; amp += w * s.amp; if (s.ridged) ridge += w; if (s.mesa) mesa += w;
        }
        double n1 = fbm(x * 0.011, z * 0.011, 4, 11), n2 = fbm(x * 0.06, z * 0.06, 2, 23);
        double hgt = base + (n1 - 0.5) * 2 * amp + (n2 - 0.5) * 2 * Math.min(2.2, amp * 0.25 + 0.4);
        if (ridge > 0.01) { double r = 1 - Math.abs(2 * fbm(x * 0.009, z * 0.009, 4, 41) - 1); hgt += ridge * (r - 0.5) * amp * 1.3; }
        if (mesa > 0.01) { double t = Math.floor(hgt / 5.0) * 5.0 + 5.0 * Math.min(1, (hgt % 5.0) / 1.2); hgt = hgt + (t - hgt) * mesa; }
        return hgt;
    }

    private Chunk generate(int cx, int cz) {
        Chunk c = new Chunk();
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) column(c, lx, lz, cx * 16 + lx, cz * 16 + lz);
        return c;
    }

    private void column(Chunk c, int lx, int lz, int x, int z) {
        int i = lz * 16 + lx;
        int bi = biomeIndexAt(x, z);
        Style st = styles[bi];
        c.biome[i] = (byte) bi;
        double hf = rawHeight(x, z);

        // villages and other sites level the ground under them
        List<Site> near = sitesNear(x, z, 60);
        Layout lay = null;
        if (near != null) for (Site s : near) {
            double d = Math.hypot(x - s.x, z - s.z);
            Layout l = layout(s);
            if (d > l.flatR + 14) continue;
            double w = d <= l.flatR ? 1 : 1 - (d - l.flatR) / 14.0;
            w = w * w * (3 - 2 * w);
            hf = hf + (l.hc - hf) * w;
            if (d <= l.flatR && lay == null) lay = l;
        }
        int ter = (int) Math.round(hf);
        byte top = st.top, sub = st.sub, under = st.under;
        int flags = 0;
        double pn = fbm(x * 0.045, z * 0.045, 3, 71);
        if (ter < SEA) {
            flags |= F_WATER;
            top = sub = st.floor;
            if (st.ocean && st.floor == Blocks3D.GRAVEL && pn > 0.55) top = Blocks3D.SAND;
            if (st.ice) {
                // the sea freezes over: a sheet of ice at sea level, with bergs of packed ice here and there
                flags = F_ICE;
                int berg = pn > 0.66 ? (int) ((pn - 0.66) * 70) : 0;
                ter = SEA + berg;
                top = berg > 2 ? Blocks3D.PACKED_ICE : Blocks3D.ICE;
                sub = Blocks3D.ICE;
                under = Blocks3D.STONE;
            }
        } else {
            if (st.patch != 0 && pn > 1 - st.patchP) top = st.patch;
            byte snowB = st.sub == Blocks3D.DIRT ? Blocks3D.SNOW : Blocks3D.SNOW_BLOCK;
            if (st.snowy && st.top != Blocks3D.SNOW && st.top != Blocks3D.SNOW_BLOCK && ter > SEA + 3 && !st.ice) top = snowB;
            if (ter > 118 && !(st.id.equals("stony_peaks")) && (st.snowy || ter > 140) && pn < 0.8) top = ter > 150 ? Blocks3D.SNOW_BLOCK : (pn > 0.35 ? Blocks3D.SNOW_BLOCK : top);
            if (st.id.equals("desert") && pn > 0.78) top = Blocks3D.SANDSTONE;
            if (st.mesa) {
                if (st.id.equals("wooded_badlands") && ter < 86) top = Blocks3D.GRASS;
                else if (pn < 0.82) top = (byte) (Blocks3D.TERRA + ((ter * 5 / 3) & 7));
            }
        }
        c.ter[i] = (short) ter;
        c.top[i] = top; c.sub[i] = sub; c.under[i] = under;
        c.flags[i] = (byte) flags;
        c.stamp[i] = -1;
        c.faceTop[i] = top;

        int finalH = ter;
        // path columns of a village
        if (lay != null && (flags & F_WATER) == 0 && lay.path.contains(((long) x << 32) ^ (z & 0xFFFFFFFFL))) { c.faceTop[i] = Blocks3D.PATH; c.top[i] = Blocks3D.PATH; }

        // what stands on it
        int[] f = featureAt(x, z, st, ter, flags, near, lay);
        if (f != null) {
            Stamp s = Stamp.get(f[0]);
            int t = s.top(f[1], f[2]);
            if (t >= 0 && s.w == 1 && s.d == 1 && s.h == 1 && Blocks3D.plant(s.at(0, 0, 0))) {
                c.faceTop[i] = s.at(0, 0, 0);        // flowers and grass are colour on the ground, not blocks
            } else if (t >= 0) {
                c.stamp[i] = (short) f[0];
                c.sx[i] = (byte) f[1]; c.sz[i] = (byte) f[2]; c.gy[i] = (short) f[3];
                int hh = f[3] + t + 1;
                if (hh > finalH) { finalH = hh; c.faceTop[i] = s.at(f[1], t, f[2]); }
                else c.stamp[i] = -1;
            }
        }
        c.h[i] = (short) finalH;
    }

    // ------------------------------------------------------------------ what stands on the ground

    private int[] featureAt(int x, int z, Style st, int ter, int flags, List<Site> near, Layout lay) {
        // buildings of a village
        if (near != null) for (Site s : near) {
            if (Math.abs(x - s.x) > 45 || Math.abs(z - s.z) > 45) continue;
            Layout l = layout(s);
            for (int[] p : l.placed) {
                Stamp sp = Stamp.get(p[0]);
                int sx = x - p[1] + sp.ax, sz = z - p[2] + sp.az;
                if (sx >= 0 && sz >= 0 && sx < sp.w && sz < sp.d && sp.top(sx, sz) >= 0) return new int[]{sp.id, sx, sz, l.hc};
            }
        }
        // scenes place things by hand
        for (int[] e : extras) {
            Stamp sp = Stamp.get(e[0]);
            int sx = x - e[1] + sp.ax, sz = z - e[2] + sp.az;
            if (sx >= 0 && sz >= 0 && sx < sp.w && sz < sp.d && sp.top(sx, sz) >= 0) return new int[]{sp.id, sx, sz, e[3]};
        }
        if ((flags & F_ICE) != 0) return null;
        boolean water = (flags & F_WATER) != 0;
        if (water && !(st.id.equals("mangrove_swamp") && ter >= SEA - 3) && !(st.id.equals("warm_ocean"))) return null;

        // trees: one candidate per 6x6 cell, each within reach of the column
        if (st.trees.length > 0 && !water) {
            int best = -1, bt = -1, bx = 0, bz = 0, bg = 0;
            for (int cx = Math.floorDiv(x - 5, 6); cx <= Math.floorDiv(x + 5, 6); cx++)
                for (int cz = Math.floorDiv(z - 5, 6); cz <= Math.floorDiv(z + 5, 6); cz++) {
                    double r = hash(cx, cz, seed + 5);
                    int ax = cx * 6 + (int) (hash(cx, cz, seed + 6) * 6), az = cz * 6 + (int) (hash(cx, cz, seed + 7) * 6);
                    Style as = ax == x && az == z ? st : styles[biomeIndexAt(ax, az)];
                    if (as.trees.length == 0 || r >= as.density) continue;
                    Tree kind = as.trees[(int) (hash(cx, cz, seed + 8) * as.trees.length)];
                    Stamp[] vars = Stamp.trees(kind);
                    Stamp sp = vars[(int) (hash(cx, cz, seed + 9) * vars.length)];
                    int sx = x - ax + sp.ax, sz = z - az + sp.az;
                    if (sx < 0 || sz < 0 || sx >= sp.w || sz >= sp.d) continue;
                    int t = sp.top(sx, sz);
                    if (t < 0) continue;
                    boolean cleared = false;
                    if (near != null) for (Site s2 : near) if (Math.hypot(ax - s2.x, az - s2.z) < (s2.kind.contains("village") ? 30 : Math.max(16, Models.stampFor(s2.kind).w * 0.75))) { cleared = true; break; }
                    if (cleared) continue;
                    int g = (ax == x && az == z) ? ter : (int) Math.round(rawHeight(ax, az));
                    if (g < SEA && !(as.id.equals("mangrove_swamp") && g >= SEA - 3)) continue;
                    if (kind == Tree.CACTUS && !(g > SEA + 1)) continue;
                    if (g + t > bt) { bt = g + t; best = sp.id; bx = sx; bz = sz; bg = g; }
                }
            if (best >= 0) return new int[]{best, bx, bz, bg};
        }
        // plants: single blocks, here and there
        if (st.plants.length > 0 && !water) {
            double r = hash(x, z, seed + 13);
            if (r < st.plantP) {
                Tree kind = st.plants[(int) (hash(x, z, seed + 14) * st.plants.length)];
                Stamp sp = Stamp.trees(kind)[0];
                return new int[]{sp.id, 0, 0, ter};
            }
        }
        if (st.id.equals("warm_ocean") && water) {
            double r = hash(x, z, seed + 15);
            if (r < 0.06) { Stamp sp = Stamp.trees(Tree.TALL_GRASS)[0]; return null; }
        }
        return null;
    }

    int groundAt(int x, int z) {
        Chunk c = chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        return c.ter[(Math.floorMod(z, 16)) * 16 + Math.floorMod(x, 16)];
    }

    // ------------------------------------------------------------------ villages

    static final class Layout {
        final int hc;
        final Models.Style style;
        int flatR = 30;
        final List<int[]> placed = new ArrayList<>();      // {stamp id, x, z}
        final Set<Long> path = new HashSet<>();
        Layout(int hc, Models.Style style) { this.hc = hc; this.style = style; }
    }

    private Layout layout(Site s) {
        Layout l = layouts.get(s);
        if (l != null) return l;
        if (!s.kind.contains("village")) {
            Stamp sp = Models.stampFor(s.kind);
            int raw = (int) Math.round(rawHeight(s.x, s.z));
            l = new Layout(raw, Models.Style.PLAINS);
            l.flatR = raw < SEA ? 0 : Math.max(sp.w, sp.d) / 2 + 5;
            l.placed.add(new int[]{sp.id, s.x, s.z});
            layouts.put(s, l);
            return l;
        }
        int hc = (int) Math.round(rawHeight(s.x, s.z));
        if (hc < SEA + 1) hc = SEA + 1;
        Style bs = styles[biomeIndexAt(s.x, s.z)];
        Models.Style ms = bs.village;
        if (s.kind.equals("desert_village")) ms = Models.Style.DESERT;
        else if (s.kind.equals("savanna_village")) ms = Models.Style.SAVANNA;
        else if (s.kind.equals("snowy_village")) ms = Models.Style.SNOWY;
        else if (s.kind.equals("taiga_village")) ms = Models.Style.TAIGA;
        else if (s.kind.equals("plains_village")) ms = Models.Style.PLAINS;
        l = new Layout(hc, ms);
        Random r = new Random(s.x * 73856093L ^ s.z * 19349663L);
        Stamp well = Models.well(ms);
        l.placed.add(new int[]{well.id, s.x, s.z});
        Stamp[] kinds = Models.houses(ms);
        List<int[]> boxes = new ArrayList<>();
        boxes.add(new int[]{s.x - 4, s.z - 4, s.x + 4, s.z + 4});
        int want = 9 + r.nextInt(6);
        for (int attempt = 0; attempt < 90 && l.placed.size() < want + 1; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2, rad = 10 + r.nextDouble() * 15;
            int px = s.x + (int) Math.round(Math.cos(ang) * rad), pz = s.z + (int) Math.round(Math.sin(ang) * rad);
            Stamp sp = kinds[r.nextInt(kinds.length)];
            if (r.nextBoolean()) sp = rotated(sp);
            boolean farm = r.nextInt(7) == 0 && !(ms == Models.Style.DESERT && false);
            if (farm) sp = Models.farm();
            int x0 = px - sp.ax, z0 = pz - sp.az, x1 = x0 + sp.w - 1, z1 = z0 + sp.d - 1;
            boolean ok = true;
            for (int[] b : boxes) if (x0 - 2 <= b[2] && x1 + 2 >= b[0] && z0 - 2 <= b[3] && z1 + 2 >= b[1]) { ok = false; break; }
            if (!ok) continue;
            boxes.add(new int[]{x0, z0, x1, z1});
            l.placed.add(new int[]{sp.id, px, pz});
            if (!farm) {
                // the path runs from the door (the middle of the near side) to the well
                int tx = px, tz = pz;
                int dxs = Integer.signum(s.x - tx), dzs = Integer.signum(s.z - tz);
                int cx = tx, cz = tz;
                for (int k = 0; k < 40 && (cx != s.x || cz != s.z); k++) {
                    if (cx != s.x && (Math.abs(cx - s.x) >= Math.abs(cz - s.z) || cz == s.z)) cx += Integer.signum(s.x - cx);
                    else if (cz != s.z) cz += Integer.signum(s.z - cz);
                    l.path.add(((long) cx << 32) ^ (cz & 0xFFFFFFFFL));
                }
            }
        }
        // the paths and houses must not overlap
        for (int[] p : l.placed) {
            Stamp sp = Stamp.get(p[0]);
            for (int x = p[1] - sp.ax; x < p[1] - sp.ax + sp.w; x++) for (int z = p[2] - sp.az; z < p[2] - sp.az + sp.d; z++) l.path.remove(((long) x << 32) ^ (z & 0xFFFFFFFFL));
        }
        layouts.put(s, l);
        return l;
    }

    private static final Map<Integer, Stamp> ROT = new ConcurrentHashMap<>();

    private static Stamp rotated(Stamp s) { return ROT.computeIfAbsent(s.id, k -> s.rotated()); }

    // ------------------------------------------------------------------ what a column looks like

    /** The block of the ground itself at height y (no trees or buildings). */
    byte groundBlock(Chunk c, int i, int y) {
        int ter = c.ter[i];
        if ((c.flags[i] & F_ICE) != 0) return y >= SEA - 2 ? Blocks3D.ICE : Blocks3D.STONE;
        if (y >= ter - 1) return c.top[i];
        if (y >= ter - 4) return c.sub[i];
        byte u = c.under[i];
        if (u == Blocks3D.TERRA) return (byte) (Blocks3D.TERRA + ((y * 5 / 3) & 7));
        return u;
    }

    /** The block seen from the side of a column at height y. */
    byte sideBlock(Chunk c, int i, int y) {
        int sid = c.stamp[i];
        int ter = c.ter[i];
        if (sid >= 0 && y >= ter) {
            Stamp s = Stamp.get(sid);
            byte b = s.side(c.sx[i], y - c.gy[i], c.sz[i]);
            if (b != 0) return b;
        }
        if ((c.flags[i] & F_ICE) != 0) return y >= SEA - 2 ? Blocks3D.ICE : Blocks3D.STONE;
        if (y >= ter - 1) return c.top[i];
        if (y >= ter - 4) return c.sub[i];
        byte u = c.under[i];
        if (u == Blocks3D.TERRA) return (byte) (Blocks3D.TERRA + ((y * 5 / 3) & 7));
        return u;
    }

    /** The grass, leaf or water colour of the biome of this column (0xRRGGBB). */
    int grassTint(Chunk c, int i) { return styles[c.biome[i]].grass; }
    int foliageTint(Chunk c, int i) { return styles[c.biome[i]].foliage; }
    int waterTint(Chunk c, int i) { return styles[c.biome[i]].water; }

    String biomeName(int x, int z) { return names[biomeIndexAt(x, z)]; }

    /** The height of the top of the column at (x, z). */
    int topAt(int x, int z) {
        Chunk c = chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        return c.h[Math.floorMod(z, 16) * 16 + Math.floorMod(x, 16)];
    }
}
