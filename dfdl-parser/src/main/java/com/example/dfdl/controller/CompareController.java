package com.example.dfdl.controller;

import com.example.dfdl.dto.BinaryCompareResponse;
import com.example.dfdl.service.BinaryCompareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Compares a client-shared SMPRES binary with an unparse-generated binary.
 */
@RestController
@RequestMapping
public class CompareController {

    private static final Logger log = LoggerFactory.getLogger(CompareController.class);

    private final BinaryCompareService binaryCompareService;

    public CompareController(BinaryCompareService binaryCompareService) {
        this.binaryCompareService = binaryCompareService;
    }

    /**
     * Compare client response binary vs unparse output binary.
     *
     * <p>Multipart fields:
     * <ul>
     *   <li>{@code clientFile} — client shared binary (e.g. Response_SMPRES_4.bin)</li>
     *   <li>{@code unparseFile} — binary from POST /unparse (e.g. SMPRES.bin)</li>
     * </ul>
     */
    @PostMapping(value = "/compare", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BinaryCompareResponse> compare(
            @RequestParam("clientFile") MultipartFile clientFile,
            @RequestParam("unparseFile") MultipartFile unparseFile) throws IOException {

        log.info(
                "Request received: POST /compare clientFile='{}' ({} bytes) unparseFile='{}' ({} bytes)",
                clientFile.getOriginalFilename(),
                clientFile.getSize(),
                unparseFile.getOriginalFilename(),
                unparseFile.getSize());

        if (clientFile.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(BinaryCompareResponse.failure("clientFile is empty"));
        }
        if (unparseFile.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(BinaryCompareResponse.failure("unparseFile is empty"));
        }

        BinaryCompareResponse response = binaryCompareService.compare(
                clientFile.getBytes(),
                clientFile.getOriginalFilename(),
                unparseFile.getBytes(),
                unparseFile.getOriginalFilename());

        return ResponseEntity.ok(response);
    }
}
