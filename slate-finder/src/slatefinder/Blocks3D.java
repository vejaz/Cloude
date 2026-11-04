package slatefinder;

/**
 * The blocks of the 3D views and their textures. Every texture is drawn in code (16x16, one for the top of a
 * block and one for its sides), in the spirit of the game's own, so nothing has to be shipped as an image.
 */
final class Blocks3D {
    private Blocks3D() {}

    static final byte AIR = 0, GRASS = 1, DIRT = 2, STONE = 3, SAND = 4, SNOW = 5, WATER = 6, OAK_LOG = 7, OAK_LEAVES = 8,
        BIRCH_LOG = 9, BIRCH_LEAVES = 10, SPRUCE_LOG = 11, SPRUCE_LEAVES = 12, SANDSTONE = 13, RED_SAND = 14, TERRA = 15 /* 15..22 */,
        ICE = 23, PACKED_ICE = 24, MYCELIUM = 25, PODZOL = 26, GRAVEL = 27, CACTUS = 28, BAMBOO = 29, CHERRY_LEAVES = 30, CHERRY_LOG = 31,
        PLANKS = 32, COBBLE = 33, SPRUCE_PLANKS = 34, GLASS = 35, DARK_LOG = 36, DARK_LEAVES = 37, JUNGLE_LOG = 38, JUNGLE_LEAVES = 39,
        ACACIA_LOG = 40, ACACIA_LEAVES = 41, MANGROVE_LOG = 42, MUD = 43, MOSS = 44, RED_MUSH = 45, BROWN_MUSH = 46, MUSH_STEM = 47,
        PATH = 48, FARMLAND = 49, BRICK = 50, PRISMARINE = 51, SEA_LANTERN = 52, ACACIA_PLANKS = 53, CUT_SANDSTONE = 54, MOSSY_COBBLE = 55,
        FLOWER_RED = 56, FLOWER_YELLOW = 57, FLOWER_PINK = 58, GRASS_TALL = 59, PALE_LEAVES = 60, PALE_LOG = 61, DEAD_BUSH = 62, BONE = 63,
        OBSIDIAN = 64, DEEPSLATE_BRICK = 65, CHEST = 66, DARK_PLANKS = 67, WHITE_WOOL = 68, BROWN_WOOL = 69, BLACK_WOOL = 70, PINK_WOOL = 71,
        RED_WOOL = 72, ORANGE_WOOL = 73, GRAY_WOOL = 74, TERRACOTTA_BRICK = 75, GOLD = 76, WHEAT = 77, WATER_LILY = 78, CORAL = 79,
        KELP = 80, SWEET_BERRY = 81, MOSSY_BRICK = 82, PUMPKIN = 83, HAY = 84, SPAWNER = 85, SCULK = 86, DEEPSLATE = 87, SNOW_BRICK = 88,
        LEAF_FLOWER = 89, MANGROVE_LEAVES = 90, PINK_PETALS = 91, TERRACOTTA_WHITE = 92, BANNER = 93, STONE_BRICK = 94, LANTERN = 95,
        SNOW_BLOCK = 96, COUNT = 97;

    /** 16x16 textures: [block][256]. */
    static final int MAX = 256;
    static final int[][] TOP = new int[MAX][], SIDE = new int[MAX][];
    /** Average colour of a block's top, for the flat map. */
    static final int[] AVG = new int[MAX];

