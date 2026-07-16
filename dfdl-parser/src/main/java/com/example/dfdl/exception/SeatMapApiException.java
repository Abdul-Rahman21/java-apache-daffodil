package com.example.dfdl.exception;

/**
 * Raised when the external seat-map HTTP API cannot be called or returns an error.
 */
public class SeatMapApiException extends RuntimeException {

    private final int statusCode;

    public SeatMapApiException(String message) {
        this(message, -1, null);
    }

    public SeatMapApiException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public SeatMapApiException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public SeatMapApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
