package slatefinder;

/**
 * Little block-style animals and props that stand in the previews, drawn big enough to recognise at a glance.
 * Every one is drawn in local units from the feet up: (dx, dy) is how far right of centre and above the ground.
 */
final class Sprites {
    private Sprites() {}

    private static void r(Art a, int ox, int oy, int s, int dx, int dy, int w, int h, int c) {
        a.rect(ox + dx * s, oy - (dy + h) * s, w * s, h * s, c);
    }

    private static void leg(Art a, int ox, int oy, int s, int dx, int h, int c) { r(a, ox, oy, s, dx, 0, 1, h, c); }

    /** A four-legged body: used for cows, sheep, llamas and the like. */
    private static void quad(Art a, int ox, int oy, int s, int bodyC, int legC, int bodyW, int bodyH, int legH) {
        for (int k = 0; k < 2; k++) { leg(a, ox, oy, s, -bodyW / 2 + 1 + k * 0, legH, legC); leg(a, ox, oy, s, bodyW / 2 - 2, legH, legC); }
        leg(a, ox, oy, s, -bodyW / 2 + 3, legH, Art.shade(legC, 0.8)); leg(a, ox, oy, s, bodyW / 2 - 4, legH, Art.shade(legC, 0.8));
        r(a, ox, oy, s, -bodyW / 2, legH, bodyW, bodyH, bodyC);
    }

    static void cow(Art a, int x, int y, int s) {
        quad(a, x, y, s, 0xFF5A3A24, 0xFF3A2A1C, 10, 5, 3);
        r(a, x, y, s, -3, 5, 3, 2, 0xFFF0EEE8); r(a, x, y, s, 1, 4, 3, 3, 0xFFF0EEE8);
        r(a, x, y, s, 5, 5, 4, 4, 0xFF5A3A24); r(a, x, y, s, 7, 5, 2, 2, 0xFFD8B8A0);
        r(a, x, y, s, 5, 9, 1, 1, 0xFFE8E0D0); r(a, x, y, s, 8, 9, 1, 1, 0xFFE8E0D0);
    }

    static void sheep(Art a, int x, int y, int s) {
        quad(a, x, y, s, 0xFFF0EEE8, 0xFFC8C0B0, 10, 6, 3);
        r(a, x, y, s, 5, 5, 4, 4, 0xFF8A7A6A);
        r(a, x, y, s, 6, 7, 1, 1, 0xFF202020);
    }

    static void llama(Art a, int x, int y, int s) {
        quad(a, x, y, s, 0xFFE8DCC4, 0xFFC8B89A, 9, 5, 5);
        r(a, x, y, s, 3, 10, 2, 5, 0xFFE8DCC4); r(a, x, y, s, 3, 15, 4, 3, 0xFFE8DCC4);
        r(a, x, y, s, 3, 18, 1, 1, 0xFFC8B89A); r(a, x, y, s, 6, 18, 1, 1, 0xFFC8B89A);
        r(a, x, y, s, -2, 8, 4, 2, 0xFFB03A3A);   // a carpet
    }

    static void camel(Art a, int x, int y, int s) {
        quad(a, x, y, s, 0xFFC8A060, 0xFFA88040, 11, 5, 6);
        r(a, x, y, s, -2, 11, 3, 3, 0xFFC8A060);                    // the hump
        r(a, x, y, s, 5, 11, 2, 6, 0xFFC8A060); r(a, x, y, s, 5, 17, 4, 3, 0xFFC8A060);
        r(a, x, y, s, 7, 18, 1, 1, 0xFF202020);
        r(a, x, y, s, -3, 10, 6, 1, 0xFFB03A3A);
    }

    static void goat(Art a, int x, int y, int s) {
        quad(a, x, y, s, 0xFFE8E4DC, 0xFFB0A898, 7, 4, 3);
        r(a, x, y, s, 3, 5, 3, 4, 0xFFE8E4DC); r(a, x, y, s, 3, 9, 1, 2, 0xFFD8D0B8); r(a, x, y, s, 5, 9, 1, 2, 0xFFD8D0B8);
        r(a, x, y, s, 5, 6, 1, 1, 0xFF202020); r(a, x, y, s, 4, 4, 1, 2, 0xFFC8C0B0);
    }

