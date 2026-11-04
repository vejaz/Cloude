package slatefinder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Everything the app keeps on disk for the maps, in one place: the exact places, the downloaded Minecraft server, the 3D
 * pictures. It knows how much that is, keeps it under the limit the person chose, and can empty it.
 */
final class ExactStore {
    private ExactStore() {}

    static final long MB = 1024L * 1024, GB = 1024L * MB;

    static File appDir() {
        String ad = System.getenv("APPDATA");
        return ad != null ? new File(ad, "SlateFinder") : new File(".", "SlateFinder");
    }

    static File root() { return new File(appDir(), "exact"); }
    static File placesDir() { return new File(root(), "places"); }
    static File serverDir() { return new File(root(), "server"); }
    static File workDir() { return new File(root(), "work"); }

    // ------------------------------------------------------------------ the limit

    static int capGb() { return Math.max(1, Math.min(2000, Settings.getInt("storageCapGb", 10))); }
    static long capBytes() { return capGb() * GB; }
    static void setCapGb(int gb) { Settings.set("storageCapGb", Math.max(1, Math.min(2000, gb))); }

    // ------------------------------------------------------------------ what is stored

    static long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        try (Stream<Path> s = Files.walk(f.toPath())) {
            return s.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    static long placesBytes() { return size(placesDir()) + size(workDir()); }
    static long serverBytes() { return size(serverDir()); }

    static long picturesBytes() {
        long t = 0;
        File[] fs = appDir().listFiles(f -> f.isDirectory() && f.getName().startsWith("previews3d"));
        if (fs != null) for (File f : fs) t += size(f);
        return t;
    }

    static long usedBytes() { return placesBytes() + serverBytes() + picturesBytes(); }

    static final class Place {
        final File file;
        final long bytes, lastUsed;
        Place(File f) { this.file = f; this.bytes = f.length(); this.lastUsed = f.lastModified(); }
    }

    static List<Place> places() {
        List<Place> out = new ArrayList<>();
        File[] fs = placesDir().listFiles((d, n) -> n.endsWith(".slx"));
        if (fs != null) for (File f : fs) out.add(new Place(f));
        out.sort(Comparator.comparingLong((Place p) -> p.lastUsed));
        return out;
    }

    static void touch(File f) { f.setLastModified(System.currentTimeMillis()); }

    // ------------------------------------------------------------------ keeping under the limit

    /**
     * Makes room for {@code need} more bytes by deleting the places not used for longest. Returns false if even
     * with every place gone it would not fit (the limit is too small, or the disk is too full).
     */
    static synchronized boolean makeRoom(long need, File keep) {
        long cap = capBytes();
        List<Place> ps = places();
        long used = usedBytes();
        long evictable = 0;
        for (Place p : ps) if (keep == null || !p.file.equals(keep)) evictable += p.bytes;
        // if it could not fit even with every place gone, delete nothing
        if (used - evictable + need > cap) return false;
        for (Place p : ps) {
            if (used + need <= cap) break;
            if (keep != null && p.file.equals(keep)) continue;
            if (p.file.delete()) used -= p.bytes;
        }
        return used + need <= cap && fitsOnDisk(need);
    }

    static boolean fitsOnDisk(long need) {
        File d = appDir();
        d.mkdirs();
        long free = d.getUsableSpace();
        return free <= 0 || free > need + 200 * MB;
    }

    // ------------------------------------------------------------------ emptying

    /** Deletes every stored place, any half-made work, and the 3D pictures (they are drawn again when needed). The downloaded server stays. */
    static synchronized long deleteMaps() {
        long before = placesBytes() + picturesBytes();
        deleteTree(placesDir());
        deleteTree(workDir());
        File[] fs = appDir().listFiles(f -> f.isDirectory() && f.getName().startsWith("previews3d"));
        if (fs != null) for (File f : fs) deleteTree(f);
        Previews3D.forget();
        return before - (placesBytes() + picturesBytes());
    }

    static synchronized long deleteServer() {
        long before = serverBytes();
        File[] fs = serverDir().listFiles();
        if (fs != null) for (File f : fs) deleteTree(f);
        RealTextures.close();
        return before - serverBytes();
    }

    static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        try (Stream<Path> s = Files.walk(f.toPath())) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException | RuntimeException ignored) { }
    }

    static String human(long bytes) {
        if (bytes >= GB) return String.format("%.1f GB", bytes / (double) GB);
        if (bytes >= MB) return String.format("%.0f MB", bytes / (double) MB);
        if (bytes >= 1024) return String.format("%.0f KB", bytes / 1024.0);
        return bytes + " B";
    }
}
