package slatefinder;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * A tiny pixel canvas used to draw the biome and structure previews.
 *
 * The pictures are painted, not screenshots: there are no game assets in the app, so each one is a
 * small block-style scene (160x90 pixels, blown up with nearest-neighbour so the pixels stay crisp).
 */
final class Art {
    static final int W = 160, H = 90;
    final int[] p = new int[W * H];
    final Random rnd;
    final long seed;

    Art(long seed) {
        this.seed = seed;
        this.rnd = new Random(seed);
    }

    // ---------------------------------------------------------------- colour helpers

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    static int r(int c) { return (c >> 16) & 255; }
    static int g(int c) { return (c >> 8) & 255; }
    static int b(int c) { return c & 255; }

    static int shade(int c, double f) {
        return rgb((int) (r(c) * f), (int) (g(c) * f), (int) (b(c) * f));
    }

    static int mix(int a, int c, double t) {
        return rgb((int) (r(a) + (r(c) - r(a)) * t), (int) (g(a) + (g(c) - g(a)) * t), (int) (b(a) + (b(c) - b(a)) * t));
    }

    /** Stable per-pixel noise in [0,1), so textures do not shimmer between draws. */
    static double hash(int x, int y, long s) {
        long h = x * 374761393L + y * 668265263L + s * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    /** Smooth value noise in [0,1), so streaks and patches look like rock rather than static. */
    static double vnoise(double x, double y, long s) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        double a = hash(xi, yi, s), b = hash(xi + 1, yi, s), c = hash(xi, yi + 1, s), d = hash(xi + 1, yi + 1, s);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    /** Water over a column of ground: open water above the ground line, tinted ground below it. */
    void submerge(int[] h, int level, int top, int bottom, double groundAlpha, int x0, int x1) {
        for (int x = Math.max(0, x0); x < Math.min(W, x1); x++) {
            if (h[x] < level - 1) continue;
            for (int y = level; y < H; y++) {
                double t = Math.min(1, (y - level) / 26.0);
                int wc = mix(top, bottom, t);
                if (y < h[x]) px(x, y, wc);
                else px(x, y, mix(get(x, y), wc, groundAlpha));
            }
            if (hash(x, level, seed) > 0.5) px(x, level, shade(top, 1.3));
        }
    }

    // ---------------------------------------------------------------- primitives

    void px(int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < W && y < H) p[y * W + x] = c;
    }

    int get(int x, int y) {
        return (x >= 0 && y >= 0 && x < W && y < H) ? p[y * W + x] : 0;
    }

    void fill(int c) { java.util.Arrays.fill(p, c); }

