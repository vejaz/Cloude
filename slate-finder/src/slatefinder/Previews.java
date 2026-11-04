package slatefinder;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Painted pictures of every biome and structure in the book.
 *
 * There are no game screenshots in the app, so these are illustrations drawn in code in a block style:
 * a landscape (or cutaway, for caves, oceans and buildings) per entry, each from its own palette.
 */
public final class Previews {
    private Previews() {}

    private static final Map<String, BufferedImage> CACHE = new HashMap<>();

    public static synchronized BufferedImage get(Lexicon.Item it) {
        BufferedImage im = CACHE.get(it.id);
        if (im == null) {
            im = render(it);
            CACHE.put(it.id, im);
        }
        return im;
    }

    static BufferedImage render(Lexicon.Item it) {
        Art a = new Art(it.id.hashCode() * 7919L + 17);
        try {
            if (it.structure()) structure(a, it.id); else { biome(a, it.id); Heroes.add(a, it.id); }
            a.vignette(0.22);
            Heroes.emblem(a, it);
        } catch (RuntimeException e) {
            a.fill(Art.rgb(40, 40, 50));
        }
        return a.image();
    }

    // ------------------------------------------------------------------ shared pieces

    interface Decor { void draw(Art a, int[] h); }

    private static final int GRASS = 0xFF79B03A, DIRT = 0xFF866043, STONE = 0xFF7D7D7D, SNOW = 0xFFF2F6FA, SAND = 0xFFE9D69A, SANDSTONE = 0xFFD9C27F;

    private static void day(Art a, int top, int bottom, int horizon, boolean clouds) {
        a.sky(top, bottom, horizon + 2);
        a.sun(124, 16, 0xFFFFF2B0);
        if (clouds) a.clouds(4, 6, 30, 0xFFFFFFFF);
    }

    private static void scatter(Art a, int[] h, int count, int minGap, java.util.function.IntConsumer f) {
        int[] xs = new int[count];
        int n = 0;
        for (int tries = 0; tries < count * 12 && n < count; tries++) {
            int x = 4 + a.rnd.nextInt(Art.W - 8);
            boolean ok = true;
            for (int k = 0; k < n; k++) if (Math.abs(xs[k] - x) < minGap) ok = false;
            if (ok && h[x] > 4 && h[x] < Art.H - 2) { xs[n++] = x; }
        }
        java.util.Arrays.sort(xs, 0, n);
        for (int k = 0; k < n; k++) f.accept(xs[k]);
    }

    private static void ground(Art a, int[] far, int farColour, int[] h, int top, int sub, int deep) {
        if (far != null) a.farHills(far, farColour);
        a.land(h, top, sub, deep, 6, 0.16);
    }

    private static void tufts(Art a, int[] h, int count, int c) {
        for (int i = 0; i < count; i++) { int x = a.rnd.nextInt(Art.W); a.grassTuft(x, h[x], Art.shade(c, 0.85 + a.rnd.nextDouble() * 0.3)); }
    }

    private static void flowers(Art a, int[] h, int count, int... colours) {
        for (int i = 0; i < count; i++) { int x = a.rnd.nextInt(Art.W); a.flower(x, h[x], colours[a.rnd.nextInt(colours.length)]); }
    }

    private static void sunflower(Art a, int x, int gy) {
        for (int j = 1; j <= 7; j++) a.px(x, gy - j, 0xFF3F8F2E);
        a.rect(x - 1, gy - 10, 3, 3, 0xFFF4C20D);
        a.px(x, gy - 9, 0xFF6B4423);
    }

    private static void snowCap(Art a, int[] h, int threshold, int c) {
        for (int x = 0; x < Art.W; x++) if (h[x] < threshold) {
            int depth = 1 + (threshold - h[x]) / 6;
            for (int j = 0; j < depth; j++) a.px(x, h[x] + j, Art.shade(c, 1.0 + (Art.hash(x, j, a.seed) - 0.5) * 0.1));
        }
    }

    private static void ice(Art a, int x, int y, int w, int c) {
        a.tex(x, y, w, 2, c, 0.12);
        a.rect(x, y, w, 1, Art.shade(c, 1.12));
    }

    // ------------------------------------------------------------------ biomes

