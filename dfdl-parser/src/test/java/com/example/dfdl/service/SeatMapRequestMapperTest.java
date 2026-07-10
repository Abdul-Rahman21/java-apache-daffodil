package com.example.dfdl.service;

import com.example.dfdl.config.DaffodilConfiguration.DaffodilProperties;
import com.example.dfdl.dto.SeatMapRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SeatMapRequestMapperTest {

    private SeatMapRequestMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DaffodilProperties properties = new DaffodilProperties();
        properties.setChannelId("4101");
        properties.setDefaultChannelName("1A");
        properties.setDefaultCurrencyCode("USD");
        properties.setDefaultClassOfService("Y");
        properties.setDefaultPricing("true");
        mapper = new SeatMapRequestMapper(properties);
        objectMapper = new ObjectMapper();
    }

    @Test
    void map_buildsSeatMapRequestLikeSample() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode org = root.putObject("ORG");
        org.putObject("C336a_13.1").put("EL9906_CompanyId", "1A");
        org.putObject("C354").put("EL6345_CurrentcyCode", "USD");

        ObjectNode tvl = root.putObject("TVL");
        tvl.putObject("C310").put("EL9916_FirstDate", "211226");
        tvl.putObject("C328a").put("EL3225_LocationId", "LAX");
        tvl.putObject("C328b").put("EL3225_LocationId", "DEN");
        tvl.putObject("C306").put("EL9906_CompanyId", "UA");
        ObjectNode c308 = tvl.putObject("C308");
        c308.put("EL9908_ProductId", "1275");
        c308.put("EL7037_CharacteristicId", "Y");

        SeatMapRequest mapped = mapper.map(root);

        assertEquals("4101", mapped.getChannelId());
        assertEquals("1A", mapped.getChannelName());
        assertEquals("USD", mapped.getCurrencyCode());
        assertEquals("", mapped.getRecordLocator());
        assertEquals("", mapped.getProductCode());
        assertEquals(1, mapped.getFlightSegments().size());

        SeatMapRequest.FlightSegment segment = mapped.getFlightSegments().get(0);
        assertEquals("LAX", segment.getDepartureAirport().getIataCode());
        assertEquals("DEN", segment.getArrivalAirport().getIataCode());
        assertEquals("2026-12-21", segment.getDepartureDateTime());
        assertEquals("UA", segment.getMarketingAirlineCode());
        assertEquals("UA", segment.getOperatingAirlineCode());
        assertEquals(1275, segment.getFlightNumber());
        assertEquals(1275, segment.getOperatingFlightNumber());
        assertEquals("Y", segment.getClassOfService());
        assertEquals("true", segment.getPricing());
    }

    @Test
    void formatDdmmyy_convertsToIsoDate() {
        assertEquals("2026-12-21", SeatMapRequestMapper.formatDdmmyy("211226"));
        assertNotNull(SeatMapRequestMapper.formatDdmmyy("010199"));
    }
}