    void rect(int x, int y, int w, int h, int c) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) px(i, j, c);
    }

    /** A rectangle with block-texture noise, like a face of a block seen from a distance. */
    void tex(int x, int y, int w, int h, int c, double amt) {
        for (int j = y; j < y + h; j++)
            for (int i = x; i < x + w; i++)
                px(i, j, shade(c, 1.0 + (hash(i, j, seed) - 0.5) * amt));
    }

    void gradient(int y0, int y1, int top, int bottom) {
        for (int j = Math.max(0, y0); j < Math.min(H, y1); j++) {
            double t = (j - y0) / (double) Math.max(1, y1 - y0 - 1);
            int c = mix(top, bottom, t);
            for (int i = 0; i < W; i++) p[j * W + i] = c;
        }
    }

    void disc(int cx, int cy, int rad, int c) {
        for (int j = -rad; j <= rad; j++)
            for (int i = -rad; i <= rad; i++)
                if (i * i + j * j <= rad * rad + rad / 2) px(cx + i, cy + j, c);
    }

    void ellipse(int cx, int cy, int rx, int ry, int c) {
        for (int j = -ry; j <= ry; j++)
            for (int i = -rx; i <= rx; i++)
                if ((double) i * i / (rx * rx + 0.5) + (double) j * j / (ry * ry + 0.5) <= 1.0) px(cx + i, cy + j, c);
    }

    void line(int x0, int y0, int x1, int y1, int c) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx - dy;
        while (true) {
            px(x0, y0, c);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    /** Fills a triangle-ish roof: a stepped pyramid of rows, each one narrower. */
    void stepped(int cx, int baseY, int halfWidth, int c, double amt) {
        int rows = halfWidth;
        for (int k = 0; k < rows; k++) tex(cx - (halfWidth - k), baseY - k, (halfWidth - k) * 2 + 1, 1, c, amt);
    }

    // ---------------------------------------------------------------- sky, light

    void sky(int top, int bottom, int horizon) {
        gradient(0, horizon, top, bottom);
    }

    void sun(int x, int y, int c) {
        disc(x, y, 6, mix(c, 0xFFFFFFFF, 0.0));
        for (int j = -9; j <= 9; j++) for (int i = -9; i <= 9; i++) {
            double d = Math.sqrt(i * i + j * j);
            if (d > 6 && d < 10) {
                int bg = get(x + i, y + j);
                px(x + i, y + j, mix(bg, c, (10 - d) / 10.0 * 0.45));
            }
        }
    }

    void moon(int x, int y) {
        disc(x, y, 5, 0xFFE8ECF5);
        disc(x + 2, y - 1, 1, 0xFFC9D0E0);
        disc(x - 2, y + 2, 1, 0xFFC9D0E0);
    }

    void stars(int n, int maxY) {
        for (int i = 0; i < n; i++) {
            int x = rnd.nextInt(W), y = rnd.nextInt(maxY);
            px(x, y, rnd.nextInt(3) == 0 ? 0xFFFFFFFF : 0xFFB8C2DD);
        }
    }

    void cloud(int x, int y, int len, int c) {
        int sh = shade(c, 0.88);
        for (int i = 0; i < len; i++) { px(x + i, y, c); px(x + i, y + 1, c); }
        for (int i = 2; i < len - 2; i++) px(x + i, y - 1, c);
        for (int i = 1; i < len - 1; i++) px(x + i, y + 2, sh);
    }

    void clouds(int count, int yMin, int yMax, int c) {
        for (int i = 0; i < count; i++) cloud(rnd.nextInt(W + 20) - 10, yMin + rnd.nextInt(Math.max(1, yMax - yMin)), 14 + rnd.nextInt(16), c);
    }

    void fog(int y0, int y1, int c, double strength) {
        for (int j = Math.max(0, y0); j < Math.min(H, y1); j++) {
            double t = (j - y0) / (double) Math.max(1, y1 - y0);
            double s = strength * (1 - Math.abs(t - 0.5) * 2 * 0.0 - t * 0.0);
            for (int i = 0; i < W; i++) p[j * W + i] = mix(p[j * W + i], c, s * (0.35 + 0.65 * t));
        }
    }

    void vignette(double amount) {
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) {
            double dx = (i - W / 2.0) / (W / 2.0), dy = (j - H / 2.0) / (H / 2.0);
            double v = 1 - amount * Math.max(0, dx * dx + dy * dy - 0.35);
            p[j * W + i] = shade(p[j * W + i], Math.max(0.5, v));
        }
    }

    // ---------------------------------------------------------------- terrain

    /** A smooth-ish random height line: base +/- amp, with the given feature size. */
    int[] heights(double base, double amp, double feature, long salt) {
        int[] h = new int[W];
        Random r2 = new Random(seed * 31 + salt);
        double o1 = r2.nextDouble() * 100, o2 = r2.nextDouble() * 100, o3 = r2.nextDouble() * 100;
        for (int x = 0; x < W; x++) {
            double v = Math.sin((x + o1) / feature) * 0.55 + Math.sin((x + o2) / (feature * 0.43)) * 0.3 + Math.sin((x + o3) / (feature * 0.19)) * 0.15;
            h[x] = (int) Math.round(base + v * amp);
        }
        return h;
    }

    /** Sharp triangular peaks. */
    int[] peaks(double base, double amp, int count, long salt) {
        int[] h = new int[W];
        Random r2 = new Random(seed * 17 + salt);
        for (int x = 0; x < W; x++) h[x] = (int) base;
        for (int k = 0; k < count; k++) {
            int cx = (int) (W * (k + 0.2 + r2.nextDouble() * 0.6) / count);
            double ht = amp * (0.55 + r2.nextDouble() * 0.45);
            double width = 20 + r2.nextDouble() * 18;
            for (int x = 0; x < W; x++) {
                double d = Math.abs(x - cx) / width;
                if (d < 1) {
                    double v = (1 - d) * ht + (hash(x, k, seed) - 0.5) * 3.2 * (1 - d);
                    h[x] = Math.min(h[x], (int) (base - v));
                }
            }
        }
        return h;
    }

    /** Fills everything below the height line with a block-textured column. */
    void land(int[] h, int top, int sub, int deep, int subDepth, double amt) {
        for (int x = 0; x < W; x++) {
            for (int y = Math.max(0, h[x]); y < H; y++) {
                int c;
                if (y == h[x]) c = top;
                else if (y < h[x] + subDepth) c = sub;
                else c = deep;
                px(x, y, shade(c, 1.0 + (hash(x, y, seed) - 0.5) * amt));
            }
        }
        // a darker rim on the top edge so the grass reads as a surface
        for (int x = 0; x < W; x++) if (h[x] >= 0 && h[x] < H) px(x, h[x], shade(top, 1.08));
    }

    /** A silhouette of far hills, drawn first so the near ground sits on top. */
    void farHills(int[] h, int c) {
        for (int x = 0; x < W; x++) for (int y = Math.max(0, h[x]); y < H; y++) px(x, y, shade(c, 1.0 + (hash(x, y, seed + 9) - 0.5) * 0.05));
    }

    void water(int level, int top, int bottom, double alpha) {
        for (int y = Math.max(0, level); y < H; y++) {
            double t = (y - level) / (double) Math.max(1, H - level);
            int wc = mix(top, bottom, Math.min(1, t * 1.3));
            for (int x = 0; x < W; x++) {
                double a = alpha;
                p[y * W + x] = mix(p[y * W + x], wc, a);
            }
        }
        for (int x = 0; x < W; x++) if (hash(x, level, seed) > 0.55) px(x, level, shade(top, 1.25));
    }

    // ---------------------------------------------------------------- plants and props

    static final int OAK_LOG = 0xFF6B5230, BIRCH_LOG = 0xFFD7D3C4, SPRUCE_LOG = 0xFF4A3622, DARK_LOG = 0xFF3B2A18,
            ACACIA_LOG = 0xFF8B7F73, JUNGLE_LOG = 0xFF5A4A28, CHERRY_LOG = 0xFF5A3A46, MANGROVE_LOG = 0xFF6A3B34;

    void log(int x, int y, int h, int c) {
        for (int j = 0; j < h; j++) { px(x, y - j, shade(c, 1.0 + (hash(x, y - j, seed) - 0.5) * 0.18)); }
    }

    void leafBlob(int cx, int cy, int rx, int ry, int c, double amt) {
        for (int j = -ry; j <= ry; j++) for (int i = -rx; i <= rx; i++) {
            if ((double) i * i / (rx * rx + 0.4) + (double) j * j / (ry * ry + 0.4) <= 1.0)
                px(cx + i, cy + j, shade(c, 1.0 + (hash(cx + i, cy + j, seed + 3) - 0.5) * amt));
        }
    }

    void oak(int x, int gy, int h, int leaf) {
        for (int j = 0; j < h; j++) { px(x, gy - j, OAK_LOG); px(x + 1, gy - j, shade(OAK_LOG, 0.85)); }
        leafBlob(x, gy - h - 1, 6, 5, leaf, 0.3);
    }

    void birch(int x, int gy, int h, int leaf) {
        for (int j = 0; j < h; j++) {
            px(x, gy - j, hash(x, j, seed) > 0.8 ? 0xFF3A382F : BIRCH_LOG);
            px(x + 1, gy - j, shade(BIRCH_LOG, 0.9));
        }
        leafBlob(x, gy - h, 4, 4, leaf, 0.3);
        leafBlob(x + 1, gy - h - 4, 3, 2, shade(leaf, 1.1), 0.3);
    }

    void spruce(int x, int gy, int h, int leaf, boolean snow) {
        for (int j = 0; j < 3; j++) px(x, gy - j, SPRUCE_LOG);
        int top = gy - h;
        for (int k = 0; k <= h - 2; k++) {
            int y = gy - 3 - k;
            int half = (int) Math.round(1 + (h - 3 - k) * 0.0 + Math.min(5, (k < h / 2 ? (h - 3 - k) * 0.42 : (h - 3 - k) * 0.42)));
            if (y < top) break;
            half = Math.max(0, Math.min(5, (int) ((y - top) * 0.45)));
            for (int i = -half; i <= half; i++) {
                int c = shade(leaf, 1.0 + (hash(x + i, y, seed) - 0.5) * 0.3);
                if (snow && (y % 3 == 0 || hash(x + i, y, seed + 5) > 0.7)) c = mix(c, 0xFFF4F8FF, 0.8);
                px(x + i, y, c);
            }
        }
    }

    void darkOak(int x, int gy, int h, int leaf) {
        for (int j = 0; j < h; j++) { px(x, gy - j, DARK_LOG); px(x + 1, gy - j, DARK_LOG); }
        leafBlob(x, gy - h - 1, 8, 5, leaf, 0.3);
    }

    void acacia(int x, int gy, int h, int leaf) {
        int cx = x;
        for (int j = 0; j < h; j++) {
            px(cx, gy - j, ACACIA_LOG);
            if (j == h / 2) cx++;
        }
        for (int j = 0; j < 3; j++) { px(cx + j, gy - h + 1, ACACIA_LOG); }
        leafBlob(cx + 1, gy - h - 1, 7, 2, leaf, 0.3);
        leafBlob(cx - 4, gy - h, 3, 1, leaf, 0.3);
    }

    void jungleTree(int x, int gy, int h, int leaf) {
        for (int j = 0; j < h; j++) { px(x, gy - j, JUNGLE_LOG); px(x + 1, gy - j, shade(JUNGLE_LOG, 0.8)); }
        leafBlob(x, gy - h, 9, 5, leaf, 0.35);
        leafBlob(x - 5, gy - h + 3, 4, 3, shade(leaf, 0.9), 0.35);
        // vines
        for (int k = -6; k <= 7; k += 3) for (int j = 0; j < 4 + (k & 3); j++) px(x + k, gy - h + 3 + j, shade(leaf, 0.75));
    }

    void cherry(int x, int gy, int h) {
        for (int j = 0; j < h; j++) px(x, gy - j, CHERRY_LOG);
        line(x, gy - h + 2, x - 4, gy - h - 1, CHERRY_LOG);
        line(x, gy - h + 3, x + 4, gy - h, CHERRY_LOG);
        leafBlob(x, gy - h - 2, 8, 5, 0xFFF5B2D4, 0.28);
        leafBlob(x - 4, gy - h - 1, 4, 3, 0xFFFFC6E0, 0.25);
        // petals on the ground
        for (int i = -7; i <= 7; i++) if (hash(x + i, gy, seed) > 0.6) px(x + i, gy, 0xFFFFC6E0);
    }

    void mangrove(int x, int gy, int h) {
        for (int j = 0; j < h; j++) px(x, gy - j, MANGROVE_LOG);
        for (int k = 1; k <= 4; k++) { px(x - k, gy - 4 + k / 2, MANGROVE_LOG); px(x + k, gy - 4 + k / 2, MANGROVE_LOG); px(x - k, gy + k / 3, MANGROVE_LOG); px(x + k, gy + k / 3, MANGROVE_LOG); }
        leafBlob(x, gy - h - 1, 7, 4, 0xFF4E8A3C, 0.3);
    }

    void cactus(int x, int gy, int h) {
        int c = 0xFF3E8A3A;
        for (int j = 0; j < h; j++) px(x, gy - j, shade(c, 1 + (hash(x, j, seed) - 0.5) * 0.2));
        if (h > 4) {
            int arm = gy - h / 2;
            px(x - 1, arm, c); px(x - 2, arm, c); px(x - 2, arm - 1, c); px(x - 2, arm - 2, c);
            px(x + 1, arm - 1, c); px(x + 2, arm - 1, c); px(x + 2, arm - 2, c);
        }
    }

    void bamboo(int x, int gy, int h) {
        int c = 0xFF7DB93A;
        for (int j = 0; j < h; j++) px(x, gy - j, j % 4 == 3 ? shade(c, 0.7) : c);
        px(x + 1, gy - h, 0xFF5E9B2E); px(x - 1, gy - h + 1, 0xFF5E9B2E);
    }

    void flower(int x, int gy, int c) {
        px(x, gy - 1, 0xFF3F8F2E);
        px(x, gy - 2, c);
    }

    void grassTuft(int x, int gy, int c) {
        px(x, gy - 1, c); px(x + 1, gy - 2, shade(c, 1.15)); px(x - 1, gy - 2, shade(c, 0.9));
    }

    void bigMushroom(int x, int gy, int h, int cap, int stem) {
        for (int j = 0; j < h; j++) { px(x, gy - j, stem); px(x + 1, gy - j, shade(stem, 0.9)); }
        ellipse(x, gy - h, 9, 4, cap);
        for (int i = -6; i <= 6; i += 4) px(x + i, gy - h - 2, 0xFFF4EEE4);
    }

    void iceSpike(int x, int gy, int h, int c) {
        for (int j = 0; j < h; j++) {
            int w = Math.max(0, (int) ((h - j) * 0.22));
            for (int i = -w; i <= w; i++) px(x + i, gy - j, shade(c, 1.0 + (hash(x + i, j, seed) - 0.5) * 0.2));
        }
    }

    void rockPile(int x, int gy, int c) {
        for (int j = 0; j < 3; j++) for (int i = -2 + j; i <= 2 - j; i++) px(x + i, gy - j, shade(c, 1 + (hash(x + i, j, seed) - 0.5) * 0.3));
    }

    // ---------------------------------------------------------------- output

    BufferedImage image() {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        im.setRGB(0, 0, W, H, p, 0, W);
        return im;
    }
}
