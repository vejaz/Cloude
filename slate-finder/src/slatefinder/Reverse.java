package slatefinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * The search backwards: give it coordinates and it looks around them on the map, then writes a description that the
 * normal search turns back into a short list of places that includes those coordinates. Every candidate description is
 * read and searched for real before it is kept, so the text it returns can be pasted into the search box as it is.
 */
final class Reverse {
    private Reverse() {}

    /** The biome or structure found near the coordinates, and how far away it is. */
    static final class Near {
        final String id, name;
        final boolean structure;
        final double dist;
        int said;          // the distance written in the description
        Near(String id, String name, boolean structure, double dist) { this.id = id; this.name = name; this.structure = structure; this.dist = dist; }
        String part() { return "the " + name.toLowerCase(Locale.ROOT) + " within " + said + " blocks"; }
    }

    static final class Report {
        String text = "";
        String spotName = "";
        boolean ok;
        boolean includes;
        long places;          // separate places the description finds
        long cells;
        String note = "";
        final List<Near> used = new ArrayList<>();
        /** After each added detail: the description so far and how many places it finds. */
        final List<String> steps = new ArrayList<>();
        final List<Long> stepPlaces = new ArrayList<>();
    }

    interface Progress { void say(String s); }

    private static final int[] NICE = {128, 192, 256, 320, 400, 500, 640, 800, 1000, 1280, 1600, 2000};
    private static final int REACH = 2000;
    private static final int MAX_PARTS = 8;

    private static int nice(double d, int step) {
        double need = Math.max(d + step * 0.5, step * 2);
        for (int n : NICE) if (n >= need) return n;
        return (int) (Math.ceil(need / 500) * 500);
    }

    static Report describe(BiomeGrid g, int x, int z, int radius, PlaceSearch.Cancel cancel, Progress progress) {
        Report rep = new Report();
        int col = g.col(x), row = g.col(z);
        if (col < 0 || row < 0 || col >= g.cols() || row >= g.cols() || g.at(col, row) < 0) {
            rep.note = "Those coordinates are outside your biome map, so there is nothing to describe.";
            return rep;
        }
        String[] ids = PlaceSearch.idsOf(g);
        String spotId = ids[g.at(col, row)];
        rep.spotName = Lexicon.nameOf(spotId);
        int step = g.step;
        say(progress, "Looking around " + String.format("%,d ~ %,d", x, z) + "…");

        // 1. what is near: the nearest cell of every other biome, and the nearest listed position of every structure
        double[] best = new double[ids.length];
        java.util.Arrays.fill(best, Double.MAX_VALUE);
        int cr = REACH / step + 1;
        for (int dr = -cr; dr <= cr; dr++) for (int dc = -cr; dc <= cr; dc++) {
            int cc = col + dc, rr = row + dr;
            if (cc < 0 || rr < 0 || cc >= g.cols() || rr >= g.cols()) continue;
            int b = g.at(cc, rr);
            if (b < 0) continue;
            double d = Math.hypot(dc * (double) step, dr * (double) step);
            // the nearest edge of the cell, not its corner
            d = Math.max(0, d - step * 0.7);
            if (d <= REACH && d < best[b]) best[b] = d;
        }
        List<Near> cands = new ArrayList<>();
        for (int b = 0; b < ids.length; b++) {
            if (ids[b].equals(spotId) || best[b] == Double.MAX_VALUE) continue;
            Lexicon.Item it = Lexicon.byId(ids[b]);
            if (it == null || it.dim != 'O') continue;
            cands.add(new Near(ids[b], it.name, false, best[b]));
        }
        for (Lexicon.Item it : Lexicon.items()) {
            if (!it.structure() || it.dim != 'O' || !PlaceSearch.hasPoints(it.id)) continue;
            double bd = Double.MAX_VALUE;
            for (int[] p : PlaceSearch.listedPoints(it.id, g)) {
                double d = Math.hypot(p[0] - x, p[1] - z);
                if (d < bd) bd = d;
            }
            if (bd <= REACH) cands.add(new Near(it.id, it.name, true, bd));
        }
        // rare things first: they cut the list of places down fastest. A biome's rarity is how many cells it has.
        long[] cellsOf = new long[ids.length];
        for (int r = 0; r < g.cols(); r++) for (int c = 0; c < g.cols(); c++) { int b = g.at(c, r); if (b >= 0) cellsOf[b]++; }
        List<Near> ordered = new ArrayList<>(cands);
        ordered.sort((a, b) -> Double.compare(weight(a, ids, cellsOf), weight(b, ids, cellsOf)));
        if (ordered.size() > 24) ordered = new ArrayList<>(ordered.subList(0, 24));
        for (Near n : ordered) n.said = nice(n.dist, step);

        // 2. start from the biome the spot is in, then add the detail that narrows it down the most, again and again
        String head = "searching for the " + rep.spotName.toLowerCase(Locale.ROOT);
        Eval base = evaluate(g, head, x, z, radius, cancel);
        if (base == null) { rep.note = "That biome could not be searched."; return rep; }
        rep.text = head;
        rep.places = base.places;
        rep.cells = base.cells;
        rep.includes = base.includes;
        rep.steps.add(head);
        rep.stepPlaces.add(base.places);
        say(progress, head + "  →  " + String.format("%,d", base.places) + " places");
        List<Near> chosen = new ArrayList<>();
        long cur = base.cells;
        for (int iter = 0; iter < MAX_PARTS && base.places > 1; iter++) {
            if (cancel != null && cancel.cancelled()) break;
            final List<Near> pool = new ArrayList<>(ordered);
            pool.removeAll(chosen);
            if (pool.isEmpty()) break;
            final String soFar = rep.text;
            final Eval[] tried = new Eval[pool.size()];
            final int[] saidAt = new int[pool.size()];
            IntStream.range(0, pool.size()).parallel().forEach(i -> {
                Near n = pool.get(i);
                int said = n.said;
                for (int attempt = 0; attempt < 3; attempt++) {
                    String t = soFar + ", the " + n.name.toLowerCase(Locale.ROOT) + " within " + said + " blocks";
                    Eval e = evaluate(g, t, x, z, radius, cancel);
                    if (e != null && e.includes && e.parsed == chosen.size() + 2) { tried[i] = e; saidAt[i] = said; break; }
                    if (e == null || !e.sane) break;
                    said = nextNice(said, step);     // the circle on the map is a little rounder than the real distance: widen it a notch
                }
            });
            int pick = -1;
            for (int i = 0; i < tried.length; i++) {
                Eval e = tried[i];
                if (e == null || e.cells >= cur) continue;
                if (pick < 0 || e.cells < tried[pick].cells || (e.cells == tried[pick].cells && saidAt[i] < saidAt[pick])) pick = i;
            }
            if (pick < 0) break;
            Near n = pool.get(pick);
            n.said = saidAt[pick];
            chosen.add(n);
            Eval e = tried[pick];
            rep.text = e.text;
            rep.places = e.places;
            rep.cells = e.cells;
            rep.includes = e.includes;
            cur = e.cells;
            base = e;
            rep.steps.add(rep.text);
            rep.stepPlaces.add(e.places);
            say(progress, rep.text + "  →  " + String.format("%,d", e.places) + " places");
        }
        // tighten: the smallest distance for each detail that still finds the spot narrows the list further
        for (int k = 0; k < chosen.size() && (cancel == null || !cancel.cancelled()); k++) {
            Near n = chosen.get(k);
            // the spot stays included down to some distance, and not below it: find that one by halving
            int hi = 0;
            while (hi < NICE.length && NICE[hi] < n.said) hi++;      // NICE[0..hi-1] are the smaller distances
            int lo = 0, found = -1;
            Eval foundEval = null;
            int top = hi - 1;
            while (lo <= top) {
                int mid = (lo + top) >>> 1;
                int old = n.said;
                n.said = NICE[mid];
                Eval e = evaluate(g, compose(head, chosen), x, z, radius, cancel);
                n.said = old;
                if (e != null && e.includes) { found = mid; foundEval = e; top = mid - 1; } else lo = mid + 1;
            }
            if (found >= 0 && foundEval.cells <= cur) { n.said = NICE[found]; cur = foundEval.cells; rep.text = foundEval.text; rep.places = foundEval.places; rep.cells = foundEval.cells; rep.includes = foundEval.includes; }
        }
        if (!chosen.isEmpty()) {
            rep.steps.add(rep.text);
            rep.stepPlaces.add(rep.places);
            say(progress, rep.text + "  →  " + String.format("%,d", rep.places) + " places");
        }
        rep.used.addAll(chosen);
        rep.ok = rep.includes;
        if (!rep.includes) rep.note = "I could not write a description that finds exactly this spot on the map.";
        else if (rep.places > 1) rep.note = "This is as specific as the map allows: " + String.format("%,d", rep.places) + " separate places fit it, and yours is one of them.";
        else rep.note = "Only this place fits it.";
        return rep;
    }

