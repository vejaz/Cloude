package slatefinder;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Block models of animals, village buildings and the other structures. They are illustrations, built to look right. */
final class Models {
    private Models() {}

    enum Style { PLAINS, DESERT, SAVANNA, SNOWY, TAIGA }

    private static final Map<String, Stamp> CACHE = new HashMap<>();

    private static synchronized Stamp cached(String key, java.util.function.Supplier<Stamp> make) {
        Stamp s = CACHE.get(key);
        if (s == null) { s = make.get(); CACHE.put(key, s); }
        return s;
    }

    // ------------------------------------------------------------------ animals (they face +x)

    static Stamp animal(String kind) { return cached("animal:" + kind, () -> makeAnimal(kind)); }

    private static void legs(Stamp s, int x0, int x1, int z0, int z1, int h, byte c) {
        s.box(x0, 0, z0, x0, h - 1, z0, c); s.box(x0, 0, z1, x0, h - 1, z1, c);
        s.box(x1, 0, z0, x1, h - 1, z0, c); s.box(x1, 0, z1, x1, h - 1, z1, c);
    }

    private static Stamp makeAnimal(String k) {
        switch (k) {
            case "cow": {
                Stamp s = new Stamp(7, 3, 4, 3, 1);
                legs(s, 1, 4, 0, 2, 1, Blocks3D.BROWN_WOOL);
                s.box(0, 1, 0, 5, 2, 2, Blocks3D.BROWN_WOOL);
                s.box(1, 2, 0, 2, 2, 1, Blocks3D.WHITE_WOOL); s.box(4, 1, 1, 5, 2, 2, Blocks3D.WHITE_WOOL);
                s.box(6, 2, 1, 6, 3, 1, Blocks3D.BROWN_WOOL); s.set(6, 3, 0, Blocks3D.WHITE_WOOL); s.set(6, 3, 2, Blocks3D.WHITE_WOOL);
                return s;
            }
            case "mooshroom": {
                Stamp s = new Stamp(7, 3, 6, 3, 1);
                legs(s, 1, 4, 0, 2, 1, Blocks3D.RED_WOOL);
                s.box(0, 1, 0, 5, 2, 2, Blocks3D.RED_WOOL);
                s.box(1, 2, 0, 2, 2, 1, Blocks3D.WHITE_WOOL);
                s.box(6, 2, 1, 6, 3, 1, Blocks3D.RED_WOOL);
                s.box(2, 3, 1, 3, 4, 1, Blocks3D.MUSH_STEM); s.box(1, 5, 0, 4, 5, 2, Blocks3D.RED_MUSH);
                return s;
            }
            case "sheep": {
                Stamp s = new Stamp(6, 3, 4, 2, 1);
                legs(s, 1, 3, 0, 2, 1, Blocks3D.GRAY_WOOL);
                s.box(0, 1, 0, 4, 2, 2, Blocks3D.WHITE_WOOL);
                s.box(5, 2, 1, 5, 3, 1, Blocks3D.GRAY_WOOL);
                return s;
            }
            case "llama": {
                Stamp s = new Stamp(7, 3, 7, 3, 1);
                legs(s, 1, 4, 0, 2, 2, Blocks3D.WHITE_WOOL);
                s.box(0, 2, 0, 5, 3, 2, Blocks3D.WHITE_WOOL);
                s.box(6, 3, 1, 6, 6, 1, Blocks3D.WHITE_WOOL); s.set(6, 6, 0, Blocks3D.WHITE_WOOL); s.set(6, 6, 2, Blocks3D.WHITE_WOOL);
                s.box(2, 3, 0, 3, 3, 2, Blocks3D.RED_WOOL);
                return s;
            }
            case "camel": {
                Stamp s = new Stamp(9, 3, 9, 4, 1);
                legs(s, 1, 6, 0, 2, 3, Blocks3D.ORANGE_WOOL);
                s.box(0, 3, 0, 7, 4, 2, Blocks3D.ORANGE_WOOL);
                s.box(3, 5, 1, 4, 5, 1, Blocks3D.ORANGE_WOOL);
                s.box(8, 4, 1, 8, 7, 1, Blocks3D.ORANGE_WOOL); s.set(8, 8, 1, Blocks3D.ORANGE_WOOL);
                s.box(2, 4, 0, 5, 4, 2, Blocks3D.RED_WOOL);
                return s;
            }
            case "goat": {
                Stamp s = new Stamp(5, 2, 5, 2, 0);
                legs(s, 1, 3, 0, 1, 1, Blocks3D.WHITE_WOOL);
                s.box(0, 1, 0, 3, 2, 1, Blocks3D.WHITE_WOOL);
                s.box(4, 2, 0, 4, 3, 1, Blocks3D.WHITE_WOOL); s.set(4, 4, 0, Blocks3D.GRAY_WOOL);
                return s;
            }
            case "fox": {
                Stamp s = new Stamp(6, 2, 3, 2, 0);
                legs(s, 1, 3, 0, 1, 1, Blocks3D.BROWN_WOOL);
                s.box(0, 1, 0, 3, 1, 1, Blocks3D.ORANGE_WOOL); s.box(4, 1, 0, 4, 2, 1, Blocks3D.ORANGE_WOOL);
                s.set(-0, 2, 0, Blocks3D.WHITE_WOOL);
                return s;
            }
            case "wolf": {
                Stamp s = new Stamp(6, 2, 3, 2, 0);
                legs(s, 1, 3, 0, 1, 1, Blocks3D.GRAY_WOOL);
                s.box(0, 1, 0, 3, 1, 1, Blocks3D.GRAY_WOOL); s.box(4, 1, 0, 4, 2, 1, Blocks3D.WHITE_WOOL);
                return s;
            }
            case "polarbear": {
                Stamp s = new Stamp(8, 4, 5, 3, 1);
                legs(s, 1, 5, 0, 3, 2, Blocks3D.WHITE_WOOL);
                s.box(0, 2, 0, 6, 3, 3, Blocks3D.WHITE_WOOL);
                s.box(7, 3, 1, 7, 4, 2, Blocks3D.WHITE_WOOL);
                return s;
            }
            case "panda": {
                Stamp s = new Stamp(7, 4, 5, 3, 1);
                legs(s, 1, 4, 0, 3, 2, Blocks3D.BLACK_WOOL);
                s.box(0, 2, 0, 5, 3, 3, Blocks3D.WHITE_WOOL);
                s.box(1, 2, 0, 1, 3, 3, Blocks3D.BLACK_WOOL);
                s.box(6, 3, 1, 6, 4, 2, Blocks3D.WHITE_WOOL); s.set(6, 4, 1, Blocks3D.BLACK_WOOL);
                return s;
            }
            case "snowgolem": {
                Stamp s = new Stamp(3, 3, 6, 1, 1);
                s.box(0, 0, 0, 2, 1, 2, Blocks3D.SNOW); s.box(0, 2, 0, 2, 3, 2, Blocks3D.SNOW);
                s.box(1, 4, 1, 1, 4, 1, Blocks3D.SNOW); s.set(1, 5, 1, Blocks3D.PUMPKIN);
                return s;
            }
            case "creaking": {
                Stamp s = new Stamp(3, 3, 8, 1, 1);
                s.box(0, 0, 0, 0, 3, 0, Blocks3D.GRAY_WOOL); s.box(2, 0, 2, 2, 3, 2, Blocks3D.GRAY_WOOL);
                s.box(0, 4, 0, 2, 6, 2, Blocks3D.GRAY_WOOL);
                s.box(1, 7, 1, 1, 7, 1, Blocks3D.GRAY_WOOL); s.set(2, 6, 1, Blocks3D.ORANGE_WOOL);
                return s;
            }
            case "armadillo": {
                Stamp s = new Stamp(5, 3, 3, 2, 1);
                s.box(0, 0, 0, 3, 1, 2, Blocks3D.BROWN_WOOL); s.box(0, 1, 0, 3, 1, 2, Blocks3D.GRAY_WOOL); s.box(4, 0, 1, 4, 0, 1, Blocks3D.BROWN_WOOL);
                return s;
            }
            case "frog": {
                Stamp s = new Stamp(3, 3, 2, 1, 1);
                s.box(0, 0, 0, 2, 0, 2, Blocks3D.MOSS); s.set(2, 1, 0, Blocks3D.WHITE_WOOL); s.set(2, 1, 2, Blocks3D.WHITE_WOOL);
                return s;
            }
            case "turtle": {
                Stamp s = new Stamp(5, 4, 3, 2, 1);
                s.box(0, 0, 0, 3, 0, 3, Blocks3D.JUNGLE_LEAVES); s.box(1, 1, 1, 2, 1, 2, Blocks3D.MOSS);
                s.set(4, 0, 1, Blocks3D.MOSS);
                return s;
            }
            case "rabbit": {
                Stamp s = new Stamp(3, 2, 3, 1, 0);
                s.box(0, 0, 0, 1, 0, 1, Blocks3D.WHITE_WOOL); s.box(2, 1, 0, 2, 2, 0, Blocks3D.WHITE_WOOL); s.set(2, 1, 1, Blocks3D.WHITE_WOOL);
                return s;
            }
            case "pig": {
                Stamp s = new Stamp(6, 3, 3, 2, 1);
                legs(s, 1, 4, 0, 2, 1, Blocks3D.PINK_WOOL);
                s.box(0, 1, 0, 4, 1, 2, Blocks3D.PINK_WOOL); s.box(5, 1, 1, 5, 2, 1, Blocks3D.PINK_WOOL);
                return s;
            }
            default: return new Stamp(1, 1, 1, 0, 0);
        }
    }

