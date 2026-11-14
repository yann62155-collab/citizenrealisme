package fr.citizen.realisme.client;

/**
 * Palette parchemin : les gris de l'interface vanilla deviennent du papier ancien et de l'encre brune.
 * Pixels au format ABGR (NativeImage). Seuls les gris sont touchés : les dessins colorés restent intacts.
 */
public final class Parchment {
    // niveaux de gris d'origine -> couleur parchemin (r, g, b)
    private static final int[] STOPS = {0, 55, 85, 139, 198, 255};
    private static final int[][] COLORS = {
            {0x22, 0x15, 0x0B},
            {0x4A, 0x30, 0x18},
            {0x6E, 0x4A, 0x27},
            {0xA3, 0x80, 0x50},
            {0xE6, 0xD2, 0xA2},
            {0xFB, 0xF1, 0xD6}};

    private Parchment() {
    }

    public static int recolor(int abgr, int x, int y) {
        int a = (abgr >>> 24) & 0xFF;
        if (a == 0) return abgr;
        int r = abgr & 0xFF;
        int g = (abgr >>> 8) & 0xFF;
        int b = (abgr >>> 16) & 0xFF;
        if (Math.abs(r - g) > 6 || Math.abs(g - b) > 6) return abgr;
        int lum = (r + g + b) / 3;
        int i = 0;
        while (i < STOPS.length - 2 && lum > STOPS[i + 1]) i++;
        float t = (lum - STOPS[i]) / (float) (STOPS[i + 1] - STOPS[i]);
        t = Math.max(0f, Math.min(1f, t));
        int nr = lerp(COLORS[i][0], COLORS[i + 1][0], t);
        int ng = lerp(COLORS[i][1], COLORS[i + 1][1], t);
        int nb = lerp(COLORS[i][2], COLORS[i + 1][2], t);
        if (lum > 150 && lum < 235) {
            // grain du papier
            int h = x * 73856093 ^ y * 19349663;
            h = (h ^ (h >>> 13)) * 0x5bd1e995;
            int noise = ((h >>> 24) & 0x0F) - 7;
            nr = clamp(nr + noise);
            ng = clamp(ng + noise);
            nb = clamp(nb + noise / 2);
        }
        return (a << 24) | (nb << 16) | (ng << 8) | nr;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
