package slatefinder;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import javax.swing.*;

/** A short message that slides in at the bottom of the window and goes away on its own ("Copied ..."). */
final class Toast extends JComponent {
    private String text = "";
    private Color accent = Skin.accent();
    private float alpha;
    private javax.swing.Timer timer;
    private int ticks;

    private static Toast forRoot(JRootPane root) {
        for (Component c : root.getLayeredPane().getComponentsInLayer(JLayeredPane.POPUP_LAYER)) if (c instanceof Toast) return (Toast) c;
        Toast t = new Toast();
        root.getLayeredPane().add(t, JLayeredPane.POPUP_LAYER);
        return t;
    }

    /** Shows a message near the bottom of the window that holds {@code anchor}. */
    static void show(Component anchor, String message, Color accent) {
        if (anchor == null) return;
        JRootPane root = SwingUtilities.getRootPane(anchor);
        if (root == null) return;
        Toast t = forRoot(root);
        t.text = message;
        t.accent = accent == null ? Skin.accent() : accent;
        t.ticks = 0;
        t.alpha = 0;
        Container lp = root.getLayeredPane();
        FontMetrics fm = lp.getFontMetrics(Theme.UI_BOLD);
        int w = Math.min(lp.getWidth() - 40, fm.stringWidth(message) + 64), h = 44;
        t.setBounds((lp.getWidth() - w) / 2, lp.getHeight() - h - 70, w, h);
        t.setVisible(true);
        if (t.timer != null) t.timer.stop();
        t.timer = new javax.swing.Timer(16, e -> {
            t.ticks++;
            int total = 150;
            t.alpha = t.ticks < 12 ? t.ticks / 12f : t.ticks > total - 24 ? Math.max(0, (total - t.ticks) / 24f) : 1f;
            t.repaint();
            if (t.ticks >= total) { ((javax.swing.Timer) e.getSource()).stop(); t.setVisible(false); }
        });
        t.timer.start();
    }

    @Override public boolean contains(int x, int y) { return false; }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.smooth(g0);
        int w = getWidth(), h = getHeight();
        float rise = (1 - alpha) * 10;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, Math.min(1, alpha))));
        g.translate(0, rise);
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Float(2, 4, w - 4, h - 4, 22, 22));
        g.setColor(new Color(16, 20, 28));
        g.fill(new RoundRectangle2D.Float(0, 0, w - 4, h - 4, 22, 22));
        g.setColor(accent);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new RoundRectangle2D.Float(.7f, .7f, w - 5.4f, h - 5.4f, 22, 22));
        // a tick
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(18, h / 2 - 2, 23, h / 2 + 3);
        g.drawLine(23, h / 2 + 3, 31, h / 2 - 7);
        g.setFont(Theme.UI_BOLD);
        g.setColor(Theme.TEXT);
        g.drawString(text, 44, (h - 4 + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
    }
}
