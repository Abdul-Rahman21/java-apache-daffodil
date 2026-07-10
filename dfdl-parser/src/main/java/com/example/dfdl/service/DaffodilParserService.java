package com.example.dfdl.service;

import com.example.dfdl.config.DaffodilConfiguration.DaffodilProperties;
import com.example.dfdl.dto.ParseResponse;
import com.example.dfdl.dto.SeatMapRequest;
import com.example.dfdl.exception.DfdlParseException;
import com.example.dfdl.exception.DfdlSchemaException;
import com.example.dfdl.util.XmlJsonConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.daffodil.japi.Compiler;
import org.apache.daffodil.japi.Daffodil;
import org.apache.daffodil.japi.DataProcessor;
import org.apache.daffodil.japi.Diagnostic;
import org.apache.daffodil.japi.ParseResult;
import org.apache.daffodil.japi.ProcessorFactory;
import org.apache.daffodil.japi.infoset.XMLTextInfosetOutputter;
import org.apache.daffodil.japi.io.InputSourceDataInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compiles the configured DFDL schema once at startup and reuses a thread-safe
 * {@link DataProcessor} singleton for all parse requests.
 */
@Service
public class DaffodilParserService {

    private static final Logger log = LoggerFactory.getLogger(DaffodilParserService.class);

    private final DaffodilProperties properties;
    private final XmlJsonConverter xmlJsonConverter;
    private final SeatMapRequestMapper seatMapRequestMapper;
    private final ObjectMapper objectMapper;

    private volatile DataProcessor dataProcessor;
    private volatile boolean schemaCompiled;
    private volatile String schemaFileName;
    private volatile String schemaAbsolutePath;

    public DaffodilParserService(
            DaffodilProperties properties,
            XmlJsonConverter xmlJsonConverter,
            SeatMapRequestMapper seatMapRequestMapper,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.xmlJsonConverter = xmlJsonConverter;
        this.seatMapRequestMapper = seatMapRequestMapper;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        log.info("Application startup: beginning DFDL schema compilation");
        try {
            compileConfiguredSchema();
        } catch (DfdlSchemaException ex) {
            // Keep the application up so /health and /diagnose remain available for troubleshooting.
            log.error("Schema compilation failed during startup: {}", ex.getMessage(), ex);
            schemaCompiled = false;
            dataProcessor = null;
        }
    }

    /**
     * Compiles the configured schema and replaces the singleton {@link DataProcessor}.
     *
     * @throws DfdlSchemaException if the schema is missing, has broken imports/includes, or fails to compile
     */
    public synchronized void compileConfiguredSchema() {
        Path schemaPath = Path.of(properties.getSchema()).toAbsolutePath().normalize();
        this.schemaAbsolutePath = schemaPath.toString();
        this.schemaFileName = schemaPath.getFileName() != null
                ? schemaPath.getFileName().toString()
                : properties.getSchema();

        if (!Files.exists(schemaPath)) {
            schemaCompiled = false;
            dataProcessor = null;
            throw new DfdlSchemaException(
                    "DFDL schema not found at configured path: " + schemaPath
                            + ". Mount schemas under /app/schema or update daffodil.schema.");
        }
        if (!Files.isRegularFile(schemaPath)) {
            schemaCompiled = false;
            dataProcessor = null;
            throw new DfdlSchemaException("Configured DFDL schema path is not a regular file: " + schemaPath);
        }

        long start = System.nanoTime();
        try {
            log.info("Schema compilation started for '{}'", schemaPath);
            Compiler compiler = Daffodil.compiler();
            ProcessorFactory processorFactory = compiler.compileFile(schemaPath.toFile());

            if (processorFactory.isError()) {
                String diagnostics = formatDiagnostics(processorFactory.getDiagnostics());
                schemaCompiled = false;
                dataProcessor = null;
                throw new DfdlSchemaException(
                        "DFDL schema compilation failed for '" + schemaPath
                                + "' (possible IBM ACE import/include incompatibility). Diagnostics: "
                                + diagnostics);
            }

            DataProcessor processor = processorFactory.onPath("/");
            if (processor.isError()) {
                String diagnostics = formatDiagnostics(processor.getDiagnostics());
                schemaCompiled = false;
                dataProcessor = null;
                throw new DfdlSchemaException(
                        "DFDL DataProcessor creation failed for '" + schemaPath + "'. Diagnostics: " + diagnostics);
            }

            this.dataProcessor = processor;
            this.schemaCompiled = true;
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            log.info("Schema compilation finished successfully for '{}' in {} ms", schemaPath, elapsedMs);
        } catch (DfdlSchemaException ex) {
            throw ex;
        } catch (Exception ex) {
            schemaCompiled = false;
            dataProcessor = null;
            throw new DfdlSchemaException(
                    "Unexpected failure compiling DFDL schema '" + schemaPath + "': " + ex.getMessage(), ex);
        }
    }

