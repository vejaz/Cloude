package slatefinder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * The 3D pictures of the book: each biome and structure is built as a small scene (its terrain, its animal, its
 * buildings), rendered once and kept on disk. The same scene is what the enlarged picture lets you walk around.
 */
final class Previews3D {
    private Previews3D() {}

    static final int W = 448, H = 252;
    private static final Map<String, BufferedImage> READY = new ConcurrentHashMap<>();
    private static final String VERSION = "v3";

    /** A scene to draw, and where to look at it from. */
    static final class Scene {
        Terrain world;
        double fx, fz, dist, yaw, pitch, fov = Math.toRadians(62);
        Caster.Opts opts = new Caster.Opts();

        Caster.Cam cam() {
            Caster.Cam c = new Caster.Cam();
            double fxv = Math.cos(pitch) * Math.sin(yaw), fyv = Math.sin(pitch), fzv = Math.cos(pitch) * Math.cos(yaw);
            double ty = world.topAt((int) Math.floor(fx), (int) Math.floor(fz)) + 3;
            c.x = fx - fxv * dist; c.z = fz - fzv * dist; c.y = ty - fyv * dist;
            double gh = world.topAt((int) Math.floor(c.x), (int) Math.floor(c.z));
            if (c.y < gh + 3) c.y = gh + 3;
            c.yaw = yaw;
            c.pitch = Math.atan2(ty - c.y, Math.max(0.01, Math.hypot(fx - c.x, fz - c.z)));
            c.fov = fov;
            return c;
        }
    }

    static boolean has3D(Lexicon.Item it) {
        switch (it.id) {
            case "dripstone_caves": case "lush_caves": case "deep_dark": case "ancient_city": return false;
            default: return true;
        }
    }

    // ------------------------------------------------------------------ the pictures

    static BufferedImage ready(Lexicon.Item it) { return READY.get(it.id); }

    static File cacheDir() {
        String ad = System.getenv("APPDATA");
        File dir = new File(ad != null ? new File(ad, "SlateFinder") : new File("."), "previews3d-" + VERSION);
        return dir;
    }

    /** Loads the stored pictures, drawing and storing any that are missing. {@code onEach} runs after every one. */
    static void warm(Runnable onEach) {
        File dir = cacheDir();
        dir.mkdirs();
        for (Lexicon.Item it : Lexicon.items()) {
            if (!has3D(it) || READY.containsKey(it.id)) continue;
            File f = new File(dir, it.id + ".png");
            BufferedImage im = null;
            if (f.isFile()) try { im = ImageIO.read(f); } catch (Exception ignored) { }
            if (im == null) {
                try {
                    im = render(scene(it));
                    ImageIO.write(im, "png", f);
                } catch (Exception | Error e) { continue; }
            }
            READY.put(it.id, im);
            if (onEach != null) onEach.run();
            try { Thread.sleep(90); } catch (InterruptedException e) { return; }   // be gentle: the window stays smooth while they are drawn
        }
    }

    static BufferedImage render(Scene s) {
        BufferedImage im = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int[] px = new int[W * H];
        new Caster(s.world).render3D(px, W, H, 1, s.cam(), s.opts, true);
        im.setRGB(0, 0, W, H, px, 0, W);
        return im;
    }

    // ------------------------------------------------------------------ the scenes

    private static String animalFor(String id) {
        switch (id) {
            case "plains": case "sunflower_plains": return "cow";
            case "snowy_plains": return "snowgolem";
            case "ice_spikes": return "polarbear";
            case "desert": return "camel";
            case "swamp": case "mangrove_swamp": return "frog";
            case "forest": case "old_growth_pine_taiga": case "old_growth_spruce_taiga": return "wolf";
            case "flower_forest": case "cherry_grove": return "rabbit";
            case "birch_forest": case "windswept_hills": case "meadow": return "sheep";
            case "old_growth_birch_forest": case "taiga": case "snowy_taiga": return "fox";
            case "pale_garden": return "creaking";
            case "grove": case "windswept_gravelly_hills": case "snowy_slopes": case "jagged_peaks": case "frozen_peaks": case "stony_peaks": return "goat";
            case "bamboo_jungle": return "panda";
            case "savanna": case "savanna_plateau": case "windswept_savanna": case "windswept_forest": return "llama";
            case "badlands": case "wooded_badlands": return "armadillo";
            case "mushroom_fields": return "mooshroom";
            default: return null;
        }
    }

    private static Terrain.Style adjusted(Terrain w, int i, double base) {
        Terrain.Style s = Terrain.copy(w.styles[i]);
        s.base = (float) base;
        w.styles[i] = s;
        return s;
    }

    static Scene scene(Lexicon.Item it) {
        Scene s = new Scene();
        s.opts.sunElev = 46; s.opts.sunAz = 205; s.opts.maxDist = 260;
        String id = it.id;
        Terrain w;
        if (it.structure()) w = structureScene(s, it);
        else w = biomeScene(s, it);
        s.world = w;
        return s;
    }

