package com.example.dfdl.service;

import com.example.dfdl.dto.BinaryCompareResponse;
import com.example.dfdl.dto.BinaryCompareResponse.CheckResult;
import com.example.dfdl.dto.BinaryCompareResponse.FileSummary;
import com.example.dfdl.dto.BinaryCompareResponse.SegmentDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares a client-shared ACE SMPRES binary with an unparse-generated binary.
 * Decodes both as EBCDIC IBM037 and reports structural / segment-level matches.
 */
@Service
public class BinaryCompareService {

    private static final Logger log = LoggerFactory.getLogger(BinaryCompareService.class);
    private static final Charset IBM037 = Charset.forName("IBM037");
    private static final Pattern MSG_TYPE = Pattern.compile("UNH\\+[^+]*\\+([A-Z0-9]+):");

    public BinaryCompareResponse compare(
            byte[] clientBytes,
            String clientFileName,
            byte[] unparseBytes,
            String unparseFileName) {

        if (clientBytes == null || clientBytes.length == 0) {
            throw new IllegalArgumentException("Client binary is empty");
        }
        if (unparseBytes == null || unparseBytes.length == 0) {
            throw new IllegalArgumentException("Unparse binary is empty");
        }

        log.info(
                "Comparing client='{}' ({} bytes) vs unparse='{}' ({} bytes)",
                clientFileName,
                clientBytes.length,
                unparseFileName,
                unparseBytes.length);

        List<String> clientSegs = segments(clientBytes);
        List<String> unparseSegs = segments(unparseBytes);

        BinaryCompareResponse response = new BinaryCompareResponse();
        response.setSuccess(true);
        response.setEncoding("IBM037 (EBCDIC)");
        response.setClientFile(summarize(clientFileName, clientBytes, clientSegs));
        response.setUnparseFile(summarize(unparseFileName, unparseBytes, unparseSegs));

        Map<String, CheckResult> checks = response.getChecks();
        List<String> matches = response.getMatches();
        List<String> differences = response.getDifferences();

        boolean byteIdentical = Arrays.equals(clientBytes, unparseBytes);
        addCheck(checks, matches, differences, "byteIdentical", byteIdentical,
                byteIdentical ? "Files are byte-for-byte identical" : "Files differ at byte level (expected if different transaction)");

        boolean bothEbcdic = response.getClientFile().isEbcdicIbm037() && response.getUnparseFile().isEbcdicIbm037();
        addCheck(checks, matches, differences, "encodingIbm037", bothEbcdic,
                bothEbcdic
                        ? "Both files decode as EBCDIC IBM037 starting with UNB+"
                        : "One or both files do not look like EBCDIC IBM037 UNB");

        boolean sameMsgType = Objects.equals(
                response.getClientFile().getMessageType(),
                response.getUnparseFile().getMessageType());
        addCheck(checks, matches, differences, "messageType", sameMsgType,
                "client=" + response.getClientFile().getMessageType()
                        + ", unparse=" + response.getUnparseFile().getMessageType());

        boolean sameSegCount = clientSegs.size() == unparseSegs.size();
        addCheck(checks, matches, differences, "segmentCount", sameSegCount,
                "client=" + clientSegs.size() + ", unparse=" + unparseSegs.size());

        boolean sameTags = response.getClientFile().getTagCounts()
                .equals(response.getUnparseFile().getTagCounts());
        addCheck(checks, matches, differences, "tagCounts", sameTags,
                "client=" + response.getClientFile().getTagCounts()
                        + ", unparse=" + response.getUnparseFile().getTagCounts());

        boolean noOrg = !hasTag(unparseSegs, "ORG");
        addCheck(checks, matches, differences, "noOrgInUnparse", noOrg,
                noOrg ? "Unparse has no ORG (matches ACE client layout)" : "Unparse unexpectedly contains ORG");

        compareExactFirst(checks, matches, differences, response.getExactSegments(),
                "TVL", clientSegs, unparseSegs);
        compareExactFirst(checks, matches, differences, response.getExactSegments(),
                "EQI", clientSegs, unparseSegs);
        compareExactFirst(checks, matches, differences, response.getExactSegments(),
                "CBD", clientSegs, unparseSegs);
        compareExactFirst(checks, matches, differences, response.getExactSegments(),
                "UNT", clientSegs, unparseSegs);

        List<String> clientRodSkel = rodSkeleton(clientSegs);
        List<String> unparseRodSkel = rodSkeleton(unparseSegs);
        boolean rodSkeletonMatch = clientRodSkel.equals(unparseRodSkel);
        addCheck(checks, matches, differences, "rodSkeleton", rodSkeletonMatch,
                rodSkeletonMatch
                        ? "ROD row structure matches (including Z and E rows): " + String.join(", ", clientRodSkel)
                        : "ROD row structure differs");

        boolean structureMatch = sameMsgType && sameSegCount && sameTags && rodSkeletonMatch
                && checks.get("TVL").isPassed()
                && checks.get("EQI").isPassed()
                && checks.get("CBD").isPassed();
        addCheck(checks, matches, differences, "aceStructureMatch", structureMatch,
                structureMatch
                        ? "ACE SMPRES layout matches client sample (structure + key segments)"
                        : "ACE structure does not fully match client sample");

        response.setSegmentDiffs(buildSegmentDiffs(clientSegs, unparseSegs));

        int passed = 0;
        int total = checks.size();
        for (CheckResult check : checks.values()) {
            if (check.isPassed()) {
                passed++;
            }
        }
        int percent = total == 0 ? 0 : (int) Math.round((passed * 100.0) / total);
        response.setMatchPercent(percent);

        if (byteIdentical) {
            response.setVerdict("IDENTICAL");
            response.setSummary("Unparse binary is byte-identical to the client shared file.");
        } else if (structureMatch && percent >= 80) {
            response.setVerdict("STRUCTURALLY_MATCHED");
            response.setSummary(
                    "Unparse binary matches the client ACE SMPRES layout. "
                            + "Differences are mainly transaction metadata (UNB/UNH/UNZ refs, time) "
                            + "and/or seat occupation values from different inventory snapshots.");
        } else if (sameMsgType && bothEbcdic) {
            response.setVerdict("PARTIAL_MATCH");
            response.setSummary(
                    "Same encoding and message type (SMPRES), but some ACE layout checks failed. See differences.");
        } else {
            response.setVerdict("MISMATCH");
            response.setSummary("Binaries do not match client ACE SMPRES expectations. See differences.");
        }

        return response;
    }

