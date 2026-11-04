package slatefinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns the game's block names into this app's blocks, and says what kind of block each is: air, water, natural ground
 * (what the surface is made of) or anything standing on it (trees, buildings, plants).
 */
final class ExactBlocks {
    private ExactBlocks() {}

    static final int AIR = 0, GROUND = 1, WATER = 2, STACK = 3;

    static final class Info {
        final byte id;
        final int kind;
        Info(byte id, int kind) { this.id = id; this.kind = kind; }
    }

    private static final Map<String, Info> CACHE = new ConcurrentHashMap<>();

    static Info of(String full) {
        String n = full == null ? "air" : full.startsWith("minecraft:") ? full.substring(10) : full;
        Info i = CACHE.get(n);
        if (i == null) { i = compute(n); CACHE.put(n, i); }
        return i;
    }

    // ------------------------------------------------------------------ what counts as ground

    private static final Set<String> GROUND_NAMES = new HashSet<>(java.util.Arrays.asList(
        "grass_block", "dirt", "coarse_dirt", "rooted_dirt", "dirt_path", "farmland", "podzol", "mycelium", "mud", "muddy_mangrove_roots", "packed_mud", "clay",
        "moss_block", "pale_moss_block", "sand", "red_sand", "suspicious_sand", "suspicious_gravel", "gravel", "stone", "deepslate", "granite", "diorite", "andesite",
        "tuff", "calcite", "dripstone_block", "sandstone", "red_sandstone", "terracotta", "snow_block", "snow", "powder_snow", "ice", "packed_ice", "blue_ice", "frosted_ice",
        "obsidian", "basalt", "smooth_basalt", "blackstone", "bedrock", "netherrack", "magma_block", "amethyst_block", "budding_amethyst", "sculk", "lava",
        "cobbled_deepslate", "cobblestone", "mossy_cobblestone", "infested_stone", "infested_deepslate", "raw_iron_block", "raw_copper_block", "raw_gold_block"));

    private static boolean ground(String n) {
        if (GROUND_NAMES.contains(n)) return true;
        if (n.endsWith("_ore") || n.startsWith("infested_")) return true;
        if (n.endsWith("_terracotta") && !n.contains("glazed")) return true;
        return false;
    }

    // ------------------------------------------------------------------ names to blocks

    private static final Map<String, Byte> DIRECT = new HashMap<>();
    private static void d(byte id, String... names) { for (String s : names) DIRECT.put(s, id); }

    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo", "crimson", "warped"};
    private static final String[] COLOURS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final int[] COLOUR_RGB = {0xF0EEE8, 0xE8841A, 0xC04CC0, 0x3AAFD9, 0xF0D020, 0x70B819, 0xF0A0B8, 0x474F52, 0x9D9D97, 0x158991, 0x7A2BAD, 0x35399D, 0x6A4A2A, 0x4A6A1F, 0xB02A2A, 0x1D1D21};
    private static final int[] PLANK_RGB = {0, 0, 0xC8B77A, 0xB88764, 0, 0, 0x765031, 0xE2B1A9, 0xE4DACD, 0xC8B25D, 0x6B3344, 0x2B6A60};

