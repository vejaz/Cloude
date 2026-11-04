package slatefinder;

import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * The "Exact world" box of the map window: turns the generated picture around the spot into the real blocks of the world,
 * using the seed. Asks once before downloading and running the game's server, shows the progress, and lets the person
 * switch between the exact blocks and the generated picture.
 */
final class ExactCard extends Theme.Card {
    private final Component owner;
    private final BiomeGrid grid;
    private final Terrain world;
    private final WorldView view;
    private final int spotX, spotZ;
    private final Consumer<String> aboutText;
    private final JLabel status = new JLabel(" ");
    private final Ui.Meter meter = new Ui.Meter();
    private final Theme.Btn go, stop;
    private final Ui.Toggle show;
    private final JLabel detail = new JLabel(" ");
    private ExactJob job;
    private ExactPlace place;
    private boolean working;

    ExactCard(Component owner, BiomeGrid grid, Terrain world, WorldView view, int x, int z, Consumer<String> aboutText) {
        super("Exact world", new BorderLayout());
        this.owner = owner; this.grid = grid; this.world = world; this.view = view; this.spotX = x; this.spotZ = z; this.aboutText = aboutText;
        setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel in = new JPanel();
        in.setLayout(new BoxLayout(in, BoxLayout.Y_AXIS));
        in.setOpaque(false);

        status.setFont(Theme.UI);
        status.setForeground(Theme.MUTED);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        in.add(status);
        in.add(Box.createVerticalStrut(8));
        meter.setAlignmentX(Component.LEFT_ALIGNMENT);
        meter.setVisible(false);
        in.add(meter);
        in.add(Box.createVerticalStrut(2));
        detail.setFont(Theme.SMALL);
        detail.setForeground(Theme.DIM);
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        in.add(detail);
        in.add(Box.createVerticalStrut(8));

        go = new Theme.Btn("Make it exact", true).on(this::begin);
        go.setPreferredSize(new Dimension(100, 36));
        go.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        go.setAlignmentX(Component.LEFT_ALIGNMENT);
        in.add(go);
        stop = new Theme.Btn("Stop", false).on(() -> { if (job != null) job.cancel(); });
        stop.setPreferredSize(new Dimension(100, 34));
        stop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        stop.setAlignmentX(Component.LEFT_ALIGNMENT);
        stop.setVisible(false);
        in.add(stop);
        show = new Ui.Toggle("Show the exact blocks", true, v -> flip(v));
        show.setAlignmentX(Component.LEFT_ALIGNMENT);
        show.setVisible(false);
        in.add(show);
        add(in, BorderLayout.CENTER);
        idle();
    }

    private static String html(String s) {
        return "<html><body style='width:268px'>" + s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</body></html>";
    }

    private void idle() {
        working = false;
        status.setText(html("The picture around the spot is generated to look right. Make it exact and it shows the real blocks of your world, from its seed " + grid.seed + "."));
        status.setForeground(Theme.MUTED);
        detail.setText(html("Only the part around the marked spot is made (about " + (2 * ExactJob.radius()) + " blocks across), so it is quick. It is kept afterwards, in a small file, inside your storage limit (Settings)."));
        meter.setVisible(false);
        go.setVisible(true);
        go.text = place == null ? "Make it exact" : "Make it exact again";
        go.repaint();
        stop.setVisible(false);
        show.setVisible(place != null);
        revalidate();
        repaint();
    }

    /** Opens a copy that is already on disk, without asking anything. */
    void autoOpen() {
        if (!Settings.getBool("exactAuto", true)) return;
        if (ExactJob.stored(grid.seed, spotX, spotZ, Math.min(ExactJob.radius(), 64)) == null) return;
        begin(true);
    }

    private void begin() { begin(false); }

    /** For tests: presses the button. */
    void press() { begin(false); }

    boolean isReady() { return place != null && !working; }
    String statusText() { return status.getText(); }

