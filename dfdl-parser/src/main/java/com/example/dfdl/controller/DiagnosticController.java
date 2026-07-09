package com.example.dfdl.controller;

import com.example.dfdl.dto.DiagnosticResponse;
import com.example.dfdl.service.DiagnosticService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping
public class DiagnosticController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticController.class);

    private final DiagnosticService diagnosticService;

    public DiagnosticController(DiagnosticService diagnosticService) {
        this.diagnosticService = diagnosticService;
    }

    /**
     * Compile the configured DFDL schema and optionally parse a binary payload
     * to assess Apache Daffodil compatibility with IBM ACE DFDL schemas.
     *
     * @param file optional binary sample; when omitted, only compile diagnostics are returned
     */
    @PostMapping("/diagnose")
    public DiagnosticResponse diagnose(
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        boolean present = file != null && !file.isEmpty();
        log.info("Request received: POST /diagnose filePresent={} size={}",
                present,
                present ? file.getSize() : 0L);

        byte[] binary = null;
        if (present) {
            binary = file.getBytes();
        }
        return diagnosticService.diagnose(binary);
    }
}
