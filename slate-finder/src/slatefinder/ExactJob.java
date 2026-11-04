package slatefinder;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Gets the real blocks around a spot: from the copy kept on disk if there is one, otherwise by having the game generate the
 * area from the seed, reading it, keeping a small copy and throwing the big world files away.
 */
final class ExactJob {
    interface Listener {
        void progress(double fraction, String text);
        void done(ExactPlace place, boolean fromDisk);
        void failed(String message, boolean cancelled);
    }

    /** Blocks each way from the marked spot: about 160 across, so only the part that matters is generated. */
    static final int DEFAULT_RADIUS = 80;

    /** For tests: a stand-in for the game's server jar. */
    static File testServer;

    private volatile boolean cancelled;
    private Thread thread;

    void cancel() { cancelled = true; }

    static int radius() { return Math.max(32, Math.min(480, Settings.getInt("exactSize", DEFAULT_RADIUS))); }

    static String fileName(long seed, int cx0, int cz0, int cw, int cd) { return seed + "_" + cx0 + "_" + cz0 + "_" + cw + "x" + cd + ".slx"; }

    /** A stored place of this seed that holds the area around (x, z), or null. The most recently used one wins. */
    static ExactPlace stored(long seed, int x, int z, int need) {
        List<ExactStore.Place> ps = ExactStore.places();
        for (int i = ps.size() - 1; i >= 0; i--) {
            ExactStore.Place p = ps.get(i);
            if (!p.file.getName().startsWith(seed + "_")) continue;
            try {
                ExactPlace pl = ExactPlace.load(p.file);
                if (pl.seed == seed && pl.covers(x, z, need)) { ExactStore.touch(p.file); return pl; }
            } catch (IOException | RuntimeException e) {
                p.file.delete();                // damaged: remove it, it will be made again
            }
        }
        return null;
    }

    /** Opens the copy on disk, or makes one. Runs in the background and reports on the listener from that thread. */
    void start(long seed, int x, int z, Listener l) {
        cancelled = false;
        thread = new Thread(() -> run(seed, x, z, l), "exact-world");
        thread.setDaemon(true);
        thread.start();
    }

    private void run(long seed, int x, int z, Listener l) {
        try {
            int r = radius();
            ExactPlace have = stored(seed, x, z, Math.min(r, 64));
            if (have != null) { l.done(have, true); return; }
            if (Runtime.version().feature() < 21) { l.failed("The game's server needs Java 21 or newer, and this app is running on Java " + Runtime.version().feature() + ".", false); return; }

            int cx0 = Math.floorDiv(x - r, 16), cz0 = Math.floorDiv(z - r, 16), cx1 = Math.floorDiv(x + r, 16), cz1 = Math.floorDiv(z + r, 16);
            int cw = cx1 - cx0 + 1, cd = cz1 - cz0 + 1;
            long chunks = (long) cw * cd;
            long need = chunks * 90 * 1024 + 150 * ExactStore.MB + (McServer.serverJar().isFile() ? 0 : 70 * ExactStore.MB);
            if (!ExactStore.makeRoom(need, null)) {
                l.failed("There is not enough room. This needs about " + ExactStore.human(need) + " free inside the storage limit (" + ExactStore.capGb() + " GB, " + ExactStore.human(ExactStore.usedBytes()) + " used) and on the disk. Raise the limit or delete map memory in Settings.", false);
                return;
            }
            File jar = testServer != null ? testServer : McServer.ensureServer((f, t) -> l.progress(f < 0 ? -1 : f * 0.12, t), () -> cancelled);
            File work = ExactStore.workDir();
            File world = McServer.generate(jar, McServer.javaBin(), seed, cx0, cz0, cx1, cz1, work, (f, t) -> l.progress(f < 0 ? -1 : 0.12 + f * 0.7, t), () -> cancelled);
            try {
                ExactPlace pl = ExactPlace.read(world, seed, cx0, cz0, cw, cd, (f, t) -> l.progress(0.82 + f * 0.15, t));
                if (pl.chunksAvailable() == 0) throw new IOException("the game wrote no finished chunks");
                l.progress(0.97, "Keeping a small copy…");
                pl.save(new File(ExactStore.placesDir(), fileName(seed, cx0, cz0, cw, cd)));
                l.done(pl, false);
            } finally {
                ExactStore.deleteTree(world.getParentFile());       // the big world files are not kept
            }
        } catch (IOException | RuntimeException e) {
            boolean c = cancelled || String.valueOf(e.getMessage()).contains("cancelled");
            l.failed(c ? "Stopped." : message(e), c);
        }
    }

    private static String message(Exception e) {
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException)
            return "Could not reach Mojang's download server (" + m + "). Check the internet connection and try again.";
        return m;
    }
}
