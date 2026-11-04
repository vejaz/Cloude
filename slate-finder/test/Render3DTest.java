package slatefinder;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Renders a few 3D views of the made-up world and of single biomes to PNG files, for looking at. */
public final class Render3DTest {
    static void save(int[] px, int w, int h, String path) throws Exception {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        im.setRGB(0, 0, w, h, px, 0, w);
        ImageIO.write(im, "png", new File(path));
    }

    public static void main(String[] a) throws Exception {
        File tmp = new File(System.getProperty("java.io.tmpdir"), "slate-describe-test");
        File gf = DescribeTestWorld.write(new File(tmp, "grid.bin"), 20000, 64, 12345);
        BiomeGrid g = BiomeGrid.load(gf);
        Terrain t = new Terrain(Terrain.ofGrid(g), 7);
        Caster c = new Caster(t);
        Caster.Opts o = new Caster.Opts();
        int w = 1100, h = 620;
        int[] px = new int[w * h];
        // find a spot with a few biomes around
        int mx = 4300, mz = -700;
        // look for a place with a coast and several biomes close together
        String want = a.length > 1 ? a[1] : "";
        if (!want.isEmpty()) {
            outer:
            for (int rr = 0; rr < 400; rr++) for (int zz = -120; zz < 120; zz += 3) for (int xx = -120; xx < 120; xx += 3) {
                int col = g.col(xx * 64), row = g.col(zz * 64);
                if (col < 0 || row < 0) continue;
                java.util.Set<String> seen = new java.util.HashSet<>();
                for (int dz = -3; dz <= 3; dz++) for (int dx = -3; dx <= 3; dx++) seen.add(g.nameAt(col + dx, row + dz).replace("minecraft:", ""));
                if (seen.contains(want) && seen.size() >= 4) { mx = xx * 64; mz = zz * 64; break outer; }
            }
        }
        Caster.Cam cam = new Caster.Cam();
        cam.x = mx - 120; cam.z = mz - 120; cam.y = t.topAt(mx, mz) + 90; cam.yaw = Math.toRadians(45); cam.pitch = Math.toRadians(-28); cam.fov = Math.toRadians(70);
        long t0 = System.currentTimeMillis();
        c.render3D(px, w, h, 1, cam, o, true);
        System.out.println("orbit " + (System.currentTimeMillis() - t0) + " ms, biome " + t.biomeName(mx, mz));
        save(px, w, h, a[0] + "/orbit.png");
        // standing there
        cam.x = mx; cam.z = mz; cam.y = t.topAt(mx, mz) + 1.62; cam.pitch = Math.toRadians(-4); cam.yaw = Math.toRadians(100);
        t0 = System.currentTimeMillis();
        o.maxDist = 260;
        c.render3D(px, w, h, 1, cam, o, true);
        System.out.println("standing " + (System.currentTimeMillis() - t0) + " ms");
        save(px, w, h, a[0] + "/standing.png");
        // flat map
        int[] mp = new int[w * h];
        t0 = System.currentTimeMillis();
        c.renderMap(mp, w, h, mx, mz, 1.2, o, false);
        System.out.println("map close " + (System.currentTimeMillis() - t0) + " ms");
        save(mp, w, h, a[0] + "/map-close.png");
        t0 = System.currentTimeMillis();
        c.renderMap(mp, w, h, mx, mz, 12, o, false);
        System.out.println("map far " + (System.currentTimeMillis() - t0) + " ms");
        save(mp, w, h, a[0] + "/map-far.png");
    }
}
