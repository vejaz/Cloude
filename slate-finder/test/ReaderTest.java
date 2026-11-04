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
        check(find(r, "River (any)") != null, "river understood");
        check(r.claims.size() == 4, "exactly four things read, got " + r.claims.size());

        // --- misspelling
        r = Reader.read("planes biome with a vilage and snowy mountians near a riber", grid);
        System.out.println(describe(r));
        check(r.target() != null && r.target().label().equals("Plains"), "planes -> plains");
        check(find(r, "Village") != null, "vilage -> village");
        check(find(r, "Snowy mountains") != null, "snowy mountians -> snowy mountains");
        check(find(r, "River (any)") != null, "riber -> river");

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
        Reader.Claim oc = find(r, "Ocean (any)"), jg = find(r, "Jungle");
        check(oc != null && oc.role == Reader.Role.ABSENT, "ocean absent");
        check(jg != null && jg.role == Reader.Role.ABSENT, "jungle absent (list carries the no)");
        Reader.Claim rv = find(r, "River (any)"), mt = find(r, "Mountains");
        check(rv != null && rv.role == Reader.Role.NEAR && rv.within >= 100 && rv.within <= 128, "river right next to ~100, got " + (rv == null ? -1 : rv.within));
        check(mt != null && mt.role == Reader.Role.NEAR && mt.within == 2000, "mountains far away = 2000, got " + (mt == null ? -1 : mt.within));

        // --- numbers
        r = Reader.read("forest with a taiga within 1.5k blocks and ocean 800 blocks away", grid);
        System.out.println(describe(r));
        check(find(r, "Taiga") != null && find(r, "Taiga").within == 1500, "1.5k blocks = 1500");
        check(find(r, "Ocean (any)") != null && find(r, "Ocean (any)").within == 800, "800 blocks");

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

        System.out.println(checks + " checks, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
