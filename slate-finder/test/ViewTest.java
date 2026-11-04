package slatefinder;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;

/** The 3D view's behaviour: zoom toward the pointer, pins that stay on the ground while the picture catches up, click to stand there, quick first pictures. */
public final class ViewTest {
    static int fails = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { fails++; System.out.println("FAIL: " + what); } }
    static void settle(int ms) throws Exception { Thread.sleep(ms); SwingUtilities.invokeAndWait(() -> { }); }

    static void waitIdle(WorldView v, int ms) throws Exception {
        long t0 = System.currentTimeMillis();
        Thread.sleep(60);
        while (v.isBusy() && System.currentTimeMillis() - t0 < ms) Thread.sleep(25);
    }

    static void press(WorldView v, int x, int y) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            v.dispatchEvent(new MouseEvent(v, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
            v.dispatchEvent(new MouseEvent(v, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
        });
    }

    public static void main(String[] a) throws Exception {
        if (!DescribeTestWorld.sandboxed()) { System.out.println("Refusing to run: APPDATA must point inside the temp folder."); System.exit(3); }
        File gridFile = new File(System.getenv("APPDATA"), "SlateFinder/biome-grid-64.bin");
        if (!gridFile.isFile()) DescribeTestWorld.write(gridFile, 60000, 64, 777);
        BiomeGrid grid = BiomeGrid.load(gridFile);
        Reader.Reading rd = Reader.read("plains next to a river", grid);
        final MapWindow[] mw = new MapWindow[1];
        Settings.set("mapMode", "3d");
        SwingUtilities.invokeAndWait(() -> { mw[0] = MapWindow.open(null, grid, rd, 1200, -800); mw[0].setSize(1500, 900); });
        settle(2500);
        WorldView v = mw[0].worldView();
        waitIdle(v, 20000);
        int w = v.getWidth(), h = v.getHeight();
        check(w > 300 && h > 300, "the view has a size (" + w + "x" + h + ")");

        // --- zoom toward the pointer (3D)
        int[][] cursors = {{w / 4, h / 2}, {3 * w / 4, h / 3}, {w / 2, 2 * h / 3}, {w / 5, h / 5}};
        for (int[] c : cursors) {
            for (double rot : new double[]{-1, -1, 1}) {          // in, in, out
                final double[][] before = new double[1][];
                final double[] scr0 = new double[2], scr1 = new double[2];
                final double[] dist = new double[2];
                SwingUtilities.invokeAndWait(() -> {
                    before[0] = v.caster.pick(v.cam(), w, h, c[0], c[1], v.opts);
                    dist[0] = v.dist;
                });
                if (before[0] == null) continue;
                SwingUtilities.invokeAndWait(() -> {
                    v.zoom(rot, c[0], c[1]);
                    double[] p = Caster.project(v.cam(), w, h, before[0][0], before[0][1], before[0][2]);
                    scr1[0] = p == null ? -9999 : p[0]; scr1[1] = p == null ? -9999 : p[1];
                    dist[1] = v.dist;
                });
                double off = Math.hypot(scr1[0] - c[0], scr1[1] - c[1]);
                // what plain zoom (about the middle of the picture) would have left: the point drifts away from the pointer
                double plainOff = Math.hypot((c[0] - w / 2.0) * (1 - dist[0] / dist[1]), (c[1] - h / 2.0) * (1 - dist[0] / dist[1]));
                check(dist[1] != dist[0], "the wheel changes the distance (" + dist[0] + " -> " + dist[1] + ")");
                check(off < Math.max(25, plainOff * 0.35), "the point under the pointer " + Arrays.toString(c) + " stays put: off by " + (int) off + " px (plain zoom would be about " + (int) plainOff + ")");
            }
        }

        // --- zoom toward the pointer (standing)
        SwingUtilities.invokeAndWait(() -> { v.goTo(1200, -800, "test"); });
        settle(300);
        for (int[] c : new int[][]{{w / 4, h / 2}, {3 * w / 4, h / 3}}) {
            final double[] res = new double[2];
            final double[] dir = new double[3];
            SwingUtilities.invokeAndWait(() -> {
                // the direction of the ray under the pointer, found by turning a far point under the cursor into a world direction
                double[] p = v.caster.pick(v.cam(), w, h, c[0], c[1], v.opts);
                dir[0] = p == null ? Double.NaN : p[0]; dir[1] = p == null ? 0 : p[1]; dir[2] = p == null ? 0 : p[2];
                v.zoom(-1, c[0], c[1]);
                double[] s = dir[0] != dir[0] ? null : Caster.project(v.cam(), w, h, dir[0], dir[1], dir[2]);
                res[0] = s == null ? -9999 : s[0]; res[1] = s == null ? -9999 : s[1];
            });
            if (dir[0] == dir[0]) check(Math.hypot(res[0] - c[0], res[1] - c[1]) < 30, "standing: the point under the pointer stays under it: off by " + (int) Math.hypot(res[0] - c[0], res[1] - c[1]) + " px");
        }
        SwingUtilities.invokeAndWait(() -> v.setMode(WorldView.Mode.ORBIT));
        SwingUtilities.invokeAndWait(() -> v.home());
        waitIdle(v, 20000);

        // --- a picture shows up quickly, whatever the detail
        final int f0 = v.framesDrawn();
        long t0 = System.currentTimeMillis();
        SwingUtilities.invokeAndWait(() -> v.zoom(-1, w / 2, h / 2));
        while (System.currentTimeMillis() - t0 < 5000 && v.framesDrawn() == f0) Thread.sleep(5);
        long quick = System.currentTimeMillis() - t0;
        int got = v.stepDone();
        System.out.println("first picture after a zoom: " + quick + " ms (drawn in blocks of " + got + " px)");
        check(v.framesDrawn() > f0 && quick < 1000, "a picture of the new zoom is up within 1 s (" + quick + " ms)");
        waitIdle(v, 20000);
        SwingUtilities.invokeAndWait(() -> v.snapshot());

        // --- pins and names stay on the ground while the picture is behind
        SwingUtilities.invokeAndWait(() -> v.snapshot());
        List<double[]> hot = v.hotSpots();
        check(!hot.isEmpty(), "pins and dots are drawn and can be clicked (" + hot.size() + ")");
        if (!hot.isEmpty()) {
            double[] spot = null;
            for (double[] hs : hot) if (hs[3] == 1200 && hs[4] == -800) spot = hs;
            check(spot != null, "the marked spot is one of them");
            final double ox = spot[0], oy = spot[1];
            final double oyaw = v.oyaw;
            // turn the camera without drawing a new picture: the overlay must follow the picture, not the camera
            SwingUtilities.invokeAndWait(() -> { v.oyaw += Math.toRadians(40); v.snapshot(); });
            double[] spot2 = null;
            for (double[] hs : v.hotSpots()) if (hs[3] == 1200 && hs[4] == -800) spot2 = hs;
            check(spot2 != null && Math.hypot(spot2[0] - ox, spot2[1] - oy) < 1.5, "the pin did not move when only the camera did: " + (spot2 == null ? "gone" : (int) Math.hypot(spot2[0] - ox, spot2[1] - oy) + " px"));
            SwingUtilities.invokeAndWait(() -> { v.oyaw = oyaw; });
        }

        // --- click a pin: stand there; drag from a pin: do not
        SwingUtilities.invokeAndWait(() -> { v.setMode(WorldView.Mode.ORBIT); v.home(); });
        waitIdle(v, 20000);
        SwingUtilities.invokeAndWait(() -> v.snapshot());
        double[] target = null;
        for (double[] hs : v.hotSpots()) if (hs[3] == 1200 && hs[4] == -800 && hs[0] > 20 && hs[0] < w - 20 && hs[1] > 20 && hs[1] < h - 20) target = hs;
        check(target != null, "the marked spot's pin is on screen to click");
        if (target != null) {
            final double[] tg = target;
            SwingUtilities.invokeAndWait(() -> {
                v.dispatchEvent(new MouseEvent(v, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, (int) tg[0], (int) tg[1], 1, false, MouseEvent.BUTTON1));
                v.dispatchEvent(new MouseEvent(v, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0, (int) tg[0] + 40, (int) tg[1] + 10, 1, false, MouseEvent.BUTTON1));
                v.dispatchEvent(new MouseEvent(v, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, (int) tg[0] + 40, (int) tg[1] + 10, 1, false, MouseEvent.BUTTON1));
            });
            check(v.mode == WorldView.Mode.ORBIT, "dragging from a pin only turns the view");
            press(v, (int) tg[0], (int) tg[1]);
            check(v.mode == WorldView.Mode.WALK, "clicking the pin stands you there");
            check(Math.abs(v.wx - 1200.5) < 0.01 && Math.abs(v.wz + 799.5) < 0.01, "at the pin's coordinates: " + v.wx + ", " + v.wz);
            settle(300);
            waitIdle(v, 20000);
        }
        System.out.println(checks + " checks, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
