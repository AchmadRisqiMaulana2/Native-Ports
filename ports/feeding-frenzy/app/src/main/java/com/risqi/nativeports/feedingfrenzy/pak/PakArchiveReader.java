package com.risqi.nativeports.feedingfrenzy.pak;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Native Java parser & builder for PopCap Games {@code main.pak} archives (2004 Feeding Frenzy).
 *
 * <p>Binary format specification:
 * <ul>
 *   <li>Entire stream XOR-ciphered with key {@code 0xF7} (plain {@code 0x00} key also supported).</li>
 *   <li>Header: little-endian int32 magic {@code 0xBAC04AC0} + uint32 version {@code 0}.</li>
 *   <li>Directory table until {@code (flags & 0x80) != 0}:
 *       {@code uint8 flags}, {@code uint8 nameLen}, {@code char[nameLen] path},
 *       {@code int32 fileSize}, {@code int64 fileTime}.</li>
 *   <li>Sequential raw file payloads immediately following {@code FILEFLAGS_END (0x80)}.</li>
 * </ul>
 *
 * <p>Also transparently unwraps {@code Feeding_Frenzy_Win_Preinstalled_EN.zip} when passed a ZIP!
 */
public final class PakArchiveReader {

    public static final int PAK_MAGIC = 0xBAC04AC0;
    public static final int PAK_XOR_KEY = 0xF7;
    public static final int FILEFLAGS_END = 0x80;
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    private final String sourceLabel;
    private final int version;
    private final boolean wasXorEncrypted;
    private final byte[] decodedBuffer;
    private final List<PakEntry> entries;
    private final Map<String, PakEntry> lowerPathLookup;
    private final byte[] companionExeBytes;
    private final String companionExeName;

    private PakArchiveReader(
            String sourceLabel,
            int version,
            boolean wasXorEncrypted,
            byte[] decodedBuffer,
            List<PakEntry> entries,
            byte[] companionExeBytes,
            String companionExeName) {
        this.sourceLabel = sourceLabel;
        this.version = version;
        this.wasXorEncrypted = wasXorEncrypted;
        this.decodedBuffer = decodedBuffer;
        this.entries = Collections.unmodifiableList(entries);
        this.companionExeBytes = companionExeBytes;
        this.companionExeName = companionExeName;

        Map<String, PakEntry> map = new LinkedHashMap<String, PakEntry>();
        for (int i = 0; i < entries.size(); i++) {
            PakEntry entry = entries.get(i);
            map.put(entry.normalizedPath.toLowerCase(Locale.US), entry);
        }
        this.lowerPathLookup = Collections.unmodifiableMap(map);
    }

    public static PakArchiveReader open(InputStream inputStream, String label) throws IOException {
        byte[] raw = readAllBytes(inputStream);
        return openBytes(raw, label);
    }

    public static PakArchiveReader openBytes(byte[] rawBytes, String label) throws IOException {
        if (rawBytes == null || rawBytes.length < 9) {
            throw new IOException("Input file too small to be a PopCap PAK or ZIP archive");
        }

        // Check PK\x03\x04 ZIP header (e.g. Feeding_Frenzy_Win_Preinstalled_EN.zip)
        if (rawBytes[0] == 0x50 && rawBytes[1] == 0x4B && rawBytes[2] == 0x03 && rawBytes[3] == 0x04) {
            return openFromZipBytes(rawBytes, label);
        }

        return parsePakBuffer(rawBytes, label, null, null);
    }