    static void biome(Art a, String id) {
        int hz = 58;
        int[] h;
        int[] far;
        switch (id) {
            case "plains": case "sunflower_plains": {
                day(a, 0xFF6FAEEF, 0xFFCDE6FF, hz, true);
                far = a.heights(52, 5, 30, 1);
                h = a.heights(66, 3, 40, 2);
                ground(a, far, 0xFF9FCB86, h, GRASS, DIRT, STONE);
                tufts(a, h, 40, 0xFF5E9B2E);
                if (id.equals("plains")) { flowers(a, h, 14, 0xFFD03030, 0xFFF0D020, 0xFFFFFFFF, 0xFFE070C0); scatter(a, h, 2, 40, x -> a.oak(x, h[x], 11 + a.rnd.nextInt(3), 0xFF4F9A32)); }
                else { scatter(a, h, 12, 8, x -> sunflower(a, x, h[x])); flowers(a, h, 6, 0xFFF0D020); }
                break;
            }
            case "meadow": {
                day(a, 0xFF6FB3F2, 0xFFD8EEFF, hz, true);
                far = a.peaks(58, 38, 4, 5);
                a.farHills(far, 0xFFA9BBCF);
                snowCap(a, far, 32, 0xFFFFFFFF);
                h = a.heights(68, 6, 34, 3);
                ground(a, null, 0, h, 0xFF8FCB4E, DIRT, STONE);
                flowers(a, h, 55, 0xFFFFFFFF, 0xFFF0D020, 0xFFB070E0, 0xFF60A0F0, 0xFFE05080);
                tufts(a, h, 25, 0xFF7ABF3A);
                scatter(a, h, 1, 50, x -> a.oak(x, h[x], 9, 0xFF5AA83A));
                break;
            }
            case "snowy_plains": case "ice_spikes": {
                a.sky(0xFFAFC2D6, 0xFFE4ECF4, hz + 2);
                a.clouds(5, 6, 30, 0xFFF4F7FA);
                far = a.heights(54, 4, 26, 1);
                h = a.heights(68, 2, 40, 2);
                ground(a, far, 0xFFD8E3EE, h, SNOW, 0xFFDCE6EE, DIRT);
                if (id.equals("snowy_plains")) {
                    scatter(a, h, 3, 40, x -> a.spruce(x, h[x], 22 + a.rnd.nextInt(5), 0xFF2F5A3A, true));
                    ice(a, 60, h[70] + 1, 28, 0xFFA9CFF0);
                } else {
                    scatter(a, h, 9, 12, x -> a.iceSpike(x, h[x] + 1, 18 + a.rnd.nextInt(30), 0xFFAFD6F2));
                    for (int x = 0; x < Art.W; x += 3) a.px(x, h[x] - 1, 0xFFFFFFFF);
                }
                break;
            }
            case "desert": {
                day(a, 0xFF74B4EA, 0xFFF4E9C6, hz, false);
                a.sun(124, 18, 0xFFFFE9A0);
                far = a.heights(54, 4, 22, 1);
                h = a.heights(68, 6, 26, 2);
                ground(a, far, 0xFFEAD7A0, h, SAND, SANDSTONE, SANDSTONE);
                for (int i = 0; i < 3; i++) { int x = 14 + a.rnd.nextInt(130); a.cactus(x, h[x], 6 + a.rnd.nextInt(4)); }
                for (int i = 0; i < 5; i++) { int x = a.rnd.nextInt(Art.W); a.px(x, h[x] - 1, 0xFF7A5A2A); a.px(x + 1, h[x] - 2, 0xFF7A5A2A); }
                break;
            }
            case "swamp": case "mangrove_swamp": {
                boolean mg = id.startsWith("mangrove");
                a.sky(mg ? 0xFF8FB8A0 : 0xFF7A9A8A, mg ? 0xFFC8DCC8 : 0xFFA6BCA8, hz + 2);
                a.clouds(3, 8, 24, 0xFFB8C4B8);
                far = a.heights(54, 4, 26, 1);
                h = a.heights(70, 2, 34, 2);
                ground(a, far, mg ? 0xFF7FA08A : 0xFF6F8F78, h, mg ? 0xFF5A4A3A : 0xFF5B7F3A, mg ? 0xFF4A3A2E : DIRT, STONE);
                a.water(71, mg ? 0xFF3F6A5A : 0xFF4A6A5A, 0xFF2A4A40, 0.88);
                for (int i = 0; i < 14; i++) { int x = a.rnd.nextInt(Art.W); a.px(x, 71, 0xFF3E8A2E); a.px(x + 1, 71, 0xFF3E8A2E); }
                if (mg) scatter(a, h, 5, 20, x -> a.mangrove(x, 70, 13 + a.rnd.nextInt(5)));
                else scatter(a, h, 3, 40, x -> { a.oak(x, Math.min(h[x], 72), 10, 0xFF5A7A3A); for (int k = -5; k <= 5; k += 3) for (int j = 0; j < 5; j++) a.px(x + k, Math.min(h[x], 72) - 6 + j, 0xFF3F6A2A); });
                a.fog(60, 82, 0xFF9DB4A4, 0.22);
                break;
            }
            case "forest": case "flower_forest": case "birch_forest": case "old_growth_birch_forest": {
                day(a, 0xFF72B0EA, 0xFFCFE6FA, hz, true);
                far = a.heights(50, 5, 28, 1);
                h = a.heights(70, 3, 36, 2);
                ground(a, far, 0xFF6FA070, h, GRASS, DIRT, STONE);
                boolean birchy = id.contains("birch");
                int n = id.equals("old_growth_birch_forest") ? 8 : 7;
                scatter(a, h, n, 15, x -> {
                    if (id.equals("old_growth_birch_forest")) a.birch(x, h[x], 26 + a.rnd.nextInt(8), 0xFF6FA83A);
                    else if (birchy || a.rnd.nextInt(5) == 0) a.birch(x, h[x], 13 + a.rnd.nextInt(3), 0xFF6FA83A);
                    else a.oak(x, h[x], 12 + a.rnd.nextInt(4), 0xFF3F8A2E);
                });
                tufts(a, h, 25, 0xFF5A9A30);
                if (id.equals("flower_forest")) flowers(a, h, 60, 0xFFF04080, 0xFFF0E040, 0xFFFFFFFF, 0xFF8060E0, 0xFFF08030, 0xFF50A0F0);
                break;
            }
            case "dark_forest": {
                a.sky(0xFF4A5A6A, 0xFF8A9AA6, hz + 2);
                far = a.heights(48, 6, 24, 1);
                h = a.heights(72, 3, 36, 2);
                ground(a, far, 0xFF3F5A44, h, 0xFF3F6A2A, DIRT, STONE);
                scatter(a, h, 6, 20, x -> a.darkOak(x, h[x], 16 + a.rnd.nextInt(6), 0xFF1F4A1A));
                scatter(a, h, 3, 30, x -> { a.px(x, h[x] - 1, 0xFF8A3030); a.px(x + 1, h[x] - 1, 0xFF8A3030); a.px(x, h[x] - 2, 0xFFB04040); });
                a.fog(50, 90, 0xFF2A3A30, 0.25);
                break;
            }
            case "pale_garden": {
                a.sky(0xFF9AA8A8, 0xFFD2DCDA, hz + 2);
                far = a.heights(50, 4, 28, 1);
                h = a.heights(72, 2, 36, 2);
                ground(a, far, 0xFFAEBAB4, h, 0xFF8A9A86, 0xFF7A6A5A, STONE);
                scatter(a, h, 6, 20, x -> {
                    for (int j = 0; j < 20; j++) { a.px(x, h[x] - j, 0xFFE6E0D8); a.px(x + 1, h[x] - j, 0xFFCFC8BE); }
                    a.leafBlob(x, h[x] - 22, 9, 5, 0xFF9AA89E, 0.25);
                    for (int k = -7; k <= 7; k += 3) for (int j = 0; j < 4 + (k & 3); j++) a.px(x + k, h[x] - 18 + j, 0xFF7C8C80);
                });
                for (int i = 0; i < 30; i++) { int x = a.rnd.nextInt(Art.W); a.px(x, h[x] - 1, 0xFFA8B8A4); }
                a.fog(50, 90, 0xFFD0D8D4, 0.25);
                break;
            }
            case "taiga": case "snowy_taiga": case "old_growth_pine_taiga": case "old_growth_spruce_taiga": case "grove": {
                boolean snowy = id.equals("snowy_taiga") || id.equals("grove");
                a.sky(snowy ? 0xFFA8BCD0 : 0xFF86B0D8, snowy ? 0xFFE2EAF2 : 0xFFD2E4F0, hz + 2);
                a.clouds(4, 6, 28, 0xFFF4F8FA);
                if (id.equals("grove")) { far = a.peaks(56, 40, 3, 8); a.farHills(far, 0xFFAAB8CC); snowCap(a, far, 34, 0xFFFFFFFF); }
                else far = a.heights(50, 5, 28, 1);
                h = a.heights(id.equals("grove") ? 66 : 70, id.equals("grove") ? 7 : 3, 34, 2);
                int top = snowy ? SNOW : id.contains("old_growth") ? 0xFF6B5A3A : 0xFF4F8A3A;
                ground(a, id.equals("grove") ? null : far, snowy ? 0xFFC8D6E0 : 0xFF557A60, h, top, snowy ? 0xFFDCE6EE : DIRT, STONE);
                boolean big = id.startsWith("old_growth");
                scatter(a, h, big ? 5 : 8, big ? 24 : 15, x -> {
                    if (big) {
                        for (int j = 0; j < 30; j++) { a.px(x, h[x] - j, Art.SPRUCE_LOG); a.px(x + 1, h[x] - j, Art.SPRUCE_LOG); a.px(x - 1, h[x] - j, Art.shade(Art.SPRUCE_LOG, 0.8)); }
                        a.spruce(x, h[x] - 4, 38, id.endsWith("pine_taiga") ? 0xFF3A6B3A : 0xFF2F5A3A, false);
                    } else a.spruce(x, h[x], 20 + a.rnd.nextInt(8), snowy ? 0xFF2F5A3A : 0xFF3A6B3E, snowy);
                });
                if (!snowy) for (int i = 0; i < 20; i++) { int x = a.rnd.nextInt(Art.W); a.px(x, h[x] - 1, 0xFF4A7A30); a.px(x, h[x] - 2, 0xFF4A7A30); }
                if (id.equals("old_growth_pine_taiga")) for (int i = 0; i < 14; i++) { int x = a.rnd.nextInt(Art.W); a.rockPile(x, h[x], 0xFF6A7A62); }
                break;
            }
            case "jungle": case "sparse_jungle": case "bamboo_jungle": {
                a.sky(0xFF78C0E8, 0xFFD6F0D8, hz + 2);
                a.clouds(3, 6, 20, 0xFFEAF6EC);
                far = a.heights(48, 6, 24, 1);
                h = a.heights(72, 3, 30, 2);
                ground(a, far, 0xFF3F8A4A, h, 0xFF4FA030, DIRT, STONE);
                if (id.equals("bamboo_jungle")) {
                    scatter(a, h, 18, 6, x -> a.bamboo(x, h[x], 18 + a.rnd.nextInt(24)));
                    scatter(a, h, 2, 50, x -> a.jungleTree(x, h[x], 26, 0xFF2F7A22));
                } else scatter(a, h, id.equals("jungle") ? 6 : 3, 16, x -> a.jungleTree(x, h[x], 22 + a.rnd.nextInt(14), 0xFF2F8A22));
                for (int i = 0; i < 30; i++) { int x = a.rnd.nextInt(Art.W); a.leafBlob(x, h[x] - 1, 2, 1, 0xFF2F8A22, 0.3); }
                a.fog(55, 90, 0xFFBFE4C8, 0.12);
                break;
            }
            case "savanna": case "savanna_plateau": case "windswept_savanna": {
                a.sky(0xFF7EB4E0, 0xFFF4DCAA, hz + 2);
                a.sun(124, 20, 0xFFFFE0A0);
                a.clouds(3, 8, 24, 0xFFFFF4E0);
                far = a.heights(52, 4, 26, 1);
                if (id.equals("savanna_plateau")) { h = a.heights(64, 2, 40, 2); for (int x = 0; x < Art.W; x++) if (x > 28 && x < 130) h[x] = Math.min(h[x], 46 + (Math.abs(x - 80) > 40 ? 6 : 0)); }
                else if (id.equals("windswept_savanna")) h = a.peaks(78, 26, 5, 3);
                else h = a.heights(68, 3, 40, 2);
                for (int x = 0; x < Art.W; x++) if (id.equals("windswept_savanna") && h[x] > 60) h[x] = 60;
                ground(a, far, 0xFFCFC48A, h, 0xFFB5AA4A, 0xFF9A7040, STONE);
                scatter(a, h, 3, 28, x -> a.acacia(x, h[x], 12 + a.rnd.nextInt(4), 0xFF7A9A2E));
                tufts(a, h, 40, 0xFFC4B85A);
                break;
            }
            case "badlands": case "wooded_badlands": case "eroded_badlands": {
                a.sky(0xFF7EB6E6, 0xFFF4DFC0, hz + 2);
                a.sun(124, 18, 0xFFFFE5A8);
                far = a.heights(56, 5, 26, 1);
                a.farHills(far, 0xFFC98A6A);
                if (id.equals("eroded_badlands")) { h = new int[Art.W]; java.util.Arrays.fill(h, 72); for (int k = 0; k < 7; k++) { int cx = 12 + k * 22 + a.rnd.nextInt(8), ht = 24 + a.rnd.nextInt(30), wd = 5 + a.rnd.nextInt(4); for (int x = cx - wd; x <= cx + wd; x++) if (x >= 0 && x < Art.W) h[x] = Math.min(h[x], 72 - ht + Math.abs(x - cx) / 2); } }
                else { h = a.heights(60, 3, 50, 2); for (int x = 0; x < Art.W; x++) if (x > 22 && x < 138) h[x] = Math.min(h[x], 44 + (x < 36 || x > 124 ? 10 : 0)); }
                int[] band = {0xFFD9784A, 0xFFB5502E, 0xFFE0B08A, 0xFFC96038, 0xFFF0E0CC, 0xFF9A4A2A, 0xFFD98A5A, 0xFFB5502E};
                for (int x = 0; x < Art.W; x++) for (int y = Math.max(0, h[x]); y < Art.H; y++) a.px(x, y, Art.shade(band[((y + 800) / 3) % band.length], 1 + (Art.hash(x, y, a.seed) - 0.5) * 0.12));
                for (int x = 0; x < Art.W; x++) if (h[x] < 80) a.px(x, h[x], id.equals("wooded_badlands") ? 0xFF7A9A3A : 0xFFC96A3A);
                if (id.equals("wooded_badlands")) scatter(a, h, 6, 18, x -> a.oak(x, h[x], 9, 0xFF4F8A2E));
                else for (int i = 0; i < 4; i++) { int x = a.rnd.nextInt(Art.W); a.cactus(x, h[x], 5); }
                break;
            }
            case "windswept_hills": case "windswept_gravelly_hills": case "windswept_forest": {
                a.sky(0xFF7098C0, 0xFFC4D6E6, hz + 2);
                a.clouds(5, 6, 26, 0xFFE4ECF4);
                far = a.peaks(58, 30, 4, 11);
                a.farHills(far, 0xFF8EA0B2);
                h = a.peaks(80, 46, 4, 12);
                boolean gravel = id.contains("gravelly");
                for (int x = 0; x < Art.W; x++) for (int y = Math.max(0, h[x]); y < Art.H; y++) {
                    int c = (y - h[x] < 2 && h[x] > 56 && !gravel) ? 0xFF6A9A46 : (gravel && Art.hash(x / 3, y / 3, a.seed) > 0.4 ? 0xFF8A8584 : (Art.hash(x, y / 2, a.seed) > 0.55 ? 0xFF6A6A6A : 0xFF7E7E7C));
                    if (y - h[x] < 2 && h[x] > 56) c = gravel ? 0xFF8A8584 : 0xFF6A9A46;
                    a.px(x, y, Art.shade(c, 1 + (Art.hash(x, y, a.seed + 1) - 0.5) * 0.2));
                }
                for (int x = 0; x < Art.W; x++) if (h[x] < 40 && !gravel) a.px(x, h[x], 0xFFE8EEF4);
                if (id.equals("windswept_forest")) scatter(a, h, 6, 14, x -> { if (h[x] > 44) a.spruce(x, h[x], 14 + a.rnd.nextInt(6), 0xFF2F5A3A, false); });
                else scatter(a, h, 3, 30, x -> { if (h[x] > 50) a.oak(x, h[x], 7, 0xFF4F8A32); });
                break;
            }
            case "snowy_slopes": case "jagged_peaks": case "frozen_peaks": case "stony_peaks": {
                boolean jag = id.equals("jagged_peaks"), froz = id.equals("frozen_peaks"), stony = id.equals("stony_peaks");
                a.sky(0xFF6E9CD0, 0xFFD0E2F4, hz + 6);
                a.clouds(4, 4, 22, 0xFFFFFFFF);
                far = a.peaks(62, 44, 4, 21);
                a.farHills(far, stony ? 0xFF9AA0A8 : 0xFFB4C8DE);
                h = id.equals("snowy_slopes") ? a.heights(58, 22, 70, 7) : a.peaks(86, jag ? 78 : 66, jag ? 3 : 3, 9);
                for (int x = 0; x < Art.W; x++) for (int y = Math.max(0, h[x]); y < Art.H; y++) {
                    double stone = Art.vnoise(x / 6.0, y / 4.0, a.seed + 4) * 0.7 + Art.hash(x, y, a.seed + 8) * 0.3;
                    int c;
                    if (stony) c = stone > 0.66 ? 0xFFF0F0EC : (stone > 0.4 ? 0xFFA0A09C : 0xFF7E7E7E);
                    else if (froz) c = stone > 0.55 ? 0xFF9AC4F0 : 0xFFD8ECFF;
                    else c = (y - h[x] < 5 + stone * 10) ? 0xFFF6FAFF : (stone > 0.55 ? 0xFF8A8C90 : 0xFFEAF0F6);
                    if (jag && y - h[x] > 10 && stone > 0.58) c = 0xFF6E7078;
                    a.px(x, y, Art.shade(c, 1 + (Art.hash(x, y, a.seed) - 0.5) * 0.08));
                }
                if (id.equals("snowy_slopes")) scatter(a, h, 6, 18, x -> { if (h[x] > 50) a.spruce(x, h[x], 12, 0xFF2F5A3A, true); });
                break;
            }
            case "cherry_grove": {
                day(a, 0xFF86BCF0, 0xFFEAF2FF, hz, true);
                far = a.peaks(60, 34, 3, 31);
                a.farHills(far, 0xFFBCC8DC);
                snowCap(a, far, 40, 0xFFFFFFFF);
                h = a.heights(70, 5, 34, 2);
                ground(a, null, 0, h, 0xFF8FCB6A, DIRT, STONE);
                scatter(a, h, 4, 28, x -> a.cherry(x, h[x], 11 + a.rnd.nextInt(4)));
                for (int i = 0; i < 40; i++) { int x = a.rnd.nextInt(Art.W); a.px(x, h[x] - 1, 0xFFFFB6D6); }
                flowers(a, h, 12, 0xFFFF8AB8, 0xFFF0F0FF);
                for (int i = 0; i < 24; i++) a.px(a.rnd.nextInt(Art.W), 20 + a.rnd.nextInt(40), 0xFFFFC6E0);
                break;
            }
            case "river": case "frozen_river": {
                boolean fr = id.equals("frozen_river");
                day(a, fr ? 0xFFB4C6D8 : 0xFF72B0EA, fr ? 0xFFE6EEF6 : 0xFFD0E8FA, hz, true);
                far = a.heights(52, 5, 28, 1);
                h = a.heights(66, 3, 36, 2);
                for (int x = 36; x < 124; x++) h[x] = Math.max(h[x], 70 + (int) (Math.sin((x - 36) / 88.0 * Math.PI) * 14));
                ground(a, far, fr ? 0xFFC8D6E2 : 0xFF9FCB86, h, fr ? SNOW : GRASS, fr ? 0xFFDCE6EE : DIRT, STONE);
                a.submerge(h, 70, fr ? 0xFFB8D6F0 : 0xFF3F76E4, fr ? 0xFF7AA0D0 : 0xFF2A4EA0, 0.55, 30, 130);
                if (fr) ice(a, 36, 69, 88, 0xFFCDE4F8);
                scatter(a, h, 3, 30, x -> { if (x < 30 || x > 130) { if (fr) a.spruce(x, h[x], 18, 0xFF2F5A3A, true); else a.oak(x, h[x], 11, 0xFF4F9A32); } });
                if (!fr) for (int i2 = 0; i2 < 8; i2++) { int x = (i2 % 2 == 0 ? 24 : 128) + a.rnd.nextInt(8); for (int j = 0; j < 5; j++) a.px(x, h[x] - j, 0xFF6AA83A); }
                break;
            }
            case "beach": case "snowy_beach": case "stony_shore": {
                boolean snowy = id.equals("snowy_beach"), stone = id.equals("stony_shore");
                day(a, snowy ? 0xFFAFC4D8 : 0xFF70B0EC, snowy ? 0xFFE4EDF6 : 0xFFD6EAFA, hz, true);
                a.gradient(52, Art.H, 0xFF4A94DA, 0xFF1F4A98);
                for (int x = 0; x < Art.W; x++) if (Art.hash(x, 2, a.seed) > 0.8) a.px(x, 52, 0xFFB8DCFF);
                h = new int[Art.W];
                for (int x = 0; x < Art.W; x++) h[x] = (int) (stone ? 84 - x * 0.3 + Math.sin(x * 0.35) * 2.5 : 82 - x * 0.2) + (stone ? 0 : (int) (Math.sin(x * 0.2) * 1.2));
                int top = stone ? 0xFF8C8E8A : snowy ? 0xFFEFEBD4 : 0xFFF0E2A6, sub = stone ? 0xFF6E706C : snowy ? 0xFFDAD2B0 : 0xFFE0CC8A;
                a.land(h, top, sub, sub, 14, 0.22);
                a.submerge(h, 58, 0xFF4A94DA, 0xFF1F4A98, 0.5, 0, 160);
                if (snowy) for (int x = 100; x < 160; x++) for (int j = 0; j < 3; j++) a.px(x, h[x] + j, 0xFFF6FAFF);
                if (snowy) { ice(a, 8, 57, 22, 0xFFCFE4F8); ice(a, 44, 58, 14, 0xFFCFE4F8); }
                if (!snowy && !stone) { a.oak(146, h[146], 11, 0xFF4F9A32); a.rect(126, h[126] - 4, 1, 4, 0xFF8A6A3A); a.rect(126, h[126] - 6, 4, 2, 0xFFE8E0D0); }
                if (stone) { a.rockPile(130, h[130], 0xFF9A9A98); a.rockPile(112, h[112], 0xFF7A7A78); a.rockPile(148, h[148], 0xFF8A8A88); }
                break;
            }
            case "ocean": case "deep_ocean": case "warm_ocean": case "lukewarm_ocean": case "deep_lukewarm_ocean": case "cold_ocean": case "deep_cold_ocean": case "frozen_ocean": case "deep_frozen_ocean":
                ocean(a, id);
                break;
            case "mushroom_fields": {
                a.sky(0xFFC8A8D8, 0xFFF0DCF0, hz + 2);
                a.clouds(4, 6, 26, 0xFFFAF0FA);
                far = a.heights(56, 3, 26, 1);
                h = a.heights(70, 3, 32, 2);
                a.water(76, 0xFF4A6AD0, 0xFF2A3A90, 1.0);
                ground(a, far, 0xFFB8A0C8, h, 0xFF8A7A90, 0xFF6A5A70, STONE);
                a.water(78, 0xFF4A6AD0, 0xFF2A3A90, 1.0);
                scatter(a, h, 4, 30, x -> a.bigMushroom(x, h[x], 16 + a.rnd.nextInt(10), a.rnd.nextBoolean() ? 0xFFCC2A2A : 0xFF9A7050, 0xFFE8E0D0));
                for (int i = 0; i < 30; i++) { int x = a.rnd.nextInt(Art.W); a.px(x, h[x] - 1, a.rnd.nextBoolean() ? 0xFFCC2A2A : 0xFF9A7050); }
                break;
            }
            case "dripstone_caves": case "lush_caves": case "deep_dark":
                cave(a, id);
                break;
            case "nether_wastes": case "soul_sand_valley": case "crimson_forest": case "warped_forest": case "basalt_deltas":
                nether(a, id);
                break;
            default:
                endBiome(a, id);
        }
    }

