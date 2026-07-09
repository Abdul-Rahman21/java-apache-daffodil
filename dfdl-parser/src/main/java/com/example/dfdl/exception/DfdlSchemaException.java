package com.example.dfdl.exception;

/**
 * Raised when a DFDL schema cannot be found, imported, or compiled.
 */
public class DfdlSchemaException extends RuntimeException {

    public DfdlSchemaException(String message) {
        super(message);
    }

    public DfdlSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
