package com.example.dfdl.service;

import com.example.dfdl.config.DaffodilConfiguration.DaffodilProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeatMapResponseMapperTest {

    private SeatMapResponseMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DaffodilProperties properties = new DaffodilProperties();
        properties.setDefaultChannelName("1A");
        properties.setDefaultCurrencyCode("USD");
        properties.setDefaultClassOfService("Y");
        mapper = new SeatMapResponseMapper(properties);
        objectMapper = new ObjectMapper();
    }

    @Test
    void toSmpreXml_matchesAceClientSegmentLayout() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode tx = root.putObject("transactionIdentifiers");
        tx.put("transactionId", "01735HSGF30001");
        tx.put("channelName", "1APPC");
        tx.put("cabinCode", "Y");

        ObjectNode flight = root.putObject("flightInfo");
        flight.put("marketingFlightNumber", 1275);
        flight.put("marketingCarrierCode", "UA");
        flight.put("departureDate", "2026-12-21T00:00:00");
        flight.put("departureAirport", "LAX");
        flight.put("arrivalAirport", "DEN");

        root.putObject("aircraftInfo").put("icr", "B3S");

        ArrayNode cabins = root.putArray("cabins");
        ObjectNode jCabin = cabins.addObject();
        jCabin.put("cabinType", "J");
        jCabin.put("layout", "AB EF");
        ArrayNode jRows = jCabin.putArray("rows");
        ObjectNode jRow = jRows.addObject();
        jRow.put("number", 1);
        jRow.putArray("seats");

        ObjectNode yCabin = cabins.addObject();
        yCabin.put("cabinType", "Y");
        yCabin.put("layout", "ABC DEF");
        ArrayNode yRows = yCabin.putArray("rows");

        ObjectNode row7 = yRows.addObject();
        row7.put("number", 7);
        row7.put("wing", false);
        ArrayNode seats7 = row7.putArray("seats");
        addSeat(seats7, "D", "Aisle", false, true, 96.99);
        addSeat(seats7, "E", "Middle", false, true, 92.99);
        addSeat(seats7, "F", "Window", true, true, 96.99);

        ObjectNode row8 = yRows.addObject();
        row8.put("number", 8);
        row8.put("wing", false);
        ArrayNode seats8 = row8.putArray("seats");
        addSeat(seats8, "A", "Window", true, true, 50.0);
        addSeat(seats8, "B", "Middle", true, true, 50.0);
        addSeat(seats8, "C", "Aisle", true, true, 50.0);
        addSeat(seats8, "D", "Aisle", true, true, 50.0);
        addSeat(seats8, "E", "Middle", true, true, 50.0);
        addSeat(seats8, "F", "Window", true, true, 50.0);

        ObjectNode row14 = yRows.addObject();
        row14.put("number", 14);
        row14.put("wing", true);
        ArrayNode seats14 = row14.putArray("seats");
        addSeat(seats14, "A", "Window", true, true, 40.0);

        String xml = mapper.toSmpreXml(root);

        assertTrue(xml.contains("<SMPRES>"));
        assertTrue(xml.contains("<EL0004_SenderId>UA1SM</EL0004_SenderId>"));
        assertTrue(xml.contains("<EL0010_RecipientId>1APPC</EL0010_RecipientId>"));
        assertTrue(xml.contains("<EL0035_TestIndicator>T</EL0035_TestIndicator>"));
        assertTrue(xml.contains("<EL0065_MessageType>SMPRES</EL0065_MessageType>"));
        assertFalse(xml.contains("<ORG>"), "Client ACE sample has no ORG segment");
        assertTrue(xml.contains("<SegmentId>EQI</SegmentId>"));
        assertTrue(xml.contains("<Blob>B3S</Blob>"));
        assertTrue(xml.contains("<EL7037_CharacteristicId>L</EL7037_CharacteristicId>"));
        assertTrue(xml.contains("<EL9992_CabinCompartment>Y</EL9992_CabinCompartment>"));
        assertTrue(xml.contains("<EL9830_SeatRowNumber>007</EL9830_SeatRowNumber>"));
        assertTrue(xml.contains("<EL9864_RowCharacteristic>Z</EL9864_RowCharacteristic>"));
        assertTrue(xml.contains("<EL9825_SeatCharacteristic>CH</EL9825_SeatCharacteristic>")
                || xml.contains("<EL9825_SeatCharacteristic>8</EL9825_SeatCharacteristic>"));
        assertFalse(xml.contains("<EL9854_CabinClass>J</EL9854_CabinClass>"),
                "Only requested cabin Y should be emitted");
        assertTrue(xml.contains("<CBD>"));
        assertTrue(xml.contains("<ROD>"));
    }

    @Test
    void toDdmmyy_formatsIsoDate() {
        assertEquals("211226", SeatMapResponseMapper.toDdmmyy("2026-12-21T00:00:00"));
    }

    private static void addSeat(
            ArrayNode seats,
            String letter,
            String location,
            boolean available,
            boolean extraPitch,
            double fee) {
        ObjectNode seat = seats.addObject();
        seat.put("letter", letter);
        seat.put("location", location);
        seat.put("isAvailable", available);
        seat.put("isRemovedSeat", false);
        seat.put("isOccupied", false);
        seat.put("isHeld", false);
        seat.put("isExit", false);
        seat.put("isDoorExit", false);
        seat.put("isExtraPitch", extraPitch);
        seat.put("sellableSeatCategory", "Economy Plus");
        ArrayNode fees = seat.putArray("servicesAndFees");
        fees.addObject().put("totalFee", fee).put("baseFee", fee);
    }
}
