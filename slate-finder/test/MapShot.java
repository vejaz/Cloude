package slatefinder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Opens the map window for a description on the made-up world and saves screenshots of its three modes. Run under xvfb with APPDATA in the temp folder. */
public final class MapShot {
    static BufferedImage snap(JFrame f) {
        BufferedImage im = new BufferedImage(f.getRootPane().getWidth(), f.getRootPane().getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        f.getRootPane().paint(g);
        g.dispose();
        return im;
    }

    public static void main(String[] a) throws Exception {
        if (!DescribeTestWorld.sandboxed()) { System.out.println("Refusing: APPDATA must be inside the temp folder."); System.exit(3); }
        File out = new File(a[0]);
        out.mkdirs();
        File gf = new File(System.getenv("APPDATA"), "SlateFinder/biome-grid-64.bin");
        if (!gf.isFile()) DescribeTestWorld.write(gf, 60000, 64, 777);
        BiomeGrid g = BiomeGrid.load(gf);
        File vf = Landmarks.fileFor("village");
        if (!vf.isFile()) DescribeTestWorld.writeVillages(vf, g, 5);
        String q = a.length > 1 ? a[1] : "plains village, river within 600, forest next to the river";
        Reader.Reading r = Reader.read(q, g);
        PlaceSearch.Result res = PlaceSearch.run(g, r.searchable(), 60000, null);
        System.out.println("places: " + res.regions.size());
        PlaceSearch.Region rg = res.regions.get(Math.min(res.regions.size() - 1, a.length > 2 ? Integer.parseInt(a[2]) : 0));
        System.out.println("spot " + rg.nearX + " " + rg.nearZ + " " + rg.biome);
        final MapWindow[] w = new MapWindow[1];
        SwingUtilities.invokeAndWait(() -> w[0] = MapWindow.open(null, g, r, rg.nearX, rg.nearZ));
        Thread.sleep(9000);
        ImageIO.write(snap(w[0]), "png", new File(out, "m1-3d.png"));
        SwingUtilities.invokeAndWait(() -> w[0].setMode(2));
        Thread.sleep(5000);
        ImageIO.write(snap(w[0]), "png", new File(out, "m2-map.png"));
        SwingUtilities.invokeAndWait(() -> w[0].setMode(1));
        Thread.sleep(7000);
        ImageIO.write(snap(w[0]), "png", new File(out, "m3-stand.png"));
        System.exit(0);
    }
}
