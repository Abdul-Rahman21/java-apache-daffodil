package com.example.dfdl.service;

import com.example.dfdl.config.DaffodilConfiguration.DaffodilProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Maps seat-map response JSON into an SMPRES XML infoset matching the ACE client
 * binary layout (see samples/Response_SMPRES_4.bin):
 * UNB → UNH → TVL → EQI → CBD → ROD… → UNT → UNZ (no ORG).
 */
@Service
public class SeatMapResponseMapper {

    private static final Logger log = LoggerFactory.getLogger(SeatMapResponseMapper.class);
    private static final DateTimeFormatter DDMMYY = DateTimeFormatter.ofPattern("ddMMyy");
    private static final DateTimeFormatter YYMMDD_HHMM = DateTimeFormatter.ofPattern("yyMMddHHmm");
    private static final String SMPRES_SENDER = "UA1SM";

    private final DaffodilProperties properties;

    public SeatMapResponseMapper(DaffodilProperties properties) {
        this.properties = properties;
    }

    public String toSmpreXml(JsonNode seatMapResponse) {
        if (seatMapResponse == null || seatMapResponse.isNull() || seatMapResponse.isMissingNode()) {
            throw new IllegalArgumentException("Seat-map response JSON is empty");
        }

        JsonNode tx = seatMapResponse.path("transactionIdentifiers");
        JsonNode flight = seatMapResponse.path("flightInfo");
        JsonNode aircraft = seatMapResponse.path("aircraftInfo");
        JsonNode cabins = seatMapResponse.path("cabins");

        String recipient = textOr(tx.path("channelName"), properties.getDefaultChannelName());
        String interchangeRef = sanitizeRef(textOr(tx.path("transactionId"), UUID.randomUUID().toString()));
        String accessRef = buildAccessRef(tx.path("transactionId"), interchangeRef);
        String nowStamp = LocalDateTime.now().format(YYMMDD_HHMM);
        String datePart = nowStamp.substring(0, 6);
        String timePart = nowStamp.substring(6, 10);

        String requestedCabin = textOr(tx.path("cabinCode"), properties.getDefaultClassOfService());
        List<JsonNode> selectedCabins = selectCabins(cabins, requestedCabin);

        StringBuilder xml = new StringBuilder(64 * 1024);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<SMPRES>\n");

        appendUnb(xml, recipient, interchangeRef, datePart, timePart);
        appendUnh(xml, accessRef);

        int bodySegments = 0;
        xml.append("  <SMPRESGroup1>\n");
        appendTvl(xml, flight);
        bodySegments++;
        appendEqi(xml, aircraft);
        bodySegments++;
        for (JsonNode cabin : selectedCabins) {
            appendCabinCbd(xml, cabin);
            bodySegments++;
            bodySegments += appendRowRods(xml, cabin);
        }
        xml.append("  </SMPRESGroup1>\n");

        // EDIFACT UNT count includes UNH and UNT.
        int segmentCount = 1 + bodySegments + 1;
        appendUnt(xml, segmentCount, "1");
        appendUnz(xml, interchangeRef);

        xml.append("</SMPRES>\n");
        log.info("Built ACE-style SMPRES infoset XML ({} chars, {} segments) for unparse", xml.length(), segmentCount);
        return xml.toString();
    }

    private void appendUnb(
            StringBuilder xml,
            String recipient,
            String interchangeRef,
            String datePart,
            String timePart) {
        // Client: UNB+IATB:1+UA1SM+1APPC+yyMMdd:HHmm+ref+ref++T
        xml.append("  <UNB>\n");
        xml.append("    <SegmentId>UNB</SegmentId>\n");
        xml.append("    <S001><EL0001>IATB</EL0001><EL0002>1</EL0002></S001>\n");
        xml.append("    <S002><EL0004_SenderId>").append(esc(SMPRES_SENDER)).append("</EL0004_SenderId></S002>\n");
        xml.append("    <S003><EL0010_RecipientId>").append(esc(recipient)).append("</EL0010_RecipientId></S003>\n");
        xml.append("    <S004><EL0017>").append(esc(datePart)).append("</EL0017>")
                .append("<EL0019>").append(esc(timePart)).append("</EL0019></S004>\n");
        xml.append("    <EL0020_InterchangeRef>").append(esc(interchangeRef)).append("</EL0020_InterchangeRef>\n");
        xml.append("    <S005><EL0022>").append(esc(interchangeRef)).append("</EL0022></S005>\n");
        xml.append("    <EL0035_TestIndicator>T</EL0035_TestIndicator>\n");
        xml.append("  </UNB>\n");
    }

