package slatefinder;

/**
 * What makes each picture recognisable at a glance: a signature animal or prop in the foreground, and a badge in
 * the corner showing the block the biome or structure is known by. The badge's colour is the same colour the
 * biome has in the "What I understood" list.
 */
final class Heroes {
    private Heroes() {}

    private static int gy(Art a, int x) {
        int[] h = a.lastLand;
        int y = h != null ? h[Math.max(0, Math.min(Art.W - 1, x))] : 72;
        return Math.max(40, Math.min(86, y));
    }

    static void add(Art a, String id) {
        int s = 2;
        switch (id) {
            case "plains": Sprites.cow(a, 36, gy(a, 36) + 1, s); Sprites.cow(a, 118, gy(a, 118) + 1, s); break;
            case "sunflower_plains": Sprites.bee(a, 62, 36, s); Sprites.bee(a, 108, 30, s); break;
            case "snowy_plains": Sprites.snowGolem(a, 38, gy(a, 38) + 1, s); Sprites.rabbit(a, 118, gy(a, 118) + 1, s, 0xFFF4F4F4); break;
            case "ice_spikes": Sprites.polarBear(a, 112, gy(a, 112) + 1, s); break;
            case "desert": Sprites.camel(a, 112, gy(a, 112) + 1, s); Sprites.deadBush(a, 30, gy(a, 30) + 1, s); break;
            case "swamp": case "mangrove_swamp": Sprites.frog(a, 44, 73, s, id.equals("swamp") ? 0xFF5A9A3A : 0xFFD8A030); for (int k = 0; k < 4; k++) Sprites.lilyPad(a, 70 + k * 22, 72, s); break;
            case "forest": Sprites.wolf(a, 112, gy(a, 112) + 1, s); break;
            case "flower_forest": Sprites.rabbit(a, 122, gy(a, 122) + 1, s, 0xFFC8A070); Sprites.bee(a, 50, 40, s); break;
            case "birch_forest": Sprites.sheep(a, 112, gy(a, 112) + 1, s); break;
            case "old_growth_birch_forest": Sprites.fox(a, 112, gy(a, 112) + 1, s, false); break;
            case "dark_forest": a.bigMushroom(120, gy(a, 120), 12, 0xFFCC2A2A, 0xFFE8E0D0); a.bigMushroom(34, gy(a, 34), 8, 0xFF9A7050, 0xFFE8E0D0); break;
            case "pale_garden": Sprites.creaking(a, 104, gy(a, 104) + 1, 3); break;
            case "taiga": Sprites.fox(a, 112, gy(a, 112) + 1, s, false); Sprites.bush(a, 40, gy(a, 40), s, 0xFF3A6A2A, 0xFFCC2A3A); break;
            case "snowy_taiga": Sprites.fox(a, 112, gy(a, 112) + 1, s, true); break;
            case "old_growth_pine_taiga": Sprites.fox(a, 108, gy(a, 108) + 1, s, false); break;
            case "old_growth_spruce_taiga": Sprites.wolf(a, 108, gy(a, 108) + 1, s); break;
            case "grove": Sprites.goat(a, 104, gy(a, 104) + 1, s); break;
            case "jungle": Sprites.parrot(a, 110, gy(a, 110) - 14, s, 0xFFD02A2A); break;
            case "sparse_jungle": Sprites.parrot(a, 118, gy(a, 118) - 2, s, 0xFF2A6AD0); break;
            case "bamboo_jungle": Sprites.panda(a, 96, gy(a, 96) + 1, 3); break;
            case "savanna": Sprites.llama(a, 112, gy(a, 112) + 1, s); break;
            case "savanna_plateau": Sprites.llama(a, 70, gy(a, 70) + 1, s); break;
            case "windswept_savanna": Sprites.llama(a, 32, gy(a, 32) + 1, s); break;
            case "badlands": Sprites.armadillo(a, 112, gy(a, 112) + 1, s); Sprites.deadBush(a, 40, gy(a, 40) + 1, s); break;
            case "wooded_badlands": Sprites.armadillo(a, 100, gy(a, 100) + 1, s); break;
            case "eroded_badlands": Sprites.deadBush(a, 112, gy(a, 112) + 1, s); Sprites.deadBush(a, 40, gy(a, 40) + 1, s); break;
            case "windswept_hills": Sprites.sheep(a, 44, gy(a, 44) + 1, s); break;
            case "windswept_gravelly_hills": Sprites.goat(a, 100, gy(a, 100) + 1, s); break;
            case "windswept_forest": Sprites.llama(a, 100, gy(a, 100) + 1, s); break;
            case "snowy_slopes": Sprites.goat(a, 84, gy(a, 84) + 1, 3); break;
            case "jagged_peaks": Sprites.goat(a, 60, gy(a, 60) + 1, s); break;
            case "frozen_peaks": Sprites.goat(a, 110, gy(a, 110) + 1, s); break;
            case "stony_peaks": Sprites.goat(a, 100, gy(a, 100) + 1, s); break;
            case "meadow": Sprites.bee(a, 60, 40, s); Sprites.sheep(a, 120, gy(a, 120) + 1, s); break;
            case "cherry_grove": Sprites.bee(a, 100, 38, s); Sprites.rabbit(a, 128, gy(a, 128) + 1, s, 0xFFF0E8E0); break;
            case "river": case "frozen_river":
                for (int k = 0; k < 3; k++) Sprites.fish(a, 56 + k * 22, 80 + (k % 2) * 4, s, 0xFFE08A7A);
                break;
            case "beach": Sprites.turtle(a, 124, gy(a, 124) + 1, s); break;
            case "snowy_beach": Sprites.rabbit(a, 130, gy(a, 130) + 1, s, 0xFFF4F4F4); break;
            case "stony_shore": Sprites.dolphin(a, 40, 62, s); break;
            case "ocean": Sprites.dolphin(a, 62, 44, s); break;
            case "deep_ocean": Sprites.squid(a, 64, 56, 3, 0xFF2A3A6A); break;
            case "warm_ocean": Sprites.turtle(a, 108, 68, s); break;
            case "lukewarm_ocean": Sprites.dolphin(a, 100, 52, s); break;
            case "deep_lukewarm_ocean": Sprites.squid(a, 90, 52, 3, 0xFF2A5A7A); break;
            case "cold_ocean": for (int k = 0; k < 3; k++) Sprites.fish(a, 40 + k * 28, 50 + (k % 2) * 10, s, 0xFFD87060); break;
            case "deep_cold_ocean": Sprites.squid(a, 70, 56, 3, 0xFF1E3A5A); break;
            case "frozen_ocean": Sprites.polarBear(a, 26, 25, s); break;
            case "deep_frozen_ocean": Sprites.squid(a, 70, 56, 3, 0xFF3A4A78); break;
            case "mushroom_fields": Sprites.mooshroom(a, 96, gy(a, 96) + 1, 3); break;
            case "lush_caves": Sprites.axolotl(a, 64, gy(a, 64) - 1, 3); break;
            case "deep_dark": Sprites.warden(a, 104, 74, s); break;
            default: break;
        }
    }

