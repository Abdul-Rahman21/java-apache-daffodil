package com.example.dfdl.service;

import com.example.dfdl.dto.ParseResponse;
import com.example.dfdl.exception.DfdlParseException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * End-to-end flow: SMPREQ binary → parse → seat-map API → unparse → SMPRES binary.
 */
@Service
public class SeatMapPipelineService {

    private static final Logger log = LoggerFactory.getLogger(SeatMapPipelineService.class);

    private final DaffodilParserService parserService;
    private final SeatMapApiClient seatMapApiClient;
    private final SeatMapUnparseService unparseService;

    public SeatMapPipelineService(
            DaffodilParserService parserService,
            SeatMapApiClient seatMapApiClient,
            SeatMapUnparseService unparseService) {
        this.parserService = parserService;
        this.seatMapApiClient = seatMapApiClient;
        this.unparseService = unparseService;
    }

    /**
     * Parse request binary, call external seat-map API with mapped request JSON,
     * then unparse the seat-map response JSON to an SMPRES binary.
     */
    public byte[] process(byte[] requestBinary) {
        log.info("Pipeline started: parse → seatmap → unparse ({} bytes)", requestBinary.length);

        ParseResponse parsed = parserService.parse(requestBinary);
        if (!parsed.isSuccess() || parsed.getJson() == null) {
            throw new DfdlParseException(
                    parsed.getError() != null ? parsed.getError() : "Parse produced no seat-map request JSON");
        }

        JsonNode seatMapRequest = parsed.getJson();
        log.info("Pipeline step 1/3: parse OK — calling seat-map API with request JSON");

        JsonNode seatMapResponse = seatMapApiClient.callSeatMap(seatMapRequest);
        log.info("Pipeline step 2/3: seat-map API OK — unparsing response JSON to binary");

        byte[] responseBinary = unparseService.unparseSeatMapResponse(seatMapResponse);
        log.info("Pipeline step 3/3: unparse OK — returning {} bytes", responseBinary.length);
        return responseBinary;
    }

    public byte[] processSampleFile(String fileName) {
        ParseResponse parsed = parserService.parseSampleFile(fileName);
        if (!parsed.isSuccess() || parsed.getJson() == null) {
            throw new DfdlParseException(
                    parsed.getError() != null ? parsed.getError() : "Parse produced no seat-map request JSON");
        }
        JsonNode seatMapResponse = seatMapApiClient.callSeatMap(parsed.getJson());
        return unparseService.unparseSeatMapResponse(seatMapResponse);
    }
}
