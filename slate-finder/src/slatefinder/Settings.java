package slatefinder;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** The Describing tab's settings, kept in %APPDATA%\SlateFinder\describe-settings.properties, and the colours they choose. */
final class Settings {
    private Settings() {}

    private static final Properties P = new Properties();
    private static final List<Runnable> LISTENERS = new ArrayList<>();
    private static boolean loaded;

    static File file() {
        String ad = System.getenv("APPDATA");
        File dir = ad != null ? new File(ad, "SlateFinder") : new File(".");
        return new File(dir, "describe-settings.properties");
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        File f = file();
        if (f.isFile()) try (FileInputStream in = new FileInputStream(f)) { P.load(in); } catch (IOException ignored) { }
    }

    static synchronized String get(String key, String def) { load(); return P.getProperty(key, def); }
    static int getInt(String key, int def) { try { return Integer.parseInt(get(key, "" + def)); } catch (NumberFormatException e) { return def; } }
    static boolean getBool(String key, boolean def) { return Boolean.parseBoolean(get(key, "" + def)); }

    static synchronized void set(String key, String value) {
        load();
        if (value.equals(P.getProperty(key))) return;
        P.setProperty(key, value);
        File f = file();
        try {
            f.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) { P.store(out, "Slate Finder - Describing tab"); }
        } catch (IOException ignored) { }
        for (Runnable r : new ArrayList<>(LISTENERS)) r.run();
    }

    static void set(String key, int value) { set(key, "" + value); }
    static void set(String key, boolean value) { set(key, "" + value); }

    static void onChange(Runnable r) { LISTENERS.add(r); }

    static String player() { String p = get("player", "vejasss").trim(); return p.isEmpty() ? "vejasss" : p; }
}

/** The accent colours. Everything the Describing tab draws in the accent reads it from here. */
final class Skin {
    private Skin() {}

    static final String[] NAMES = {"Cyan", "Green", "Violet", "Orange", "Pink", "Blue", "Gold"};
    private static final int[][] COLOURS = {
        {0x4FD1E8, 0x1B6473}, {0x4ADE80, 0x1B7A47}, {0xA78BFA, 0x5B3FB0}, {0xFB923C, 0x9A4A12}, {0xF472B6, 0x9A2E68}, {0x60A5FA, 0x1F4F9A}, {0xFACC15, 0x8A6A0A}};

    static int index() {
        String n = Settings.get("accent", "Cyan");
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equalsIgnoreCase(n)) return i;
        return 0;
    }

    static Color accent() { return new Color(COLOURS[index()][0]); }
    static Color accentDark() { return new Color(COLOURS[index()][1]); }
    static Color on() { return new Color(0x06141A); }
}