    // ------------------------------------------------------------------ oceans

    private static void ocean(Art a, String id) {
        boolean deep = id.startsWith("deep_"), frozen = id.contains("frozen"), warm = id.contains("warm") && !id.contains("lukewarm"), luke = id.contains("lukewarm"), cold = id.contains("cold");
        int top = frozen ? 0xFF6A8EC0 : warm ? 0xFF3FD0D8 : luke ? 0xFF3FA8D8 : cold ? 0xFF4A7AAA : 0xFF3F6FD0;
        int bot = frozen ? 0xFF2A3E78 : warm ? 0xFF1F7AA8 : luke ? 0xFF1F5A98 : cold ? 0xFF1F3E70 : 0xFF1A2E78;
        int skyTop = frozen ? 0xFFAFC2D8 : 0xFF74B4EC, skyBot = frozen ? 0xFFE4ECF4 : 0xFFD0E8FA;
        a.sky(skyTop, skyBot, 26);
        a.clouds(3, 4, 14, 0xFFFFFFFF);
        a.sun(126, 10, 0xFFFFF0B0);
        int surface = 24;
        a.gradient(surface, Art.H, top, deep ? Art.shade(bot, 0.55) : bot);
        // light rays
        for (int k = 0; k < 6; k++) { int x0 = 10 + k * 28 + a.rnd.nextInt(8); for (int y = surface; y < 70; y++) { int x = x0 + (y - surface) / 3; a.px(x, y, Art.mix(a.get(x, y), 0xFFFFFFFF, 0.07)); a.px(x + 1, y, Art.mix(a.get(x + 1, y), 0xFFFFFFFF, 0.07)); } }
        for (int x = 0; x < Art.W; x++) { a.px(x, surface, Art.shade(top, 1.35)); if (Art.hash(x, 3, a.seed) > 0.7) a.px(x, surface - 1, 0xFFCDE8FF); }
        // floor
        int[] fl = a.heights(deep ? 82 : 74, deep ? 3 : 4, 30, 4);
        int floorTop = warm || luke ? 0xFFE2D29A : cold ? 0xFF7A7A78 : deep ? 0xFF6A6A70 : 0xFFD8C890;
        for (int x = 0; x < Art.W; x++) for (int y = fl[x]; y < Art.H; y++) a.px(x, y, Art.shade(Art.mix(floorTop, bot, deep ? 0.55 : 0.3), 0.92 + Art.hash(x, y, a.seed) * 0.16));
        if (frozen) {
            for (int k = 0; k < 6; k++) { int x = k * 28 + a.rnd.nextInt(10), w = 14 + a.rnd.nextInt(14); ice(a, x, surface - 1, w, 0xFFDCEEFF); a.tex(x + 2, surface + 1, w - 4, 3 + a.rnd.nextInt(6), 0xFFAACCF0, 0.15); }
            // iceberg
            int cx = 100;
            for (int j = 0; j < 14; j++) a.rect(cx - 12 + j / 2, surface - 12 + j, 24 - j, 1, Art.shade(0xFFEAF4FF, 1 - j * 0.01));
            for (int j = 0; j < 16; j++) a.rect(cx - 10 + j / 3, surface + 2 + j, 20 - j * 2 / 3, 1, Art.mix(0xFF9FC8F0, top, j / 20.0));
        }
        if (warm) {
            int[] cs = {0xFFE0409A, 0xFFE06A30, 0xFFE8D040, 0xFF8040C8, 0xFF30B0E0};
            for (int k = 0; k < 9; k++) { int x = 8 + k * 17 + a.rnd.nextInt(6), c = cs[a.rnd.nextInt(cs.length)], ht = 5 + a.rnd.nextInt(9); for (int j = 0; j < ht; j++) { a.px(x, fl[Math.min(159, x)] - j, Art.shade(c, 0.9 + a.rnd.nextDouble() * 0.2)); a.px(x + 1, fl[Math.min(158, x)] - j + 2, Art.shade(c, 0.8)); } if (k % 2 == 0) { a.px(x - 1, fl[x] - ht + 1, c); a.px(x + 2, fl[x] - ht + 2, c); } }
        }
        if (!deep && !frozen) for (int k = 0; k < 7; k++) { int x = 6 + k * 22 + a.rnd.nextInt(8); int ht = 12 + a.rnd.nextInt(14); for (int j = 0; j < ht; j++) { a.px(x + (j / 4 % 2), fl[x] - j, 0xFF3F9A32); } }
        if (!deep) for (int k = 0; k < 5; k++) { int x = a.rnd.nextInt(Art.W); a.px(x, fl[x] - 1, 0xFF2F8A2E); a.px(x, fl[x] - 2, 0xFF2F8A2E); }
        // fish
        int fishN = deep ? 3 : 7;
        for (int k = 0; k < fishN; k++) {
            int x = a.rnd.nextInt(Art.W - 8), y = surface + 10 + a.rnd.nextInt(Math.max(8, fl[Math.min(159, x)] - surface - 20));
            int c = warm ? (k % 2 == 0 ? 0xFFF08020 : 0xFFE84090) : (cold ? 0xFFB8A890 : 0xFF9A9AA8);
            a.rect(x, y, 5, 2, c); a.px(x + 5, y - 1, c); a.px(x + 5, y + 2, c); a.px(x + 1, y, 0xFF101010);
        }
        a.fog(surface, Art.H, bot, deep ? 0.3 : 0.12);
    }