    private static PakArchiveReader openFromZipBytes(byte[] zipBytes, String label) throws IOException {
        byte[] foundPak = null;
        String foundPakName = null;
        byte[] foundExe = null;
        String foundExeName = null;

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        try {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.isDirectory()) {
                    continue;
                }
                String entryName = ze.getName();
                String low = entryName.toLowerCase(Locale.US);
                if (low.endsWith(".pak") && foundPak == null) {
                    foundPak = readAllBytes(zis);
                    foundPakName = entryName;
                } else if (low.endsWith(".exe") && (low.contains("frenzy") || foundExe == null)) {
                    foundExe = readAllBytes(zis);
                    foundExeName = entryName;
                }
            }
        } finally {
            zis.close();
        }

        if (foundPak == null) {
            throw new IOException("ZIP archive does not contain a PopCap .pak file (" + label + ")");
        }
        return parsePakBuffer(foundPak, label + "::" + foundPakName, foundExe, foundExeName);
    }

    private static PakArchiveReader parsePakBuffer(
            byte[] rawPak,
            String label,
            byte[] exeBytes,
            String exeName) throws IOException {
        int plainMagic = readInt32LE(rawPak, 0);
        byte[] decoded;
        boolean encrypted;
        if (plainMagic == PAK_MAGIC) {
            decoded = rawPak.clone();
            encrypted = false;
        } else {
            decoded = xorCopy(rawPak, PAK_XOR_KEY);
            int xorMagic = readInt32LE(decoded, 0);
            if (xorMagic != PAK_MAGIC) {
                throw new IOException(String.format(
                        Locale.US,
                        "Invalid PopCap PAK magic (plain=0x%08X, xor0xF7=0x%08X, expected=0x%08X)",
                        plainMagic, xorMagic, PAK_MAGIC));
            }
            encrypted = true;
        }

        int version = readInt32LE(decoded, 4);
        int pos = 8;

        List<String> rawNames = new ArrayList<String>();
        List<String> normPaths = new ArrayList<String>();
        List<Integer> sizes = new ArrayList<Integer>();
        List<Long> fileTimes = new ArrayList<Long>();

        while (pos < decoded.length) {
            int flags = decoded[pos] & 0xFF;
            pos++;
            if ((flags & FILEFLAGS_END) != 0) {
                break;
            }
            if (pos >= decoded.length) {
                throw new IOException("Truncated PopCap PAK header reading name length");
            }
            int nameLen = decoded[pos] & 0xFF;
            pos++;
            if (pos + nameLen + 12 > decoded.length) {
                throw new IOException("Truncated PopCap PAK directory entry at pos=" + pos);
            }
            String rawName = new String(decoded, pos, nameLen, LATIN1);
            pos += nameLen;

            String norm = rawName.replace('\\', '/');
            while (norm.startsWith("/")) {
                norm = norm.substring(1);
            }

            int fileSize = readInt32LE(decoded, pos);
            long fileTime = readInt64LE(decoded, pos + 4);
            pos += 12;

            rawNames.add(rawName);
            normPaths.add(norm);
            sizes.add(Integer.valueOf(fileSize));
            fileTimes.add(Long.valueOf(fileTime));
        }

        int dataCursor = pos;
        List<PakEntry> parsedEntries = new ArrayList<PakEntry>(rawNames.size());
        for (int i = 0; i < rawNames.size(); i++) {
            int sz = sizes.get(i).intValue();
            if (sz < 0 || dataCursor + sz > decoded.length) {
                throw new IOException("PAK entry payload out of bounds for " + normPaths.get(i));
            }
            String norm = normPaths.get(i);
            parsedEntries.add(new PakEntry(
                    i,
                    rawNames.get(i),
                    norm,
                    dataCursor,
                    sz,
                    fileTimes.get(i).longValue(),
                    PakEntry.classifyPath(norm)));
            dataCursor += sz;
        }

        return new PakArchiveReader(label, version, encrypted, decoded, parsedEntries, exeBytes, exeName);
    }

    /**
     * Packs an in-memory map of path -> payload bytes into a valid PopCap XOR-0xF7 {@code .pak} archive.
     */
    public static byte[] buildPak(Map<String, byte[]> files, boolean xorEncrypt) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeInt32LE(out, PAK_MAGIC);
        writeInt32LE(out, 0);

        for (Map.Entry<String, byte[]> item : files.entrySet()) {
            String winPath = item.getKey().replace('/', '\\');
            byte[] nameBytes = winPath.getBytes(LATIN1);
            if (nameBytes.length > 255) {
                throw new IOException("Path exceeds 255-byte PopCap PAK limit: " + winPath);
            }
            byte[] payload = item.getValue();
            out.write(0x00);
            out.write(nameBytes.length & 0xFF);
            out.write(nameBytes);
            writeInt32LE(out, payload.length);
            writeInt64LE(out, 127200000000000000L);
        }

        out.write(FILEFLAGS_END);
        for (Map.Entry<String, byte[]> item : files.entrySet()) {
            out.write(item.getValue());
        }

        byte[] plain = out.toByteArray();
        return xorEncrypt ? xorCopy(plain, PAK_XOR_KEY) : plain;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public int getVersion() {
        return version;
    }

    public boolean wasXorEncrypted() {
        return wasXorEncrypted;
    }

    public List<PakEntry> getEntries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    public boolean hasCompanionExe() {
        return companionExeBytes != null && companionExeBytes.length > 0;
    }

    public byte[] getCompanionExeBytes() {
        return companionExeBytes != null ? companionExeBytes.clone() : null;
    }

    public String getCompanionExeName() {
        return companionExeName;
    }

    public PakEntry findEntry(String path) {
        if (path == null) {
            return null;
        }
        String norm = path.replace('\\', '/');
        while (norm.startsWith("/")) {
            norm = norm.substring(1);
        }
        return lowerPathLookup.get(norm.toLowerCase(Locale.US));
    }

    /**
     * Finds PopCap companion alpha mask entry for a sprite image entry (e.g. {@code images/_minnow.gif}
     * or {@code images/minnow_.gif} for {@code images/minnow.jpg}).
     */
    public PakEntry findAlphaMaskFor(PakEntry spriteEntry) {
        if (spriteEntry == null) {
            return null;
        }
        String dir = spriteEntry.getDirectory();
        String file = spriteEntry.getFileName();
        int dot = file.lastIndexOf('.');
        String stem = dot > 0 ? file.substring(0, dot) : file;
        String prefix = dir.isEmpty() ? "" : (dir + "/");

        String[] candidates = new String[] {
            prefix + "_" + stem + ".gif",
            prefix + "_" + stem + ".png",
            prefix + "_" + stem + ".jpg",
            prefix + stem + "_.gif",
            prefix + stem + "_alpha.gif",
            prefix + stem + "_alpha.png"
        };
        for (int i = 0; i < candidates.length; i++) {
            PakEntry hit = findEntry(candidates[i]);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    public byte[] readEntryBytes(PakEntry entry) {
        byte[] slice = new byte[entry.size];
        System.arraycopy(decodedBuffer, entry.offset, slice, 0, entry.size);
        return slice;
    }

    public Map<String, Integer> countByCategory() {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < entries.size(); i++) {
            String cat = entries.get(i).category;
            Integer prev = counts.get(cat);
            counts.put(cat, Integer.valueOf(prev == null ? 1 : prev.intValue() + 1));
        }
        return counts;
    }

    public static byte[] xorCopy(byte[] src, int key) {
        byte[] dst = new byte[src.length];
        byte k = (byte) (key & 0xFF);
        for (int i = 0; i < src.length; i++) {
            dst[i] = (byte) (src[i] ^ k);
        }
        return dst;
    }

    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(16384);
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }

    static int readInt32LE(byte[] b, int off) {
        return (b[off] & 0xFF)
                | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16)
                | ((b[off + 3] & 0xFF) << 24);
    }

    static long readInt64LE(byte[] b, int off) {
        return ((long) readInt32LE(b, off) & 0xFFFFFFFFL)
                | (((long) readInt32LE(b, off + 4) & 0xFFFFFFFFL) << 32);
    }

    private static void writeInt32LE(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    private static void writeInt64LE(ByteArrayOutputStream out, long v) {
        writeInt32LE(out, (int) (v & 0xFFFFFFFFL));
        writeInt32LE(out, (int) ((v >>> 32) & 0xFFFFFFFFL));
    }
}
