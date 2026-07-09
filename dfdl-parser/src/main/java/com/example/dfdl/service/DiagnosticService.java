package com.example.dfdl.service;

import com.example.dfdl.config.DaffodilConfiguration.DaffodilProperties;
import com.example.dfdl.dto.DiagnosticResponse;
import org.apache.daffodil.japi.Compiler;
import org.apache.daffodil.japi.Daffodil;
import org.apache.daffodil.japi.DataProcessor;
import org.apache.daffodil.japi.ParseResult;
import org.apache.daffodil.japi.ProcessorFactory;
import org.apache.daffodil.japi.infoset.XMLTextInfosetOutputter;
import org.apache.daffodil.japi.io.InputSourceDataInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Determines whether Apache Daffodil can compile (and optionally parse with)
 * IBM ACE DFDL schemas by capturing raw compile/parse diagnostics and exceptions.
 */
@Service
public class DiagnosticService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticService.class);

    private final DaffodilProperties properties;
    private final DaffodilParserService parserService;

    public DiagnosticService(DaffodilProperties properties, DaffodilParserService parserService) {
        this.properties = properties;
        this.parserService = parserService;
    }

    /**
     * Compiles the configured schema and, when binary data is supplied, attempts a parse.
     * Exceptions from Apache Daffodil are not suppressed; raw messages are returned.
     */
    public DiagnosticResponse diagnose(byte[] binaryData) {
        log.info("Diagnostic execution started");
        long start = System.nanoTime();

        DiagnosticResponse response = new DiagnosticResponse();
        Path schemaPath = Path.of(properties.getSchema()).toAbsolutePath().normalize();
        response.setSchemaPath(schemaPath.toString());
        response.setNotes(
                "Compatibility probe for IBM ACE DFDL schemas with Apache Daffodil. "
                        + "compileSuccess=true indicates the schema compiled; "
                        + "parseSuccess reflects parse of the supplied binary (if any).");

        List<String> compileDiagnostics = new ArrayList<>();
        DataProcessor processor = null;

        try {
            if (!Files.exists(schemaPath)) {
                response.setCompileSuccess(false);
                compileDiagnostics.add("ERROR: Schema file does not exist: " + schemaPath);
                response.setCompileDiagnostics(compileDiagnostics);
                response.setExceptionType(java.nio.file.NoSuchFileException.class.getName());
                response.setExceptionMessage("Schema file does not exist: " + schemaPath);
                return finish(response, start);
            }

            Compiler compiler = Daffodil.compiler();
            ProcessorFactory processorFactory = compiler.compileFile(schemaPath.toFile());
            compileDiagnostics.addAll(parserService.collectDiagnostics(processorFactory.getDiagnostics()));

            if (processorFactory.isError()) {
                response.setCompileSuccess(false);
                response.setCompileDiagnostics(compileDiagnostics);
                response.setExceptionMessage("ProcessorFactory reported isError=true");
                response.setExceptionType("org.apache.daffodil.japi.ProcessorFactory");
                return finish(response, start);
            }

            processor = processorFactory.onPath("/");
            compileDiagnostics.addAll(parserService.collectDiagnostics(processor.getDiagnostics()));

            if (processor.isError()) {
                response.setCompileSuccess(false);
                response.setCompileDiagnostics(compileDiagnostics);
                response.setExceptionMessage("DataProcessor reported isError=true");
                response.setExceptionType("org.apache.daffodil.japi.DataProcessor");
                return finish(response, start);
            }

            response.setCompileSuccess(true);
            response.setCompileDiagnostics(compileDiagnostics);

            if (binaryData == null || binaryData.length == 0) {
                response.setParseSuccess(null);
                response.setParseDiagnostics(List.of(
                        "INFO: No binary payload supplied; compile-only diagnostic completed."));
                return finish(response, start);
            }

            List<String> parseDiagnostics = new ArrayList<>();
            ByteArrayOutputStream xmlOut = new ByteArrayOutputStream();
            XMLTextInfosetOutputter outputter = new XMLTextInfosetOutputter(xmlOut, true);

            try (InputSourceDataInputStream input =
                         new InputSourceDataInputStream(new ByteArrayInputStream(binaryData))) {
                ParseResult parseResult = processor.parse(input, outputter);
                parseDiagnostics.addAll(parserService.collectDiagnostics(parseResult.getDiagnostics()));
                response.setParseSuccess(!parseResult.isError());
                response.setParseDiagnostics(parseDiagnostics);
                if (parseResult.isError()) {
                    response.setExceptionMessage("ParseResult reported isError=true");
                    response.setExceptionType("org.apache.daffodil.japi.ParseResult");
                }
            }
        } catch (Exception ex) {
            // Do not suppress Apache Daffodil (or related) exceptions — surface raw messages.
            log.error("Diagnostic execution raised exception: {}", ex.toString(), ex);
            if (!response.isCompileSuccess() && response.getCompileDiagnostics().isEmpty()) {
                response.setCompileSuccess(false);
            }
            response.setExceptionType(ex.getClass().getName());
            response.setExceptionMessage(ex.getMessage() != null ? ex.getMessage() : ex.toString());

            Throwable cause = ex.getCause();
            List<String> extras = new ArrayList<>(response.getCompileDiagnostics());
            extras.add("EXCEPTION: " + ex.getClass().getName() + ": "
                    + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
            if (cause != null) {
                extras.add("CAUSE: " + cause.getClass().getName() + ": "
                        + (cause.getMessage() != null ? cause.getMessage() : cause.toString()));
            }
            response.setCompileDiagnostics(extras);

            if (processor != null && binaryData != null && binaryData.length > 0
                    && response.getParseSuccess() == null) {
                List<String> parseExtras = new ArrayList<>(response.getParseDiagnostics());
                parseExtras.add("EXCEPTION during parse phase: " + ex.getClass().getName() + ": "
                        + (ex.getMessage() != null ? ex.getMessage() : ex.toString()));
                response.setParseDiagnostics(parseExtras);
                response.setParseSuccess(false);
            }
        }

        return finish(response, start);
    }

    private DiagnosticResponse finish(DiagnosticResponse response, long startNanos) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        response.setElapsedMillis(elapsedMs);
        log.info(
                "Diagnostic execution finished: compileSuccess={}, parseSuccess={}, elapsed={} ms, schema={}",
                response.isCompileSuccess(),
                response.getParseSuccess(),
                elapsedMs,
                response.getSchemaPath());
        return response;
    }
}