    // ------------------------------------------------------------------ caves

    private static void cave(Art a, String id) {
        boolean dripstone = id.equals("dripstone_caves"), lush = id.equals("lush_caves"), dd = id.equals("deep_dark");
        int stone = dripstone ? 0xFF6A5A4A : lush ? 0xFF5A5A58 : 0xFF2A323A;
        a.fill(stone);
        for (int y = 0; y < Art.H; y++) for (int x = 0; x < Art.W; x++) a.px(x, y, Art.shade(stone, 0.88 + Art.hash(x, y, a.seed) * 0.24));
        // the cavity
        int[] ceil = a.heights(16, 8, 24, 1), floor = a.heights(74, 6, 20, 2);
        for (int x = 0; x < Art.W; x++) {
            int c0 = Math.max(6, ceil[x]), f0 = Math.min(Art.H - 8, floor[x]);
            for (int y = c0; y < f0; y++) {
                double t = (y - c0) / (double) Math.max(1, f0 - c0);
                int base = dripstone ? Art.mix(0xFF3A2E26, 0xFF241C18, t) : lush ? Art.mix(0xFF1E3A26, 0xFF14281A, t) : Art.mix(0xFF06121A, 0xFF030A10, t);
                a.px(x, y, Art.shade(base, 0.92 + Art.hash(x, y, a.seed + 2) * 0.16));
            }
            if (dripstone) {
                for (int y = f0; y < Art.H; y++) a.px(x, y, Art.shade(0xFF8A6A4A, 0.85 + Art.hash(x, y, a.seed) * 0.3));
                for (int y = 0; y < c0; y++) a.px(x, y, Art.shade(0xFF6A5444, 0.85 + Art.hash(x, y, a.seed) * 0.3));
            } else if (lush) {
                for (int y = f0; y < Art.H; y++) a.px(x, y, y - f0 < 3 ? Art.shade(0xFF5A9A3A, 0.9 + Art.hash(x, y, a.seed) * 0.2) : Art.shade(0xFF6A5A3A, 0.9 + Art.hash(x, y, a.seed) * 0.2));
                for (int y = 0; y < c0; y++) a.px(x, y, y > c0 - 3 ? Art.shade(0xFF5A9A3A, 0.9 + Art.hash(x, y, a.seed) * 0.2) : Art.shade(0xFF5A5A58, 0.9 + Art.hash(x, y, a.seed) * 0.2));
            } else {
                for (int y = f0; y < Art.H; y++) a.px(x, y, Art.shade(0xFF12202A, 0.8 + Art.hash(x, y, a.seed) * 0.4));
                for (int y = 0; y < c0; y++) a.px(x, y, Art.shade(0xFF16242E, 0.8 + Art.hash(x, y, a.seed) * 0.4));
            }
        }
        if (dripstone) {
            for (int k = 0; k < 18; k++) { int x = 8 + a.rnd.nextInt(144); int c0 = Math.max(6, ceil[x]); int len = 6 + a.rnd.nextInt(22); for (int j = 0; j < len; j++) { int w = Math.max(0, (len - j) / 7); for (int i = -w; i <= w; i++) a.px(x + i, c0 + j, Art.shade(0xFFB89A74, 0.85 + Art.hash(x + i, j, a.seed) * 0.3)); } }
            for (int k = 0; k < 14; k++) { int x = 8 + a.rnd.nextInt(144); int f0 = Math.min(Art.H - 8, floor[x]); int len = 5 + a.rnd.nextInt(18); for (int j = 0; j < len; j++) { int w = Math.max(0, (len - j) / 6); for (int i = -w; i <= w; i++) a.px(x + i, f0 - j, Art.shade(0xFFB89A74, 0.85 + Art.hash(x + i, j, a.seed) * 0.3)); } }
            for (int k = 0; k < 4; k++) { int x = 20 + a.rnd.nextInt(120); for (int y = 20; y < 60; y++) if (y % 6 < 3) a.px(x, y, 0xFF5AA0E8); }
        } else if (lush) {
            for (int k = 0; k < 16; k++) { int x = 6 + a.rnd.nextInt(148); int c0 = Math.max(6, ceil[x]); int len = 4 + a.rnd.nextInt(14); for (int j = 0; j < len; j++) a.px(x, c0 + j, 0xFF4A9A32); if (a.rnd.nextInt(3) > 0) { a.px(x, c0 + len, 0xFFF0A030); a.px(x, c0 + len + 1, 0xFFFFD060); } }
            for (int k = 0; k < 8; k++) { int x = 8 + a.rnd.nextInt(144); int f0 = Math.min(Art.H - 8, floor[x]); a.leafBlob(x, f0 - 2, 3, 2, 0xFF7A9A3A, 0.3); a.px(x, f0 - 3, 0xFFE8A0D0); a.px(x + 1, f0 - 4, 0xFFE8A0D0); }
            for (int k = 0; k < 10; k++) { int x = 8 + a.rnd.nextInt(144); a.px(x, Math.min(Art.H - 8, floor[x]) - 1, 0xFFF0E8A0); }
            a.fog(10, 80, 0xFF4A9A5A, 0.12);
        } else {
            for (int k = 0; k < 40; k++) { int x = 6 + a.rnd.nextInt(148); int f0 = Math.min(Art.H - 8, floor[x]); a.px(x, f0, 0xFF0B2A36); a.px(x + 1, f0, 0xFF0B2A36); if (a.rnd.nextInt(2) == 0) { a.px(x, f0 + 1, 0xFF1AC8B8); } }
            for (int k = 0; k < 6; k++) { int x = 16 + a.rnd.nextInt(130); int f0 = Math.min(Art.H - 8, floor[x]); a.rect(x - 1, f0 - 3, 4, 3, 0xFF0A2630); a.rect(x - 1, f0 - 4, 4, 1, 0xFF22E0D0); a.px(x, f0 - 5, 0xFF22E0D0); }
            for (int k = 0; k < 5; k++) { int x = 12 + a.rnd.nextInt(136); int f0 = Math.min(Art.H - 8, floor[x]); a.rect(x - 1, f0 - 5, 3, 5, 0xFF0A2630); a.px(x, f0 - 6, 0xFF9AF8F0); a.px(x - 1, f0 - 6, 0xFF1AC8B8); a.px(x + 1, f0 - 6, 0xFF1AC8B8); }
            // soul lanterns
            for (int k = 0; k < 3; k++) { int x = 30 + k * 50; a.px(x, 20, 0xFF30D8E8); a.rect(x - 1, 21, 3, 3, 0xFF1AB8C8); a.px(x, 22, 0xFFA8FFFF); }
            a.vignette(0.5);
        }
    }

