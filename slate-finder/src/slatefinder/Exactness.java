package slatefinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Proof that an exact place really is the world the biome map came from. Two checks, both against things the game wrote itself:
 * the biome at points of the biome map against the biome of the generated chunks, and the structures the game started in these
 * chunks against where the seed says they should be.
 */
final class Exactness {
    private Exactness() {}

    static final class Report {
        int biomeChecked, biomeSame;
        double biomePct;
        int expected, confirmed;
        final List<String> structureLines = new ArrayList<>();
        final List<String> found = new ArrayList<>();
        /** 2: it matches; 1: mostly; 0: it does not look like the same world. */
        int level;
        String verdict = "";
    }

    static Report check(ExactPlace pl, BiomeGrid grid) {
        Report r = new Report();
        // 1. the biome map against the real chunks: at the middle and at the corner of each map cell, whichever the map used
        int step = grid.step;
        int c0 = Math.max(0, grid.col(pl.x0())), c1 = Math.min(grid.cols() - 1, grid.col(pl.x1()));
        int r0 = Math.max(0, grid.col(pl.z0())), r1 = Math.min(grid.cols() - 1, grid.col(pl.z1()));
        String[] ids = PlaceSearch.idsOf(grid);
        int[] checked = new int[2], same = new int[2];
        for (int row = r0; row <= r1; row++) for (int col = c0; col <= c1; col++) {
            int bi = grid.at(col, row);
            if (bi < 0 || bi >= ids.length) continue;
            for (int k = 0; k < 2; k++) {
                int x = grid.worldX(col) + (k == 0 ? 0 : step / 2), z = grid.worldX(row) + (k == 0 ? 0 : step / 2);
                String real = pl.biomeAt(x, z);
                if (real == null) continue;
                checked[k]++;
                if (real.equals(ids[bi])) same[k]++;
            }
        }
        int k = same[1] * Math.max(1, checked[0]) > same[0] * Math.max(1, checked[1]) ? 1 : 0;
        r.biomeChecked = checked[k];
        r.biomeSame = same[k];
        r.biomePct = r.biomeChecked == 0 ? 0 : 100.0 * r.biomeSame / r.biomeChecked;

        // 2. the structures the game started here, against where the seed puts them
        Map<String, List<ExactPlace.Start>> actual = new HashMap<>();
        for (ExactPlace.Start s : pl.starts) actual.computeIfAbsent(s.id, q -> new ArrayList<>()).add(s);
        for (ExactPlace.Start s : pl.starts) {
            String nm = PlaceSearch.pretty(s.id.startsWith("minecraft:") ? s.id.substring(10) : s.id);
            r.found.add(nm + "  at  X " + String.format("%,d", s.cx * 16 + 8) + "  Z " + String.format("%,d", s.cz * 16 + 8));
        }
        for (StructureSeeds.Spec sp : StructureSeeds.all()) {
            if (sp.spacing == 1) continue;
            List<int[]> want = new ArrayList<>();
            // the outer ring of chunks is left out: a start there may belong to a chunk this place does not hold
            for (int[] c : StructureSeeds.potentialStarts(sp.id, pl.seed, pl.cx0 + 1, pl.cz0 + 1, pl.cx0 + pl.cw - 2, pl.cz0 + pl.cd - 2)) {
                int x = c[0] * 16 + 8, z = c[1] * 16 + 8;
                int col = grid.col(x), row = grid.col(z);
                if (col < 0 || row < 0) continue;
                int bi = grid.at(col, row);
                if (bi < 0) continue;
                boolean okBiome = false;
                for (String b : sp.biomes) if (b.equals(ids[bi])) { okBiome = true; break; }
                if (okBiome) want.add(c);
            }
            if (want.isEmpty()) continue;
            int ok = 0;
            for (int[] c : want) {
                boolean hit = false;
                for (String real : sp.realIds) {
                    List<ExactPlace.Start> l = actual.get(real);
                    if (l != null) for (ExactPlace.Start s : l) if (s.cx == c[0] && s.cz == c[1]) hit = true;
                }
                if (hit) ok++;
            }
            r.expected += want.size();
            r.confirmed += ok;
            r.structureLines.add((ok == want.size() ? "✓  " : "✕  ") + PlaceSearch.pretty(sp.id) + ": " + ok + " of " + want.size() + " found in the real world where the seed puts " + (want.size() == 1 ? "it" : "them"));
        }

        boolean structuresOk = r.expected == 0 || r.confirmed * 10 >= r.expected * 7;
        if (r.biomePct >= 80 && structuresOk) {
            r.level = 2;
            r.verdict = String.format(Locale.ROOT, "Exact: %.0f%% of %d points of your biome map agree with these real chunks%s.", r.biomePct, r.biomeChecked,
                r.expected == 0 ? "" : ", and " + r.confirmed + " of " + r.expected + " calculated structures were found where the seed puts them");
        } else if (r.biomePct >= 60 && structuresOk) {
            r.level = 1;
            r.verdict = String.format(Locale.ROOT, "Mostly exact: %.0f%% of %d points of your biome map agree (the map has one sample every %d blocks, so edges of biomes differ).", r.biomePct, r.biomeChecked, step);
        } else {
            r.level = 0;
            r.verdict = r.biomeChecked == 0 ? "Could not compare with your biome map here."
                : String.format(Locale.ROOT, "Does not match: only %.0f%% of your biome map agrees with these chunks%s. The seed or the Minecraft version of your biome map is probably different from the one used here (1.21.11, seed %d).",
                    r.biomePct, structuresOk ? "" : " and the structures are not where the seed puts them", pl.seed);
        }
        return r;
    }
}
