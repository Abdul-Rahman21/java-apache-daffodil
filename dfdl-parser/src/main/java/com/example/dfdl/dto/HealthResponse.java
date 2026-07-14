package com.example.dfdl.dto;

public class HealthResponse {

    private String status;
    private boolean schemaCompiled;
    private String schema;
    private boolean responseSchemaCompiled;
    private String responseSchema;
    /**
     * Character encoding of ACE EDIFACT binaries for both parse and unparse.
     * Always IBM037 (EBCDIC) — not ASCII/UTF-8. Matches client sample binaries.
     */
    private String binaryEncoding = "IBM037";
    private String binaryEncodingName = "EBCDIC";

    public HealthResponse() {
    }

    public HealthResponse(
            String status,
            boolean schemaCompiled,
            String schema,
            boolean responseSchemaCompiled,
            String responseSchema) {
        this.status = status;
        this.schemaCompiled = schemaCompiled;
        this.schema = schema;
        this.responseSchemaCompiled = responseSchemaCompiled;
        this.responseSchema = responseSchema;
        this.binaryEncoding = "IBM037";
        this.binaryEncodingName = "EBCDIC";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSchemaCompiled() {
        return schemaCompiled;
    }

    public void setSchemaCompiled(boolean schemaCompiled) {
        this.schemaCompiled = schemaCompiled;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public boolean isResponseSchemaCompiled() {
        return responseSchemaCompiled;
    }

    public void setResponseSchemaCompiled(boolean responseSchemaCompiled) {
        this.responseSchemaCompiled = responseSchemaCompiled;
    }

    public String getResponseSchema() {
        return responseSchema;
    }

    public void setResponseSchema(String responseSchema) {
        this.responseSchema = responseSchema;
    }

    public String getBinaryEncoding() {
        return binaryEncoding;
    }

    public void setBinaryEncoding(String binaryEncoding) {
        this.binaryEncoding = binaryEncoding;
    }

    public String getBinaryEncodingName() {
        return binaryEncodingName;
    }

    public void setBinaryEncodingName(String binaryEncodingName) {
        this.binaryEncodingName = binaryEncodingName;
    }
}
