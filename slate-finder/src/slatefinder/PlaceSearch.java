package slatefinder;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Finds the places on the biome map that fit a reading: the target, with everything described around it.
 *
 * Unlike a cell-by-cell walk this dilates each "near" claim over the whole map in two passes, so a claim
 * costs one sweep however far away it is said to be, and it groups what is left into regions instead of
 * listing thousands of neighbouring cells.
 */
public final class PlaceSearch {
    private PlaceSearch() {}

    public interface Cancel { boolean cancelled(); }

    public static final class Region {
        public int nearX, nearZ, midX, midZ, minX, maxX, minZ, maxZ;
        public long cells;
        public double dist;
        public String biome;
        public boolean exact;
    }

    public static final class Result {
        public final List<Region> regions = new ArrayList<>();
        public final List<String> steps = new ArrayList<>();
        public final List<Long> stepCounts = new ArrayList<>();
        public long cells;
        public long ms;
        public boolean cancelled;
        public int step;
    }

    // ------------------------------------------------------------------ position lists

    private static final class Points {
        long modified;
        int[] xs = new int[0], zs = new int[0];
    }

    private static final Map<String, Points> CACHE = new HashMap<>();

    public static boolean hasPoints(String kind) {
        try { return Landmarks.fileFor(kind).isFile(); } catch (RuntimeException e) { return false; }
    }

    private static synchronized Points points(String kind) {
        File f = Landmarks.fileFor(kind);
        if (!f.isFile()) return null;
        Points p = CACHE.get(kind);
        if (p != null && p.modified == f.lastModified()) return p;
        p = new Points();
        p.modified = f.lastModified();
        try {
            List<String> lines = Files.readAllLines(f.toPath());
            int[] xs = new int[Math.max(16, lines.size())], zs = new int[xs.length];
            int n = 0;
            for (int i = 1; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                int sp = t.indexOf(' ');
                if (sp < 0) continue;
                try {
                    xs[n] = Integer.parseInt(t.substring(0, sp));
                    zs[n] = Integer.parseInt(t.substring(sp + 1).trim());
                    n++;
                } catch (NumberFormatException ignored) { }
            }
            p.xs = Arrays.copyOf(xs, n);
            p.zs = Arrays.copyOf(zs, n);
        } catch (Exception e) {
            return null;
        }
        CACHE.put(kind, p);
        return p;
    }

    // ------------------------------------------------------------------ the search

