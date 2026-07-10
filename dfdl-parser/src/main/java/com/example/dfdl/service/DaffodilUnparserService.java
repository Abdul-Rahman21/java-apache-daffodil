package com.example.dfdl.service;

import com.example.dfdl.config.DaffodilConfiguration.DaffodilProperties;
import com.example.dfdl.exception.DfdlSchemaException;
import com.example.dfdl.exception.DfdlUnparseException;
import jakarta.annotation.PostConstruct;
import org.apache.daffodil.japi.Compiler;
import org.apache.daffodil.japi.Daffodil;
import org.apache.daffodil.japi.DataProcessor;
import org.apache.daffodil.japi.Diagnostic;
import org.apache.daffodil.japi.ProcessorFactory;
import org.apache.daffodil.japi.UnparseResult;
import org.apache.daffodil.japi.infoset.XMLTextInfosetInputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compiles the SMPRES DFDL schema once and unparses XML infosets to EBCDIC binary.
 */
@Service
public class DaffodilUnparserService {

    private static final Logger log = LoggerFactory.getLogger(DaffodilUnparserService.class);

    private final DaffodilProperties properties;
    private final DaffodilParserService parserService;

    private volatile DataProcessor dataProcessor;
    private volatile boolean schemaCompiled;
    private volatile String schemaFileName;
    private volatile String schemaAbsolutePath;

    public DaffodilUnparserService(DaffodilProperties properties, DaffodilParserService parserService) {
        this.properties = properties;
        this.parserService = parserService;
    }

    @PostConstruct
    public void initialize() {
        log.info("Application startup: beginning DFDL response schema compilation");
        try {
            compileResponseSchema();
        } catch (DfdlSchemaException ex) {
            log.error("Response schema compilation failed during startup: {}", ex.getMessage(), ex);
            schemaCompiled = false;
            dataProcessor = null;
        }
    }

    public synchronized void compileResponseSchema() {
        Path schemaPath = Path.of(properties.getResponseSchema()).toAbsolutePath().normalize();
        this.schemaAbsolutePath = schemaPath.toString();
        this.schemaFileName = schemaPath.getFileName() != null
                ? schemaPath.getFileName().toString()
                : properties.getResponseSchema();

        if (!Files.exists(schemaPath) || !Files.isRegularFile(schemaPath)) {
            schemaCompiled = false;
            dataProcessor = null;
            throw new DfdlSchemaException(
                    "DFDL response schema not found at configured path: " + schemaPath
                            + ". Mount schemas under /app/schema or update daffodil.response-schema.");
        }

        long start = System.nanoTime();
        try {
            log.info("Response schema compilation started for '{}'", schemaPath);
            Compiler compiler = Daffodil.compiler();
            ProcessorFactory processorFactory = compiler.compileFile(schemaPath.toFile());
            if (processorFactory.isError()) {
                schemaCompiled = false;
                dataProcessor = null;
                throw new DfdlSchemaException(
                        "DFDL response schema compilation failed for '" + schemaPath + "'. Diagnostics: "
                                + parserService.formatDiagnostics(processorFactory.getDiagnostics()));
            }

            DataProcessor processor = processorFactory.onPath("/");
            if (processor.isError()) {
                schemaCompiled = false;
                dataProcessor = null;
                throw new DfdlSchemaException(
                        "DFDL response DataProcessor creation failed for '" + schemaPath + "'. Diagnostics: "
                                + parserService.formatDiagnostics(processor.getDiagnostics()));
            }

            this.dataProcessor = processor;
            this.schemaCompiled = true;
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            log.info("Response schema compilation finished successfully for '{}' in {} ms", schemaPath, elapsedMs);
        } catch (DfdlSchemaException ex) {
            throw ex;
        } catch (Exception ex) {
            schemaCompiled = false;
            dataProcessor = null;
            throw new DfdlSchemaException(
                    "Unexpected failure compiling DFDL response schema '" + schemaPath + "': " + ex.getMessage(), ex);
        }
    }

    public byte[] unparseXml(String infosetXml) {
        Objects.requireNonNull(infosetXml, "infosetXml must not be null");
        if (infosetXml.isBlank()) {
            throw new DfdlUnparseException("Infoset XML is empty; cannot unparse");
        }
        ensureCompiled();

        log.info("Unparsing started ({} XML chars)", infosetXml.length());
        long start = System.nanoTime();
        ByteArrayOutputStream binaryOut = new ByteArrayOutputStream();
        try (WritableByteChannel channel = Channels.newChannel(binaryOut);
             ByteArrayInputStream xmlIn = new ByteArrayInputStream(infosetXml.getBytes(StandardCharsets.UTF_8))) {

            XMLTextInfosetInputter inputter = new XMLTextInfosetInputter(xmlIn);
            UnparseResult result = dataProcessor.unparse(inputter, channel);
            if (result.isError()) {
                String diagnostics = formatDiagnostics(result.getDiagnostics());
                throw new DfdlUnparseException("DFDL unparse failed. Diagnostics: " + diagnostics);
            }
            byte[] bytes = binaryOut.toByteArray();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            log.info("Unparsing finished successfully in {} ms ({} bytes)", elapsedMs, bytes.length);
            return bytes;
        } catch (DfdlUnparseException ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            log.warn("Unparsing failed after {} ms: {}", elapsedMs, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            log.error("Unparsing failed after {} ms with unexpected exception", elapsedMs, ex);
            throw new DfdlUnparseException(
                    "Apache Daffodil raised an exception during unparse: " + ex.getMessage(), ex);
        }
    }

    private String formatDiagnostics(List<Diagnostic> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "(no diagnostics reported)";
        }
        return diagnostics.stream().map(Diagnostic::toString).collect(Collectors.joining(" | "));
    }

    private void ensureCompiled() {
        if (!schemaCompiled || dataProcessor == null) {
            throw new DfdlSchemaException(
                    "DFDL response schema is not compiled. Check startup logs and schema path: "
                            + schemaAbsolutePath);
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
}
