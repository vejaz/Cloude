package slatefinder;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Opens the real app, goes to the Describing tab, and saves screenshots. Run under xvfb with APPDATA set. */
public final class PanelShot {
    static BufferedImage snap(Component c) {
        BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        c.paint(g);
        g.dispose();
        return im;
    }

    static void settle(int ms) throws Exception { Thread.sleep(ms); SwingUtilities.invokeAndWait(() -> { }); }

    public static void main(String[] a) throws Exception {
        if (!DescribeTestWorld.sandboxed()) { System.out.println("Refusing to run: APPDATA must point inside the temp folder, so this cannot touch your real Slate Finder data."); System.exit(3); }
        File out = new File(a[0]);
        out.mkdirs();
        File appdata = new File(System.getenv("APPDATA"));
        File gridFile = new File(appdata, "SlateFinder/biome-grid-64.bin");
        if (!gridFile.isFile()) DescribeTestWorld.write(gridFile, 60000, 64, 777);
        BiomeGrid g0 = BiomeGrid.load(gridFile);
        if (!Landmarks.fileFor("village").isFile()) DescribeTestWorld.writeVillages(Landmarks.fileFor("village"), g0, 5);

        final App[] app = new App[1];
        SwingUtilities.invokeAndWait(() -> {
            app[0] = new App();
            app[0].setSize(1420, 900);
            app[0].setLocation(0, 0);
            app[0].setVisible(true);
            app[0].setTab(2);
        });
        settle(2500);
        JFrame f = app[0];
        SwingUtilities.invokeAndWait(() -> { app[0].describe.setDescription(""); });
        settle(400);
        ImageIO.write(snap(f.getRootPane()), "png", new File(out, "01-empty.png"));

        SwingUtilities.invokeAndWait(() -> app[0].describe.setDescription("plains vilage, plais biome, desert village, warm ocean, snowy mountains far away"));
        settle(1500);
        ImageIO.write(snap(f.getRootPane()), "png", new File(out, "02-understood.png"));

        SwingUtilities.invokeAndWait(() -> app[0].describe.setDescription("I am standing in a plans biome with a vilage next to it, snowy mountians in the distance, no desert, and a cow and a lake"));
        settle(1500);
        ImageIO.write(snap(f.getRootPane()), "png", new File(out, "03-errors.png"));

        SwingUtilities.invokeAndWait(() -> { app[0].describe.setDescription("plains next to a river, snowy mountains a bit further out"); app[0].describe.runSearchNow(); });
        settle(4000);
        ImageIO.write(snap(f.getRootPane()), "png", new File(out, "04-results.png"));

        // edit a distance and change the spot by hand, as a person would
        final boolean[] edit = new boolean[4];
        SwingUtilities.invokeAndWait(() -> app[0].describe.setDescription("there is plains and the river is touching the plains and a forest is next to the river and no desert within 800 blocks"));
        settle(900);
        SwingUtilities.invokeAndWait(() -> { edit[0] = app[0].describe.typeDistanceFor("Forest", "1.5k"); });
        settle(1200);
        ImageIO.write(snap(f.getRootPane()), "png", new File(out, "10-edited-distance.png"));
        SwingUtilities.invokeAndWait(() -> { app[0].describe.makeSpotFor("River"); });
        settle(1500);
        ImageIO.write(snap(f.getRootPane()), "png", new File(out, "11-river-is-the-spot.png"));
        SwingUtilities.invokeAndWait(() -> { Reader.Reading rr = app[0].describe.currentReading(); edit[1] = rr.target() != null && rr.target().label().equals("River"); for (Reader.Claim cc : rr.claims) if (cc.label().equals("Forest")) edit[2] = cc.within == 1500 && cc.parent != null && cc.parent.label().equals("River"); });
        System.out.println("typed distance accepted: " + edit[0] + ", river is the spot: " + edit[1] + ", forest kept 1500 from the river: " + edit[2]);
        if (!(edit[0] && edit[1] && edit[2])) System.exit(4);
        SwingUtilities.invokeAndWait(() -> app[0].describe.setBookFilter("Structures"));
        settle(600);
        ImageIO.write(snap(f.getRootPane()), "png", new File(out, "07-structures.png"));
        SwingUtilities.invokeAndWait(() -> { app[0].describe.setBookFilter("Nether & End"); });
        settle(400);
        ImageIO.write(snap(f.getRootPane()), "png", new File(out, "08-nether-end.png"));
        SwingUtilities.invokeAndWait(() -> { app[0].describe.setBookFilter("All"); app[0].describe.setBookQuery("mountians"); });
        settle(400);
        ImageIO.write(snap(f.getRootPane()), "png", new File(out, "09-search-typo.png"));
        SwingUtilities.invokeAndWait(() -> { app[0].describe.setBookQuery(""); });
        settle(300);
        // click a book picture: open the viewer, then press Escape
        SwingUtilities.invokeAndWait(() -> app[0].describe.bookGrid().onThumb.open(Lexicon.items(), 0));
        settle(700);
        ImageIO.write(snap(f.getRootPane()), "png", new File(out, "05-viewer.png"));
        final boolean[] open = new boolean[3];
        SwingUtilities.invokeAndWait(() -> open[0] = app[0].describe.viewer().isOpen());
        // a real Escape key event, delivered the way the keyboard delivers it
        SwingUtilities.invokeAndWait(() -> {
            Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (owner == null) owner = app[0].describe.viewer();
            long now = System.currentTimeMillis();
            KeyEvent esc = new KeyEvent(owner, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
            KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(owner, esc);
            open[1] = app[0].describe.viewer().isOpen();
        });
        if (open[1]) {
            // fall back to the bound action if focus handling in this headless display swallowed the event
            System.out.println("note: key event did not reach the viewer in this headless session");
        }
        settle(300);
        System.out.println("viewer open before Esc: " + open[0] + ", after: " + open[1]);
        ImageIO.write(snap(f.getRootPane()), "png", new File(out, "06-closed.png"));
        System.exit(open[0] && !open[1] ? 0 : 2);
    }
}
