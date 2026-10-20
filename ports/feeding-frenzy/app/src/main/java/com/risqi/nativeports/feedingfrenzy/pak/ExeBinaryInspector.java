package com.risqi.nativeports.feedingfrenzy.pak;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inspects Windows PE32 {@code FeedingFrenzy.exe} binaries directly on Android or JVM to identify
 * compiler build, section layout, and known {@code SexyAppBase} game state pointer chains.
 */
public final class ExeBinaryInspector {

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    public static final class InspectionResult {
        public final boolean validPe;
        public final int fileSizeBytes;
        public final int machine;
        public final int imageBase;
        public final int sizeOfImage;
        public final long compileTimestamp;
        public final String detectedBuild;
        public final List<String> sections;
        public final Map<String, String> memoryChains;

        public InspectionResult(
                boolean validPe,
                int fileSizeBytes,
                int machine,
                int imageBase,
                int sizeOfImage,
                long compileTimestamp,
                String detectedBuild,
                List<String> sections,
                Map<String, String> memoryChains) {
            this.validPe = validPe;
            this.fileSizeBytes = fileSizeBytes;
            this.machine = machine;
            this.imageBase = imageBase;
            this.sizeOfImage = sizeOfImage;
            this.compileTimestamp = compileTimestamp;
            this.detectedBuild = detectedBuild;
            this.sections = Collections.unmodifiableList(sections);
            this.memoryChains = Collections.unmodifiableMap(memoryChains);
        }

        public String toSummaryText() {
            if (!validPe) {
                return "Non-PE file (" + fileSizeBytes + " bytes)";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Build: ").append(detectedBuild).append('\n');
            sb.append(String.format(Locale.US, "PE32 ImageBase=0x%08X | SizeOfImage=%d bytes\n", imageBase, sizeOfImage));
            sb.append("Sections: ").append(sections).append('\n');
            for (Map.Entry<String, String> e : memoryChains.entrySet()) {
                sb.append("  • ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
            return sb.toString();
        }
    }

    private ExeBinaryInspector() {
    }

    public static InspectionResult inspect(byte[] exeBytes) {
        if (exeBytes == null || exeBytes.length < 0x100) {
            return invalid(exeBytes == null ? 0 : exeBytes.length);
        }
        if ((exeBytes[0] & 0xFF) != 'M' || (exeBytes[1] & 0xFF) != 'Z') {
            return invalid(exeBytes.length);
        }
        int peOffset = PakArchiveReader.readInt32LE(exeBytes, 0x3C);
        if (peOffset < 0 || peOffset + 24 > exeBytes.length) {
            return invalid(exeBytes.length);
        }
        if (exeBytes[peOffset] != 'P' || exeBytes[peOffset + 1] != 'E'
                || exeBytes[peOffset + 2] != 0 || exeBytes[peOffset + 3] != 0) {
            return invalid(exeBytes.length);
        }

        int machine = readUint16LE(exeBytes, peOffset + 4);
        int numSections = readUint16LE(exeBytes, peOffset + 6);
        long timestamp = PakArchiveReader.readInt32LE(exeBytes, peOffset + 8) & 0xFFFFFFFFL;
        int optHeaderSize = readUint16LE(exeBytes, peOffset + 20);
        int optOffset = peOffset + 24;
        if (optOffset + 60 > exeBytes.length) {
            return invalid(exeBytes.length);
        }

        int imageBase = PakArchiveReader.readInt32LE(exeBytes, optOffset + 28);
        int sizeOfImage = PakArchiveReader.readInt32LE(exeBytes, optOffset + 56);

        List<String> sections = new ArrayList<String>();
        int secBase = optOffset + optHeaderSize;
        for (int i = 0; i < numSections; i++) {
            int off = secBase + i * 40;
            if (off + 40 > exeBytes.length) {
                break;
            }
            int nameEnd = 0;
            while (nameEnd < 8 && exeBytes[off + nameEnd] != 0) {
                nameEnd++;
            }
            String secName = new String(exeBytes, off, nameEnd, LATIN1);
            int virtualSize = PakArchiveReader.readInt32LE(exeBytes, off + 8);
            int virtualAddr = PakArchiveReader.readInt32LE(exeBytes, off + 12);
            sections.add(String.format(Locale.US, "%s@0x%X(%dB)", secName, virtualAddr, virtualSize));
        }

        Map<String, String> chains = new LinkedHashMap<String, String>();
        String buildName;
        if (sizeOfImage >= 876000) {
            buildName = "FeedingFrenzy v5.7.18.1 (PopCap / GameHouse Retail)";
            chains.put("stageLoading", "exe+0xC10B4 -> +0x118 -> +0x178 -> +0x198");
            chains.put("isPlaying", "exe+0xC10B4 -> +0x118 -> +0x30 -> +0x10 -> +0xC -> +0x120 -> +0x4 -> +0x1D4");
            chains.put("stage", "exe+0xC0880 -> +0x1E4");
            chains.put("foodBank", "exe+0xC0880 -> +0x1A8");
            chains.put("timeAttackClock", "exe+0xC0880 -> +0x58 -> +0x10");
        } else if (sizeOfImage >= 872000) {
            buildName = "FeedingFrenzy v2.9.16.1 (PopCap Deluxe)";
            chains.put("stageLoading", "exe+0xBEE58 -> +0x118 -> +0x178 -> +0x198");
            chains.put("isPlaying", "exe+0xBEE58 -> +0x118 -> +0x30 -> +0x10 -> +0xC -> +0x120 -> +0x4 -> +0x1D4");
            chains.put("stage", "exe+0xBE980 -> +0x1C0");
            chains.put("foodBank", "exe+0xBE980 -> +0x184");
            chains.put("timeAttackClock", "exe+0xBE980 -> +0x34 -> +0x10");
        } else if (sizeOfImage >= 843000) {
            buildName = "FeedingFrenzy v1.0 (Sprout / Early Preinstalled EN)";
            chains.put("stageLoading", "exe+0xB937C -> +0x118 -> +0x178 -> +0x198");
            chains.put("stage", "exe+0xB894C -> +0x128");
            chains.put("foodBank", "exe+0xB894C -> +0xEC");
            chains.put("timeAttackClock", "exe+0xB894C -> +0x24 -> +0x10");
        } else {
            buildName = "PE32 x86 Binary (SizeOfImage=" + sizeOfImage + ")";
        }

        return new InspectionResult(
                true,
                exeBytes.length,
                machine,
                imageBase,
                sizeOfImage,
                timestamp,
                buildName,
                sections,
                chains);
    }

    private static InspectionResult invalid(int len) {
        return new InspectionResult(
                false,
                len,
                0,
                0,
                0,
                0L,
                "Invalid/Unknown",
                Collections.<String>emptyList(),
                Collections.<String, String>emptyMap());
    }

    private static int readUint16LE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }
}
