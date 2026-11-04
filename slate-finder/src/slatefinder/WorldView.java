package slatefinder;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.*;

/**
 * An interactive view of a piece of the world: orbit it in 3D, stand in it and look around, or see it from above as
 * a flat map. Pictures are drawn on a background thread: a coarse one at once, then sharper ones while nothing moves.
 */
final class WorldView extends JComponent {
    enum Mode { ORBIT, WALK, MAP }

    /** A pin: the chosen spot, or a biome or structure of the description found near it. */
    static final class Marker {
        String label, sub;
        int x, z;
        Color colour = Color.WHITE;
        boolean primary;
        Marker parent;
        Marker(String label, int x, int z, Color c) { this.label = label; this.x = x; this.z = z; this.colour = c; }
    }

    final Terrain world;
    final Caster caster;
    final Caster.Opts opts = new Caster.Opts();
    Mode mode = Mode.ORBIT;

    // orbit
    double tx, tz, dist = 320, oyaw = Math.toRadians(32), opitch = Math.toRadians(-34);
    // walk
    double wx, wz, wy = 90, wyaw = Math.toRadians(30), wpitch = Math.toRadians(-3), walkFov = Math.toRadians(80);
    boolean fly;
    boolean arrowsMove = true;
    // map
    double mx, mz, bpp = 6;

    final List<Marker> markers = new ArrayList<>();
    List<Terrain.Site> sites = new ArrayList<>();
    boolean showMarkers = true, showLinks = true, showLabels = true, showSites = true, showGrid = true, showCompass = true, showHud = true;
    int quality = 2;
    Consumer<String> hoverInfo = s -> { };

    /** A finished picture and the view it was drawn from: pins and names are placed with these, so they stay on the ground while the picture catches up. */
    private static final class Frame {
        BufferedImage img;
        Caster.Cam cam;
        double mx, mz, bpp;
        Mode mode;
    }

    private volatile Frame frame;
    /** What the overlays are placed with while painting. */
    private Caster.Cam vCam;
    private double vMx, vMz, vBpp = 6;
    boolean simple;
    Consumer<Mode> modeListener = m -> { };
    /** Things that can be clicked to go there: {x, y, radius, world x, world z}, and what they are called. */
    private final List<double[]> hot = new ArrayList<>();
    private final List<String> hotName = new ArrayList<>();
    private int pressX, pressY;
    private boolean moved;
    private final AtomicInteger version = new AtomicInteger();
    private final Object lock = new Object();
    private boolean dirty;
    private volatile boolean alive = true;
    private volatile boolean busy;
    private volatile int doneStep = 99;
    private final AtomicInteger frames = new AtomicInteger();
    private int hx = -1, hy = -1;
    private String hoverText = "";
    private final boolean[] keys = new boolean[256];
    private final javax.swing.Timer mover;
    private int lastX, lastY, button;
    private long lastPick;

