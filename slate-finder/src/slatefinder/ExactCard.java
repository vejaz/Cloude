package slatefinder;

import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * The "Exact world" box of the map window: turns the generated picture around the spot into the real blocks of the world,
 * using the seed. Asks once before downloading and running the game's server, shows the progress, proves the result is the same
 * world as the biome map, and lets the person switch between the exact blocks and the generated picture.
 */
final class ExactCard extends Theme.Card {
    static final int[] SIZES = {48, 80, 160, 320};
    private static final String[] SIZE_NAMES = {"Small", "Normal", "Wide", "Huge"};

    private final Component owner;
    private final BiomeGrid grid;
    private final Terrain world;
    private final WorldView view;
    private final int spotX, spotZ;
    private final Consumer<String> aboutText;
    private final JLabel status = new JLabel(" ");
    private final JLabel proof = new JLabel(" ");
    private final Ui.Meter meter = new Ui.Meter();
    private final Theme.Btn go, stop;
    private final Ui.Toggle show;
    private final Ui.Seg sizeSeg;
    private final JLabel sizeNote = new JLabel(" ");
    private final JLabel detail = new JLabel(" ");
    private ExactJob job;
    private ExactPlace place;
    private Exactness.Report report;
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
        in.add(Box.createVerticalStrut(6));
        proof.setFont(Theme.UI_BOLD);
        proof.setAlignmentX(Component.LEFT_ALIGNMENT);
        in.add(proof);
        in.add(Box.createVerticalStrut(6));
        meter.setAlignmentX(Component.LEFT_ALIGNMENT);
        meter.setVisible(false);
        in.add(meter);
        in.add(Box.createVerticalStrut(2));
        detail.setFont(Theme.SMALL);
        detail.setForeground(Theme.DIM);
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        in.add(detail);
        in.add(Box.createVerticalStrut(8));

        int cur = 1;
        for (int i = 0; i < SIZES.length; i++) if (Math.abs(SIZES[i] - ExactJob.radius()) < Math.abs(SIZES[cur] - ExactJob.radius())) cur = i;
        sizeSeg = new Ui.Seg(SIZE_NAMES, cur, i -> { Settings.set("exactSize", SIZES[i]); sizeText(i); });
        sizeSeg.setAlignmentX(Component.LEFT_ALIGNMENT);
        in.add(sizeSeg);
        sizeNote.setFont(Theme.SMALL);
        sizeNote.setForeground(Theme.DIM);
        sizeNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        in.add(Box.createVerticalStrut(3));
        in.add(sizeNote);
        in.add(Box.createVerticalStrut(8));
        sizeText(cur);

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

    private void sizeText(int i) {
        sizeNote.setText("<html><body style='width:246px'>About " + (2 * SIZES[i]) + " blocks across around the marked spot. Bigger shows more of the surroundings to recognise, and takes longer.</body></html>");
    }

    private static String html(String s) {
        return "<html><body style='width:246px'>" + s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>") + "</body></html>";
    }

    private void idle() {
        working = false;
        status.setText(html("The picture around the spot is generated to look right. Make it exact and it shows the real blocks of your world, from its seed " + grid.seed + "."));
        status.setForeground(Theme.MUTED);
        proof.setText(" ");
        detail.setText(html("The game makes only the part around the marked spot, so it is quick. It is kept afterwards, in a small file, inside your storage limit (Settings)."));
        meter.setVisible(false);
        go.setVisible(true);
        go.text = place == null ? "Make it exact" : "Make it exact again";
        go.repaint();
        stop.setVisible(false);
        sizeSeg.setVisible(true);
        sizeNote.setVisible(true);
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
    Exactness.Report exactness() { return report; }
    String proofText() { return proof.getText(); }

    private void begin(boolean quiet) {
        if (working) return;
        if (!quiet && ExactJob.stored(grid.seed, spotX, spotZ, ExactJob.radius()) == null && !consent()) return;
        working = true;
        place = null;
        report = null;
        status.setForeground(Theme.TEXT);
        status.setText(html(quiet ? "Opening the exact blocks…" : "Getting the exact blocks…"));
        proof.setText(" ");
        detail.setText(" ");
        meter.setVisible(true);
        meter.set(-1, Skin.accent());
        go.setVisible(false);
        stop.setVisible(true);
        sizeSeg.setVisible(false);
        sizeNote.setVisible(false);
        show.setVisible(false);
        job = new ExactJob();
        job.start(grid.seed, spotX, spotZ, quiet, new ExactJob.Listener() {
            public void progress(double f, String t) { SwingUtilities.invokeLater(() -> { meter.set(f, Skin.accent()); detail.setText(html(t)); }); }
            public void done(ExactPlace p, boolean fromDisk) {
                // the game's own pictures, if its jar is on this computer
                boolean pics = RealTextures.ready() || RealTextures.load(RealTextures.findJar());
                p.bind(world);
                Exactness.Report rep = Exactness.check(p, grid);
                SwingUtilities.invokeLater(() -> ready(p, rep, fromDisk, pics));
            }
            public void failed(String message, boolean cancelled) { SwingUtilities.invokeLater(() -> failed(message, cancelled)); }
        });
    }

    private void ready(ExactPlace p, Exactness.Report rep, boolean fromDisk, boolean pics) {
        working = false;
        place = p;
        report = rep;
        world.attach(p);
        view.render();
        meter.setVisible(false);
        stop.setVisible(false);
        go.setVisible(true);
        go.text = "Make it bigger or again";
        go.repaint();
        sizeSeg.setVisible(true);
        sizeNote.setVisible(true);
        show.setVisible(true);
        show.setOn(true);
        int w = p.cw * 16, d = p.cd * 16;
        status.setForeground(rep.level == 0 ? Theme.WARN : Theme.TEXT);
        status.setText(html("Real blocks of your world (seed " + grid.seed + "), " + String.format("%,d", w) + " × " + String.format("%,d", d) + " blocks around the spot."));
        proof.setForeground(rep.level == 2 ? Theme.GOOD : rep.level == 1 ? Theme.WARN : Theme.BAD);
        proof.setText(html((rep.level == 2 ? "✓  " : rep.level == 1 ? "≈  " : "✕  ") + rep.verdict));
        StringBuilder sb = new StringBuilder();
        for (String l : rep.structureLines) sb.append(l).append('\n');
        if (!rep.found.isEmpty()) {
            sb.append("Structures in this area: ");
            for (int i = 0; i < Math.min(5, rep.found.size()); i++) sb.append(i == 0 ? "" : ";  ").append(rep.found.get(i));
            if (rep.found.size() > 5) sb.append(";  and ").append(rep.found.size() - 5).append(" more");
            sb.append('\n');
        }
        sb.append("Past the edge of the area the picture is generated to look right.");
        if (!pics) sb.append(" The game's own pictures were not found on this computer, so the app's own textures are used.");
        if (!fromDisk) sb.append(" A small copy is kept, so next time it opens at once.");
        detail.setText(html(sb.toString()));
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
        sizeSeg.setVisible(true);
        sizeNote.setVisible(true);
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
