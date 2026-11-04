package slatefinder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Saves screenshots of the two older tabs, to compare one jar against another. */
public final class TabCompare {
    public static void main(String[] a) throws Exception {
        File out = new File(a[0]);
        out.mkdirs();
        final App[] app = new App[1];
        SwingUtilities.invokeAndWait(() -> { app[0] = new App(); app[0].setSize(1420, 900); app[0].setLocation(0, 0); app[0].setVisible(true); });
        for (int tab = 0; tab < 3; tab++) {
            final int t = tab;
            SwingUtilities.invokeAndWait(() -> app[0].setTab(t));
            Thread.sleep(1800);
            SwingUtilities.invokeAndWait(() -> { });
            JRootPane rp = app[0].getRootPane();
            BufferedImage im = new BufferedImage(rp.getWidth(), rp.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            rp.paint(g);
            g.dispose();
            ImageIO.write(im, "png", new File(out, "tab" + tab + ".png"));
        }
        System.exit(0);
    }
}
