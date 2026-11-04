package slatefinder;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/**
 * A stand-in for the game's server, for tests: reads the same console commands, takes its time like the real one (a few
 * hundred chunks per save), and writes the world in the game's file format. Run it as a jar from the folder the app gives it.
 */
public final class FakeMcServer {
    public static void main(String[] a) throws Exception {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream("server.properties")) { p.load(in); }
        long seed = Long.parseLong(p.getProperty("level-seed", "0"));
        if (!new File("eula.txt").isFile() || !Files.readString(new File("eula.txt").toPath()).contains("eula=true")) { System.out.println("You need to agree to the EULA in order to run the server."); System.exit(1); }
        System.out.println("[Server thread/INFO]: Starting minecraft server version 1.21.11");
        if (p.getProperty("fake-crash") != null) { System.out.println("[main/ERROR]: Failed to start the minecraft server"); System.exit(1); }
        Thread.sleep(300);
        System.out.println("[Server thread/INFO]: Done (0.3s)! For help, type \"help\"");
        ExactTestWorld.Demo fn = new ExactTestWorld.Demo(seed);
        Deque<long[]> pending = new ArrayDeque<>();
        Map<Long, byte[]> done = new HashMap<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("forceload add ")) {
                String[] t = line.split(" ");
                int x1 = Integer.parseInt(t[2]), z1 = Integer.parseInt(t[3]), x2 = Integer.parseInt(t[4]), z2 = Integer.parseInt(t[5]);
                int cx1 = Math.floorDiv(x1, 16), cz1 = Math.floorDiv(z1, 16), cx2 = Math.floorDiv(x2, 16), cz2 = Math.floorDiv(z2, 16);
                if ((cx2 - cx1 + 1) * (cz2 - cz1 + 1) > 256) { System.out.println("[Server thread/INFO]: Too many chunks specified (maximum 256, specified " + ((cx2 - cx1 + 1) * (cz2 - cz1 + 1)) + ")"); continue; }
                for (int cz = cz1; cz <= cz2; cz++) for (int cx = cx1; cx <= cx2; cx++) pending.add(new long[]{cx, cz});
                System.out.println("[Server thread/INFO]: Marked chunks " + cx1 + ", " + cz1 + " to " + cx2 + ", " + cz2 + " in minecraft:overworld to be force loaded");
            } else if (line.equals("save-all flush")) {
                for (int i = 0; i < 260 && !pending.isEmpty(); i++) {
                    long[] c = pending.poll();
                    int cx = (int) c[0], cz = (int) c[1];
                    done.put(((long) cx << 32) ^ (cz & 0xFFFFFFFFL), ExactTestWorld.chunk(cx, cz, fn, "minecraft:full"));
                }
                // a chunk still being worked on is on disk too, but not finished
                if (!pending.isEmpty()) { long[] c = pending.peek(); done.put(((long) c[0] << 32) ^ (c[1] & 0xFFFFFFFFL), ExactTestWorld.chunk((int) c[0], (int) c[1], fn, "minecraft:structure_starts")); }
                ExactTestWorld.writeRegions(new File("world"), done);
                System.out.println("[Server thread/INFO]: Saved the game");
            } else if (line.equals("stop")) {
                System.out.println("[Server thread/INFO]: Stopping the server");
                System.exit(0);
            }
        }
    }
}