    // ------------------------------------------------------------------ nether and end

    private static void nether(Art a, String id) {
        int sky, floor, ceilc, glow;
        switch (id) {
            case "soul_sand_valley": sky = 0xFF1A3A4A; floor = 0xFF5A4A3A; ceilc = 0xFF2A4A58; glow = 0xFF40E0F0; break;
            case "crimson_forest": sky = 0xFF5A1018; floor = 0xFF8A2A32; ceilc = 0xFF4A1018; glow = 0xFFFF5040; break;
            case "warped_forest": sky = 0xFF0A3A44; floor = 0xFF1A6A6A; ceilc = 0xFF0A2A34; glow = 0xFF30E0C8; break;
            case "basalt_deltas": sky = 0xFF3A3030; floor = 0xFF4A4A4E; ceilc = 0xFF2A2628; glow = 0xFFFF7A20; break;
            default: sky = 0xFF5A1818; floor = 0xFF7A2A2A; ceilc = 0xFF3A1010; glow = 0xFFFF6A20;
        }
        a.gradient(0, Art.H, Art.shade(sky, 0.7), sky);
        int[] cl = a.heights(14, 8, 22, 5);
        for (int x = 0; x < Art.W; x++) for (int y = 0; y < Math.max(2, cl[x]); y++) a.px(x, y, Art.shade(ceilc, 0.8 + Art.hash(x, y, a.seed) * 0.4));
        for (int k = 0; k < 14; k++) { int x = a.rnd.nextInt(Art.W), len = 3 + a.rnd.nextInt(10); for (int j = 0; j < len; j++) a.px(x, cl[x] + j, Art.shade(ceilc, 0.8)); if (id.equals("crimson_forest") || id.equals("warped_forest")) for (int j = 0; j < len + 6; j++) a.px(x, cl[x] + j, id.equals("warped_forest") ? 0xFF2A9A8A : 0xFFB02A3A); }
        int[] h = a.heights(70, id.equals("basalt_deltas") ? 6 : 5, 26, 6);
        a.land(h, floor, Art.shade(floor, 0.85), Art.shade(floor, 0.7), 8, 0.3);
        if (id.equals("nether_wastes") || id.equals("basalt_deltas")) {
            for (int x = 30; x < 110; x++) for (int y = 78; y < Art.H; y++) a.px(x, y, Art.shade(0xFFFF7A20, 0.85 + Art.hash(x, y, a.seed) * 0.3));
            for (int x = 30; x < 110; x++) a.px(x, 77, 0xFFFFC060);
        }
        if (id.equals("basalt_deltas")) for (int k = 0; k < 14; k++) { int x = 4 + k * 11 + a.rnd.nextInt(4), ht = 8 + a.rnd.nextInt(26); a.tex(x, 72 - ht + (h[Math.min(159, x)] - 70), 4, ht + 20, 0xFF5A5A60, 0.3); a.rect(x, 72 - ht + (h[Math.min(159, x)] - 70), 4, 1, 0xFF7A7A82); }
        if (id.equals("soul_sand_valley")) {
            for (int k = 0; k < 9; k++) { int x = 8 + a.rnd.nextInt(144); a.px(x, h[x] - 1, glow); a.px(x, h[x] - 2, 0xFFA8FFFF); a.px(x - 1, h[x] - 1, glow); a.px(x + 1, h[x] - 1, glow); }
            for (int k = 0; k < 3; k++) { int x = 20 + k * 48; for (int j = 0; j < 22; j++) a.rect(x + (j % 2), h[x] - j, 3, 1, 0xFFD8CCB0); }
        }
        if (id.equals("crimson_forest") || id.equals("warped_forest")) {
            boolean w = id.equals("warped_forest");
            int stem = w ? 0xFF2A8A8A : 0xFF8A2A3A, cap = w ? 0xFF1AA89A : 0xFFB02A32, wart = w ? 0xFF14C8A0 : 0xFFD83A3A;
            for (int k = 0; k < 6; k++) { int x = 14 + k * 26 + a.rnd.nextInt(8), ht = 18 + a.rnd.nextInt(12); for (int j = 0; j < ht; j++) { a.px(x, h[x] - j, stem); a.px(x + 1, h[x] - j, Art.shade(stem, 0.8)); } a.leafBlob(x, h[x] - ht, 9, 4, cap, 0.35); for (int i = -6; i <= 6; i += 3) a.px(x + i, h[x] - ht - 3, wart); if (!w) for (int i = -7; i <= 7; i += 4) for (int j = 0; j < 8; j++) a.px(x + i, h[x] - ht + 3 + j, 0xFFC02A3A); }
            for (int k = 0; k < 40; k++) { int x = a.rnd.nextInt(Art.W); a.px(x, h[x] - 1, w ? 0xFF1AC8B0 : 0xFFE04040); }
        }
        if (id.equals("nether_wastes")) for (int k = 0; k < 8; k++) { int x = a.rnd.nextInt(Art.W); a.rockPile(x, h[x], 0xFF8A3030); }
        a.fog(30, 90, glow, 0.08);
        a.vignette(0.4);
    }