    // ------------------------------------------------------------------ village buildings

    static Stamp[] houses(Style st) {
        return new Stamp[]{house(st, 5, 7), house(st, 7, 7), house(st, 5, 5), house(st, 9, 7), tower(st), house(st, 7, 9)};
    }

    private static byte wall(Style s) { switch (s) { case DESERT: return Blocks3D.CUT_SANDSTONE; case SAVANNA: return Blocks3D.ACACIA_PLANKS; case SNOWY: case TAIGA: return Blocks3D.SPRUCE_PLANKS; default: return Blocks3D.PLANKS; } }
    private static byte log(Style s) { switch (s) { case DESERT: return Blocks3D.SANDSTONE; case SAVANNA: return Blocks3D.ACACIA_LOG; case SNOWY: case TAIGA: return Blocks3D.SPRUCE_LOG; default: return Blocks3D.OAK_LOG; } }
    private static byte roof(Style s) { switch (s) { case DESERT: return Blocks3D.SANDSTONE; case SAVANNA: return Blocks3D.ACACIA_PLANKS; case SNOWY: return Blocks3D.SNOW; case TAIGA: return Blocks3D.DARK_PLANKS; default: return Blocks3D.COBBLE; } }

    static Stamp house(Style st, int w, int d) {
        return cached("house:" + st + w + "x" + d, () -> buildHouse(st, w, d, 4));
    }