    static {
        d(Blocks3D.GRASS, "grass_block"); d(Blocks3D.DIRT, "dirt", "coarse_dirt", "rooted_dirt"); d(Blocks3D.PODZOL, "podzol"); d(Blocks3D.MYCELIUM, "mycelium");
        d(Blocks3D.PATH, "dirt_path"); d(Blocks3D.FARMLAND, "farmland"); d(Blocks3D.MUD, "mud", "muddy_mangrove_roots", "packed_mud", "mud_bricks");
        d(Blocks3D.SAND, "sand", "suspicious_sand"); d(Blocks3D.RED_SAND, "red_sand", "red_sandstone", "cut_red_sandstone", "smooth_red_sandstone", "chiseled_red_sandstone");
        d(Blocks3D.GRAVEL, "gravel", "suspicious_gravel");
        d(Blocks3D.STONE, "stone", "granite", "diorite", "andesite", "polished_granite", "polished_diorite", "polished_andesite", "smooth_stone", "infested_stone", "stone_slab", "smooth_stone_slab", "grindstone", "stonecutter");
        d(Blocks3D.COBBLE, "cobblestone", "furnace", "blast_furnace", "smoker", "dispenser", "dropper", "observer", "piston", "sticky_piston");
        d(Blocks3D.MOSSY_COBBLE, "mossy_cobblestone"); d(Blocks3D.STONE_BRICK, "stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks");
        d(Blocks3D.MOSSY_BRICK, "mossy_stone_bricks"); d(Blocks3D.BRICK, "bricks");
        d(Blocks3D.SANDSTONE, "sandstone", "chiseled_sandstone", "smooth_sandstone"); d(Blocks3D.CUT_SANDSTONE, "cut_sandstone");
        d(Blocks3D.SNOW, "snow"); d(Blocks3D.SNOW_BLOCK, "snow_block", "powder_snow"); d(Blocks3D.ICE, "ice", "frosted_ice"); d(Blocks3D.PACKED_ICE, "packed_ice", "blue_ice");
        d(Blocks3D.MOSS, "moss_block", "moss_carpet", "vine", "cave_vines", "cave_vines_plant", "glow_lichen", "big_dripleaf", "big_dripleaf_stem", "small_dripleaf", "hanging_roots", "spore_blossom", "azalea", "flowering_azalea");
        d(Blocks3D.DEEPSLATE, "deepslate", "cobbled_deepslate", "polished_deepslate", "chiseled_deepslate"); d(Blocks3D.DEEPSLATE_BRICK, "deepslate_bricks", "deepslate_tiles", "cracked_deepslate_bricks", "cracked_deepslate_tiles");
        d(Blocks3D.OBSIDIAN, "obsidian", "crying_obsidian"); d(Blocks3D.GLASS, "glass", "glass_pane", "tinted_glass"); d(Blocks3D.SEA_LANTERN, "sea_lantern");
        d(Blocks3D.PRISMARINE, "prismarine", "prismarine_bricks", "dark_prismarine"); d(Blocks3D.HAY, "hay_block"); d(Blocks3D.PUMPKIN, "pumpkin", "carved_pumpkin", "jack_o_lantern", "melon");
        d(Blocks3D.CHEST, "chest", "trapped_chest", "barrel", "ender_chest"); d(Blocks3D.SPAWNER, "spawner"); d(Blocks3D.SCULK, "sculk", "sculk_vein", "sculk_catalyst", "sculk_sensor", "sculk_shrieker");
        d(Blocks3D.BONE, "bone_block"); d(Blocks3D.GOLD, "gold_block", "bell", "raw_gold_block"); d(Blocks3D.CACTUS, "cactus"); d(Blocks3D.BAMBOO, "bamboo", "bamboo_sapling", "sugar_cane");
        d(Blocks3D.LANTERN, "torch", "wall_torch", "soul_torch", "soul_wall_torch", "lantern", "soul_lantern", "campfire", "soul_campfire", "glowstone", "shroomlight", "redstone_lamp", "candle");
        d(Blocks3D.RED_MUSH, "red_mushroom_block"); d(Blocks3D.BROWN_MUSH, "brown_mushroom_block"); d(Blocks3D.MUSH_STEM, "mushroom_stem");
        d(Blocks3D.WHEAT, "wheat", "carrots", "potatoes", "beetroots", "melon_stem", "pumpkin_stem", "attached_melon_stem", "attached_pumpkin_stem", "torchflower_crop", "pitcher_crop");
        d(Blocks3D.SWEET_BERRY, "sweet_berry_bush"); d(Blocks3D.WATER_LILY, "lily_pad"); d(Blocks3D.KELP, "kelp", "kelp_plant", "sea_pickle");
        d(Blocks3D.CORAL, "tube_coral_block", "brain_coral_block", "bubble_coral_block", "fire_coral_block", "horn_coral_block", "tube_coral", "brain_coral", "bubble_coral", "fire_coral", "horn_coral",
            "tube_coral_fan", "brain_coral_fan", "bubble_coral_fan", "fire_coral_fan", "horn_coral_fan", "tube_coral_wall_fan", "brain_coral_wall_fan", "bubble_coral_wall_fan", "fire_coral_wall_fan", "horn_coral_wall_fan",
            "dead_tube_coral_block", "dead_brain_coral_block", "dead_bubble_coral_block", "dead_fire_coral_block", "dead_horn_coral_block");
        d(Blocks3D.GRASS_TALL, "short_grass", "grass", "tall_grass", "fern", "large_fern", "seagrass", "tall_seagrass", "bush", "firefly_bush", "leaf_litter", "wildflowers");
        d(Blocks3D.FLOWER_RED, "poppy", "red_tulip", "orange_tulip", "rose_bush", "azure_bluet", "oxeye_daisy", "cornflower", "blue_orchid", "lily_of_the_valley", "wither_rose", "red_mushroom", "torchflower", "pitcher_plant", "closed_eyeblossom", "open_eyeblossom", "white_tulip");
        d(Blocks3D.FLOWER_YELLOW, "dandelion", "sunflower"); d(Blocks3D.FLOWER_PINK, "pink_tulip", "allium", "peony", "lilac");
        d(Blocks3D.PINK_PETALS, "pink_petals"); d(Blocks3D.DEAD_BUSH, "dead_bush", "brown_mushroom", "dry_grass", "short_dry_grass", "tall_dry_grass");
        d(Blocks3D.OAK_LEAVES, "azalea_leaves", "oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling", "cherry_sapling", "mangrove_propagule", "pale_oak_sapling");
        d(Blocks3D.LEAF_FLOWER, "flowering_azalea_leaves");
        d(Blocks3D.PLANKS, "oak_planks", "crafting_table", "fletching_table", "smithing_table", "cartography_table", "loom", "composter", "lectern", "bookshelf", "chiseled_bookshelf", "jukebox", "note_block", "ladder", "scaffolding", "beehive", "bee_nest");
        d(Blocks3D.SPRUCE_PLANKS, "spruce_planks"); d(Blocks3D.ACACIA_PLANKS, "acacia_planks"); d(Blocks3D.DARK_PLANKS, "dark_oak_planks");
        byte[] logs = {Blocks3D.OAK_LOG, Blocks3D.SPRUCE_LOG, Blocks3D.BIRCH_LOG, Blocks3D.JUNGLE_LOG, Blocks3D.ACACIA_LOG, Blocks3D.DARK_LOG, Blocks3D.MANGROVE_LOG, Blocks3D.CHERRY_LOG, Blocks3D.PALE_LOG, Blocks3D.BAMBOO, Blocks3D.MANGROVE_LOG, Blocks3D.CHERRY_LOG};
        byte[] leaves = {Blocks3D.OAK_LEAVES, Blocks3D.SPRUCE_LEAVES, Blocks3D.BIRCH_LEAVES, Blocks3D.JUNGLE_LEAVES, Blocks3D.ACACIA_LEAVES, Blocks3D.DARK_LEAVES, Blocks3D.MANGROVE_LEAVES, Blocks3D.CHERRY_LEAVES, Blocks3D.PALE_LEAVES, Blocks3D.OAK_LEAVES, Blocks3D.OAK_LEAVES, Blocks3D.OAK_LEAVES};
        for (int i = 0; i < WOODS.length; i++) {
            String w = WOODS[i];
            d(logs[i], w + "_log", w + "_wood", "stripped_" + w + "_log", "stripped_" + w + "_wood", w + "_stem", w + "_hyphae");
            d(leaves[i], w + "_leaves");
        }
        d(Blocks3D.WHITE_WOOL, "white_wool", "white_carpet", "white_concrete", "white_concrete_powder", "white_bed", "white_banner", "white_wall_banner", "white_candle", "white_shulker_box");
        d(Blocks3D.TERRACOTTA_WHITE, "white_terracotta");
    }

