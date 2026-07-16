package com.example.dfdl.controller;

import com.example.dfdl.service.SeatMapPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Orchestrated API: SMPREQ {@code .bin} → parse → external seat-map → unparse → SMPRES {@code .bin}.
 */
@RestController
@RequestMapping
public class ProcessController {

    private static final Logger log = LoggerFactory.getLogger(ProcessController.class);

    private final SeatMapPipelineService pipelineService;

    public ProcessController(SeatMapPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /**
     * Full pipeline from uploaded request binary.
     *
     * <ol>
     *   <li>Parse SMPREQ {@code .bin} → seat-map request JSON</li>
     *   <li>POST that JSON to configured seat-map API (default {@code http://localhost:9000/api/seatmap})</li>
     *   <li>Unparse seat-map response JSON → SMPRES {@code .bin}</li>
     * </ol>
     */
    @PostMapping(
            value = "/process",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> process(@RequestParam("file") MultipartFile file) throws IOException {
        log.info("Request received: POST /process filename='{}' size={}",
                file.getOriginalFilename(), file.getSize());
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Invalid binary: uploaded file is empty");
        }
        byte[] binary = pipelineService.process(file.getBytes());
        return binaryResponse(binary);
    }

    /**
     * Same pipeline using a sample file under the configured samples directory.
     */
    @PostMapping(
            value = "/process/sample/{fileName}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> processSample(@PathVariable("fileName") String fileName) {
        log.info("Request received: POST /process/sample/{}", fileName);
        byte[] binary = pipelineService.processSampleFile(fileName);
        return binaryResponse(binary);
    }

    private static ResponseEntity<byte[]> binaryResponse(byte[] binary) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"SMPRES.bin\"")
                .header("X-DFDL-Binary-Encoding", "IBM037")
                .header("X-DFDL-Binary-Encoding-Name", "EBCDIC")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(binary.length)
                .body(binary);
    }
}
