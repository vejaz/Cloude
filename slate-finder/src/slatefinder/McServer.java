package slatefinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes the game generate a piece of the world from the seed: downloads the game's own server (from Mojang, once), runs
 * it quietly in the background with that seed, asks it to load the chunks around a spot, waits until they are written to
 * disk, and stops it. What is left is an ordinary world folder that {@link ExactPlace} reads.
 */
final class McServer {
    private McServer() {}

    static final String VERSION = "1.21.11";
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    interface Progress { void update(double fraction, String text); }

    static File serverJar() { return new File(ExactStore.serverDir(), "server-" + VERSION + ".jar"); }

    /** The java that runs this app (the game's server needs 21 or newer, and so does this app). */
    static File javaBin() {
        File f = new File(System.getProperty("java.home"), "bin/" + (System.getProperty("os.name", "").toLowerCase().contains("win") ? "javaw.exe" : "java"));
        File alt = new File(System.getProperty("java.home"), "bin/java" + (System.getProperty("os.name", "").toLowerCase().contains("win") ? ".exe" : ""));
        return alt.isFile() ? alt : f;
    }

    // ------------------------------------------------------------------ downloading what is needed

    private static String fetchText(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "SlateFinder");
        try (InputStream in = c.getInputStream()) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }

    private static String field(String obj, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"?([^\",}\\s]+)\"?").matcher(obj);
        return m.find() ? m.group(1) : null;
    }

    /** Finds Mojang's download for this version: {url, sha1, size}. {@code which} is "server" or "client". */
    static String[] locate(String which, String manifest, java.util.function.Function<String, String> get) throws IOException {
        Matcher m = Pattern.compile("\\{[^{}]*\"id\"\\s*:\\s*\"" + Pattern.quote(VERSION) + "\"[^{}]*\\}").matcher(manifest);
        if (!m.find()) throw new IOException("Mojang's list has no Minecraft " + VERSION);
        String vurl = field(m.group(), "url");
        if (vurl == null) throw new IOException("Mojang's list has no address for Minecraft " + VERSION);
        String vjson = get.apply(vurl);
        int i = vjson.indexOf("\"" + which + "\"");
        if (i < 0) throw new IOException("Minecraft " + VERSION + " has no " + which + " download");
        int open = vjson.indexOf('{', i), close = vjson.indexOf('}', open);
        String obj = vjson.substring(open, close + 1);
        return new String[]{field(obj, "url"), field(obj, "sha1"), field(obj, "size")};
    }

    private static void download(String url, File to, String sha1, Progress p, String what, BooleanSupplier cancelled) throws IOException {
        to.getParentFile().mkdirs();
        File part = new File(to.getPath() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "SlateFinder");
        long total = c.getContentLengthLong();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[1 << 16];
                long got = 0;
                int n;
                long last = 0;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled.getAsBoolean()) throw new IOException("cancelled");
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    got += n;
                    long now = System.currentTimeMillis();
                    if (now - last > 200 && p != null) { last = now; p.update(total > 0 ? got / (double) total : -1, "Downloading " + what + "  " + ExactStore.human(got) + (total > 0 ? " of " + ExactStore.human(total) : "")); }
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            if (sha1 != null && !sb.toString().equalsIgnoreCase(sha1)) throw new IOException("the download of " + what + " is damaged (checksum differs); try again");
            Files.move(part.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        } finally {
            part.delete();
        }
    }

    /** Downloads the game's server from Mojang if it is not here yet. */
    static File ensureServer(Progress p, BooleanSupplier cancelled) throws IOException {
        File jar = serverJar();
        if (jar.isFile() && jar.length() > 10_000_000) return jar;
        if (p != null) p.update(-1, "Asking Mojang for Minecraft " + VERSION + "…");
        String[] loc = locate("server", fetchText(MANIFEST), u -> { try { return fetchText(u); } catch (IOException e) { throw new RuntimeException(e); } });
        download(loc[0], jar, loc[1], p, "the Minecraft server", cancelled);
        return jar;
    }

    // ------------------------------------------------------------------ running it

    static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
    }

    static void writeConfig(File dir, long seed, int port) throws IOException {
        dir.mkdirs();
        Files.writeString(new File(dir, "eula.txt").toPath(), "eula=true\n");
        String props = "level-seed=" + seed + "\nlevel-name=world\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port=" + port + "\nenable-query=false\nenable-rcon=false\nenable-status=false\n"
            + "motd=Slate Finder\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nallow-nether=false\nspawn-monsters=false\nspawn-animals=false\nspawn-npcs=false\n"
            + "generate-structures=true\nregion-file-compression=deflate\nmax-tick-time=-1\nsync-chunk-writes=false\nmax-players=1\ndifficulty=peaceful\ngamemode=creative\nlevel-type=minecraft\\:normal\n"
            + "use-native-transport=false\nplayer-idle-timeout=0\n";
        Files.writeString(new File(dir, "server.properties").toPath(), props);
    }

    /** The commands that load every chunk of the area: forceload takes at most 256 chunks at a time. */
    static java.util.List<String> forceloadCommands(int cx0, int cz0, int cx1, int cz1) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int z = cz0; z <= cz1; z += 16) for (int x = cx0; x <= cx1; x += 16) {
            int xe = Math.min(cx1, x + 15), ze = Math.min(cz1, z + 15);
            out.add("forceload add " + (x * 16) + " " + (z * 16) + " " + (xe * 16 + 15) + " " + (ze * 16 + 15));
        }
        return out;
    }

    /** Whether the chunk (cx, cz) is written, and finished (status "full"). */
    static boolean chunkDone(Map<Long, Anvil.Region> cache, File regionDir, int cx, int cz) {
        try {
            int rx = Math.floorDiv(cx, 32), rz = Math.floorDiv(cz, 32);
            long key = ((long) rx << 32) ^ (rz & 0xFFFFFFFFL);
            Anvil.Region r = cache.get(key);
            if (r == null) { r = new Anvil.Region(regionDir, rx, rz); cache.put(key, r); }
            Map<String, Object> ch = r.chunk(Math.floorMod(cx, 32), Math.floorMod(cz, 32));
            if (ch == null) return false;
            String st = Nbt.str(ch.get("Status"));
            return st != null && (st.equals("minecraft:full") || st.equals("full"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Generates the chunks cx0..cx1 by cz0..cz1 of the world with this seed and returns the world folder.
     * {@code server} is the game's server jar; {@code java} the java to run it with.
     */
    static File generate(File server, File java, long seed, int cx0, int cz0, int cx1, int cz1, File work, Progress p, BooleanSupplier cancelled) throws IOException {
        File dir = new File(work, "job-" + System.currentTimeMillis());
        int port = freePort();
        writeConfig(dir, seed, port);
        int total = (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
        ProcessBuilder pb = new ProcessBuilder(java.getPath(), "-Xms256M", "-Xmx2048M", "-Dfile.encoding=UTF-8", "-jar", server.getPath(), "nogui");
        pb.directory(dir);
        pb.redirectErrorStream(true);
        if (p != null) p.update(-1, "Starting Minecraft " + VERSION + " in the background…");
        Process proc = pb.start();
        Deque<String> tail = new ArrayDeque<>();
        CountDownLatch ready = new CountDownLatch(1);
        final Object saveLock = new Object();
        final int[] saves = {0};
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    synchronized (tail) { tail.add(line); if (tail.size() > 60) tail.poll(); }
                    if (line.contains("Done (") && line.contains("For help")) ready.countDown();
                    if (line.contains("Saved the game")) synchronized (saveLock) { saves[0]++; saveLock.notifyAll(); }
                }
            } catch (IOException ignored) { }
        }, "mc-server-log");
        reader.setDaemon(true);
        reader.start();
        OutputStream stdin = proc.getOutputStream();
        try {
            long t0 = System.currentTimeMillis();
            while (!ready.await(500, TimeUnit.MILLISECONDS)) {
                if (cancelled.getAsBoolean()) throw new IOException("cancelled");
                if (!proc.isAlive()) throw new IOException("the Minecraft server stopped while starting.\n" + lastLines(tail));
                if (System.currentTimeMillis() - t0 > 6 * 60_000) throw new IOException("the Minecraft server did not start within 6 minutes.\n" + lastLines(tail));
                if (p != null) p.update(-1, "Starting Minecraft " + VERSION + " in the background…  " + (System.currentTimeMillis() - t0) / 1000 + " s");
            }
            for (String cmd : forceloadCommands(cx0, cz0, cx1, cz1)) send(stdin, cmd);
            File regionDir = null;
            Set<Long> done = new HashSet<>();
            long t1 = System.currentTimeMillis();
            long lastDoneAt = t1;
            while (done.size() < total) {
                if (cancelled.getAsBoolean()) throw new IOException("cancelled");
                if (!proc.isAlive()) throw new IOException("the Minecraft server stopped early.\n" + lastLines(tail));
                int before;
                synchronized (saveLock) { before = saves[0]; }
                send(stdin, "save-all flush");
                synchronized (saveLock) {
                    long until = System.currentTimeMillis() + 90_000;
                    while (saves[0] == before && System.currentTimeMillis() < until && proc.isAlive()) saveLock.wait(500);
                }
                if (regionDir == null) regionDir = Anvil.regionDir(new File(dir, "world"));
                if (regionDir != null) {
                    Map<Long, Anvil.Region> cache = new java.util.HashMap<>();
                    for (int z = cz0; z <= cz1; z++) for (int x = cx0; x <= cx1; x++) {
                        long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
                        if (!done.contains(k) && chunkDone(cache, regionDir, x, z)) { done.add(k); lastDoneAt = System.currentTimeMillis(); }
                    }
                }
                if (p != null) p.update(done.size() / (double) total, "Generating the world from the seed…  " + done.size() + " of " + total + " chunks");
                if (done.size() >= total) break;
                if (System.currentTimeMillis() - lastDoneAt > 5 * 60_000) throw new IOException("the world stopped growing for 5 minutes.\n" + lastLines(tail));
                if (System.currentTimeMillis() - t1 > 40 * 60_000) throw new IOException("generating took longer than 40 minutes.\n" + lastLines(tail));
                Thread.sleep(2000);
            }
            if (p != null) p.update(1, "Saving…");
            send(stdin, "stop");
            if (!proc.waitFor(90, TimeUnit.SECONDS)) proc.destroyForcibly();
            File world = new File(dir, "world");
            if (Anvil.regionDir(world) == null) throw new IOException("the Minecraft server wrote no world.\n" + lastLines(tail));
            return world;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        } catch (IOException | RuntimeException e) {
            try { send(stdin, "stop"); } catch (IOException ignored) { }
            try { if (!proc.waitFor(15, TimeUnit.SECONDS)) proc.destroyForcibly(); } catch (InterruptedException ie) { proc.destroyForcibly(); }
            ExactStore.deleteTree(dir);
            throw e;
        } finally {
            if (proc.isAlive()) proc.destroyForcibly();
        }
    }

    private static void send(OutputStream stdin, String cmd) throws IOException {
        stdin.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    private static String lastLines(Deque<String> tail) {
        synchronized (tail) { return String.join("\n", tail); }
    }
}