    private static final String[] SUFFIXES = {"_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_trapdoor", "_door", "_wall_hanging_sign", "_hanging_sign", "_wall_sign", "_sign", "_pane",
        "_carpet", "_wall_banner", "_banner", "_bed", "_candle", "_shulker_box", "_concrete_powder", "_concrete", "_wool", "_stained_glass", "_stained_glass_pane", "_glazed_terracotta", "_head", "_wall_head"};

    private static Info compute(String n) {
        if (n.equals("air") || n.equals("cave_air") || n.equals("void_air") || n.equals("light") || n.equals("structure_void") || n.equals("barrier") || n.equals("jigsaw") || n.equals("structure_block")) return new Info(Blocks3D.AIR, AIR);
        if (n.equals("water") || n.equals("bubble_column")) return new Info(Blocks3D.WATER, WATER);
        int kind = ground(n) ? GROUND : STACK;
        return new Info(idFor(n), kind);
    }

    /** The app's block for a game block name. Names it does not know become a plainly coloured block of their own. */
    static byte idFor(String n) {
        Byte b = DIRECT.get(n);
        if (b != null) return b;
        if (n.endsWith("_terracotta") && !n.contains("glazed")) return (byte) (Blocks3D.TERRA + terraBand(n.substring(0, n.length() - 11)));
        if (n.equals("terracotta")) return (byte) (Blocks3D.TERRA + 3);
        String base = n;
        for (String s : SUFFIXES) if (n.endsWith(s) && n.length() > s.length()) { base = n.substring(0, n.length() - s.length()); break; }
        if (!base.equals(n)) {
            // wooden things are planks of their wood; stone things are the stone they are made of
            for (String w : WOODS) if (base.equals(w)) { b = DIRECT.get(w + "_planks"); return b != null ? b : dyn(w + "_planks", PLANK_RGB[indexOf(WOODS, w)]); }
            for (String cand : new String[]{base, base + "s", base + "_planks", base + "_bricks", base + "_block"}) { b = DIRECT.get(cand); if (b != null) return b; }
            if (n.endsWith("_stained_glass") || n.endsWith("_stained_glass_pane")) return Blocks3D.GLASS;
            for (int i = 0; i < COLOURS.length; i++) if (base.equals(COLOURS[i])) return colourBlock(i, "_wool");
            for (int i = 0; i < COLOURS.length; i++) if (n.startsWith(COLOURS[i] + "_")) return colourBlock(i, n.endsWith("_concrete") || n.endsWith("_concrete_powder") ? "_concrete" : "_wool");
        }
        for (String w : WOODS) if (n.equals(w + "_planks")) return dyn(n, PLANK_RGB[indexOf(WOODS, w)]);
        for (int i = 0; i < COLOURS.length; i++) {
            if (n.equals(COLOURS[i] + "_wool") || n.equals(COLOURS[i] + "_concrete") || n.equals(COLOURS[i] + "_concrete_powder")) return colourBlock(i, n.endsWith("wool") ? "_wool" : "_concrete");
        }
        if (n.startsWith("potted_")) return Blocks3D.FLOWER_RED;
        if (n.contains("sapling")) return Blocks3D.OAK_LEAVES;
        if (n.contains("coral")) return Blocks3D.CORAL;
        if (n.endsWith("_ore")) return Blocks3D.STONE;
        if (n.startsWith("infested_")) return Blocks3D.STONE;
        if (n.startsWith("polished_") || n.startsWith("smooth_") || n.startsWith("chiseled_") || n.startsWith("cracked_")) {
            String rest = n.substring(n.indexOf('_') + 1);
            Byte r = DIRECT.get(rest);
            if (r != null) return r;
        }
        return dyn(n, guess(n));
    }

