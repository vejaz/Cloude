package slatefinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Where the game puts its structures, worked out from the world seed. Most structures are placed on a grid of regions:
 * each region of {@code spacing} x {@code spacing} chunks gets one chosen chunk, picked by a random generator started from the
 * seed, the region and a number (the salt) that is different for each kind of structure. The numbers below are the game's own,
 * read from Minecraft 1.21.11's data files (worldgen/structure_set), and the biomes each structure may start in come from the
 * same files (worldgen/structure and the has_structure biome tags). A chosen chunk only gets the structure if its biome is
 * one of those, which is checked against the biome map here (every 64 blocks), so the result is exact where the map is.
 */
final class StructureSeeds {
    private StructureSeeds() {}

    /** One structure set of the game. */
    static final class Spec {
        final String id;                 // this app's id of the structure
        final int spacing, separation, salt;
        final boolean triangular;
        final double frequency;
        final int reduction;             // 0 none, 1 legacy_type_1, 2 legacy_type_2, 3 legacy_type_3
        final String[] biomes;           // where it may start
        final String[] realIds;          // names the game gives its starts, to confirm them in chunk files
        final int offset;                // blocks added to the middle of the chunk (buried treasure)
        final String excludeNear;        // another set that must not have a start within 10 chunks
        Spec(String id, int spacing, int separation, int salt, boolean triangular, double frequency, int reduction, String[] biomes, String[] realIds, int offset, String excludeNear) {
            this.id = id; this.spacing = spacing; this.separation = separation; this.salt = salt; this.triangular = triangular;
            this.frequency = frequency; this.reduction = reduction; this.biomes = biomes; this.realIds = realIds; this.offset = offset; this.excludeNear = excludeNear;
        }
    }

    private static final Map<String, Spec> SPECS = new HashMap<>();

    private static String[] b(String s) { return s.split(","); }

    private static void add(String id, int spacing, int sep, int salt, boolean tri, double freq, int red, String biomes, String real, int off, String excl) {
        SPECS.put(id, new Spec(id, spacing, sep, salt, tri, freq, red, b(biomes), b(real), off, excl));
    }

    static {
        String ocean = "cold_ocean,deep_cold_ocean,deep_frozen_ocean,deep_lukewarm_ocean,deep_ocean,frozen_ocean,lukewarm_ocean,ocean,warm_ocean";
        add("village", 34, 8, 10387312, false, 1, 0, "meadow,plains,desert,savanna,snowy_plains,taiga,snowy_taiga", "minecraft:village", 0, null);
        add("desert_pyramid", 32, 8, 14357617, false, 1, 0, "desert", "minecraft:desert_pyramid", 0, null);
        add("igloo", 32, 8, 14357618, false, 1, 0, "snowy_plains,snowy_slopes,snowy_taiga", "minecraft:igloo", 0, null);
        add("jungle_temple", 32, 8, 14357619, false, 1, 0, "bamboo_jungle,jungle", "minecraft:jungle_pyramid", 0, null);
        add("swamp_hut", 32, 8, 14357620, false, 1, 0, "swamp", "minecraft:swamp_hut", 0, null);
        add("pillager_outpost", 32, 8, 165745296, false, 0.2, 1, "cherry_grove,desert,frozen_peaks,grove,jagged_peaks,meadow,plains,savanna,snowy_plains,snowy_slopes,stony_peaks,taiga", "minecraft:pillager_outpost", 0, "village");
        add("ocean_monument", 32, 5, 10387313, true, 1, 0, "deep_cold_ocean,deep_frozen_ocean,deep_lukewarm_ocean,deep_ocean", "minecraft:monument", 0, null);
        add("woodland_mansion", 80, 20, 10387319, true, 1, 0, "dark_forest,pale_garden", "minecraft:mansion", 0, null);
        add("ocean_ruin", 20, 8, 14357621, false, 1, 0, ocean, "minecraft:ocean_ruin_cold,minecraft:ocean_ruin_warm", 0, null);
        add("shipwreck", 24, 4, 165745295, false, 1, 0, ocean + ",beach,snowy_beach", "minecraft:shipwreck,minecraft:shipwreck_beached", 0, null);
        add("ruined_portal", 40, 15, 34222645, false, 1, 0,
            "beach,birch_forest,dark_forest,dripstone_caves,flower_forest,forest,frozen_river,grove,ice_spikes,lush_caves,mushroom_fields,old_growth_birch_forest,old_growth_pine_taiga,old_growth_spruce_taiga,pale_garden,plains,river,savanna,snowy_beach,snowy_plains,snowy_taiga,sunflower_plains,taiga,"
                + "desert,bamboo_jungle,jungle,sparse_jungle,mangrove_swamp,swamp,badlands,cherry_grove,eroded_badlands,frozen_peaks,jagged_peaks,meadow,savanna_plateau,snowy_slopes,stony_peaks,stony_shore,windswept_forest,windswept_gravelly_hills,windswept_hills,windswept_savanna,wooded_badlands," + ocean,
            "minecraft:ruined_portal,minecraft:ruined_portal_desert,minecraft:ruined_portal_jungle,minecraft:ruined_portal_swamp,minecraft:ruined_portal_mountain,minecraft:ruined_portal_ocean", 0, null);
        add("trail_ruins", 34, 8, 83469867, false, 1, 0, "jungle,old_growth_birch_forest,old_growth_pine_taiga,old_growth_spruce_taiga,snowy_taiga,taiga", "minecraft:trail_ruins", 0, null);
        add("buried_treasure", 1, 0, 0, false, 0.01, 2, "beach,snowy_beach", "minecraft:buried_treasure", 9, null);
    }