    private void begin(boolean quiet) {
        if (working) return;
        if (!quiet && ExactJob.stored(grid.seed, spotX, spotZ, Math.min(ExactJob.radius(), 64)) == null && !consent()) return;
        working = true;
        place = null;
        status.setForeground(Theme.TEXT);
        status.setText(html(quiet ? "Opening the exact blocks…" : "Getting the exact blocks…"));
        detail.setText(" ");
        meter.setVisible(true);
        meter.set(-1, Skin.accent());
        go.setVisible(false);
        stop.setVisible(true);
        show.setVisible(false);
        job = new ExactJob();
        job.start(grid.seed, spotX, spotZ, new ExactJob.Listener() {
            public void progress(double f, String t) { SwingUtilities.invokeLater(() -> { meter.set(f, Skin.accent()); detail.setText(html(t)); }); }
            public void done(ExactPlace p, boolean fromDisk) {
                // the game's own pictures, if its jar is on this computer
                boolean pics = RealTextures.ready() || RealTextures.load(RealTextures.findJar());
                p.bind(world);
                SwingUtilities.invokeLater(() -> ready(p, fromDisk, pics));
            }
            public void failed(String message, boolean cancelled) { SwingUtilities.invokeLater(() -> failed(message, cancelled)); }
        });
    }

    private void ready(ExactPlace p, boolean fromDisk, boolean pics) {
        working = false;
        place = p;
        world.attach(p);
        view.render();
        meter.setVisible(false);
        stop.setVisible(false);
        go.setVisible(false);
        show.setVisible(true);
        show.setOn(true);
        status.setForeground(Theme.GOOD);
        int w = p.cw * 16, d = p.cd * 16;
        status.setText(html("✓  These are the real blocks of your world (seed " + grid.seed + "), " + String.format("%,d", w) + " × " + String.format("%,d", d) + " blocks around the spot."));
        detail.setText(html("Past the edge of that area the picture is generated to look right." + (pics ? "" : " The game's own pictures were not found on this computer, so the app's own textures are used.") + (fromDisk ? "" : " A small copy is kept, so next time it opens at once.")));
        aboutText.accept("The blocks around the spot are <b>the real ones</b>: the game generated this area from the seed, and the app drew every block, tree, building and plant as it is. "
            + "Past the edge of that area, the <b>biomes</b> and <b>positions of villages</b> are still real, but the fine detail is generated to look right. Only natural terrain is shown: nothing built by players.");
        revalidate();
        repaint();
    }

    private void failed(String message, boolean cancelled) {
        working = false;
        meter.setVisible(false);
        stop.setVisible(false);
        go.setVisible(true);
        go.text = cancelled ? "Make it exact" : "Try again";
        go.repaint();
        status.setForeground(cancelled ? Theme.MUTED : Theme.WARN);
        status.setText(html(cancelled ? "Stopped. Nothing was kept." : "Could not make it exact."));
        detail.setText(cancelled ? " " : html(message.length() > 700 ? message.substring(0, 700) + "…" : message));
        revalidate();
        repaint();
    }

    private void flip(boolean exact) {
        if (place == null) return;
        world.attach(exact ? place : null);
        view.render();
    }

    void stopWork() { if (job != null && working) job.cancel(); }

    /** Asks, once, before downloading and running the game's server. */
    private boolean consent() {
        if (Settings.getBool("exactConsent", false)) return true;
        String msg = "<html><body style='width:380px'><b>Make the exact blocks?</b><br><br>"
            + "To show the real blocks, Slate Finder will:<br>"
            + "• download Minecraft's own server (about 56 MB) from Mojang, once<br>"
            + "• run it quietly in the background on this computer, reachable only from this computer, to generate the area around the spot from your world's seed<br>"
            + "• keep a small copy of that area and delete the rest<br><br>"
            + "It uses the Java this app runs on. It takes a few minutes the first time, and uses disk space inside the limit you set in Settings.<br><br>"
            + "By continuing you agree to Minecraft's EULA: <b>https://aka.ms/MinecraftEULA</b></body></html>";
        Object[] opts = {"Agree and continue", "Not now"};
        int r = JOptionPane.showOptionDialog(SwingUtilities.getWindowAncestor(owner), msg, "Exact world", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
        if (r != 0) return false;
        Settings.set("exactConsent", true);
        return true;
    }
}
