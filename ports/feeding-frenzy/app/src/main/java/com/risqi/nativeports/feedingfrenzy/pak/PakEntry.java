package com.risqi.nativeports.feedingfrenzy.pak;

/**
 * Metadata record for an individual asset entry inside a PopCap SexyAppFramework {@code .pak} archive.
 */
public final class PakEntry {

    public static final String CAT_SPRITE = "sprite_image";
    public static final String CAT_ALPHA_MASK = "alpha_mask";
    public static final String CAT_AUDIO = "audio";
    public static final String CAT_CONFIG = "config_data";
    public static final String CAT_OTHER = "other";

    public final int index;
    public final String rawName;
    public final String normalizedPath;
    public final int offset;
    public final int size;
    public final long windowsFileTime;
    public final String category;

    public PakEntry(
            int index,
            String rawName,
            String normalizedPath,
            int offset,
            int size,
            long windowsFileTime,
            String category) {
        this.index = index;
        this.rawName = rawName;
        this.normalizedPath = normalizedPath;
        this.offset = offset;
        this.size = size;
        this.windowsFileTime = windowsFileTime;
        this.category = category;
    }

    public String getFileName() {
        int slash = normalizedPath.lastIndexOf('/');
        return slash >= 0 ? normalizedPath.substring(slash + 1) : normalizedPath;
    }

    public String getDirectory() {
        int slash = normalizedPath.lastIndexOf('/');
        return slash >= 0 ? normalizedPath.substring(0, slash) : "";
    }

    public static String classifyPath(String normalizedPath) {
        String low = normalizedPath.toLowerCase();
        int slash = low.lastIndexOf('/');
        String base = slash >= 0 ? low.substring(slash + 1) : low;
        if (low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".png")
                || low.endsWith(".gif") || low.endsWith(".bmp") || low.endsWith(".tga")) {
            if (base.startsWith("_") || base.endsWith("_.gif") || base.contains("alpha")) {
                return CAT_ALPHA_MASK;
            }
            return CAT_SPRITE;
        }
        if (low.endsWith(".ogg") || low.endsWith(".wav") || low.endsWith(".mp3")
                || low.endsWith(".mo3") || low.endsWith(".xm") || low.endsWith(".it")) {
            return CAT_AUDIO;
        }
        if (low.endsWith(".xml") || low.endsWith(".txt") || low.endsWith(".cfg")
                || low.endsWith(".ini") || low.endsWith(".dat")) {
            return CAT_CONFIG;
        }
        return CAT_OTHER;
    }

    @Override
    public String toString() {
        return "PakEntry[" + index + "] " + normalizedPath + " (" + size + " B, " + category + ")";
    }
}
