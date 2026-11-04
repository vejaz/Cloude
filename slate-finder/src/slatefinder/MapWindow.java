package slatefinder;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * "See the map": the place that was found, drawn around its coordinates in 3D or from above, with a pin on the
 * coordinates and a pin for every biome and structure of the description, found near it.
 */
final class MapWindow extends JFrame {
    private final BiomeGrid grid;
    private final Reader.Reading reading;
    private final int spotX, spotZ;
    private final WorldView view;
    private final Terrain world;
    private final JPanel witnessBox = new JPanel();
    private final JLabel status = new JLabel(" ");
    private final JLabel checkLine = new JLabel(" ");
    private final Map<Reader.Claim, WorldView.Marker> markerOf = new IdentityHashMap<>();
    private Ui.Seg modeSeg;
    private List<PlaceSearch.Witness> witnesses = new ArrayList<>();
    private final Component owner;
    private ExactCard exactCard;
    private JLabel aboutLabel;

    static MapWindow open(Component parent, BiomeGrid grid, Reader.Reading reading, int x, int z) {
        MapWindow w = new MapWindow(parent, grid, reading, x, z);
        w.setVisible(true);
        return w;
    }

    private MapWindow(Component parent, BiomeGrid grid, Reader.Reading reading, int x, int z) {
        super("Map  ·  " + String.format("%,d ~ %,d", x, z));
        this.owner = parent;
        this.grid = grid;
        this.reading = reading;
        this.spotX = x;
        this.spotZ = z;
        setIconImages(Theme.icons());
        getContentPane().setBackground(Theme.BG);
        setLayout(new BorderLayout());

        world = new Terrain(Terrain.ofGrid(grid), 7);
        List<Terrain.Site> sites = new ArrayList<>();
        for (Lexicon.Item it : Lexicon.items()) {
            if (!it.structure() || it.pointsFrom != null || !PlaceSearch.hasPoints(it.id) || it.id.equals("trial_chambers") || it.id.equals("buried_treasure")) continue;
            for (int[] p : PlaceSearch.listedPoints(it.id, grid)) {
                if (Math.abs(p[0] - x) > 14000 || Math.abs(p[1] - z) > 14000) continue;
                Terrain.Site s = new Terrain.Site(p[0], p[1], it.id);
                world.addSite(s);
                sites.add(s);
            }
        }
        view = new WorldView(world);
        view.sites = sites;
        view.tx = view.mx = view.wx = x;
        view.tz = view.mz = view.wz = z;
        view.opts.sunElev = Settings.getInt("sun", 52);
        view.opts.maxDist = Settings.getInt("distance", 520);
        view.quality = Settings.getInt("detail", 2);
        view.opts.shadows = Settings.getBool("shadows", true);
        view.opts.ao = Settings.getBool("ao", true);
        view.showSites = Settings.getBool("showSites", true);
        view.simple = Settings.getBool("simple", false);
        view.wy = world.topAt(x, z) + 1.62;
        view.hoverInfo = s -> status.setText(s.isEmpty() ? " " : s);
        view.modeListener = m -> SwingUtilities.invokeLater(() -> { if (modeSeg != null) modeSeg.setIndex(m == WorldView.Mode.ORBIT ? 0 : m == WorldView.Mode.WALK ? 1 : 2); });

        WorldView.Marker spot = new WorldView.Marker("Your spot", x, z, Skin.accent());
        spot.primary = true;
        spot.sub = String.format("X %,d  Z %,d", x, z);
        view.markers.add(spot);

        add(topBar(), BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(0, 14, 0, 0));
        center.add(view, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        add(side(), BorderLayout.EAST);
        add(bottom(), BorderLayout.SOUTH);

        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(scr.width - 60, 1500), Math.min(scr.height - 80, 900));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() { public void windowClosed(WindowEvent e) { view.dispose(); if (exactCard != null) exactCard.stopWork(); } });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() { public void actionPerformed(ActionEvent e) { dispose(); } });

        String mode = Settings.get("mapMode", "3d");
        if (mode.equals("walk")) { view.setMode(WorldView.Mode.WALK); modeSeg.setIndex(1); }
        else if (mode.equals("map")) { view.setMode(WorldView.Mode.MAP); modeSeg.setIndex(2); }
        view.render();

        exactCard.autoOpen();
        Thread t = new Thread(this::findWitnesses, "map-witnesses");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ what is around the spot

    private void findWitnesses() {
        List<PlaceSearch.Witness> ws = PlaceSearch.witnesses(grid, reading.searchable(), spotX, spotZ);
        SwingUtilities.invokeLater(() -> showWitnesses(ws));
    }

    private static Color colourOf(Reader.Claim c) {
        String id = !c.ref.biomes.isEmpty() ? c.ref.biomes.get(0) : !c.ref.structures.isEmpty() ? c.ref.structures.get(0) : null;
        Lexicon.Item it = id == null ? null : Lexicon.byId(id);
        Color col = it != null ? it.colour : Color.GRAY;
        // bright enough to read on the picture
        float[] hsb = Color.RGBtoHSB(col.getRed(), col.getGreen(), col.getBlue(), null);
        return Color.getHSBColor(hsb[0], Math.min(1f, hsb[1] + 0.15f), Math.max(0.85f, hsb[2]));
    }

    private void showWitnesses(List<PlaceSearch.Witness> ws) {
        witnesses = ws;
        int found = 0, total = 0;
        for (PlaceSearch.Witness w : ws) {
            total++;
            if (w.found) found++;
            if (w.claim.role == Reader.Role.TARGET) continue;
            if (!w.found || w.claim.role == Reader.Role.ABSENT) continue;
            WorldView.Marker m = new WorldView.Marker(w.claim.label(), w.x, w.z, colourOf(w.claim));
            m.sub = w.text;
            markerOf.put(w.claim, m);
            view.markers.add(m);
        }
        for (PlaceSearch.Witness w : ws) {
            WorldView.Marker m = markerOf.get(w.claim);
            if (m != null && w.parent != null) m.parent = w.parent.role == Reader.Role.TARGET ? view.markers.get(0) : markerOf.get(w.parent);
        }
        witnessBox.removeAll();
        for (PlaceSearch.Witness w : ws) witnessBox.add(witnessRow(w));
        witnessBox.revalidate();
        witnessBox.repaint();
        boolean all = found == total && total > 0;
        checkLine.setText(all ? "✓  Everything you described is here  (" + found + " of " + total + ")" : "✕  " + (total - found) + " of " + total + " things could not be found around this spot");
        checkLine.setForeground(all ? Theme.GOOD : Theme.WARN);
        view.fitAll();
    }

    private JComponent witnessRow(PlaceSearch.Witness w) {
        Reader.Claim c = w.claim;
        Color col = colourOf(c);
        JPanel row = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.smooth(g0);
                g.setColor(Theme.PANEL_HI);
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                super.paintComponent(g0);
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(8, 10, 8, 10));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent dot = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.smooth(g0);
                g.setColor(col);
                g.fill(new RoundRectangle2D.Float(0, 2, 14, 14, 5, 5));
            }
        };
        dot.setPreferredSize(new Dimension(14, 18));
        row.add(dot, BorderLayout.WEST);
        JPanel mid = new JPanel();
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.setOpaque(false);
        String mark = w.found ? "✓" : "✕";
        JLabel name = Theme.label(mark + "  " + c.label(), Theme.UI_BOLD, w.found ? Theme.TEXT : Theme.WARN);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        mid.add(name);
        JLabel sub = new JLabel("<html><body style='width:190px'>" + esc(w.text) + (w.found && c.role != Reader.Role.ABSENT && c.role != Reader.Role.TARGET ? "<br>X " + String.format("%,d", w.x) + "  Z " + String.format("%,d", w.z) + (w.exact ? "  (listed position)" : "") : "") + "</body></html>");
        sub.setFont(Theme.SMALL);
        sub.setForeground(Theme.MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        mid.add(sub);
        row.add(mid, BorderLayout.CENTER);
        if (w.found && c.role != Reader.Role.ABSENT) {
            JPanel btns = new JPanel(new GridLayout(2, 1, 0, 5));
            btns.setOpaque(false);
            DescribePanel.Pill look = new DescribePanel.Pill("Look", Skin.accent(), false);
            look.onClick(() -> { view.focus(w.x, w.z); if (view.mode == WorldView.Mode.WALK) view.setMode(WorldView.Mode.ORBIT); modeSeg.setIndex(view.mode == WorldView.Mode.MAP ? 2 : 0); });
            DescribePanel.Pill stand = new DescribePanel.Pill("Stand", Skin.accent(), false);
            stand.onClick(() -> { view.wx = w.x; view.wz = w.z; view.wy = world.topAt(w.x, w.z) + 1.62; view.setMode(WorldView.Mode.WALK); modeSeg.setIndex(1); view.render(); });
            btns.add(look);
            btns.add(stand);
            JPanel e = new JPanel(new BorderLayout());
            e.setOpaque(false);
            e.add(btns, BorderLayout.NORTH);
            row.add(e, BorderLayout.EAST);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        JPanel wrap = new JPanel();
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.add(row);
        wrap.add(Box.createVerticalStrut(6));
        return wrap;
    }

    ExactCard exactCard() { return exactCard; }
    WorldView worldView() { return view; }

    /** For tests: switches the view (0 3D, 1 stand there, 2 top-down). */
    void setMode(int i) { view.setMode(i == 0 ? WorldView.Mode.ORBIT : i == 1 ? WorldView.Mode.WALK : WorldView.Mode.MAP); modeSeg.setIndex(i); view.render(); }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // ------------------------------------------------------------------ the bars

    private JComponent topBar() {
        JPanel bar = new JPanel(new BorderLayout(14, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(12, 14, 10, 14));
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        JLabel t = Theme.label("The place that was found", Theme.TITLE, Theme.TEXT);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel xy = Theme.label(String.format("X %,d    Z %,d", spotX, spotZ), Theme.MONO_B, Skin.accent());
        xy.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(t);
        left.add(xy);
        bar.add(left, BorderLayout.WEST);

        modeSeg = new Ui.Seg(new String[]{"3D view", "Stand there", "Top-down map"}, 0, i -> {
            view.setMode(i == 0 ? WorldView.Mode.ORBIT : i == 1 ? WorldView.Mode.WALK : WorldView.Mode.MAP);
            Settings.set("mapMode", i == 0 ? "3d" : i == 1 ? "walk" : "map");
            view.requestFocusInWindow();
        });
        modeSeg.setPreferredSize(new Dimension(360, 34));
        JPanel mid = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        mid.setOpaque(false);
        mid.add(modeSeg);
        bar.add(mid, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        right.setOpaque(false);
        Theme.Btn copy = new Theme.Btn("Copy coordinates", true).on(() -> {
            String s = spotX + " ~ " + spotZ;
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
            Toast.show(this, "Copied " + s + "  —  paste it after /tp " + Settings.player(), Skin.accent());
        });
        copy.setPreferredSize(new Dimension(150, 34));
        Theme.Btn tp = new Theme.Btn("Copy /tp", false).on(() -> {
            String s = "/tp " + Settings.player() + " " + spotX + " ~ " + spotZ;
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
            Toast.show(this, "Copied  " + s, Skin.accent());
        });
        tp.setPreferredSize(new Dimension(92, 34));
        Theme.Btn save = new Theme.Btn("Save picture", false).on(this::savePicture);
        save.setPreferredSize(new Dimension(112, 34));
        right.add(copy);
        right.add(tp);
        right.add(save);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void savePicture() {
        try {
            File dir = new File(System.getProperty("user.home"), "Pictures/Slate Finder");
            if (!dir.isDirectory() && !dir.mkdirs()) dir = new File(System.getProperty("user.home"));
            File f = new File(dir, String.format("map-%d_%d-%d.png", spotX, spotZ, System.currentTimeMillis() / 1000));
            BufferedImage im = view.snapshot();
            ImageIO.write(im, "png", f);
            Toast.show(this, "Saved " + f.getName() + "  in  " + f.getParent(), Skin.accent());
        } catch (Exception e) {
            Toast.show(this, "Could not save the picture: " + e.getMessage(), Theme.BAD);
        }
    }

    private JComponent bottom() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(8, 18, 10, 18));
        status.setFont(Theme.SMALL);
        status.setForeground(Theme.MUTED);
        JLabel help = Theme.label("Drag to turn  ·  wheel zooms toward the pointer  ·  right-drag to move  ·  click a pin or a village dot to stand there  ·  double-click the ground to stand there  ·  W A S D to walk  ·  F to fly  ·  R to go back", Theme.SMALL, Theme.DIM);
        bar.add(status, BorderLayout.WEST);
        bar.add(help, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------------ options on the right

    private JComponent side() {
        JPanel col = new DescribePanel.Column();

        exactCard = new ExactCard(owner, grid, world, view, spotX, spotZ, html -> SwingUtilities.invokeLater(() -> {
            if (aboutLabel != null) aboutLabel.setText("<html><body style='width:270px'>" + html + "</body></html>");
        }));
        col.add(exactCard);
        col.add(Box.createVerticalStrut(10));

        Theme.Card c1 = new Theme.Card("Your description here", new BorderLayout());
        c1.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel in1 = new JPanel();
        in1.setLayout(new BoxLayout(in1, BoxLayout.Y_AXIS));
        in1.setOpaque(false);
        checkLine.setFont(Theme.UI_BOLD);
        checkLine.setForeground(Theme.MUTED);
        checkLine.setText("Looking around the spot…");
        checkLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        in1.add(checkLine);
        in1.add(Box.createVerticalStrut(8));
        witnessBox.setLayout(new BoxLayout(witnessBox, BoxLayout.Y_AXIS));
        witnessBox.setOpaque(false);
        witnessBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        in1.add(witnessBox);
        c1.add(in1, BorderLayout.CENTER);
        col.add(c1);
        col.add(Box.createVerticalStrut(10));

        Theme.Card c2 = new Theme.Card("View", new BorderLayout());
        c2.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel in2 = stack();
        in2.add(new Ui.Slider("Time of day", 8, 80, view.opts.sunElev, v -> v < 20 ? "dawn" : v < 62 ? "day" : "noon", v -> { view.opts.sunElev = v; Settings.set("sun", (int) (double) v); view.render(); }));
        in2.add(new Ui.Slider("How far you can see", 150, 1100, view.opts.maxDist, v -> String.format("%,.0f blocks", v), v -> { view.opts.maxDist = (int) (double) v; Settings.set("distance", (int) (double) v); view.render(); }));
        in2.add(new Ui.Slider("Field of view  (stand there)", 30, 105, Math.toDegrees(view.walkFov), v -> String.format("%.0f°", v), v -> { view.walkFov = Math.toRadians(v); view.render(); }));
        in2.add(new Ui.Toggle("Simple look  (flat colours, fastest)", view.simple, v -> { view.simple = v; Settings.set("simple", v); view.render(); }));
        in2.add(new Ui.Slider("Detail", 1, 3, view.quality, v -> v < 1.5 ? "fast" : v < 2.5 ? "balanced" : "sharp", v -> { view.quality = (int) Math.round(v); Settings.set("detail", view.quality); view.render(); }));
        c2.add(in2, BorderLayout.CENTER);
        col.add(c2);
        col.add(Box.createVerticalStrut(10));

        Theme.Card c3 = new Theme.Card("Show", new BorderLayout());
        c3.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel in3 = stack();
        in3.add(new Ui.Toggle("Pins and beams", true, v -> { view.showMarkers = v; view.repaint(); }));
        in3.add(new Ui.Toggle("Lines between linked things", true, v -> { view.showLinks = v; view.repaint(); }));
        in3.add(new Ui.Toggle("Name labels", true, v -> { view.showLabels = v; view.repaint(); }));
        in3.add(new Ui.Toggle("Villages and structures", view.showSites, v -> { view.showSites = v; Settings.set("showSites", v); view.repaint(); }));
        in3.add(new Ui.Toggle("Coordinate grid  (top-down)", true, v -> { view.showGrid = v; view.repaint(); }));
        in3.add(new Ui.Toggle("Compass", true, v -> { view.showCompass = v; view.repaint(); }));
        in3.add(new Ui.Toggle("Shadows", view.opts.shadows, v -> { view.opts.shadows = v; Settings.set("shadows", v); view.render(); }));
        in3.add(new Ui.Toggle("Soft corners", view.opts.ao, v -> { view.opts.ao = v; Settings.set("ao", v); view.render(); }));
        c3.add(in3, BorderLayout.CENTER);
        col.add(c3);
        col.add(Box.createVerticalStrut(10));

        Theme.Card c4 = new Theme.Card("Go to", new BorderLayout());
        c4.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel in4 = stack();
        JTextField fx = field(String.valueOf(spotX)), fz = field(String.valueOf(spotZ));
        JPanel r = new JPanel(new GridLayout(1, 3, 8, 0));
        r.setOpaque(false);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        r.add(new DescribePanel.RoundBox(fx, 8));
        r.add(new DescribePanel.RoundBox(fz, 8));
        Theme.Btn go = new Theme.Btn("Go", false).on(() -> {
            try { view.focus(Integer.parseInt(fx.getText().trim().replace(",", "")), Integer.parseInt(fz.getText().trim().replace(",", ""))); }
            catch (NumberFormatException e) { Toast.show(this, "Type two whole numbers: X and Z", Theme.WARN); }
        });
        r.add(go);
        in4.add(Theme.label("X                                  Z", Theme.SMALL, Theme.DIM));
        in4.add(Box.createVerticalStrut(4));
        in4.add(r);
        in4.add(Box.createVerticalStrut(8));
        Theme.Btn home = new Theme.Btn("Back to the spot", false).on(view::home);
        home.setAlignmentX(Component.LEFT_ALIGNMENT);
        home.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        in4.add(home);
        c4.add(in4, BorderLayout.CENTER);
        col.add(c4);
        col.add(Box.createVerticalStrut(10));

        Theme.Card c5 = new Theme.Card("About this view", new BorderLayout());
        c5.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel about = aboutLabel = new JLabel("<html><body style='width:270px'>The <b>biomes</b> and the <b>positions of villages</b> are real, from your biome map (one sample every " + grid.step + " blocks). "
            + "Everything finer than that - the exact hills, where each tree stands, the shape of a house - is generated to look right. "
            + "It is a faithful picture of the <i>place</i>, not a copy of the blocks in your game.</body></html>");
        about.setFont(Theme.SMALL);
        about.setForeground(Theme.MUTED);
        c5.add(about, BorderLayout.CENTER);
        col.add(c5);
        col.add(Box.createVerticalGlue());

        JScrollPane sp = new JScrollPane(col, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(new EmptyBorder(0, 8, 0, 14));
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        DescribePanel.slim(sp.getVerticalScrollBar(), 8);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.setPreferredSize(new Dimension(392, 100));
        return sp;
    }

    private static JPanel stack() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    private static JTextField field(String t) {
        JTextField f = new JTextField(t);
        f.setOpaque(false);
        f.setBorder(new EmptyBorder(4, 8, 4, 8));
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Skin.accent());
        f.setFont(Theme.UI);
        return f;
    }
}
