package com.example.dfdl.dto;

public class HealthResponse {

    private String status;
    private boolean schemaCompiled;
    private String schema;
    private boolean responseSchemaCompiled;
    private String responseSchema;

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
}
