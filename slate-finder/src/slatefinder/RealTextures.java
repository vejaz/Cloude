package slatefinder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * The game's own block pictures, read from the Minecraft jar that is already on this computer (nothing is copied
 * out of it or shipped with the app). Used for the exact world. Without the jar the exact world falls back to the
 * app's own painted textures.
 */
final class RealTextures {
    private RealTextures() {}

    private static volatile boolean ready;
    private static ZipFile zip;
    private static final int[][] TOP = new int[Blocks3D.MAX][], SIDE = new int[Blocks3D.MAX][];
    private static final int[] AVG = new int[Blocks3D.MAX];
    private static final boolean[] TRIED = new boolean[Blocks3D.MAX];
    private static final Map<Integer, String[]> NAMES = new HashMap<>();

    private static void m(byte id, String top, String side) { NAMES.put(id & 255, new String[]{top, side}); }
    private static void m(byte id, String both) { m(id, both, both); }

    static {
        m(Blocks3D.GRASS, "grass_block_top", "grass_block_side");
        m(Blocks3D.DIRT, "dirt"); m(Blocks3D.STONE, "stone"); m(Blocks3D.SAND, "sand"); m(Blocks3D.SNOW, "snow"); m(Blocks3D.WATER, "water_still");
        m(Blocks3D.OAK_LOG, "oak_log_top", "oak_log"); m(Blocks3D.OAK_LEAVES, "oak_leaves");
        m(Blocks3D.BIRCH_LOG, "birch_log_top", "birch_log"); m(Blocks3D.BIRCH_LEAVES, "birch_leaves");
        m(Blocks3D.SPRUCE_LOG, "spruce_log_top", "spruce_log"); m(Blocks3D.SPRUCE_LEAVES, "spruce_leaves");
        m(Blocks3D.SANDSTONE, "sandstone_top", "sandstone"); m(Blocks3D.RED_SAND, "red_sand");
        String[] bands = {"orange_terracotta", "red_terracotta", "yellow_terracotta", "terracotta", "white_terracotta", "brown_terracotta", "light_gray_terracotta", "gray_terracotta"};
        for (int i = 0; i < 8; i++) m((byte) (Blocks3D.TERRA + i), bands[i]);
        m(Blocks3D.ICE, "ice"); m(Blocks3D.PACKED_ICE, "packed_ice");
        m(Blocks3D.MYCELIUM, "mycelium_top", "mycelium_side"); m(Blocks3D.PODZOL, "podzol_top", "podzol_side"); m(Blocks3D.GRAVEL, "gravel");
        m(Blocks3D.CACTUS, "cactus_top", "cactus_side"); m(Blocks3D.BAMBOO, "bamboo_stalk");
        m(Blocks3D.CHERRY_LEAVES, "cherry_leaves"); m(Blocks3D.CHERRY_LOG, "cherry_log_top", "cherry_log");
        m(Blocks3D.PLANKS, "oak_planks"); m(Blocks3D.COBBLE, "cobblestone"); m(Blocks3D.SPRUCE_PLANKS, "spruce_planks"); m(Blocks3D.GLASS, "glass");
        m(Blocks3D.DARK_LOG, "dark_oak_log_top", "dark_oak_log"); m(Blocks3D.DARK_LEAVES, "dark_oak_leaves");
        m(Blocks3D.JUNGLE_LOG, "jungle_log_top", "jungle_log"); m(Blocks3D.JUNGLE_LEAVES, "jungle_leaves");
        m(Blocks3D.ACACIA_LOG, "acacia_log_top", "acacia_log"); m(Blocks3D.ACACIA_LEAVES, "acacia_leaves");
        m(Blocks3D.MANGROVE_LOG, "mangrove_log_top", "mangrove_log"); m(Blocks3D.MUD, "mud"); m(Blocks3D.MOSS, "moss_block");
        m(Blocks3D.RED_MUSH, "red_mushroom_block"); m(Blocks3D.BROWN_MUSH, "brown_mushroom_block"); m(Blocks3D.MUSH_STEM, "mushroom_stem");
        m(Blocks3D.PATH, "dirt_path_top", "dirt_path_side"); m(Blocks3D.FARMLAND, "farmland", "dirt");
        m(Blocks3D.BRICK, "bricks"); m(Blocks3D.PRISMARINE, "prismarine"); m(Blocks3D.SEA_LANTERN, "sea_lantern");
        m(Blocks3D.ACACIA_PLANKS, "acacia_planks"); m(Blocks3D.CUT_SANDSTONE, "cut_sandstone"); m(Blocks3D.MOSSY_COBBLE, "mossy_cobblestone");
        m(Blocks3D.FLOWER_RED, "poppy"); m(Blocks3D.FLOWER_YELLOW, "dandelion"); m(Blocks3D.FLOWER_PINK, "pink_tulip"); m(Blocks3D.GRASS_TALL, "short_grass");
        m(Blocks3D.PALE_LEAVES, "pale_oak_leaves"); m(Blocks3D.PALE_LOG, "pale_oak_log_top", "pale_oak_log"); m(Blocks3D.DEAD_BUSH, "dead_bush");
        m(Blocks3D.BONE, "bone_block_top", "bone_block_side"); m(Blocks3D.OBSIDIAN, "obsidian"); m(Blocks3D.DEEPSLATE_BRICK, "deepslate_bricks");
        m(Blocks3D.DARK_PLANKS, "dark_oak_planks"); m(Blocks3D.WHITE_WOOL, "white_wool"); m(Blocks3D.BROWN_WOOL, "brown_wool"); m(Blocks3D.BLACK_WOOL, "black_wool");
        m(Blocks3D.PINK_WOOL, "pink_wool"); m(Blocks3D.RED_WOOL, "red_wool"); m(Blocks3D.ORANGE_WOOL, "orange_wool"); m(Blocks3D.GRAY_WOOL, "gray_wool");
        m(Blocks3D.TERRACOTTA_BRICK, "bricks"); m(Blocks3D.GOLD, "gold_block"); m(Blocks3D.WHEAT, "wheat_stage7"); m(Blocks3D.WATER_LILY, "lily_pad");
        m(Blocks3D.CORAL, "tube_coral_block"); m(Blocks3D.KELP, "kelp"); m(Blocks3D.SWEET_BERRY, "sweet_berry_bush_stage3"); m(Blocks3D.MOSSY_BRICK, "mossy_stone_bricks");
        m(Blocks3D.PUMPKIN, "pumpkin_top", "pumpkin_side"); m(Blocks3D.HAY, "hay_block_top", "hay_block_side"); m(Blocks3D.SPAWNER, "spawner");
        m(Blocks3D.SCULK, "sculk"); m(Blocks3D.DEEPSLATE, "deepslate_top", "deepslate"); m(Blocks3D.SNOW_BRICK, "snow");
        m(Blocks3D.LEAF_FLOWER, "flowering_azalea_leaves"); m(Blocks3D.MANGROVE_LEAVES, "mangrove_leaves"); m(Blocks3D.PINK_PETALS, "pink_petals");
        m(Blocks3D.TERRACOTTA_WHITE, "white_terracotta"); m(Blocks3D.STONE_BRICK, "stone_bricks"); m(Blocks3D.SNOW_BLOCK, "snow"); m(Blocks3D.LANTERN, "lantern");
    }