    private static void endBiome(Art a, String id) {
        a.gradient(0, Art.H, 0xFF0A0614, 0xFF241238);
        a.stars(90, 60);
        int stone = 0xFFE4E0A8;
        switch (id) {
            case "the_end": {
                int[] h = new int[Art.W];
                for (int x = 0; x < Art.W; x++) h[x] = 66 + (int) (Math.pow(Math.abs(x - 80) / 80.0, 2) * 40);
                a.land(h, stone, Art.shade(stone, 0.9), Art.shade(stone, 0.8), 40, 0.2);
                for (int k = 0; k < 5; k++) { int x = 20 + k * 30, ht = 28 + (k % 3) * 8; a.tex(x, 66 - ht, 5, ht + 8, 0xFF1A1424, 0.3); a.rect(x, 66 - ht - 2, 5, 2, 0xFF3A3A44); a.px(x + 2, 66 - ht - 5, 0xFFFF6AD0); a.px(x + 2, 66 - ht - 4, 0xFFFFA8E8); }
                a.disc(80, 14, 3, 0xFF3A2A50);
                break;
            }
            case "small_end_islands": {
                for (int k = 0; k < 8; k++) { int cx = 12 + a.rnd.nextInt(136), cy = 24 + a.rnd.nextInt(52), r = 5 + a.rnd.nextInt(9); for (int i = -r; i <= r; i++) { int d = (int) (Math.sqrt(Math.max(0, r * r - i * i)) * 0.45); for (int j = 0; j <= d + 2; j++) a.px(cx + i, cy + j, Art.shade(stone, 0.92 + Art.hash(cx + i, j, a.seed) * 0.2)); a.px(cx + i, cy, Art.shade(stone, 1.1)); } for (int j = 0; j < r; j++) a.px(cx + (j % 3) - 1, cy + (int) (r * 0.45) + 3 + j, Art.shade(stone, 0.6)); }
                break;
            }
            default: {
                boolean hi = id.equals("end_highlands"), bar = id.equals("end_barrens");
                int[] h = a.heights(hi ? 54 : 66, hi ? 12 : 3, 34, 3);
                if (bar) for (int x = 0; x < Art.W; x++) h[x] = 66 + (int) (Math.pow(Math.abs(x - 80) / 80.0, 2) * 30);
                a.land(h, stone, Art.shade(stone, 0.9), Art.shade(stone, 0.8), 60, 0.2);
                if (!bar) for (int k = 0; k < (hi ? 10 : 7); k++) { int x = 6 + a.rnd.nextInt(148), ht = 8 + a.rnd.nextInt(hi ? 22 : 12); for (int j = 0; j < ht; j++) { a.px(x, h[x] - j, 0xFFA04AB8); if (j > ht / 2 && j % 4 == 0) { a.px(x - 1, h[x] - j, 0xFFA04AB8); a.px(x + 1, h[x] - j - 1, 0xFFA04AB8); } } a.px(x, h[x] - ht - 1, 0xFFF0C0F8); }
                if (hi) { int x = 118; for (int t = 0; t < 3; t++) { a.tex(x + t * 8, h[x] - 22 - t * 4, 6, 22 + t * 4, 0xFFB898B8, 0.25); a.rect(x + t * 8 - 1, h[x] - 24 - t * 4, 8, 2, 0xFFD8B8D8); } }
            }
        }
        a.fog(60, 90, 0xFF5A3A78, 0.15);
    }

    // ------------------------------------------------------------------ structures

    static void structure(Art a, String id) {
        StructureArt.draw(a, id);
    }
}
