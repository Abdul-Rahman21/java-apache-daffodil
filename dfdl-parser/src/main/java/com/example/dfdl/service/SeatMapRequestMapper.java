package com.example.dfdl.service;

import com.example.dfdl.config.DaffodilConfiguration.DaffodilProperties;
import com.example.dfdl.dto.SeatMapRequest;
import com.example.dfdl.dto.SeatMapRequest.Airport;
import com.example.dfdl.dto.SeatMapRequest.FlightSegment;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps Apache Daffodil SMPREQ infoset JSON into the downstream seat-map request format
 * (see samples/Request_seatmaprequest_2.txt).
 */
@Service
public class SeatMapRequestMapper {

    private static final Logger log = LoggerFactory.getLogger(SeatMapRequestMapper.class);

    private final DaffodilProperties properties;

    public SeatMapRequestMapper(DaffodilProperties properties) {
        this.properties = properties;
    }

    public SeatMapRequest map(JsonNode smpreqInfoset) {
        if (smpreqInfoset == null || smpreqInfoset.isNull()) {
            throw new IllegalArgumentException("DFDL infoset JSON is empty; cannot map seat-map request");
        }

        // Jackson XML may wrap under root element name or return the root object directly.
        JsonNode root = smpreqInfoset.has("SMPREQ") ? smpreqInfoset.get("SMPREQ") : smpreqInfoset;
        JsonNode org = root.path("ORG");
        JsonNode tvlNode = root.get("TVL");

        SeatMapRequest request = new SeatMapRequest();
        request.setChannelId(properties.getChannelId());
        request.setChannelName(firstNonBlank(
                text(org, "C336a_13.1", "EL9906_CompanyId"),
                text(org, "C336b_13.1", "EL9906_CompanyId"),
                properties.getDefaultChannelName()));
        request.setCurrencyCode(firstNonBlank(
                text(org, "C354", "EL6345_CurrentcyCode"),
                properties.getDefaultCurrencyCode()));
        request.setRecordLocator("");
        request.setProductCode("");
        request.setFlightSegments(mapFlightSegments(tvlNode));

        log.debug(
                "Mapped seat-map request: channelId={}, channelName={}, currency={}, segments={}",
                request.getChannelId(),
                request.getChannelName(),
                request.getCurrencyCode(),
                request.getFlightSegments().size());
        return request;
    }

    private List<FlightSegment> mapFlightSegments(JsonNode tvlNode) {
        List<FlightSegment> segments = new ArrayList<>();
        if (tvlNode == null || tvlNode.isNull() || tvlNode.isMissingNode()) {
            return segments;
        }

        if (tvlNode.isArray()) {
            for (JsonNode tvl : tvlNode) {
                segments.add(mapOneSegment(tvl));
            }
        } else {
            segments.add(mapOneSegment(tvlNode));
        }
        return segments;
    }

    private FlightSegment mapOneSegment(JsonNode tvl) {
        FlightSegment segment = new FlightSegment();

        String departure = text(tvl, "C328a", "EL3225_LocationId");
        String arrival = text(tvl, "C328b", "EL3225_LocationId");
        String airline = text(tvl, "C306", "EL9906_CompanyId");
        String flightNumberRaw = text(tvl, "C308", "EL9908_ProductId");
        String classOfService = properties.getDefaultClassOfService();
        String dateRaw = text(tvl, "C310", "EL9916_FirstDate");

        segment.setDepartureAirport(new Airport(departure));
        segment.setArrivalAirport(new Airport(arrival));
        segment.setDepartureDateTime(formatDdmmyy(dateRaw));
        segment.setMarketingAirlineCode(airline);
        segment.setOperatingAirlineCode(airline);

        Integer flightNumber = parseFlightNumber(flightNumberRaw);
        segment.setFlightNumber(flightNumber);
        segment.setOperatingFlightNumber(flightNumber);
        segment.setClassOfService(classOfService);
        segment.setPricing(properties.getDefaultPricing());
        return segment;
    }

    /**
     * EDIFACT date in this feed is DDMMYY (e.g. 211226 → 2026-12-21).
     */
    static String formatDdmmyy(String ddmmyy) {
        if (ddmmyy == null || ddmmyy.isBlank()) {
            return null;
        }
        String digits = ddmmyy.trim();
        if (digits.length() != 6 || !digits.chars().allMatch(Character::isDigit)) {
            return ddmmyy;
        }
        int day = Integer.parseInt(digits.substring(0, 2));
        int month = Integer.parseInt(digits.substring(2, 4));
        int yearTwo = Integer.parseInt(digits.substring(4, 6));
        int year = yearTwo >= 70 ? 1900 + yearTwo : 2000 + yearTwo;
        return String.format("%04d-%02d-%02d", year, month, day);
    }

    private static Integer parseFlightNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String text(JsonNode parent, String... path) {
        if (parent == null || parent.isMissingNode() || parent.isNull()) {
            return null;
        }
        JsonNode current = parent;
        for (String part : path) {
            current = current.path(part);
            if (current.isMissingNode() || current.isNull()) {
                return null;
            }
        }
        if (current.isValueNode()) {
            String value = current.asText();
            return value == null || value.isBlank() ? null : value.trim();
        }
        // Some XML→JSON conversions put text under ""
        if (current.has("") && current.get("").isValueNode()) {
            String value = current.get("").asText();
            return value == null || value.isBlank() ? null : value.trim();
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