    private void appendUnh(StringBuilder xml, String accessRef) {
        xml.append("  <UNH>\n");
        xml.append("    <SegmentId>UNH</SegmentId>\n");
        xml.append("    <EL0062_MsgRefNumber>1</EL0062_MsgRefNumber>\n");
        xml.append("    <S009>");
        xml.append("<EL0065_MessageType>SMPRES</EL0065_MessageType>");
        xml.append("<EL0052_MsgTypeVersionNum>93</EL0052_MsgTypeVersionNum>");
        xml.append("<EL0054_MsgTypeReleaseNum>2</EL0054_MsgTypeReleaseNum>");
        xml.append("<EL0051_ControlAgency>IA</EL0051_ControlAgency>");
        xml.append("</S009>\n");
        xml.append("    <EL0068_CommonAccessRef>").append(esc(accessRef)).append("</EL0068_CommonAccessRef>\n");
        xml.append("  </UNH>\n");
    }

    private void appendTvl(StringBuilder xml, JsonNode flight) {
        String dep = textOr(flight.path("departureAirport"), "");
        String arr = textOr(flight.path("arrivalAirport"), "");
        String mkt = textOr(flight.path("marketingCarrierCode"), "");
        String flightNo = flight.path("marketingFlightNumber").isMissingNode()
                ? ""
                : flight.path("marketingFlightNumber").asText();
        String date = toDdmmyy(textOr(flight.path("departureDate"), null));

        xml.append("    <TVL>\n");
        xml.append("      <SegmentId>TVL</SegmentId>\n");
        if (!date.isBlank()) {
            xml.append("      <C310><EL9916_FirstDate>").append(esc(date)).append("</EL9916_FirstDate></C310>\n");
        }
        xml.append("      <C328a><EL3225_LocationId>").append(esc(dep)).append("</EL3225_LocationId></C328a>\n");
        xml.append("      <C328b><EL3225_LocationId>").append(esc(arr)).append("</EL3225_LocationId></C328b>\n");
        xml.append("      <C306><EL9906_CompanyId>").append(esc(mkt)).append("</EL9906_CompanyId></C306>\n");
        // Client: 1275:L
        xml.append("      <C308><EL9908_ProductId>").append(esc(flightNo)).append("</EL9908_ProductId>")
                .append("<EL7037_CharacteristicId>L</EL7037_CharacteristicId></C308>\n");
        xml.append("    </TVL>\n");
    }

    private void appendEqi(StringBuilder xml, JsonNode aircraft) {
        String icr = textOr(aircraft.path("icr"), "");
        // Client: EQI++++++B3S  (5 empty fillers + aircraft type)
        xml.append("    <EQI>\n");
        xml.append("      <SegmentId>EQI</SegmentId>\n");
        for (int i = 0; i < 5; i++) {
            xml.append("      <FILLER></FILLER>\n");
        }
        if (!icr.isBlank()) {
            xml.append("      <Blob>").append(esc(icr)).append("</Blob>\n");
        }
        xml.append("    </EQI>\n");
    }

