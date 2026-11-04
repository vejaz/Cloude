package slatefinder;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

/**
 * Full-window picture viewer for the book. Click a picture to open it, Escape (or a click anywhere) to close.
 * It lives in the window's glass pane, so it covers the whole app without touching any other tab.
 */
final class Viewer extends JComponent {
    private List<Lexicon.Item> items = List.of();
    private int index;
    private float fade;
    private Timer timer;
    private JRootPane root;
    private Component oldGlass;
    private Rectangle addBtn = new Rectangle(), closeBtn = new Rectangle(), prevBtn = new Rectangle(), nextBtn = new Rectangle(), imgBox = new Rectangle();
    private boolean hoverAdd, hoverClose;
    private final Consumer<Lexicon.Item> onAdd;
    private final java.util.function.Function<Lexicon.Item, String> status;
    private boolean openNow;

    Viewer(Consumer<Lexicon.Item> onAdd, java.util.function.Function<Lexicon.Item, String> status) {
        this.onAdd = onAdd;
        this.status = status;
        setOpaque(false);
        setFocusable(true);
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prev");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "add");
        am.put("close", new AbstractAction() { public void actionPerformed(ActionEvent e) { close(); } });
        am.put("prev", new AbstractAction() { public void actionPerformed(ActionEvent e) { step(-1); } });
        am.put("next", new AbstractAction() { public void actionPerformed(ActionEvent e) { step(1); } });
        am.put("add", new AbstractAction() { public void actionPerformed(ActionEvent e) { add(); } });
        MouseAdapter ma = new MouseAdapter() {
            public void mouseMoved(MouseEvent e) {
                boolean a = addBtn.contains(e.getPoint()), c = closeBtn.contains(e.getPoint());
                if (a != hoverAdd || c != hoverClose) { hoverAdd = a; hoverClose = c; repaint(); }
                setCursor(Cursor.getPredefinedCursor(a || c || prevBtn.contains(e.getPoint()) || nextBtn.contains(e.getPoint()) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
            public void mouseClicked(MouseEvent e) {
                Point p = e.getPoint();
                if (addBtn.contains(p)) add();
                else if (prevBtn.contains(p)) step(-1);
                else if (nextBtn.contains(p)) step(1);
                else if (closeBtn.contains(p) || !imgBox.contains(p)) close();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(e -> step(e.getWheelRotation() > 0 ? 1 : -1));
    }

    boolean isOpen() { return openNow; }

    void open(Component from, List<Lexicon.Item> list, int at) {
        JRootPane rp = SwingUtilities.getRootPane(from);
        if (rp == null || list.isEmpty()) return;
        items = list;
        index = Math.max(0, Math.min(list.size() - 1, at));
        if (!openNow) {
            root = rp;
            oldGlass = rp.getGlassPane();
            rp.setGlassPane(this);
            setVisible(true);
            fade = 0;
            if (timer != null) timer.stop();
            timer = new Timer(14, e -> { fade = Math.min(1f, fade + 0.14f); repaint(); if (fade >= 1f) ((Timer) e.getSource()).stop(); });
            timer.start();
        }
        openNow = true;
        requestFocusInWindow();
        repaint();
    }

    void close() {
        if (!openNow) return;
        openNow = false;
        if (timer != null) timer.stop();
        setVisible(false);
        if (root != null && oldGlass != null) { root.setGlassPane(oldGlass); oldGlass.setVisible(false); }
        root = null;
    }

    private void step(int d) {
        if (items.isEmpty()) return;
        index = (index + d + items.size()) % items.size();
        repaint();
    }

    private void add() {
        if (items.isEmpty()) return;
        onAdd.accept(items.get(index));
        close();
    }

    @Override public boolean contains(int x, int y) { return openNow; }

    @Override
    protected void paintComponent(Graphics g0) {
        if (!openNow || items.isEmpty()) return;
        Graphics2D g = Theme.smooth(g0);
        int w = getWidth(), h = getHeight();
        float f = fade * fade * (3 - 2 * fade);
        g.setColor(new Color(4, 6, 10, (int) (225 * f)));
        g.fillRect(0, 0, w, h);
        Lexicon.Item it = items.get(index);
        Image img = Previews.get(it);

        int maxW = Math.min(w - 220, 1280), maxH = h - 210;
        int iw = maxW, ih = iw * 9 / 16;
        if (ih > maxH) { ih = maxH; iw = ih * 16 / 9; }
        iw = Math.max(320, iw); ih = iw * 9 / 16;
        int x = (w - iw) / 2, y = (int) ((h - ih) / 2 - 36 + (1 - f) * 16);
        imgBox.setBounds(x - 16, y - 16, iw + 32, ih + 150);

        // frame + glow
        for (int i = 14; i > 0; i--) { g.setColor(new Color(it.colour.getRed(), it.colour.getGreen(), it.colour.getBlue(), (int) (4 * f))); g.fill(new RoundRectangle2D.Float(x - i * 1.6f, y - i * 1.6f, iw + i * 3.2f, ih + i * 3.2f, 18 + i, 18 + i)); }
        g.setColor(new Color(Theme.PANEL.getRed(), Theme.PANEL.getGreen(), Theme.PANEL.getBlue(), (int) (255 * f)));
        g.fill(new RoundRectangle2D.Float(x - 8, y - 8, iw + 16, ih + 16, 16, 16));
        Shape clip = g.getClip();
        g.setClip(new RoundRectangle2D.Float(x, y, iw, ih, 10, 10));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, f));
        g.drawImage(img, x, y, iw, ih, null);
        g.setComposite(AlphaComposite.SrcOver);
        g.setClip(clip);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Theme.LINE);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(x - 8 + .5f, y - 8 + .5f, iw + 15, ih + 15, 16, 16));

        // caption
        int cy = y + ih + 34;
        g.setFont(Theme.TITLE.deriveFont(Font.BOLD, 24f));
        g.setColor(Theme.TEXT);
        g.drawString(it.name, x, cy);
        int nameW = g.getFontMetrics().stringWidth(it.name);
        // tags
        int tx = x + nameW + 14;
        tx = tag(g, tx, cy - 17, it.structure() ? "STRUCTURE" : "BIOME", it.colour);
        tx = tag(g, tx, cy - 17, it.dimName().toUpperCase(), Theme.MUTED);
        String st = status.apply(it);
        if (st != null) tag(g, tx, cy - 17, st, st.startsWith("SEARCHABLE") ? Theme.GOOD : st.startsWith("BY BIOME") ? Theme.WARN : Theme.DIM);
        g.setFont(Theme.UI.deriveFont(14f));
        g.setColor(Theme.MUTED);
        drawWrapped(g, it.note, x, cy + 26, iw - 250);
        if (it.structure() && it.habitatNote != null) { g.setFont(Theme.SMALL); g.setColor(Theme.DIM); drawWrapped(g, it.habitatNote, x, cy + 66, iw - 250); }
        String say = "Say it as:  " + Lexicon.aliasKeys().stream().filter(k -> Lexicon.find(k) != null && Lexicon.find(k).label.equalsIgnoreCase(it.name)).limit(4).collect(java.util.stream.Collectors.joining("  ·  "));
        g.setFont(Theme.SMALL);
        g.setColor(Theme.DIM);
        if (say.length() > 12) g.drawString(say, x, cy + (it.structure() && it.habitatNote != null ? 90 : 56));

        // buttons
        int bw = 190, bh = 38;
        addBtn.setBounds(x + iw - bw, cy - 14, bw, bh);
        g.setColor(hoverAdd ? Theme.mix(Theme.ACCENT, Color.WHITE, 0.15) : Theme.ACCENT);
        g.fill(new RoundRectangle2D.Float(addBtn.x, addBtn.y, bw, bh, 10, 10));
        g.setColor(new Color(0x06222A));
        g.setFont(Theme.UI_BOLD.deriveFont(13f));
        centre(g, "ADD TO DESCRIPTION", addBtn);
        closeBtn.setBounds(x + iw - 34, y - 6 - 36, 30, 30);
        g.setColor(hoverClose ? Theme.TEXT : Theme.MUTED);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(closeBtn.x + 8, closeBtn.y + 8, closeBtn.x + 22, closeBtn.y + 22);
        g.drawLine(closeBtn.x + 22, closeBtn.y + 8, closeBtn.x + 8, closeBtn.y + 22);

        // arrows
        prevBtn.setBounds(x - 76, y + ih / 2 - 28, 56, 56);
        nextBtn.setBounds(x + iw + 20, y + ih / 2 - 28, 56, 56);
        arrow(g, prevBtn, true);
        arrow(g, nextBtn, false);

        g.setFont(Theme.SMALL);
        g.setColor(Theme.DIM);
        String hint = "Esc to close   ·   ← → to browse   ·   Enter to add   ·   " + (index + 1) + " / " + items.size();
        g.drawString(hint, (w - g.getFontMetrics().stringWidth(hint)) / 2, h - 22);
    }

    private static int tag(Graphics2D g, int x, int y, String s, Color c) {
        Font old = g.getFont();
        g.setFont(Theme.SMALL_B);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(s) + 16;
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
        g.fill(new RoundRectangle2D.Float(x, y, w, 22, 22, 22));
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 150));
        g.draw(new RoundRectangle2D.Float(x + .5f, y + .5f, w - 1, 21, 22, 22));
        g.setColor(Theme.mix(c, Color.WHITE, 0.35));
        g.drawString(s, x + 8, y + 15);
        g.setFont(old);
        return x + w + 8;
    }

    private static void drawWrapped(Graphics2D g, String s, int x, int y, int width) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            if (fm.stringWidth(line + word) > width && line.length() > 0) { g.drawString(line.toString(), x, y); y += fm.getHeight(); line.setLength(0); }
            line.append(word).append(' ');
        }
        if (line.length() > 0) g.drawString(line.toString(), x, y);
    }

    private static void centre(Graphics2D g, String s, Rectangle r) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, r.x + (r.width - fm.stringWidth(s)) / 2, r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2);
    }

    private void arrow(Graphics2D g, Rectangle r, boolean left) {
        g.setColor(new Color(255, 255, 255, 16));
        g.fillOval(r.x, r.y, r.width, r.height);
        g.setColor(Theme.MUTED);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = r.x + r.width / 2, cy = r.y + r.height / 2, d = left ? 1 : -1;
        g.drawLine(cx + d * 5, cy - 11, cx - d * 6, cy);
        g.drawLine(cx - d * 6, cy, cx + d * 5, cy + 11);
    }
}
