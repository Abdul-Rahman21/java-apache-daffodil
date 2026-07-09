package com.example.dfdl.exception;

/**
 * Raised when binary data cannot be parsed with the compiled DFDL schema.
 */
public class DfdlParseException extends RuntimeException {

    public DfdlParseException(String message) {
        super(message);
    }

    public DfdlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
