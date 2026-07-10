package com.example.dfdl.service;

import com.example.dfdl.exception.DfdlUnparseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Orchestrates seat-map response JSON → SMPRES XML → binary unparse.
 */
@Service
public class SeatMapUnparseService {

    private static final Logger log = LoggerFactory.getLogger(SeatMapUnparseService.class);

    private final SeatMapResponseMapper responseMapper;
    private final DaffodilUnparserService unparserService;
    private final ObjectMapper objectMapper;

    public SeatMapUnparseService(
            SeatMapResponseMapper responseMapper,
            DaffodilUnparserService unparserService,
            ObjectMapper objectMapper) {
        this.responseMapper = responseMapper;
        this.unparserService = unparserService;
        this.objectMapper = objectMapper;
    }

    public byte[] unparseSeatMapResponse(JsonNode seatMapResponse) {
        log.info("Request processing: seat-map response → SMPRES binary");
        String xml = responseMapper.toSmpreXml(seatMapResponse);
        return unparserService.unparseXml(xml);
    }

    public byte[] unparseSeatMapResponseJson(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            throw new IllegalArgumentException("JSON body is empty");
        }
        try {
            JsonNode node = objectMapper.readTree(jsonBody);
            return unparseSeatMapResponse(node);
        } catch (IOException ex) {
            throw new DfdlUnparseException("Invalid seat-map response JSON: " + ex.getMessage(), ex);
        }
    }
}
