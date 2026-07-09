package com.example.dfdl.controller;

import com.example.dfdl.dto.HealthResponse;
import com.example.dfdl.dto.ParseResponse;
import com.example.dfdl.service.DaffodilParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping
public class ParserController {

    private static final Logger log = LoggerFactory.getLogger(ParserController.class);

    private final DaffodilParserService parserService;

    public ParserController(DaffodilParserService parserService) {
        this.parserService = parserService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        boolean compiled = parserService.isSchemaCompiled();
        return new HealthResponse(
                compiled ? "UP" : "DOWN",
                compiled,
                parserService.getSchemaFileName());
    }

    /**
     * Parse a binary file uploaded as multipart field {@code file}.
     */
    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParseResponse> parse(@RequestParam("file") MultipartFile file) throws IOException {
        log.info("Request received: POST /parse filename='{}' size={}",
                file.getOriginalFilename(), file.getSize());
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(ParseResponse.failure("Invalid binary: uploaded file is empty"));
        }
        ParseResponse response = parserService.parse(file.getBytes());
        return ResponseEntity.ok(response);
    }

    /**
     * Parse a binary file already present under the configured samples directory.
     */
    @PostMapping("/parse/sample/{fileName}")
    public ResponseEntity<ParseResponse> parseSample(@PathVariable("fileName") String fileName) {
        log.info("Request received: POST /parse/sample/{}", fileName);
        ParseResponse response = parserService.parseSampleFile(fileName);
        return ResponseEntity.ok(response);
    }
}
