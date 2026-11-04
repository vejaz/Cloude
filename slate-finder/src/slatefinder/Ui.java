package slatefinder;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;
import javax.swing.JComponent;

/** Small controls drawn to match the app: switches, sliders and a segmented picker. */
final class Ui {
    private Ui() {}

    /** An on/off switch with a label. */
    static final class Toggle extends JComponent {
        private boolean on;
        private final String label;
        private boolean hover;
        private final Consumer<Boolean> change;

        Toggle(String label, boolean on, Consumer<Boolean> change) {
            this.label = label; this.on = on; this.change = change;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseClicked(MouseEvent e) { setOn(!Toggle.this.on); Toggle.this.change.accept(Toggle.this.on); }
            });
        }

        void setOn(boolean v) { on = v; repaint(); }
        boolean isOn() { return on; }
        @Override public Dimension getPreferredSize() { return new Dimension(200, 28); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 28); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.smooth(g0);
            int w = getWidth(), h = getHeight();
            g.setFont(Theme.UI);
            g.setColor(hover ? Theme.TEXT : Theme.MUTED);
            g.drawString(label, 0, (h + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
            int tw = 34, th = 18, tx = w - tw, ty = (h - th) / 2;
            g.setColor(on ? Skin.accentDark() : Theme.LINE);
            g.fill(new RoundRectangle2D.Float(tx, ty, tw, th, th, th));
            g.setColor(on ? Skin.accent() : Theme.DIM);
            g.fillOval(on ? tx + tw - th + 2 : tx + 2, ty + 2, th - 4, th - 4);
        }
    }

    /** A slider with a label and a value readout. */
    static final class Slider extends JComponent {
        private double value;
        private final double min, max;
        private final String label;
        private final java.util.function.DoubleFunction<String> format;
        private final Consumer<Double> change;
        private boolean drag;

        Slider(String label, double min, double max, double value, java.util.function.DoubleFunction<String> format, Consumer<Double> change) {
            this.label = label; this.min = min; this.max = max; this.value = value; this.format = format; this.change = change;
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { drag = true; set(e.getX()); }
                public void mouseDragged(MouseEvent e) { if (drag) set(e.getX()); }
                public void mouseReleased(MouseEvent e) { drag = false; }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        private void set(int x) {
            double t = Math.max(0, Math.min(1, (x - 2) / (double) Math.max(1, getWidth() - 4)));
            value = min + (max - min) * t;
            repaint();
            change.accept(value);
        }

        void setValue(double v) { value = v; repaint(); }
        @Override public Dimension getPreferredSize() { return new Dimension(200, 44); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 44); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.smooth(g0);
            int w = getWidth();
            g.setFont(Theme.UI);
            g.setColor(Theme.MUTED);
            g.drawString(label, 0, 13);
            g.setFont(Theme.SMALL_B);
            String v = format.apply(value);
            g.setColor(Theme.TEXT);
            g.drawString(v, w - g.getFontMetrics().stringWidth(v), 13);
            double t = (value - min) / (max - min);
            g.setColor(Theme.LINE);
            g.fill(new RoundRectangle2D.Float(0, 26, w, 5, 5, 5));
            g.setColor(Skin.accentDark());
            g.fill(new RoundRectangle2D.Float(0, 26, (float) (w * t), 5, 5, 5));
            g.setColor(Skin.accent());
            g.fillOval((int) Math.max(0, Math.min(w - 14, w * t - 7)), 22, 14, 14);
        }
    }

    /** A row of choices, one selected. */
    static final class Seg extends JComponent {
        private final String[] items;
        private int index;
        private final Consumer<Integer> change;
        private int hover = -1;

        Seg(String[] items, int index, Consumer<Integer> change) {
            this.items = items; this.index = index; this.change = change;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
                public void mouseClicked(MouseEvent e) { int i = at(e.getX()); if (i >= 0) { Seg.this.index = i; repaint(); Seg.this.change.accept(i); } }
            });
            addMouseMotionListener(new MouseAdapter() { public void mouseMoved(MouseEvent e) { int i = at(e.getX()); if (i != hover) { hover = i; repaint(); } } });
        }

        private int at(int x) { return Math.max(0, Math.min(items.length - 1, x * items.length / Math.max(1, getWidth()))); }
        void setIndex(int i) { index = i; repaint(); }
        @Override public Dimension getPreferredSize() { return isPreferredSizeSet() ? super.getPreferredSize() : new Dimension(200, 32); }
        @Override public Dimension getMaximumSize() { return isMaximumSizeSet() ? super.getMaximumSize() : new Dimension(Integer.MAX_VALUE, 32); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.smooth(g0);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.PANEL_HI);
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, 10, 10));
            float sw = w / (float) items.length;
            g.setColor(Skin.accentDark());
            g.fill(new RoundRectangle2D.Float(index * sw + 2, 2, sw - 4, h - 4, 8, 8));
            g.setFont(Theme.SMALL_B);
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < items.length; i++) {
                g.setColor(i == index ? Theme.TEXT : (i == hover ? Theme.MUTED : Theme.DIM));
                g.drawString(items[i], i * sw + (sw - fm.stringWidth(items[i])) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
    }
    /** A thin progress bar. A negative value means "working, no idea how long": a block slides along it. */
    static final class Meter extends JComponent {
        private double value;
        private Color colour = Skin.accent();
        private int phase;
        private final javax.swing.Timer timer = new javax.swing.Timer(40, e -> { phase = (phase + 7) % 1000; repaint(); });

        Meter() { setPreferredSize(new Dimension(100, 10)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 10)); }

        void set(double v, Color c) {
            value = v;
            if (c != null) colour = c;
            if (v < 0 && !timer.isRunning()) timer.start();
            if (v >= 0 && timer.isRunning()) timer.stop();
            repaint();
        }

        @Override public void removeNotify() { timer.stop(); super.removeNotify(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Theme.smooth(g0);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.LINE);
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            g.setColor(colour);
            if (value < 0) {
                float bw = w * 0.28f, x = (w + bw) * phase / 1000f - bw;
                Shape old = g.getClip();
                g.setClip(new RoundRectangle2D.Float(0, 0, w, h, h, h));
                g.fill(new RoundRectangle2D.Float(x, 0, bw, h, h, h));
                g.setClip(old);
            } else {
                g.fill(new RoundRectangle2D.Float(0, 0, (float) Math.max(h, w * Math.min(1, value)), h, h, h));
            }
        }
    }
}