    static void fox(Art a, int x, int y, int s, boolean white) {
        int body = white ? 0xFFF0F0F0 : 0xFFD8702A;
        quad(a, x, y, s, body, 0xFF3A2A22, 7, 3, 2);
        r(a, x, y, s, 3, 5, 3, 3, body); r(a, x, y, s, 5, 5, 1, 1, 0xFF202020);
        r(a, x, y, s, 3, 8, 1, 1, body); r(a, x, y, s, 5, 8, 1, 1, body);
        r(a, x, y, s, -7, 4, 4, 2, body); r(a, x, y, s, -9, 4, 2, 2, 0xFFF0F0F0);
    }

    static void wolf(Art a, int x, int y, int s) {
        quad(a, x, y, s, 0xFFB8B4AC, 0xFF8A867E, 8, 4, 3);
        r(a, x, y, s, 4, 6, 4, 3, 0xFFB8B4AC); r(a, x, y, s, 7, 6, 1, 1, 0xFF202020); r(a, x, y, s, 4, 9, 1, 1, 0xFF8A867E); r(a, x, y, s, 7, 9, 1, 1, 0xFF8A867E);
        r(a, x, y, s, -7, 5, 3, 2, 0xFFB8B4AC);
    }

    static void polarBear(Art a, int x, int y, int s) {
        quad(a, x, y, s, 0xFFF6F6F2, 0xFFD8D8D0, 12, 6, 3);
        r(a, x, y, s, 6, 6, 4, 4, 0xFFF6F6F2); r(a, x, y, s, 9, 7, 1, 1, 0xFF202020); r(a, x, y, s, 10, 6, 1, 1, 0xFF202020);
        r(a, x, y, s, 6, 10, 1, 1, 0xFFE0E0D8); r(a, x, y, s, 9, 10, 1, 1, 0xFFE0E0D8);
    }

    static void panda(Art a, int x, int y, int s) {
        quad(a, x, y, s, 0xFFF4F4F0, 0xFF1A1A1A, 9, 6, 3);
        r(a, x, y, s, -4, 3, 2, 3, 0xFF1A1A1A); r(a, x, y, s, 3, 3, 2, 3, 0xFF1A1A1A);
        r(a, x, y, s, -2, 8, 5, 1, 0xFF1A1A1A);
        r(a, x, y, s, 3, 6, 5, 5, 0xFFF4F4F0); r(a, x, y, s, 3, 11, 1, 1, 0xFF1A1A1A); r(a, x, y, s, 7, 11, 1, 1, 0xFF1A1A1A);
        r(a, x, y, s, 4, 8, 1, 2, 0xFF1A1A1A); r(a, x, y, s, 6, 8, 1, 2, 0xFF1A1A1A); r(a, x, y, s, 5, 7, 1, 1, 0xFF1A1A1A);
    }

    static void rabbit(Art a, int x, int y, int s, int c) {
        r(a, x, y, s, -2, 0, 4, 3, c); r(a, x, y, s, 1, 3, 2, 2, c);
        r(a, x, y, s, 1, 5, 1, 3, c); r(a, x, y, s, 3, 5, 1, 3, c);
        r(a, x, y, s, 2, 4, 1, 1, 0xFF202020);
    }

    static void parrot(Art a, int x, int y, int s, int c) {
        r(a, x, y, s, -1, 0, 3, 5, c); r(a, x, y, s, -1, 5, 3, 2, c);
        r(a, x, y, s, 2, 5, 1, 1, 0xFFE8D030); r(a, x, y, s, 1, 6, 1, 1, 0xFF202020);
        r(a, x, y, s, -1, -3, 2, 3, Art.shade(c, 0.8)); r(a, x, y, s, -2, 2, 1, 3, 0xFF2A6AD0);
    }

    static void bee(Art a, int x, int y, int s) {
        r(a, x, y, s, -2, 0, 5, 3, 0xFFF0C020);
        r(a, x, y, s, -1, 0, 1, 3, 0xFF2A2A2A); r(a, x, y, s, 1, 0, 1, 3, 0xFF2A2A2A);
        r(a, x, y, s, 3, 1, 1, 1, 0xFF202020);
        r(a, x, y, s, -1, 3, 3, 2, 0xFFD8F0FF);
    }

    static void turtle(Art a, int x, int y, int s) {
        r(a, x, y, s, -4, 1, 8, 4, 0xFF4A8A3A); r(a, x, y, s, -3, 5, 6, 1, 0xFF5AA84A);
        r(a, x, y, s, -3, 3, 2, 2, 0xFF3A7A2A); r(a, x, y, s, 1, 3, 2, 2, 0xFF3A7A2A);
        r(a, x, y, s, 4, 2, 3, 3, 0xFF8AB05A); r(a, x, y, s, 6, 3, 1, 1, 0xFF202020);
        r(a, x, y, s, -4, 0, 2, 1, 0xFF8AB05A); r(a, x, y, s, 2, 0, 2, 1, 0xFF8AB05A);
    }