    /** Whether this structure's positions can be worked out from the seed. */
    static boolean supports(String id) { return SPECS.containsKey(id); }

    static Spec spec(String id) { return SPECS.get(id); }

    static java.util.Collection<Spec> all() { return SPECS.values(); }

    // ------------------------------------------------------------------ the game's own placement

    private static long regionSeed(long worldSeed, int rx, int rz, int salt) {
        return (long) rx * 341873128712L + (long) rz * 132897987541L + worldSeed + (long) salt;
    }

    /** The chunk of region (rx, rz) chosen for the structure, as {chunkX, chunkZ}. */
    static int[] chunkInRegion(Spec s, long seed, int rx, int rz) {
        Random r = new Random(regionSeed(seed, rx, rz, s.salt));
        int range = s.spacing - s.separation;
        int x, z;
        if (s.triangular) { x = (r.nextInt(range) + r.nextInt(range)) / 2; z = (r.nextInt(range) + r.nextInt(range)) / 2; }
        else { x = range <= 0 ? 0 : r.nextInt(range); z = range <= 0 ? 0 : r.nextInt(range); }
        return new int[]{rx * s.spacing + x, rz * s.spacing + z};
    }

    /** The extra dice some structures roll (outposts: one in five; buried treasure: one in a hundred). */
    private static boolean rolls(Spec s, long seed, int cx, int cz) {
        switch (s.reduction) {
            case 1: {
                int i = cx >> 4, j = cz >> 4;
                Random r = new Random((long) (i ^ (j << 4)) ^ seed);
                r.nextInt();
                return r.nextInt((int) (1.0f / (float) s.frequency)) == 0;
            }
            case 2: {
                Random r = new Random(regionSeed(seed, cx, cz, 10387320));
                return r.nextFloat() < (float) s.frequency;
            }
            default: return true;
        }
    }

    /** Whether a village would have been placed on this chunk, whatever is in it: what keeps outposts away from villages. */
    private static boolean villageChunk(long seed, int cx, int cz) {
        Spec v = SPECS.get("village");
        int[] c = chunkInRegion(v, seed, Math.floorDiv(cx, v.spacing), Math.floorDiv(cz, v.spacing));
        return c[0] == cx && c[1] == cz;
    }

    private static boolean villageWithin(long seed, int cx, int cz, int range) {
        for (int dz = -range; dz <= range; dz++) for (int dx = -range; dx <= range; dx++) if (villageChunk(seed, cx + dx, cz + dz)) return true;
        return false;
    }

    // ------------------------------------------------------------------ the positions on a map

    private static final Map<String, int[][]> CACHE = new HashMap<>();