    private static String compose(String head, List<Near> parts) {
        StringBuilder sb = new StringBuilder(head);
        for (Near n : parts) sb.append(", ").append(n.part());
        return sb.toString();
    }

    private static void say(Progress p, String s) { if (p != null) p.say(s); }

    private static int nextNice(int d, int step) {
        for (int n : NICE) if (n > d) return n;
        return d + 500;
    }

    /** Smaller is tried first: rare biomes and structures, then near ones. */
    private static double weight(Near n, String[] ids, long[] cellsOf) {
        if (n.structure) return n.dist * 0.5 + 200;                       // structures are specific; prefer near ones
        long cells = 1;
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(n.id)) { cells = Math.max(1, cellsOf[i]); break; }
        return Math.log(cells) * 400 + n.dist;
    }

    /** A description read and searched for real. */
    private static final class Eval {
        String text;
        boolean includes, sane;
        long cells, places;
        int parsed;
    }

    private static Eval evaluate(BiomeGrid g, String text, int x, int z, int radius, PlaceSearch.Cancel cancel) {
        Reader.Reading rd = Reader.read(text, g);
        Eval e = new Eval();
        e.text = text;
        Reader.Claim t = rd.target();
        int searchable = 0;
        for (Reader.Claim c : rd.claims) if (c.searchable()) searchable++;
        e.parsed = searchable;
        e.sane = t != null && !rd.isAnywhere() && searchable == rd.claims.size() && !rd.hasError();
        if (!e.sane) return e;
        PlaceSearch.Result r = PlaceSearch.run(g, rd.searchable(), radius, cancel);
        if (r.cancelled) return null;
        e.cells = r.cells;
        e.places = r.regions.size();
        e.includes = r.contains(g, x, z);
        return e;
    }
}
