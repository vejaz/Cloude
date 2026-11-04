package slatefinder;

import java.io.File;
import java.util.*;

/** Full-size map (radius 225,000, step 64: 7,032 x 7,032 cells): timing and memory of real searches. */
public final class FullScale {
    public static void main(String[] a) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "slate-fullscale");
        File gf = new File(dir, "grid.bin");
        if (!gf.isFile()) DescribeTestWorld.write(gf, 225000, 64, 4242);
        BiomeGrid g = BiomeGrid.load(gf);
        System.out.println("grid " + g.cols() + "x" + g.cols());
        String[] qs = {
            "plains", "plains with a river within 300 and snowy mountains far away", "desert with no ocean and no jungle, mountains far away",
            "swamp next to a river, dark forest in the distance, taiga and ocean far away", "desert pyramid", "ocean monument next to a beach",
            "plains, a village, snowy mountains and a river", "forest within 20000 blocks of a desert"
        };
        for (String q : qs) {
            Reader.Reading r = Reader.read(q, g);
            long t = System.currentTimeMillis();
            PlaceSearch.Result res = PlaceSearch.run(g, r.searchable(), 225000, null);
            long used = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20;
            System.out.printf("%-80s %6d ms  %,10d cells  %,6d regions  heap %d MB%n", q, System.currentTimeMillis() - t, res.cells, res.regions.size(), used);
        }
        // cancel works promptly
        Reader.Reading r = Reader.read("plains with a river and snowy mountains and forest and ocean", g);
        long t = System.currentTimeMillis();
        final long start = t;
        PlaceSearch.Result res = PlaceSearch.run(g, r.searchable(), 225000, () -> System.currentTimeMillis() - start > 300);
        System.out.println("cancelled=" + res.cancelled + " after " + (System.currentTimeMillis() - t) + " ms");
    }
}