    // ------------------------------------------------------------------ the corner badge

    private static final int OUTLINE = 0xFF0A0C10;

    /** kind, colour 1, colour 2, colour 3 (optional) */
    private static Object[] spec(Lexicon.Item it) {
        String id = it.id;
        int c = it.colour.getRGB();
        switch (id) {
            case "plains": return new Object[]{"grass", 0xFF79B03A, 0xFF866043};
            case "sunflower_plains": return new Object[]{"grassdots", 0xFF79B03A, 0xFF866043, 0xFFF4C20D};
            case "snowy_plains": return new Object[]{"noise", 0xFFF4F8FA, 0xFFD8E2EC};
            case "ice_spikes": return new Object[]{"ice", 0xFF8AB8F0, 0xFFD8ECFF};
            case "desert": return new Object[]{"noise", 0xFFE9D69A, 0xFFD2BC7A};
            case "swamp": return new Object[]{"water", 0xFF4A6A5A, 0xFF2F8A3A};
            case "mangrove_swamp": return new Object[]{"logend", 0xFF6A3B34, 0xFF8A4A44};
            case "forest": return new Object[]{"logend", 0xFF8A6A3A, 0xFF6B5230};
            case "flower_forest": return new Object[]{"leavesdots", 0xFF3F8A2E, 0xFF4F9A3A, 0xFFF04080};
            case "birch_forest": return new Object[]{"birch", 0xFFD7D3C4, 0xFF2A2A26};
            case "old_growth_birch_forest": return new Object[]{"logend", 0xFFD7D3C4, 0xFFB0AC9A};
            case "dark_forest": return new Object[]{"logend", 0xFF3B2A18, 0xFF2A1E10};
            case "pale_garden": return new Object[]{"logend", 0xFFE0DCD0, 0xFFB8B4A8};
            case "taiga": return new Object[]{"logend", 0xFF5A4630, 0xFF4A3622};
            case "snowy_taiga": return new Object[]{"snowtop", 0xFF4A3622, 0xFFF4F8FA};
            case "old_growth_pine_taiga": return new Object[]{"noise", 0xFF6B5A3A, 0xFF4A3A22};
            case "old_growth_spruce_taiga": return new Object[]{"logend", 0xFF4A3622, 0xFF3A2A1A};
            case "grove": return new Object[]{"leavesdots", 0xFF2F5A3A, 0xFF3A6B3E, 0xFFF4F8FA};
            case "jungle": return new Object[]{"logend", 0xFF5A4A28, 0xFF7A6638};
            case "sparse_jungle": return new Object[]{"leaves", 0xFF3F8A2E, 0xFF5AA83A};
            case "bamboo_jungle": return new Object[]{"bamboo", 0xFF7DB93A, 0xFF5E9B2E};
            case "savanna": case "savanna_plateau": case "windswept_savanna": return new Object[]{"logend", 0xFF6A6A64, 0xFFBA663A};
            case "badlands": case "wooded_badlands": case "eroded_badlands": return new Object[]{"stripes", 0xFFD9784A, 0xFFB5502E, 0xFFE0B08A};
            case "windswept_hills": return new Object[]{"grass", 0xFF6A9A46, 0xFF7D7D7D};
            case "windswept_gravelly_hills": return new Object[]{"noise", 0xFF8A8584, 0xFF6A6A68};
            case "windswept_forest": return new Object[]{"leavesdots", 0xFF2F5A3A, 0xFF3A6B3E, 0xFF7D7D7D};
            case "snowy_slopes": return new Object[]{"snowtop", 0xFF7D7D7D, 0xFFF4F8FA};
            case "jagged_peaks": return new Object[]{"snowtop", 0xFF6E7078, 0xFFFFFFFF};
            case "frozen_peaks": return new Object[]{"ice", 0xFF9AC4F0, 0xFFE8F4FF};
            case "stony_peaks": return new Object[]{"noise", 0xFFE8E8E4, 0xFFA0A09C};
            case "meadow": return new Object[]{"grassdots", 0xFF8FCB4E, 0xFF866043, 0xFFFFFFFF};
            case "cherry_grove": return new Object[]{"leaves", 0xFFF5B2D4, 0xFFFFC6E0};
            case "river": return new Object[]{"water", 0xFF3F76E4, 0xFF9AC0FF};
            case "frozen_river": return new Object[]{"water", 0xFFA5C8F0, 0xFFE4F2FF};
            case "beach": return new Object[]{"noise", 0xFFF0E2A6, 0xFFE0CC8A};
            case "snowy_beach": return new Object[]{"snowtop", 0xFFE8E0C0, 0xFFF4F8FA};
            case "stony_shore": return new Object[]{"noise", 0xFF8C8E8A, 0xFF6E706C};
            case "ocean": return new Object[]{"water", 0xFF3F6FD0, 0xFF8AB0F0};
            case "deep_ocean": return new Object[]{"water", 0xFF1C2E78, 0xFF3A5AB0};
            case "warm_ocean": return new Object[]{"coral", 0xFF3FD0D8, 0xFFE0409A};
            case "lukewarm_ocean": return new Object[]{"water", 0xFF3FA8D8, 0xFFB0E0F8};
            case "deep_lukewarm_ocean": return new Object[]{"water", 0xFF1F5A98, 0xFF3A8AC8};
            case "cold_ocean": return new Object[]{"water", 0xFF4A7AAA, 0xFFA0C0E0};
            case "deep_cold_ocean": return new Object[]{"water", 0xFF1F3E70, 0xFF3A5A90};
            case "frozen_ocean": return new Object[]{"ice", 0xFF6A8EC0, 0xFFDCEEFF};
            case "deep_frozen_ocean": return new Object[]{"ice", 0xFF3A4E88, 0xFFA0B8E0};
            case "mushroom_fields": return new Object[]{"noise", 0xFF8A7A90, 0xFFB8A8C0, 0xFFCC2A2A};
            case "dripstone_caves": return new Object[]{"noise", 0xFF8F7A62, 0xFF5A4A3A};
            case "lush_caves": return new Object[]{"leavesdots", 0xFF5CA84A, 0xFF7ABA5A, 0xFFE8A0D0};
            case "deep_dark": return new Object[]{"glow", 0xFF0E2A34, 0xFF1AC8B8};
            case "village": case "plains_village": return new Object[]{"plank", 0xFFB8935A, 0xFF7A5A3A};
            case "desert_village": return new Object[]{"brick", 0xFFE6CC84, 0xFFC8A458};
            case "savanna_village": return new Object[]{"plank", 0xFFB5663A, 0xFF7A3A1E};
            case "snowy_village": return new Object[]{"snowtop", 0xFF6A4A2E, 0xFFF4F8FA};
            case "taiga_village": return new Object[]{"plank", 0xFF5A4028, 0xFF3A2A1A};
            case "pillager_outpost": return new Object[]{"logend", 0xFF3B2A18, 0xFF2A1E10};
            case "woodland_mansion": return new Object[]{"plank", 0xFF4A3622, 0xFF2A1E10};
            case "ocean_monument": return new Object[]{"glow", 0xFF4A9A8A, 0xFFB8F0E8};
            case "ocean_ruin": return new Object[]{"brick", 0xFF7C7870, 0xFF4A4A46};
            case "warm_ocean_ruin": return new Object[]{"brick", 0xFFE6CC84, 0xFFB89A50};
            case "cold_ocean_ruin": return new Object[]{"brick", 0xFF6A7A82, 0xFF3A464C};
            case "shipwreck": case "ocean_shipwreck": case "beached_shipwreck": return new Object[]{"plank", 0xFF6A4A2A, 0xFF3A2A18};
            case "buried_treasure": return new Object[]{"cross", 0xFFE8D08A, 0xFFE02020};
            case "desert_pyramid": return new Object[]{"stripes", 0xFFE6CC84, 0xFFE8A04A, 0xFF5A8AB8};
            case "jungle_temple": return new Object[]{"noise", 0xFF6A8A5A, 0xFF7A7A78};
            case "igloo": return new Object[]{"brick", 0xFFEAF2F8, 0xFFB8C8D4};
            case "swamp_hut": return new Object[]{"plank", 0xFF5A4A32, 0xFF2F4A2A};
            case "ancient_city": return new Object[]{"brick", 0xFF2A2E3A, 0xFF1AC8C8};
            case "trail_ruins": return new Object[]{"stripes", 0xFFB5502E, 0xFFD9784A, 0xFFE0B08A};
            case "desert_well": return new Object[]{"brick", 0xFFE6CC84, 0xFF3F76E4};
            case "fossil": return new Object[]{"cross", 0xFFD9C27F, 0xFFF0EAD8};
            default: return new Object[]{"noise", c, Art.shade(c, 0.8)};
        }
    }