    private void appendCabinCbd(StringBuilder xml, JsonNode cabin) {
        String cabinType = textOr(cabin.path("cabinType"), "Y");
        String layout = textOr(cabin.path("layout"), "");
        int firstRow = firstRowNumber(cabin);
        int lastRow = lastRowNumber(cabin);
        int[] wingRange = wingRowRange(cabin);

        // Client: CBD+Y:::Y+007:039++++14:24+A:W+B:9+C:A+D:A+E:9+F:W
        xml.append("    <CBD>\n");
        xml.append("      <SegmentId>CBD</SegmentId>\n");
        xml.append("      <C342>");
        xml.append("<EL9854_CabinClass>").append(esc(cabinType)).append("</EL9854_CabinClass>");
        xml.append("<EL7037_CharacteristicId></EL7037_CharacteristicId>");
        xml.append("<EL9873_CabinServiceClass></EL9873_CabinServiceClass>");
        xml.append("<EL9992_CabinCompartment>").append(esc(cabinType)).append("</EL9992_CabinCompartment>");
        xml.append("</C342>\n");
        xml.append("      <C052>");
        xml.append("<EL9830_SeatRowNumber>").append(padRow(firstRow)).append("</EL9830_SeatRowNumber>");
        xml.append("<EL9830_SeatRowNumber>").append(padRow(lastRow)).append("</EL9830_SeatRowNumber>");
        xml.append("</C052>\n");
        // Keep empty positional fields so unparse emits client-style ++++ before wing rows.
        xml.append("      <EL9863_CabinClassLocation></EL9863_CabinClassLocation>\n");
        xml.append("      <C053><EL9830_SeatRowNumber></EL9830_SeatRowNumber></C053>\n");
        xml.append("      <EL9883_SeatOccupationInd></EL9883_SeatOccupationInd>\n");
        if (wingRange != null) {
            xml.append("      <C058>");
            // Client sample uses unpadded wing rows: 14:24 (cabin range stays zero-padded 007:039)
            xml.append("<EL9830_SeatRowNumber>").append(wingRange[0]).append("</EL9830_SeatRowNumber>");
            xml.append("<EL9830_SeatRowNumber>").append(wingRange[1]).append("</EL9830_SeatRowNumber>");
            xml.append("</C058>\n");
        }
        for (String column : layoutColumns(layout)) {
            String colDesc = columnDescription(cabin, column);
            xml.append("      <C054><EL9831_SeatColumn>").append(esc(column)).append("</EL9831_SeatColumn>");
            if (colDesc != null) {
                xml.append("<EL9882_ColumnDesc>").append(esc(colDesc)).append("</EL9882_ColumnDesc>");
            }
            xml.append("</C054>\n");
        }
        xml.append("    </CBD>\n");
    }

    /**
     * @return number of ROD segments written
     */
    private int appendRowRods(StringBuilder xml, JsonNode cabin) {
        JsonNode rows = cabin.path("rows");
        if (!rows.isArray() || rows.isEmpty()) {
            return 0;
        }

        TreeMap<Integer, JsonNode> byNumber = new TreeMap<>();
        for (JsonNode row : rows) {
            byNumber.put(row.path("number").asInt(0), row);
        }

        List<String> columns = layoutColumns(textOr(cabin.path("layout"), ""));
        int first = byNumber.firstKey();
        int last = byNumber.lastKey();
        int written = 0;

        for (int rowNumber = first; rowNumber <= last; rowNumber++) {
            JsonNode row = byNumber.get(rowNumber);
            if (row == null) {
                // Client facility / missing rows: ROD+13+Z
                xml.append("    <ROD>\n");
                xml.append("      <SegmentId>ROD</SegmentId>\n");
                xml.append("      <EL9830_SeatRowNumber>").append(rowNumber).append("</EL9830_SeatRowNumber>\n");
                xml.append("      <C049><EL9864_RowCharacteristic>Z</EL9864_RowCharacteristic></C049>\n");
                xml.append("    </ROD>\n");
                written++;
                continue;
            }

            boolean exitRow = isExitRow(row);
            Map<String, JsonNode> seatsByLetter = indexSeats(row);

            xml.append("    <ROD>\n");
            xml.append("      <SegmentId>ROD</SegmentId>\n");
            xml.append("      <EL9830_SeatRowNumber>").append(rowNumber).append("</EL9830_SeatRowNumber>\n");
            if (exitRow) {
                xml.append("      <C049><EL9864_RowCharacteristic>E</EL9864_RowCharacteristic></C049>\n");
            }

            for (String column : columns) {
                JsonNode seat = seatsByLetter.get(column);
                appendSeatC051(xml, seat, column, exitRow);
            }
            xml.append("    </ROD>\n");
            written++;
        }
        return written;
    }

    private void appendSeatC051(StringBuilder xml, JsonNode seat, String column, boolean exitRow) {
        // Missing / removed seats → occupied restricted (client A:O:8 style)
        if (seat == null || seat.path("isRemovedSeat").asBoolean(false)) {
            xml.append("      <C051>");
            xml.append("<EL9831_SeatColumn>").append(esc(column)).append("</EL9831_SeatColumn>");
            xml.append("<EL9865_SeatOccupation>O</EL9865_SeatOccupation>");
            xml.append("<EL9825_SeatCharacteristic>8</EL9825_SeatCharacteristic>");
            xml.append("</C051>\n");
            return;
        }

        boolean available = seat.path("isAvailable").asBoolean(false)
                && !seat.path("isOccupied").asBoolean(false)
                && !seat.path("isHeld").asBoolean(false);
        String occupation = available ? "F" : "O";
        List<String> chars = seatCharacteristics(seat, occupation, exitRow);

        xml.append("      <C051>");
        xml.append("<EL9831_SeatColumn>").append(esc(column)).append("</EL9831_SeatColumn>");
        xml.append("<EL9865_SeatOccupation>").append(occupation).append("</EL9865_SeatOccupation>");
        for (String characteristic : chars) {
            xml.append("<EL9825_SeatCharacteristic>").append(esc(characteristic)).append("</EL9825_SeatCharacteristic>");
        }
        xml.append("</C051>\n");
    }