    static void frog(Art a, int x, int y, int s, int c) {
        r(a, x, y, s, -3, 1, 6, 3, c); r(a, x, y, s, -4, 0, 2, 2, c); r(a, x, y, s, 2, 0, 2, 2, c);
        r(a, x, y, s, -3, 4, 2, 2, c); r(a, x, y, s, 1, 4, 2, 2, c);
        r(a, x, y, s, -3, 5, 1, 1, 0xFF202020); r(a, x, y, s, 2, 5, 1, 1, 0xFF202020);
        r(a, x, y, s, -1, 1, 2, 1, 0xFFF0E0C0);
    }

    static void axolotl(Art a, int x, int y, int s) {
        r(a, x, y, s, -5, 0, 9, 3, 0xFFF0A0B8); r(a, x, y, s, 4, 0, 3, 4, 0xFFF0A0B8);
        r(a, x, y, s, 7, 1, 1, 1, 0xFF202020); r(a, x, y, s, 3, 4, 1, 3, 0xFFE0508A); r(a, x, y, s, 5, 4, 1, 3, 0xFFE0508A);
        r(a, x, y, s, -8, 1, 3, 1, 0xFFF0A0B8); r(a, x, y, s, -5, 3, 1, 1, 0xFFD8708A);
    }

    static void warden(Art a, int x, int y, int s) {
        r(a, x, y, s, -3, 0, 2, 7, 0xFF0A2A34); r(a, x, y, s, 1, 0, 2, 7, 0xFF0A2A34);
        r(a, x, y, s, -4, 7, 8, 7, 0xFF0C3440);
        r(a, x, y, s, -1, 9, 2, 3, 0xFF1AC8B8);                       // the glowing chest
        r(a, x, y, s, -3, 14, 6, 4, 0xFF0A2A34);
        r(a, x, y, s, -2, 15, 1, 1, 0xFF1AC8B8); r(a, x, y, s, 1, 15, 1, 1, 0xFF1AC8B8);
        r(a, x, y, s, -6, 5, 2, 8, 0xFF0A2A34); r(a, x, y, s, 4, 5, 2, 8, 0xFF0A2A34);
        r(a, x, y, s, -5, 14, 1, 2, 0xFF1AC8B8); r(a, x, y, s, 4, 14, 1, 2, 0xFF1AC8B8);
    }

    static void creaking(Art a, int x, int y, int s) {
        int c = 0xFF6E6A64;
        r(a, x, y, s, -2, 0, 2, 8, c); r(a, x, y, s, 1, 0, 2, 8, c);
        r(a, x, y, s, -3, 8, 7, 7, 0xFF8A857C);
        r(a, x, y, s, -2, 11, 2, 2, 0xFFE8701A); r(a, x, y, s, 1, 11, 2, 2, 0xFFE8701A);    // orange eyes
        r(a, x, y, s, -5, 7, 2, 7, c); r(a, x, y, s, 4, 7, 2, 7, c);
        r(a, x, y, s, -1, 9, 3, 1, 0xFF3A3834);
    }

    static void mooshroom(Art a, int x, int y, int s) {
        quad(a, x, y, s, 0xFFB02A2A, 0xFF8A2020, 10, 5, 3);
        r(a, x, y, s, -3, 5, 3, 2, 0xFFF0EEE8); r(a, x, y, s, 1, 6, 2, 1, 0xFFF0EEE8);
        r(a, x, y, s, 5, 5, 4, 4, 0xFFB02A2A); r(a, x, y, s, 7, 5, 2, 2, 0xFFD8B8A0);
        r(a, x, y, s, -3, 7, 3, 3, 0xFFE8E0D0); r(a, x, y, s, -4, 10, 5, 2, 0xFFCC2A2A); // a mushroom on its back
        r(a, x, y, s, 1, 7, 2, 2, 0xFFE8E0D0); r(a, x, y, s, 0, 9, 4, 2, 0xFF9A7050);
    }