    static void emblem(Art a, Lexicon.Item it) {
        Object[] sp = spec(it);
        String kind = (String) sp[0];
        int c1 = (Integer) sp[1], c2 = (Integer) sp[2], c3 = sp.length > 3 ? (Integer) sp[3] : c2;
        final int S = 22, X0 = 6, Y0 = Art.H - S - 6;
        a.rect(X0 - 2, Y0 - 2, S + 4, S + 4, OUTLINE);
        long salt = it.id.hashCode();
        for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) {
            double n = Art.hash(i, j, salt), n2 = Art.hash(i / 2, j / 2, salt + 7);
            int col;
            switch (kind) {
                case "grass": col = j < 7 ? (n < 0.3 ? Art.shade(c1, 0.85) : c1) : (n < 0.3 ? Art.shade(c2, 0.8) : c2); if (j >= 7 && j < 10 && (i * 3 + j) % 5 == 0) col = c1; break;
                case "grassdots": col = j < 7 ? c1 : c2; if (j < 7 && n > 0.78) col = c3; if (j >= 7 && n < 0.2) col = Art.shade(c2, 0.8); break;
                case "snowtop": col = j < 8 ? (n < 0.25 ? Art.mix(c2, 0xFFC8D6E4, 0.4) : c2) : (n < 0.3 ? Art.shade(c1, 0.8) : c1); if (j >= 8 && j < 11 && (i + j) % 4 == 0) col = c2; break;
                case "stripes": { int[] b = {c1, c2, c3, c2}; col = Art.shade(b[(j / 4) % 4], 0.92 + n * 0.14); break; }
                case "logend": { int ring = Math.min(Math.min(i, S - 1 - i), Math.min(j, S - 1 - j)); col = ring < 2 ? Art.shade(c2, 0.7) : (ring % 4 < 2 ? c1 : Art.shade(c1, 0.78)); if (ring >= 9) col = c2; if (n > 0.93) col = Art.shade(col, 0.85); break; }
                case "birch": col = (i % 7 == 3 && n > 0.2) || (n > 0.93) ? c2 : Art.shade(c1, 0.94 + n * 0.1); break;
                case "leaves": col = n2 > 0.55 ? c2 : Art.shade(c1, 0.85 + n * 0.25); if (n > 0.9) col = Art.shade(c1, 0.55); break;
                case "leavesdots": col = n2 > 0.55 ? c2 : Art.shade(c1, 0.85 + n * 0.25); if (n > 0.86) col = c3; break;
                case "water": { double wv = Math.sin((i + j * 0.6) * 0.9) + Math.sin(i * 0.5 - j * 0.8); col = wv > 0.9 ? c2 : Art.shade(c1, 0.9 + n * 0.2); if (kind.equals("water") && c2 == 0xFF2F8A3A && n > 0.85) col = c2; break; }
                case "ice": col = ((i + j) % 9 < 2) ? c2 : Art.shade(c1, 0.92 + n * 0.16); if ((i - j + 40) % 11 == 0) col = Art.mix(c1, c2, 0.6); break;
                case "coral": col = n > 0.8 ? c2 : (n > 0.62 ? 0xFF3A7AE0 : Art.shade(c1, 0.9 + n * 0.2)); break;
                case "bamboo": col = (i % 6 < 2) ? (j % 7 == 3 ? Art.shade(c1, 0.6) : c1) : Art.shade(c2, 0.6 + n * 0.4); break;
                case "plank": col = (j % 6 == 5) ? c2 : Art.shade(c1, 0.9 + n * 0.18); if ((j / 6 + i / 11) % 2 == 0 && i % 11 == 0) col = c2; break;
                case "brick": col = ((j % 6 == 5) || ((i + (j / 6 % 2) * 5) % 11 == 10)) ? c2 : Art.shade(c1, 0.9 + n * 0.18); break;
                case "glow": col = n > 0.9 ? c2 : Art.shade(c1, 0.8 + n * 0.4); if (n > 0.96) col = Art.mix(c2, 0xFFFFFFFF, 0.5); break;
                case "cross": col = Art.shade(c1, 0.9 + n * 0.18); if (Math.abs(i - j) < 2 || Math.abs(i + j - (S - 1)) < 2) col = c2; break;
                default: col = n2 > 0.62 ? c2 : Art.shade(c1, 0.9 + n * 0.2); if (it.id.equals("mushroom_fields") && n > 0.9) col = c3; break;
            }
            a.px(X0 + i, Y0 + j, col);
        }
        // bevel: light top-left, dark bottom-right, so it reads as a block
        for (int k = 0; k < S; k++) { a.px(X0 + k, Y0, Art.mix(a.get(X0 + k, Y0), 0xFFFFFFFF, 0.35)); a.px(X0, Y0 + k, Art.mix(a.get(X0, Y0 + k), 0xFFFFFFFF, 0.25)); a.px(X0 + k, Y0 + S - 1, Art.shade(a.get(X0 + k, Y0 + S - 1), 0.7)); a.px(X0 + S - 1, Y0 + k, Art.shade(a.get(X0 + S - 1, Y0 + k), 0.75)); }
    }
}