    /**
     * ACE-style seat characteristics aligned with Response_SMPRES_4.bin patterns:
     * CH (chargeable), 9 (middle), E/1 (exit), 8 (blocked/occupied), 1 (restricted).
     */
    static List<String> seatCharacteristics(JsonNode seat, String occupation, boolean exitRow) {
        List<String> chars = new ArrayList<>();
        boolean exitSeat = exitRow
                || seat.path("isExit").asBoolean(false)
                || seat.path("isDoorExit").asBoolean(false);
        String location = textOr(seat.path("location"), "").toLowerCase(Locale.ROOT);
        boolean middle = location.contains("middle") || location.contains("center");
        boolean chargeable = isChargeable(seat);

        if ("O".equals(occupation)) {
            if (exitSeat) {
                chars.add("E");
                chars.add("1");
            } else {
                chars.add("8");
            }
            return chars;
        }

        // Free seats
        if (exitSeat) {
            chars.add("E");
            chars.add("1");
            return chars;
        }
        if (chargeable) {
            chars.add("CH");
            return chars;
        }
        if (middle) {
            chars.add("9");
        }
        return chars;
    }

    private static boolean isChargeable(JsonNode seat) {
        if (seat.path("isPreferred").asBoolean(false) || seat.path("isExtraPitch").asBoolean(false)) {
            return true;
        }
        String category = textOr(seat.path("sellableSeatCategory"), "").toLowerCase(Locale.ROOT);
        if (category.contains("plus") || category.contains("preferred") || category.contains("premium")) {
            return true;
        }
        JsonNode fees = seat.path("servicesAndFees");
        if (fees.isArray()) {
            for (JsonNode fee : fees) {
                if (fee.path("totalFee").asDouble(0) > 0 || fee.path("baseFee").asDouble(0) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void appendUnt(StringBuilder xml, int segmentCount, String msgRef) {
        xml.append("  <UNT>\n");
        xml.append("    <SegmentId>UNT</SegmentId>\n");
        xml.append("    <EL0074_13.1>").append(Math.max(segmentCount, 2)).append("</EL0074_13.1>\n");
        xml.append("    <EL0062>").append(esc(msgRef)).append("</EL0062>\n");
        xml.append("  </UNT>\n");
    }

    private void appendUnz(StringBuilder xml, String interchangeRef) {
        xml.append("  <UNZ>\n");
        xml.append("    <SegmentId>UNZ</SegmentId>\n");
        xml.append("    <EL0036>1</EL0036>\n");
        xml.append("    <EL0020>").append(esc(interchangeRef)).append("</EL0020>\n");
        xml.append("  </UNZ>\n");
    }

    private static List<JsonNode> selectCabins(JsonNode cabins, String requestedCabin) {
        List<JsonNode> all = new ArrayList<>();
        if (cabins == null || !cabins.isArray()) {
            return all;
        }
        for (JsonNode cabin : cabins) {
            all.add(cabin);
        }
        if (requestedCabin == null || requestedCabin.isBlank()) {
            return all;
        }
        List<JsonNode> matched = new ArrayList<>();
        for (JsonNode cabin : all) {
            if (requestedCabin.equalsIgnoreCase(textOr(cabin.path("cabinType"), ""))) {
                matched.add(cabin);
            }
        }
        return matched.isEmpty() ? all : matched;
    }

    private static Map<String, JsonNode> indexSeats(JsonNode row) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        JsonNode seats = row.path("seats");
        if (!seats.isArray()) {
            return map;
        }
        for (JsonNode seat : seats) {
            String letter = textOr(seat.path("letter"), "");
            if (!letter.isBlank()) {
                map.put(letter.toUpperCase(Locale.ROOT), seat);
            }
        }
        return map;
    }

    private static boolean isExitRow(JsonNode row) {
        JsonNode seats = row.path("seats");
        if (!seats.isArray()) {
            return false;
        }
        for (JsonNode seat : seats) {
            if (seat.path("isExit").asBoolean(false) || seat.path("isDoorExit").asBoolean(false)) {
                return true;
            }
        }
        return false;
    }

    private static int[] wingRowRange(JsonNode cabin) {
        JsonNode rows = cabin.path("rows");
        if (!rows.isArray()) {
            return null;
        }
        Integer first = null;
        Integer last = null;
        for (JsonNode row : rows) {
            if (row.path("wing").asBoolean(false)) {
                int n = row.path("number").asInt(0);
                if (first == null || n < first) {
                    first = n;
                }
                if (last == null || n > last) {
                    last = n;
                }
            }
        }
        if (first == null) {
            return null;
        }
        return new int[]{first, last};
    }

    private static String columnDescription(JsonNode cabin, String column) {
        JsonNode rows = cabin.path("rows");
        if (!rows.isArray()) {
            return null;
        }
        for (JsonNode row : rows) {
            JsonNode seats = row.path("seats");
            if (!seats.isArray()) {
                continue;
            }
            for (JsonNode seat : seats) {
                if (!column.equalsIgnoreCase(textOr(seat.path("letter"), ""))) {
                    continue;
                }
                String location = textOr(seat.path("location"), "").toLowerCase(Locale.ROOT);
                if (location.contains("window")) {
                    return "W";
                }
                if (location.contains("aisle")) {
                    return "A";
                }
                if (location.contains("middle") || location.contains("center")) {
                    return "9";
                }
            }
        }
        return null;
    }

    private static List<String> layoutColumns(String layout) {
        Set<String> cols = new LinkedHashSet<>();
        if (layout == null) {
            return new ArrayList<>(cols);
        }
        for (int i = 0; i < layout.length(); i++) {
            char c = layout.charAt(i);
            if (Character.isLetter(c)) {
                cols.add(String.valueOf(Character.toUpperCase(c)));
            }
        }
        return new ArrayList<>(cols);
    }

    private static int firstRowNumber(JsonNode cabin) {
        JsonNode rows = cabin.path("rows");
        if (!rows.isArray() || rows.isEmpty()) {
            return 1;
        }
        return rows.get(0).path("number").asInt(1);
    }

    private static int lastRowNumber(JsonNode cabin) {
        JsonNode rows = cabin.path("rows");
        if (!rows.isArray() || rows.isEmpty()) {
            return 1;
        }
        int max = 1;
        for (JsonNode row : rows) {
            max = Math.max(max, row.path("number").asInt(1));
        }
        return max;
    }

    private static String padRow(int row) {
        return String.format(Locale.ROOT, "%03d", row);
    }

    private static String buildAccessRef(JsonNode transactionId, String interchangeRef) {
        String raw = textOr(transactionId, "");
        if (raw.contains("/")) {
            return raw.length() > 35 ? raw.substring(0, 35) : raw;
        }
        // Client-style access ref keeps a readable prefix; fall back to interchange ref.
        return interchangeRef;
    }

    static String toDdmmyy(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isBlank()) {
            return "";
        }
        try {
            if (isoDateTime.length() >= 19 && isoDateTime.charAt(10) == 'T') {
                LocalDateTime ldt = LocalDateTime.parse(isoDateTime.substring(0, 19));
                return ldt.format(DDMMYY);
            }
            if (isoDateTime.length() >= 10) {
                LocalDate ld = LocalDate.parse(isoDateTime.substring(0, 10));
                return ld.format(DDMMYY);
            }
        } catch (DateTimeParseException ignored) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(isoDateTime);
                return odt.toLocalDate().format(DDMMYY);
            } catch (DateTimeParseException ignored2) {
                return "";
            }
        }
        return "";
    }

    private static String sanitizeRef(String raw) {
        String cleaned = raw.replace("-", "").replace(" ", "");
        if (cleaned.length() > 14) {
            cleaned = cleaned.substring(0, 14);
        }
        return cleaned.isBlank() ? "00000000000001" : cleaned.toUpperCase(Locale.ROOT);
    }

    private static String textOr(JsonNode node, String fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        String value = node.asText();
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return fallback;
        }
        return value.trim();
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