    static Stamp tower(Style st) { return cached("tower:" + st, () -> buildHouse(st, 5, 5, 8)); }

    private static Stamp buildHouse(Style st, int w, int d, int H) {
        Stamp s = new Stamp(w, d, H + w / 2 + 3, w / 2, d / 2);
        byte wallB = wall(st), logB = log(st), roofB = roof(st);
        s.box(0, 0, 0, w - 1, 0, d - 1, st == Style.DESERT ? Blocks3D.CUT_SANDSTONE : Blocks3D.COBBLE);
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
            boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
            boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
            for (int y = 1; y <= H; y++) {
                byte b = edge ? (corner ? logB : wallB) : wallB;
                if (edge && !corner && y >= 2 && y <= 3 && ((x + z) % 3 == 0) && H == 4) b = Blocks3D.GLASS;
                if (edge && !corner && H > 4 && y >= 2 && y <= 3 && ((x + z) % 2 == 0)) b = Blocks3D.GLASS;
                if (H > 4 && edge && !corner && y >= 5 && y <= 6 && ((x + z) % 2 == 1)) b = Blocks3D.GLASS;
                if (z == 0 && x == w / 2 && y <= 2) b = Blocks3D.DARK_PLANKS;
                s.set(x, y, z, b);
            }
        }
        if (st == Style.DESERT) {
            s.box(0, H + 1, 0, w - 1, H + 1, d - 1, roofB);
            for (int x = 0; x < w; x++) { s.set(x, H + 2, 0, Blocks3D.CUT_SANDSTONE); s.set(x, H + 2, d - 1, Blocks3D.CUT_SANDSTONE); }
            for (int z = 0; z < d; z++) { s.set(0, H + 2, z, Blocks3D.CUT_SANDSTONE); s.set(w - 1, H + 2, z, Blocks3D.CUT_SANDSTONE); }
        } else {
            for (int x = 0; x < w; x++) {
                int rise = Math.min(x, w - 1 - x);
                for (int z = 0; z < d; z++) {
                    for (int y = H + 1; y <= H + 1 + rise; y++) s.set(x, y, z, (z == 0 || z == d - 1) && y <= H + rise ? wallB : roofB);
                }
            }
        }
        return s;
    }

    static Stamp well(Style st) {
        return cached("well:" + st, () -> {
            Stamp s = new Stamp(5, 5, 7, 2, 2);
            byte base = st == Style.DESERT ? Blocks3D.CUT_SANDSTONE : Blocks3D.COBBLE;
            s.box(0, 0, 0, 4, 1, 4, base);
            s.box(1, 1, 1, 3, 1, 3, Blocks3D.WATER);
            for (int[] c : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) s.box(c[0], 2, c[1], c[0], 4, c[1], log(st));
            s.box(0, 5, 0, 4, 5, 4, roof(st) == Blocks3D.SNOW ? Blocks3D.SPRUCE_PLANKS : roof(st));
            return s;
        });
    }

    static Stamp farm() {
        return cached("farm", () -> {
            Stamp s = new Stamp(9, 9, 2, 4, 4);
            for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) {
                if (x == 4) s.set(x, 0, z, Blocks3D.WATER);
                else { s.set(x, 0, z, Blocks3D.FARMLAND); s.set(x, 1, z, z % 2 == 0 ? Blocks3D.WHEAT : Blocks3D.GRASS_TALL); }
            }
            s.box(0, 0, 0, 8, 0, 0, Blocks3D.OAK_LOG); s.box(0, 0, 8, 8, 0, 8, Blocks3D.OAK_LOG);
            return s;
        });
    }

    // ------------------------------------------------------------------ other structures

    static Stamp pyramid() {
        return cached("pyramid", () -> {
            int R = 20;
            Stamp s = new Stamp(41, 41, 25, 20, 20);
            for (int x = 0; x < 41; x++) for (int z = 0; z < 41; z++) {
                int d = Math.max(Math.abs(x - R), Math.abs(z - R));
                int hgt = R - d;
                for (int y = 0; y <= hgt; y++) {
                    byte b = Blocks3D.SANDSTONE;
                    if (y == 3 || y == 4 || y == 11 || y == 12) b = Blocks3D.ORANGE_WOOL;
                    if (y == 8) b = Blocks3D.CUT_SANDSTONE;
                    if (y == hgt && y > 15) b = Blocks3D.CUT_SANDSTONE;
                    s.set(x, y, z, b);
                }
            }
            for (int[] c : new int[][]{{3, 3}, {37, 3}, {3, 37}, {37, 37}}) for (int y = 0; y < 9; y++) { s.box(c[0] - 1, y, c[1] - 1, c[0] + 1, y, c[1] + 1, y > 6 ? Blocks3D.ORANGE_WOOL : Blocks3D.CUT_SANDSTONE); }
            return s;
        });
    }

    static Stamp jungleTemple() {
        return cached("jungletemple", () -> {
            Stamp s = new Stamp(17, 14, 13, 8, 7);
            Random r = new Random(5);
            int[][] tiers = {{17, 14, 0, 5}, {13, 10, 5, 9}, {9, 6, 9, 12}};
            for (int[] t : tiers) {
                int ox = (17 - t[0]) / 2, oz = (14 - t[1]) / 2;
                for (int x = 0; x < t[0]; x++) for (int z = 0; z < t[1]; z++) for (int y = t[2]; y < t[3]; y++) s.set(ox + x, y, oz + z, r.nextInt(3) == 0 ? Blocks3D.COBBLE : Blocks3D.MOSSY_COBBLE);
            }
            for (int k = 0; k < 18; k++) { int x = r.nextInt(17), z = r.nextInt(14); int top = s.top(x, z); if (top >= 0 && top < 12) s.set(x, top + 1, z, Blocks3D.JUNGLE_LEAVES); }
            return s;
        });
    }

    static Stamp igloo() {
        return cached("igloo", () -> {
            Stamp s = new Stamp(11, 9, 6, 4, 4);
            for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) {
                double r = Math.sqrt((x - 4) * (x - 4) + (z - 4) * (z - 4));
                if (r <= 4.2) { int hgt = (int) Math.round(Math.sqrt(Math.max(0, 17.6 - r * r))) ; for (int y = 0; y < Math.min(5, hgt); y++) s.set(x, y, z, Blocks3D.SNOW_BRICK); }
            }
            s.box(9, 0, 3, 10, 1, 5, Blocks3D.SNOW_BRICK); s.set(10, 0, 4, Blocks3D.AIR);
            s.box(2, 1, 4, 2, 1, 4, Blocks3D.GLASS);
            return s;
        });
    }

    static Stamp swampHut() {
        return cached("swamphut", () -> {
            Stamp s = new Stamp(9, 11, 12, 4, 5);
            s.box(0, 0, 0, 8, 3, 10, Blocks3D.DARK_LOG);
            s.box(1, 4, 1, 7, 4, 9, Blocks3D.SPRUCE_PLANKS);
            for (int x = 1; x <= 7; x++) for (int z = 1; z <= 9; z++) {
                boolean edge = x == 1 || z == 1 || x == 7 || z == 9;
                if (!edge) continue;
                for (int y = 5; y <= 7; y++) s.set(x, y, z, (y == 6 && (x + z) % 3 == 0 && x != 1 && x != 7 && z != 1 && z != 9) ? Blocks3D.GLASS : Blocks3D.SPRUCE_PLANKS);
            }
            s.box(2, 5, 2, 6, 7, 8, Blocks3D.SPRUCE_PLANKS);
            for (int x = 0; x < 9; x++) { int rise = Math.min(x, 8 - x); for (int z = 0; z < 11; z++) for (int y = 8; y <= 8 + rise; y++) s.set(x, y, z, Blocks3D.DARK_PLANKS); }
            s.box(6, 8, 8, 6, 12, 8, Blocks3D.COBBLE);
            return s;
        });
    }

    static Stamp outpost() {
        return cached("outpost", () -> {
            Stamp s = new Stamp(11, 11, 24, 5, 5);
            s.box(2, 0, 2, 8, 3, 8, Blocks3D.COBBLE);
            s.box(2, 4, 2, 8, 16, 8, Blocks3D.DARK_PLANKS);
            for (int y = 7; y <= 9; y++) { s.set(2, y, 5, Blocks3D.AIR); }
            s.box(1, 17, 1, 9, 17, 9, Blocks3D.DARK_PLANKS);
            for (int x = 1; x <= 9; x++) for (int z = 1; z <= 9; z++) if ((x == 1 || z == 1 || x == 9 || z == 9) && (x + z) % 2 == 0) s.set(x, 18, z, Blocks3D.DARK_PLANKS);
            s.box(5, 18, 5, 5, 22, 5, Blocks3D.DARK_LOG); s.box(6, 20, 5, 6, 22, 5, Blocks3D.BANNER);
            s.box(2, 4, 4, 2, 5, 6, Blocks3D.GLASS);
            s.box(0, 0, 4, 1, 1, 6, Blocks3D.COBBLE);
            return s;
        });
    }

    static Stamp mansion() {
        return cached("mansion", () -> {
            int w = 27, d = 19;
            Stamp s = new Stamp(w, d, 24, 13, 9);
            s.box(0, 0, 0, w - 1, 5, d - 1, Blocks3D.COBBLE);
            s.box(0, 6, 0, w - 1, 11, d - 1, Blocks3D.DARK_PLANKS);
            for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (!edge) continue;
                boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                if (corner) { s.box(x, 0, z, x, 11, z, Blocks3D.DARK_LOG); continue; }
                for (int y : new int[]{2, 3, 8, 9}) if ((x + z) % 4 == 1) s.set(x, y, z, Blocks3D.GLASS);
            }
            s.box(w / 2 - 1, 0, 0, w / 2 + 1, 4, 0, Blocks3D.DARK_PLANKS);
            for (int x = 0; x < w; x++) { int rise = Math.min(x, w - 1 - x) / 2; for (int z = 0; z < d; z++) for (int y = 12; y <= 12 + Math.min(rise, 10); y++) s.set(x, y, z, (z == 0 || z == d - 1) && y < 12 + Math.min(rise, 10) ? Blocks3D.DARK_PLANKS : Blocks3D.DEEPSLATE); }
            return s;
        });
    }

    static Stamp monument() {
        return cached("monument", () -> {
            Stamp s = new Stamp(31, 31, 36, 15, 15);
            int[][] tiers = {{31, 0, 9}, {23, 9, 17}, {15, 17, 24}, {7, 24, 31}};
            Random r = new Random(11);
            for (int[] t : tiers) {
                int o = (31 - t[0]) / 2;
                for (int x = 0; x < t[0]; x++) for (int z = 0; z < t[0]; z++) for (int y = t[1]; y < t[2]; y++) s.set(o + x, y, o + z, (x + z + y) % 9 == 0 ? Blocks3D.SEA_LANTERN : r.nextInt(4) == 0 ? Blocks3D.PRISMARINE : Blocks3D.PRISMARINE);
            }
            for (int[] c : new int[][]{{1, 1}, {29, 1}, {1, 29}, {29, 29}}) s.box(c[0], 0, c[1], c[0], 14, c[1], Blocks3D.SEA_LANTERN);
            s.box(14, 31, 14, 16, 33, 16, Blocks3D.PRISMARINE); s.box(15, 34, 15, 15, 35, 15, Blocks3D.GOLD);
            return s;
        });
    }

    static Stamp oceanRuin(String kind) {
        return cached("ruin:" + kind, () -> {
            Stamp s = new Stamp(13, 13, 7, 6, 6);
            Random r = new Random(3);
            byte a = kind.equals("warm") ? Blocks3D.CUT_SANDSTONE : Blocks3D.STONE_BRICK, b = kind.equals("warm") ? Blocks3D.SANDSTONE : Blocks3D.MOSSY_BRICK;
            for (int x = 0; x < 13; x++) for (int z = 0; z < 13; z++) {
                boolean wall = x == 2 || x == 10 || z == 2 || z == 10;
                if (!wall || r.nextInt(5) == 0) continue;
                int hgt = 2 + r.nextInt(4);
                for (int y = 0; y < hgt; y++) s.set(x, y, z, r.nextInt(3) == 0 ? b : a);
            }
            s.box(5, 0, 5, 7, 0, 7, Blocks3D.GRAVEL); s.set(6, 1, 6, Blocks3D.CHEST);
            return s;
        });
    }

    static Stamp shipwreck(boolean beached) {
        return cached("ship:" + beached, () -> {
            Stamp s = new Stamp(27, 9, 14, 13, 4);
            for (int x = 0; x < 27; x++) {
                int half = x < 4 ? x / 2 + 1 : x > 22 ? (26 - x) / 2 + 1 : 4;
                for (int z = 4 - half; z <= 4 + half; z++) {
                    boolean side = z == 4 - half || z == 4 + half;
                    for (int y = 0; y < 4; y++) {
                        if (beached && x > 14 && y > 1 && (x + z) % 3 == 0) continue;
                        if (y == 3 && !side) s.set(x, y, z, Blocks3D.PLANKS);
                        else if (side || y == 0) s.set(x, y, z, y > 2 ? Blocks3D.DARK_PLANKS : Blocks3D.SPRUCE_PLANKS);
                    }
                }
            }
            s.box(12, 4, 4, 12, 12, 4, Blocks3D.DARK_LOG);
            for (int y = 7; y <= 11; y++) for (int z = 2; z <= 6; z++) if ((y + z) % 5 != 0) s.set(13, y, z, Blocks3D.WHITE_WOOL);
            s.set(8, 4, 4, Blocks3D.CHEST);
            return s;
        });
    }

    static Stamp treasure() {
        return cached("treasure", () -> {
            Stamp s = new Stamp(9, 9, 2, 4, 4);
            for (int i = 0; i < 9; i++) { s.set(i, 0, i, Blocks3D.RED_WOOL); s.set(i, 0, 8 - i, Blocks3D.RED_WOOL); }
            s.set(4, 1, 4, Blocks3D.CHEST);
            return s;
        });
    }

    static Stamp desertWell() {
        return cached("desertwell", () -> {
            Stamp s = new Stamp(5, 5, 6, 2, 2);
            s.box(0, 0, 0, 4, 1, 4, Blocks3D.CUT_SANDSTONE); s.box(1, 1, 1, 3, 1, 3, Blocks3D.WATER);
            for (int[] c : new int[][]{{0, 0}, {4, 0}, {0, 4}, {4, 4}}) s.box(c[0], 2, c[1], c[0], 3, c[1], Blocks3D.SANDSTONE);
            s.box(0, 4, 0, 4, 4, 4, Blocks3D.CUT_SANDSTONE);
            return s;
        });
    }

    static Stamp trailRuins() {
        return cached("trailruins", () -> {
            Stamp s = new Stamp(11, 11, 5, 5, 5);
            Random r = new Random(7);
            s.box(0, 0, 0, 10, 0, 10, Blocks3D.GRAVEL);
            for (int x = 0; x < 11; x++) for (int z = 0; z < 11; z++) {
                boolean wall = x == 1 || x == 9 || z == 1 || z == 9;
                if (wall && r.nextInt(3) > 0) for (int y = 1; y <= 1 + r.nextInt(3); y++) s.set(x, y, z, r.nextBoolean() ? Blocks3D.TERRACOTTA_BRICK : Blocks3D.TERRA);
            }
            return s;
        });
    }

    static Stamp fossil() {
        return cached("fossil", () -> {
            Stamp s = new Stamp(15, 9, 4, 7, 4);
            s.box(0, 0, 4, 14, 0, 4, Blocks3D.BONE);
            for (int x = 1; x < 14; x += 2) { s.box(x, 0, 1, x, 2, 1, Blocks3D.BONE); s.box(x, 0, 7, x, 2, 7, Blocks3D.BONE); s.set(x, 3, 2, Blocks3D.BONE); s.set(x, 3, 6, Blocks3D.BONE); }
            s.box(14, 0, 3, 14, 1, 5, Blocks3D.BONE);
            return s;
        });
    }

    /** The model for a structure kind, when there is one. */
    static Stamp stampFor(String kind) {
        switch (kind) {
            case "desert_pyramid": return pyramid();
            case "jungle_temple": return jungleTemple();
            case "igloo": return igloo();
            case "swamp_hut": return swampHut();
            case "pillager_outpost": return outpost();
            case "woodland_mansion": return mansion();
            case "ocean_monument": return monument();
            case "ocean_ruin": case "cold_ocean_ruin": return oceanRuin("cold");
            case "warm_ocean_ruin": return oceanRuin("warm");
            case "shipwreck": case "ocean_shipwreck": return shipwreck(false);
            case "beached_shipwreck": return shipwreck(true);
            case "buried_treasure": return treasure();
            case "desert_well": return desertWell();
            case "trail_ruins": return trailRuins();
            case "fossil": return fossil();
            case "ruined_portal": return ruinedPortal();
            default: return well(Style.PLAINS);
        }
    }

    /** A broken portal frame of obsidian, with crying obsidian and netherrack around it. */
    static Stamp ruinedPortal() {
        return cached("ruinedPortal", () -> {
            Stamp s = new Stamp(7, 5, 6, 3, 2);
            for (int x = 0; x < 7; x++) for (int z = 0; z < 5; z++) if ((x * 7 + z * 3) % 5 < 3) s.set(x, 0, z, Blocks3D.DEEPSLATE);
            s.box(2, 1, 2, 2, 4, 2, Blocks3D.OBSIDIAN); s.box(5, 1, 2, 5, 3, 2, Blocks3D.OBSIDIAN);
            s.box(3, 4, 2, 4, 4, 2, Blocks3D.OBSIDIAN); s.set(5, 4, 2, Blocks3D.SCULK);
            s.set(3, 1, 2, Blocks3D.OBSIDIAN); s.set(1, 1, 3, Blocks3D.SCULK); s.set(4, 1, 3, Blocks3D.SCULK); s.set(6, 1, 1, Blocks3D.GOLD);
            return s;
        });
    }

    /** A clump of coral on the sea floor. */
    static Stamp coral(int seed) {
        return cached("coral:" + seed, () -> {
            Random r = new Random(seed * 31L + 7);
            Stamp s = new Stamp(5, 5, 6, 2, 2);
            for (int k = 0; k < 7; k++) { int x = 1 + r.nextInt(3), z = 1 + r.nextInt(3), hgt = 1 + r.nextInt(5); s.box(x, 0, z, x, hgt - 1, z, Blocks3D.CORAL); }
            s.set(2, 0, 2, Blocks3D.CORAL);
            return s;
        });
    }

    static Stamp lamp() {
        return cached("lamp", () -> { Stamp s = new Stamp(1, 1, 5, 0, 0); s.box(0, 0, 0, 0, 3, 0, Blocks3D.OAK_LOG); s.set(0, 4, 0, Blocks3D.LANTERN); return s; });
    }
}
