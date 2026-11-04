package slatefinder;

import java.io.File;
import java.util.*;

/** Checks the sentence reader and the search against a made-up world. Exit code 1 on any failure. */
public final class ReaderTest {
    static int fails = 0, checks = 0;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) { fails++; System.out.println("FAIL: " + what); }
    }

    static String describe(Reader.Reading r) {
        StringBuilder sb = new StringBuilder();
        for (Reader.Claim c : r.claims) sb.append(c.role).append(':').append(c.label()).append('(').append(c.within).append(c.searchable() ? "" : ",X").append(") ");
        return sb.toString();
    }

    static Reader.Claim find(Reader.Reading r, String label) {
        for (Reader.Claim c : r.claims) if (c.label().equalsIgnoreCase(label)) return c;
        return null;
    }

    /** The slow, obviously-correct version of the search, to check the fast one against. */
    static long bruteForce(BiomeGrid g, Reader.Reading rd) {
        int n = g.cols();
        int c0 = Math.max(0, g.col(-20000)), c1 = Math.min(n - 1, g.col(20000));
        Map<Reader.Claim, List<Reader.Claim>> kids = new IdentityHashMap<>();
        Reader.Claim target = rd.target();
        for (Reader.Claim c : rd.searchable()) {
            if (c == target) continue;
            Reader.Claim p = c.parent == null ? target : c.parent;
            kids.computeIfAbsent(p, k -> new ArrayList<>()).add(c);
        }
        long count = 0;
        for (int z = c0; z <= c1; z++) for (int x = c0; x <= c1; x++) if (holds(g, target, x, z, kids, c0, c1)) count++;
        return count;
    }

    static boolean isType(BiomeGrid g, Reader.Claim c, int x, int z) {
        int b = g.at(x, z);
        if (b < 0) return false;
        String id = PlaceSearch.idsOf(g)[b];
        return c.biomes.contains(id);
    }

    static boolean holds(BiomeGrid g, Reader.Claim node, int x, int z, Map<Reader.Claim, List<Reader.Claim>> kids, int c0, int c1) {
        if (!isType(g, node, x, z)) return false;
        for (Reader.Claim ch : kids.getOrDefault(node, List.of())) {
            int rmax = Math.max(1, (int) Math.ceil(ch.within / (double) g.step));
            int rmin = ch.minWithin > 0 ? Math.max(1, (int) Math.ceil(ch.minWithin / (double) g.step)) : 0;
            boolean any = false, inner = false;
            for (int dz = -rmax; dz <= rmax && !(any && rmin == 0); dz++) {
                for (int dx = -rmax; dx <= rmax; dx++) {
                    if (!PlaceSearch.inKernel(dx, dz, rmax)) continue;
                    int xx = x + dx, zz = z + dz;
                    if (xx < 0 || zz < 0 || xx >= g.cols() || zz >= g.cols()) continue;
                    boolean ok = ch.role == Reader.Role.ABSENT ? isType(g, ch, xx, zz) : holds(g, ch, xx, zz, kids, c0, c1);
                    if (!ok) continue;
                    any = true;
                    if (rmin > 0 && PlaceSearch.inKernel(dx, dz, rmin)) inner = true;
                }
            }
            if (ch.role == Reader.Role.ABSENT) { if (any) return false; }
            else {
                if (!any) return false;
                if (rmin > 0) {
                    // a ring: something qualifying within the outer distance but none within the inner one
                    boolean ring = false;
                    for (int dz = -rmax; dz <= rmax && !ring; dz++) for (int dx = -rmax; dx <= rmax && !ring; dx++) {
                        if (!PlaceSearch.inKernel(dx, dz, rmax) || PlaceSearch.inKernel(dx, dz, rmin)) continue;
                        int xx = x + dx, zz = z + dz;
                        if (xx < 0 || zz < 0 || xx >= g.cols() || zz >= g.cols()) continue;
                        if (holds(g, ch, xx, zz, kids, c0, c1)) ring = true;
                    }
                    if (!ring || inner) return false;
                }
            }
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        File tmp = new File(System.getProperty("java.io.tmpdir"), "slate-describe-test");
        File gridFile = DescribeTestWorld.write(new File(tmp, "grid.bin"), 20000, 64, 12345);
        BiomeGrid grid = BiomeGrid.load(gridFile);

        // --- the example from the request: plains, a village, snowy mountains and a river
        Reader.Reading r = Reader.read("there is a plains biome also a village snowy mountains and a river", grid);
        System.out.println(describe(r));
        check(r.target() != null && r.target().label().equals("Plains"), "plains is the target");
        check(find(r, "Village") != null, "village understood");
        check(find(r, "Snowy mountains") != null, "snowy mountains understood");
        check(find(r, "River") != null, "river understood");
        check(r.claims.size() == 4, "exactly four things read, got " + r.claims.size());

        // --- misspelling
        r = Reader.read("planes biome with a vilage and snowy mountians near a riber", grid);
        System.out.println(describe(r));
        check(r.target() != null && r.target().label().equals("Plains"), "planes -> plains");
        check(find(r, "Village") != null, "vilage -> village");
        check(find(r, "Snowy mountains") != null, "snowy mountians -> snowy mountains");
        check(find(r, "River") != null, "riber -> river");

        // --- near-misses that must NOT be "corrected" into biomes
        for (String ok : new String[]{"I plan to build a house", "the tail of a dog", "a stone road with a wall"}) {
            Reader.Reading rr = Reader.read(ok, grid);
            check(rr.claims.isEmpty(), "'" + ok + "' should name nothing but read " + describe(rr));
        }
        r = Reader.read("I am in a plans biome", grid);
        check(r.target() != null && r.target().label().equals("Plains"), "plans -> plains, got " + describe(r));

        // --- negation, distance, target marker
        r = Reader.read("I am standing in a desert with no ocean and no jungle, a river right next to me and mountains far away", grid);
        System.out.println(describe(r));
        check(r.target() != null && r.target().label().equals("Desert"), "marked target is desert");
        Reader.Claim oc = find(r, "Ocean"), jg = find(r, "Jungle");
        check(oc != null && oc.role == Reader.Role.ABSENT, "ocean absent");
        check(jg != null && jg.role == Reader.Role.ABSENT, "jungle absent (list carries the no)");
        Reader.Claim rv = find(r, "River"), mt = find(r, "Mountains");
        check(rv != null && rv.role == Reader.Role.NEAR && rv.within >= 100 && rv.within <= 128, "river right next to ~100, got " + (rv == null ? -1 : rv.within));
        check(mt != null && mt.role == Reader.Role.NEAR && mt.within == 2000, "mountains far away = 2000, got " + (mt == null ? -1 : mt.within));

        // --- exactly what was said, nothing broader
        String[][] exact = {
            {"ocean", "Ocean", "ocean"}, {"warm ocean", "Warm Ocean", "warm_ocean"}, {"lukewarm ocean", "Lukewarm Ocean", "lukewarm_ocean"}, {"cold ocean", "Cold Ocean", "cold_ocean"},
            {"deep ocean", "Deep Ocean", "deep_ocean"}, {"frozen ocean", "Frozen Ocean", "frozen_ocean"}, {"plains", "Plains", "plains"}, {"sunflower plains", "Sunflower Plains", "sunflower_plains"},
            {"forest", "Forest", "forest"}, {"birch forest", "Birch Forest", "birch_forest"}, {"taiga", "Taiga", "taiga"}, {"snowy taiga", "Snowy Taiga", "snowy_taiga"},
            {"river", "River", "river"}, {"frozen river", "Frozen River", "frozen_river"}, {"beach", "Beach", "beach"}, {"jungle", "Jungle", "jungle"}, {"savanna", "Savanna", "savanna"},
            {"badlands", "Badlands", "badlands"}, {"swamp", "Swamp", "swamp"}};
        for (String[] e : exact) {
            Reader.Reading er = Reader.read(e[0], grid);
            Reader.Claim ec = er.target();
            check(ec != null && ec.biomes.size() == 1 && ec.biomes.get(0).equals(e[2]) && ec.label().equals(e[1]), "'" + e[0] + "' must mean only " + e[2] + ", got " + (ec == null ? "nothing" : ec.biomes));
        }
        // the search itself is exact too: "ocean" must not match the warm ocean cells
        {
            Set<String> seen = new HashSet<>();
            PlaceSearch.Result ocs = PlaceSearch.run(grid, Reader.read("ocean", grid).searchable(), 20000, null);
            for (PlaceSearch.Region rg : ocs.regions) seen.add(rg.biome);
            check(seen.size() == 1 && seen.contains("Ocean"), "'ocean' search returns only Ocean cells, got " + seen);
            seen.clear();
            for (PlaceSearch.Region rg : PlaceSearch.run(grid, Reader.read("warm ocean", grid).searchable(), 20000, null).regions) seen.add(rg.biome);
            check(seen.size() == 1 && seen.contains("Warm Ocean"), "'warm ocean' search returns only Warm Ocean cells, got " + seen);
            seen.clear();
            for (PlaceSearch.Region rg : PlaceSearch.run(grid, Reader.read("plains", grid).searchable(), 20000, null).regions) seen.add(rg.biome);
            check(seen.size() == 1 && seen.contains("Plains"), "'plains' search returns only Plains cells, got " + seen);
        }
        // the whole family, when you ask for it
        for (String[] e : new String[][]{{"any ocean", "9"}, {"all forests", "5"}, {"every taiga", "4"}, {"any plains biome", "2"}, {"all oceans", "9"}}) {
            Reader.Claim fc = Reader.read(e[0], grid).target();
            check(fc != null && fc.ref.biomes.size() == Integer.parseInt(e[1]), "'" + e[0] + "' means " + e[1] + " biomes, got " + (fc == null ? "nothing" : fc.ref.biomes.size()));
        }
        // comma lists, typos, and village kinds
        r = Reader.read("plains vilage, plais biome", grid);
        System.out.println(describe(r));
        check(r.target() != null && r.target().label().equals("Plains Village"), "comma list: the first thing named is the spot, got " + (r.target() == null ? "none" : r.target().label()));
        check(find(r, "Plains") != null, "comma list: 'plais biome' -> Plains");
        r = Reader.read("desert village, savanna village, snowy village, taiga village", grid);
        check(find(r, "Desert Village") != null && find(r, "Savanna Village") != null && find(r, "Snowy Village") != null && find(r, "Taiga Village") != null, "every village kind understood: " + describe(r));
        r = Reader.read("warm ocean ruins, cold ocean ruins, beached shipwreck, ocean shipwreck", grid);
        check(find(r, "Warm Ocean Ruins") != null && find(r, "Cold Ocean Ruins") != null && find(r, "Beached Shipwreck") != null && find(r, "Ocean Shipwreck") != null, "ruin and wreck kinds understood: " + describe(r));
        r = Reader.read("desert village", grid);
        check(r.target() != null && (r.target().structures.contains("desert_village") || (r.target().biomes.size() == 1 && r.target().biomes.contains("desert"))), "desert village is searched as desert villages only");

        // --- a long, messy description: numbers that come before their name, a name said twice, "within"
        r = Reader.read("village, meadow biome, pale garden biome, next to the village is a dark forest biome, about  400 blocks there is  a grove and about 200 blocks there is a bamboo jungle, forest within 600 blocks, pale garden", null);
        System.out.println(describe(r));
        check(r.target() != null && r.target().label().equals("Village"), "long description: the first thing named, the village, is the spot");
        String[][] want = {{"Meadow", "300"}, {"Pale Garden", "300"}, {"Dark Forest", "100"}, {"Grove", "400"}, {"Bamboo Jungle", "200"}, {"Forest", "600"}};
        for (String[] w : want) { Reader.Claim wc = find(r, w[0]); check(wc != null && wc.within == Integer.parseInt(w[1]), "long description: " + w[0] + " within " + w[1] + ", got " + (wc == null ? "none" : wc.within)); }
        check(r.claims.size() == 7, "long description: seven distinct things, got " + r.claims.size());
        boolean complained = false;
        for (Reader.Issue i : r.issues) if (i.text.contains("within")) complained = true;
        check(!complained, "'within' is not reported as an unknown word");

        // --- every way of saying how far: limits, ranges, minimums, units
        String[][] dist = {
            {"plains with a grove within 500 blocks", "500", "0"}, {"plains, grove up to 500 blocks", "500", "0"}, {"plains, grove no more than 500 blocks away", "500", "0"},
            {"plains, grove not more than 500 blocks", "500", "0"}, {"plains, grove at most 500 blocks away", "500", "0"}, {"plains, grove less than 500 blocks away", "500", "0"},
            {"plains, grove under 500 blocks", "500", "0"}, {"plains, grove inside 500 blocks", "500", "0"}, {"plains, grove within a radius of 500", "500", "0"},
            {"plains, grove in a 500 block radius", "500", "0"}, {"plains, grove maximum of 500 blocks", "500", "0"}, {"plains, grove closer than 500 blocks", "500", "0"},
            {"plains, grove about 500 blocks away", "500", "0"}, {"plains, grove roughly 500 blocks", "500", "0"}, {"plains, grove 1.5 km away", "1500", "0"}, {"plains, grove within 2 kilometers", "2000", "0"},
            {"plains, grove 400 to 800 blocks away", "800", "400"}, {"plains, grove between 400 and 800 blocks", "800", "400"}, {"plains, grove 400-800 blocks", "800", "400"},
            {"plains, grove from 400 to 800 blocks away", "800", "400"}, {"plains, grove 400 to 800", "800", "400"}, {"plains, grove at least 500 blocks away", "2000", "500"},
            {"plains, grove more than 500 blocks away", "2000", "500"}, {"plains, grove over 500 blocks", "2000", "500"}, {"plains, grove further than 500 blocks", "2000", "500"},
            {"plains, grove 32 chunks away", "512", "0"}, {"plains, a grove within 2 chunks", "32", "0"},
        };
        for (String[] d : dist) {
            Reader.Reading dr = Reader.read(d[0], null);
            Reader.Claim gc = find(dr, "Grove");
            check(gc != null && gc.role == Reader.Role.NEAR && gc.within == Integer.parseInt(d[1]) && gc.minWithin == Integer.parseInt(d[2]),
                "'" + d[0] + "' -> NEAR " + d[2] + ".." + d[1] + ", got " + (gc == null ? "nothing" : gc.role + " " + gc.minWithin + ".." + gc.within));
            boolean junk = false;
            for (Reader.Issue i : dr.issues) if (i.sev == Reader.Sev.WARN) junk = true;
            check(!junk, "'" + d[0] + "' reports no unknown words: " + dr.issues.size());
        }
        // an absence keeps meaning absence, with or without the limit words
        for (String ab : new String[]{"plains with no desert within 500 blocks", "plains, no desert within 500 blocks", "plains without desert up to 500 blocks"}) {
            Reader.Claim dc = find(Reader.read(ab, null), "Desert");
            check(dc != null && dc.role == Reader.Role.ABSENT && dc.within == 500, "'" + ab + "' -> no desert within 500, got " + (dc == null ? "nothing" : dc.role + " " + dc.within));
        }
        // a range really is a ring on the map: nothing inside the inner distance
        {
            Reader.Reading rg = Reader.read("plains, river 2000 to 6000 blocks away", grid);
            Reader.Reading inner = Reader.read("plains, river within 2000 blocks", grid);
            Reader.Reading outer = Reader.read("plains, river within 6000 blocks", grid);
            long ring = PlaceSearch.run(grid, rg.searchable(), 20000, null).cells, in = PlaceSearch.run(grid, inner.searchable(), 20000, null).cells, out = PlaceSearch.run(grid, outer.searchable(), 20000, null).cells;
            check(ring == out - in && ring >= 0, "range = within the outer minus within the inner (" + ring + " vs " + out + " - " + in + ")");
        }

        // --- chains: your two real descriptions
        r = Reader.read("desert village, forest, river, sparse jungle, jungle close to the forest, savanna within 50 river of the village warm ocean within 100 blocks of the village bambo jungle next to the jungle searching ofr the village", null);
        check(r.target() != null && r.target().label().equals("Desert Village"), "chain 1: the village is the spot");
        check(find(r, "Jungle") != null && find(r, "Jungle").parent == find(r, "Forest") && find(r, "Jungle").within == 300, "chain 1: jungle close to the forest");
        check(find(r, "Bamboo Jungle") != null && find(r, "Bamboo Jungle").parent == find(r, "Jungle"), "chain 1: bamboo jungle next to the jungle (the jungle, not sparse jungle)");
        check(find(r, "Warm Ocean") != null && find(r, "Warm Ocean").within == 100 && find(r, "Warm Ocean").parent == r.target(), "chain 1: warm ocean within 100 of the village");
        check(find(r, "Savanna") != null && find(r, "Savanna").within == 50, "chain 1: savanna within 50");
        boolean ofr = false;
        for (Reader.Issue i : r.issues) if (i.text.contains("ofr")) ofr = true;
        check(!ofr, "chain 1: 'ofr' is not reported");
        r = Reader.read("there's a desert village and next to the village is a desert and the savana is touching a plains biome and the plains biomes is touching a jungles biome and in the jungle theirs a jungle temple and at the desert village thers a chery blossom biome and touching the cherry blossom biome is a plains biome and touching the plains biome is a warm ocean", null);
        System.out.println(describe(r));
        check(r.target() != null && r.target().label().equals("Desert Village"), "chain 2: the village is the spot");
        check(r.claims.size() == 9, "chain 2: nine things (two separate plains), got " + r.claims.size());
        List<Reader.Claim> plains = new ArrayList<>();
        for (Reader.Claim pc : r.claims) if (pc.label().equals("Plains")) plains.add(pc);
        check(plains.size() == 2, "chain 2: 'a plains biome' twice is two plains");
        Reader.Claim ds = find(r, "Desert"), sv = find(r, "Savanna"), jgl = find(r, "Jungle"), jt = find(r, "Jungle Temple"), ch = find(r, "Cherry Grove"), wo = find(r, "Warm Ocean");
        check(ds != null && ds.parent == r.target() && ds.within == 100, "chain 2: desert next to the village");
        check(sv != null && sv.parent == ds, "chain 2: savanna touches the desert");
        check(plains.size() == 2 && plains.get(0).parent == sv && jgl != null && jgl.parent == plains.get(0), "chain 2: savanna - plains - jungle");
        check(jt != null && jt.parent == jgl, "chain 2: the temple is in the jungle");
        check(ch != null && ch.parent == r.target(), "chain 2: cherry grove at the village");
        check(plains.size() == 2 && plains.get(1).parent == ch && wo != null && wo.parent == plains.get(1), "chain 2: cherry grove - plains - warm ocean");

        // --- the search follows the chain exactly (checked against a slow brute-force version)
        for (String q : new String[]{
            "there is plains and the river is touching the plains and a forest is next to the river",
            "plains, next to the plains is a river and touching the river is a snowy taiga",
            "forest, the plains is touching the forest and no desert within 600 blocks",
            "taiga, close to the taiga is a river, the river is within 400 to 1500 blocks of a forest"}) {
            Reader.Reading cr = Reader.read(q, grid);
            long fast = PlaceSearch.run(grid, cr.searchable(), 20000, null).cells;
            long slow = bruteForce(grid, cr);
            System.out.println(describe(cr) + " -> " + fast + " cells");
            check(fast == slow, "chain search equals brute force for '" + q + "': " + fast + " vs " + slow);
        }
        {
            Reader.Reading rg = Reader.read("taiga, close to the taiga is a river, the river is within 400 to 1500 blocks of a forest", grid);
            Reader.Claim fo2 = find(rg, "Forest");
            check(fo2 != null && fo2.parent == find(rg, "River") && fo2.minWithin == 400 && fo2.within == 1500, "a range between two linked things: " + (fo2 == null ? "none" : fo2.parent + " " + fo2.minWithin + ".." + fo2.within));
        }
        // making another thing the spot, and typing a distance
        {
            Reader.Reading cr = Reader.read("there is plains and the river is touching the plains and a forest is next to the river", grid);
            Reader.Claim rvr = find(cr, "River");
            cr.makeSpot(rvr);
            check(cr.target() == rvr && rvr.parent == null, "makeSpot: river is now the spot");
            check(find(cr, "Plains").parent == rvr && find(cr, "Forest").parent == rvr, "makeSpot: the rest hangs from the river");
            long viaFast = PlaceSearch.run(grid, cr.searchable(), 20000, null).cells;
            check(viaFast == bruteForce(grid, cr), "makeSpot: search matches brute force");
            Reader.Claim fo = find(cr, "Forest");
            cr.setDistance(fo, 0, 1000);
            check(fo.within == 1000 && fo.parent == rvr, "typed distance 1000 kept");
            check(PlaceSearch.run(grid, cr.searchable(), 20000, null).cells == bruteForce(grid, cr), "typed distance: search matches brute force");
            check(Arrays.equals(Reader.parseDistance("1.5k"), new int[]{0, 1500}) && Arrays.equals(Reader.parseDistance("400-800"), new int[]{400, 800}) && Arrays.equals(Reader.parseDistance("  250 blocks "), new int[]{0, 250}) && Reader.parseDistance("soon") == null, "parseDistance");
        }

        // --- numbers
        r = Reader.read("forest with a taiga within 1.5k blocks and ocean 800 blocks away", grid);
        System.out.println(describe(r));
        check(find(r, "Taiga") != null && find(r, "Taiga").within == 1500, "1.5k blocks = 1500");
        check(find(r, "Ocean") != null && find(r, "Ocean").within == 800, "800 blocks");

        // --- every single biome and structure resolves by its own name, in several spellings
        for (Lexicon.Item it : Lexicon.items()) {
            for (String spelling : new String[]{it.name, it.id.replace('_', ' '), it.name.toLowerCase() + (it.name.endsWith("s") ? "" : "s")}) {
                Lexicon.Ref ref = Lexicon.find(Lexicon.norm(spelling));
                check(ref != null, "'" + spelling + "' is not understood");
                if (ref != null && !ref.family) {
                    List<String> all = it.structure() ? ref.structures : ref.biomes;
                    check(all.contains(it.id), "'" + spelling + "' maps to " + ref.label + " not " + it.id);
                }
            }
        }

        // --- nothing readable
        r = Reader.read("blah blorp", grid);
        check(r.hasError() && !r.canSearch(), "gibberish is an error");
        r = Reader.read("cows and creepers", grid);
        check(r.hasError(), "mobs only is an error");
        boolean said = false;
        for (Reader.Issue i : r.issues) if (i.text.contains("cow")) said = true;
        check(said, "mob words are reported");
        r = Reader.read("", grid);
        check(r.claims.isEmpty() && r.issues.isEmpty(), "blank is silent");

        // --- things the overworld map cannot hold
        r = Reader.read("nether wastes with a nether fortress", grid);
        System.out.println(describe(r));
        check(!r.canSearch() && r.hasError(), "nether-only description cannot be searched");
        r = Reader.read("plains next to a nether fortress", grid);
        check(r.canSearch() && r.target().label().equals("Plains"), "overworld target survives an unsearchable extra");
        r = Reader.read("desert pyramid", grid);
        System.out.println(describe(r));
        check(r.canSearch() && r.target().approximate && r.target().biomes.contains("desert"), "desert pyramid searched by its biome");
        r = Reader.read("mineshaft", grid);
        check(!r.canSearch(), "mineshaft cannot be placed");

        // --- thin biome as the target gets a warning
        r = Reader.read("river", grid);
        boolean thin = false;
        for (Reader.Issue i : r.issues) if (i.text.contains("narrower")) thin = true;
        check(thin, "river target is flagged as thin");

        // --- running the search
        r = Reader.read("plains next to a river, snowy mountains in the distance", grid);
        System.out.println(describe(r));
        PlaceSearch.Result res = PlaceSearch.run(grid, r.searchable(), 20000, null);
        System.out.println("regions " + res.regions.size() + " cells " + res.cells + " in " + res.ms + "ms: " + res.steps);
        check(res.regions.size() > 0, "found some places");
        for (PlaceSearch.Region rg : res.regions) {
            check(rg.biome.toLowerCase().contains("plains"), "result is plains: " + rg.biome);
            if (!rg.biome.toLowerCase().contains("plains")) break;
        }
        // absent really removes things
        Reader.Reading withRiver = Reader.read("plains with a river", grid), noRiver = Reader.read("plains with no river nearby", grid);
        long a = PlaceSearch.run(grid, withRiver.searchable(), 20000, null).cells, b = PlaceSearch.run(grid, noRiver.searchable(), 20000, null).cells;
        long all = PlaceSearch.run(grid, Reader.read("plains", grid).searchable(), 20000, null).cells;
        System.out.println("plains " + all + "  with river " + a + "  no river " + b);
        check(a > 0 && b > 0 && a + b == all, "near + absent partition the target cells (" + a + " + " + b + " vs " + all + ")");

        // structures with a real position list
        if (!DescribeTestWorld.sandboxed()) {
            System.out.println("(APPDATA is not a temp folder; skipping the structure-list checks so no real data is touched)");
        } else {
            File villages = Landmarks.fileFor("village");
            if (!villages.isFile()) DescribeTestWorld.writeVillages(villages, grid, 99);
            Reader.Reading vr = Reader.read("I want a village next to a river, snowy mountains far away", grid);
            System.out.println(describe(vr));
            check(vr.target() != null && vr.target().label().equals("Village") && !vr.target().approximate, "village is the target, from its position list");
            PlaceSearch.Result vres = PlaceSearch.run(grid, vr.searchable(), 20000, null);
            check(vres.cells > 0 && vres.regions.size() > 0, "villages found next to rivers");
            boolean allExact = true;
            for (PlaceSearch.Region rg : vres.regions) if (!rg.exact) allExact = false;
            check(allExact, "village results are the listed positions, not cell centres");
            Reader.Claim dv = Reader.read("desert village", grid).target();
            check(dv != null && !dv.approximate && dv.structures.contains("desert_village"), "desert village uses the village list when there is one");
            PlaceSearch.Result dres = PlaceSearch.run(grid, Reader.read("desert village", grid).searchable(), 20000, null);
            PlaceSearch.Result allv = PlaceSearch.run(grid, Reader.read("village", grid).searchable(), 20000, null);
            check(dres.regions.size() > 0 && dres.regions.size() < allv.regions.size(), "desert villages are a part of all villages (" + dres.regions.size() + " of " + allv.regions.size() + ")");
            Reader.Reading nv = Reader.read("plains with no village within 500 blocks", grid);
            long withV = PlaceSearch.run(grid, Reader.read("plains with a village within 500 blocks", grid).searchable(), 20000, null).cells;
            long noV = PlaceSearch.run(grid, nv.searchable(), 20000, null).cells;
            long allP = PlaceSearch.run(grid, Reader.read("plains", grid).searchable(), 20000, null).cells;
            check(withV + noV == allP && withV > 0, "village near/absent partition plains (" + withV + " + " + noV + " vs " + allP + ")");
        }

        // radius limits
        PlaceSearch.Result near = PlaceSearch.run(grid, Reader.read("plains", grid).searchable(), 5000, null);
        for (PlaceSearch.Region rg : near.regions) check(Math.abs(rg.nearX) <= 5100 && Math.abs(rg.nearZ) <= 5100, "radius respected");
        check(near.cells < all, "smaller radius, fewer cells");

        // --- "coordinates next to the village and the river": none of them is the spot, the spot is anywhere near all of them
        {
            String[] yes = {"I want the coordinates next to the village and the river", "tp me next to the village and the river", "I am standing next to a village and a river",
                "I want to build near a village and a river", "a place between a village and a river", "coordinates near a desert village and a river"};
            for (String q : yes) {
                Reader.Reading aw = Reader.read(q, grid);
                check(aw.isAnywhere() && aw.target() != null && aw.target().anywhere, "'" + q + "' -> the spot is anywhere near: " + describe(aw));
                check(aw.claims.size() == 3 && aw.searchable().size() == 3, "'" + q + "' reads two things plus the spot, got " + describe(aw));
                for (Reader.Claim c : aw.claims) if (!c.anywhere) check(c.role == Reader.Role.NEAR && c.parent == null && c.within > 0, "'" + q + "': " + c.label() + " hangs off the spot");
            }
            String[] no = {"searching for a village next to a river", "village next to river", "coordinates of the village next to the river", "i live in a desert next to a village", "I want a village near a river", "looking for a place near a village"};
            for (String q : no) {
                Reader.Reading nr = Reader.read(q, grid);
                check(!nr.isAnywhere() && nr.target() != null && !nr.target().anywhere, "'" + q + "' keeps a named thing as the spot: " + describe(nr));
            }
            Reader.Reading aw = Reader.read("coordinates next to the village and the river", grid);
            Reader.Claim vv = find(aw, "Village"), rr = find(aw, "River");
            check(vv != null && rr != null && vv.within == 100 && rr.within >= 100 && rr.within <= 128, "one closeness for the list: village " + (vv == null ? -1 : vv.within) + ", river " + (rr == null ? -1 : rr.within));
            Reader.Reading bw = Reader.read("coordinates next to the forest and the river", grid);
            long fast = PlaceSearch.run(grid, bw.searchable(), 20000, null).cells;
            long slow = bruteForce(grid, bw);
            check(bw.isAnywhere() && fast == slow && fast > 0, "anywhere search matches brute force: " + fast + " vs " + slow);
            // the spot has to be a place where both are near
            PlaceSearch.Result ar = PlaceSearch.run(grid, aw.searchable(), 20000, null);
            check(!ar.regions.isEmpty(), "anywhere search finds places");
            // buttons: make one of them the spot, then back to anywhere
            aw.makeSpot(vv);
            check(!aw.isAnywhere() && aw.target() == vv && aw.claims.size() == 2, "make this the spot removes the anywhere spot");
            aw.makeAnywhere(grid);
            check(aw.isAnywhere() && aw.target().anywhere && vv.role == Reader.Role.NEAR, "makeAnywhere puts it back");
            aw.clearAnywhere();
            check(!aw.isAnywhere() && aw.target() != null && !aw.target().anywhere, "clearAnywhere gives the spot to a named thing");
        }

        // --- the search backwards: coordinates in, a description out, and that description finds the coordinates
        {
            Random rr = new Random(77);
            int good = 0, tries = 25;
            for (int i = 0; i < tries; i++) {
                int x = rr.nextInt(36000) - 18000, z = rr.nextInt(36000) - 18000;
                Reverse.Report rep = Reverse.describe(grid, x, z, 20000, null, null);
                check(rep.ok && rep.includes, "describe " + x + " ~ " + z + " finds the spot: " + rep.text + " / " + rep.note);
                if (!rep.ok) continue;
                Reader.Reading back = Reader.read(rep.text, grid);
                check(!back.hasError() && back.target() != null && back.target().label().equalsIgnoreCase(rep.spotName), "the text reads back with the spot biome first: " + rep.text);
                PlaceSearch.Result pr = PlaceSearch.run(grid, back.searchable(), 20000, null);
                check(pr.contains(grid, x, z), "pasting the text into the search finds " + x + " ~ " + z);
                check(pr.regions.size() == rep.places, "the number of places matches what was reported: " + pr.regions.size() + " vs " + rep.places);
                for (Reader.Claim c : back.claims) check(c.searchable(), "every part of the text is understood: " + c.label());
                if (rep.places == 1) good++;
            }
            System.out.println("describe-backwards: " + good + " of " + tries + " pinned to a single place");
            check(good >= tries / 4, "at least a quarter of the spots could be pinned to one place, got " + good);
            Reverse.Report off = Reverse.describe(grid, 90000, 90000, 20000, null, null);
            check(!off.ok && !off.note.isEmpty(), "coordinates off the map are refused politely");
        }

        System.out.println(checks + " checks, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