    public static Result run(BiomeGrid grid, List<Reader.Claim> claims, int radius, Cancel cancel) {
        long t0 = System.currentTimeMillis();
        Result res = new Result();
        res.step = grid.step;
        Reader.Claim target = null;
        for (Reader.Claim c : claims) if (c.role == Reader.Role.TARGET && c.searchable()) { target = c; break; }
        if (target == null) return res;

        int n = grid.cols();
        int c0 = Math.max(0, grid.col(-radius)), c1 = Math.min(n - 1, grid.col(radius));
        if (grid.col(-radius) < 0) c0 = 0;
        if (grid.col(radius) < 0) c1 = n - 1;
        int w = c1 - c0 + 1;
        if (w <= 0) return res;
        byte[] pass = new byte[w * w];

        // the target
        boolean[] tmask = maskOf(grid, target.biomes);
        boolean anyMask = false;
        for (boolean b : tmask) if (b) anyMask = true;
        if (anyMask) {
            for (int row = 0; row < w; row++) {
                int base = row * w;
                for (int col = 0; col < w; col++) {
                    int b = grid.at(c0 + col, c0 + row);
                    if (b >= 0 && tmask[b]) pass[base + col] = 1;
                }
            }
        }
        Map<Integer, int[]> exact = new HashMap<>();
        for (String kind : target.structures) {
            Points p = points(kind);
            if (p == null) continue;
            for (int i = 0; i < p.xs.length; i++) {
                int col = grid.col(p.xs[i]) - c0, row = grid.col(p.zs[i]) - c0;
                if (col < 0 || row < 0 || col >= w || row >= w) continue;
                pass[row * w + col] = 1;
                exact.putIfAbsent(row * w + col, new int[]{p.xs[i], p.zs[i]});
            }
        }
        note(res, label(target, false), count(pass));
        if (cancel != null && cancel.cancelled()) { res.cancelled = true; return finish(res, t0); }

        // everything described around it
        byte[] src = new byte[w * w], tmp = new byte[w * w];
        for (Reader.Claim c : claims) {
            if (c == target || !c.searchable()) continue;
            if (cancel != null && cancel.cancelled()) { res.cancelled = true; return finish(res, t0); }
            Arrays.fill(src, (byte) 0);
            boolean[] m = maskOf(grid, c.biomes);
            boolean any = false;
            for (boolean b : m) if (b) { any = true; break; }
            if (any) {
                for (int row = 0; row < w; row++) {
                    int base = row * w;
                    for (int col = 0; col < w; col++) {
                        int b = grid.at(c0 + col, c0 + row);
                        if (b >= 0 && m[b]) src[base + col] = 1;
                    }
                }
            }
            // a window slightly past the radius, so things just outside it still count
            for (String kind : c.structures) {
                Points p = points(kind);
                if (p == null) continue;
                for (int i = 0; i < p.xs.length; i++) {
                    int col = grid.col(p.xs[i]) - c0, row = grid.col(p.zs[i]) - c0;
                    if (col < 0 || row < 0 || col >= w || row >= w) continue;
                    src[row * w + col] = 1;
                }
            }
            int r = Math.max(1, (int) Math.ceil(c.within / (double) grid.step));
            dilate(src, tmp, w, r);
            boolean wantNear = c.role != Reader.Role.ABSENT;
            for (int i = 0; i < pass.length; i++) {
                if (pass[i] != 0 && (src[i] != 0) != wantNear) pass[i] = 0;
            }
            note(res, label(c, true), count(pass));
        }

        // group what is left into regions
        res.cells = count(pass);
        res.regions.addAll(regions(grid, pass, w, c0, exact, cancel));
        res.regions.sort(Comparator.comparingDouble(a -> a.dist));
        if (cancel != null && cancel.cancelled()) res.cancelled = true;
        return finish(res, t0);
    }

    private static Result finish(Result r, long t0) { r.ms = System.currentTimeMillis() - t0; return r; }

    private static void note(Result r, String text, long left) { r.steps.add(text); r.stepCounts.add(left); }

    private static String label(Reader.Claim c, boolean near) {
        String name = c.label();
        if (c.role == Reader.Role.ABSENT) return "no " + name + " within " + commas(c.within) + " blocks";
        if (c.role == Reader.Role.TARGET) return name + (c.approximate ? " (by its biome)" : "");
        return name + " within " + commas(c.within) + " blocks" + (c.approximate ? " (by its biome)" : "");
    }

    private static String commas(long v) { return String.format("%,d", v); }

    private static boolean[] maskOf(BiomeGrid grid, List<String> ids) {
        boolean[] out = new boolean[256];
        for (String id : ids) {
            boolean[] m = grid.mask(id);
            for (int i = 0; i < 256; i++) if (m[i]) out[i] = true;
        }
        return out;
    }

    static long count(byte[] a) {
        long n = 0;
        for (byte b : a) if (b != 0) n++;
        return n;
    }

    /** src becomes 1 wherever any 1 lies within r cells (a square window), using two sliding passes. */
    static void dilate(byte[] src, byte[] tmp, int w, int r) {
        int[] ps = new int[w + 1];
        for (int row = 0; row < w; row++) {
            int base = row * w;
            for (int x = 0; x < w; x++) ps[x + 1] = ps[x] + (src[base + x] != 0 ? 1 : 0);
            for (int x = 0; x < w; x++) tmp[base + x] = (byte) (ps[Math.min(w, x + r + 1)] - ps[Math.max(0, x - r)] > 0 ? 1 : 0);
        }
        int[] cnt = new int[w];
        for (int y = 0; y <= Math.min(w - 1, r); y++) { int base = y * w; for (int x = 0; x < w; x++) cnt[x] += tmp[base + x]; }
        for (int y = 0; y < w; y++) {
            int base = y * w;
            for (int x = 0; x < w; x++) src[base + x] = (byte) (cnt[x] > 0 ? 1 : 0);
            int add = y + r + 1, del = y - r;
            if (add < w) { int b2 = add * w; for (int x = 0; x < w; x++) cnt[x] += tmp[b2 + x]; }
            if (del >= 0) { int b2 = del * w; for (int x = 0; x < w; x++) cnt[x] -= tmp[b2 + x]; }
        }
    }