    public ParseResponse parse(byte[] binaryData) {
        Objects.requireNonNull(binaryData, "binaryData must not be null");
        if (binaryData.length == 0) {
            throw new DfdlParseException("Invalid binary: uploaded file is empty");
        }
        ensureCompiled();

        log.info("Parsing started ({} bytes)", binaryData.length);
        long start = System.nanoTime();
        try {
            String xml = parseToXml(dataProcessor, binaryData);
            JsonNode infoset = xmlJsonConverter.xmlToJson(xml);
            SeatMapRequest seatMapRequest = seatMapRequestMapper.map(infoset);
            JsonNode mappedJson = objectMapper.valueToTree(seatMapRequest);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            log.info("Parsing finished successfully in {} ms", elapsedMs);
            return ParseResponse.ok(xml, mappedJson, infoset);
        } catch (DfdlParseException ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            log.warn("Parsing failed after {} ms: {}", elapsedMs, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            log.error("Parsing failed after {} ms with unexpected exception", elapsedMs, ex);
            throw new DfdlParseException("Unexpected parse failure: " + ex.getMessage(), ex);
        }
    }

    public ParseResponse parse(InputStream inputStream) {
        try {
            return parse(inputStream.readAllBytes());
        } catch (IOException ex) {
            throw new DfdlParseException("Failed to read binary input stream: " + ex.getMessage(), ex);
        }
    }

    public ParseResponse parseSampleFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Sample file name must not be blank");
        }
        Path samplesDir = Path.of(properties.getSamplesDir()).toAbsolutePath().normalize();
        Path samplePath = samplesDir.resolve(fileName).normalize();
        if (!samplePath.startsWith(samplesDir)) {
            throw new IllegalArgumentException("Sample file path escapes the samples directory: " + fileName);
        }
        if (!Files.exists(samplePath) || !Files.isRegularFile(samplePath)) {
            throw new IllegalArgumentException("Sample file not found under " + samplesDir + ": " + fileName);
        }
        try {
            log.info("Parsing sample file '{}'", samplePath);
            return parse(Files.readAllBytes(samplePath));
        } catch (IOException ex) {
            throw new DfdlParseException("Failed to read sample file '" + samplePath + "': " + ex.getMessage(), ex);
        }
    }

    /**
     * Parses binary data with a caller-supplied {@link DataProcessor} (used by diagnostics).
     */
    public String parseToXml(DataProcessor processor, byte[] binaryData) {
        Objects.requireNonNull(processor, "processor must not be null");
        Objects.requireNonNull(binaryData, "binaryData must not be null");

        ByteArrayOutputStream xmlOut = new ByteArrayOutputStream();
        XMLTextInfosetOutputter outputter = new XMLTextInfosetOutputter(xmlOut, true);

        try (InputSourceDataInputStream input =
                     new InputSourceDataInputStream(new ByteArrayInputStream(binaryData))) {
            ParseResult parseResult = processor.parse(input, outputter);
            if (parseResult.isError()) {
                String diagnostics = formatDiagnostics(parseResult.getDiagnostics());
                throw new DfdlParseException("DFDL parse failed. Diagnostics: " + diagnostics);
            }
            return xmlOut.toString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (DfdlParseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DfdlParseException(
                    "Apache Daffodil raised an exception during parse: " + ex.getMessage(), ex);
        }
    }

    public List<String> collectDiagnostics(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
                .map(this::formatDiagnostic)
                .collect(Collectors.toList());
    }

    public String formatDiagnostics(List<Diagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "(no diagnostics reported)";
        }
        return diagnostics.stream()
                .map(this::formatDiagnostic)
                .collect(Collectors.joining(" | "));
    }

    private String formatDiagnostic(Diagnostic diagnostic) {
        StringBuilder sb = new StringBuilder();
        sb.append(diagnostic.isError() ? "ERROR" : "WARNING");
        sb.append(": ");
        sb.append(diagnostic.getMessage());
        if (diagnostic.getDataLocations() != null && !diagnostic.getDataLocations().isEmpty()) {
            sb.append(" @ ").append(diagnostic.getDataLocations());
        }
        if (diagnostic.getLocationsInSchemaFiles() != null && !diagnostic.getLocationsInSchemaFiles().isEmpty()) {
            sb.append(" schema=").append(diagnostic.getLocationsInSchemaFiles());
        }
        Throwable cause = diagnostic.getSomeCause();
        if (cause != null) {
            sb.append(" cause=").append(cause.getClass().getName())
                    .append(": ").append(cause.getMessage());
        }
        return sb.toString();
    }

    private void ensureCompiled() {
        if (!schemaCompiled || dataProcessor == null) {
            throw new DfdlSchemaException(
                    "DFDL schema is not compiled. Check startup logs and schema path: " + schemaAbsolutePath);
        }
    }

    public boolean isSchemaCompiled() {
        return schemaCompiled;
    }

    public String getSchemaFileName() {
        return schemaFileName;
    }

    public String getSchemaAbsolutePath() {
        return schemaAbsolutePath;
    }

    public DataProcessor getDataProcessor() {
        return dataProcessor;
    }

    public DaffodilProperties getProperties() {
        return properties;
    }
}
