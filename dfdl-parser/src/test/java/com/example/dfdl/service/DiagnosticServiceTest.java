package com.example.dfdl.service;

import com.example.dfdl.config.DaffodilConfiguration.DaffodilProperties;
import com.example.dfdl.dto.DiagnosticResponse;
import com.example.dfdl.util.XmlJsonConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticServiceTest {

    @TempDir
    Path tempDir;

    private Path schemaPath;
    private Path samplesDir;
    private DiagnosticService diagnosticService;

    @BeforeEach
    void setUp() {
        schemaPath = Path.of("schemas/CYO_SMPREQ.xsd").toAbsolutePath().normalize();
        samplesDir = Path.of("samples").toAbsolutePath().normalize();

        DaffodilProperties properties = new DaffodilProperties();
        properties.setSchema(schemaPath.toString());
        properties.setSamplesDir(samplesDir.toString());

        DaffodilParserService parserService =
                new DaffodilParserService(properties, new XmlJsonConverter(new ObjectMapper()));
        parserService.compileConfiguredSchema();
        diagnosticService = new DiagnosticService(properties, parserService);
    }

    @Test
    void diagnose_compileOnly_reportsCompileSuccess() {
        DiagnosticResponse response = diagnosticService.diagnose(null);

        assertTrue(response.isCompileSuccess());
        assertNull(response.getParseSuccess());
        assertEquals(schemaPath.toString(), response.getSchemaPath());
        assertTrue(response.getElapsedMillis() >= 0);
        assertNotNull(response.getCompileDiagnostics());
        assertNotNull(response.getNotes());
    }

    @Test
    void diagnose_withValidBinary_reportsParseSuccess() throws Exception {
        byte[] binary = Files.readAllBytes(samplesDir.resolve("sample_smpreq.bin"));

        DiagnosticResponse response = diagnosticService.diagnose(binary);

        assertTrue(response.isCompileSuccess());
        assertTrue(Boolean.TRUE.equals(response.getParseSuccess()));
        assertNotNull(response.getParseDiagnostics());
    }

    @Test
    void diagnose_withInvalidBinary_reportsParseFailure() {
        byte[] invalid = new byte[] {0x01};

        DiagnosticResponse response = diagnosticService.diagnose(invalid);

        assertTrue(response.isCompileSuccess());
        assertFalse(Boolean.TRUE.equals(response.getParseSuccess()));
        assertFalse(response.getParseDiagnostics().isEmpty());
    }

    @Test
    void diagnose_missingSchema_reportsCompileFailureWithRawMessage() {
        DaffodilProperties properties = new DaffodilProperties();
        properties.setSchema(tempDir.resolve("absent.xsd").toString());
        properties.setSamplesDir(samplesDir.toString());

        DaffodilParserService parserService =
                new DaffodilParserService(properties, new XmlJsonConverter(new ObjectMapper()));
        DiagnosticService service = new DiagnosticService(properties, parserService);

        DiagnosticResponse response = service.diagnose(null);

        assertFalse(response.isCompileSuccess());
        assertNotNull(response.getExceptionMessage());
        assertTrue(response.getExceptionMessage().contains("does not exist")
                || response.getCompileDiagnostics().stream().anyMatch(d -> d.contains("does not exist")));
    }
}
