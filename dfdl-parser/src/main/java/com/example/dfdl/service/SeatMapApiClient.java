package com.example.dfdl.service;

import com.example.dfdl.config.DaffodilConfiguration.DaffodilProperties;
import com.example.dfdl.exception.SeatMapApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * HTTP client for the external seat-map API ({@code POST /api/seatmap}).
 */
@Component
public class SeatMapApiClient {

    private static final Logger log = LoggerFactory.getLogger(SeatMapApiClient.class);

    private final RestClient restClient;
    private final String seatMapApiUrl;

    public SeatMapApiClient(RestClient.Builder restClientBuilder, DaffodilProperties properties) {
        this.seatMapApiUrl = properties.getSeatMapApiUrl();
        this.restClient = restClientBuilder.build();
        log.info("Seat-map API client configured for '{}'", seatMapApiUrl);
    }

    /**
     * POSTs seat-map <strong>request</strong> JSON and returns the seat-map <strong>response</strong> JSON.
     */
    public JsonNode callSeatMap(JsonNode seatMapRequest) {
        if (seatMapRequest == null || seatMapRequest.isNull() || seatMapRequest.isMissingNode()) {
            throw new SeatMapApiException("Seat-map request JSON is empty; cannot call " + seatMapApiUrl);
        }

        log.info("Calling seat-map API POST {}", seatMapApiUrl);
        long start = System.nanoTime();
        try {
            JsonNode response = restClient.post()
                    .uri(seatMapApiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(seatMapRequest)
                    .retrieve()
                    .body(JsonNode.class);

            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (response == null || response.isNull()) {
                throw new SeatMapApiException(
                        "Seat-map API returned an empty body from " + seatMapApiUrl + " (" + elapsedMs + " ms)");
            }
            log.info("Seat-map API call succeeded in {} ms", elapsedMs);
            return response;
        } catch (RestClientResponseException ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            String body = ex.getResponseBodyAsString();
            String detail = (body == null || body.isBlank()) ? ex.getStatusText() : truncate(body, 500);
            throw new SeatMapApiException(
                    "Seat-map API returned HTTP " + ex.getStatusCode().value()
                            + " from " + seatMapApiUrl + " after " + elapsedMs + " ms: " + detail,
                    ex.getStatusCode().value(),
                    ex);
        } catch (RestClientException ex) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            throw new SeatMapApiException(
                    "Seat-map API call failed to " + seatMapApiUrl + " after " + elapsedMs + " ms: "
                            + ex.getMessage(),
                    ex);
        }
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}
