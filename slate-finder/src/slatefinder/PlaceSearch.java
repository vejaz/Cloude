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
        public boolean truncated;
        public int step;
    }

    // ------------------------------------------------------------------ position lists

    private static final class Points {
        long modified;
        int[] xs = new int[0], zs = new int[0];
    }

    private static final Map<String, Points> CACHE = new HashMap<>();

    /** True if a position list exists for this structure (variants such as "desert village" use their parent's list). */
    public static boolean hasPoints(String kind) {
        try { return Landmarks.fileFor(listKind(kind)).isFile(); } catch (RuntimeException e) { return false; }
    }

    private static String listKind(String kind) {
        Lexicon.Item it = Lexicon.byId(kind);
        return it != null ? it.listKind() : kind;
    }

    /**
     * Positions for a structure on this map. A variant ("desert village") is its parent's list cut down to
     * the points that actually sit in the variant's biomes, so it is exact, not a guess.
     */
    private static synchronized Points points(String kind, BiomeGrid grid) {
        Lexicon.Item it = Lexicon.byId(kind);
        Points base = loadList(listKind(kind));
        if (base == null || it == null || it.pointsFrom == null || it.habitat == null || grid == null) return base;
        String key = kind + "@" + System.identityHashCode(grid) + "@" + base.modified;
        Points cut = VARIANTS.get(key);
        if (cut != null) return cut;
        boolean[] ok = maskOf(grid, Arrays.asList(it.habitat));
        int[] xs = new int[base.xs.length], zs = new int[base.xs.length];
        int n = 0;
        for (int i = 0; i < base.xs.length; i++) {
            int b = grid.at(grid.col(base.xs[i]), grid.col(base.zs[i]));
            if (b >= 0 && ok[b]) { xs[n] = base.xs[i]; zs[n] = base.zs[i]; n++; }
        }
        cut = new Points();
        cut.modified = base.modified;
        cut.xs = Arrays.copyOf(xs, n);
        cut.zs = Arrays.copyOf(zs, n);
        VARIANTS.put(key, cut);
        return cut;
    }

    private static final Map<String, Points> VARIANTS = new HashMap<>();

    private static synchronized Points loadList(String kind) {
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
        Run run = new Run(grid, c0, w, cancel, res);

        // who hangs from whom: the spot at the root, each thing from what it was described next to
        Map<Reader.Claim, List<Reader.Claim>> kids = new IdentityHashMap<>();
        for (Reader.Claim c : claims) {
            if (c == target || !c.searchable()) continue;
            Reader.Claim p = c.parent;
            if (p == null || p == c || !claims.contains(p)) p = target;
            kids.computeIfAbsent(p, k -> new ArrayList<>()).add(c);
        }
        run.kids = kids;

        // the target itself
        byte[] pass = run.typeMask(target);
        Map<Integer, int[]> exact = new HashMap<>();
        for (String kind : target.structures) {
            Points p = points(kind, grid);
            if (p == null) continue;
            for (int i = 0; i < p.xs.length; i++) {
                int col = grid.col(p.xs[i]) - c0, row = grid.col(p.zs[i]) - c0;
                if (col < 0 || row < 0 || col >= w || row >= w) continue;
                exact.putIfAbsent(row * w + col, new int[]{p.xs[i], p.zs[i]});
            }
        }
        note(res, label(target), count(pass));
        for (Reader.Claim ch : kids.getOrDefault(target, List.of())) {
            if (run.stop()) { res.cancelled = true; return finish(res, t0); }
            byte[] cond = run.condition(ch);
            if (cond == null) { res.cancelled = true; return finish(res, t0); }
            for (int i = 0; i < pass.length; i++) if (cond[i] == 0) pass[i] = 0;
            run.release(cond);
            note(res, label(ch) + descendants(kids, ch), count(pass));
        }

        // group what is left into regions
        res.cells = count(pass);
        boolean[] trunc = new boolean[1];
        res.regions.addAll(regions(grid, pass, w, c0, exact, cancel, trunc));
        res.truncated = trunc[0];
        res.regions.sort(Comparator.comparingDouble(a -> a.dist));
        if (cancel != null && cancel.cancelled()) res.cancelled = true;
        return finish(res, t0);
    }

    private static String descendants(Map<Reader.Claim, List<Reader.Claim>> kids, Reader.Claim c) {
        int n = 0;
        Deque<Reader.Claim> q = new ArrayDeque<>(kids.getOrDefault(c, List.of()));
        while (!q.isEmpty()) { Reader.Claim x = q.poll(); n++; q.addAll(kids.getOrDefault(x, List.of())); }
        return n == 0 ? "" : "  (+" + n + " linked)";
    }

    /** One search over the map: the arrays it needs, and the tree of what must hold around each cell. */
    private static final class Run {
        final BiomeGrid grid;
        final int c0, w;
        final Cancel cancel;
        final Result res;
        Map<Reader.Claim, List<Reader.Claim>> kids;
        private final ArrayDeque<byte[]> pool = new ArrayDeque<>();
        private final byte[] tmp;

        Run(BiomeGrid grid, int c0, int w, Cancel cancel, Result res) {
            this.grid = grid; this.c0 = c0; this.w = w; this.cancel = cancel; this.res = res;
            this.tmp = new byte[w * w];
        }

        boolean stop() { return cancel != null && cancel.cancelled(); }

        byte[] get() {
            byte[] a = pool.poll();
            if (a == null) return new byte[w * w];
            Arrays.fill(a, (byte) 0);
            return a;
        }

        void release(byte[] a) { if (a != null && pool.size() < 6) pool.add(a); }

        /** 1 where this kind of thing is (biome cells, and the cells holding a listed structure). */
        byte[] typeMask(Reader.Claim c) {
            byte[] out = get();
            boolean[] m = maskOf(grid, c.biomes);
            boolean any = false;
            for (boolean b : m) if (b) { any = true; break; }
            if (any) {
                for (int row = 0; row < w; row++) {
                    int base = row * w;
                    for (int col = 0; col < w; col++) {
                        int b = grid.at(c0 + col, c0 + row);
                        if (b >= 0 && m[b]) out[base + col] = 1;
                    }
                }
            }
            for (String kind : c.structures) {
                Points p = points(kind, grid);
                if (p == null) continue;
                for (int i = 0; i < p.xs.length; i++) {
                    int col = grid.col(p.xs[i]) - c0, row = grid.col(p.zs[i]) - c0;
                    if (col < 0 || row < 0 || col >= w || row >= w) continue;
                    out[row * w + col] = 1;
                }
            }
            return out;
        }

        /** Cells where this thing is, and where everything described from it holds too. */
        byte[] where(Reader.Claim c) {
            byte[] acc = typeMask(c);
            for (Reader.Claim ch : kids.getOrDefault(c, List.of())) {
                if (stop()) return acc;
                byte[] cond = condition(ch);
                if (cond == null) return acc;
                for (int i = 0; i < acc.length; i++) if (cond[i] == 0) acc[i] = 0;
                release(cond);
            }
            return acc;
        }

        /** 1 for every cell that has this thing, in the way it was described, around it. Null if stopped. */
        byte[] condition(Reader.Claim c) {
            if (stop()) return null;
            byte[] src = c.role == Reader.Role.ABSENT ? typeMask(c) : where(c);
            if (stop()) { release(src); return null; }
            int r = Math.max(1, (int) Math.ceil(c.within / (double) grid.step));
            byte[] inner = null;
            if (c.minWithin > 0 && c.role != Reader.Role.ABSENT) {
                inner = get();
                System.arraycopy(src, 0, inner, 0, src.length);
                dilate(inner, tmp, w, Math.max(1, (int) Math.ceil(c.minWithin / (double) grid.step)));
            }
            dilate(src, tmp, w, r);
            if (inner != null) { for (int i = 0; i < src.length; i++) if (inner[i] != 0) src[i] = 0; release(inner); }
            if (c.role == Reader.Role.ABSENT) for (int i = 0; i < src.length; i++) src[i] = (byte) (src[i] != 0 ? 0 : 1);
            return src;
        }
    }

    private static Result finish(Result r, long t0) { r.ms = System.currentTimeMillis() - t0; return r; }

    private static void note(Result r, String text, long left) { r.steps.add(text); r.stepCounts.add(left); }

    private static String label(Reader.Claim c) {
        String name = c.label();
        String by = c.approximate ? " (by its biome)" : "";
        String of = c.parent != null ? " " + c.parent.label() : "";
        if (c.role == Reader.Role.ABSENT) return "no " + name + " within " + commas(c.within) + " blocks" + (c.parent != null ? " of" + of : "");
        if (c.role == Reader.Role.TARGET) return name + by;
        if (c.parent != null && c.relation != null && (c.relation.equals("touching") || c.relation.equals("in") || c.relation.equals("at") || c.relation.equals("next to")))
            return name + by + " " + c.relation + of;
        if (c.minWithin > 0) return name + " " + commas(c.minWithin) + " to " + commas(c.within) + " blocks" + (c.parent != null ? " from" + of : "") + by;
        return name + " within " + commas(c.within) + " blocks" + (c.parent != null ? " of" + of : "") + by;
    }

    private static String commas(long v) { return String.format("%,d", v); }

    /** The biome ids this map holds, without the "minecraft:" prefix, by index. */
    static String[] idsOf(BiomeGrid grid) {
        String[] out = new String[grid.biomes.length];
        for (int i = 0; i < out.length; i++) {
            String s = grid.biomes[i];
            int c = s.indexOf(':');
            out[i] = c >= 0 ? s.substring(c + 1) : s;
        }
        return out;
    }

    /** Whether this map holds exactly that biome (the grid's own lookup also matches "ocean" to every ocean). */
    public static boolean known(BiomeGrid grid, String id) {
        for (String s : idsOf(grid)) if (s.equals(id)) return true;
        return false;
    }

    /** Exact biome match: "ocean" is the ocean and nothing else, "warm_ocean" is the warm ocean and nothing else. */
    static boolean[] maskOf(BiomeGrid grid, List<String> ids) {
        boolean[] out = new boolean[256];
        String[] have = idsOf(grid);
        for (String id : ids) for (int i = 0; i < have.length && i < 256; i++) if (have[i].equals(id)) out[i] = true;
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

    private static List<Region> regions(BiomeGrid grid, byte[] pass, int w, int c0, Map<Integer, int[]> exact, Cancel cancel, boolean[] truncatedFlag) {
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
            if (out.size() > 20000) { truncatedFlag[0] = true; break; }
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
