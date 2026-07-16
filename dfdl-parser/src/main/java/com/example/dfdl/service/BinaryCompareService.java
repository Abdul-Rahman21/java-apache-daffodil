package com.example.dfdl.service;

import com.example.dfdl.dto.BinaryCompareResponse;
import com.example.dfdl.dto.BinaryCompareResponse.CheckResult;
import com.example.dfdl.dto.BinaryCompareResponse.FailedCheck;
import com.example.dfdl.dto.BinaryCompareResponse.FieldMismatch;
import com.example.dfdl.dto.BinaryCompareResponse.FileSummary;
import com.example.dfdl.dto.BinaryCompareResponse.MismatchDetails;
import com.example.dfdl.dto.BinaryCompareResponse.SegmentDiff;
import com.example.dfdl.dto.ExtractedBinaryJson;
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

        ExtractedBinaryJson clientJson = extractJson(clientFileName, clientBytes, clientSegs);
        ExtractedBinaryJson unparseJson = extractJson(unparseFileName, unparseBytes, unparseSegs);
        response.setClientJson(clientJson);
        response.setUnparseJson(unparseJson);
        response.setMismatchedValues(buildFieldMismatches(clientJson, unparseJson));

        int passed = 0;
        int total = checks.size();
        for (CheckResult check : checks.values()) {
            if (check.isPassed()) {
                passed++;
            }
        }
        int percent = total == 0 ? 0 : (int) Math.round((passed * 100.0) / total);
        int mismatchPercent = 100 - percent;
        response.setMatchPercent(percent);
        response.setMismatchPercent(mismatchPercent);

        if (mismatchPercent > 0) {
            MismatchDetails details = buildMismatchDetails(checks, total, passed, mismatchPercent);
            details.setMismatchedValues(response.getMismatchedValues());
            details.setWhyNotMatched(enrichWhyNotMatched(details.getWhyNotMatched(), response.getMismatchedValues()));
            if (!response.getMismatchedValues().isEmpty()) {
                details.setReason(
                        details.getReason()
                                + " mismatchedValues lists "
                                + response.getMismatchedValues().size()
                                + " JSON field difference(s).");
            }
            response.setMismatchDetails(details);
        }

        if (byteIdentical) {
            response.setVerdict("IDENTICAL");
            response.setSummary("Unparse binary is byte-identical to the client shared file.");
        } else if (structureMatch && percent >= 80) {
            response.setVerdict("STRUCTURALLY_MATCHED");
            response.setSummary(
                    "Unparse binary matches the client ACE SMPRES layout. "
                            + "See mismatchedValues for exact JSON field differences "
                            + "(usually UNB/UNH/UNZ refs, time, or ROD seat occupation).");
        } else if (sameMsgType && bothEbcdic) {
            response.setVerdict("PARTIAL_MATCH");
            response.setSummary(
                    "Same encoding and message type (SMPRES), but some ACE layout checks failed. "
                            + "See mismatchDetails and mismatchedValues.");
        } else {
            response.setVerdict("MISMATCH");
            response.setSummary(
                    "Binaries do not match client ACE SMPRES expectations. "
                            + "See mismatchDetails and mismatchedValues.");
        }

        return response;
    }

    private static MismatchDetails buildMismatchDetails(
            Map<String, CheckResult> checks,
            int total,
            int passed,
            int mismatchPercent) {
        MismatchDetails details = new MismatchDetails();
        details.setTotalCheckCount(total);
        details.setFailedCheckCount(total - passed);

        List<FailedCheck> failedChecks = new ArrayList<>();
        List<String> whyNotMatched = new ArrayList<>();
        for (Map.Entry<String, CheckResult> entry : checks.entrySet()) {
            CheckResult result = entry.getValue();
            if (result.isPassed()) {
                continue;
            }
            String checkName = entry.getKey();
            String detail = result.getDetail();
            String impact = impactForCheck(checkName);
            failedChecks.add(new FailedCheck(checkName, detail, impact));
            whyNotMatched.add(checkName + ": " + detail + " — " + impact);
        }
        details.setFailedChecks(failedChecks);
        details.setWhyNotMatched(whyNotMatched);
        details.setReason(
                mismatchPercent + "% of structural checks did not pass ("
                        + (total - passed) + " of " + total + " failed). "
                        + "See failedChecks / whyNotMatched for per-check causes.");
        return details;
    }

    private static String impactForCheck(String checkName) {
        return switch (checkName) {
            case "byteIdentical" ->
                    "Not a layout failure by itself — expected when transaction ids, timestamps, or seat inventory differ.";
            case "encodingIbm037" ->
                    "One or both files are not valid EBCDIC IBM037 SMPRES; decode / schema may be wrong.";
            case "messageType" ->
                    "Message types differ (e.g. SMPREQ vs SMPRES); binaries are not the same ACE message.";
            case "segmentCount" ->
                    "Different number of EDIFACT segments; body structure is incomplete or extra segments exist.";
            case "tagCounts" ->
                    "Tag mix differs (missing/extra TVL, EQI, CBD, ROD, etc.); ACE layout is not aligned.";
            case "noOrgInUnparse" ->
                    "Client ACE SMPRES sample has no ORG; unparse should omit ORG as well.";
            case "TVL", "EQI", "CBD", "UNT" ->
                    "This ACE layout segment must match the client sample exactly for a full structural match.";
            case "rodSkeleton" ->
                    "ROD row map (including Z facility / E exit rows) differs from the client sample.";
            case "aceStructureMatch" ->
                    "Overall ACE structure check failed because one or more of messageType, segmentCount, tagCounts, TVL, EQI, CBD, or rodSkeleton failed.";
            default -> "This check failed; inspect detail for client vs unparse values.";
        };
    }

    private static List<String> enrichWhyNotMatched(
            List<String> whyNotMatched,
            List<FieldMismatch> mismatchedValues) {
        List<String> enriched = new ArrayList<>(whyNotMatched != null ? whyNotMatched : List.of());
        int shown = 0;
        for (FieldMismatch mismatch : mismatchedValues) {
            if (shown >= 8) {
                enriched.add("...and " + (mismatchedValues.size() - shown) + " more field difference(s) in mismatchedValues");
                break;
            }
            enriched.add(
                    "field " + mismatch.getPath()
                            + ": client=" + mismatch.getClientValue()
                            + ", unparse=" + mismatch.getUnparseValue()
                            + " (" + mismatch.getCategory() + ")");
            shown++;
        }
        return enriched;
    }

    private static ExtractedBinaryJson extractJson(String fileName, byte[] bytes, List<String> segs) {
        ExtractedBinaryJson json = new ExtractedBinaryJson();
        json.setSource(fileName != null ? fileName : "unknown.bin");
        json.setEncoding("IBM037");
        json.setSizeBytes(bytes.length);
        json.setSegmentCount(segs.size());
        json.setTagCounts(tagCounts(segs));

        Map<String, Object> envelope = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        for (String raw : segs) {
            String tag = tagOf(raw);
            switch (tag) {
                case "UNB" -> envelope.put("UNB", parseUnb(raw));
                case "UNH" -> envelope.put("UNH", parseUnh(raw));
                case "UNT" -> envelope.put("UNT", parseUnt(raw));
                case "UNZ" -> envelope.put("UNZ", parseUnz(raw));
                case "TVL" -> json.setFlight(parseTvl(raw));
                case "EQI" -> json.setAircraft(parseEqi(raw));
                case "CBD" -> json.setCabin(parseCbd(raw));
                case "ROD" -> rows.add(parseRod(raw));
                default -> {
                }
            }
        }
        json.setEnvelope(envelope);
        json.setRows(rows);
        return json;
    }

    private static Map<String, Object> parseUnb(String raw) {
        String[] p = raw.split("\\+", -1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raw", raw);
        m.put("syntax", p.length > 1 ? p[1] : null);
        m.put("sender", p.length > 2 ? p[2] : null);
        m.put("recipient", p.length > 3 ? p[3] : null);
        if (p.length > 4) {
            String[] dt = p[4].split(":", -1);
            m.put("date", dt[0]);
            m.put("time", dt.length > 1 ? dt[1] : null);
        }
        m.put("interchangeRef", p.length > 5 ? p[5] : null);
        m.put("applicationRef", p.length > 6 && !p[6].isEmpty() ? p[6] : null);
        m.put("testIndicator", p.length > 9 ? p[9] : (p.length > 0 ? p[p.length - 1] : null));
        return m;
    }

    private static Map<String, Object> parseUnh(String raw) {
        String[] p = raw.split("\\+", -1);
        String[] type = p.length > 2 ? p[2].split(":", -1) : new String[0];
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raw", raw);
        m.put("messageRef", p.length > 1 ? p[1] : null);
        m.put("messageType", type.length > 0 ? type[0] : null);
        m.put("version", type.length > 1 ? type[1] : null);
        m.put("release", type.length > 2 ? type[2] : null);
        m.put("agency", type.length > 3 ? type[3] : null);
        m.put("accessRef", p.length > 3 ? p[3] : null);
        return m;
    }

    private static Map<String, Object> parseUnt(String raw) {
        String[] p = raw.split("\\+", -1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raw", raw);
        m.put("segmentCount", p.length > 1 ? p[1] : null);
        m.put("messageRef", p.length > 2 ? p[2] : null);
        return m;
    }

    private static Map<String, Object> parseUnz(String raw) {
        String[] p = raw.split("\\+", -1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raw", raw);
        m.put("controlCount", p.length > 1 ? p[1] : null);
        m.put("interchangeRef", p.length > 2 ? p[2] : null);
        return m;
    }

    private static Map<String, Object> parseTvl(String raw) {
        String[] p = raw.split("\\+", -1);
        String ddmmyy = p.length > 1 ? p[1] : "";
        String iso = ddmmyy.length() == 6
                ? "20" + ddmmyy.substring(4, 6) + "-" + ddmmyy.substring(2, 4) + "-" + ddmmyy.substring(0, 2)
                : null;
        String[] flight = p.length > 5 ? p[5].split(":", -1) : new String[0];
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raw", raw);
        m.put("dateDdmmyy", ddmmyy);
        m.put("departureDateIso", iso);
        m.put("departureAirport", p.length > 2 ? p[2] : null);
        m.put("arrivalAirport", p.length > 3 ? p[3] : null);
        m.put("carrier", p.length > 4 ? p[4] : null);
        m.put("flightNumber", flight.length > 0 ? flight[0] : null);
        m.put("characteristic", flight.length > 1 ? flight[1] : null);
        return m;
    }

    private static Map<String, Object> parseEqi(String raw) {
        String[] p = raw.split("\\+", -1);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raw", raw);
        m.put("icr", p[p.length - 1]);
        return m;
    }

    private static Map<String, Object> parseCbd(String raw) {
        String[] p = raw.split("\\+", -1);
        String cabinType = p.length > 1 ? p[1].split(":", -1)[0] : null;
        String[] rowRange = p.length > 2 ? p[2].split(":", -1) : new String[0];
        String[] wingRange = p.length > 6 ? p[6].split(":", -1) : new String[0];
        List<Map<String, String>> columns = new ArrayList<>();
        for (int i = 7; i < p.length; i++) {
            String[] col = p[i].split(":", -1);
            if (!col[0].isEmpty()) {
                Map<String, String> column = new LinkedHashMap<>();
                column.put("column", col[0]);
                column.put("description", col.length > 1 ? col[1] : "");
                columns.add(column);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raw", raw);
        m.put("cabinType", cabinType);
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("first", rowRange.length > 0 ? rowRange[0] : null);
        range.put("last", rowRange.length > 1 ? rowRange[1] : null);
        m.put("rowRange", range);
        if (wingRange.length > 0 && !wingRange[0].isEmpty()) {
            Map<String, Object> wing = new LinkedHashMap<>();
            wing.put("first", wingRange[0]);
            wing.put("last", wingRange.length > 1 ? wingRange[1] : null);
            m.put("wingRange", wing);
        }
        m.put("columns", columns);
        return m;
    }

    private static Map<String, Object> parseRod(String raw) {
        String[] p = raw.split("\\+", -1);
        int rowNumber = 0;
        try {
            rowNumber = p.length > 1 ? Integer.parseInt(p[1]) : 0;
        } catch (NumberFormatException ignored) {
            rowNumber = 0;
        }
        String rowCharacteristic = null;
        int seatStart = 2;
        if (p.length > 2 && !p[2].contains(":")) {
            rowCharacteristic = p[2];
            seatStart = 3;
        }
        List<Map<String, Object>> seats = new ArrayList<>();
        for (int i = seatStart; i < p.length; i++) {
            String[] parts = p[i].split(":", -1);
            if (parts.length >= 2) {
                Map<String, Object> seat = new LinkedHashMap<>();
                seat.put("column", parts[0]);
                seat.put("occupation", parts[1]);
                List<String> chars = new ArrayList<>();
                for (int j = 2; j < parts.length; j++) {
                    chars.add(parts[j]);
                }
                seat.put("characteristics", chars);
                seats.add(seat);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raw", raw);
        m.put("rowNumber", rowNumber);
        if (rowCharacteristic != null) {
            m.put("rowCharacteristic", rowCharacteristic);
        }
        m.put("seats", seats);
        return m;
    }

    private static List<FieldMismatch> buildFieldMismatches(
            ExtractedBinaryJson client,
            ExtractedBinaryJson unparse) {
        List<FieldMismatch> diffs = new ArrayList<>();

        compareScalar(diffs, "sizeBytes", client.getSizeBytes(), unparse.getSizeBytes(),
                "METADATA", "File size differs after unparse");
        compareScalar(diffs, "segmentCount", client.getSegmentCount(), unparse.getSegmentCount(),
                "STRUCTURE", "EDIFACT segment count differs");
        compareScalar(diffs, "tagCounts", client.getTagCounts(), unparse.getTagCounts(),
                "STRUCTURE", "Segment tag mix differs");

        compareMapFields(diffs, "envelope.UNB", mapOf(client.getEnvelope(), "UNB"), mapOf(unparse.getEnvelope(), "UNB"),
                "TRANSACTION", "UNB dynamic fields often differ (recipient/time/refs)");
        compareMapFields(diffs, "envelope.UNH", mapOf(client.getEnvelope(), "UNH"), mapOf(unparse.getEnvelope(), "UNH"),
                "TRANSACTION", "UNH accessRef usually differs per transaction");
        compareMapFields(diffs, "envelope.UNT", mapOf(client.getEnvelope(), "UNT"), mapOf(unparse.getEnvelope(), "UNT"),
                "STRUCTURE", "UNT segment count / message ref");
        compareMapFields(diffs, "envelope.UNZ", mapOf(client.getEnvelope(), "UNZ"), mapOf(unparse.getEnvelope(), "UNZ"),
                "TRANSACTION", "UNZ interchange ref usually differs per transaction");

        compareMapFields(diffs, "flight", client.getFlight(), unparse.getFlight(),
                "LAYOUT", "TVL flight fields should normally match the client sample");
        compareMapFields(diffs, "aircraft", client.getAircraft(), unparse.getAircraft(),
                "LAYOUT", "EQI aircraft ICR should normally match the client sample");
        compareMapFields(diffs, "cabin", client.getCabin(), unparse.getCabin(),
                "LAYOUT", "CBD cabin layout should normally match the client sample");

        compareRows(diffs, client.getRows(), unparse.getRows());
        return diffs;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Map<String, Object> parent, String key) {
        if (parent == null) {
            return null;
        }
        Object value = parent.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static void compareRows(
            List<FieldMismatch> diffs,
            List<Map<String, Object>> clientRows,
            List<Map<String, Object>> unparseRows) {
        Map<Integer, Map<String, Object>> clientByRow = indexRows(clientRows);
        Map<Integer, Map<String, Object>> unparseByRow = indexRows(unparseRows);

        for (Integer rowNumber : clientByRow.keySet()) {
            if (!unparseByRow.containsKey(rowNumber)) {
                diffs.add(new FieldMismatch(
                        "rows[" + rowNumber + "]",
                        clientByRow.get(rowNumber).get("raw"),
                        null,
                        "SEATMAP",
                        "Row present in client binary but missing in unparse binary"));
            }
        }
        for (Integer rowNumber : unparseByRow.keySet()) {
            if (!clientByRow.containsKey(rowNumber)) {
                diffs.add(new FieldMismatch(
                        "rows[" + rowNumber + "]",
                        null,
                        unparseByRow.get(rowNumber).get("raw"),
                        "SEATMAP",
                        "Row present in unparse binary but missing in client binary"));
            }
        }

        for (Map.Entry<Integer, Map<String, Object>> entry : clientByRow.entrySet()) {
            Integer rowNumber = entry.getKey();
            Map<String, Object> unparseRow = unparseByRow.get(rowNumber);
            if (unparseRow == null) {
                continue;
            }
            Map<String, Object> clientRow = entry.getValue();
            compareScalar(diffs,
                    "rows[" + rowNumber + "].rowCharacteristic",
                    clientRow.get("rowCharacteristic"),
                    unparseRow.get("rowCharacteristic"),
                    "SEATMAP",
                    "Row characteristic differs (Z facility / E exit / blank)");
            compareSeats(diffs, rowNumber, clientRow.get("seats"), unparseRow.get("seats"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void compareSeats(
            List<FieldMismatch> diffs,
            int rowNumber,
            Object clientSeatsObj,
            Object unparseSeatsObj) {
        List<Map<String, Object>> clientSeats = asSeatList(clientSeatsObj);
        List<Map<String, Object>> unparseSeats = asSeatList(unparseSeatsObj);
        Map<String, Map<String, Object>> clientByCol = indexSeats(clientSeats);
        Map<String, Map<String, Object>> unparseByCol = indexSeats(unparseSeats);

        for (String column : clientByCol.keySet()) {
            Map<String, Object> clientSeat = clientByCol.get(column);
            Map<String, Object> unparseSeat = unparseByCol.get(column);
            if (unparseSeat == null) {
                diffs.add(new FieldMismatch(
                        "rows[" + rowNumber + "].seats." + column,
                        clientSeat,
                        null,
                        "SEATMAP",
                        "Seat column present in client but missing in unparse"));
                continue;
            }
            compareScalar(diffs,
                    "rows[" + rowNumber + "].seats." + column + ".occupation",
                    clientSeat.get("occupation"),
                    unparseSeat.get("occupation"),
                    "SEATMAP",
                    "Seat occupation differs (F free / O occupied) — often expected if inventory snapshot differs");
            compareScalar(diffs,
                    "rows[" + rowNumber + "].seats." + column + ".characteristics",
                    clientSeat.get("characteristics"),
                    unparseSeat.get("characteristics"),
                    "SEATMAP",
                    "Seat characteristics differ (CH/9/E/8/1) — often expected if inventory snapshot differs");
        }
        for (String column : unparseByCol.keySet()) {
            if (!clientByCol.containsKey(column)) {
                diffs.add(new FieldMismatch(
                        "rows[" + rowNumber + "].seats." + column,
                        null,
                        unparseByCol.get(column),
                        "SEATMAP",
                        "Seat column present in unparse but missing in client"));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asSeatList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> seats = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                seats.add((Map<String, Object>) map);
            }
        }
        return seats;
    }

    private static Map<String, Map<String, Object>> indexSeats(List<Map<String, Object>> seats) {
        Map<String, Map<String, Object>> byColumn = new LinkedHashMap<>();
        for (Map<String, Object> seat : seats) {
            Object column = seat.get("column");
            if (column != null) {
                byColumn.put(String.valueOf(column), seat);
            }
        }
        return byColumn;
    }

    private static Map<Integer, Map<String, Object>> indexRows(List<Map<String, Object>> rows) {
        Map<Integer, Map<String, Object>> byNumber = new LinkedHashMap<>();
        if (rows == null) {
            return byNumber;
        }
        for (Map<String, Object> row : rows) {
            Object number = row.get("rowNumber");
            if (number instanceof Number n) {
                byNumber.put(n.intValue(), row);
            }
        }
        return byNumber;
    }

    private static void compareMapFields(
            List<FieldMismatch> diffs,
            String prefix,
            Map<String, Object> client,
            Map<String, Object> unparse,
            String category,
            String explanation) {
        if (client == null && unparse == null) {
            return;
        }
        if (client == null || unparse == null) {
            diffs.add(new FieldMismatch(prefix, client, unparse, category, explanation));
            return;
        }
        for (String key : client.keySet()) {
            if ("raw".equals(key)) {
                continue;
            }
            Object clientVal = client.get(key);
            Object unparseVal = unparse.get(key);
            if (!Objects.equals(stringify(clientVal), stringify(unparseVal))) {
                diffs.add(new FieldMismatch(
                        prefix + "." + key,
                        clientVal,
                        unparseVal,
                        category,
                        explanation));
            }
        }
        for (String key : unparse.keySet()) {
            if ("raw".equals(key) || client.containsKey(key)) {
                continue;
            }
            diffs.add(new FieldMismatch(
                    prefix + "." + key,
                    null,
                    unparse.get(key),
                    category,
                    explanation));
        }
    }

    private static void compareScalar(
            List<FieldMismatch> diffs,
            String path,
            Object clientValue,
            Object unparseValue,
            String category,
            String explanation) {
        if (!Objects.equals(stringify(clientValue), stringify(unparseValue))) {
            diffs.add(new FieldMismatch(path, clientValue, unparseValue, category, explanation));
        }
    }

    private static String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
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
