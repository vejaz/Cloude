package slatefinder;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import javax.imageio.ImageIO;

/** Checks the exact-world pieces against a made-up world written in the game's own format, and against a stand-in for the game's server. */
public final class ExactTest {
    static int fails = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { fails++; System.out.println("FAIL: " + what); } }

    static final int CX0 = -5, CZ0 = -5, CX1 = 10, CZ1 = 7;

    /** What the reader should say about a column, worked out straight from the world's own definition. */
    static void compare(ExactPlace pl, ExactTestWorld.Demo fn, String label) { compare(pl, fn, label, CX0, CZ0, CX1, CZ1); }

    static void compare(ExactPlace pl, ExactTestWorld.Demo fn, String label, int CX0, int CZ0, int CX1, int CZ1) {
        int bad = 0, cols = 0;
        for (int cz = CZ0; cz <= CZ1; cz++) for (int cx = CX0; cx <= CX1; cx++) {
            ExactPlace.Col[] ch = pl.cols[(cz - pl.cz0) * pl.cw + (cx - pl.cx0)];
            if (ch == null) { check(false, label + ": chunk " + cx + "," + cz + " missing"); continue; }
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                ExactPlace.Col c = ch[lz * 16 + lx];
                cols++;
                int yg = -65;
                for (int y = 319; y >= -64; y--) if (ExactBlocks.of(fn.block(x, y, z)).kind == ExactBlocks.GROUND) { yg = y; break; }
                String top = pl.blocks.get(c.top);
                boolean ok = c.ter == yg && top.equals(fn.block(x, yg, z));
                // the stack: everything above the ground except the sea (and air), lowest first
                List<String> want = new ArrayList<>();
                int lo = -1;
                for (int y = yg + 1; y <= 319; y++) {
                    String b = fn.block(x, y, z);
                    ExactBlocks.Info in = ExactBlocks.of(b);
                    boolean air = in.kind == ExactBlocks.AIR || (in.kind == ExactBlocks.WATER && y <= 62);
                    if (!air && lo < 0) lo = y;
                    if (lo >= 0) want.add(air ? "minecraft:air" : b);
                }
                while (!want.isEmpty() && want.get(want.size() - 1).equals("minecraft:air")) want.remove(want.size() - 1);
                List<String> got = new ArrayList<>();
                if (c.stack >= 0) for (short s : pl.stacks.get(c.stack)) got.add(pl.blocks.get(s));
                boolean plantOnly = !want.isEmpty() && want.size() <= 2 && lo == yg + 1;
                if (plantOnly) for (String b : want) if (!b.equals("minecraft:air") && !Blocks3D.plant(ExactBlocks.of(b).id)) plantOnly = false;
                if (plantOnly) {
                    ok &= c.stack < 0 && c.deco >= 0 && pl.blocks.get(c.deco).equals(want.get(0));
                } else {
                    ok &= got.equals(want) && (want.isEmpty() || c.gy == lo - 1);
                }
                boolean water = yg < 62 && ExactBlocks.of(fn.block(x, 62, z)).kind == ExactBlocks.WATER;
                ok &= ((c.flags & Terrain.F_WATER) != 0) == water;
                String biome = pl.biomes.get(c.biome);
                ok &= biome.equals(fn.biome((x >> 2) << 2, (yg >> 2) << 2, (z >> 2) << 2));
                if (!ok) { if (bad++ < 5) System.out.println(label + " mismatch at " + x + "," + z + ": ter " + c.ter + " vs " + yg + ", top " + top + ", stack " + got + " vs " + want + ", biome " + biome); }
            }
        }
        check(bad == 0, label + ": " + bad + " of " + cols + " columns differ from the world");
    }

    public static void main(String[] args) throws Exception {
        if (!DescribeTestWorld.sandboxed()) { System.out.println("Refusing to run: APPDATA must point inside the temp folder."); System.exit(3); }
        File tmp = Files.createTempDirectory("slate-exact").toFile();
        File outDir = args.length > 0 ? new File(args[0]) : tmp;
        outDir.mkdirs();
        ExactTestWorld.Demo fn = new ExactTestWorld.Demo(4242);

        // --- block names
        check(ExactBlocks.of("minecraft:grass_block").kind == ExactBlocks.GROUND && ExactBlocks.of("minecraft:grass_block").id == Blocks3D.GRASS, "grass block is ground");
        check(ExactBlocks.of("minecraft:coal_ore").kind == ExactBlocks.GROUND && ExactBlocks.of("minecraft:snow").kind == ExactBlocks.GROUND, "ores and snow layers count as ground");
        check(ExactBlocks.of("minecraft:oak_log").kind == ExactBlocks.STACK && ExactBlocks.of("minecraft:oak_log").id == Blocks3D.OAK_LOG, "oak log stands on the ground");
        check(ExactBlocks.of("minecraft:water").kind == ExactBlocks.WATER && ExactBlocks.of("minecraft:cave_air").kind == ExactBlocks.AIR, "water and air");
        check(ExactBlocks.of("minecraft:oak_stairs").id == Blocks3D.PLANKS && ExactBlocks.of("minecraft:oak_fence").id == Blocks3D.PLANKS, "oak stairs and fences are oak planks");
        check(ExactBlocks.of("minecraft:spruce_slab").id == Blocks3D.SPRUCE_PLANKS && ExactBlocks.of("minecraft:cobblestone_wall").id == Blocks3D.COBBLE && ExactBlocks.of("minecraft:stone_brick_stairs").id == Blocks3D.STONE_BRICK, "slabs, walls and stairs use what they are made of");
        check(ExactBlocks.of("minecraft:stripped_birch_log").id == Blocks3D.BIRCH_LOG && ExactBlocks.of("minecraft:azalea_leaves").id == Blocks3D.OAK_LEAVES, "stripped logs and azalea leaves");
        check(ExactBlocks.of("minecraft:orange_terracotta").id == Blocks3D.TERRA && ExactBlocks.of("minecraft:terracotta").id == Blocks3D.TERRA + 3, "terracotta bands");
        check(ExactBlocks.of("minecraft:tall_grass").id == Blocks3D.GRASS_TALL && Blocks3D.plant(ExactBlocks.of("minecraft:poppy").id), "plants");
        byte dyn = ExactBlocks.of("minecraft:amethyst_cluster").id;
        check((dyn & 255) >= Blocks3D.COUNT && Blocks3D.AVG[dyn & 255] != 0 && Blocks3D.TOP[dyn & 255] != null, "a block the table does not know gets its own coloured block");
        check(ExactBlocks.of("minecraft:amethyst_cluster").id == dyn, "and the same one every time");
        byte birchPlanks = ExactBlocks.of("minecraft:birch_stairs").id;
        check((birchPlanks & 255) >= Blocks3D.COUNT && ExactBlocks.of("minecraft:birch_planks").id == birchPlanks, "birch stairs and birch planks are one block");
        check(ExactBlocks.of("minecraft:lime_wool").id != ExactBlocks.of("minecraft:blue_wool").id, "colours of wool differ");

        // --- the chunk-by-chunk reader
        File world = new File(tmp, "world");
        long t0 = System.currentTimeMillis();
        ExactTestWorld.write(world, fn, CX0, CZ0, CX1, CZ1);
        System.out.println("wrote the made-up world in " + (System.currentTimeMillis() - t0) + " ms, " + ExactStore.size(world) / 1024 + " KB");
        File rd = Anvil.regionDir(world);
        check(rd != null && rd.isDirectory(), "region folder found");
        Anvil.Region r00 = new Anvil.Region(rd, 0, 0);
        check(r00.exists() && r00.has(1, 1) && !r00.has(31, 31), "region 0,0 has its chunks, and not the ones that are not there");
        Map<String, Object> ch = r00.chunk(1, 1);
        check(ch != null && "minecraft:full".equals(Nbt.str(ch.get("Status"))) && Nbt.list(ch.get("sections")).size() == 26 && Nbt.num(ch.get("xPos"), -999) == 1, "chunk NBT reads: status, 26 sections, position");
        long t1 = System.currentTimeMillis();
        ExactPlace pl = ExactPlace.read(world, 4242, CX0, CZ0, CX1 - CX0 + 1, CZ1 - CZ0 + 1, null);
        System.out.println("read " + pl.chunksAvailable() + " chunks in " + (System.currentTimeMillis() - t1) + " ms; " + pl.stacks.size() + " distinct stacks, " + pl.blocks.size() + " block names");
        check(pl.chunksAvailable() == (CX1 - CX0 + 1) * (CZ1 - CZ0 + 1), "every chunk read");
        compare(pl, fn, "from the region files");

        // specific things a person would look for
        ExactPlace.Col house = pl.cols[(1 - pl.cz0) * pl.cw + (1 - pl.cx0)][(30 - 16) * 16 + (30 - 16)];
        check(house.ter == 75 && pl.blocks.get(house.top).equals("minecraft:cobblestone"), "the house floor is cobblestone at y 75");
        ExactPlace.Col wall = pl.cols[(1 - pl.cz0) * pl.cw + (1 - pl.cx0)][(28 - 16) * 16 + (30 - 16)];
        check(wall.stack >= 0 && pl.stacks.get(wall.stack).length >= 4, "a wall column has its planks and roof as a stack");
        ExactPlace.Col door = pl.cols[(2 - pl.cz0) * pl.cw + (2 - pl.cx0)][(38 - 32) * 16 + (33 - 32)];
        check(door.stack >= 0 && pl.stacks.get(door.stack).length == 2 && door.gy == 77, "the door column starts above the doorway (gy " + door.gy + ")");
        ExactPlace.Col torch = pl.cols[(1 - pl.cz0) * pl.cw + (1 - pl.cx0)][(31 - 16) * 16 + (31 - 16)];
        short[] ts = torch.stack >= 0 ? pl.stacks.get(torch.stack) : new short[0];
        check(ts.length == 3 && pl.blocks.get(ts[0]).equals("minecraft:torch") && ts[1] == 0 && pl.blocks.get(ts[2]).equals("minecraft:oak_stairs"), "a torch under the roof keeps the air gap between them: " + Arrays.toString(ts));

        // --- saving and loading give the same thing back
        File saved = new File(tmp, "place.slx");
        pl.save(saved);
        ExactPlace back = ExactPlace.load(saved);
        System.out.println("saved place: " + saved.length() / 1024 + " KB for " + pl.chunksAvailable() + " chunks (the world files were " + ExactStore.size(world) / 1024 + " KB)");
        check(saved.length() < ExactStore.size(world), "the saved place is smaller than the world files");
        compare(back, fn, "after saving and loading");
        check(back.seed == 4242 && back.cx0 == CX0 && back.cw == pl.cw && back.stacks.size() == pl.stacks.size(), "header kept");
        try { Files.write(new File(tmp, "bad.slx").toPath(), new byte[]{1, 2, 3, 4}); ExactPlace.load(new File(tmp, "bad.slx")); check(false, "a damaged file is refused"); } catch (IOException e) { check(true, "a damaged file is refused"); }

        // --- into the 3D world
        Terrain world3 = new Terrain(Terrain.scene(new String[]{"plains", "desert"}, (x, z) -> 0), 5);
        back.bind(world3);
        world3.attach(back);
        int wrong = 0, n = 0;
        for (int x = -80; x < 170; x += 3) for (int z = -80; z < 120; z += 3) {
            int yg = -65;
            for (int y = 319; y >= -64; y--) if (ExactBlocks.of(fn.block(x, y, z)).kind == ExactBlocks.GROUND) { yg = y; break; }
            int top = world3.topAt(x, z);
            n++;
            if (top < yg) wrong++;                                              // the column is never lower than its ground
            if (world3.groundAt(x, z) != yg) wrong++;
        }
        check(wrong == 0, "terrain heights match the world's ground at " + n + " sample points (" + wrong + " wrong)");
        check(world3.topAt(33, 33) >= 78, "the house roof stands above the floor");
        check(world3.topAt(0, 5000) != Integer.MIN_VALUE, "outside the place, the generated terrain still answers");
        check(world3.biomeName(10, 10).equals("plains") && world3.biomeName(130, 10).equals("desert"), "biomes come from the world: " + world3.biomeName(10, 10) + ", " + world3.biomeName(130, 10));

        // --- drawing it
        Caster cs = new Caster(world3);
        for (int mode = 0; mode < 2; mode++) {
            RealTextures.close();
            BufferedImage im = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);
            int[] px = new int[960 * 540];
            Caster.Cam cam = new Caster.Cam();
            cam.x = 33 + 34; cam.z = 33 - 30; cam.y = 98; cam.yaw = Math.atan2(33 - cam.x, 33 - cam.z); cam.pitch = Math.toRadians(-24); cam.fov = Math.toRadians(70);
            Caster.Opts o = new Caster.Opts();
            o.maxDist = 400; o.sunElev = 50;
            long t2 = System.currentTimeMillis();
            cs.render3D(px, 960, 540, 1, cam, o, true);
            im.setRGB(0, 0, 960, 540, px, 0, 960);
            ImageIO.write(im, "png", new File(outDir, "exact-3d-" + mode + ".png"));
            System.out.println("3D picture in " + (System.currentTimeMillis() - t2) + " ms");
            // not all one colour, and not all sky
            Set<Integer> cols = new HashSet<>();
            for (int i = 0; i < px.length; i += 97) cols.add(px[i] & 0xFFFFFF);
            check(cols.size() > 60, "the picture has detail (" + cols.size() + " colours)");
            int[] map = new int[800 * 500];
            cs.renderMap(map, 800, 500, 40, 20, 0.4, o, false);
            BufferedImage mm = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
            mm.setRGB(0, 0, 800, 500, map, 0, 800);
            ImageIO.write(mm, "png", new File(outDir, "exact-map-" + mode + ".png"));
            double[] pk = cs.pick(cam, 960, 540, 480, 270, o);
            check(pk != null, "a click on the picture lands on something");
            break;
        }

        // --- the game's own pictures, when its jar is at hand (set MCJAR to the path of the client jar)
        String mcjar = System.getenv("MCJAR");
        if (mcjar != null && new File(mcjar).isFile()) {
            check(RealTextures.load(new File(mcjar)), "the game's jar opens");
            int[] g = RealTextures.pick(Blocks3D.GRASS & 255, true);
            check(g != null && g.length == 256, "grass top picture read");
            int[] oak = RealTextures.pick(Blocks3D.OAK_LOG & 255, false);
            check(oak != null && oak != RealTextures.pick(Blocks3D.OAK_LOG & 255, true), "oak log has different sides and top");
            check(RealTextures.pick(Blocks3D.GRASS & 255, false) != null && RealTextures.avg(Blocks3D.STONE & 255) != 0, "side of grass and the average colour of stone");
            int found = 0, total = 0;
            for (int id = 1; id < Blocks3D.COUNT; id++) { total++; if (RealTextures.pick(id, true) != null) found++; }
            System.out.println("pictures found for " + found + " of " + (total) + " built-in blocks");
            check(found >= 85, "most built-in blocks have a real picture (" + found + ")");
            int dynFound = 0, dynTotal = 0;
            for (Map.Entry<String, Integer> e : Blocks3D.dynamic().entrySet()) { dynTotal++; if (RealTextures.pick(e.getValue(), true) != null) dynFound++; }
            System.out.println("pictures found for " + dynFound + " of " + dynTotal + " added blocks " + Blocks3D.dynamic().keySet());
            world3.exactTextures = true;
            BufferedImage im = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);
            int[] px = new int[960 * 540];
            Caster.Cam cam = new Caster.Cam();
            cam.x = 33 + 34; cam.z = 33 - 30; cam.y = 98; cam.yaw = Math.atan2(33 - cam.x, 33 - cam.z); cam.pitch = Math.toRadians(-24); cam.fov = Math.toRadians(70);
            Caster.Opts o = new Caster.Opts();
            o.maxDist = 400; o.sunElev = 50;
            cs.render3D(px, 960, 540, 1, cam, o, true);
            im.setRGB(0, 0, 960, 540, px, 0, 960);
            ImageIO.write(im, "png", new File(outDir, "exact-3d-real.png"));
            cam.x = 33 + 12; cam.z = 33 - 12; cam.y = 80; cam.yaw = Math.atan2(33 - cam.x, 33 - cam.z); cam.pitch = Math.toRadians(-12); cam.fov = Math.toRadians(70);
            cs.render3D(px, 960, 540, 1, cam, o, true);
            im.setRGB(0, 0, 960, 540, px, 0, 960);
            ImageIO.write(im, "png", new File(outDir, "exact-3d-real-close.png"));
            int[] map = new int[800 * 500];
            cs.renderMap(map, 800, 500, 40, 20, 0.4, o, false);
            BufferedImage mm = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
            mm.setRGB(0, 0, 800, 500, map, 0, 800);
            ImageIO.write(mm, "png", new File(outDir, "exact-map-real.png"));
            RealTextures.close();
        }

        // --- what the commands ask the server for
        for (int[] a : new int[][]{{-5, -5, 10, 7}, {0, 0, 0, 0}, {-17, 3, 40, 70}, {5, 5, 20, 20}}) {
            List<String> cmds = McServer.forceloadCommands(a[0], a[1], a[2], a[3]);
            boolean[][] seen = new boolean[a[2] - a[0] + 1][a[3] - a[1] + 1];
            boolean tooBig = false, overlap = false;
            for (String c : cmds) {
                String[] t = c.split(" ");
                int x1 = Math.floorDiv(Integer.parseInt(t[2]), 16), z1 = Math.floorDiv(Integer.parseInt(t[3]), 16), x2 = Math.floorDiv(Integer.parseInt(t[4]), 16), z2 = Math.floorDiv(Integer.parseInt(t[5]), 16);
                if ((x2 - x1 + 1) * (z2 - z1 + 1) > 256) tooBig = true;
                for (int z = z1; z <= z2; z++) for (int x = x1; x <= x2; x++) { if (seen[x - a[0]][z - a[1]]) overlap = true; seen[x - a[0]][z - a[1]] = true; }
            }
            boolean all = true;
            for (boolean[] col : seen) for (boolean b : col) if (!b) all = false;
            check(!tooBig && !overlap && all, "forceload commands for " + Arrays.toString(a) + " cover the area exactly, in pieces of at most 256 chunks (" + cmds.size() + " commands)");
        }

        // --- the storage
        ExactStore.deleteTree(ExactStore.root());
        check(ExactStore.placesBytes() == 0, "nothing stored to start with");
        ExactStore.placesDir().mkdirs();
        File[] ps = new File[4];
        for (int i = 0; i < ps.length; i++) {
            ps[i] = new File(ExactStore.placesDir(), "p" + i + ".slx");
            pl.save(ps[i]);
            ps[i].setLastModified(System.currentTimeMillis() - (ps.length - i) * 100000L);
        }
        long one = ps[0].length();
        check(ExactStore.places().size() == 4 && ExactStore.places().get(0).file.equals(ps[0]), "places are listed oldest first");
        check(ExactStore.placesBytes() >= 4 * one, "usage counts the places");
        Settings.set("storageCapGb", 1);
        check(ExactStore.capGb() == 1 && ExactStore.capBytes() == ExactStore.GB, "the limit is what was chosen");
        check(ExactStore.makeRoom(10 * ExactStore.MB, null) && ExactStore.places().size() == 4, "with room, nothing is deleted");
        check(ExactStore.makeRoom(ExactStore.capBytes() - ExactStore.usedBytes() + one * 2 - one / 2, null) && ExactStore.places().size() < 4 && ExactStore.places().size() >= 1, "when full, the places used longest ago go first (" + ExactStore.places().size() + " left)");
        check(!ExactStore.places().isEmpty() && !ExactStore.places().stream().anyMatch(p -> p.file.equals(ps[0])), "the oldest one was deleted, not the newest");
        check(!ExactStore.makeRoom(5 * ExactStore.GB, null), "something bigger than the whole limit cannot be made room for");
        long freed = ExactStore.deleteMaps();
        check(ExactStore.placesBytes() == 0 && freed > 0, "deleting the map memory empties it (freed " + ExactStore.human(freed) + ")");
        Settings.set("storageCapGb", 10);
        check(ExactStore.human(1536 * ExactStore.MB).equals("1.5 GB") && ExactStore.human(300 * ExactStore.MB).equals("300 MB"), "sizes read well");

        // --- the stand-in server, end to end: start, load the chunks, wait until they are written, stop
        File fake = new File(tmp, "fake-server.jar");
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "slatefinder.FakeMcServer");
        try (JarOutputStream jo = new JarOutputStream(new FileOutputStream(fake), mf)) {
            for (String cn : new String[]{"FakeMcServer", "ExactTestWorld", "ExactTestWorld$Fn", "ExactTestWorld$Demo", "ExactTestWorld$W"}) {
                try (InputStream in = ExactTest.class.getResourceAsStream("/slatefinder/" + cn + ".class")) {
                    jo.putNextEntry(new JarEntry("slatefinder/" + cn + ".class"));
                    jo.write(in.readAllBytes());
                    jo.closeEntry();
                }
            }
        }
        File work = new File(tmp, "work");
        List<Double> fr = new ArrayList<>();
        long t3 = System.currentTimeMillis();
        File w2 = McServer.generate(fake, McServer.javaBin(), 4242, CX0, CZ0, 15, 15, work, (f, t) -> fr.add(f), () -> false);
        System.out.println("fake server: " + (System.currentTimeMillis() - t3) + " ms, " + fr.size() + " progress reports");
        check(w2.isDirectory() && Anvil.regionDir(w2) != null, "the server's world folder is returned");
        check(fr.size() >= 3 && fr.get(fr.size() - 1) >= 0.99, "progress was reported up to the end (" + fr.size() + " reports)");
        ExactPlace viaServer = ExactPlace.read(w2, 4242, CX0, CZ0, 15 - CX0 + 1, 15 - CZ0 + 1, null);
        check(viaServer.chunksAvailable() == (15 - CX0 + 1) * (15 - CZ0 + 1), "all the chunks the server was asked for arrived, finished");
        compare(viaServer, fn, "via the stand-in server", CX0, CZ0, 15, 15);
        check(Files.readString(new File(w2.getParentFile(), "eula.txt").toPath()).contains("eula=true") && Files.readString(new File(w2.getParentFile(), "server.properties").toPath()).contains("level-seed=4242"), "the seed and the agreement were written for the server");
        check(Files.readString(new File(w2.getParentFile(), "server.properties").toPath()).contains("server-ip=127.0.0.1"), "the server listens only on this computer");
        ExactStore.deleteTree(work);

        // cancelling stops the server and leaves nothing behind
        final int[] calls = {0};
        try {
            McServer.generate(fake, McServer.javaBin(), 4242, CX0, CZ0, CX1, CZ1, work, (f, t) -> calls[0]++, () -> calls[0] >= 1);
            check(false, "cancel stops it");
        } catch (IOException e) {
            check(e.getMessage().contains("cancelled"), "cancel stops it: " + e.getMessage());
        }
        File[] left = work.listFiles();
        check(left == null || left.length == 0, "a cancelled job leaves no files behind");

        // a server that cannot start says why
        File crash = new File(tmp, "crash-server.jar");
        Files.copy(fake.toPath(), crash.toPath());
        try {
            // the stand-in reads this property from server.properties, which generate() writes: add it through a wrapper
            File wrap = new File(tmp, "crash.jar");
            Manifest m2 = new Manifest();
            m2.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            m2.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "slatefinder.CrashStart");
            try (JarOutputStream jo = new JarOutputStream(new FileOutputStream(wrap), m2)) {
                jo.putNextEntry(new JarEntry("slatefinder/CrashStart.class"));
                try (InputStream in = ExactTest.class.getResourceAsStream("/slatefinder/CrashStart.class")) { jo.write(in.readAllBytes()); }
                jo.closeEntry();
            }
            McServer.generate(wrap, McServer.javaBin(), 1, 0, 0, 1, 1, work, null, () -> false);
            check(false, "a server that dies says so");
        } catch (IOException e) {
            check(e.getMessage().contains("stopped") && e.getMessage().contains("Failed to start"), "a server that dies says so, with its last words: " + e.getMessage());
        }
        // --- the whole job: nothing on disk -> generate, read, keep a small copy, delete the big files; then the second time it opens the copy
        ExactStore.deleteTree(ExactStore.root());
        ExactJob.testServer = fake;
        Settings.set("exactSize", 96);
        for (int round = 0; round < 3; round++) {
            final Object[] res = new Object[3];
            final List<String> says = new ArrayList<>();
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            long t5 = System.currentTimeMillis();
            ExactJob job = new ExactJob();
            job.start(777, 30, 30, new ExactJob.Listener() {
                public void progress(double f, String t) { synchronized (says) { says.add(t); } }
                public void done(ExactPlace p, boolean fromDisk) { res[0] = p; res[1] = fromDisk; latch.countDown(); }
                public void failed(String m, boolean c) { res[2] = m; latch.countDown(); }
            });
            if (round == 2) { Thread.sleep(150); job.cancel(); }
            check(latch.await(120, java.util.concurrent.TimeUnit.SECONDS), "job " + round + " finishes");
            if (round == 0) {
                check(res[0] != null && !(Boolean) res[1], "first time: made from the seed (" + res[2] + ")");
                ExactPlace jp = (ExactPlace) res[0];
                check(jp != null && jp.covers(30, 30, 96) && jp.seed == 777, "the place holds the area around the spot");
                if (jp != null) {
                    ExactTestWorld.Demo fn7 = new ExactTestWorld.Demo(777);
                    int okc = 0, allc = 0;
                    for (int cz = jp.cz0; cz < jp.cz0 + jp.cd; cz++) for (int cx = jp.cx0; cx < jp.cx0 + jp.cw; cx++) {
                        ExactPlace.Col[] cc = jp.cols[(cz - jp.cz0) * jp.cw + (cx - jp.cx0)];
                        allc++;
                        if (cc != null) { int yg = -65; int x = cx * 16 + 5, z = cz * 16 + 7; for (int y = 319; y >= -64; y--) if (ExactBlocks.of(fn7.block(x, y, z)).kind == ExactBlocks.GROUND) { yg = y; break; } if (cc[7 * 16 + 5].ter == yg) okc++; }
                    }
                    check(okc == allc, "the job's place matches the seed's world (" + okc + " of " + allc + " sample columns)");
                }
                File[] kept = ExactStore.placesDir().listFiles();
                check(kept != null && kept.length == 1 && kept[0].getName().startsWith("777_"), "one small copy was kept");
                File[] wk = ExactStore.workDir().listFiles();
                check(wk == null || wk.length == 0, "the big world files were deleted");
                System.out.println("job: " + (System.currentTimeMillis() - t5) + " ms, kept " + ExactStore.human(kept[0].length()) + ", " + says.size() + " messages");
            } else if (round == 1) {
                check(res[0] != null && (Boolean) res[1], "second time: opened from the copy on disk, no server needed");
                check(System.currentTimeMillis() - t5 < 5000, "and quickly (" + (System.currentTimeMillis() - t5) + " ms)");
            } else {
                // a different place far away, cancelled right away
                check(res[0] != null || (res[2] != null), "a cancelled or finished job reports back");
            }
        }
        ExactJob.testServer = null;
        Settings.set("exactSize", ExactJob.DEFAULT_RADIUS);
        ExactStore.deleteTree(tmp);
        System.out.println(checks + " checks, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
