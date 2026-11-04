package slatefinder;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * The Describing finder tab: say what a place looks like, and it finds where on the map that is.
 *
 * Left: the description, and what was understood (and what was not). Middle: a book of every biome
 * and structure, with a picture each - click a picture to see it full size, Escape to close it.
 * Right: the places that match.
 */
public final class DescribePanel extends JPanel {
    private static final int LEFT_W = 392, RIGHT_W = 372;

    private final App app;
    private BiomeGrid grid;
    private volatile int token;      // the real search
    private volatile int liveToken;  // the live count while typing

    private final PlainArea text = new PlainArea();
    private final JPanel readingBox = new JPanel();
    private final JPanel notesBox = new JPanel();
    private final JPanel funnelBox = new JPanel();
    private final JPanel resultsBox = new JPanel();
    private final JLabel status = new JLabel(" ");
    private final CountView count = new CountView();
    private final JLabel mapStatus = new JLabel(" ");
    private final JTextField radiusField = field("225000");
    private final JTextField keyField = secret();
    private final JTextField groqField = secret();
    final Theme.Btn searchBtn = new Theme.Btn("FIND IT", true);
    private final javax.swing.Timer debounce;
    private Reader.Reading reading = new Reader.Reading();
    /** What was changed by hand in the "What I understood" rows; kept while the sentence is edited around it. */
    private final Map<String, int[]> distEdits = new LinkedHashMap<>();
    private ReverseDialog reverseDlg;
    private String spotKey;
    /** null: as the sentence reads; TRUE: the spot is anywhere near everything; FALSE: one of the named things is the spot. */
    private Boolean anywhereOverride;
    private final Viewer viewer;
    private final BookGrid book = new BookGrid();
    private final PlainField bookSearch = new PlainField("Search every biome and structure…");
    private String bookFilter = "All";
    private final List<Pill> filterPills = new ArrayList<>();
    private JPanel pillsPanel;
    private final JLabel bookCount = new JLabel(" ");
    private volatile boolean searching;
    private volatile boolean liveBusy;

    public DescribePanel(App app) {
        super(new BorderLayout(14, 0));
        this.app = app;
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(14, 16, 12, 16));
        setOpaque(true);

        viewer = new Viewer(this::addToDescription, this::statusOf);

        add(scroll(left(), LEFT_W), BorderLayout.WEST);
        add(book(), BorderLayout.CENTER);
        add(scroll(right(), RIGHT_W), BorderLayout.EAST);

