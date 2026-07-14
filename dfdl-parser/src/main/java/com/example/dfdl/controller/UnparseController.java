package com.example.dfdl.controller;

import com.example.dfdl.service.SeatMapUnparseService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class UnparseController {

    private static final Logger log = LoggerFactory.getLogger(UnparseController.class);

    private final SeatMapUnparseService seatMapUnparseService;

    public UnparseController(SeatMapUnparseService seatMapUnparseService) {
        this.seatMapUnparseService = seatMapUnparseService;
    }

    /**
     * Reverse flow: seat-map response JSON → DFDL SMPRES unparse → binary file.
     *
     * <p>Request body: JSON matching {@code Respone_seatmapresponse_3.txt}.
     * Response: {@code application/octet-stream} binary ({@code SMPRES.bin}) encoded as
     * <strong>EBCDIC IBM037</strong> (same as ACE client sample {@code Response_SMPRES_4.bin}).
     * Not ASCII/UTF-8 — open with IBM037/CP037 to read EDIFACT text.
     */
    @PostMapping(
            value = "/unparse",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> unparse(@RequestBody JsonNode seatMapResponse) {
        log.info("Request received: POST /unparse (output encoding=IBM037/EBCDIC)");
        byte[] binary = seatMapUnparseService.unparseSeatMapResponse(seatMapResponse);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"SMPRES.bin\"")
                // Explicit so callers know this is ACE EBCDIC, not ASCII text.
                .header("X-DFDL-Binary-Encoding", "IBM037")
                .header("X-DFDL-Binary-Encoding-Name", "EBCDIC")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(binary.length)
                .body(binary);
    }
}
