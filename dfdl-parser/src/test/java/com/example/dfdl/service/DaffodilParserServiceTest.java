package com.example.dfdl.service;

import com.example.dfdl.config.DaffodilConfiguration.DaffodilProperties;
import com.example.dfdl.dto.ParseResponse;
import com.example.dfdl.exception.DfdlParseException;
import com.example.dfdl.exception.DfdlSchemaException;
import com.example.dfdl.util.XmlJsonConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaffodilParserServiceTest {

    @TempDir
    Path tempDir;

    private Path schemaPath;
    private Path samplesDir;
    private DaffodilParserService service;

    @BeforeEach
    void setUp() throws Exception {
        schemaPath = Path.of("schemas/CYO_SMPREQ.xsd").toAbsolutePath().normalize();
        samplesDir = Path.of("samples").toAbsolutePath().normalize();
        assertTrue(Files.exists(schemaPath), "Expected project schema at " + schemaPath);

        DaffodilProperties properties = new DaffodilProperties();
        properties.setSchema(schemaPath.toString());
        properties.setSamplesDir(samplesDir.toString());

        ObjectMapper objectMapper = new ObjectMapper();
        service = new DaffodilParserService(
                properties,
                new XmlJsonConverter(objectMapper),
                new SeatMapRequestMapper(properties),
                objectMapper);
        service.compileConfiguredSchema();
    }

    @Test
    void compileConfiguredSchema_marksSchemaCompiled() {
        assertTrue(service.isSchemaCompiled());
        assertEquals("CYO_SMPREQ.xsd", service.getSchemaFileName());
        assertNotNull(service.getDataProcessor());
    }

    @Test
    void parse_validBinary_returnsMappedSeatMapJson() throws Exception {
        byte[] binary = Files.readAllBytes(samplesDir.resolve("Request_SMPREQ_1.bin"));

        ParseResponse response = service.parse(binary);

        assertTrue(response.isSuccess());
        assertNotNull(response.getXml());
        assertTrue(response.getXml().contains("SMPREQ") || response.getXml().contains("UNB"));
        assertNotNull(response.getJson());
        assertEquals("4101", response.getJson().path("ChannelId").asText());
        assertEquals("1A", response.getJson().path("ChannelName").asText());
        assertEquals("USD", response.getJson().path("CurrencyCode").asText());
        assertTrue(response.getJson().path("FlightSegments").isArray());
        assertEquals("LAX", response.getJson().path("FlightSegments").get(0)
                .path("DepartureAirport").path("IataCode").asText());
        assertEquals("DEN", response.getJson().path("FlightSegments").get(0)
                .path("ArrivalAirport").path("IataCode").asText());
        assertEquals("2026-12-21", response.getJson().path("FlightSegments").get(0)
                .path("DepartureDateTime").asText());
    }

    @Test
    void parse_emptyBinary_throwsParseException() {
        DfdlParseException ex = assertThrows(DfdlParseException.class, () -> service.parse(new byte[0]));
        assertTrue(ex.getMessage().toLowerCase().contains("empty"));
    }

    @Test
    void parse_invalidBinary_throwsParseException() {
        byte[] invalid = new byte[] {0x00, 0x01};

        DfdlParseException ex = assertThrows(DfdlParseException.class, () -> service.parse(invalid));
        assertTrue(ex.getMessage().contains("parse") || ex.getMessage().contains("Diagnostics"));
    }

    @Test
    void parseSampleFile_readsFromSamplesDirectory() {
        ParseResponse response = service.parseSampleFile("Request_SMPREQ_1.bin");
        assertTrue(response.isSuccess());
        assertNotNull(response.getJson());
        assertTrue(response.getJson().has("FlightSegments"));
    }

    @Test
    void compileConfiguredSchema_missingSchema_throwsSchemaException() {
        DaffodilProperties properties = new DaffodilProperties();
        properties.setSchema(tempDir.resolve("missing.xsd").toString());
        properties.setSamplesDir(samplesDir.toString());

        ObjectMapper objectMapper = new ObjectMapper();
        DaffodilParserService missingService = new DaffodilParserService(
                properties,
                new XmlJsonConverter(objectMapper),
                new SeatMapRequestMapper(properties),
                objectMapper);

        DfdlSchemaException ex = assertThrows(DfdlSchemaException.class, missingService::compileConfiguredSchema);
        assertTrue(ex.getMessage().contains("not found"));
        assertFalse(missingService.isSchemaCompiled());
    }

    @Test
    void parse_inputStream_delegatesSuccessfully() throws Exception {
        byte[] binary = Files.readAllBytes(samplesDir.resolve("Request_SMPREQ_1.bin"));
        ParseResponse response = service.parse(new java.io.ByteArrayInputStream(binary));
        assertTrue(response.isSuccess());
        assertTrue(new String(response.getXml().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                .length() > 0);
    }
}