    static boolean ready() { return ready; }

    /** Where the game's jar usually is: the launcher's folder. */
    static File findJar() {
        String chosen = Settings.get("mcJar", "");
        if (!chosen.isEmpty() && new File(chosen).isFile()) return new File(chosen);
        String ad = System.getenv("APPDATA");
        File base = ad != null ? new File(ad, ".minecraft/versions") : new File(System.getProperty("user.home"), ".minecraft/versions");
        File want = new File(base, McServer.VERSION + "/" + McServer.VERSION + ".jar");
        if (want.isFile()) return want;
        File[] dirs = base.listFiles(File::isDirectory);
        File best = null;
        if (dirs != null) for (File d : dirs) {
            File j = new File(d, d.getName() + ".jar");
            if (j.isFile() && j.length() > 5_000_000 && (best == null || j.lastModified() > best.lastModified())) best = j;
        }
        return best;
    }

    /** Opens the jar and prepares the pictures. Returns false (and leaves the painted textures in use) if it cannot. */
    static synchronized boolean load(File jar) {
        close();
        if (jar == null || !jar.isFile()) return false;
        try {
            zip = new ZipFile(jar);
            if (zip.getEntry("assets/minecraft/textures/block/stone.png") == null) { zip.close(); zip = null; return false; }
        } catch (Exception e) { zip = null; return false; }
        java.util.Arrays.fill(TRIED, false);
        java.util.Arrays.fill(TOP, null);
        java.util.Arrays.fill(SIDE, null);
        java.util.Arrays.fill(AVG, 0);
        ready = true;
        return true;
    }

    static synchronized void close() {
        ready = false;
        if (zip != null) try { zip.close(); } catch (Exception ignored) { }
        zip = null;
    }

