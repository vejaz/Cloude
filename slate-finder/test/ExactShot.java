package slatefinder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.jar.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Opens the map window on a made-up world, presses "Make it exact" with the stand-in server, and saves pictures of each stage. */
public final class ExactShot {
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
        File tmp = Files.createTempDirectory("slate-exactshot").toFile();
        File gridFile = new File(tmp, "grid.bin");
        DescribeTestWorld.write(gridFile, 20000, 64, 777);
        BiomeGrid grid = BiomeGrid.load(gridFile);
        // the stand-in server
        File fake = new File(tmp, "fake-server.jar");
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "slatefinder.FakeMcServer");
        try (JarOutputStream jo = new JarOutputStream(new FileOutputStream(fake), mf)) {
            for (String cn : new String[]{"FakeMcServer", "ExactTestWorld", "ExactTestWorld$Fn", "ExactTestWorld$Demo", "ExactTestWorld$W"}) {
                try (InputStream in = ExactShot.class.getResourceAsStream("/slatefinder/" + cn + ".class")) { jo.putNextEntry(new JarEntry("slatefinder/" + cn + ".class")); jo.write(in.readAllBytes()); jo.closeEntry(); }
            }
        }
        ExactStore.deleteTree(ExactStore.root());
        ExactJob.testServer = fake;
        Settings.set("exactConsent", true);
        Settings.set("exactSize", 80);
        String mc = System.getenv("MCJAR");
        if (mc != null) Settings.set("mcJar", mc);
        Settings.set("mapMode", "3d");

        Reader.Reading rd = Reader.read("plains next to a river", grid);
        final MapWindow[] mw = new MapWindow[1];
        SwingUtilities.invokeAndWait(() -> { mw[0] = MapWindow.open(null, grid, rd, 30, 30); mw[0].setSize(1500, 900); });
        settle(2500);
        ImageIO.write(snap(mw[0].getRootPane()), "png", new File(out, "x1-before.png"));
        SwingUtilities.invokeAndWait(() -> mw[0].exactCard().press());
        settle(1200);
        ImageIO.write(snap(mw[0].getRootPane()), "png", new File(out, "x2-working.png"));
        long t0 = System.currentTimeMillis();
        while (!mw[0].exactCard().isReady() && System.currentTimeMillis() - t0 < 90000) Thread.sleep(300);
        System.out.println("ready after " + (System.currentTimeMillis() - t0) + " ms: " + mw[0].exactCard().statusText().replaceAll("<[^>]+>", ""));
        settle(2500);
        ImageIO.write(snap(mw[0].getRootPane()), "png", new File(out, "x3-exact-3d.png"));
        SwingUtilities.invokeAndWait(() -> mw[0].setMode(2));
        settle(2000);
        ImageIO.write(snap(mw[0].getRootPane()), "png", new File(out, "x4-exact-map.png"));
        SwingUtilities.invokeAndWait(() -> mw[0].setMode(1));
        settle(2500);
        ImageIO.write(snap(mw[0].getRootPane()), "png", new File(out, "x5-exact-walk.png"));
        // a second window on the same spot opens the saved copy by itself
        SwingUtilities.invokeAndWait(() -> mw[0].dispose());
        final MapWindow[] m2 = new MapWindow[1];
        SwingUtilities.invokeAndWait(() -> { m2[0] = MapWindow.open(null, grid, rd, 40, 20); m2[0].setSize(1500, 900); });
        t0 = System.currentTimeMillis();
        while (!m2[0].exactCard().isReady() && System.currentTimeMillis() - t0 < 20000) Thread.sleep(200);
        System.out.println("second window ready by itself after " + (System.currentTimeMillis() - t0) + " ms: " + m2[0].exactCard().isReady());
        settle(2000);
        ImageIO.write(snap(m2[0].getRootPane()), "png", new File(out, "x6-reopened.png"));
        // settings with the storage card
        final SettingsDialog[] sd = new SettingsDialog[1];
        SwingUtilities.invokeAndWait(() -> { sd[0] = new SettingsDialog(m2[0]); sd[0].setVisible(true); });
        settle(2500);
        ImageIO.write(snap(sd[0].getRootPane()), "png", new File(out, "x7-settings.png"));
        // the whole of the settings window, not just what fits on screen
        SwingUtilities.invokeAndWait(() -> {
            JScrollPane sp = (JScrollPane) sd[0].getContentPane();
            Component v = sp.getViewport().getView();
            v.setSize(440, v.getPreferredSize().height);
            v.doLayout();
            for (Component c : ((Container) v).getComponents()) c.doLayout();
            BufferedImage im = new BufferedImage(440, v.getPreferredSize().height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            g.setColor(Theme.BG); g.fillRect(0, 0, im.getWidth(), im.getHeight());
            v.paint(g); g.dispose();
            try { ImageIO.write(im, "png", new File(out, "x8-settings-full.png")); } catch (IOException e) { throw new RuntimeException(e); }
        });
        System.out.println("done");
        System.exit(m2[0].exactCard().isReady() ? 0 : 5);
    }
}