    private static List<Region> regions(BiomeGrid grid, byte[] pass, int w, int c0, Map<Integer, int[]> exact, Cancel cancel) {
        List<Region> out = new ArrayList<>();
        int[] queue = new int[1024];
        for (int start = 0; start < pass.length; start++) {
            if (pass[start] == 0) continue;
            if (cancel != null && (start & 0xFFFFF) == 0 && cancel.cancelled()) break;
            int head = 0, tail = 0;
            queue[tail++] = start;
            pass[start] = 0;
            long sumC = 0, sumR = 0;
            int minC = Integer.MAX_VALUE, maxC = -1, minR = Integer.MAX_VALUE, maxR = -1;
            long bestD = Long.MAX_VALUE;
            int bestCell = start;
            while (head < tail) {
                int cell = queue[head++];
                int row = cell / w, col = cell - row * w;
                sumC += col; sumR += row;
                if (col < minC) minC = col;
                if (col > maxC) maxC = col;
                if (row < minR) minR = row;
                if (row > maxR) maxR = row;
                long dx = grid.worldX(c0 + col), dz = grid.worldX(c0 + row);
                long d = dx * dx + dz * dz;
                if (d < bestD) { bestD = d; bestCell = cell; }
                int[] nb = {cell - 1, cell + 1, cell - w, cell + w};
                for (int k = 0; k < 4; k++) {
                    int q = nb[k];
                    if (q < 0 || q >= pass.length) continue;
                    if ((k == 0 && col == 0) || (k == 1 && col == w - 1)) continue;
                    if (pass[q] == 0) continue;
                    pass[q] = 0;
                    if (tail == queue.length) queue = Arrays.copyOf(queue, queue.length * 2);
                    queue[tail++] = q;
                }
            }
            Region rg = new Region();
            rg.cells = tail;
            int bRow = bestCell / w, bCol = bestCell - bRow * w;
            int[] ex = exact.get(bestCell);
            rg.exact = ex != null;
            rg.nearX = ex != null ? ex[0] : grid.worldX(c0 + bCol);
            rg.nearZ = ex != null ? ex[1] : grid.worldX(c0 + bRow);
            rg.dist = Math.sqrt((double) rg.nearX * rg.nearX + (double) rg.nearZ * rg.nearZ);
            double mc = sumC / (double) tail, mr = sumR / (double) tail;
            double md = Double.MAX_VALUE;
            int midCell = bestCell;
            for (int i = 0; i < tail; i++) {
                int cell = queue[i];
                int row = cell / w, col = cell - row * w;
                double d = (col - mc) * (col - mc) + (row - mr) * (row - mr);
                if (d < md) { md = d; midCell = cell; }
            }
            int mRow = midCell / w, mCol = midCell - mRow * w;
            rg.midX = grid.worldX(c0 + mCol);
            rg.midZ = grid.worldX(c0 + mRow);
            rg.minX = grid.worldX(c0 + minC); rg.maxX = grid.worldX(c0 + maxC);
            rg.minZ = grid.worldX(c0 + minR); rg.maxZ = grid.worldX(c0 + maxR);
            rg.biome = pretty(grid.nameAt(c0 + bCol, c0 + bRow));
            out.add(rg);
            if (out.size() > 20000) break;
        }
        return out;
    }

    public static String pretty(String id) {
        if (id == null) return "?";
        int i = id.indexOf(':');
        String s = (i >= 0 ? id.substring(i + 1) : id).replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char ch : s.toCharArray()) { sb.append(up ? Character.toUpperCase(ch) : ch); up = ch == ' '; }
        return sb.toString();
    }
}
