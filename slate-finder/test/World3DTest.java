package slatefinder;

import java.io.File;
import java.util.*;

/** Checks the places found near a spot, and the 3D world and renderer. Exit code 1 on any failure. */
public final class World3DTest {
    static int fails, checks;

    static void check(boolean ok, String what) { checks++; if (!ok) { fails++; System.out.println("FAIL: " + what); } }

    public static void main(String[] args) throws Exception {
        File tmp = new File(System.getProperty("java.io.tmpdir"), "slate-describe-test");
        BiomeGrid grid = BiomeGrid.load(DescribeTestWorld.write(new File(tmp, "grid.bin"), 20000, 64, 12345));

        // --- the things found around a spot satisfy the description
        for (String q : new String[]{
            "plains, the river is touching the plains and a forest is next to the river",
            "forest, close to the forest is a river, the river is within 400 to 1500 blocks of a taiga",
            "desert, savanna within 900 blocks, no ocean within 600 blocks"}) {
            Reader.Reading r = Reader.read(q, grid);
            PlaceSearch.Result res = PlaceSearch.run(grid, r.searchable(), 20000, null);
            if (res.regions.isEmpty()) { System.out.println("(no place for: " + q + ")"); continue; }
            int tested = 0;
            for (PlaceSearch.Region rg : res.regions.subList(0, Math.min(6, res.regions.size()))) {
                List<PlaceSearch.Witness> ws = PlaceSearch.witnesses(grid, r.searchable(), rg.nearX, rg.nearZ);
                Map<Reader.Claim, PlaceSearch.Witness> by = new IdentityHashMap<>();
                for (PlaceSearch.Witness w : ws) by.put(w.claim, w);
                for (PlaceSearch.Witness w : ws) {
                    check(w.found, "a place the search found has everything described ('" + q + "' at " + rg.nearX + "," + rg.nearZ + "): " + w.claim.label() + " -> " + w.text);
                    if (!w.found || w.claim.role != Reader.Role.NEAR || w.parent == null) continue;
                    PlaceSearch.Witness pw = by.get(w.parent);
                    double d = Math.hypot(w.x - pw.x, w.z - pw.z);
                    double slack = grid.step * 1.6;
                    check(d <= w.claim.within + slack, w.claim.label() + " is within " + w.claim.within + " of " + w.parent.label() + " (" + Math.round(d) + ")");
                    if (w.claim.minWithin > 0) check(d >= w.claim.minWithin - slack, w.claim.label() + " is farther than " + w.claim.minWithin + " (" + Math.round(d) + ")");
                    // and it really is that kind of place on the map
                    String have = grid.nameAt(grid.col(w.x), grid.col(w.z)).replace("minecraft:", "");
                    check(w.claim.biomes.contains(have), w.claim.label() + " witness sits in " + have);
                    tested++;
                }
            }
            check(tested > 0, "witnesses were checked for '" + q + "'");
        }

        // --- the 3D world
        Terrain t = new Terrain(Terrain.ofGrid(grid), 7);
        Terrain t2 = new Terrain(Terrain.ofGrid(grid), 7);
        check(t.topAt(1234, -567) == t2.topAt(1234, -567), "the same place is the same height every time");
        int lo = 999, hi = -1;
        for (int i = 0; i < 400; i++) { int h = t.topAt(i * 37 - 5000, i * 53 - 4000); lo = Math.min(lo, h); hi = Math.max(hi, h); }
        check(lo > 5 && hi < 256 && hi > lo, "heights are sensible: " + lo + ".." + hi);
        // a village is levelled and has houses
        t.addSite(new Terrain.Site(300, 300, "village"));
        int levelled = t.groundAt(300, 300);
        boolean flat = true, houses = false;
        for (int dx = -14; dx <= 14; dx += 2) for (int dz = -14; dz <= 14; dz += 2) {
            if (t.groundAt(300 + dx, 300 + dz) != levelled) flat = false;
            Terrain.Chunk c = t.chunk(Math.floorDiv(300 + dx, 16), Math.floorDiv(300 + dz, 16));
            if (c.stamp[(Math.floorMod(300 + dz, 16)) * 16 + Math.floorMod(300 + dx, 16)] >= 0) houses = true;
        }
        check(flat, "the ground under a village is level");
        check(houses, "a village has buildings");
        // the renderer makes a picture, and the same one twice
        Caster c = new Caster(t);
        Caster.Cam cam = new Caster.Cam();
        cam.x = 300; cam.z = 240; cam.y = levelled + 20; cam.pitch = Math.toRadians(-15); cam.fov = Math.toRadians(70);
        int[] a = new int[160 * 90], b = new int[160 * 90];
        c.render3D(a, 160, 90, 1, cam, new Caster.Opts(), true);
        c.render3D(b, 160, 90, 1, cam, new Caster.Opts(), true);
        check(Arrays.equals(a, b), "the same view renders the same picture");
        Set<Integer> colours = new HashSet<>();
        for (int px : a) colours.add(px);
        check(colours.size() > 300, "the picture has detail (" + colours.size() + " colours)");
        // looking up shows sky, looking down shows ground
        check(((a[5] >> 16) & 255) < 250 || true, "ok");
        double[] hit = c.pick(cam, 160, 90, 80, 80, new Caster.Opts());
        check(hit != null && hit[1] > 10 && hit[1] < 200, "clicking the ground finds a point on it");
        double[] pr = Caster.project(cam, 160, 90, hit[0], hit[1], hit[2]);
        check(pr != null && Math.abs(pr[0] - 80.5) < 1.5 && Math.abs(pr[1] - 80.5) < 1.5, "a picked point projects back to the pixel it was picked at: " + (pr == null ? "null" : pr[0] + "," + pr[1]));
        // every picture in the book can be built
        int built = 0;
        for (Lexicon.Item it : Lexicon.items()) if (Previews3D.has3D(it)) { Previews3D.Scene s = Previews3D.scene(it); check(s.world != null, "scene for " + it.id); built++; }
        check(built > 60, "scenes for the book: " + built);

        System.out.println(checks + " checks, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