    private static int rgb(int r, int g, int b) { return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b); }
    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }
    private static int shade(int c, double f) { return rgb((int) (((c >> 16) & 255) * f), (int) (((c >> 8) & 255) * f), (int) ((c & 255) * f)); }
    private static int mix(int a, int b, double t) {
        return rgb((int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t), (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t), (int) ((a & 255) + ((b & 255) - (a & 255)) * t));
    }
    private static double h(int x, int y, int s) {
        long v = x * 374761393L + y * 668265263L + s * 2147483647L;
        v = (v ^ (v >>> 13)) * 1274126177L;
        v ^= v >>> 16;
        return (v & 0xFFFFFF) / (double) 0x1000000;
    }

    private interface Px { int at(int x, int y); }

    private static int[] tex(Px p) {
        int[] t = new int[256];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) t[y * 16 + x] = p.at(x, y);
        return t;
    }

    private static int[] noise(int c, double amp, int seed) { return tex((x, y) -> shade(c, 1 + (h(x, y, seed) - 0.5) * amp)); }

    private static int[] blobs(int a, int b, double amp, int seed) {
        return tex((x, y) -> { double n = h(x / 2, y / 2, seed) * 0.7 + h(x, y, seed + 1) * 0.3; return shade(n > 0.52 ? a : b, 1 + (h(x, y, seed + 2) - 0.5) * amp); });
    }

    private static int[] stripesV(int a, int b, int seed) {
        return tex((x, y) -> shade(((x + (int) (h(0, y / 4, seed) * 3)) % 4 < 2) ? a : b, 1 + (h(x, y, seed) - 0.5) * 0.14));
    }

    private static int[] rings(int a, int b) {
        return tex((x, y) -> { int r = Math.min(Math.min(x, 15 - x), Math.min(y, 15 - y)); return r < 2 ? b : (r % 4 < 2 ? a : shade(a, 0.84)); });
    }

    private static int[] leaves(int a, int b, int seed) {
        return tex((x, y) -> { double n = h(x, y, seed); return n < 0.18 ? shade(a, 0.45) : n > 0.7 ? b : shade(a, 0.85 + h(x, y, seed + 1) * 0.3); });
    }

    private static int[] planks(int a, int b, int seed) {
        return tex((x, y) -> y % 4 == 3 ? b : shade(a, 1 + (h(x, y / 4, seed) - 0.5) * 0.18 + (h(x, y, seed + 1) - 0.5) * 0.08));
    }

    private static int[] bricks(int a, int b, int seed) {
        return tex((x, y) -> (y % 4 == 3 || (x + (y / 4 % 2) * 4) % 8 == 7) ? b : shade(a, 1 + (h(x, y, seed) - 0.5) * 0.16));
    }

    private static int[] grassSide(int grass, int dirt, int seed) {
        return tex((x, y) -> y < 3 + (int) (h(x, 0, seed) * 2.5) ? shade(grass, 0.92 + h(x, y, seed) * 0.18) : shade(dirt, 0.9 + h(x, y, seed + 1) * 0.2));
    }

    private static int[] snowSide(int under, int seed) {
        return tex((x, y) -> y < 3 + (int) (h(x, 0, seed) * 2.5) ? shade(0xFFF4F8FA, 0.94 + h(x, y, seed) * 0.08) : shade(under, 0.9 + h(x, y, seed + 1) * 0.2));
    }

    private static void def(byte b, int[] top, int[] side) {
        TOP[b] = top;
        SIDE[b] = side == null ? top : side;
    }

    private static void both(byte b, int[] t) { def(b, t, t); }

    static {
        def(GRASS, noise(0x79B03A, 0.22, 1), grassSide(0x79B03A, 0x866043, 2));
        both(DIRT, blobs(0x866043, 0x6E4E34, 0.2, 3));
        both(STONE, blobs(0x7D7D7D, 0x6E6E6E, 0.2, 4));
        both(SAND, noise(0xE9D69A, 0.14, 5));
        def(SNOW, noise(0xF4F8FA, 0.07, 6), snowSide(0x866043, 7));
        both(WATER, tex((x, y) -> mix(0xFF3F76E4, 0xFF5A90F0, (Math.sin(x * 0.7 + y * 0.4) + 1) / 4)));
        def(OAK_LOG, rings(0xFFA07F4C, 0xFF6B5230), stripesV(0xFF6B5230, 0xFF5A4326, 8));
        both(OAK_LEAVES, leaves(0xFF3F8A2E, 0xFF5AA83A, 9));
        def(BIRCH_LOG, rings(0xFFD8C99A, 0xFFC8C0A0), tex((x, y) -> h(x / 2, y, 10) > 0.8 ? 0xFF2A2A26 : shade(0xFFD7D3C4, 0.94 + h(x, y, 11) * 0.1)));
        both(BIRCH_LEAVES, leaves(0xFF6FA83A, 0xFF8AC24A, 12));
        def(SPRUCE_LOG, rings(0xFF8A6A40, 0xFF4A3622), stripesV(0xFF4A3622, 0xFF3A2A1A, 13));
        both(SPRUCE_LEAVES, leaves(0xFF2F5A3A, 0xFF3F7A4A, 14));
        def(SANDSTONE, noise(0xFFE6CC84, 0.1, 15), tex((x, y) -> y % 8 == 6 ? 0xFFC8A458 : shade(0xFFE0C478, 0.95 + h(x, y, 16) * 0.1)));
        both(RED_SAND, noise(0xFFC9682E, 0.14, 17));
        int[] bands = {0xFFD9784A, 0xFFB5502E, 0xFFE0B08A, 0xFFC96038, 0xFFF0E0CC, 0xFF9A4A2A, 0xFFD98A5A, 0xFFB5502E};
        for (int i = 0; i < 8; i++) both((byte) (TERRA + i), noise(bands[i], 0.1, 18 + i));
        both(ICE, tex((x, y) -> shade(((x + y) % 11 < 2) ? 0xFFDCEEFF : 0xFFA8CCF0, 0.95 + h(x, y, 30) * 0.1)));
        both(PACKED_ICE, tex((x, y) -> shade(((x - y + 32) % 9 < 2) ? 0xFFD0E4FF : 0xFF8AB4F0, 0.94 + h(x, y, 31) * 0.12)));
        def(MYCELIUM, blobs(0xFF8A7A90, 0xFF6E6074, 0.2, 32), grassSide(0xFF8A7A90, 0xFF866043, 33));
        both(PODZOL, blobs(0xFF6B5A3A, 0xFF4A3A22, 0.25, 34));
        both(GRAVEL, blobs(0xFF8A8584, 0xFF6A6A68, 0.3, 35));
        def(CACTUS, tex((x, y) -> (x == 0 || x == 15 || y == 0 || y == 15) ? 0xFF2F6A2A : 0xFF3E8A3A), tex((x, y) -> (x % 5 == 2) ? 0xFF2F6A2A : shade(0xFF3E8A3A, 0.9 + h(x, y, 36) * 0.15)));
        both(BAMBOO, tex((x, y) -> (x >= 5 && x <= 10) ? ((y % 8 == 0) ? 0xFF5E9B2E : 0xFF7DB93A) : shade(0xFF4A8A2A, 0.8)));
        both(CHERRY_LEAVES, leaves(0xFFF5B2D4, 0xFFFFD0E4, 37));
        def(CHERRY_LOG, rings(0xFFC89AA4, 0xFF5A3A46), stripesV(0xFF5A3A46, 0xFF46303A, 38));
        both(PLANKS, planks(0xFFB8935A, 0xFF8A6A3A, 39));
        both(COBBLE, blobs(0xFF7A7A78, 0xFF5A5A58, 0.25, 40));
        both(SPRUCE_PLANKS, planks(0xFF7A5A3A, 0xFF5A4028, 41));
        both(GLASS, tex((x, y) -> (x == 0 || x == 15 || y == 0 || y == 15) ? 0xFF8AB0C8 : 0xFFB8DCF0));
        def(DARK_LOG, rings(0xFF5A4630, 0xFF3B2A18), stripesV(0xFF3B2A18, 0xFF2A1E10, 42));
        both(DARK_LEAVES, leaves(0xFF2A5A22, 0xFF3A7A2E, 43));
        def(JUNGLE_LOG, rings(0xFF7A6638, 0xFF5A4A28), stripesV(0xFF5A4A28, 0xFF4A3A1C, 44));
        both(JUNGLE_LEAVES, leaves(0xFF2F8A22, 0xFF4AAA32, 45));
        def(ACACIA_LOG, rings(0xFFBA663A, 0xFF6A6A64), stripesV(0xFF8B7F73, 0xFF6A6058, 46));
        both(ACACIA_LEAVES, leaves(0xFF7A9A2E, 0xFF9ABA40, 47));
        def(MANGROVE_LOG, rings(0xFF8A4A44, 0xFF6A3B34), stripesV(0xFF6A3B34, 0xFF4A2A26, 48));
        both(MUD, blobs(0xFF4A3A34, 0xFF3A2C28, 0.2, 49));
        both(MOSS, leaves(0xFF5E7A2E, 0xFF7A9A3E, 50));
        both(RED_MUSH, tex((x, y) -> h(x / 3, y / 3, 51) > 0.7 ? 0xFFF4EEE4 : shade(0xFFCC2A2A, 0.9 + h(x, y, 52) * 0.2)));
        both(BROWN_MUSH, tex((x, y) -> h(x / 3, y / 3, 53) > 0.75 ? 0xFFE0D4C0 : shade(0xFF9A7050, 0.9 + h(x, y, 54) * 0.2)));
        both(MUSH_STEM, noise(0xFFE8E0D0, 0.1, 55));
        def(PATH, blobs(0xFFA89060, 0xFF8A7448, 0.2, 56), tex((x, y) -> y < 2 ? 0xFFA89060 : shade(0xFF866043, 0.9 + h(x, y, 57) * 0.2)));
        def(FARMLAND, tex((x, y) -> (x % 4 == 1) ? 0xFF3A6A22 : shade(0xFF5A3A22, 0.9 + h(x, y, 58) * 0.2)), noise(0xFF866043, 0.2, 59));
        both(BRICK, bricks(0xFFA8524A, 0xFF8A8078, 60));
        both(PRISMARINE, tex((x, y) -> h(x / 2, y / 2, 61) > 0.6 ? 0xFF7AC8B8 : shade(0xFF4A9A8A, 0.9 + h(x, y, 62) * 0.2)));
        both(SEA_LANTERN, tex((x, y) -> (x % 8 < 1 || y % 8 < 1) ? 0xFF9AD8D0 : 0xFFE8FFF8));
        both(ACACIA_PLANKS, planks(0xFFB5663A, 0xFF8A4A28, 63));
        both(CUT_SANDSTONE, tex((x, y) -> (x == 0 || y == 0) ? 0xFFC8A458 : shade(0xFFE6CC84, 0.96 + h(x, y, 64) * 0.08)));
        both(MOSSY_COBBLE, blobs(0xFF6A8A5A, 0xFF5A7A4A, 0.25, 65));
        both(FLOWER_RED, noise(0xFFD03030, 0.2, 66));
        both(FLOWER_YELLOW, noise(0xFFF0D020, 0.2, 67));
        both(FLOWER_PINK, noise(0xFFE070C0, 0.2, 68));
        both(GRASS_TALL, leaves(0xFF4F9A2E, 0xFF6ABA3A, 69));
        both(PALE_LEAVES, leaves(0xFF9AA89E, 0xFFB8C4BA, 70));
        def(PALE_LOG, rings(0xFFE6E0D8, 0xFFB8B0A4), stripesV(0xFFE6E0D8, 0xFFCFC8BE, 71));
        both(DEAD_BUSH, noise(0xFF7A5A2A, 0.3, 72));
        both(BONE, tex((x, y) -> (x + y) % 6 < 2 ? 0xFFD8D0B8 : 0xFFF0EAD8));
        both(OBSIDIAN, tex((x, y) -> shade(0xFF1A0F2A, 0.8 + h(x, y, 73) * 0.7)));
        both(DEEPSLATE_BRICK, bricks(0xFF3A3E4A, 0xFF22262E, 74));
        both(CHEST, tex((x, y) -> (y == 7 || x == 7) ? 0xFFD0D0D0 : shade(0xFF8A5A2A, 0.9 + h(x, y, 75) * 0.2)));
        both(DARK_PLANKS, planks(0xFF4A3622, 0xFF2A1E10, 76));
        both(WHITE_WOOL, noise(0xFFF0EEE8, 0.08, 77));
        both(BROWN_WOOL, noise(0xFF6A4A2A, 0.12, 78));
        both(BLACK_WOOL, noise(0xFF2A2A2E, 0.12, 79));
        both(PINK_WOOL, noise(0xFFF0A0B8, 0.1, 80));
        both(RED_WOOL, noise(0xFFB02A2A, 0.12, 81));
        both(ORANGE_WOOL, noise(0xFFE8841A, 0.12, 82));
        both(GRAY_WOOL, noise(0xFFA8A49C, 0.12, 83));
        both(TERRACOTTA_BRICK, bricks(0xFFB5502E, 0xFF8A3A22, 84));
        both(GOLD, tex((x, y) -> (x == 0 || y == 0 || x == 15 || y == 15) ? 0xFFD8A020 : 0xFFF8D840));
        both(WHEAT, tex((x, y) -> (x % 3 == 0) ? 0xFFC8A838 : 0xFFE8C850));
        both(WATER_LILY, noise(0xFF2F8A3A, 0.2, 85));
        both(CORAL, tex((x, y) -> h(x / 3, y / 3, 86) > 0.5 ? 0xFFE0409A : 0xFF30B0E0));
        both(KELP, noise(0xFF3F9A32, 0.3, 87));
        both(SWEET_BERRY, tex((x, y) -> h(x, y, 88) > 0.8 ? 0xFFCC2A3A : 0xFF3A6A2A));
        both(MOSSY_BRICK, bricks(0xFF6A8A5A, 0xFF4A6A3A, 89));
        both(PUMPKIN, tex((x, y) -> x % 4 == 0 ? 0xFFC86A10 : 0xFFE8941A));
        both(HAY, tex((x, y) -> y % 4 == 0 ? 0xFFB89A28 : 0xFFE0C038));
        both(SPAWNER, tex((x, y) -> (x % 4 == 0 || y % 4 == 0) ? 0xFF3A3A44 : 0xFF121018));
        both(SCULK, tex((x, y) -> h(x, y, 90) > 0.9 ? 0xFF22E0D0 : shade(0xFF0E2A34, 0.8 + h(x, y, 91) * 0.5)));
        both(DEEPSLATE, blobs(0xFF4A4E58, 0xFF3A3E48, 0.2, 92));
        both(SNOW_BRICK, bricks(0xFFEAF2F8, 0xFFB8C8D4, 93));
        both(LEAF_FLOWER, leaves(0xFF3F8A2E, 0xFFF04080, 94));
        both(MANGROVE_LEAVES, leaves(0xFF4E8A3C, 0xFF6AAA4A, 95));
        both(PINK_PETALS, noise(0xFFFFB6D6, 0.2, 96));
        both(TERRACOTTA_WHITE, noise(0xFFD2B8A8, 0.1, 97));
        both(BANNER, tex((x, y) -> (y > 5 && y < 9) ? 0xFF8AA0E0 : 0xFF1F2F5A));
        both(STONE_BRICK, bricks(0xFF7C7870, 0xFF5A5A56, 98));
        both(SNOW_BLOCK, noise(0xF4F8FA, 0.07, 100));
        both(LANTERN, tex((x, y) -> (x > 3 && x < 12 && y > 3 && y < 12) ? 0xFFFFE08A : 0xFF3A3A3A));
        for (int b = 1; b < COUNT; b++) {
            if (TOP[b] == null) { TOP[b] = SIDE[b] = noise(0xFFFF00FF, 0.1, 99); }
            long r = 0, g = 0, bl = 0;
            for (int px : TOP[b]) { r += (px >> 16) & 255; g += (px >> 8) & 255; bl += px & 255; }
            AVG[b] = rgb((int) (r / 256), (int) (g / 256), (int) (bl / 256));
        }
    }

    private static final java.util.Map<String, Integer> DYNAMIC = new java.util.HashMap<>();
    private static int nextId = COUNT;

    /** A block that real world data has and this table does not: it gets the next free id and a plain textured colour. -1 when the table is full. */
    static synchronized int register(String name, int rgb) {
        Integer have = DYNAMIC.get(name);
        if (have != null) return have;
        if (nextId >= MAX) return -1;
        int id = nextId++;
        int c = 0xFF000000 | (rgb & 0xFFFFFF);
        int[] t = noise(c, 0.14, 200 + id);
        TOP[id] = t; SIDE[id] = t;
        AVG[id] = c;
        DYNAMIC.put(name, id);
        return id;
    }

    static synchronized String nameOf(int id) {
        for (java.util.Map.Entry<String, Integer> e : DYNAMIC.entrySet()) if (e.getValue() == id) return e.getKey();
        return null;
    }

    static synchronized java.util.Map<String, Integer> dynamic() { return new java.util.HashMap<>(DYNAMIC); }

    /** Blocks that are drawn as thin plants: they do not make a solid column of their own. */
    static boolean plant(byte b) {
        return b == FLOWER_RED || b == FLOWER_YELLOW || b == FLOWER_PINK || b == GRASS_TALL || b == DEAD_BUSH || b == SWEET_BERRY || b == PINK_PETALS;
    }

    static boolean leaf(byte b) {
        return b == OAK_LEAVES || b == BIRCH_LEAVES || b == SPRUCE_LEAVES || b == JUNGLE_LEAVES || b == ACACIA_LEAVES || b == CHERRY_LEAVES || b == DARK_LEAVES || b == PALE_LEAVES || b == MANGROVE_LEAVES || b == LEAF_FLOWER;
    }

    static boolean grassy(byte b) { return b == GRASS || b == MOSS || b == GRASS_TALL; }
}
