package slatefinder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Screenshots of "coordinates next to the village and the river" and of the Test coordinates window. Run under xvfb with APPDATA set. */
public final class AnywhereShot {
    static BufferedImage snap(Component c) {
        BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        c.paint(g);
        g.dispose();
        return im;
    }
    static void settle(int ms) throws Exception { Thread.sleep(ms); SwingUtilities.invokeAndWait(() -> { }); }

    public static void main(String[] a) throws Exception {
        if (!DescribeTestWorld.sandboxed()) { System.out.println("Refusing to run: APPDATA must point inside the temp folder."); System.exit(3); }
        File out = new File(a[0]);
        out.mkdirs();
        File gridFile = new File(System.getenv("APPDATA"), "SlateFinder/biome-grid-64.bin");
        if (!gridFile.isFile()) DescribeTestWorld.write(gridFile, 60000, 64, 777);
        BiomeGrid g0 = BiomeGrid.load(gridFile);
        if (!Landmarks.fileFor("village").isFile()) DescribeTestWorld.writeVillages(Landmarks.fileFor("village"), g0, 5);
        final App[] app = new App[1];
        SwingUtilities.invokeAndWait(() -> { app[0] = new App(); app[0].setSize(1420, 900); app[0].setLocation(0, 0); app[0].setVisible(true); app[0].setTab(2); });
        settle(2500);
        SwingUtilities.invokeAndWait(() -> app[0].describe.setDescription("I want the coordinates next to the village and the river"));
        settle(1500);
        ImageIO.write(snap(app[0].getRootPane()), "png", new File(out, "a1-anywhere.png"));
        SwingUtilities.invokeAndWait(() -> app[0].describe.runSearchNow());
        settle(4000);
        ImageIO.write(snap(app[0].getRootPane()), "png", new File(out, "a2-anywhere-results.png"));
        final ReverseDialog[] dlg = new ReverseDialog[1];
        SwingUtilities.invokeAndWait(() -> { dlg[0] = app[0].describe.openReverse(); dlg[0].prefill(8474, 4424); dlg[0].start(); });
        settle(11000);
        ImageIO.write(snap(dlg[0].getRootPane()), "png", new File(out, "a3-test-coordinates.png"));
        System.out.println("done");
        System.exit(0);
    }
}