        debounce = new javax.swing.Timer(260, e -> reparse());
        debounce.setRepeats(false);
        text.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { typed(); }
            public void removeUpdate(DocumentEvent e) { typed(); }
            public void changedUpdate(DocumentEvent e) { typed(); }
        });
        searchBtn.on(this::search);
        showEmptyResults();
        refreshReading();

        // Escape anywhere in the tab closes the viewer
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeViewer");
        // enabled only while the viewer is open, so Escape is never swallowed from anything else in the app
        getActionMap().put("closeViewer", new AbstractAction() {
            @Override public boolean isEnabled() { return viewer.isOpen(); }
            public void actionPerformed(ActionEvent e) { viewer.close(); }
        });

        Settings.onChange(() -> SwingUtilities.invokeLater(this::applyLook));
        loadGridInBackground();
        Thread warm = new Thread(() -> {
            for (Lexicon.Item it : Lexicon.items()) Previews.get(it);
            SwingUtilities.invokeLater(book::repaint);
            if (Settings.getBool("pics3d", true)) Previews3D.warm(() -> SwingUtilities.invokeLater(book::repaint));
        }, "describe-previews");
        warm.setPriority(Thread.MIN_PRIORITY);
        warm.setDaemon(true);
        warm.start();
    }

    public int radius() {
        try { return Math.max(1000, Math.min(Integer.parseInt(radiusField.getText().trim().replace(",", "")), 225000)); }
        catch (NumberFormatException e) { return 225000; }
    }

    // ================================================================== layout

    private JComponent scroll(JComponent inner, int width) {
        JScrollPane sp = new JScrollPane(inner, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        slim(sp.getVerticalScrollBar(), 8);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.setPreferredSize(new Dimension(width, 100));
        return sp;
    }

    static final class Column extends JPanel implements Scrollable {
        Column() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setOpaque(false); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static Theme.Card card(String title) {
        Theme.Card c = new Theme.Card(title, new BorderLayout());
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JPanel stack() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    private JComponent left() {
        Column col = new Column();

        // the description, the number of places, and the button: all in view at once, no scrolling
        Theme.Card c1 = card("Describe the place");
        JPanel in = stack();
        text.setFont(Theme.UI.deriveFont(14.5f));
        text.setHint("Name what you can see, separated by commas:  plains village, river, snowy mountains far away, no desert");
        JScrollPane ts = new JScrollPane(text, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ts.setBorder(null);
        ts.setOpaque(false);
        ts.getViewport().setOpaque(false);
        slim(ts.getVerticalScrollBar(), 6);
        RoundBox tb = new RoundBox(ts, 12);
        tb.setPreferredSize(new Dimension(100, 96));
        tb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        tb.setAlignmentX(Component.LEFT_ALIGNMENT);
        in.add(tb);
        in.add(Box.createVerticalStrut(10));

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        bar.add(count, BorderLayout.CENTER);
        searchBtn.setPreferredSize(new Dimension(132, 52));
        JPanel btnWrap = new JPanel(new BorderLayout());
        btnWrap.setOpaque(false);
        btnWrap.add(searchBtn, BorderLayout.CENTER);
        bar.add(btnWrap, BorderLayout.EAST);
        in.add(bar);
        in.add(Box.createVerticalStrut(8));
        status.setFont(Theme.SMALL);
        status.setForeground(Theme.MUTED);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        in.add(status);
        c1.add(in, BorderLayout.CENTER);
        col.add(c1);
        col.add(Box.createVerticalStrut(10));

        Theme.Card c2 = card("What I understood");
        readingBox.setLayout(new BoxLayout(readingBox, BoxLayout.Y_AXIS));
        readingBox.setOpaque(false);
        notesBox.setLayout(new BoxLayout(notesBox, BoxLayout.Y_AXIS));
        notesBox.setOpaque(false);
        JPanel both = stack();
        both.add(readingBox);
        both.add(notesBox);
        c2.add(both, BorderLayout.CENTER);
        col.add(c2);
        col.add(Box.createVerticalStrut(10));

        Theme.Card c3 = card("Search area");
        JPanel s = stack();
        JPanel rr = new JPanel(new BorderLayout(8, 0));
        rr.setOpaque(false);
        rr.setAlignmentX(Component.LEFT_ALIGNMENT);
        rr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        rr.add(Theme.label("Within", Theme.UI, Theme.MUTED), BorderLayout.WEST);
        radiusField.setPreferredSize(new Dimension(92, 30));
        JPanel mid = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        mid.setOpaque(false);
        mid.add(new RoundBox(radiusField, 8));
        rr.add(mid, BorderLayout.CENTER);
        rr.add(Theme.label("blocks of spawn", Theme.SMALL, Theme.DIM), BorderLayout.EAST);
        s.add(rr);
        s.add(Box.createVerticalStrut(10));
        funnelBox.setLayout(new BoxLayout(funnelBox, BoxLayout.Y_AXIS));
        funnelBox.setOpaque(false);
        funnelBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(funnelBox);
        mapStatus.setFont(Theme.SMALL);
        mapStatus.setForeground(Theme.DIM);
        mapStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(Box.createVerticalStrut(6));
        s.add(mapStatus);
        c3.add(s, BorderLayout.CENTER);
        col.add(c3);
        col.add(Box.createVerticalStrut(10));
        col.add(settings());
        col.add(Box.createVerticalGlue());
        return col;
    }

    /** The big number: how many places match what is typed, always next to the text box. */
    static final class CountView extends JComponent {
        private String number = "—", caption = "describe a place";
        private Color colour = Theme.DIM;
        CountView() { setPreferredSize(new Dimension(150, 56)); }
        void set(String number, String caption, Color colour) { this.number = number; this.caption = caption; this.colour = colour; repaint(); }
        String number() { return number; }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.smooth(g0);
            g.setColor(Theme.PANEL_HI);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
            g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 200));
            g.fill(new RoundRectangle2D.Float(0, 10, 4, getHeight() - 20, 4, 4));
            g.setFont(Theme.TITLE.deriveFont(Font.BOLD, 30f));
            g.setColor(colour);
            g.drawString(number, 16, 34);
            g.setFont(Theme.SMALL);
            g.setColor(Theme.MUTED);
            g.drawString(caption, 17, 50);
        }
    }

    private JComponent settings() {
        Theme.Card c = card("Smart reading (optional)");
        JPanel p = stack();
        JLabel l = wrapLabel("The built-in reader needs no key and understands every biome and structure. Add a key only if you want an AI to read long, messy sentences.", Theme.SMALL, Theme.DIM, LEFT_W - 60);
        p.add(l);
        p.add(Box.createVerticalStrut(8));
        p.add(keyRow("Gemini key", keyField, () -> {
            try { Describe.saveKey(keyField.getText().trim()); note("Gemini key saved.", Theme.GOOD); }
            catch (Exception e) { note("Could not save the key: " + e.getMessage(), Theme.BAD); }
        }, Describe.key()));
        p.add(Box.createVerticalStrut(6));
        p.add(keyRow("Groq key", groqField, () -> {
            try { Describe.saveGroqKey(groqField.getText().trim()); note("Groq key saved.", Theme.GOOD); }
            catch (Exception e) { note("Could not save the Groq key: " + e.getMessage(), Theme.BAD); }
        }, Describe.groqKey()));
        c.add(p, BorderLayout.CENTER);
        return c;
    }

    /** A key field: the saved key stays in it (as dots) when you click it, so nothing seems to vanish. */
    private static JPasswordField secret() {
        JPasswordField f = new JPasswordField();
        f.setOpaque(false);
        f.setBorder(new EmptyBorder(4, 8, 4, 8));
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Skin.accent());
        f.setFont(Theme.UI);
        f.setEchoChar('\u2022');
        return f;
    }

    private JComponent keyRow(String label, JTextField f, Runnable save, String current) {
        JPanel r = new JPanel(new BorderLayout(8, 0));
        r.setOpaque(false);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        JLabel l = Theme.label(label, Theme.SMALL, Theme.MUTED);
        l.setPreferredSize(new Dimension(70, 20));
        r.add(l, BorderLayout.WEST);
        f.setText(current == null ? "" : current.trim());
        r.add(new RoundBox(f, 8), BorderLayout.CENTER);
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        east.setOpaque(false);
        if (f instanceof JPasswordField) {
            JPasswordField pf = (JPasswordField) f;
            Pill show = new Pill("Show", Theme.MUTED, false);
            show.onClick(() -> {
                boolean hidden = pf.getEchoChar() != 0;
                pf.setEchoChar(hidden ? (char) 0 : '\u2022');
            });
            east.add(show);
        }
        Theme.Btn b = new Theme.Btn("Save", false).on(save);
        b.setPreferredSize(new Dimension(62, 30));
        east.add(b);
        r.add(east, BorderLayout.EAST);
        return r;
    }

    private JComponent right() {
        Column col = new Column();
        Theme.Card c = card("Places that match");
        resultsBox.setLayout(new BoxLayout(resultsBox, BoxLayout.Y_AXIS));
        resultsBox.setOpaque(false);
        c.add(resultsBox, BorderLayout.CENTER);
        col.add(c);
        col.add(Box.createVerticalGlue());
        return col;
    }

    // ------------------------------------------------------------------ the book

    private JComponent book() {
        Theme.Card c = card("Biome & structure book");
        JPanel top = stack();
        bookSearch.setPreferredSize(new Dimension(100, 36));
        RoundBox sb = new RoundBox(bookSearch, 12);
        sb.setPreferredSize(new Dimension(100, 38));
        sb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        sb.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(sb);
        top.add(Box.createVerticalStrut(8));
        pillsPanel = new JPanel(new WrapLayout(6, 6));
        pillsPanel.setOpaque(false);
        pillsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buildPills();
        top.add(pillsPanel);
        bookCount.setFont(Theme.SMALL);
        bookCount.setForeground(Theme.DIM);
        bookCount.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(Box.createVerticalStrut(6));
        top.add(bookCount);
        top.add(Box.createVerticalStrut(2));
        JComponent legend = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.smooth(g0);
                g.setFont(Theme.SMALL);
                int x = 0;
                Object[][] k = {{Theme.GOOD, "exact"}, {Theme.WARN, "found by the biome it generates in"}};
                for (Object[] e : k) {
                    g.setColor((Color) e[0]);
                    g.fillOval(x, 5, 8, 8);
                    g.setColor(Theme.DIM);
                    g.drawString((String) e[1], x + 13, 13);
                    x += 13 + g.getFontMetrics().stringWidth((String) e[1]) + 18;
                }
            }
        };
        legend.setPreferredSize(new Dimension(100, 18));
        legend.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(legend);
        top.add(Box.createVerticalStrut(6));
        c.add(top, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(book, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        slim(sp.getVerticalScrollBar(), 8);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        c.add(sp, BorderLayout.CENTER);

        bookSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refreshBook(); }
            public void removeUpdate(DocumentEvent e) { refreshBook(); }
            public void changedUpdate(DocumentEvent e) { refreshBook(); }
        });
        book.onThumb = (list, i) -> viewer.open(this, list, i);
        book.onAdd = this::addToDescription;
        book.statusOf = this::statusOf;
        refreshBook();
        return c;
    }

    private void buildPills() {
        pillsPanel.removeAll();
        filterPills.clear();
        for (String f : new String[]{"All", "Biomes", "Structures"}) {
            Pill p = new Pill(f, Skin.accent(), true);
            p.setActive(f.equals(bookFilter));
            p.onClick(() -> { bookFilter = f; for (Pill q : filterPills) q.setActive(q.label.equals(f)); refreshBook(); });
            filterPills.add(p);
            pillsPanel.add(p);
        }
        Pill rev = new Pill("Test coordinates", Theme.MUTED, false);
        rev.setToolTipText("Type coordinates and see what you could have described to get exactly that spot");
        rev.onClick(this::openReverse);
        pillsPanel.add(rev);
        Pill gear = new Pill("\u2699  Settings", Theme.MUTED, false);
        gear.onClick(() -> { SettingsDialog dlg = new SettingsDialog(this); dlg.setVisible(true); });
        pillsPanel.add(gear);
        pillsPanel.revalidate();
        pillsPanel.repaint();
    }

    /** The "Test coordinates" window. */
    ReverseDialog openReverse() {
        if (reverseDlg != null && reverseDlg.isDisplayable()) { reverseDlg.setVisible(true); reverseDlg.toFront(); return reverseDlg; }
        reverseDlg = new ReverseDialog(this, grid, this::radius, s -> text.setText(s));
        reverseDlg.setVisible(true);
        return reverseDlg;
    }

    /** Called when a setting changes: redraw everything that uses the accent colour or the picture size. */
    private void applyLook() {
        buildPills();
        showReading();
        book.revalidate();
        book.repaint();
        searchBtn.repaint();
        count.repaint();
        funnelBox.repaint();
        if (!resultsBox.getComponents().equals(null)) resultsBox.repaint();
        repaint();
    }

    private void refreshBook() {
        String q = Lexicon.norm(bookSearch.getText());
        String raw = bookSearch.getText().trim().toLowerCase(Locale.ROOT);
        List<Lexicon.Item> out = new ArrayList<>();
        // a word like "mountains" or "river" stands for a whole family, and typos are forgiven
        Set<String> viaWord = new HashSet<>();
        if (raw.length() >= 3) {
            Lexicon.Ref ref = Reader.lookup(raw);
            if (ref != null) { viaWord.addAll(ref.biomes); viaWord.addAll(ref.structures); }
        }
        for (Lexicon.Item it : Lexicon.items()) {
            // only what can actually be searched is listed: overworld, and present in this map
            if (availability(it) > 1) continue;
            boolean ok;
            switch (bookFilter) {
                case "Biomes": ok = !it.structure(); break;
                case "Structures": ok = it.structure(); break;
                default: ok = true;
            }
            if (!ok) continue;
            if (!raw.isEmpty() && !viaWord.contains(it.id) && !matches(it, raw, q)) continue;
            out.add(it);
        }
        book.setItems(out);
        bookCount.setText(out.size() + " that can be searched   ·   click a picture to enlarge it, click the card to add it");
    }

    private static boolean matches(Lexicon.Item it, String raw, String q) {
        if (it.name.toLowerCase(Locale.ROOT).contains(raw) || it.id.contains(raw.replace(' ', '_')) || it.note.toLowerCase(Locale.ROOT).contains(raw)) return true;
        for (String a : it.aliases) if (Lexicon.norm(a).contains(q) || a.contains(raw)) return true;
        if (raw.equals("biome") || raw.equals("biomes")) return !it.structure();
        if (raw.equals("structure") || raw.equals("structures")) return it.structure();
        return false;
    }

    /** 0 searchable exactly, 1 searchable by biome, 2 not in this map, 3 cannot be placed. */
    private int availability(Lexicon.Item it) {
        if (it.dim == 'N' || it.dim == 'E') return 2;
        if (!it.structure()) return grid == null || PlaceSearch.known(grid, it.id) ? 0 : 2;
        if (PlaceSearch.hasPoints(it.id)) return 0;
        if (it.habitat == null) return 3;
        if (grid != null) for (String h : it.habitat) if (PlaceSearch.known(grid, h)) return 1;
        return grid == null ? 1 : 2;
    }

    private String statusOf(Lexicon.Item it) {
        switch (availability(it)) {
            case 0: return "SEARCHABLE";
            case 1: return "BY BIOME";
            case 2: return it.dim == 'N' || it.dim == 'E' ? "NOT IN OVERWORLD MAP" : "NOT IN YOUR MAP";
            default: return "NO POSITION LIST";
        }
    }

    private void addToDescription(Lexicon.Item it) {
        String cur = text.getText().stripTrailing();
        String add = it.name.toLowerCase(Locale.ROOT);
        if (cur.isEmpty()) text.setText(add);
        else text.setText(cur + (cur.endsWith(",") ? " " : ", ") + add);
        text.requestFocusInWindow();
        text.setCaretPosition(text.getText().length());
    }

    // ================================================================== reading

    private void typed() {
        debounce.restart();
        if (text.getText().isBlank()) { refreshReading(); liveCount(); }
    }

    private void reparse() {
        refreshReading();
        liveCount();
    }

    private void refreshReading() {
        reading = Reader.read(text.getText(), grid);
        if (text.getText().isBlank()) { distEdits.clear(); spotKey = null; anywhereOverride = null; }
        applyEdits();
        showReading();
    }

    /** A stable name for a claim: its label, and which one of that label it is. */
    private String keyOf(Reader.Claim c) {
        int n = 0;
        for (Reader.Claim o : reading.claims) { if (o == c) break; if (o.label().equals(c.label()) && (o.role == Reader.Role.ABSENT) == (c.role == Reader.Role.ABSENT)) n++; }
        return c.label() + "/" + (c.role == Reader.Role.ABSENT ? "no" : "yes") + "#" + n;
    }

    private Reader.Claim byKey(String key) {
        for (Reader.Claim c : reading.claims) if (keyOf(c).equals(key)) return c;
        return null;
    }

    private void applyEdits() {
        if (Boolean.TRUE.equals(anywhereOverride) && !reading.isAnywhere()) reading.makeAnywhere(grid);
        else if (Boolean.FALSE.equals(anywhereOverride) && reading.isAnywhere()) reading.clearAnywhere();
        if (spotKey != null && !reading.isAnywhere()) {
            Reader.Claim c = byKey(spotKey);
            if (c != null && c.searchable() && c.role != Reader.Role.TARGET) reading.makeSpot(c);
        }
        boolean any = false;
        for (Map.Entry<String, int[]> e : distEdits.entrySet()) {
            Reader.Claim c = byKey(e.getKey());
            if (c == null || c.role == Reader.Role.TARGET) continue;
            c.userMin = e.getValue()[0];
            c.userWithin = e.getValue()[1];
            any = true;
        }
        if (any) Reader.orient(reading);
    }

    /** Called when a row's distance is typed in. */
    void editDistance(Reader.Claim c, int min, int max) {
        distEdits.put(keyOf(c), new int[]{min, max});
        reading.setDistance(c, min, max);
        showReading();
        liveCount();
    }

    /** Called when a row's "make this the spot" is pressed. */
    void chooseSpot(Reader.Claim c) {
        spotKey = keyOf(c);
        anywhereOverride = Boolean.FALSE;
        reading.makeSpot(c);
        showReading();
        liveCount();
    }

    /** The "anywhere near all of these" switch under the rows. */
    private void toggleAnywhere() {
        boolean now = reading.isAnywhere();
        anywhereOverride = now ? Boolean.FALSE : Boolean.TRUE;
        spotKey = null;
        if (now) reading.clearAnywhere(); else reading.makeAnywhere(grid);
        showReading();
        liveCount();
    }

    private void resetEdits() {
        distEdits.clear();
        spotKey = null;
        anywhereOverride = null;
        refreshReading();
        liveCount();
    }

    private void showReading() {
        readingBox.removeAll();
        notesBox.removeAll();
        if (text.getText().isBlank()) {
            readingBox.add(wrapLabel("Nothing yet. Name the places you can see - biomes and structures - and say how close they are. Everything it understands shows up here.", Theme.UI, Theme.DIM, LEFT_W - 60));
        } else {
            if (!reading.claims.isEmpty()) {
                readingBox.add(wrapLabel("Click a distance to change it. Press \u201cmake this the spot\u201d on any row to get the coordinates of that one instead, or ask for coordinates anywhere near all of them.", Theme.SMALL, Theme.DIM, LEFT_W - 60));
                readingBox.add(Box.createVerticalStrut(8));
            }
            for (Reader.Claim c : reading.claims) readingBox.add(claimRow(c));
            if (reading.claims.isEmpty()) readingBox.add(wrapLabel("Nothing recognised yet.", Theme.UI, Theme.DIM, LEFT_W - 60));
            int named = 0;
            for (Reader.Claim c : reading.claims) if (!c.anywhere && c.role != Reader.Role.ABSENT && c.searchable()) named++;
            if (named >= 1) {
                JPanel ap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                ap.setOpaque(false);
                ap.setAlignmentX(Component.LEFT_ALIGNMENT);
                boolean on = reading.isAnywhere();
                Pill any = new Pill(on ? "\u2713  the spot is anywhere near all of these  \u2014  click to undo" : "or: the spot can be anywhere near all of these", Skin.accent(), false);
                if (on) any.setActive(true);
                any.setToolTipText("None of them is the spot: give me coordinates next to all of them. You can also write \u201ccoordinates next to the village and the river\u201d.");
                any.onClick(this::toggleAnywhere);
                ap.add(any);
                readingBox.add(Box.createVerticalStrut(2));
                readingBox.add(ap);
                readingBox.add(Box.createVerticalStrut(6));
            }
            if (!distEdits.isEmpty() || spotKey != null || anywhereOverride != null) {
                JPanel rp = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                rp.setOpaque(false);
                rp.setAlignmentX(Component.LEFT_ALIGNMENT);
                Pill reset = new Pill("undo my changes", Theme.MUTED, false);
                reset.onClick(this::resetEdits);
                rp.add(reset);
                readingBox.add(rp);
            }
            if (!reading.issues.isEmpty()) {
                notesBox.add(Box.createVerticalStrut(8));
                JPanel sep = new JPanel();
                sep.setBackground(Theme.LINE);
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                sep.setPreferredSize(new Dimension(1, 1));
                sep.setAlignmentX(Component.LEFT_ALIGNMENT);
                notesBox.add(sep);
                notesBox.add(Box.createVerticalStrut(6));
                for (Reader.Issue i : reading.issues) { if ((spotKey != null || anywhereOverride != null) && i.text.startsWith("Finding ")) continue; notesBox.add(issueRow(i)); }
            }
        }
        readingBox.revalidate();
        notesBox.revalidate();
        readingBox.repaint();
        notesBox.repaint();
    }

    private JComponent claimRow(Reader.Claim c) {
        JPanel r = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.smooth(g0);
                g.setColor(Theme.PANEL_HI);
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                if (c.role == Reader.Role.TARGET) {
                    g.setColor(new Color(Skin.accent().getRed(), Skin.accent().getGreen(), Skin.accent().getBlue(), 120));
                    g.setStroke(new BasicStroke(1.2f));
                    g.draw(new RoundRectangle2D.Float(.6f, .6f, getWidth() - 1.2f, getHeight() - 1.2f, 10, 10));
                }
                super.paintComponent(g0);
            }
        };
        r.setOpaque(false);
        r.setBorder(new EmptyBorder(8, 10, 8, 10));
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        Color sw = swatch(c);
        JComponent dot = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.smooth(g0);
                g.setColor(sw);
                g.fill(new RoundRectangle2D.Float(0, 2, 14, 14, 5, 5));
            }
        };
        dot.setPreferredSize(new Dimension(14, 18));
        r.add(dot, BorderLayout.WEST);

        JPanel mid = stack();
        JLabel name = Theme.label(c.label() + (c.ref.family ? "  ·  " + (c.ref.biomes.size() + c.ref.structures.size()) + " kinds" : ""), Theme.UI_BOLD, c.searchable() ? Theme.TEXT : Theme.MUTED);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        mid.add(name);
        String sub = null;
        if (c.parent != null && c.role != Reader.Role.TARGET) {
            String how = c.userWithin >= 0 ? "within " + String.format("%,d", c.userWithin) + " of" : (c.relation == null ? "near" : c.relation);
            sub = "\u21b3 " + (c.role == Reader.Role.ABSENT ? "not " : "") + how + " " + c.parent.label();
        } else if (c.ref.family) {
            List<String> names = new ArrayList<>();
            for (String id : c.ref.biomes) names.add(Lexicon.nameOf(id));
            for (String id : c.ref.structures) names.add(Lexicon.nameOf(id));
            sub = String.join(", ", names);
        } else if (!c.said.equalsIgnoreCase(c.label())) sub = "you said \"" + c.said + "\"";
        if (sub != null) mid.add(wrapLabel(sub, Theme.SMALL, c.parent != null ? Theme.MUTED : Theme.DIM, 250));
        mid.add(Box.createVerticalStrut(6));

        // controls: the distance (click to type a new one) and "make this the spot"
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.setOpaque(false);
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (!c.searchable()) {
            Pill p = new Pill("CAN'T SEARCH", Theme.WARN, true);
            p.setActive(true);
            controls.add(p);
        } else if (c.role == Reader.Role.TARGET) {
            Pill p = new Pill("THE SPOT  \u2014  coordinates are for this", Skin.accent(), true);
            p.setActive(true);
            controls.add(p);
        } else {
            controls.add(new DistanceChip(c, (mn, mx) -> editDistance(c, mn, mx)));
            if (c.role != Reader.Role.ABSENT) {
                Pill spot = new Pill("make this the spot", Skin.accent(), false);
                spot.setToolTipText("Give me the coordinates of this one instead");
                spot.onClick(() -> chooseSpot(c));
                controls.add(spot);
            }
        }
        mid.add(controls);
        r.add(mid, BorderLayout.CENTER);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
        JPanel wrap = stack();
        wrap.add(r);
        wrap.add(Box.createVerticalStrut(6));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        return wrap;
    }

    /** The distance on a row. Click it, delete the number, type a new one, press Enter. */
    static final class DistanceChip extends JPanel {
        private final Reader.Claim claim;
        private final java.util.function.BiConsumer<Integer, Integer> commit;
        private final CardLayout cards = new CardLayout();
        private final JTextField field = new JTextField(9);
        private boolean hover;
        private boolean editing;

        DistanceChip(Reader.Claim claim, java.util.function.BiConsumer<Integer, Integer> commit) {
            super(null);
            this.claim = claim;
            this.commit = commit;
            setLayout(cards);
            setOpaque(false);
            JComponent pill = new JComponent() {
                @Override public Dimension getPreferredSize() { return new Dimension(getFontMetrics(Theme.SMALL_B).stringWidth(label()) + 36, 24); }
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = Theme.smooth(g0);
                    int w = getWidth(), h = getHeight();
                    Color c = claim.role == Reader.Role.ABSENT ? Theme.BAD : Theme.GOOD;
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), hover ? 55 : 30));
                    g.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), hover ? 210 : 140));
                    g.setStroke(new BasicStroke(1f));
                    g.draw(new RoundRectangle2D.Float(.5f, .5f, w - 1, h - 1, h, h));
                    g.setFont(Theme.SMALL_B);
                    g.setColor(Theme.mix(c, Color.WHITE, 0.3));
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(label(), 11, (h + fm.getAscent() - fm.getDescent()) / 2);
                    // a little pencil: this can be edited
                    int px = w - 17, py = h / 2 + 4;
                    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(hover ? Theme.TEXT : Theme.mix(c, Theme.MUTED, 0.4));
                    g.drawLine(px, py, px + 7, py - 7);
                    g.drawLine(px, py, px - 1, py + 1);
                    g.drawLine(px + 2, py - 2, px + 5, py + 1);
                }
            };
            pill.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            pill.setToolTipText("Click to change how far away this is");
            pill.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; pill.repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; pill.repaint(); }
                public void mouseClicked(MouseEvent e) { startEditing(); }
            });
            add(pill, "pill");

            field.setOpaque(false);
            field.setForeground(Theme.TEXT);
            field.setCaretColor(Skin.accent());
            field.setFont(Theme.SMALL_B.deriveFont(12f));
            field.setBorder(new EmptyBorder(3, 10, 3, 10));
            field.setToolTipText("Type blocks (500), thousands (1.5k) or a range (400-800), then press Enter. Esc cancels.");
            field.addActionListener(e -> finish(true));
            field.addFocusListener(new FocusAdapter() { public void focusLost(FocusEvent e) { if (editing) finish(true); } });
            field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
            field.getActionMap().put("cancel", new AbstractAction() { public void actionPerformed(ActionEvent e) { finish(false); } });
            add(new RoundBox(field, 14), "edit");
            cards.show(this, "pill");
        }

        String claimLabel() { return claim.label(); }

        private String label() {
            String lead = claim.role == Reader.Role.ABSENT ? "NOT within " : "NEAR \u2264 ";
            if (claim.minWithin > 0 && claim.role != Reader.Role.ABSENT) return "NEAR  " + String.format("%,d", claim.minWithin) + "\u2013" + String.format("%,d", claim.within);
            return lead + String.format("%,d", claim.within);
        }

        @Override public Dimension getPreferredSize() { return editing ? new Dimension(124, 26) : new Dimension(getFontMetrics(Theme.SMALL_B).stringWidth(label()) + 36, 24); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        void startEditing() {
            editing = true;
            field.setText(claim.minWithin > 0 && claim.role != Reader.Role.ABSENT ? claim.minWithin + "-" + claim.within : String.valueOf(claim.within));
            cards.show(this, "edit");
            revalidate();
            if (getParent() != null) getParent().revalidate();
            field.requestFocusInWindow();
            field.selectAll();
        }

        /** Commits a typed distance (also used by tests). Returns false if it holds no number. */
        boolean typeDistance(String s) {
            int[] d = Reader.parseDistance(s);
            if (d == null || d[1] <= 0) return false;
            commit.accept(d[0], d[1]);
            return true;
        }

        private void finish(boolean save) {
            if (!editing) return;
            editing = false;
            String t = field.getText();
            if (save && !t.isBlank() && !typeDistance(t)) save = false;
            if (!save) { cards.show(this, "pill"); revalidate(); if (getParent() != null) getParent().revalidate(); }
        }
    }

    private static Color swatch(Reader.Claim c) {
        String id = !c.ref.biomes.isEmpty() ? c.ref.biomes.get(0) : !c.ref.structures.isEmpty() ? c.ref.structures.get(0) : null;
        Lexicon.Item it = id == null ? null : Lexicon.byId(id);
        return it != null ? it.colour : Theme.MUTED;
    }

    private JComponent issueRow(Reader.Issue i) {
        JPanel r = new JPanel(new BorderLayout(8, 0));
        r.setOpaque(false);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        Color c = i.sev == Reader.Sev.ERROR ? Theme.BAD : i.sev == Reader.Sev.WARN ? Theme.WARN : Skin.accent();
        String glyph = i.sev == Reader.Sev.ERROR ? "✕" : i.sev == Reader.Sev.WARN ? "!" : "i";
        JComponent ic = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.smooth(g0);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 45));
                g.fillOval(0, 2, 16, 16);
                g.setColor(c);
                g.setFont(Theme.SMALL_B);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(glyph, 8 - fm.stringWidth(glyph) / 2, 14);
            }
        };
        ic.setPreferredSize(new Dimension(16, 20));
        r.add(ic, BorderLayout.WEST);
        JPanel mid = stack();
        mid.add(wrapLabel(i.text, Theme.SMALL, i.sev == Reader.Sev.INFO ? Theme.MUTED : Theme.TEXT, LEFT_W - 120));
        r.add(mid, BorderLayout.CENTER);
        if (i.from != null && i.to != null) {
            Pill fix = new Pill("use \"" + i.to + "\"", Skin.accent(), false);
            fix.onClick(() -> replaceWord(i.from, i.to));
            JPanel e = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            e.setOpaque(false);
            e.add(fix);
            JPanel south = stack();
            south.add(Box.createVerticalStrut(3));
            south.add(e);
            e.setAlignmentX(Component.LEFT_ALIGNMENT);
            mid.add(Box.createVerticalStrut(3));
            fix.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel fl = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            fl.setOpaque(false);
            fl.setAlignmentX(Component.LEFT_ALIGNMENT);
            fl.add(fix);
            mid.add(fl);
        }
        JPanel wrap = stack();
        wrap.add(r);
        wrap.add(Box.createVerticalStrut(7));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrap.getPreferredSize().height));
        return wrap;
    }

    private void replaceWord(String from, String to) {
        String t = text.getText();
        String out = t.replaceAll("(?i)(?<![A-Za-z])" + java.util.regex.Pattern.quote(from) + "(?![A-Za-z])", java.util.regex.Matcher.quoteReplacement(to));
        if (!out.equals(t)) text.setText(out);
    }

    /** A label that wraps at a fixed width (Swing labels do not wrap on their own). */
    private static JLabel wrapLabel(String s, Font f, Color c, int width) {
        // Swing's HTML renderer lays "width" out a little wider than it says, so leave some room
        JLabel l = new JLabel("<html><body style='width:" + Math.max(70, (int) (width * 0.86)) + "px'>" + esc(s) + "</body></html>");
        l.setFont(f);
        l.setForeground(c);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // ================================================================== live count and search

    private void liveCount() {
        funnelBox.removeAll();
        funnelBox.revalidate();
        funnelBox.repaint();
        liveToken++;
        if (text.getText().isBlank()) { count.set("—", "describe a place", Theme.DIM); note(" ", Theme.MUTED); return; }
        if (grid == null) { count.set("—", "map not loaded", Theme.WARN); return; }
        if (!reading.canSearch()) { count.set("—", "can't search that yet", Theme.WARN); note("Fix the notes below, then it will count.", Theme.MUTED); return; }
        final int my = ++liveToken;
        count.set("…", "counting places", Theme.DIM);
        final BiomeGrid g = grid;
        final List<Reader.Claim> claims = reading.searchable();
        final int rad = radius();
        Thread t = new Thread(() -> {
            PlaceSearch.Result res = PlaceSearch.run(g, claims, rad, () -> my != liveToken);
            SwingUtilities.invokeLater(() -> { if (my == liveToken && !res.cancelled && !searching) showFunnel(res); });
        }, "describe-count");
        t.setDaemon(true);
        t.start();
    }

    private void showFunnel(PlaceSearch.Result res) {
        funnelBox.removeAll();
        long max = 1;
        for (long v : res.stepCounts) max = Math.max(max, v);
        for (int i = 0; i < res.steps.size(); i++) funnelBox.add(funnelRow(res.steps.get(i), res.stepCounts.get(i), max, i == res.steps.size() - 1));
        funnelBox.revalidate();
        funnelBox.repaint();
        long last = res.stepCounts.isEmpty() ? 0 : res.stepCounts.get(res.stepCounts.size() - 1);
        int regions = res.regions.size();
        String shown = res.truncated ? "20,000+" : String.format("%,d", regions);
        if (last == 0) { count.set("0", "nowhere matches", Theme.BAD); note("Nothing matches. Something in the description may be wrong, or those never meet in this world.", Theme.BAD); }
        else if (regions <= 40) { count.set(shown, regions == 1 ? "place matches" : "places match", Theme.GOOD); note("Small enough - press FIND IT to see where.", Theme.GOOD); }
        else { count.set(shown, "places match", Theme.WARN); note("Still broad. Add another landmark or say how close it is.", Theme.WARN); }
    }

    private JComponent funnelRow(String label, long n, long max, boolean last) {
        JComponent row = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.smooth(g0);
                int w = getWidth();
                g.setFont(Theme.SMALL);
                g.setColor(Theme.MUTED);
                String shown = label;
                FontMetrics fm = g.getFontMetrics();
                int avail = w - 110;
                while (fm.stringWidth(shown) > avail && shown.length() > 4) shown = shown.substring(0, shown.length() - 2);
                if (!shown.equals(label)) shown += "…";
                g.drawString(shown, 0, 11);
                String cnt = String.format("%,d", n);
                g.setColor(last ? Skin.accent() : Theme.TEXT);
                g.setFont(Theme.SMALL_B);
                g.drawString(cnt, w - g.getFontMetrics().stringWidth(cnt), 11);
                g.setColor(Theme.PANEL_HI);
                g.fill(new RoundRectangle2D.Float(0, 17, w, 5, 5, 5));
                double f = n <= 0 ? 0 : Math.max(0.012, Math.sqrt(n / (double) max));
                g.setColor(last ? Skin.accent() : Skin.accentDark());
                g.fill(new RoundRectangle2D.Float(0, 17, (float) (w * f), 5, 5, 5));
            }
        };
        row.setPreferredSize(new Dimension(100, 28));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private void search() {
        if (searching) { token++; return; }
        if (grid == null) { note("The biome map is not loaded.", Theme.WARN); return; }
        if (text.getText().isBlank()) { note("Describe the place first.", Theme.WARN); return; }
        reading = Reader.read(text.getText(), grid);
        applyEdits();
        showReading();
        final String typed = text.getText();
        final BiomeGrid g = grid;
        final int rad = radius();
        final int my = ++token;
        searching = true;
        searchBtn.text = "STOP";
        searchBtn.repaint();
        count.set("…", "searching", Theme.DIM);
        note("Searching the whole map…", Theme.MUTED);
        resultsBox.removeAll();
        resultsBox.add(wrapLabel("Searching the map…", Theme.UI, Theme.DIM, RIGHT_W - 60));
        resultsBox.revalidate();
        resultsBox.repaint();
        Thread t = new Thread(() -> {
            Reader.Reading rd = reading;
            // when the built-in reader gets nothing, an AI key (if one is set) gets a try
            if (rd.claims.isEmpty() && (Describe.hasKey() || Describe.hasGroq())) {
                try {
                    Describe.Result ai = Describe.parse(typed, g);
                    Reader.Reading fromAi = fromAi(ai.claims, g);
                    if (!fromAi.claims.isEmpty()) { rd = fromAi; rd.issues.add(0, new Reader.Issue(Reader.Sev.INFO, "Read by " + ai.how + ".", null, null)); }
                } catch (RuntimeException ignored) { }
            }
            final Reader.Reading used = rd;
            PlaceSearch.Result res = PlaceSearch.run(g, used.searchable(), rad, () -> my != token);
            SwingUtilities.invokeLater(() -> {
                searching = false;
                searchBtn.text = "FIND IT";
                searchBtn.repaint();
                if (my != token && res.cancelled) { note("Stopped.", Theme.MUTED); showEmptyResults(); return; }
                reading = used;
                showReading();
                showFunnel(res);
                showResults(res);
            });
        }, "describe-search");
        t.setDaemon(true);
        t.start();
    }

    private static Reader.Reading fromAi(List<BiomeQuery.Claim> claims, BiomeGrid g) {
        Reader.Reading out = new Reader.Reading();
        for (BiomeQuery.Claim c : claims) {
            Lexicon.Ref ref;
            if (c.structure != null) {
                ref = Lexicon.find(Lexicon.norm(c.structure.replace('_', ' ')));
            } else {
                ref = Lexicon.find(Lexicon.norm(c.biome.replace("minecraft:", "").replace('_', ' ')));
                if (ref == null || (!ref.biomes.contains(c.biome.replace("minecraft:", "")) && !ref.family)) {
                    Lexicon.Item it = Lexicon.byId(c.biome.replace("minecraft:", ""));
                    if (it != null) { ref = new Lexicon.Ref(it.name); ref.biomes.add(it.id); }
                }
            }
            if (ref == null) continue;
            Reader.Claim rc = Reader.claimFor(ref, c.biome, c.target ? Reader.Role.TARGET : c.absent ? Reader.Role.ABSENT : Reader.Role.NEAR, c.within);
            out.claims.add(rc);
        }
        Reader.resolveAll(out, g);
        return out;
    }

    private void showResults(PlaceSearch.Result res) {
        resultsBox.removeAll();
        if (res.regions.isEmpty()) {
            note("No place in the world matches that description.", Theme.BAD);
            resultsBox.add(wrapLabel("Nothing matches. Something in the description is wrong, or those places never meet in this world. Check the notes on the left.", Theme.UI, Theme.DIM, RIGHT_W - 60));
        } else {
            note(String.format("%,d matching cells, found in %.1f s.", res.cells, res.ms / 1000.0), res.regions.size() <= 40 ? Theme.GOOD : Theme.WARN);
            JLabel head = Theme.label(String.format("%,d place%s, nearest to spawn first", res.regions.size(), res.regions.size() == 1 ? "" : "s"), Theme.SMALL_B, Theme.MUTED);
            head.setAlignmentX(Component.LEFT_ALIGNMENT);
            resultsBox.add(head);
            resultsBox.add(Box.createVerticalStrut(8));
            int shown = Math.min(res.regions.size(), 40);
            for (int i = 0; i < shown; i++) resultsBox.add(resultRow(i + 1, res.regions.get(i), res.step));
            if (res.regions.size() > shown) resultsBox.add(wrapLabel("and " + (res.regions.size() - shown) + " more - narrow it further to see them.", Theme.SMALL, Theme.DIM, RIGHT_W - 60));
        }
        resultsBox.revalidate();
        resultsBox.repaint();
    }

    private void showEmptyResults() {
        resultsBox.removeAll();
        JComponent hint = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.smooth(g0);
                int w = getWidth();
                g.setColor(Theme.PANEL_HI);
                g.fill(new RoundRectangle2D.Float(0, 0, w, getHeight(), 12, 12));
                g.setColor(Theme.DIM);
                g.setStroke(new BasicStroke(2f));
                int cx = w / 2, cy = 44;
                g.drawOval(cx - 16, cy - 16, 32, 32);
                g.drawLine(cx + 12, cy + 12, cx + 22, cy + 22);
                g.setFont(Theme.UI);
                g.setColor(Theme.MUTED);
                String a = "Describe a place, then press FIND IT.";
                g.drawString(a, (w - g.getFontMetrics().stringWidth(a)) / 2, cy + 56);
                g.setFont(Theme.SMALL);
                g.setColor(Theme.DIM);
                String b = "Matching places appear here, nearest to spawn first.";
                g.drawString(b, (w - g.getFontMetrics().stringWidth(b)) / 2, cy + 76);
            }
        };
        hint.setPreferredSize(new Dimension(100, 160));
        hint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultsBox.add(hint);
        resultsBox.revalidate();
        resultsBox.repaint();
    }

    private JComponent resultRow(int rank, PlaceSearch.Region r, int step) {
        JPanel row = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = Theme.smooth(g0);
                g.setColor(Theme.PANEL_HI);
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                super.paintComponent(g0);
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(10, 12, 10, 10));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel n = Theme.label(String.valueOf(rank), Theme.TITLE, Theme.DIM);
        n.setPreferredSize(new Dimension(30, 24));
        row.add(n, BorderLayout.WEST);
        JPanel mid = stack();
        JLabel xy = Theme.label(String.format("X %,d    Z %,d", r.nearX, r.nearZ), Theme.MONO_B, Skin.accent());
        xy.setAlignmentX(Component.LEFT_ALIGNMENT);
        mid.add(xy);
        long area = r.cells * (long) step * step;
        String size = r.exact ? "exact position" : area >= 1_000_000 ? String.format("region of about %.1f million blocks²", area / 1e6) : String.format("region of about %,d blocks²", area);
        mid.add(wrapLabel(String.format("%s  ·  %,.0f blocks from spawn", r.biome, r.dist), Theme.SMALL, Theme.TEXT, 190));
        mid.add(wrapLabel(size, Theme.SMALL, Theme.DIM, 190));
        if (!r.exact) mid.add(wrapLabel(String.format("X %,d to %,d   Z %,d to %,d", r.minX, r.maxX, r.minZ, r.maxZ), Theme.SMALL, Theme.DIM, 190));
        row.add(mid, BorderLayout.CENTER);
        Theme.Btn map = new Theme.Btn("See map", true).on(() -> openMap(r.nearX, r.nearZ));
        map.setPreferredSize(new Dimension(78, 30));
        Theme.Btn copy = new Theme.Btn("Copy", false).on(() -> copy(r.nearX, r.nearZ));
        copy.setPreferredSize(new Dimension(78, 28));
        Theme.Btn use = new Theme.Btn("Use", false).on(() -> { if (app != null) app.useDescribedArea(r.nearX, r.nearZ); });
        use.setPreferredSize(new Dimension(78, 28));
        JPanel btns = new JPanel(new GridLayout(3, 1, 0, 6));
        btns.setOpaque(false);
        btns.add(map);
        btns.add(copy);
        btns.add(use);
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(btns, BorderLayout.NORTH);
        row.add(east, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        JPanel wrap = stack();
        wrap.add(row);
        wrap.add(Box.createVerticalStrut(8));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        return wrap;
    }

    private void copy(int x, int z) {
        String s = x + " ~ " + z;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
        note("Copied " + s + " - paste it after /tp " + Settings.player(), Theme.GOOD);
        Toast.show(this, "Copied  " + s + "   \u2014   paste it after /tp " + Settings.player(), Skin.accent());
    }

    /** Opens the map for a place that was found. */
    void openMap(int x, int z) {
        if (grid == null) { Toast.show(this, "The biome map is not loaded yet.", Theme.WARN); return; }
        try { MapWindow.open(this, grid, reading, x, z); }
        catch (RuntimeException e) { Toast.show(this, "Could not open the map: " + e.getMessage(), Theme.BAD); }
    }

    private void note(String s, Color c) {
        status.setText("<html><body style='width:268px'>" + esc(s) + "</body></html>");
        status.setForeground(c);
    }

    // ================================================================== the map file

    private void loadGridInBackground() {
        mapStatus.setText("Loading the biome map…");
        Thread t = new Thread(() -> {
            File f = BiomeGrid.defaultFile();
            if (!f.isFile()) {
                SwingUtilities.invokeLater(() -> {
                    mapStatus.setText("<html><body style='width:290px'>No biome map yet. Build it with the biome-grid tool, then put it at " + esc(f.getPath()) + "</body></html>");
                    mapStatus.setForeground(Theme.WARN);
                });
                return;
            }
            try {
                BiomeGrid g = BiomeGrid.load(f);
                SwingUtilities.invokeLater(() -> {
                    grid = g;
                    mapStatus.setText(String.format("Biome map ready  ·  seed %d  ·  %d biomes  ·  every %d blocks", g.seed, g.biomes.length, g.step));
                    mapStatus.setForeground(Theme.DIM);
                    refreshReading();
                    refreshBook();
                    if (!text.getText().isBlank()) liveCount();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> { mapStatus.setText("<html><body style='width:290px'>Could not read the biome map: " + esc(String.valueOf(e.getMessage())) + "</body></html>"); mapStatus.setForeground(Theme.BAD); });
            }
        }, "biome-grid-load");
        t.setDaemon(true);
        t.start();
    }

    // for tests and screenshots
    void setDescription(String s) { text.setText(s); reparse(); }
    /** Types a distance into the row for this label, as if clicked and typed. */
    boolean typeDistanceFor(String label, String typed) {
        for (Component c : allComponents(readingBox)) if (c instanceof DistanceChip && ((DistanceChip) c).claimLabel().equals(label)) return ((DistanceChip) c).typeDistance(typed);
        return false;
    }
    void makeSpotFor(String label) { for (Reader.Claim c : reading.claims) if (c.label().equals(label) && c.role != Reader.Role.TARGET) { chooseSpot(c); return; } }
    private static List<Component> allComponents(Container c) {
        List<Component> out = new ArrayList<>();
        for (Component x : c.getComponents()) { out.add(x); if (x instanceof Container) out.addAll(allComponents((Container) x)); }
        return out;
    }
    Reader.Reading currentReading() { return reading; }
    void setBookFilter(String f) { bookFilter = f; for (Pill q : filterPills) q.setActive(q.label.equals(f)); refreshBook(); }
    void setBookQuery(String q) { bookSearch.setText(q); }
    void setGrid(BiomeGrid g) { grid = g; refreshReading(); refreshBook(); }
    void runSearchNow() { search(); }
    Viewer viewer() { return viewer; }
    BookGrid bookGrid() { return book; }

    // ================================================================== small custom controls

    private static JTextField field(String t) {
        JTextField f = new JTextField(t);
        f.setOpaque(false);
        f.setBorder(new EmptyBorder(4, 8, 4, 8));
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Skin.accent());
        f.setFont(Theme.UI);
        return f;
    }

    /** A text area that shows a hint while empty. */
    static final class PlainArea extends JTextArea {
        private String hint = "";
        PlainArea() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setForeground(Theme.TEXT);
            setCaretColor(Skin.accent());
            setBorder(new EmptyBorder(10, 12, 10, 12));
            setSelectionColor(Skin.accentDark());
            setSelectedTextColor(Theme.TEXT);
        }
        void setHint(String h) { hint = h; }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (getText().isEmpty() && !hint.isEmpty()) {
                Graphics2D g = Theme.smooth(g0);
                g.setColor(Theme.DIM);
                g.setFont(getFont());
                FontMetrics fm = g.getFontMetrics();
                int w = getWidth() - 24, y = 10 + fm.getAscent();
                StringBuilder line = new StringBuilder();
                for (String word : hint.split(" ")) {
                    if (fm.stringWidth(line + word) > w) { g.drawString(line.toString(), 12, y); y += fm.getHeight(); line.setLength(0); }
                    line.append(word).append(' ');
                }
                g.drawString(line.toString(), 12, y);
            }
        }
    }

    static final class PlainField extends JTextField {
        private final String hint;
        PlainField(String hint) {
            this.hint = hint;
            setOpaque(false);
            setBorder(new EmptyBorder(6, 12, 6, 12));
            setForeground(Theme.TEXT);
            setCaretColor(Skin.accent());
            setFont(Theme.UI.deriveFont(14f));
        }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (getText().isEmpty()) {
                Graphics2D g = Theme.smooth(g0);
                g.setColor(Theme.DIM);
                g.setFont(getFont());
                g.drawString(hint, 12, (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
            }
        }
    }

    /** Rounded dark box that also lights its border while its text field has focus. */
    static final class RoundBox extends JPanel {
        private final int radius;
        private boolean focus;
        RoundBox(JComponent inner, int radius) {
            super(new BorderLayout());
            this.radius = radius;
            setOpaque(false);
            add(inner, BorderLayout.CENTER);
            Component target = inner instanceof JScrollPane ? ((JScrollPane) inner).getViewport().getView() : inner;
            target.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { focus = true; repaint(); }
                public void focusLost(FocusEvent e) { focus = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.smooth(g0);
            g.setColor(Theme.PANEL_HI);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            g.setColor(focus ? Skin.accentDark() : Theme.LINE);
            g.setStroke(new BasicStroke(focus ? 1.5f : 1f));
            g.draw(new RoundRectangle2D.Float(.5f, .5f, getWidth() - 1, getHeight() - 1, radius, radius));
            super.paintComponent(g0);
        }
    }

    /** A small rounded label that can be clicked. */
    static final class Pill extends JComponent {
        final String label;
        private final Color colour;
        private final boolean toggle;
        private boolean active, hover;
        private Runnable click;
        Pill(String label, Color colour, boolean toggle) {
            this.label = label; this.colour = colour; this.toggle = toggle;
            setFont(Theme.SMALL_B);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseClicked(MouseEvent e) { if (click != null) click.run(); }
            });
        }
        Pill onClick(Runnable r) { click = r; return this; }
        void setActive(boolean a) { active = a; repaint(); }
        @Override public Dimension getPreferredSize() { return new Dimension(getFontMetrics(getFont()).stringWidth(label) + 20, 24); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.smooth(g0);
            g.setFont(getFont());
            int w = getWidth(), h = getHeight();
            boolean on = active || (hover && click != null && !toggle);
            g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), on ? (active ? 48 : 30) : (hover ? 22 : 12)));
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), on ? 170 : 70));
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Float(.5f, .5f, w - 1, h - 1, h, h));
            g.setColor(on ? Theme.mix(colour, Color.WHITE, 0.25) : Theme.mix(colour, Theme.MUTED, 0.4));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(label, (w - fm.stringWidth(label)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    static void slim(JScrollBar b, int width) {
        b.setUI(new SlimBar());
        b.setOpaque(false);
        b.setPreferredSize(new Dimension(width, 0));
    }

    static final class SlimBar extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { thumbColor = Theme.LINE; trackColor = new Color(0, 0, 0, 0); }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private static JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        @Override protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
            Graphics2D g = Theme.smooth(g0);
            g.setColor(isThumbRollover() ? Theme.DIM : Theme.LINE);
            g.fill(new RoundRectangle2D.Float(r.x + 1, r.y + 2, r.width - 2, r.height - 4, 6, 6));
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
    }

    /** A flow layout that wraps and reports the height it needs when wrapped. */
    static final class WrapLayout extends FlowLayout {
        WrapLayout(int h, int v) { super(FlowLayout.LEFT, h, v); }
        @Override public Dimension preferredLayoutSize(Container t) { return size(t, true); }
        @Override public Dimension minimumLayoutSize(Container t) { return size(t, false); }
        private Dimension size(Container t, boolean pref) {
            synchronized (t.getTreeLock()) {
                int width = t.getWidth();
                if (width == 0) width = Integer.MAX_VALUE / 4;
                Insets in = t.getInsets();
                int max = width - in.left - in.right - getHgap() * 2;
                Dimension d = new Dimension(0, 0);
                int rowW = 0, rowH = 0;
                for (Component c : t.getComponents()) {
                    if (!c.isVisible()) continue;
                    Dimension cd = pref ? c.getPreferredSize() : c.getMinimumSize();
                    if (rowW + cd.width > max && rowW > 0) { d.width = Math.max(d.width, rowW); d.height += rowH + getVgap(); rowW = 0; rowH = 0; }
                    rowW += cd.width + getHgap();
                    rowH = Math.max(rowH, cd.height);
                }
                d.width = Math.max(d.width, rowW);
                d.height += rowH + in.top + in.bottom + getVgap() * 2;
                if (width < Integer.MAX_VALUE / 4) d.width = Math.min(d.width, width);
                return d;
            }
        }
    }

    // ================================================================== the book grid

    interface ThumbOpen { void open(List<Lexicon.Item> list, int index); }

    static final class BookGrid extends JPanel implements Scrollable {
        private List<Lexicon.Item> items = List.of();
        private int hoverIdx = -1, hoverZone;
        ThumbOpen onThumb;
        java.util.function.Consumer<Lexicon.Item> onAdd;
        java.util.function.Function<Lexicon.Item, String> statusOf = it -> null;
        private static final int GAP = 12;
        private static int cw() { switch (Settings.get("cardSize", "medium")) { case "small": return 150; case "large": return 236; default: return 176; } }
        private static int ch() { return (cw() - 12) * 9 / 16 + 74; }

        BookGrid() {
            setOpaque(false);
            ToolTipManager.sharedInstance().registerComponent(this);
            MouseAdapter ma = new MouseAdapter() {
                public void mouseMoved(MouseEvent e) { update(e.getPoint()); }
                public void mouseExited(MouseEvent e) { hoverIdx = -1; repaint(); }
                public void mouseClicked(MouseEvent e) {
                    int i = indexAt(e.getPoint());
                    if (i < 0) return;
                    Rectangle r = cell(i);
                    if (thumbRect(r).contains(e.getPoint())) { if (onThumb != null) onThumb.open(items, i); }
                    else if (onAdd != null) onAdd.accept(items.get(i));
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void setItems(List<Lexicon.Item> l) { items = l; revalidate(); repaint(); }

        private int cols() { return Math.max(1, (getWidth() + GAP) / (cw() + GAP)); }

        private Rectangle cell(int i) {
            int cols = cols();
            int used = cols * cw() + (cols - 1) * GAP;
            int x0 = Math.max(0, (getWidth() - used) / 2);
            return new Rectangle(x0 + (i % cols) * (cw() + GAP), (i / cols) * (ch() + GAP), cw(), ch());
        }

        private Rectangle thumbRect(Rectangle c) { return new Rectangle(c.x + 6, c.y + 6, c.width - 12, (c.width - 12) * 9 / 16); }

        private int indexAt(Point p) {
            for (int i = 0; i < items.size(); i++) if (cell(i).contains(p)) return i;
            return -1;
        }

        private void update(Point p) {
            int i = indexAt(p);
            int z = i >= 0 && thumbRect(cell(i)).contains(p) ? 1 : (i >= 0 ? 2 : 0);
            if (i != hoverIdx || z != hoverZone) { hoverIdx = i; hoverZone = z; repaint(); }
            setCursor(Cursor.getPredefinedCursor(i >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }

        @Override public String getToolTipText(MouseEvent e) {
            int i = indexAt(e.getPoint());
            if (i < 0) return null;
            return hoverZone == 1 ? "Click to see the picture full size" : "Click to add \"" + items.get(i).name.toLowerCase(Locale.ROOT) + "\" to your description";
        }

        @Override public Dimension getPreferredSize() {
            int cols = cols();
            int rows = (items.size() + cols - 1) / cols;
            return new Dimension(100, Math.max(40, rows * (ch() + GAP)));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.smooth(g0);
            Rectangle clip = g.getClipBounds() != null ? g.getClipBounds() : new Rectangle(0, 0, getWidth(), getHeight());
            if (items.isEmpty()) {
                g.setColor(Theme.DIM);
                g.setFont(Theme.UI);
                String s = "Nothing in the book matches that.";
                g.drawString(s, (getWidth() - g.getFontMetrics().stringWidth(s)) / 2, 60);
                return;
            }
            for (int i = 0; i < items.size(); i++) {
                Rectangle c = cell(i);
                if (!c.intersects(clip)) continue;
                paintCard(g, items.get(i), c, i == hoverIdx, i == hoverIdx && hoverZone == 1);
            }
        }

        private void paintCard(Graphics2D g, Lexicon.Item it, Rectangle c, boolean hover, boolean overThumb) {
            g.setColor(hover ? Theme.PANEL_HI : Theme.mix(Theme.PANEL, Theme.PANEL_HI, 0.5));
            g.fill(new RoundRectangle2D.Float(c.x, c.y, c.width, c.height, 12, 12));
            g.setColor(hover ? Skin.accentDark() : Theme.LINE_SOFT);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Float(c.x + .5f, c.y + .5f, c.width - 1, c.height - 1, 12, 12));
            Rectangle t = thumbRect(c);
            Shape oldClip = g.getClip();
            g.clip(new RoundRectangle2D.Float(t.x, t.y, t.width, t.height, 8, 8));
            Image im = Previews.get(it);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int pad = overThumb ? 4 : 0;
            g.drawImage(im, t.x - pad, t.y - pad * 9 / 16, t.width + pad * 2, t.height + pad * 9 / 8, null);
            if (overThumb) {
                g.setColor(new Color(0, 0, 0, 90));
                g.fillRect(t.x, t.y, t.width, t.height);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = t.x + t.width / 2, cy = t.y + t.height / 2;
                g.drawOval(cx - 9, cy - 9, 14, 14);
                g.drawLine(cx + 2, cy + 2, cx + 9, cy + 9);
                g.drawLine(cx - 6, cy - 2, cx - 2, cy - 2);
                g.drawLine(cx - 4, cy - 4, cx - 4, cy);
            }
            g.setClip(oldClip);
            // type dot + availability
            String st = statusOf.apply(it);
            Color sc = st == null ? Theme.DIM : st.equals("SEARCHABLE") ? Theme.GOOD : st.equals("BY BIOME") ? Theme.WARN : Theme.DIM;
            g.setColor(new Color(0, 0, 0, 150));
            g.fill(new RoundRectangle2D.Float(t.x + 6, t.y + 6, 8, 8, 8, 8));
            g.setColor(sc);
            g.fill(new RoundRectangle2D.Float(t.x + 7, t.y + 7, 6, 6, 6, 6));
            if (it.structure()) {
                g.setFont(Theme.SMALL_B);
                String s = "STRUCTURE";
                int w = g.getFontMetrics().stringWidth(s) + 12;
                g.setColor(new Color(0, 0, 0, 150));
                g.fill(new RoundRectangle2D.Float(t.x + t.width - w - 6, t.y + 6, w, 16, 16, 16));
                g.setColor(new Color(235, 240, 248));
                g.drawString(s, t.x + t.width - w, t.y + 18);
            }
            int ty = t.y + t.height + 16;
            g.setFont(Theme.UI_BOLD);
            g.setColor(Theme.TEXT);
            g.drawString(ellipsis(g, it.name, c.width - 18), c.x + 9, ty);
            g.setFont(Theme.SMALL);
            g.setColor(Theme.DIM);
            g.drawString(ellipsis(g, it.note, c.width - 18), c.x + 9, ty + 15);
        }

        private static String ellipsis(Graphics2D g, String s, int w) {
            FontMetrics fm = g.getFontMetrics();
            if (fm.stringWidth(s) <= w) return s;
            while (s.length() > 3 && fm.stringWidth(s + "…") > w) s = s.substring(0, s.length() - 1);
            return s + "…";
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 28; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 240; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
