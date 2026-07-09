package com.example.dfdl.exception;

import com.example.dfdl.dto.ParseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DfdlParseException.class)
    public ResponseEntity<ParseResponse> handleParseException(DfdlParseException ex) {
        log.warn("Parsing failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ParseResponse.failure(ex.getMessage()));
    }

    @ExceptionHandler(DfdlSchemaException.class)
    public ResponseEntity<Map<String, Object>> handleSchemaException(DfdlSchemaException ex) {
        log.error("Schema error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(
                "SCHEMA_ERROR",
                ex.getMessage(),
                ex));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ParseResponse> handleMissingPart(MissingServletRequestPartException ex) {
        String message = "Missing multipart field '" + ex.getRequestPartName() + "'. Expected field name: file";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ParseResponse.failure(message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ParseResponse> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ParseResponse.failure("Uploaded file exceeds configured maximum size: " + ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ParseResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ParseResponse.failure(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unexpected runtime exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(
                "UNEXPECTED_ERROR",
                ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName(),
                ex));
    }

    private Map<String, Object> errorBody(String code, String message, Throwable ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("errorCode", code);
        body.put("error", message);
        body.put("exceptionType", ex.getClass().getName());
        if (ex.getCause() != null) {
            body.put("cause", ex.getCause().getMessage());
            body.put("causeType", ex.getCause().getClass().getName());
        }
        return body;
    }
}