    private static FileSummary summarize(String fileName, byte[] bytes, List<String> segs) {
        FileSummary summary = new FileSummary();
        summary.setFileName(fileName != null ? fileName : "unknown.bin");
        summary.setSizeBytes(bytes.length);
        summary.setSegmentCount(segs.size());
        summary.setFirstBytesHex(toHex(bytes, 3));
        String text = new String(bytes, IBM037);
        summary.setEbcdicIbm037(text.startsWith("UNB+"));
        summary.setMessageType(extractMessageType(segs));
        summary.setTagCounts(tagCounts(segs));
        return summary;
    }

    private static List<String> segments(byte[] bytes) {
        String text = new String(bytes, IBM037);
        List<String> segs = new ArrayList<>();
        for (String part : text.split("'", -1)) {
            if (part != null && !part.isEmpty()) {
                segs.add(part);
            }
        }
        return segs;
    }

    private static Map<String, Integer> tagCounts(List<String> segs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String seg : segs) {
            String tag = tagOf(seg);
            counts.merge(tag, 1, Integer::sum);
        }
        return counts;
    }

    private static String tagOf(String seg) {
        if (seg == null || seg.isBlank()) {
            return "";
        }
        int plus = seg.indexOf('+');
        return plus < 0 ? seg : seg.substring(0, plus);
    }

    private static String extractMessageType(List<String> segs) {
        for (String seg : segs) {
            if (seg.startsWith("UNH")) {
                Matcher m = MSG_TYPE.matcher(seg);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return "UNKNOWN";
    }

    private static boolean hasTag(List<String> segs, String tag) {
        for (String seg : segs) {
            if (tagOf(seg).equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private static String firstOf(List<String> segs, String tag) {
        for (String seg : segs) {
            if (tagOf(seg).equals(tag)) {
                return seg;
            }
        }
        return null;
    }

    private static void compareExactFirst(
            Map<String, CheckResult> checks,
            List<String> matches,
            List<String> differences,
            Map<String, String> exactSegments,
            String tag,
            List<String> clientSegs,
            List<String> unparseSegs) {
        String client = firstOf(clientSegs, tag);
        String unparse = firstOf(unparseSegs, tag);
        boolean passed = Objects.equals(client, unparse) && client != null;
        if (passed) {
            exactSegments.put(tag, client);
        }
        String detail;
        if (client == null && unparse == null) {
            detail = "Missing in both";
            passed = true;
        } else if (passed) {
            detail = "EXACT: " + client;
        } else {
            detail = "client=" + client + " | unparse=" + unparse;
        }
        addCheck(checks, matches, differences, tag, passed, detail);
    }

    private static List<String> rodSkeleton(List<String> segs) {
        List<String> skeleton = new ArrayList<>();
        for (String seg : segs) {
            if (!seg.startsWith("ROD+")) {
                continue;
            }
            if (seg.matches("ROD\\+\\d+\\+Z")) {
                skeleton.add(seg);
            } else if (seg.matches("ROD\\+\\d+\\+E\\+.*")) {
                Matcher m = Pattern.compile("ROD\\+(\\d+)\\+E\\+").matcher(seg);
                skeleton.add(m.find() ? "ROD+" + m.group(1) + "+E" : "ROD+E");
            } else {
                Matcher m = Pattern.compile("ROD\\+(\\d+)\\+\\+").matcher(seg);
                skeleton.add(m.find() ? "ROD+" + m.group(1) + "++" : "ROD++");
            }
        }
        return skeleton;
    }

    private static List<SegmentDiff> buildSegmentDiffs(List<String> clientSegs, List<String> unparseSegs) {
        List<SegmentDiff> diffs = new ArrayList<>();
        int max = Math.max(clientSegs.size(), unparseSegs.size());
        for (int i = 0; i < max; i++) {
            String client = i < clientSegs.size() ? clientSegs.get(i) : null;
            String unparse = i < unparseSegs.size() ? unparseSegs.get(i) : null;
            String tag = tagOf(client != null ? client : unparse);

            SegmentDiff diff = new SegmentDiff();
            diff.setIndex(i + 1);
            diff.setTag(tag);
            diff.setClient(client);
            diff.setUnparse(unparse);

            if (client == null) {
                diff.setStatus("EXTRA_IN_UNPARSE");
                diff.setNote("Segment present only in unparse file");
                diffs.add(diff);
            } else if (unparse == null) {
                diff.setStatus("MISSING_IN_UNPARSE");
                diff.setNote("Segment present only in client file");
                diffs.add(diff);
            } else if (client.equals(unparse)) {
                diff.setStatus("EXACT");
                diff.setNote("Identical segment");
                // Keep exact structural segments; skip flooding with every identical ROD
                if (!tag.equals("ROD") || i < 8) {
                    diffs.add(diff);
                }
            } else if (Objects.equals(normalizeForStructure(client), normalizeForStructure(unparse))) {
                diff.setStatus("STRUCTURAL_MATCH");
                diff.setNote("Same structure; dynamic values differ (refs/time/seat occupation)");
                diffs.add(diff);
            } else if (tagOf(client).equals(tagOf(unparse))) {
                diff.setStatus("CONTENT_DIFF");
                diff.setNote(noteForTag(tag));
                diffs.add(diff);
            } else {
                diff.setStatus("TAG_MISMATCH");
                diff.setNote("Different segment tags at this position");
                diffs.add(diff);
            }
        }
        return diffs;
    }

    private static String normalizeForStructure(String seg) {
        if (seg == null) {
            return null;
        }
        String tag = tagOf(seg);
        if ("UNB".equals(tag)) {
            return "UNB+IATB:1+UA1SM+RECIP+DATE:TIME+REF+REF++T";
        }
        if ("UNH".equals(tag)) {
            return "UNH+1+SMPRES:93:2:IA+REF";
        }
        if ("UNZ".equals(tag)) {
            return "UNZ+1+REF";
        }
        if ("ROD".equals(tag)) {
            if (seg.matches("ROD\\+\\d+\\+Z")) {
                return seg;
            }
            if (seg.matches("ROD\\+\\d+\\+E\\+.*")) {
                Matcher m = Pattern.compile("ROD\\+(\\d+)\\+E\\+").matcher(seg);
                return m.find() ? "ROD+" + m.group(1) + "+E+SEATS" : seg;
            }
            Matcher m = Pattern.compile("ROD\\+(\\d+)\\+\\+").matcher(seg);
            return m.find() ? "ROD+" + m.group(1) + "++SEATS" : seg;
        }
        return seg;
    }

    private static String noteForTag(String tag) {
        return switch (tag) {
            case "UNB", "UNH", "UNZ" -> "Expected for different transaction id / time / recipient";
            case "ROD" -> "Seat occupation/characteristics may differ if JSON inventory differs from client snapshot";
            case "TVL", "EQI", "CBD", "UNT" -> "Unexpected content difference for ACE layout segment";
            default -> "Segment content differs";
        };
    }

    private static void addCheck(
            Map<String, CheckResult> checks,
            List<String> matches,
            List<String> differences,
            String name,
            boolean passed,
            String detail) {
        checks.put(name, new CheckResult(passed, detail));
        if (passed) {
            matches.add(name + ": " + detail);
        } else {
            differences.add(name + ": " + detail);
        }
    }

    private static String toHex(byte[] bytes, int count) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(count, bytes.length);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.ROOT, "0x%02X", bytes[i]));
        }
        return sb.toString();
    }
}