    private static int indexOf(String[] a, String s) { for (int i = 0; i < a.length; i++) if (a[i].equals(s)) return i; return 0; }

    private static byte dyn(String name, int rgb) {
        int id = Blocks3D.register(name, rgb);
        return (byte) (id < 0 ? Blocks3D.STONE : id);
    }

    private static int terraBand(String color) {
        switch (color) {
            case "orange": return 0; case "red": return 1; case "yellow": return 2; case "white": return 4; case "brown": return 5; case "light_gray": return 6;
            default: return 7;
        }
    }

    private static byte colourBlock(int c, String kind) {
        String nm = COLOURS[c] + kind;
        Byte b = DIRECT.get(COLOURS[c] + "_wool");
        if (kind.equals("_wool") && b != null) return b;
        switch (COLOURS[c]) {
            case "orange": if (kind.equals("_wool")) return Blocks3D.ORANGE_WOOL; break;
            case "pink": if (kind.equals("_wool")) return Blocks3D.PINK_WOOL; break;
            case "gray": case "light_gray": if (kind.equals("_wool")) return Blocks3D.GRAY_WOOL; break;
            case "red": if (kind.equals("_wool")) return Blocks3D.RED_WOOL; break;
            case "black": if (kind.equals("_wool")) return Blocks3D.BLACK_WOOL; break;
            case "brown": if (kind.equals("_wool")) return Blocks3D.BROWN_WOOL; break;
            default:
        }
        return dyn(nm, COLOUR_RGB[c]);
    }

