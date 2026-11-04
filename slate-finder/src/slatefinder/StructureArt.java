package slatefinder;

/** Painted pictures of the structures: each is a small scene of the building in its usual setting. */
final class StructureArt {
    private StructureArt() {}

    private static final int GRASS = 0xFF79B03A, DIRT = 0xFF866043, STONE = 0xFF7D7D7D;
    private static final int PLANK = 0xFFB8935A, DARK_PLANK = 0xFF4A3622, COBBLE = 0xFF7A7A78, MOSSY = 0xFF6A8A5A, BRICK = 0xFF7C7870, GLASS = 0xFFB8DCF0;

    static void draw(Art a, String id) {
        switch (id) {
            case "village": case "plains_village": village(a, "plains"); break;
            case "desert_village": village(a, "desert"); break;
            case "savanna_village": village(a, "savanna"); break;
            case "snowy_village": village(a, "snowy"); break;
            case "taiga_village": village(a, "taiga"); break;
            case "pillager_outpost": outpost(a); break;
            case "woodland_mansion": mansion(a); break;
            case "ocean_monument": monument(a); break;
            case "ocean_ruin": oceanRuin(a, "default"); break;
            case "warm_ocean_ruin": oceanRuin(a, "warm"); break;
            case "cold_ocean_ruin": oceanRuin(a, "cold"); break;
            case "shipwreck": case "ocean_shipwreck": shipwreck(a); break;
            case "beached_shipwreck": beachedShipwreck(a); break;
            case "buried_treasure": treasure(a); break;
            case "desert_pyramid": pyramid(a); break;
            case "jungle_temple": jungleTemple(a); break;
            case "igloo": igloo(a); break;
            case "swamp_hut": swampHut(a); break;
            case "mineshaft": mineshaft(a); break;
            case "stronghold": stronghold(a); break;
            case "ancient_city": ancientCity(a); break;
            case "trail_ruins": trailRuins(a); break;
            case "trial_chambers": trialChambers(a); break;
            case "ruined_portal": ruinedPortal(a); break;
            case "monster_room": monsterRoom(a); break;
            case "desert_well": desertWell(a); break;
            case "fossil": fossil(a); break;
            case "amethyst_geode": geode(a); break;
            case "nether_fortress": netherFortress(a); break;
            case "bastion_remnant": bastion(a); break;
            case "nether_fossil": netherFossil(a); break;
            case "end_city": endCity(a); break;
            default: a.fill(0xFF404050);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void sky(Art a, int top, int bottom, boolean sun) {
        a.sky(top, bottom, 64);
        if (sun) a.sun(128, 16, 0xFFFFF2B0);
        a.clouds(3, 6, 26, 0xFFFFFFFF);
    }

    private static int[] flatGround(Art a, int y, int top, int sub) {
        int[] h = new int[Art.W];
        java.util.Arrays.fill(h, y);
        for (int x = 0; x < Art.W; x++) h[x] += (int) Math.round(Math.sin(x * 0.07 + a.seed % 7) * 1.2);
        a.land(h, top, sub, STONE, 6, 0.16);
        return h;
    }

    /** A little house: walls, a stepped roof, a door and windows. */
    private static void house(Art a, int x, int gy, int w, int h, int wall, int roof, int log) {
        a.tex(x, gy - h, w, h, wall, 0.2);
        a.rect(x, gy - h, 1, h, log); a.rect(x + w - 1, gy - h, 1, h, log); a.rect(x, gy - h, w, 1, log);
        for (int k = 0; k <= w / 2 + 1; k++) { a.tex(x - 1 + k, gy - h - 1 - k, w + 2 - 2 * k, 1, roof, 0.22); if (w + 2 - 2 * k <= 1) break; }
        a.rect(x + w / 2 - 1, gy - 6, 3, 6, DARK_PLANK);
        a.rect(x + 2, gy - h + 3, 3, 3, GLASS);
        if (w > 12) a.rect(x + w - 5, gy - h + 3, 3, 3, GLASS);
    }

    private static void villager(Art a, int x, int gy) {
        a.rect(x, gy - 7, 3, 3, 0xFFC8967A);
        a.px(x + 1, gy - 5, 0xFF8A5A3A);
        a.rect(x, gy - 4, 3, 4, 0xFF6A4A2A);
    }

    // ------------------------------------------------------------------ overworld

    private static void village(Art a, String type) {
        boolean desert = type.equals("desert"), savanna = type.equals("savanna"), snowy = type.equals("snowy"), taiga = type.equals("taiga");
        if (desert) { a.sky(0xFF74B4EA, 0xFFF4E9C6, 64); a.sun(128, 16, 0xFFFFE9A0); }
        else if (snowy) { a.sky(0xFFAFC2D6, 0xFFE4ECF4, 64); a.clouds(4, 6, 26, 0xFFF4F7FA); }
        else if (savanna) { a.sky(0xFF7EB4E0, 0xFFF4DCAA, 64); a.sun(128, 18, 0xFFFFE0A0); a.clouds(3, 8, 24, 0xFFFFF4E0); }
        else if (taiga) { a.sky(0xFF86B0D8, 0xFFD2E4F0, 64); a.clouds(4, 6, 28, 0xFFF4F8FA); }
        else sky(a, 0xFF72B0EA, 0xFFCFE6FA, true);
        a.farHills(a.heights(52, 5, 28, 1), desert ? 0xFFEAD7A0 : snowy ? 0xFFD8E3EE : savanna ? 0xFFCFC48A : taiga ? 0xFF557A60 : 0xFF9FCB86);
        int top = desert ? 0xFFF2DE96 : snowy ? 0xFFF2F6FA : savanna ? 0xFFB5AA4A : taiga ? 0xFF4F8A3A : GRASS;
        int sub = desert ? 0xFFD9C27F : snowy ? 0xFFDCE6EE : DIRT;
        int[] h = flatGround(a, 70, top, sub);
        for (int x = 0; x < Art.W; x++) a.px(x, 70, Art.shade(desert ? 0xFFE0C070 : 0xFFB8A070, 0.9 + Art.hash(x, 1, a.seed) * 0.2));
        int wall, roof, log;
        if (desert) { wall = 0xFFE6CC84; roof = 0xFFD9B86A; log = 0xFFC8A458; }
        else if (savanna) { wall = 0xFFB5663A; roof = 0xFF8A4A2A; log = 0xFF6A3A22; }
        else if (snowy) { wall = 0xFF6A4A2E; roof = 0xFFF2F6FA; log = 0xFF4A3220; }
        else if (taiga) { wall = 0xFF5A4028; roof = 0xFF3F3A34; log = 0xFF3A2A1A; }
        else { wall = PLANK; roof = 0xFF7A5A3A; log = 0xFF4A3622; }
        if (desert) {
            // flat-roofed sandstone boxes
            for (int[] b : new int[][]{{8, 22, 14}, {52, 16, 11}, {108, 26, 17}}) {
                a.tex(b[0], 70 - b[2], b[1], b[2], wall, 0.2);
                a.rect(b[0] - 1, 70 - b[2] - 2, b[1] + 2, 2, roof);
                a.rect(b[0] + b[1] / 2 - 1, 64, 3, 6, DARK_PLANK);
                a.rect(b[0] + 3, 70 - b[2] + 3, 3, 3, GLASS);
            }
            a.cactus(34, 70, 6); a.cactus(98, 70, 8); a.cactus(150, 70, 5);
        } else {
            house(a, 8, 70, 22, 14, wall, roof, log);
            house(a, 52, 70, 16, 11, taiga ? 0xFF8A8A84 : 0xFFC8C0B0, snowy ? 0xFFF2F6FA : taiga ? 0xFF4A4A44 : 0xFF8A3A2A, log);
            house(a, 108, 70, 26, 17, wall, roof, log);
        }
        // well
        a.tex(84, 63, 10, 7, COBBLE, 0.3); a.rect(85, 64, 8, 2, 0xFF3F76E4); a.rect(84, 56, 1, 7, DARK_PLANK); a.rect(93, 56, 1, 7, DARK_PLANK); a.tex(83, 54, 12, 2, 0xFF7A5A3A, 0.2);
        if (!desert && !snowy) { a.rect(34, 68, 14, 2, 0xFF5A3A22); for (int x = 34; x < 48; x += 2) { a.px(x, 67, 0xFFE8D040); a.px(x, 66, 0xFFE8D040); } }
        villager(a, 74, 70); villager(a, 100, 70);
        if (savanna) { a.acacia(146, 70, 12, 0xFF7A9A2E); a.acacia(36, 70, 10, 0xFF7A9A2E); }
        else if (snowy || taiga) { a.spruce(146, 70, 22, 0xFF2F5A3A, snowy); a.spruce(40, 70, 20, 0xFF2F5A3A, snowy); }
        else if (!desert) { a.oak(144, 70, 10, 0xFF4F9A32); }
        if (!desert && !snowy) for (int i2 = 0; i2 < 20; i2++) { int x = a.rnd.nextInt(Art.W); a.grassTuft(x, 70, savanna ? 0xFFC4B85A : 0xFF5E9B2E); }
    }

    private static void outpost(Art a) {
        sky(a, 0xFF86AEDA, 0xFFE0E4D8, true);
        a.farHills(a.heights(54, 5, 26, 1), 0xFFA8C0A0);
        int[] h = flatGround(a, 72, 0xFF8FB050, DIRT);
        // dark oak watchtower
        int x = 62;
        a.tex(x, 22, 34, 6, 0xFF3B2A18, 0.2);
        a.tex(x + 3, 28, 28, 30, 0xFF4A3622, 0.25);
        for (int j = 28; j < 58; j += 6) a.rect(x + 3, j, 28, 1, 0xFF2A1E10);
        a.tex(x + 8, 58, 18, 14, COBBLE, 0.25);
        a.rect(x + 14, 62, 6, 10, 0xFF2A1E10);
        a.rect(x + 8, 33, 5, 5, 0xFF1A1208); a.rect(x + 21, 33, 5, 5, 0xFF1A1208);
        // crenellations and banner
        for (int i = 0; i < 34; i += 4) a.rect(x + i, 18, 3, 4, 0xFF3B2A18);
        a.rect(x + 17, 6, 1, 14, 0xFF2A1E10); a.rect(x + 18, 6, 8, 6, 0xFF1F2F5A); a.rect(x + 19, 7, 6, 1, 0xFF8AA0E0); a.rect(x + 20, 9, 4, 1, 0xFF8AA0E0);
        // scaffolding & cage
        a.tex(x + 38, 52, 10, 20, 0xFF6A4A2A, 0.2); a.rect(x + 40, 54, 6, 6, 0xFF8A8A92);
        // pillager
        a.rect(30, 66, 3, 6, 0xFF6A6A72); a.rect(30, 63, 3, 3, 0xFF8A9A8A); a.px(31, 64, 0xFF202020);
        a.rect(110, 66, 3, 6, 0xFF6A6A72); a.rect(110, 63, 3, 3, 0xFF8A9A8A);
        for (int i = 0; i < 12; i++) { int xx = a.rnd.nextInt(Art.W); a.grassTuft(xx, 72, 0xFF7A9A3A); }
    }

    private static void mansion(Art a) {
        a.sky(0xFF4A5A6A, 0xFF8A9AA6, 60);
        a.farHills(a.heights(48, 6, 24, 1), 0xFF3F5A44);
        int[] h = flatGround(a, 74, 0xFF3F6A2A, DIRT);
        for (int k = 0; k < 7; k++) { int x = 6 + k * 24; if (x > 40 && x < 116) continue; a.darkOak(x, 74, 18, 0xFF1F4A1A); }
        // big mansion
        a.tex(40, 34, 80, 40, 0xFF5A4A3A, 0.25);          // cobblestone lower
        a.tex(40, 34, 80, 8, 0xFF3B2A18, 0.2);            // dark oak storey
        for (int k = 0; k <= 40; k++) a.tex(36 + k, 33 - k / 2, 88 - 2 * k, 1, 0xFF2A2218, 0.2);
        a.tex(70, 14, 20, 14, 0xFF4A3A2A, 0.2);
        for (int k = 0; k <= 10; k++) a.tex(68 + k, 13 - k / 2, 24 - 2 * k, 1, 0xFF2A2218, 0.2);
        for (int x = 46; x < 114; x += 12) { a.rect(x, 44, 5, 8, 0xFFB8DCF0); a.rect(x, 58, 5, 8, 0xFFB8DCF0); }
        a.rect(74, 56, 12, 18, 0xFF2A1E10);
        a.rect(78, 20, 4, 5, 0xFFB8DCF0);
        // illagers
        a.rect(30, 68, 3, 6, 0xFF6A6A72); a.rect(30, 65, 3, 3, 0xFF8A9A8A);
        a.rect(128, 68, 3, 6, 0xFF3A6A8A); a.rect(128, 65, 3, 3, 0xFF8A9A8A);
        a.fog(30, 90, 0xFF2A3A30, 0.18);
    }

    private static void underwater(Art a, int top, int bottom, int floorY, int floorColour) {
        a.sky(0xFF74B4EC, 0xFFD0E8FA, 14);
        a.sun(126, 6, 0xFFFFF0B0);
        a.gradient(14, Art.H, top, bottom);
        for (int x = 0; x < Art.W; x++) a.px(x, 14, Art.shade(top, 1.35));
        int[] fl = a.heights(floorY, 3, 30, 4);
        for (int x = 0; x < Art.W; x++) for (int y = fl[x]; y < Art.H; y++) a.px(x, y, Art.shade(Art.mix(floorColour, bottom, 0.25), 0.92 + Art.hash(x, y, a.seed) * 0.16));
    }

    private static void monument(Art a) {
        underwater(a, 0xFF2A5AB8, 0xFF10286A, 80, 0xFF6A6A78);
        int[][] rows = {{58, 24}, {50, 32}, {42, 40}, {34, 48}};
        int cx = 80;
        int[] prism = {0xFF5AA898, 0xFF4A9A8A, 0xFF62B8A8, 0xFF2A6A66};
        for (int i = 0; i < rows.length; i++) { int y = 28 + i * 12, w = 18 + i * 14; a.tex(cx - w, y, w * 2, 12, prism[i % 4], 0.35); for (int x = cx - w + 3; x < cx + w - 3; x += 8) a.rect(x, y + 4, 3, 3, 0xFFB8F0E8); }
        for (int x = cx - 30; x < cx + 30; x += 12) { a.rect(x, 22, 4, 8, 0xFF4A9A8A); }
        a.rect(cx - 4, 16, 8, 6, 0xFF2A6A66); a.rect(cx - 2, 14, 4, 2, 0xFFB8F0E8);
        a.rect(cx - 3, 80, 6, 2, 0xFFF0D040); a.rect(cx - 3, 78, 6, 2, 0xFFD8B030);
        for (int k = 0; k < 4; k++) { int x = 30 + k * 36; a.rect(x, 54, 3, 3, 0xFFD0FFF4); a.rect(x + 1, 55, 1, 1, 0xFFFFFFFF); }
        // guardian
        a.ellipse(36, 40, 6, 5, 0xFF4A8A78); a.rect(34, 39, 4, 3, 0xFFF0A030); a.px(36, 40, 0xFF101010);
        a.fog(14, Art.H, 0xFF10286A, 0.25);
    }

    private static void oceanRuin(Art a, String kind) {
        boolean warm = kind.equals("warm"), cold = kind.equals("cold");
        underwater(a, warm ? 0xFF3FD0D8 : cold ? 0xFF3A5A8A : 0xFF3F6FD0, warm ? 0xFF1F7AA8 : cold ? 0xFF1A2A50 : 0xFF1A2E78, 70, warm ? 0xFFEAD89A : cold ? 0xFF7A7A78 : 0xFFD8C890);
        int stone = warm ? 0xFFE6CC84 : BRICK, moss = warm ? 0xFFD2B872 : MOSSY;
        a.tex(24, 52, 8, 22, moss, 0.3); a.tex(46, 58, 8, 16, stone, 0.3); a.tex(24, 52, 40, 4, stone, 0.3);
        a.tex(70, 62, 30, 12, stone, 0.3); a.rect(80, 50, 4, 14, stone); a.tex(104, 56, 10, 18, moss, 0.3);
        a.rect(112, 52, 4, 4, 0xFFC8A860);
        if (warm) {
            int[] cs = {0xFFE0409A, 0xFFE06A30, 0xFFE8D040, 0xFF8040C8, 0xFF30B0E0};
            for (int k = 0; k < 9; k++) { int x = 8 + k * 17, ht = 4 + a.rnd.nextInt(7); for (int j2 = 0; j2 < ht; j2++) a.px(x, 74 - j2, cs[k % cs.length]); }
        } else for (int k = 0; k < 5; k++) { int x = 14 + k * 30; for (int j2 = 0; j2 < 12; j2++) a.px(x + (j2 / 4 % 2), 74 - j2, 0xFF3F9A32); }
        for (int k = 0; k < 4; k++) { int x = 20 + a.rnd.nextInt(120), y = 30 + a.rnd.nextInt(24); a.rect(x, y, 5, 2, warm ? 0xFFF08020 : 0xFF9AA0B8); a.px(x + 5, y - 1, warm ? 0xFFF08020 : 0xFF9AA0B8); }
    }

    private static void beachedShipwreck(Art a) {
        sky(a, 0xFF72B0EA, 0xFFD6EAFA, true);
        a.gradient(50, 62, 0xFF4A94DA, 0xFF3F7ACA);
        int[] h = new int[Art.W];
        for (int x = 0; x < Art.W; x++) h[x] = 60 + (int) (Math.max(0, x - 20) * 0.04);
        a.land(h, 0xFFF0E2A6, 0xFFE0CC8A, 0xFFD2BC7A, 40, 0.22);
        for (int x = 0; x < 30; x++) for (int y = 56; y < 62; y++) a.px(x, y, Art.mix(0xFF4A94DA, 0xFF3F7ACA, (y - 56) / 6.0));
        // a ship tipped on its side in the sand
        for (int j = 0; j < 16; j++) { int w = 50 - Math.abs(j - 6) * 3 - (j > 10 ? (j - 10) * 4 : 0); a.tex(44 + j, 52 + j / 2, Math.max(4, w), 1, j % 2 == 0 ? 0xFF6A4A2A : 0xFF7A5A3A, 0.3); }
        a.line(74, 56, 96, 30, DARK_PLANK); a.line(75, 56, 97, 30, DARK_PLANK);
        a.tex(88, 30, 12, 9, 0xFFD8D0C0, 0.25);
        a.rect(66, 58, 8, 5, 0xFF8A5A2A); a.rect(69, 59, 2, 2, 0xFFD0D0D0);
        for (int i = 0; i < 6; i++) { int x = 24 + i * 22; a.px(x, h[x] - 1, 0xFF6A4A2A); a.px(x + 1, h[x] - 2, 0xFF6A4A2A); }
        a.oak(144, h[144], 9, 0xFF4F9A32);
    }

    private static void shipwreck(Art a) {
        underwater(a, 0xFF3F6FD0, 0xFF1A2E78, 74, 0xFFD8C890);
        int[] hull = {0xFF6A4A2A, 0xFF7A5A3A};
        for (int j = 0; j < 14; j++) { int w = 52 - Math.abs(j - 5) * 2 - (j > 9 ? (j - 9) * 4 : 0); a.tex(54 - w / 2 + 20 + j / 2, 50 + j, w, 1, hull[j % 2], 0.3); }
        a.rect(60, 44, 40, 4, 0xFF8A6A3A);
        a.rect(80, 14, 3, 32, DARK_PLANK); a.rect(66, 22, 3, 24, DARK_PLANK);
        a.tex(74, 22, 16, 12, 0xFFD8D0C0, 0.2); a.rect(76, 20, 12, 2, 0xFFC8C0B0);
        a.rect(82, 44, 8, 6, 0xFF8A5A2A); a.rect(85, 46, 2, 2, 0xFFD0D0D0);
        for (int k = 0; k < 6; k++) { int x = 10 + k * 28; for (int j = 0; j < 10 + k % 3 * 4; j++) a.px(x + (j / 4 % 2), 78 - j, 0xFF3F9A32); }
        for (int k = 0; k < 3; k++) { int x = 20 + a.rnd.nextInt(120), y = 28 + a.rnd.nextInt(20); a.rect(x, y, 5, 2, 0xFF9AA0B8); a.px(x + 5, y - 1, 0xFF9AA0B8); }
    }

    private static void treasure(Art a) {
        sky(a, 0xFF72B0EA, 0xFFD6EAFA, true);
        a.gradient(50, 60, 0xFF4A94DA, 0xFF3F7ACA);
        int[] h = new int[Art.W];
        for (int x = 0; x < Art.W; x++) h[x] = 58 + (int) (Math.max(0, x - 40) * 0.05);
        a.land(h, 0xFFF0E2A6, 0xFFE0CC8A, 0xFFD2BC7A, 40, 0.22);
        for (int x = 0; x < 40; x++) for (int y = 56; y < Art.H; y++) if (y < 58 + 0) a.px(x, y, 0xFF4A94DA);
        // buried chest cutaway
        a.tex(60, 70, 14, 10, 0xFF5A4A2A, 0.2);
        a.tex(62, 72, 10, 7, 0xFF8A5A2A, 0.25); a.rect(62, 74, 10, 1, 0xFF4A3220); a.rect(66, 74, 2, 2, 0xFFD0D0D0);
        for (int j = 0; j < 3; j++) a.px(68 + j, 70 + j - 8 + 4, 0);
        // red X on the surface
        for (int i = 0; i < 6; i++) { a.px(66 + i, 58 - 1, 0xFFE02020); a.px(71 - i, 58 - 1, 0xFFE02020); }
        a.rect(110, 62, 4, 14, 0xFF6A4A2A); a.px(111, 60, 0xFFE0D0A0);
        a.leafBlob(112, 56, 6, 4, 0xFF4F9A32, 0.3); a.oak(130, h[130], 8, 0xFF4F9A32);
        // dotted path to the chest
        for (int j = 0; j < 8; j++) a.px(66 + j * 0, 59 + j, a.rnd.nextInt(2) == 0 ? 0xFFC8B070 : 0xFFE8D890);
    }

    private static void pyramid(Art a) {
        a.sky(0xFF74B4EA, 0xFFF4E9C6, 60);
        a.sun(130, 18, 0xFFFFE9A0);
        a.farHills(a.heights(56, 4, 22, 1), 0xFFEAD7A0);
        int[] h = flatGround(a, 74, 0xFFF2DE96, 0xFFD9C27F);
        for (int x = 0; x < Art.W; x++) for (int y = h[x]; y < Art.H; y++) a.px(x, y, Art.shade(y - h[x] < 5 ? 0xFFF2DE96 : 0xFFD9C27F, 0.92 + Art.hash(x, y, a.seed) * 0.16));
        int cx = 80;
        for (int k = 0; k < 40; k++) {
            int w = 40 - k, y = 74 - k;
            int c = k % 8 == 2 ? 0xFFE8A04A : k % 8 == 3 ? 0xFF5A8AB8 : 0xFFE6CC84;
            a.tex(cx - w, y, w * 2 + 1, 1, c, 0.2);
        }
        a.rect(cx - 5, 64, 10, 10, 0xFF1A140C);
        a.cactus(24, 74, 7); a.cactus(136, 74, 5);
        for (int k = 0; k < 6; k++) { int x = a.rnd.nextInt(Art.W); a.px(x, 73, 0xFF7A5A2A); }
    }

    private static void jungleTemple(Art a) {
        a.sky(0xFF78C0E8, 0xFFD6F0D8, 60);
        a.farHills(a.heights(48, 6, 24, 1), 0xFF3F8A4A);
        int[] h = flatGround(a, 74, 0xFF4FA030, DIRT);
        for (int k = 0; k < 6; k++) { int x = 6 + k * 30; if (x > 44 && x < 116) continue; a.jungleTree(x, 74, 26, 0xFF2F8A22); }
        int cx = 80;
        for (int t = 0; t < 4; t++) { int w = 40 - t * 9, y = 74 - t * 12; a.tex(cx - w, y - 12, w * 2, 12, t % 2 == 0 ? MOSSY : COBBLE, 0.4); for (int i = -w + 3; i < w - 2; i += 6) a.px(cx + i, y - 11, 0xFF3F7A2A); }
        a.rect(cx - 4, 62, 8, 12, 0xFF1A2214);
        for (int k = 0; k < 9; k++) { int x = cx - 38 + k * 9; for (int j = 0; j < 3 + k % 4; j++) a.px(x, 62 + j, 0xFF3F8A22); }
        a.fog(55, 90, 0xFFBFE4C8, 0.1);
    }

    private static void igloo(Art a) {
        a.sky(0xFFAFC2D6, 0xFFE4ECF4, 62);
        a.clouds(4, 6, 26, 0xFFF4F7FA);
        a.farHills(a.heights(54, 4, 26, 1), 0xFFD8E3EE);
        int[] h = flatGround(a, 72, 0xFFF2F6FA, 0xFFDCE6EE);
        for (int x = 0; x < Art.W; x++) for (int y = h[x]; y < Art.H; y++) a.px(x, y, Art.shade(y - h[x] < 6 ? 0xFFF2F6FA : DIRT, 0.94 + Art.hash(x, y, a.seed) * 0.12));
        int cx = 78;
        for (int j = 0; j < 20; j++) { int w = (int) Math.sqrt(Math.max(0, 22 * 22 - (22 - j) * (22 - j) * 1.0)); a.tex(cx - w, 72 - j, w * 2, 1, 0xFFEDF4FA, 0.18); }
        for (int j = 0; j < 20; j += 3) for (int x = -22; x <= 22; x++) if (a.get(cx + x, 72 - j) != 0 && (x + j * 2) % 7 == 0) a.px(cx + x, 72 - j, 0xFFCFDBE6);
        a.rect(cx + 14, 62, 6, 10, 0xFF14202A); a.rect(cx + 10, 56, 4, 4, 0xFFC8E0F0);
        a.rect(cx + 20, 60, 2, 12, 0xFF6A4A2A); a.px(cx + 20, 59, 0xFFF0C030);
        a.spruce(14, 72, 22, 0xFF2F5A3A, true); a.spruce(144, 72, 20, 0xFF2F5A3A, true);
    }

    private static void swampHut(Art a) {
        a.sky(0xFF7A9A8A, 0xFFA6BCA8, 62);
        a.clouds(3, 8, 24, 0xFFB8C4B8);
        a.farHills(a.heights(54, 4, 26, 1), 0xFF6F8F78);
        int[] h = a.heights(72, 2, 34, 2);
        a.land(h, 0xFF5B7F3A, DIRT, STONE, 6, 0.16);
        a.water(70, 0xFF4A6A5A, 0xFF2A4A40, 0.9);
        a.fog(55, 90, 0xFF9DB4A4, 0.2);
        // stilts and hut
        for (int x : new int[]{54, 62, 96, 104}) a.rect(x, 62, 2, 12, 0xFF4A3622);
        a.tex(50, 58, 60, 4, 0xFF5A4630, 0.2);
        a.tex(54, 42, 52, 16, 0xFF6A5A3A, 0.25);
        a.stepped(80, 42, 30, 0xFF4A3A22, 0.25);
        a.rect(60, 46, 6, 6, 0xFF14181A); a.rect(94, 46, 6, 6, 0xFF14181A);
        a.rect(76, 48, 8, 10, 0xFF2A1E10);
        a.rect(98, 30, 8, 14, 0xFF4A4A4E); a.rect(98, 28, 8, 3, 0xFF3A3A3E);   // chimney
        a.rect(112, 52, 8, 6, 0xFF4A4A4E); a.rect(114, 50, 4, 2, 0xFF2A8A3A);  // cauldron
        a.rect(36, 56, 3, 6, 0xFF3A2A5A); a.rect(36, 53, 3, 3, 0xFF8AB07A); a.rect(35, 52, 5, 1, 0xFF2A1A4A);
        for (int i = 0; i < 10; i++) { int x = a.rnd.nextInt(Art.W); a.px(x, 70, 0xFF3E8A2E); a.px(x + 1, 70, 0xFF3E8A2E); }
        a.oak(14, 72, 10, 0xFF5A7A3A);
    }

    private static void cutaway(Art a, int stone, int cavity) {
        for (int y = 0; y < Art.H; y++) for (int x = 0; x < Art.W; x++) a.px(x, y, Art.shade(stone, 0.88 + Art.hash(x, y, a.seed) * 0.24));
    }

    private static void mineshaft(Art a) {
        cutaway(a, 0xFF6A6A68, 0);
        a.rect(0, 30, Art.W, 40, 0xFF1A140C);
        for (int x = 0; x < Art.W; x++) for (int y = 30; y < 70; y++) a.px(x, y, Art.shade(0xFF1E1810, 0.9 + Art.hash(x, y, a.seed) * 0.2));
        // supports
        for (int x = 10; x < Art.W; x += 36) { a.rect(x, 30, 3, 40, 0xFF8A6A3A); a.rect(x + 14, 30, 3, 40, 0xFF8A6A3A); a.rect(x - 1, 30, 18, 3, 0xFF9A7A44); }
        // floor planks + rails
        a.tex(0, 70, Art.W, 3, 0xFF8A6A3A, 0.3);
        a.rect(0, 68, Art.W, 1, 0xFF9A9AA4); a.rect(0, 66, Art.W, 1, 0xFF9A9AA4);
        for (int x = 2; x < Art.W; x += 5) a.rect(x, 67, 2, 3, 0xFF6A4A2A);
        // cobwebs
        for (int k = 0; k < 4; k++) { int x = 14 + k * 38; for (int i = 0; i < 8; i++) { a.px(x + i, 31 + i / 2, 0xFFD8D8D8); a.px(x - i + 8, 31 + i / 2, 0xFFD8D8D8); a.px(x + 4, 31 + i, 0xFFD8D8D8); } }
        // torch & chest cart
        a.rect(42, 50, 2, 6, 0xFF6A4A2A); a.rect(41, 47, 4, 3, 0xFFF0B030); a.px(42, 46, 0xFFFFE060);
        a.rect(100, 58, 14, 8, 0xFF7A7A84); a.rect(100, 56, 14, 2, 0xFF6A6A74); a.rect(104, 52, 5, 4, 0xFF8A5A2A);
        for (int x = 0; x < Art.W; x += 3) { a.px(x, 20 + (x * 7 % 5), 0xFFD8C8A0); }
        // glow berries? no; an ore vein
        for (int k = 0; k < 5; k++) { int x = 6 + a.rnd.nextInt(148), y = 6 + a.rnd.nextInt(18); a.rect(x, y, 2, 2, 0xFF30C8D8); }
    }

    private static void stronghold(Art a) {
        cutaway(a, 0xFF5A5A5C, 0);
        a.rect(10, 14, 140, 66, 0xFF2A2A2C);
        for (int y = 14; y < 80; y++) for (int x = 10; x < 150; x++) a.px(x, y, Art.shade(((x / 8 + y / 4) % 2 == 0) ? 0xFF6A6A68 : 0xFF5E5E5C, 0.85 + Art.hash(x, y, a.seed) * 0.3));
        for (int x = 10; x < 150; x += 8) a.rect(x, 14, 1, 66, 0xFF3A3A3C);
        // floor with lava pool
        a.tex(10, 76, 140, 4, 0xFF4A4A4C, 0.3);
        a.rect(24, 76, 18, 2, 0xFFE8661A); a.rect(26, 75, 14, 1, 0xFFFFA040);
        // end portal frame
        for (int i = 0; i < 5; i++) { a.tex(58 + i * 9, 62, 7, 14, 0xFF3B6A5A, 0.3); a.tex(58 + i * 9, 62, 7, 4, 0xFF4A7A5A, 0.2); a.rect(62 + i * 9, 58, 3, 3, 0xFF1F8A5A); }
        for (int x = 62; x < 100; x++) for (int y = 56; y < 62; y++) a.px(x, y, Art.mix(0xFF0A0A1A, 0xFF1A3A5A, Art.hash(x, y, a.seed)));
        for (int k = 0; k < 14; k++) a.px(64 + a.rnd.nextInt(34), 56 + a.rnd.nextInt(6), 0xFFB8F0E8);
        // iron bars + torch + stairs
        for (int x = 20; x < 56; x += 3) a.rect(x, 34, 1, 24, 0xFF9AA0A8);
        a.rect(20, 34, 36, 1, 0xFF9AA0A8);
        a.rect(112, 44, 2, 6, 0xFF6A4A2A); a.rect(111, 41, 4, 3, 0xFFF0B030);
        for (int s = 0; s < 8; s++) a.tex(120 + s * 3, 76 - s * 3, 3, 3 + s * 3, 0xFF7A7A78, 0.3);
        a.vignette(0.3);
    }

    private static void ancientCity(Art a) {
        a.fill(0xFF04141C);
        for (int y = 0; y < Art.H; y++) for (int x = 0; x < Art.W; x++) a.px(x, y, Art.shade(0xFF08202A, 0.7 + Art.hash(x, y, a.seed) * 0.5));
        // huge buildings of deepslate bricks and sculk
        for (int k = 0; k < 4; k++) { int x = 10 + k * 40, w = 24 + k % 2 * 8, ht = 30 + (k * 11) % 24; a.tex(x, 78 - ht, w, ht, 0xFF2A2E3A, 0.35); for (int y = 78 - ht; y < 78; y += 5) a.rect(x, y, w, 1, 0xFF1A1E28); a.rect(x - 2, 78 - ht - 2, w + 4, 3, 0xFF3A404E); for (int i = 3; i < w - 3; i += 6) a.rect(x + i, 78 - ht + 6, 2, 3, 0xFF1AC8C8); }
        a.tex(0, 78, Art.W, 12, 0xFF0C2A36, 0.5);
        for (int x = 0; x < Art.W; x++) if (Art.hash(x, 7, a.seed) > 0.45) { a.px(x, 78, 0xFF0E3A4A); a.px(x, 79, 0xFF22E0D0); }
        // soul lanterns, sculk shriekers, catalysts
        for (int k = 0; k < 7; k++) { int x = 12 + k * 22; a.rect(x, 24, 1, 4, 0xFF303040); a.rect(x - 1, 28, 3, 4, 0xFF30D8E8); a.px(x, 29, 0xFFC8FFFF); }
        for (int k = 0; k < 3; k++) { int x = 24 + k * 56; a.rect(x, 72, 6, 6, 0xFF0A2630); a.rect(x + 1, 70, 4, 2, 0xFFE8E0C0); a.px(x + 2, 74, 0xFF22E0D0); }
        a.rect(150, 74, 4, 4, 0xFF0A1A24);
        // glow
        for (int k = 0; k < 7; k++) { int x = 12 + k * 22; for (int j = -8; j <= 8; j++) for (int i = -8; i <= 8; i++) { double d = Math.sqrt(i * i + j * j); if (d < 8) a.px(x + i, 30 + j, Art.mix(a.get(x + i, 30 + j), 0xFF30D8E8, (8 - d) / 8 * 0.18)); } }
        a.vignette(0.4);
    }

    private static void trailRuins(Art a) {
        a.sky(0xFF86B0D8, 0xFFD2E4F0, 52);
        a.clouds(3, 6, 20, 0xFFF4F8FA);
        a.farHills(a.heights(46, 5, 28, 1), 0xFF557A60);
        int[] h = flatGround(a, 56, 0xFF4F8A3A, DIRT);
        for (int x = 0; x < Art.W; x++) for (int y = 58; y < Art.H; y++) {
            int c = y < 66 ? DIRT : y < 74 ? 0xFF8A8580 : 0xFF6A6A68;
            a.px(x, y, Art.shade(c, 0.9 + Art.hash(x, y, a.seed + 2) * 0.2));
        }
        // buried terracotta ruins
        int[] cols = {0xFFB5502E, 0xFFD9784A, 0xFFE0B08A, 0xFF9A4A2A};
        for (int row = 0; row < 3; row++) for (int x = 40; x < 120; x += 6) if ((x + row * 3) % 12 != 0 || row == 0) a.tex(x, 62 + row * 6, 6, 6, cols[(x / 6 + row) % 4], 0.3);
        for (int x = 48; x < 112; x += 24) { a.tex(x, 50, 8, 12, cols[1], 0.3); a.rect(x, 48, 8, 2, cols[2]); }
        // suspicious gravel & brush
        for (int x = 70; x < 90; x += 2) a.px(x, 57, 0xFF8A8580);
        a.rect(78, 58, 4, 3, 0xFF8A8580); a.px(79, 59, 0xFFE8E0C0);
        a.rect(76, 60, 8, 1, 0xFFC8B8A0);
        a.spruce(18, 56, 24, 0xFF3A6B3E, false); a.spruce(140, 56, 26, 0xFF3A6B3E, false);
    }

    private static void trialChambers(Art a) {
        cutaway(a, 0xFF6A6A68, 0);
        for (int y = 14; y < 78; y++) for (int x = 14; x < 146; x++) a.px(x, y, Art.shade(((x / 6 + y / 6) % 2 == 0) ? 0xFF8A8478 : 0xFF7E786C, 0.9 + Art.hash(x, y, a.seed) * 0.2));
        // copper walls
        for (int y = 14; y < 78; y++) { a.px(14, y, 0xFFC87A4A); a.px(15, y, 0xFFB86A3A); a.px(145, y, 0xFFC87A4A); a.px(144, y, 0xFFB86A3A); }
        for (int k = 0; k < 6; k++) { int y = 18 + k * 11; for (int x = 16; x < 144; x++) if ((x + k) % 12 < 2) a.px(x, y, k % 2 == 0 ? 0xFF4AB09A : 0xFFC87A4A); }
        // trial spawner
        a.rect(68, 56, 24, 22, 0xFF2A323A); a.rect(70, 58, 20, 18, 0xFF121820); a.rect(78, 62, 4, 4, 0xFFE8D8A0); a.rect(76, 64, 8, 1, 0xFF6AA8F0);
        for (int i = 0; i < 10; i++) a.px(72 + a.rnd.nextInt(16), 59 + a.rnd.nextInt(14), 0xFF6AA8F0);
        // chandeliers & vault
        for (int x = 28; x < 140; x += 36) { a.rect(x, 14, 1, 10, 0xFF4A4A4C); a.rect(x - 2, 24, 5, 3, 0xFFC87A4A); a.px(x, 22, 0xFFFFE080); }
        a.rect(112, 60, 16, 18, 0xFF9A7A56); a.rect(116, 64, 8, 10, 0xFFE8C060); a.rect(118, 68, 4, 3, 0xFF3A3A3C);
        a.rect(26, 66, 14, 12, 0xFFC87A4A); a.rect(28, 68, 10, 8, 0xFF4AB09A);
        a.vignette(0.3);
    }

    private static void ruinedPortal(Art a) {
        a.sky(0xFF74B4EA, 0xFFCFE6FA, 62);
        a.sun(128, 16, 0xFFFFF2B0);
        a.clouds(3, 6, 24, 0xFFFFFFFF);
        a.farHills(a.heights(54, 5, 28, 1), 0xFF9FCB86);
        int[] h = flatGround(a, 74, GRASS, DIRT);
        // netherrack patch and magma
        for (int x = 40; x < 120; x++) for (int y = 74; y < 80 - Math.abs(x - 80) / 12; y++) a.px(x, y, Art.shade(0xFF8A3A3A, 0.85 + Art.hash(x, y, a.seed) * 0.3));
        for (int x = 58; x < 100; x += 7) { a.px(x, 76, 0xFFE87A20); a.px(x + 1, 77, 0xFFFFB040); }
        // broken obsidian frame
        int ox = 66;
        int[] obs = {0xFF1A0F2A, 0xFF2A1A3E};
        for (int j = 0; j < 30; j++) { a.tex(ox, 74 - j, 5, 1, obs[(j / 4) % 2], 0.3); if (j < 22) a.tex(ox + 28, 74 - j, 5, 1, obs[(j / 4) % 2], 0.3); }
        for (int i = 0; i < 18; i++) a.tex(ox + 5 + i, 74 - 29 - 0, 1, 5, obs[(i / 4) % 2], 0.3);
        a.tex(ox, 70, 33, 4, obs[0], 0.3);
        for (int k = 0; k < 7; k++) a.rect(ox + 2 + k * 4, 36 + (k * 3) % 7, 3, 4, 0xFF7A2AC8);   // crying obsidian
        for (int j = 4; j < 24; j++) for (int i = 5; i < 28; i++) if (Art.hash(i, j, a.seed) > 0.45) a.px(ox + i, 74 - j, Art.mix(0xFF5A1AA8, 0xFFC890FF, Art.hash(i, j, a.seed + 1)));
        a.oak(20, 74, 10, 0xFF4F9A32); a.oak(140, 74, 8, 0xFF4F9A32);
    }

    private static void monsterRoom(Art a) {
        cutaway(a, 0xFF6A6A68, 0);
        for (int y = 22; y < 70; y++) for (int x = 40; x < 120; x++) { boolean moss = Art.hash(x / 2, y / 2, a.seed) > 0.55; a.px(x, y, Art.shade(moss ? 0xFF6A8A5A : 0xFF7A7A78, 0.7 + Art.hash(x, y, a.seed) * 0.35)); }
        for (int y = 24; y < 62; y++) for (int x = 42; x < 118; x++) a.px(x, y, Art.shade(0xFF1C1814, 0.85 + Art.hash(x, y, a.seed) * 0.3));
        a.tex(42, 62, 76, 8, 0xFF7A7A78, 0.4);
        for (int x = 42; x < 118; x += 3) if (Art.hash(x, 9, a.seed) > 0.4) a.px(x, 62, 0xFF6A8A5A);
        // spawner cage
        a.rect(72, 44, 16, 18, 0xFF121018);
        for (int i = 0; i < 16; i += 3) { a.rect(72 + i, 44, 1, 18, 0xFF3A3A44); }
        for (int j = 0; j < 18; j += 3) a.rect(72, 44 + j, 16, 1, 0xFF3A3A44);
        a.rect(78, 50, 4, 5, 0xFF5A8A3A); a.rect(78, 50, 4, 1, 0xFF8AB05A);
        a.rect(80, 46, 1, 3, 0xFFFF6A20); a.rect(79, 45, 3, 1, 0xFFFFB040);
        // chests
        a.rect(48, 56, 10, 6, 0xFF8A5A2A); a.rect(48, 58, 10, 1, 0xFF4A3220); a.rect(52, 58, 2, 2, 0xFFD0D0D0);
        a.rect(104, 56, 10, 6, 0xFF8A5A2A); a.rect(104, 58, 10, 1, 0xFF4A3220); a.rect(108, 58, 2, 2, 0xFFD0D0D0);
        a.vignette(0.3);
    }

    private static void desertWell(Art a) {
        a.sky(0xFF74B4EA, 0xFFF4E9C6, 62);
        a.sun(130, 18, 0xFFFFE9A0);
        a.farHills(a.heights(56, 4, 22, 1), 0xFFEAD7A0);
        int[] h = flatGround(a, 74, 0xFFF2DE96, 0xFFD9C27F);
        for (int x = 0; x < Art.W; x++) for (int y = h[x]; y < Art.H; y++) a.px(x, y, Art.shade(y - h[x] < 5 ? 0xFFF2DE96 : 0xFFD9C27F, 0.92 + Art.hash(x, y, a.seed) * 0.16));
        a.tex(66, 56, 30, 18, 0xFFE6CC84, 0.25);
        a.rect(70, 56, 22, 3, 0xFF3F76E4);
        for (int k = 0; k < 2; k++) { a.rect(66 + k * 28, 40, 3, 16, 0xFFD9C27F); }
        a.tex(64, 36, 34, 4, 0xFFE6CC84, 0.25);
        for (int i = 0; i < 4; i++) a.tex(66 + i, 34 - i, 30 - 2 * i, 1, 0xFFE0C478, 0.25);
        for (int x = 66; x < 96; x += 6) a.rect(x, 62, 3, 12, 0xFFD2B872);
        a.cactus(24, 74, 7); a.cactus(136, 74, 5);
    }

    private static void fossil(Art a) {
        cutaway(a, 0xFFE0CC8A, 0);
        for (int y = 0; y < Art.H; y++) for (int x = 0; x < Art.W; x++) { int c = y < 22 ? 0xFFF2DE96 : y < 50 ? 0xFFD9C27F : 0xFF8A8580; a.px(x, y, Art.shade(c, 0.88 + Art.hash(x, y, a.seed) * 0.24)); }
        a.sky(0xFF74B4EA, 0xFFF4E9C6, 14);
        a.sun(130, 6, 0xFFFFE9A0);
        // giant spine and ribs
        for (int x = 24; x < 136; x += 2) a.rect(x, 44 + (int) (Math.sin(x * 0.07) * 4), 3, 3, 0xFFF0EAD8);
        for (int k = 0; k < 8; k++) { int x = 32 + k * 13; int y = 46 + (int) (Math.sin(x * 0.07) * 4); for (int j = 0; j < 20; j++) { int off = (int) (Math.sin(j / 20.0 * Math.PI) * 6); a.px(x - off, y + j, 0xFFE8E0CC); a.px(x - off + 1, y + j, 0xFFD8D0BC); } }
        a.ellipse(134, 46, 8, 5, 0xFFF0EAD8); a.rect(132, 44, 3, 3, 0xFF4A4034); a.rect(138, 46, 6, 3, 0xFFE8E0CC);
        a.cactus(30, 22, 6);
    }

    private static void geode(Art a) {
        cutaway(a, 0xFF6A6A68, 0);
        for (int y = 0; y < Art.H; y++) for (int x = 0; x < Art.W; x++) a.px(x, y, Art.shade(0xFF6A6A68, 0.8 + Art.hash(x, y, a.seed) * 0.35));
        int cx = 80, cy = 45;
        for (int j = -34; j <= 34; j++) for (int i = -48; i <= 48; i++) {
            double d = Math.sqrt(i * i / 2.25 + j * j);
            if (d < 34) a.px(cx + i, cy + j, Art.shade(0xFF2A2A2E, 0.8 + Art.hash(i, j, a.seed) * 0.3));        // blackstone-ish shell
            if (d < 31) a.px(cx + i, cy + j, Art.shade(0xFFDADAD0, 0.85 + Art.hash(i, j, a.seed + 1) * 0.3));  // calcite
            if (d < 28) a.px(cx + i, cy + j, Art.shade(0xFF6A4AA8, 0.8 + Art.hash(i, j, a.seed + 2) * 0.35)); // budding
            if (d < 23) a.px(cx + i, cy + j, Art.shade(0xFF14101E, 0.8 + Art.hash(i, j, a.seed + 3) * 0.3));
        }
        for (int k = 0; k < 34; k++) { double t = a.rnd.nextDouble() * Math.PI * 2; int x = cx + (int) (Math.cos(t) * 20 * 1.5), y = cy + (int) (Math.sin(t) * 20); int len = 3 + a.rnd.nextInt(5); for (int j = 0; j < len; j++) { int px = x - (int) (Math.cos(t) * j * 1.5), py = y - (int) (Math.sin(t) * j); a.px(px, py, j == len - 1 ? 0xFFE0B8FF : 0xFFB070E8); a.px(px + 1, py, 0xFF9A55D8); } }
        a.rect(cx - 1, cy, 3, 3, 0xFFE0B8FF);
        a.vignette(0.3);
    }

    // ------------------------------------------------------------------ nether and end

    private static void netherFortress(Art a) {
        a.gradient(0, Art.H, 0xFF3A1010, 0xFF6A2020);
        for (int x = 0; x < Art.W; x++) for (int y = 0; y < 12; y++) a.px(x, y, Art.shade(0xFF3A1010, 0.8 + Art.hash(x, y, a.seed) * 0.4));
        for (int x = 0; x < Art.W; x++) for (int y = 76; y < Art.H; y++) a.px(x, y, Art.shade(0xFFFF7A20, 0.85 + Art.hash(x, y, a.seed) * 0.3));
        int[] nb = {0xFF2A1418, 0xFF34181E};
        // bridge and arches
        a.tex(0, 54, Art.W, 5, 0xFF2A1418, 0.35); a.tex(0, 50, Art.W, 3, 0xFF3A1A20, 0.35);
        for (int x = 0; x < Art.W; x += 20) { a.tex(x + 3, 59, 8, 17, nb[(x / 20) % 2], 0.35); a.tex(x + 3, 30, 6, 20, nb[0], 0.35); for (int i = 0; i < 14; i++) { double t = i / 13.0; int yy = (int) (40 + Math.sin(t * Math.PI) * -6); a.rect(x + 3 + i, yy + 20, 1, 4, nb[1]); } }
        a.tex(60, 24, 40, 26, 0xFF2A1418, 0.3);
        for (int i = 60; i < 100; i += 4) a.rect(i, 20, 3, 4, 0xFF2A1418);
        a.rect(70, 32, 6, 8, 0xFF0A0408); a.rect(84, 32, 6, 8, 0xFF0A0408);
        // nether wart, blaze, wither skeleton
        a.rect(110, 46, 6, 4, 0xFFA02A2A); a.px(112, 45, 0xFFC83A3A);
        a.rect(30, 38, 6, 6, 0xFFE8B030); a.rect(31, 39, 4, 1, 0xFF6A4A10); for (int i = 0; i < 3; i++) a.rect(29 + i * 3, 45, 1, 5, 0xFFC89020);
        a.rect(124, 40, 3, 10, 0xFF1A1A1A); a.rect(124, 36, 4, 4, 0xFF2A2A2A);
        a.fog(30, 90, 0xFFFF6A20, 0.1);
        a.vignette(0.4);
    }

    private static void bastion(Art a) {
        a.gradient(0, Art.H, 0xFF2A1A1A, 0xFF4A2A28);
        for (int x = 0; x < Art.W; x++) for (int y = 0; y < 10; y++) a.px(x, y, Art.shade(0xFF2A1818, 0.8 + Art.hash(x, y, a.seed) * 0.4));
        for (int x = 0; x < Art.W; x++) for (int y = 78; y < Art.H; y++) a.px(x, y, Art.shade(0xFF6A2A2A, 0.8 + Art.hash(x, y, a.seed) * 0.4));
        // blackstone tower
        a.tex(40, 24, 80, 54, 0xFF2E2A34, 0.4);
        for (int y = 24; y < 78; y += 6) a.rect(40, y, 80, 1, 0xFF1A161E);
        for (int x = 40; x < 120; x += 12) a.rect(x, 24, 1, 54, 0xFF1A161E);
        for (int i = 40; i < 120; i += 8) a.rect(i, 18, 6, 6, 0xFF2E2A34);
        // gilded blackstone & gold blocks
        for (int k = 0; k < 6; k++) { int x = 46 + a.rnd.nextInt(68), y = 30 + a.rnd.nextInt(40); a.rect(x, y, 4, 4, 0xFFF0C030); a.px(x + 1, y + 1, 0xFFFFE880); }
        a.rect(72, 56, 16, 22, 0xFF0A0810);
        a.rect(60, 44, 40, 2, 0xFF4A3A2A); a.rect(60, 40, 2, 4, 0xFF4A3A2A); a.rect(98, 40, 2, 4, 0xFF4A3A2A);
        // piglin
        a.rect(24, 68, 5, 8, 0xFFE8B030); a.rect(24, 62, 5, 6, 0xFFE8A89A); a.rect(23, 64, 1, 2, 0xFFE8A89A); a.rect(29, 64, 1, 2, 0xFFE8A89A);
        a.rect(126, 68, 5, 8, 0xFFE8B030); a.rect(126, 62, 5, 6, 0xFFE8A89A);
        // lava falls
        for (int y = 10; y < 78; y++) { a.px(26, y, 0xFFFF7A20); a.px(27, y, 0xFFFFB040); }
        a.fog(30, 90, 0xFFFF6A20, 0.08);
        a.vignette(0.4);
    }

    private static void netherFossil(Art a) {
        a.gradient(0, Art.H, 0xFF1A3A4A, 0xFF2A5A68);
        for (int x = 0; x < Art.W; x++) for (int y = 0; y < 12; y++) a.px(x, y, Art.shade(0xFF16303C, 0.8 + Art.hash(x, y, a.seed) * 0.4));
        int[] h = a.heights(70, 4, 26, 6);
        a.land(h, 0xFF5A4A3A, 0xFF4A3C30, 0xFF3A2E26, 8, 0.3);
        for (int x = 20; x < 140; x += 2) a.rect(x, 56 + (int) (Math.sin(x * 0.09) * 3), 3, 4, 0xFFE0D4B0);
        for (int k = 0; k < 7; k++) { int x = 30 + k * 15; int y = 58 + (int) (Math.sin(x * 0.09) * 3); for (int j = 0; j < 14; j++) { int off = (int) (Math.sin(j / 14.0 * Math.PI) * 5); a.px(x - off, y + j, 0xFFE0D4B0); a.px(x - off + 1, y + j, 0xFFC8BC98); } }
        a.ellipse(140, 58, 8, 5, 0xFFE8DCB8); a.rect(137, 56, 3, 3, 0xFF3A2E26);
        for (int k = 0; k < 8; k++) { int x = 6 + a.rnd.nextInt(148); a.px(x, h[x] - 1, 0xFF40E0F0); a.px(x, h[x] - 2, 0xFFA8FFFF); }
        a.vignette(0.4);
    }

    private static void endCity(Art a) {
        a.gradient(0, Art.H, 0xFF0A0614, 0xFF241238);
        a.stars(90, 60);
        int[] h = new int[Art.W];
        for (int x = 0; x < Art.W; x++) h[x] = 76 + (int) (Math.pow(Math.abs(x - 80) / 80.0, 2) * 20);
        a.land(h, 0xFFE4E0A8, 0xFFD0CC98, 0xFFC0BC88, 40, 0.2);
        int[] pur = {0xFFB898B8, 0xFFA888A8};
        for (int t = 0; t < 3; t++) { int x = 24 + t * 44, w = 22 - t * 4, ht = 36 + t * 6; a.tex(x, 76 - ht, w, ht, pur[t % 2], 0.28); for (int y = 76 - ht; y < 76; y += 6) a.rect(x, y, w, 1, 0xFF8A688A); a.rect(x - 2, 76 - ht - 3, w + 4, 3, 0xFFD8B8D8); a.rect(x + w / 2 - 2, 76 - ht - 8, 4, 5, 0xFFD8B8D8); a.rect(x + 3, 76 - ht + 8, 3, 4, 0xFF301838); }
        // floating end ship
        a.tex(118, 22, 30, 6, 0xFF8A688A, 0.3); a.tex(114, 28, 8, 6, 0xFF6A4A6A, 0.3); a.rect(128, 12, 2, 10, 0xFF6A4A6A); a.rect(130, 12, 14, 8, 0xFFE8D8F0);
        a.rect(60, 62, 6, 8, 0xFFB040C8); a.rect(62, 58, 2, 4, 0xFFD070E8);
        a.fog(60, 90, 0xFF5A3A78, 0.15);
    }
}
