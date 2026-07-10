package com.example.dfdl.exception;

/**
 * Raised when seat-map JSON cannot be unparsed to binary with the SMPRES DFDL schema.
 */
public class DfdlUnparseException extends RuntimeException {

    public DfdlUnparseException(String message) {
        super(message);
    }

    public DfdlUnparseException(String message, Throwable cause) {
        super(message, cause);
    }
}