    private static Terrain biomeScene(Scene s, Lexicon.Item it) {
        String id = it.id;
        Terrain w;
        boolean ocean = id.contains("ocean");
        s.yaw = 0; s.fx = 0; s.fz = 0;
        if (ocean) {
            w = new Terrain(Terrain.scene(new String[]{id, "beach", "plains"}, (x, z) -> z < -70 ? 2 : z < -40 ? 1 : 0, 16), 3);
            boolean deep = id.contains("deep");
            adjusted(w, 0, deep ? 30 : 55);
            if (id.equals("warm_ocean")) { java.util.Random r = new java.util.Random(4); for (int k = 0; k < 34; k++) w.addExtra(Models.coral(k % 5), -40 + r.nextInt(80), -25 + r.nextInt(80)); }
            if (id.equals("cold_ocean") || id.equals("lukewarm_ocean")) w.addExtra(Models.shipwreck(false), 22, 22);
            s.fz = 10; s.dist = 100; s.pitch = Math.toRadians(-27); s.yaw = Math.toRadians(14);
            s.opts.maxDist = 420;
            return w;
        }
        switch (id) {
            case "river": case "frozen_river": {
                String land = id.equals("frozen_river") ? "snowy_plains" : "plains";
                w = new Terrain(Terrain.scene(new String[]{id, land}, (x, z) -> Math.abs(z) < 14 ? 0 : 1, 16), 3);
                s.fz = 0; s.dist = 85; s.pitch = Math.toRadians(-20); s.yaw = Math.toRadians(25);
                return w;
            }
            case "beach": case "snowy_beach": case "stony_shore": {
                String land = id.equals("snowy_beach") ? "snowy_plains" : id.equals("stony_shore") ? "windswept_hills" : "plains";
                w = new Terrain(Terrain.scene(new String[]{id, "ocean", land}, (x, z) -> z > 44 ? 1 : z > -8 ? 0 : 2, 16), 3);
                adjusted(w, 1, 54);
                s.fz = 18; s.dist = 80; s.pitch = Math.toRadians(-17); s.yaw = Math.toRadians(28);
                if (!id.equals("stony_shore")) w.addExtra(Models.animal(id.equals("beach") ? "turtle" : "rabbit"), 8, 8);
                return w;
            }
            default:
        }
        w = new Terrain(Terrain.scene(new String[]{id}, (x, z) -> 0), 3);
        Terrain.Style st = w.styles[0];
        boolean high = st.base > 90;
        s.dist = high ? 150 : 46; s.pitch = Math.toRadians(high ? -10 : -12); s.fz = high ? 50 : 6; s.yaw = Math.toRadians(high ? 20 : 0);
        s.opts.maxDist = high ? 420 : 250;
        String animal = animalFor(id);
        if (animal != null) {
            w.addExtra(Models.animal(animal), high ? 30 : 4, high ? 0 : -9);
            if (!high) w.addExtra(Models.animal(animal), -8, -2);
        }
        return w;
    }

    private static Terrain structureScene(Scene s, Lexicon.Item it) {
        String id = it.id;
        String biome;
        switch (id) {
            case "village": case "plains_village": case "pillager_outpost": biome = "plains"; break;
            case "desert_village": case "desert_pyramid": case "desert_well": case "fossil": biome = "desert"; break;
            case "savanna_village": biome = "savanna"; break;
            case "snowy_village": case "igloo": biome = "snowy_plains"; break;
            case "taiga_village": case "trail_ruins": biome = "taiga"; break;
            case "woodland_mansion": biome = "dark_forest"; break;
            case "jungle_temple": biome = "jungle"; break;
            case "swamp_hut": biome = "swamp"; break;
            case "warm_ocean_ruin": biome = "warm_ocean"; break;
            case "buried_treasure": case "beached_shipwreck": biome = "beach"; break;
            case "ocean_monument": biome = "deep_ocean"; break;
            default: biome = id.contains("ruin") || id.contains("ship") ? "ocean" : "plains";
        }
        Terrain w = new Terrain(Terrain.scene(new String[]{biome}, (x, z) -> 0, 16), 5);
        boolean water = biome.contains("ocean");
        if (water) adjusted(w, 0, id.equals("ocean_monument") ? 34 : 56);
        if (biome.equals("beach")) adjusted(w, 0, 64.2);
        w.addSite(new Terrain.Site(0, 0, id));
        boolean village = id.contains("village") || id.equals("village");
        s.fx = 0; s.fz = 0;
        s.yaw = Math.toRadians(village ? 18 : 28);
        s.pitch = Math.toRadians(village ? -24 : -17);
        switch (id) {
            case "village": case "plains_village": case "desert_village": case "savanna_village": case "snowy_village": case "taiga_village": s.dist = 78; s.opts.maxDist = 340; break;
            case "woodland_mansion": s.dist = 64; s.fz = 2; s.pitch = Math.toRadians(-14); break;
            case "ocean_monument": s.dist = 88; s.pitch = Math.toRadians(-20); s.opts.maxDist = 380; break;
            case "desert_pyramid": s.dist = 85; break;
            case "pillager_outpost": s.dist = 58; s.pitch = Math.toRadians(-12); break;
            case "jungle_temple": s.dist = 46; s.pitch = Math.toRadians(-24); break;
            case "igloo": s.dist = 24; break;
            case "swamp_hut": s.dist = 34; break;
            case "shipwreck": case "ocean_shipwreck": case "beached_shipwreck": s.dist = 40; s.pitch = Math.toRadians(-36); break;
            case "ocean_ruin": case "warm_ocean_ruin": case "cold_ocean_ruin": s.dist = 30; s.pitch = Math.toRadians(-42); break;
            case "buried_treasure": s.dist = 22; break;
            case "desert_well": s.dist = 16; break;
            case "trail_ruins": s.dist = 30; break;
            case "fossil": s.dist = 26; break;
            default: s.dist = 40;
        }
        return w;
    }
}
