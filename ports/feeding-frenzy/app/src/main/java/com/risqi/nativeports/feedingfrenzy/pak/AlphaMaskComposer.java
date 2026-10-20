package com.risqi.nativeports.feedingfrenzy.pak;

/**
 * Pure Java pixel composer for PopCap Games split RGB + Grayscale Alpha Mask sprite sheets.
 *
 * <p>In 2004 PopCap games (including Feeding Frenzy), transparent sprites are stored as a JPEG
 * RGB color sheet (e.g. {@code minnowSwim.jpg}) paired with an 8-bit grayscale GIF alpha mask
 * ({@code _minnowSwim.gif}), avoiding large PNG files over 2004 dial-up downloads.
 */
public final class AlphaMaskComposer {

    private AlphaMaskComposer() {
    }

    /**
     * Merges an RGB pixel buffer and a grayscale/luminance alpha mask buffer into non-premultiplied
     * {@code ARGB_8888} integers. Automatically nearest-neighbor scales the alpha buffer if dimensions differ.
     */
    public static int[] composeArgb(
            int[] rgbPixels,
            int width,
            int height,
            int[] alphaPixels,
            int alphaWidth,
            int alphaHeight) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid image dimensions: " + width + "x" + height);
        }
        int total = width * height;
        int[] out = new int[total];
        if (alphaPixels == null || alphaWidth <= 0 || alphaHeight <= 0) {
            System.arraycopy(rgbPixels, 0, out, 0, total);
            return out;
        }

        boolean sameSize = (width == alphaWidth) && (height == alphaHeight);
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            int ay = sameSize ? y : (y * alphaHeight) / height;
            int alphaRowOffset = ay * alphaWidth;
            for (int x = 0; x < width; x++) {
                int rgb = rgbPixels[rowOffset + x] & 0x00FFFFFF;
                int ax = sameSize ? x : (x * alphaWidth) / width;
                int maskPx = alphaPixels[alphaRowOffset + ax];
                int r = (maskPx >>> 16) & 0xFF;
                int g = (maskPx >>> 8) & 0xFF;
                int b = maskPx & 0xFF;
                int lum = (r * 77 + g * 150 + b * 29) >>> 8;
                out[rowOffset + x] = (lum << 24) | rgb;
            }
        }
        return out;
    }

    /**
     * Applies colorkey transparency (used by single-file PopCap BMP/GIF sprites with black/magenta background).
     */
    public static int[] applyColorKey(int[] argbPixels, int keyRgb, int tolerance) {
        int[] out = new int[argbPixels.length];
        int kr = (keyRgb >>> 16) & 0xFF;
        int kg = (keyRgb >>> 8) & 0xFF;
        int kb = keyRgb & 0xFF;
        for (int i = 0; i < argbPixels.length; i++) {
            int px = argbPixels[i];
            int r = (px >>> 16) & 0xFF;
            int g = (px >>> 8) & 0xFF;
            int b = px & 0xFF;
            if (Math.abs(r - kr) <= tolerance
                    && Math.abs(g - kg) <= tolerance
                    && Math.abs(b - kb) <= tolerance) {
                out[i] = 0x00000000;
            } else {
                out[i] = 0xFF000000 | (px & 0x00FFFFFF);
            }
        }
        return out;
    }
}
