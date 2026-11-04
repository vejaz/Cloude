package slatefinder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Builds a small made-up biome map file in the real format, so the Describing tab can be exercised
 * (tests, screenshots) without the seed-sized map. Not used by the app itself.
 */
public final class DescribeTestWorld {
    private DescribeTestWorld() {}

    static final String[] BIOMES = {
        "plains", "sunflower_plains", "forest", "birch_forest", "dark_forest", "taiga", "snowy_taiga", "snowy_plains", "ice_spikes",
        "desert", "badlands", "savanna", "jungle", "swamp", "river", "frozen_river", "beach", "ocean", "deep_ocean", "warm_ocean",
        "frozen_ocean", "snowy_slopes", "jagged_peaks", "frozen_peaks", "stony_peaks", "meadow", "cherry_grove", "grove", "windswept_hills",
        "mushroom_fields", "cold_ocean", "lukewarm_ocean", "deep_dark", "dripstone_caves", "lush_caves", "old_growth_pine_taiga"
    };

    /** True only when %APPDATA% points inside the temp folder, so tests can never touch a real Slate Finder data folder. */
    public static boolean sandboxed() {
        String ad = System.getenv("APPDATA");
        return ad != null && new File(ad).getAbsolutePath().startsWith(new File(System.getProperty("java.io.tmpdir")).getAbsolutePath());
    }

    public static File write(File f, int radius, int step, long seed) throws IOException {
        int n = 2 * radius / step + 1;
        byte[] data = new byte[n * n];
        Random rnd = new Random(seed);
        double[] ox = new double[6], oz = new double[6];
        for (int i = 0; i < 6; i++) { ox[i] = rnd.nextDouble() * 1000; oz[i] = rnd.nextDouble() * 1000; }
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < BIOMES.length; i++) idx.put(BIOMES[i], i);
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                double wx = x * 0.045, wz = z * 0.045;
                double temp = Math.sin(wx * 0.6 + ox[0]) + Math.sin(wz * 0.5 + oz[0]) * 0.8 + Math.sin((wx + wz) * 1.3 + ox[1]) * 0.35;
                double wet = Math.sin(wx * 0.7 + ox[2]) + Math.cos(wz * 0.6 + oz[2]) * 0.9 + Math.sin((wx - wz) * 1.1 + ox[3]) * 0.3;
                double alt = Math.sin(wx * 0.9 + ox[4]) * 0.7 + Math.sin(wz * 0.8 + oz[4]) * 0.7 + Math.sin((wx + 2 * wz) * 1.7 + ox[5]) * 0.3;
                String b;
                if (alt < -0.9) b = temp < -0.9 ? "frozen_ocean" : temp > 0.8 ? "warm_ocean" : alt < -1.25 ? "deep_ocean" : "ocean";
                else if (alt < -0.8) b = temp < -0.9 ? "snowy_plains" : "beach";
                else if (alt > 1.0) b = temp < -0.2 ? (alt > 1.2 ? "frozen_peaks" : "snowy_slopes") : (alt > 1.2 ? "jagged_peaks" : (wet > 0.3 ? "meadow" : "stony_peaks"));
                else if (Math.abs(Math.sin(wx * 0.9 + wz * 0.7 + ox[1] * 3)) < 0.045) b = temp < -0.8 ? "frozen_river" : "river";
                else if (temp < -1.0) b = wet > 0.4 ? "snowy_taiga" : (wet < -1.3 ? "ice_spikes" : "snowy_plains");
                else if (temp < -0.1) b = wet > 0.5 ? "taiga" : wet < -0.6 ? "plains" : (alt > 0.6 ? "windswept_hills" : "forest");
                else if (temp < 0.8) b = wet > 0.9 ? "swamp" : wet > 0.3 ? "dark_forest" : wet < -0.7 ? "sunflower_plains" : (wet < 0 ? "plains" : "birch_forest");
                else b = wet > 0.6 ? "jungle" : wet > -0.2 ? "savanna" : wet < -1.2 ? "badlands" : "desert";
                data[z * n + x] = (byte) idx.get(b).intValue();
            }
        }
        f.getParentFile().mkdirs();
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(f))) {
            o.write(("BIOMEGRID1\nseed " + seed + "\nstep " + step + "\nradius " + radius + "\nn " + n + "\ny 64\nbiomes " + BIOMES.length + "\n").getBytes(StandardCharsets.US_ASCII));
            for (String b : BIOMES) o.write(("minecraft:" + b + "\n").getBytes(StandardCharsets.US_ASCII));
            o.write("DATA\n".getBytes(StandardCharsets.US_ASCII));
            o.write(data);
        }
        return f;
    }

    /** Scatters "villages" over plains-like cells so structure searching has something to find. */
    public static void writeVillages(File f, BiomeGrid g, long seed) throws IOException {
        Random rnd = new Random(seed);
        StringBuilder sb = new StringBuilder("STRUCTURES1 village " + seed + "\n");
        int placed = 0;
        for (int tries = 0; tries < 40000 && placed < 600; tries++) {
            int col = rnd.nextInt(g.cols()), row = rnd.nextInt(g.cols());
            String b = g.nameAt(col, row);
            if (b.endsWith("plains") || b.endsWith("savanna") || b.endsWith("taiga") || b.endsWith("desert")) {
                sb.append(g.worldX(col) + rnd.nextInt(60)).append(' ').append(g.worldX(row) + rnd.nextInt(60)).append('\n');
                placed++;
            }
        }
        f.getParentFile().mkdirs();
        java.nio.file.Files.writeString(f.toPath(), sb.toString());
    }
}