    WorldView(Terrain world) {
        this.world = world;
        this.caster = new Caster(world);
        setFocusable(true);
        setBackground(Theme.BG);
        setOpaque(true);
        Thread t = new Thread(this::loop, "world-view-render");
        t.setDaemon(true);
        t.start();
        mover = new javax.swing.Timer(16, e -> move());
        mover.start();

        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) { requestFocusInWindow(); lastX = pressX = e.getX(); lastY = pressY = e.getY(); button = e.getButton(); moved = false; }
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && !moved && Math.abs(e.getX() - pressX) < 5 && Math.abs(e.getY() - pressY) < 5) {
                    int i = hotAt(e.getX(), e.getY());
                    if (i >= 0) goTo(hot.get(i)[3], hot.get(i)[4], hotName.get(i));
                }
            }
            public void mouseDragged(MouseEvent e) {
                if (Math.abs(e.getX() - pressX) > 4 || Math.abs(e.getY() - pressY) > 4) moved = true;
                drag(e.getX() - lastX, e.getY() - lastY, button); lastX = e.getX(); lastY = e.getY(); hover(e.getX(), e.getY());
            }
            public void mouseMoved(MouseEvent e) { hover(e.getX(), e.getY()); }
            public void mouseExited(MouseEvent e) { hx = -1; repaint(); }
            public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 && hotAt(e.getX(), e.getY()) < 0) standHere(e.getX(), e.getY()); }
            public void mouseWheelMoved(MouseWheelEvent e) { zoom(e.getPreciseWheelRotation(), e.getX(), e.getY()); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k < keys.length) keys[k] = true;
                if (k == KeyEvent.VK_F) { fly = !fly; if (!fly) wy = groundHere() + 1.62; }
                else if (k == KeyEvent.VK_1) setMode(Mode.ORBIT);
                else if (k == KeyEvent.VK_2) setMode(Mode.WALK);
                else if (k == KeyEvent.VK_3) setMode(Mode.MAP);
                else if (k == KeyEvent.VK_R) home();
            }
            public void keyReleased(KeyEvent e) { if (e.getKeyCode() < keys.length) keys[e.getKeyCode()] = false; }
        });
        addComponentListener(new ComponentAdapter() { public void componentResized(ComponentEvent e) { render(); } });
    }

    void dispose() { alive = false; mover.stop(); synchronized (lock) { lock.notifyAll(); } }

    // ------------------------------------------------------------------ the camera

    /** Where the feet go: the ground itself in the real world (so you can walk under a roof or between trees), the top of anything on it otherwise. */
    private double groundHere() {
        int x = (int) Math.floor(wx), z = (int) Math.floor(wz);
        return world.exactTextures ? world.groundAt(x, z) : world.topAt(x, z);
    }

    Caster.Cam cam() {
        Caster.Cam c = new Caster.Cam();
        if (mode == Mode.WALK) {
            c.x = wx; c.z = wz; c.y = fly ? wy : groundHere() + 1.62;
            c.yaw = wyaw; c.pitch = wpitch; c.fov = walkFov;
        } else {
            double fx = Math.cos(opitch) * Math.sin(oyaw), fy = Math.sin(opitch), fz = Math.cos(opitch) * Math.cos(oyaw);
            double ty = world.topAt((int) Math.floor(tx), (int) Math.floor(tz)) + 1;
            c.x = tx - fx * dist; c.z = tz - fz * dist; c.y = ty - fy * dist;
            double gh = world.topAt((int) Math.floor(c.x), (int) Math.floor(c.z));
            if (c.y < gh + 3) c.y = gh + 3;
            c.yaw = oyaw;
            c.pitch = Math.atan2(ty - c.y, Math.max(0.01, Math.hypot(tx - c.x, tz - c.z)));
            c.fov = Math.toRadians(62);
        }
        return c;
    }

    void setMode(Mode m) {
        if (m == mode) return;
        if (m == Mode.WALK && mode == Mode.ORBIT) { wx = tx; wz = tz; wy = groundHere() + 1.62; wyaw = oyaw; }
        if (m == Mode.MAP) { mx = mode == Mode.WALK ? wx : tx; mz = mode == Mode.WALK ? wz : tz; }
        if (m == Mode.ORBIT && mode == Mode.WALK) { tx = wx; tz = wz; oyaw = wyaw; }
        if (m == Mode.ORBIT && mode == Mode.MAP) { tx = mx; tz = mz; }
        if (m == Mode.WALK && mode == Mode.MAP) { wx = mx; wz = mz; wy = groundHere() + 1.62; }
        mode = m;
        modeListener.accept(m);
        opts.maxDist = m == Mode.WALK ? Math.max(300, opts.maxDist) : opts.maxDist;
        render();
    }

    void focus(double x, double z) {
        tx = x; tz = z; mx = x; mz = z; wx = x; wz = z;
        if (mode == Mode.ORBIT) dist = Math.min(dist, 220);
        render();
    }

    /** Frames every pin: zooms the map, and pulls the 3D camera back, so they are all in view. */
    void fitAll() {
        if (markers.isEmpty()) { render(); return; }
        double x0 = 1e18, x1 = -1e18, z0 = 1e18, z1 = -1e18;
        for (Marker m : markers) { x0 = Math.min(x0, m.x); x1 = Math.max(x1, m.x); z0 = Math.min(z0, m.z); z1 = Math.max(z1, m.z); }
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, span = Math.max(60, Math.max(x1 - x0, z1 - z0));
        tx = mx = wx = cx; tz = mz = wz = cz;
        int w = Math.max(400, getWidth()), h = Math.max(300, getHeight());
        bpp = Math.max(0.35, span * 1.5 / Math.min(w, h));
        dist = Math.max(90, Math.min(2200, span * 1.35 + 70));
        wy = groundHere() + 1.62;
        render();
    }

    /** The overview of the chosen spot. */
    void home() {
        Marker p = null;
        for (Marker m : markers) if (m.primary) p = m;
        double x = p != null ? p.x : tx, z = p != null ? p.z : tz;
        tx = mx = wx = x; tz = mz = wz = z;
        dist = 320; opitch = Math.toRadians(-34); oyaw = Math.toRadians(32); bpp = 6; wyaw = Math.toRadians(30); wpitch = Math.toRadians(-3);
        wy = groundHere() + 1.62;
        render();
    }

    void standHere(int px, int py) {
        double[] p = hitAt(px, py);
        if (p == null) return;
        wx = p[0]; wz = p[2]; wy = world.topAt((int) Math.floor(wx), (int) Math.floor(wz)) + 1.62;
        if (mode == Mode.ORBIT) wyaw = oyaw;
        setMode(Mode.WALK);
        render();
    }

    /** Clicking a pin or a village dot: stand there, looking the way the view was looking. */
    void goTo(double x, double z, String name) {
        if (mode == Mode.ORBIT) wyaw = oyaw;
        if (mode != Mode.WALK) setMode(Mode.WALK);        // switching modes sets the standing place from the old view, so do it first
        wx = x + 0.5; wz = z + 0.5;
        wy = (world.exactTextures ? world.groundAt((int) Math.floor(x), (int) Math.floor(z)) : world.topAt((int) Math.floor(x), (int) Math.floor(z))) + 1.62;
        wpitch = Math.toRadians(-4);
        fly = false;
        hoverText = "Standing at " + name + "   X " + String.format("%,d", (long) Math.floor(x)) + "   Z " + String.format("%,d", (long) Math.floor(z));
        hoverInfo.accept(hoverText);
        render();
    }

    /** For tests: where the clickable pins and dots were drawn last: {x, y, radius, world x, world z}. */
    List<double[]> hotSpots() { return new ArrayList<>(hot); }
    int stepDone() { return doneStep; }
    int framesDrawn() { return frames.get(); }
    boolean isBusy() { return busy; }

    /** The clickable pin or dot under this point, or -1. */
    private int hotAt(int px, int py) {
        int best = -1;
        double bd = 1e18;
        for (int i = 0; i < hot.size(); i++) {
            double[] h = hot.get(i);
            double d = Math.hypot(px - h[0], py - h[1]);
            if (d <= h[2] && d < bd) { bd = d; best = i; }
        }
        return best;
    }

    private double[] hitAt(int px, int py) {
        int w = getWidth(), h = getHeight();
        Frame f = frame;
        boolean same = f != null && f.mode == mode;      // what is under the pointer is what is drawn, not where the camera is heading
        if (mode == Mode.MAP) {
            double m0 = same ? f.mx : mx, m1 = same ? f.mz : mz, b = same ? f.bpp : bpp;
            double x = m0 + (px - w / 2.0) * b, z = m1 + (py - h / 2.0) * b;
            return new double[]{x, world.topAt((int) Math.floor(x), (int) Math.floor(z)), z};
        }
        return caster.pick(same ? f.cam : cam(), w, h, px, py, opts);
    }

    // ------------------------------------------------------------------ input

    private void drag(int dx, int dy, int button) {
        if (mode == Mode.ORBIT) {
            if (button == MouseEvent.BUTTON1) { oyaw += dx * 0.006; opitch = Math.max(Math.toRadians(-89), Math.min(Math.toRadians(-3), opitch - dy * 0.005)); }
            else { double s = dist * 0.0016, rx = -Math.cos(oyaw), rz = Math.sin(oyaw), fx = Math.sin(oyaw), fz = Math.cos(oyaw); tx += (-rx * dx + fx * dy) * s; tz += (-rz * dx + fz * dy) * s; }
        } else if (mode == Mode.WALK) {
            wyaw -= dx * 0.004; wpitch = Math.max(Math.toRadians(-89), Math.min(Math.toRadians(89), wpitch - dy * 0.004));
        } else {
            mx -= dx * bpp; mz -= dy * bpp;
        }
        render();
    }

    /** Zooms toward the point under the pointer, so what you are looking at stays where it is while it gets bigger or smaller. */
    void zoom(double rot, int px, int py) {
        int w = getWidth(), h = getHeight();
        if (mode == Mode.ORBIT) {
            double nd = Math.max(3, Math.min(2800, dist * Math.exp(rot * 0.22)));
            double k = nd / dist;
            double[] p = k == 1 ? null : caster.pick(cam(), w, h, px, py, opts);
            if (p != null) { tx = p[0] + k * (tx - p[0]); tz = p[2] + k * (tz - p[2]); }
            dist = nd;
        } else if (mode == Mode.WALK) {
            double old = walkFov, nf = Math.max(Math.toRadians(18), Math.min(Math.toRadians(110), old * Math.exp(rot * 0.1)));
            double aspect = w / (double) Math.max(1, h), ndx = (px + 0.5) / w * 2 - 1, ndy = 1 - (py + 0.5) / h * 2;
            double tOld = Math.tan(old / 2), tNew = Math.tan(nf / 2);
            // turn toward the pointer by the angle the picture's shrinking moves it away from the middle
            wyaw -= Math.atan(ndx * tOld * aspect) - Math.atan(ndx * tNew * aspect);
            wpitch = Math.max(Math.toRadians(-89), Math.min(Math.toRadians(89), wpitch + Math.atan(ndy * tOld) - Math.atan(ndy * tNew)));
            walkFov = nf;
        } else {
            double bx = mx + (px - w / 2.0) * bpp, bz = mz + (py - h / 2.0) * bpp;
            bpp = Math.max(0.04, Math.min(90, bpp * Math.exp(rot * 0.22)));
            mx = bx - (px - w / 2.0) * bpp; mz = bz - (py - h / 2.0) * bpp;
        }
        render();
    }

    private void move() {
        if (!isShowing()) return;
        double dx = 0, dz = 0, dy = 0;
        boolean fast = keys[KeyEvent.VK_SHIFT];
        double sp = (mode == Mode.WALK ? (fly ? 0.9 : 0.32) : dist * 0.012) * (fast ? 3 : 1);
        if (keys[KeyEvent.VK_W] || (arrowsMove && keys[KeyEvent.VK_UP])) { dx += 1; }
        if (keys[KeyEvent.VK_S] || (arrowsMove && keys[KeyEvent.VK_DOWN])) { dx -= 1; }
        if (keys[KeyEvent.VK_A] || (arrowsMove && keys[KeyEvent.VK_LEFT])) { dz -= 1; }
        if (keys[KeyEvent.VK_D] || (arrowsMove && keys[KeyEvent.VK_RIGHT])) { dz += 1; }
        if (keys[KeyEvent.VK_SPACE] || keys[KeyEvent.VK_E]) dy += 1;
        if (keys[KeyEvent.VK_CONTROL] || keys[KeyEvent.VK_Q]) dy -= 1;
        if (dx == 0 && dz == 0 && dy == 0) return;
        if (mode == Mode.WALK) {
            double fx = Math.sin(wyaw), fz = Math.cos(wyaw), rx = -Math.cos(wyaw), rz = Math.sin(wyaw);
            wx += (fx * dx + rx * dz) * sp; wz += (fz * dx + rz * dz) * sp;
            if (fly) wy += dy * sp; else wy = groundHere() + 1.62;
        } else if (mode == Mode.ORBIT) {
            double fx = Math.sin(oyaw), fz = Math.cos(oyaw), rx = -Math.cos(oyaw), rz = Math.sin(oyaw);
            tx += (fx * dx + rx * dz) * sp; tz += (fz * dx + rz * dz) * sp;
        } else {
            mx += dz * sp * bpp * 4; mz += -dx * sp * bpp * 4;
        }
        render();
    }

    private void hover(int px, int py) {
        hx = px; hy = py;
        int hs = hotAt(px, py);
        setCursor(Cursor.getPredefinedCursor(hs >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        long now = System.currentTimeMillis();
        if (now - lastPick > 40) {
            lastPick = now;
            if (hs >= 0) {
                hoverText = hotName.get(hs) + "   X " + String.format("%,d", (long) hot.get(hs)[3]) + "   Z " + String.format("%,d", (long) hot.get(hs)[4]) + "   ·   click to stand there";
            } else {
                double[] p = hitAt(px, py);
                if (p != null) {
                    int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[2]);
                    hoverText = String.format("X %,d   Y %d   Z %,d   ·   %s", x, (int) Math.floor(p[1]), z, PlaceSearch.pretty(world.biomeName(x, z)));
                } else hoverText = "";
            }
            hoverInfo.accept(hoverText);
        }
        repaint();
    }

    // ------------------------------------------------------------------ drawing in the background

    void render() {
        version.incrementAndGet();
        synchronized (lock) { dirty = true; lock.notifyAll(); }
    }

    private void loop() {
        while (alive) {
            synchronized (lock) {
                while (!dirty && alive) { try { lock.wait(); } catch (InterruptedException e) { return; } }
                dirty = false;
            }
            if (!alive) return;
            int ver = version.get();
            int w = getWidth(), h = getHeight();
            if (w < 40 || h < 40) continue;
            boolean simpleNow = simple;
            // quick flat-coloured pictures first, so the view answers at once; the detailed one comes when it has held still for a moment
            int[] steps = simpleNow ? new int[]{8, 3, 2} : quality >= 3 ? new int[]{8, 4, 2, 1} : quality == 2 ? new int[]{8, 3, 2, 1} : new int[]{8, 4};
            busy = true;
            for (int s : steps) {
                if (ver != version.get()) break;
                if (s == 1) {
                    long until = System.currentTimeMillis() + 220;
                    while (System.currentTimeMillis() < until && ver == version.get() && alive) { try { Thread.sleep(15); } catch (InterruptedException e) { return; } }
                    if (ver != version.get()) break;
                }
                Frame f = new Frame();
                f.mode = mode; f.mx = mx; f.mz = mz; f.bpp = bpp;
                f.cam = mode == Mode.MAP ? null : cam();
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                int[] pix = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
                try {
                    opts.flat = simpleNow || s > 1;
                    if (f.mode == Mode.MAP) {
                        int rw = Math.max(1, (w + s - 1) / s), rh = Math.max(1, (h + s - 1) / s);
                        int[] small = new int[rw * rh];
                        caster.renderMap(small, rw, rh, f.mx, f.mz, f.bpp * s, opts, false, () -> ver != version.get());
                        for (int y = 0; y < h; y++) { int sy = Math.min(rh - 1, y / s); for (int x = 0; x < w; x++) pix[y * w + x] = small[sy * rw + Math.min(rw - 1, x / s)]; }
                    } else {
                        caster.render3D(pix, w, h, s, f.cam, opts, s == 1, () -> ver != version.get());
                    }
                } catch (RuntimeException ex) { break; }
                if (ver != version.get()) break;
                f.img = img;
                frame = f;
                doneStep = s;
                frames.incrementAndGet();
                SwingUtilities.invokeLater(this::repaint);
            }
            busy = false;
            SwingUtilities.invokeLater(this::repaint);
        }
    }

    BufferedImage snapshot() {
        BufferedImage im = new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        paint(g);
        g.dispose();
        return im;
    }

    // ------------------------------------------------------------------ the picture and what is drawn over it

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = Theme.smooth(g0);
        int w = getWidth(), h = getHeight();
        g.setColor(Theme.BG);
        g.fillRect(0, 0, w, h);
        Frame fr = frame;
        BufferedImage im = fr == null ? null : fr.img;
        boolean same = fr != null && fr.mode == mode;
        vCam = same ? fr.cam : (mode == Mode.MAP ? null : cam());
        vMx = same ? fr.mx : mx; vMz = same ? fr.mz : mz; vBpp = same ? fr.bpp : bpp;
        hot.clear();
        hotName.clear();
        if (im != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(im, 0, 0, w, h, null);
        } else {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.UI);
            g.drawString("Building the landscape…", w / 2 - 70, h / 2);
        }
        Caster.Cam cam = vCam;
        if (showGrid && mode == Mode.MAP) drawGrid(g, w, h);
        if (showSites) drawSites(g, cam, w, h);
        if (showMarkers) drawMarkers(g, cam, w, h);
        if (showHud) drawHud(g, cam, w, h);
    }

    private double[] screen(Caster.Cam cam, int w, int h, double x, double y, double z) {
        if (mode == Mode.MAP) return new double[]{w / 2.0 + (x - vMx) / vBpp, h / 2.0 + (z - vMz) / vBpp, 1};
        return Caster.project(cam, w, h, x, y, z);
    }

    private void drawGrid(Graphics2D g, int w, int h) {
        double spacing = 16;
        while (spacing / vBpp < 70) spacing *= (spacing < 64 ? 4 : 4);
        if (spacing / vBpp > 400 && spacing > 16) spacing /= 4;
        double x0 = vMx - w / 2.0 * vBpp, x1 = vMx + w / 2.0 * vBpp, z0 = vMz - h / 2.0 * vBpp, z1 = vMz + h / 2.0 * vBpp;
        g.setStroke(new BasicStroke(1f));
        g.setFont(Theme.SMALL);
        for (double x = Math.floor(x0 / spacing) * spacing; x <= x1; x += spacing) {
            int sx = (int) Math.round(w / 2.0 + (x - vMx) / vBpp);
            g.setColor(new Color(255, 255, 255, spacing % 1024 == 0 ? 60 : 32));
            g.drawLine(sx, 0, sx, h);
            g.setColor(new Color(255, 255, 255, 150));
            g.drawString(String.format("%,d", (long) x), sx + 4, 14);
        }
        for (double z = Math.floor(z0 / spacing) * spacing; z <= z1; z += spacing) {
            int sy = (int) Math.round(h / 2.0 + (z - vMz) / vBpp);
            g.setColor(new Color(255, 255, 255, spacing % 1024 == 0 ? 60 : 32));
            g.drawLine(0, sy, w, sy);
            g.setColor(new Color(255, 255, 255, 150));
            g.drawString(String.format("%,d", (long) z), 6, sy - 3);
        }
    }

    private void drawSites(Graphics2D g, Caster.Cam cam, int w, int h) {
        int drawn = 0;
        double cx = mode == Mode.MAP ? mx : (mode == Mode.WALK ? wx : tx), cz = mode == Mode.MAP ? mz : (mode == Mode.WALK ? wz : tz);
        double range = mode == Mode.MAP ? bpp * Math.max(w, h) : Math.max(500, opts.maxDist);
        for (Terrain.Site s : sites) {
            if (Math.abs(s.x - cx) > range || Math.abs(s.z - cz) > range) continue;
            double gy = mode == Mode.MAP ? 0 : world.topAt(s.x, s.z) + 8;
            double[] p = screen(cam, w, h, s.x, gy, s.z);
            if (p == null || p[0] < -20 || p[0] > w + 20 || p[1] < -20 || p[1] > h + 20) continue;
            if (++drawn > 80) break;
            house(g, (int) p[0], (int) p[1], new Color(0xF2C46B));
            hot.add(new double[]{p[0], p[1], 12, s.x, s.z});
            hotName.add(Lexicon.nameOf(s.kind));
        }
    }

    private static void house(Graphics2D g, int x, int y, Color c) {
        g.setColor(new Color(0, 0, 0, 150));
        g.fillOval(x - 9, y - 9, 18, 18);
        g.setColor(c);
        Path2D.Float roof = new Path2D.Float();
        roof.moveTo(x - 6, y - 1); roof.lineTo(x, y - 7); roof.lineTo(x + 6, y - 1); roof.closePath();
        g.fill(roof);
        g.fillRect(x - 4, y - 1, 8, 6);
    }

    private void drawMarkers(Graphics2D g, Caster.Cam cam, int w, int h) {
        java.util.Map<Marker, double[]> top = new java.util.IdentityHashMap<>();
        for (Marker m : markers) {
            double gy = world.topAt(m.x, m.z);
            double[] base = screen(cam, w, h, m.x + 0.5, gy, m.z + 0.5);
            double[] tip = mode == Mode.MAP ? base : screen(cam, w, h, m.x + 0.5, gy + (m.primary ? 70 : 42), m.z + 0.5);
            if (base == null || tip == null) continue;
            top.put(m, tip);
        }
        if (showLinks) {
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{7f, 5f}, 0f));
            for (Marker m : markers) {
                if (m.parent == null) continue;
                double[] a = top.get(m), b = top.get(m.parent);
                if (a == null || b == null) continue;
                g.setColor(new Color(m.colour.getRed(), m.colour.getGreen(), m.colour.getBlue(), 190));
                g.drawLine((int) a[0], (int) a[1], (int) b[0], (int) b[1]);
            }
            g.setStroke(new BasicStroke(1f));
        }
        List<Marker> order = new ArrayList<>(markers);
        order.sort((a, b) -> Boolean.compare(a.primary, b.primary));
        placedLabels.clear();
        for (Marker m : order) {
            double[] tip = top.get(m);
            if (tip == null) continue;
            int x = (int) tip[0], y = (int) tip[1];
            if (x < -200 || x > w + 200 || y < -100 || y > h + 100) continue;
            Color c = m.colour;
            if (mode != Mode.MAP) {
                double[] base = screen(cam, w, h, m.x + 0.5, world.topAt(m.x, m.z), m.z + 0.5);
                if (base != null) {
                    g.setStroke(new BasicStroke(m.primary ? 4f : 2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
                    g.drawLine((int) base[0], (int) base[1], x, y);
                    g.setStroke(new BasicStroke(m.primary ? 2f : 1.2f));
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 230));
                    g.drawLine((int) base[0], (int) base[1], x, y);
                    g.fillOval((int) base[0] - 3, (int) base[1] - 3, 6, 6);
                    g.setStroke(new BasicStroke(1f));
                }
            }
            // the pin
            int r = m.primary ? 11 : 8;
            g.setColor(new Color(0, 0, 0, 170));
            g.fillOval(x - r - 2, y - r - 2, r * 2 + 4, r * 2 + 4);
            g.setColor(c);
            g.fillOval(x - r, y - r, r * 2, r * 2);
            g.setColor(Color.WHITE);
            if (m.primary) { g.setStroke(new BasicStroke(2f)); g.drawOval(x - r + 3, y - r + 3, r * 2 - 6, r * 2 - 6); g.fillOval(x - 2, y - 2, 4, 4); g.setStroke(new BasicStroke(1f)); }
            hot.add(new double[]{x, y, r + 5, m.x, m.z});
            hotName.add(m.label);
            if (showLabels) label(g, x, y - r - 6, m, w);
        }
    }

    private final List<Rectangle> placedLabels = new ArrayList<>();

    private void label(Graphics2D g, int x, int y, Marker m, int w) {
        g.setFont(Theme.SMALL_B);
        FontMetrics fm = g.getFontMetrics();
        String a = m.label, b = m.sub == null ? "" : m.sub;
        int tw = Math.max(fm.stringWidth(a), fm.stringWidth(b)) + 16, th = b.isEmpty() ? 22 : 36;
        int lx = Math.max(4, Math.min(w - tw - 4, x - tw / 2)), ly = y - th;
        // keep labels from sitting on top of each other
        for (int tries = 0; tries < 8; tries++) {
            Rectangle r = new Rectangle(lx, ly, tw, th);
            boolean hit = false;
            for (Rectangle o : placedLabels) if (o.intersects(r)) { hit = true; ly = o.y - th - 4; break; }
            if (!hit) break;
        }
        placedLabels.add(new Rectangle(lx, ly, tw, th));
        g.setColor(new Color(10, 12, 16, 215));
        g.fill(new RoundRectangle2D.Float(lx, ly, tw, th, 9, 9));
        g.setColor(new Color(m.colour.getRed(), m.colour.getGreen(), m.colour.getBlue(), 220));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Float(lx + .5f, ly + .5f, tw - 1, th - 1, 9, 9));
        g.setStroke(new BasicStroke(1f));
        g.setColor(Theme.TEXT);
        g.drawString(a, lx + 8, ly + 15);
        if (!b.isEmpty()) { g.setFont(Theme.SMALL); g.setColor(Theme.MUTED); g.drawString(b, lx + 8, ly + 29); }
    }

    private void drawHud(Graphics2D g, Caster.Cam cam, int w, int h) {
        g.setFont(Theme.SMALL_B);
        if (!hoverText.isEmpty() && hx >= 0) {
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(hoverText) + 18;
            g.setColor(new Color(10, 12, 16, 200));
            g.fill(new RoundRectangle2D.Float(12, 12, tw, 26, 10, 10));
            g.setColor(Theme.TEXT);
            g.drawString(hoverText, 21, 29);
        }
        if (showCompass) {
            int cx = w - 44, cy = 44;
            g.setColor(new Color(10, 12, 16, 190));
            g.fillOval(cx - 26, cy - 26, 52, 52);
            g.setColor(Theme.LINE);
            g.drawOval(cx - 26, cy - 26, 52, 52);
            double ang = mode == Mode.MAP ? 0 : (cam == null ? 0 : cam.yaw);
            // north is -z; the needle points to where north is on screen
            double nx = Math.sin(ang + Math.PI) * -1, ny = Math.cos(ang + Math.PI) * -1;
            double ax = cx + Math.sin(Math.PI - ang) * 17 * -1, ay = cy + Math.cos(Math.PI - ang) * 17 * 1;
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double sx = Math.sin(ang), sy = Math.cos(ang);
            // screen-space direction of north given the camera yaw: forward is (sin,cos); north (0,-1) projected on right/forward
            double rightX = -Math.cos(ang), rightZ = Math.sin(ang), fwdX = Math.sin(ang), fwdZ = Math.cos(ang);
            double nRight = 0 * rightX + (-1) * rightZ, nFwd = 0 * fwdX + (-1) * fwdZ;
            double px = cx + nRight * 18, py = cy - nFwd * 18;
            g.setColor(new Color(0xF87171));
            g.drawLine(cx, cy, (int) px, (int) py);
            g.setColor(Theme.MUTED);
            g.drawLine(cx, cy, (int) (cx - (px - cx)), (int) (cy - (py - cy)));
            g.setStroke(new BasicStroke(1f));
            g.setFont(Theme.SMALL_B);
            g.setColor(Theme.TEXT);
            g.drawString("N", (int) (cx + nRight * 31) - 4, (int) (cy - nFwd * 31) + 4);
        }
        if (mode == Mode.WALK) {
            g.setColor(new Color(255, 255, 255, 200));
            g.setStroke(new BasicStroke(1.6f));
            g.drawLine(w / 2 - 8, h / 2, w / 2 + 8, h / 2);
            g.drawLine(w / 2, h / 2 - 8, w / 2, h / 2 + 8);
            g.setStroke(new BasicStroke(1f));
        }
        if (mode == Mode.MAP) {
            // scale bar
            double[] nice = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000};
            double target = 130 * vBpp;
            double len = nice[0];
            for (double n : nice) if (n <= target) len = n;
            int px = (int) (len / vBpp);
            int bx = 18, by = h - 26;
            g.setColor(new Color(10, 12, 16, 200));
            g.fill(new RoundRectangle2D.Float(bx - 8, by - 20, px + 70, 32, 9, 9));
            g.setColor(Theme.TEXT);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(bx, by, bx + px, by);
            g.drawLine(bx, by - 5, bx, by + 5);
            g.drawLine(bx + px, by - 5, bx + px, by + 5);
            g.setStroke(new BasicStroke(1f));
            g.setFont(Theme.SMALL_B);
            g.drawString(String.format("%,.0f blocks", len), bx + px + 8, by + 4);
        }
        if (busy && doneStep > 1 && !simple) {
            g.setColor(new Color(10, 12, 16, 190));
            g.fill(new RoundRectangle2D.Float(w - 120, h - 36, 108, 24, 9, 9));
            g.setColor(Theme.MUTED);
            g.setFont(Theme.SMALL);
            g.drawString("sharpening…", w - 104, h - 20);
        }
    }
}
