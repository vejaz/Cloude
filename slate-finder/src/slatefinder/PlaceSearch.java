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
        /** Which map cells matched (kept so a coordinate can be asked about), and where that window starts. */
        byte[] mask;
        int c0, w;

        /** Whether the map cell holding this coordinate matched the description. */
        public boolean contains(BiomeGrid grid, int x, int z) {
            if (mask == null) return false;
            int col = grid.col(x) - c0, row = grid.col(z) - c0;
            return grid.col(x) >= 0 && grid.col(z) >= 0 && col >= 0 && row >= 0 && col < w && row * w + col < mask.length && mask[row * w + col] != 0;
        }
    }

    // ------------------------------------------------------------------ position lists

    private static final class Points {
        long modified;
        int[] xs = new int[0], zs = new int[0];
    }

    private static final Map<String, Points> CACHE = new HashMap<>();

    /** True if a position list exists for this structure (variants such as "desert village" use their parent's list). */
    public static boolean hasPoints(String kind) {
        try { return Landmarks.fileFor(listKind(kind)).isFile() || StructureSeeds.supports(listKind(kind)); } catch (RuntimeException e) { return false; }
    }

    private static final Map<String, Points> COMPUTED = new HashMap<>();

    /** Positions worked out from the seed (the game's own structure placement), for structures with no list on disk. */
    private static synchronized Points computedList(String kind, BiomeGrid grid) {
        if (grid == null || !StructureSeeds.supports(kind)) return null;
        String key = kind + "@" + grid.seed + "@" + System.identityHashCode(grid);
        Points p = COMPUTED.get(key);
        if (p != null) return p;
        int[][] pos = StructureSeeds.positions(kind, grid);
        p = new Points();
        p.modified = grid.seed;
        p.xs = new int[pos.length];
        p.zs = new int[pos.length];
        for (int i = 0; i < pos.length; i++) { p.xs[i] = pos[i][0]; p.zs[i] = pos[i][1]; }
        COMPUTED.put(key, p);
        return p;
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
        if (base == null) base = computedList(listKind(kind), grid);
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
        res.mask = pass.clone();
        res.c0 = c0;
        res.w = w;
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
        private short[] dist;

        Run(BiomeGrid grid, int c0, int w, Cancel cancel, Result res) {
            this.grid = grid; this.c0 = c0; this.w = w; this.cancel = cancel; this.res = res;
            this.tmp = new byte[w * w];
        }

        void disc(byte[] a, int r) {
            if (r > 2 && dist == null) dist = new short[w * w];
            dilateDisc(a, tmp, dist, w, r);
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
                disc(inner, Math.max(1, (int) Math.ceil(c.minWithin / (double) grid.step)));
            }
            disc(src, r);
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

    /** Whether a cell offset is within r cells, as the circle the distance means (an octagon within 8% of one). */
    static boolean inKernel(int dx, int dz, int r) {
        dx = Math.abs(dx); dz = Math.abs(dz);
        if (r <= 2) return dx <= r && dz <= r;
        int a = (int) Math.round(0.414 * r), b = r - a;
        return Math.max(dx - a, 0) + Math.max(dz - a, 0) <= b;
    }

    /**
     * src becomes 1 wherever any 1 lies within r cells, as a circle: a square of the first part of the radius, then a
     * diamond of the rest (their sum is an octagon, close to a circle). Two sliding passes and two sweeps over the map.
     */
    static void dilateDisc(byte[] src, byte[] tmp, short[] dist, int w, int r) {
        if (r <= 2) { dilate(src, tmp, w, r); return; }
        int a = (int) Math.round(0.414 * r), b = r - a;
        if (a > 0) dilate(src, tmp, w, a);
        final short INF = Short.MAX_VALUE;
        int n = w * w;
        for (int i = 0; i < n; i++) dist[i] = src[i] != 0 ? 0 : INF;
        for (int z = 0; z < w; z++) {
            int base = z * w;
            for (int x = 0; x < w; x++) {
                int i = base + x;
                int d = dist[i];
                if (x > 0 && dist[i - 1] + 1 < d) d = dist[i - 1] + 1;
                if (z > 0 && dist[i - w] + 1 < d) d = dist[i - w] + 1;
                dist[i] = (short) Math.min(d, INF);
            }
        }
        for (int z = w - 1; z >= 0; z--) {
            int base = z * w;
            for (int x = w - 1; x >= 0; x--) {
                int i = base + x;
                int d = dist[i];
                if (x < w - 1 && dist[i + 1] + 1 < d) d = dist[i + 1] + 1;
                if (z < w - 1 && dist[i + w] + 1 < d) d = dist[i + w] + 1;
                dist[i] = (short) Math.min(d, INF);
                src[i] = (byte) (d <= b ? 1 : 0);
            }
        }
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

    // ------------------------------------------------------------------ where each described thing is, around a chosen spot

    /** One thing from the description, found near the chosen coordinates. */
    public static final class Witness {
        public final Reader.Claim claim;
        public boolean found;
        public int x, z;
        public double dist;
        public boolean exact;
        public Reader.Claim parent;
        public String text = "";
        Witness(Reader.Claim c) { this.claim = c; }
    }

    /**
     * For a place chosen on the map, finds where each biome and structure of the description actually is around it,
     * following the chain: each thing is looked for near the thing it was described next to.
     */
    public static List<Witness> witnesses(BiomeGrid grid, List<Reader.Claim> claims, int mx, int mz) {
        List<Witness> out = new ArrayList<>();
        Reader.Claim target = null;
        for (Reader.Claim c : claims) if (c.role == Reader.Role.TARGET && c.searchable()) { target = c; break; }
        if (target == null) return out;
        Map<Reader.Claim, List<Reader.Claim>> kids = new IdentityHashMap<>();
        int span = 0;
        for (Reader.Claim c : claims) {
            if (c == target || !c.searchable()) continue;
            Reader.Claim p = c.parent;
            if (p == null || p == c || !claims.contains(p)) p = target;
            kids.computeIfAbsent(p, k -> new ArrayList<>()).add(c);
        }
        span = depthSpan(kids, target);
        int step = grid.step;
        int R = Math.min(9000, span + 3 * step);
        int colM = grid.col(mx), rowM = grid.col(mz);
        if (colM < 0 || rowM < 0) return out;
        int reach = R / step + 2;
        int c0 = Math.max(0, Math.min(colM, rowM) - reach);
        int w = Math.min(grid.cols() - c0, Math.max(colM, rowM) - c0 + reach + 1);
        Result scratch = new Result();
        Run run = new Run(grid, c0, w, null, scratch);
        run.kids = kids;
        Map<Reader.Claim, Witness> byClaim = new IdentityHashMap<>();
        Witness tw = new Witness(target);
        tw.found = true; tw.x = mx; tw.z = mz; tw.exact = true; tw.text = "the spot";
        byClaim.put(target, tw);
        place(run, grid, target, colM - c0, rowM - c0, tw, byClaim, c0, w);
        for (Reader.Claim c : claims) {
            Witness x = byClaim.get(c);
            if (x == null) {
                x = new Witness(c);
                if (c.role == Reader.Role.ABSENT) { x.found = true; x.text = "none within " + String.format("%,d", c.within) + " blocks, as described"; }
                else x.text = "not found";
                byClaim.put(c, x);
            }
            out.add(x);
        }
        return out;
    }

    private static int depthSpan(Map<Reader.Claim, List<Reader.Claim>> kids, Reader.Claim n) {
        int best = 0;
        for (Reader.Claim ch : kids.getOrDefault(n, List.of())) best = Math.max(best, Math.max(1, ch.within) + depthSpan(kids, ch));
        return best;
    }

    private static void place(Run run, BiomeGrid grid, Reader.Claim node, int ac, int ar, Witness nodeW, Map<Reader.Claim, Witness> out, int c0, int w) {
        for (Reader.Claim ch : run.kids.getOrDefault(node, List.of())) {
            Witness cw = new Witness(ch);
            cw.parent = node;
            out.put(ch, cw);
            if (ch.role == Reader.Role.ABSENT) { cw.found = true; cw.text = "none within " + String.format("%,d", ch.within) + " blocks of " + node.label() + ", as described"; continue; }
            byte[] ok = run.where(ch);
            int r = Math.max(1, (int) Math.ceil(ch.within / (double) grid.step));
            int rmin = ch.minWithin > 0 ? Math.max(1, (int) Math.ceil(ch.minWithin / (double) grid.step)) : 0;
            double best = Double.MAX_VALUE;
            int bc = -1, br = -1;
            for (int dr = -r; dr <= r; dr++) {
                int rr = ar + dr;
                if (rr < 0 || rr >= w) continue;
                for (int dc = -r; dc <= r; dc++) {
                    int cc = ac + dc;
                    if (cc < 0 || cc >= w) continue;
                    if (!inKernel(dc, dr, r)) continue;
                    if (rmin > 0 && inKernel(dc, dr, rmin)) continue;
                    if (ok[rr * w + cc] == 0) continue;
                    double d = (double) dc * dc + (double) dr * dr;
                    if (d < best) { best = d; bc = cc; br = rr; }
                }
            }
            run.release(ok);
            if (bc < 0) { cw.text = "not found near " + node.label(); continue; }
            cw.found = true;
            cw.x = grid.worldX(c0 + bc);
            cw.z = grid.worldX(c0 + br);
            // a structure with a position list: the real position, the nearest one to the thing it hangs from
            if (!ch.structures.isEmpty()) {
                double bd = Double.MAX_VALUE;
                int ax = nodeW.x, az = nodeW.z;
                for (String kind : ch.structures) {
                    Points p = points(kind, grid);
                    if (p == null) continue;
                    for (int i = 0; i < p.xs.length; i++) {
                        double d = Math.hypot(p.xs[i] - cw.x, p.zs[i] - cw.z);
                        if (d < grid.step * 1.2 && d < bd) { bd = d; cw.x = p.xs[i]; cw.z = p.zs[i]; cw.exact = true; }
                    }
                }
            }
            cw.dist = Math.hypot(cw.x - nodeW.x, cw.z - nodeW.z);
            String how = ch.relation != null ? ch.relation : "near";
            cw.text = String.format("%s %s, %,.0f blocks away", how, node.label(), cw.dist);
            place(run, grid, ch, bc, br, cw, out, c0, w);
        }
    }

    /** The listed positions of a structure on this map (variants are cut down to their biomes). */
    public static int[][] listedPoints(String kind, BiomeGrid grid) {
        Points p = points(kind, grid);
        if (p == null) return new int[0][];
        int[][] out = new int[p.xs.length][];
        for (int i = 0; i < out.length; i++) out[i] = new int[]{p.xs[i], p.zs[i]};
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