    /** Every start of this structure on the map: {x, z} of the middle of its chunk. Null if the structure is not placed this way. */
    static synchronized int[][] positions(String id, BiomeGrid grid) {
        Spec s = SPECS.get(id);
        if (s == null || grid == null) return null;
        String key = id + "@" + grid.seed + "@" + System.identityHashCode(grid);
        int[][] have = CACHE.get(key);
        if (have != null) return have;
        int[][] out = compute(s, grid.seed, grid);
        CACHE.put(key, out);
        return out;
    }

    private static boolean[] allowed(Spec s, BiomeGrid grid) {
        String[] ids = PlaceSearch.idsOf(grid);
        Set<String> ok = new HashSet<>(Arrays.asList(s.biomes));
        boolean[] a = new boolean[ids.length];
        for (int i = 0; i < ids.length; i++) a[i] = ok.contains(ids[i]);
        return a;
    }

    private static boolean validAt(boolean[] allowed, BiomeGrid grid, int blockX, int blockZ) {
        int c = grid.col(blockX), r = grid.col(blockZ);
        if (c < 0 || r < 0) return false;
        int b = grid.at(c, r);
        return b >= 0 && b < allowed.length && allowed[b];
    }

    static int[][] compute(Spec s, long seed, BiomeGrid grid) {
        boolean[] ok = allowed(s, grid);
        int minChunk = Math.floorDiv(-grid.radius, 16), maxChunk = Math.floorDiv(grid.radius, 16);
        List<int[]> out = new ArrayList<>();
        if (s.spacing == 1) {
            // one roll per chunk: only the chunks whose map cell has the right biome need rolling
            for (int row = 0; row < grid.cols(); row++) for (int col = 0; col < grid.cols(); col++) {
                int bi = grid.at(col, row);
                if (bi < 0 || bi >= ok.length || !ok[bi]) continue;
                int x0 = grid.worldX(col), z0 = grid.worldX(row);
                for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z0 + grid.step - 1, 16); cz++)
                    for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x0 + grid.step - 1, 16); cx++)
                        if (rolls(s, seed, cx, cz)) out.add(new int[]{cx * 16 + s.offset, cz * 16 + s.offset});
            }
            return out.toArray(new int[0][]);
        }
        int r0 = Math.floorDiv(minChunk, s.spacing) - 1, r1 = Math.floorDiv(maxChunk, s.spacing) + 1;
        Spec ex = s.excludeNear == null ? null : SPECS.get(s.excludeNear);
        for (int rz = r0; rz <= r1; rz++) for (int rx = r0; rx <= r1; rx++) {
            int[] c = chunkInRegion(s, seed, rx, rz);
            if (c[0] < minChunk || c[0] > maxChunk || c[1] < minChunk || c[1] > maxChunk) continue;
            int bx = c[0] * 16 + 8, bz = c[1] * 16 + 8;
            if (!validAt(ok, grid, bx, bz)) continue;
            if (s.reduction != 0 && !rolls(s, seed, c[0], c[1])) continue;
            if (ex != null && villageWithin(seed, c[0], c[1], 10)) continue;
            out.add(new int[]{bx, bz});
        }
        return out.toArray(new int[0][]);
    }

    /** The starts of this structure inside a box of chunks (for checking an exact area against the seed). Biomes are not looked at. */
    static List<int[]> potentialStarts(String id, long seed, int cx0, int cz0, int cx1, int cz1) {
        Spec s = SPECS.get(id);
        List<int[]> out = new ArrayList<>();
        if (s == null || s.spacing == 1) return out;
        for (int rz = Math.floorDiv(cz0, s.spacing) - 1; rz <= Math.floorDiv(cz1, s.spacing) + 1; rz++)
            for (int rx = Math.floorDiv(cx0, s.spacing) - 1; rx <= Math.floorDiv(cx1, s.spacing) + 1; rx++) {
                int[] c = chunkInRegion(s, seed, rx, rz);
                if (c[0] < cx0 || c[0] > cx1 || c[1] < cz0 || c[1] > cz1) continue;
                if (s.reduction != 0 && !rolls(s, seed, c[0], c[1])) continue;
                if (s.excludeNear != null && villageWithin(seed, c[0], c[1], 10)) continue;
                out.add(c);
            }
        return out;
    }
}
