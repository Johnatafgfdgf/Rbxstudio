package com.rbxnative.androidport;

import java.util.Locale;

/**
 * Pixel-only before/after comparison of two USER-selected images.
 * This is not a visual-quality score and never reads files or calls a model.
 */
public final class StudioVisualCompare {
    public static final int SAMPLE_SIZE = 160;
    private static final int CHANGE_THRESHOLD = 30;
    private StudioVisualCompare() {}

    /**
     * Honest 160x160 diagnostic color map, NOT a screenshot and NOT
     * an aesthetic grade. Dark monochrome = no threshold change;
     * yellow/orange/red = increasing RGB channel difference.
     *
     * Reuses exactly the threshold used by describe(). Pixel arrays
     * must be opaque composited samples from comparePixelSample().
     */
    public static int[] differenceHeatmap(int[] before, int[] after,
            int width, int height) {
        if (before == null || after == null || width < 1 || height < 1 ||
                (long) width * height > SAMPLE_SIZE * SAMPLE_SIZE ||
                before.length != (long) width * height ||
                after.length != (long) width * height)
            throw new IllegalArgumentException("Amostra de diferença inválida.");
        int[] result = new int[before.length];
        for (int i = 0; i < result.length; i++) {
            int p = before[i], q = after[i];
            if ((p >>> 24) != 255 || (q >>> 24) != 255)
                throw new IllegalArgumentException("Pixels precisam ser opacos.");
            int deltaR = Math.abs(((p >>> 16) & 255) - ((q >>> 16) & 255));
            int deltaG = Math.abs(((p >>> 8) & 255) - ((q >>> 8) & 255));
            int deltaB = Math.abs((p & 255) - (q & 255));
            int delta = Math.max(deltaR, Math.max(deltaG, deltaB));
            if (delta >= 180) {
                result[i] = 0xffff5549; // very large difference: coral red
            } else if (delta >= 100) {
                result[i] = 0xffffa33b; // large difference: orange
            } else if (delta >= CHANGE_THRESHOLD) {
                result[i] = 0xfff8d56a; // detectable difference: yellow
            } else {
                int r = (q >>> 16) & 255, g = (q >>> 8) & 255, b = q & 255;
                int gray = Math.max(20, (77 * r + 150 * g + 29 * b) / 384);
                result[i] = 0xff000000 | gray << 16 | gray << 8 | gray;
            }
        }
        return result;
    }

    public static String describe(int[] before, int[] after, int width, int height) {
        if (before == null || after == null || width < 1 || height < 1 ||
                (long) width * height > SAMPLE_SIZE * SAMPLE_SIZE ||
                before.length != (long) width * height ||
                after.length != (long) width * height)
            throw new IllegalArgumentException("Amostra inválida ou grande demais.");

        final int count = width * height;
        long totalChannelDifference = 0;
        long totalBeforeLight = 0, totalAfterLight = 0;
        int changed = 0;
        int[] regionChanges = new int[9];
        int[] regionPixels = new int[9];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pos = y * width + x;
                int p = before[pos], q = after[pos];
                // The caller composites both images on the same dark
                // background. Their alpha must therefore be opaque.
                if (((p >>> 24) & 255) != 255 ||
                        ((q >>> 24) & 255) != 255)
                    throw new IllegalArgumentException("Compare apenas pixels opacos.");
                int r0 = (p >>> 16) & 255, g0 = (p >>> 8) & 255, b0 = p & 255;
                int r1 = (q >>> 16) & 255, g1 = (q >>> 8) & 255, b1 = q & 255;
                int rd = Math.abs(r1 - r0), gd = Math.abs(g1 - g0);
                int bd = Math.abs(b1 - b0);
                totalChannelDifference += rd + gd + bd;
                totalBeforeLight += (77 * r0 + 150 * g0 + 29 * b0) >> 8;
                totalAfterLight += (77 * r1 + 150 * g1 + 29 * b1) >> 8;
                int region = Math.min(2, y * 3 / height) * 3 +
                        Math.min(2, x * 3 / width);
                regionPixels[region]++;
                if (Math.max(rd, Math.max(gd, bd)) >= CHANGE_THRESHOLD) {
                    changed++;
                    regionChanges[region]++;
                }
            }
        }
        String[] positions = {
            "superior esquerda", "superior centro", "superior direita",
            "centro esquerda", "centro", "centro direita",
            "inferior esquerda", "inferior centro", "inferior direita"
        };
        int busiest = 0;
        for (int i = 1; i < 9; i++) {
            // Compare regional proportions, not absolute region sizes.
            if (regionChanges[i] * (long) regionPixels[busiest] >
                    regionChanges[busiest] * (long) regionPixels[i]) {
                busiest = i;
            }
        }

        double percent = changed * 100.0 / count;
        double avg = totalChannelDifference / (count * 3.0);
        double brightness = (totalAfterLight - totalBeforeLight) / (double) count;
        String regionText = changed == 0 ? "nenhuma mudança detectável" :
                positions[busiest] + " (" +
                String.format(Locale.ROOT, "%.1f", regionChanges[busiest] *
                        100.0 / regionPixels[busiest]) + "% da região)";
        return String.format(Locale.ROOT,
                "COMPARAÇÃO LOCAL DE PIXELS — ANTES / DEPOIS\n" +
                "Mudança detectável (diferença >= %d em um canal): %.1f%%\n" +
                "Diferença RGB média por canal (0–255): %.1f\n" +
                "Variação média de luminosidade (depois menos antes): %+.1f/255\n" +
                "Região com maior proporção de mudanças: %s\n\n" +
                "Comparação alinhada por redimensionamento da imagem. " +
                "Câmera, enquadramento, resolução e momento da animação devem ser " +
                "equivalentes para uma comparação confiável. Uma diferença de pixels " +
                "NÃO comprova que a imagem ficou melhor, que um VFX funciona ou " +
                "que o Play foi testado.",
                CHANGE_THRESHOLD, percent, avg, brightness, regionText);
    }
}