    static void snowGolem(Art a, int x, int y, int s) {
        r(a, x, y, s, -3, 0, 6, 6, 0xFFF4F8FA); r(a, x, y, s, -2, 6, 4, 4, 0xFFF4F8FA);
        r(a, x, y, s, -2, 10, 4, 3, 0xFFF4F8FA); r(a, x, y, s, -2, 13, 4, 1, 0xFFE07A1A);
        r(a, x, y, s, -1, 11, 1, 1, 0xFF202020); r(a, x, y, s, 1, 11, 1, 1, 0xFF202020);
        r(a, x, y, s, -2, 14, 4, 2, 0xFFE8941A);                       // the pumpkin
        r(a, x, y, s, -5, 7, 2, 1, 0xFF6A4A2A); r(a, x, y, s, 3, 7, 2, 1, 0xFF6A4A2A);
    }

    static void fish(Art a, int x, int y, int s, int c) {
        r(a, x, y, s, -2, 0, 5, 2, c); r(a, x, y, s, 3, 1, 1, 1, c); r(a, x, y, s, -4, 0, 2, 3, Art.shade(c, 0.8));
        r(a, x, y, s, 2, 1, 1, 1, 0xFF202020);
    }

    static void dolphin(Art a, int x, int y, int s) {
        int c = 0xFF7A8AA0;
        r(a, x, y, s, -6, 2, 12, 4, c); r(a, x, y, s, 6, 2, 3, 2, Art.shade(c, 1.1));
        r(a, x, y, s, -1, 6, 2, 3, Art.shade(c, 0.85)); r(a, x, y, s, -9, 4, 3, 3, Art.shade(c, 0.85));
        r(a, x, y, s, -6, 0, 12, 2, 0xFFD8E0EC); r(a, x, y, s, 3, 4, 1, 1, 0xFF202020);
    }

    static void squid(Art a, int x, int y, int s, int c) {
        int light = Art.mix(c, 0xFFFFFFFF, 0.45);
        r(a, x, y, s, -3, 6, 7, 6, c); r(a, x, y, s, -2, 12, 5, 2, c);
        r(a, x, y, s, -3, 11, 7, 1, light);
        r(a, x, y, s, -2, 8, 2, 2, 0xFF101010); r(a, x, y, s, 1, 8, 2, 2, 0xFF101010);
        for (int k = -3; k <= 3; k += 1) r(a, x, y, s, k, 0, 1, 6 - (k & 1), light);
    }

    static void armadillo(Art a, int x, int y, int s) {
        r(a, x, y, s, -4, 1, 8, 4, 0xFFA8706A); r(a, x, y, s, -3, 5, 6, 1, 0xFFC08880);
        for (int k = -3; k <= 2; k += 2) r(a, x, y, s, k, 1, 1, 4, 0xFF8A5A54);
        r(a, x, y, s, 4, 1, 3, 3, 0xFFC09088); r(a, x, y, s, 6, 2, 1, 1, 0xFF202020);
        r(a, x, y, s, -3, 0, 1, 1, 0xFF6A4A44); r(a, x, y, s, 1, 0, 1, 1, 0xFF6A4A44);
    }

    static void bush(Art a, int x, int y, int s, int leaf, int berry) {
        a.leafBlob(x, y - 3 * s, 4 * s, 3 * s, leaf, 0.35);
        for (int k = -2; k <= 2; k++) a.px(x + k * s, y - 4 * s + (k & 1) * s, berry);
    }

    static void deadBush(Art a, int x, int y, int s) {
        int c = 0xFF7A5A2A;
        r(a, x, y, s, 0, 0, 1, 4, c); r(a, x, y, s, -2, 3, 2, 1, c); r(a, x, y, s, 1, 4, 2, 1, c);
        r(a, x, y, s, -3, 4, 1, 2, c); r(a, x, y, s, 3, 5, 1, 2, c);
    }

    static void lilyPad(Art a, int x, int y, int s) { r(a, x, y, s, -2, 0, 5, 1, 0xFF2F8A3A); r(a, x, y, s, 0, 0, 1, 1, 0xFF14181A); }

    static void bigFlower(Art a, int x, int y, int s, int petal) {
        r(a, x, y, s, 0, 0, 1, 7, 0xFF3F8F2E);
        r(a, x, y, s, -2, 7, 5, 1, petal); r(a, x, y, s, -1, 6, 3, 3, petal); r(a, x, y, s, 0, 7, 1, 1, 0xFFF0D020);
        r(a, x, y, s, -2, 3, 2, 1, 0xFF3F8F2E);
    }
}