    static int[] pick(int id, boolean top) {
        if (!ready || id < 0 || id >= Blocks3D.MAX) return null;
        if (!TRIED[id]) fill(id);
        return top ? TOP[id] : SIDE[id];
    }

    static int avg(int id) {
        if (!ready || id < 0 || id >= Blocks3D.MAX) return 0;
        if (!TRIED[id]) fill(id);
        return AVG[id];
    }

    private static synchronized void fill(int id) {
        if (TRIED[id]) return;
        try {
            String[] n = NAMES.get(id);
            if (n == null && id >= Blocks3D.COUNT) {
                String dyn = Blocks3D.nameOf(id);
                if (dyn != null) for (String[] cand : ExactBlocks.textureCandidates(dyn)) {
                    if (read(cand[0]) != null || read(cand[1]) != null) { n = cand; break; }
                }
            }
            if (n != null) {
                int[] top = read(n[0]), side = n[1].equals(n[0]) ? top : read(n[1]);
                if (top == null) top = side;
                if (side == null) side = top;
                if (id == (Blocks3D.GRASS & 255) && side != null) side = grassSide(side);
                TOP[id] = top; SIDE[id] = side;
                if (top != null) {
                    long r = 0, g = 0, b = 0;
                    for (int px : top) { r += (px >> 16) & 255; g += (px >> 8) & 255; b += px & 255; }
                    AVG[id] = 0xFF000000 | ((int) (r / 256) << 16) | ((int) (g / 256) << 8) | (int) (b / 256);
                }
            }
        } catch (Exception | Error ignored) {
        } finally {
            TRIED[id] = true;
        }
    }

    /** The side of a grass block is dirt with a grass fringe that the game colours; colour the fringe like grass. */
    private static int[] grassSide(int[] dirtSide) {
        int[] over = read("grass_block_side_overlay", false);
        if (over == null) return dirtSide;
        int[] out = dirtSide.clone();
        for (int i = 0; i < 256; i++) {
            int a = (over[i] >>> 24);
            if (a < 40) continue;
            int r = (((over[i] >> 16) & 255) * 0x91) / 255, g = (((over[i] >> 8) & 255) * 0xBD) / 255, b = ((over[i] & 255) * 0x59) / 255;
            out[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return out;
    }

    private static int[] read(String name) { return read(name, true); }

    /** One 16x16 picture. Transparent pixels (leaves, plants) are filled with a darker shade of the picture's own colour. */
    private static int[] read(String name, boolean fillClear) {
        if (zip == null) return null;
        ZipEntry e = zip.getEntry("assets/minecraft/textures/block/" + name + ".png");
        if (e == null) return null;
        try (InputStream in = zip.getInputStream(e)) {
            BufferedImage im = ImageIO.read(in);
            if (im == null || im.getWidth() < 16 || im.getHeight() < 16) return null;
            int sw = im.getWidth() / 16;       // a 32x or 64x picture is sampled down
            int[] out = new int[256];
            long r = 0, g = 0, b = 0, n = 0;
            // grey pictures (stone, leaves, grass) must be read as they are stored: Java's own conversion brightens them
            boolean gray = im.getColorModel().getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY;
            int bits = gray ? im.getColorModel().getComponentSize(0) : 8;
            java.awt.image.Raster ras = im.getRaster();
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int px;
                if (gray) {
                    int v = ras.getSample(x * sw, y * sw, 0), al = ras.getNumBands() > 1 ? ras.getSample(x * sw, y * sw, 1) : (1 << bits) - 1;
                    if (bits == 16) { v >>= 8; al >>= 8; } else if (bits < 8) { v = v * 255 / ((1 << bits) - 1); al = al * 255 / ((1 << bits) - 1); }
                    px = (al << 24) | (v << 16) | (v << 8) | v;
                } else px = im.getRGB(x * sw, y * sw);
                out[y * 16 + x] = px;
                if ((px >>> 24) > 100) { r += (px >> 16) & 255; g += (px >> 8) & 255; b += px & 255; n++; }
            }
            if (!fillClear) return out;
            int fill = n == 0 ? 0xFF404040 : 0xFF000000 | ((int) (r / n * 0.55) << 16) | ((int) (g / n * 0.55) << 8) | (int) (b / n * 0.55);
            for (int i = 0; i < 256; i++) if ((out[i] >>> 24) <= 100) out[i] = fill; else out[i] |= 0xFF000000;
            return out;
        } catch (Exception ex) {
            return null;
        }
    }
}