    /** A plain colour for a block this table has never heard of, from words in its name. */
    static int guess(String n) {
        n = n.toLowerCase(Locale.ROOT);
        for (int i = 0; i < COLOURS.length; i++) if (n.startsWith(COLOURS[i] + "_")) return COLOUR_RGB[i];
        String[][] words = {
            {"exposed_copper", "A1766A"}, {"weathered_copper", "6C9F83"}, {"oxidized_copper", "52A58C"}, {"copper", "C06B4E"}, {"iron", "D8D8D8"}, {"gold", "F8D840"}, {"diamond", "62EDE4"}, {"emerald", "17DD62"},
            {"lapis", "1F43A8"}, {"redstone", "AA0F01"}, {"coal", "202020"}, {"quartz", "EBE6DC"}, {"amethyst", "8F6BC5"}, {"nether_brick", "2E1A1E"}, {"netherrack", "6E3535"}, {"blackstone", "2A252C"},
            {"basalt", "4B4B50"}, {"tuff", "6C6D67"}, {"calcite", "E0E0DA"}, {"dripstone", "866B5C"}, {"bedrock", "555555"}, {"magma", "8F3B14"}, {"lava", "E86A12"}, {"clay", "A0A6B6"},
            {"glass", "B8DCF0"}, {"leaves", "3F8A2E"}, {"planks", "9C7F4E"}, {"log", "6B5230"}, {"wood", "6B5230"}, {"brick", "8E5A4C"}, {"sand", "E0D3A0"}, {"dirt", "866043"}, {"grass", "5E9A38"},
            {"moss", "5E7A2E"}, {"snow", "F4F8FA"}, {"ice", "A8CCF0"}, {"wool", "E8E8E0"}, {"web", "E4E4E4"}, {"rail", "8B7B6B"}, {"stone", "808080"}, {"slate", "4A4E58"}, {"sponge", "C8C040"}, {"honey", "E8A818"},
        };
        for (String[] w : words) if (n.contains(w[0])) return Integer.parseInt(w[1], 16);
        int h = n.hashCode();
        return 0x707070 | ((h & 0x3F) << 16) | (((h >> 6) & 0x3F) << 8) | ((h >> 12) & 0x3F);
    }

    /** Names of the game's pictures a block may use, best guess first: {top, side}. */
    static List<String[]> textureCandidates(String n) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{n, n});
        out.add(new String[]{n + "_top", n + "_side"});
        out.add(new String[]{n + "_top", n});
        if (n.endsWith("_planks")) out.add(new String[]{n, n});
        if (n.endsWith("_wool")) out.add(new String[]{n, n});
        if (n.endsWith("_concrete")) out.add(new String[]{n, n});
        for (String suf : new String[]{"_bricks", "_brick"}) if (n.endsWith(suf)) out.add(new String[]{n.replace("_brick", "_bricks"), n.replace("_brick", "_bricks")});
        return out;
    }

    static String[] textureNames(String n) { return textureCandidates(n).get(0); }
}
